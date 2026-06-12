package com.feedman.android.core.auth

import com.feedman.android.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionStateProvider] の AuthRepository 連動実装（Issue #24 / requirements.md）。
 *
 * 起動時の挙動:
 *
 * 1. 構築直後は [SessionState.Restoring] から開始する（Req 1.1）。
 * 2. [ApplicationScope] 上で復元コルーチンを起動し、[TokenStore] を読む。
 *    - access token / refresh token が空 → [SessionState.LoggedOut] に確定（Req 1.3 / NFR 1.2）。
 *      ネットワーク I/O は発行しない。
 *    - access token / refresh token が保存済み → [AuthRepository.refresh] を 1 回試行する。
 *      - [RefreshResult.Success] → [SessionState.LoggedIn] に確定（Req 1.2）。
 *      - [RefreshResult.AuthRequired] → 保存済みトークンは [AuthRepositoryImpl] 側で消去済み。
 *        [SessionState.LoggedOut] に確定（Req 1.4 / NFR 3.1）。
 *      - [RefreshResult.NetworkFailure] / [RefreshResult.ServerError] → 保存済みトークンを
 *        保持したまま [SessionState.LoggedIn] にフォールバック（Req 1.5）。
 *        以降の API 呼び出しで 401 が発生すれば、Issue #22 の認証 Interceptor が再 refresh を
 *        試行し、認証切れと判定されれば [AuthRepository.observeIsAuthenticated] が `false` に
 *        遷移するため、本 Provider は [SessionState.LoggedOut] に追従できる。
 * 3. 復元コルーチン全体に [withTimeoutOrNull] で 5 秒の上限を掛ける（NFR 1.1）。タイムアウトを
 *    超えた場合はネットワーク失敗扱いと同じフォールバック（保存トークンがあれば LoggedIn、
 *    無ければ LoggedOut）に倒す。
 * 4. 復元が確定したら、以降は [AuthRepository.observeIsAuthenticated] の値変化に追従する
 *    （Req 4.3 / Req 5.3）。`true` → [SessionState.LoggedIn] / `false` → [SessionState.LoggedOut]。
 *
 * 本 Provider は `mockMode = false` の実環境向けに限定される（DI 切替は
 * [com.feedman.android.di.AuthModule]）。mockMode = true 環境では
 * [MockModeSessionStateProvider] が代わりにバインドされる。
 *
 * @param authRepository 認証境界。`refresh()` で起動時復元、`observeIsAuthenticated()` で
 *     以降の遷移追跡を行う。
 * @param tokenStore 保存済みトークンの有無を判定するため直接参照する（ネットワーク I/O
 *     スキップ判断 / Req NFR 1.2）。
 * @param scope アプリケーション寿命の [CoroutineScope]。復元コルーチンと
 *     `observeIsAuthenticated()` 追従コルーチンの両方を起動する。
 * @param restoreTimeoutMillis 起動時復元の上限ミリ秒（NFR 1.1）。テスト時のみ短縮可能。
 */
@Singleton
class AuthRepositorySessionStateProvider @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionStateProvider {

    private val _state: MutableStateFlow<SessionState> = MutableStateFlow(SessionState.Restoring)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    /** 復元処理の上限。テスト用にデフォルト値とは別の値を渡せる。 */
    private var restoreTimeoutMillis: Long = DEFAULT_RESTORE_TIMEOUT_MILLIS

    init {
        scope.launch { restoreAndFollow() }
    }

    /**
     * テスト専用: 復元タイムアウトを上書きする。本番経路からは呼ばない。
     * 内部状態に副作用は持たず、次回 [restoreAndFollow] 呼び出し（テスト構成）で参照される。
     */
    internal fun overrideRestoreTimeoutForTest(millis: Long) {
        restoreTimeoutMillis = millis
    }

    private suspend fun restoreAndFollow() {
        // Phase 1: 起動時復元（Req 1.1〜1.5 / NFR 1.1 / NFR 1.2）。
        val restored = withTimeoutOrNull(restoreTimeoutMillis) { performRestore() }
        val confirmed = restored ?: fallbackOnTimeoutOrFailure()
        _state.value = confirmed

        // Phase 2: 以降は AuthRepository.observeIsAuthenticated() の **変化** に追従する
        // （Req 4.3 / Req 5.3）。`StateFlow` は購読開始時に現在値を即時 emit するが、復元
        // フォールバック（Req 1.5: ネットワーク失敗時の LoggedIn 維持）では Repository 側の
        // `isAuthenticated` がまだ `false` のままなので、初期値を採用するとフォールバック
        // 判定を覆して LoggedOut に倒してしまう。`drop(1)` で初期値を捨て、以降の遷移
        // （exchange 成功で true / 401 起因の TokenStore 消去で false）だけを反映する。
        authRepository.observeIsAuthenticated().drop(1).collect { isAuthenticated ->
            _state.value = if (isAuthenticated) SessionState.LoggedIn else SessionState.LoggedOut
        }
    }

    /**
     * 保存済みトークンの有無を見て、必要なら refresh を 1 回試す（Req 1.2〜1.5）。
     *
     * 戻り値は確定した [SessionState]（[SessionState.Restoring] にはならない）。タイムアウト
     * 超過時は本関数自体がキャンセルされ、[fallbackOnTimeoutOrFailure] で別途確定される。
     */
    private suspend fun performRestore(): SessionState {
        val stored = tokenStore.read()
        val hasAccessToken = !stored?.accessToken.isNullOrBlank()
        if (!hasAccessToken) {
            // Req 1.3 / NFR 1.2: 保存済みトークン無し → ネットワーク I/O 発生させずに LoggedOut。
            return SessionState.LoggedOut
        }
        return when (authRepository.refresh()) {
            // Req 1.2: refresh 成功 → LoggedIn 確定。
            RefreshResult.Success -> SessionState.LoggedIn
            // Req 1.4: INVALID_REFRESH_TOKEN は AuthRepositoryImpl 側で TokenStore 消去 +
            // isAuthenticated=false への遷移が済んでいる（NFR 3.1）。Provider は LoggedOut に確定。
            RefreshResult.AuthRequired -> SessionState.LoggedOut
            // Req 1.5: ネットワーク失敗・サーバーエラーは保存トークンを保持したまま LoggedIn にフォールバック。
            is RefreshResult.NetworkFailure,
            is RefreshResult.ServerError -> SessionState.LoggedIn
        }
    }

    /**
     * [restoreTimeoutMillis] を超えて復元が完了しなかった場合のフォールバック判定（NFR 1.1）。
     *
     * - 保存済みアクセストークンがあるなら [SessionState.LoggedIn] にフォールバック
     *   （Req 1.5 と同様の挙動。最初の API 401 で #22 が回復を試みる）。
     * - 保存トークンが無いなら [SessionState.LoggedOut]。
     *
     * 本関数自体はタイムアウト外側で呼ばれるが、TokenStore.read() 自体は短時間で完了する
     * ローカル I/O のため、再度短時間内で結果を返せる前提とする。万一読み取りが失敗した
     * 場合は安全側として LoggedOut に倒す。
     */
    private suspend fun fallbackOnTimeoutOrFailure(): SessionState {
        return try {
            val stored = tokenStore.read()
            if (!stored?.accessToken.isNullOrBlank()) {
                SessionState.LoggedIn
            } else {
                SessionState.LoggedOut
            }
        } catch (_: Throwable) {
            // TokenStore 読み取り失敗（鍵破損など）は安全側として LoggedOut に倒す。
            SessionState.LoggedOut
        }
    }

    private companion object {
        /** Req NFR 1.1: 起動時復元の上限 5 秒。 */
        const val DEFAULT_RESTORE_TIMEOUT_MILLIS: Long = 5_000L
    }
}
