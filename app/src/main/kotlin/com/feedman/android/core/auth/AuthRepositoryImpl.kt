package com.feedman.android.core.auth

import com.feedman.android.core.data.UserRepository
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.network.RefreshTokenRequest
import com.feedman.android.core.network.RevokeTokenRequest
import com.feedman.android.core.network.TokenExchangeRequest
import com.feedman.android.core.network.TokenResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] の本実装（Issue #21 / requirements.md）。
 *
 * - exchange: SERVER.md §1.3 `POST /api/auth/token` を呼び、TokenStore に保存（Req 1）。
 * - refresh: SERVER.md §1.3 `POST /api/auth/refresh` を呼び、ローテーション結果を上書き保存（Req 2）。
 *   単一飛行: 並行呼び出しは [refreshInFlight] を介して同一結果を共有（Req 2.3 / NFR 2）。
 * - revoke: SERVER.md §1.3 `POST /api/auth/revoke` を呼び、結果に関わらず TokenStore を消去（Req 3）。
 * - currentUser: [UserRepository] 経由で SPEC §4.2 `GET /auth/me` を呼ぶ（Req 5）。
 *   UserRepository を経由することで `GET /auth/me` の HTTP 契約・テストの重複を避ける。
 *
 * ## 観測可能なログイン状態（NFR 3）
 *
 * 構築時に TokenStore を 1 度 read してアクセストークン保持有無を [isAuthenticated] に反映する。
 * 以後は exchange / refresh / revoke 経由の TokenStore 書き換えと同期して値を更新する
 * （外部から TokenStore を直接書き換える前提は持たない — Issue #21 のスコープでは
 * AuthRepository が唯一の書き換え窓口になる想定）。
 *
 * ## エラー変換
 *
 * [FeedmanApi] は [FeedmanException] を throw する。本実装ではこれを [ExchangeResult] /
 * [RefreshResult] / [CurrentUserResult] にマップする。`NETWORK_ERROR` はそれぞれの
 * `NetworkFailure` に、それ以外は `ServerError` / `Failure` / `AuthRequired` に分岐する。
 *
 * @param clock TokenSet の expires_at 計算に使う時刻ソース。テストでは [Clock.fixed] を渡す。
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
    private val tokenStore: TokenStore,
    private val userRepository: UserRepository,
    private val clock: Clock,
) : AuthRepository {

    private val refreshMutex = Mutex()

    /**
     * 進行中の refresh 呼び出しの結果を共有する Deferred（単一飛行 / Req 2.3 / NFR 2）。
     * `refreshMutex` で保護される。null のときは進行中の呼び出しが無い。
     */
    private var refreshInFlight: CompletableDeferred<RefreshResult>? = null

    private val isAuthenticated = MutableStateFlow(false)

    /**
     * 初期状態を TokenStore から読み出して [isAuthenticated] に反映する suspend 初期化フック。
     *
     * Hilt の `@Inject` は suspend init をサポートしないため、本実装の初期値は `false` で開始し、
     * 上位レイヤ（#22 系の AuthSession 等）から最初の [refresh] / [exchange] / [revoke] /
     * [refreshAuthenticatedState] 呼び出しで反映される。これにより本クラスは「保存・消去後の
     * 反映」を保証する（NFR 3.1）。
     */
    suspend fun refreshAuthenticatedState() {
        val token = tokenStore.read()?.accessToken
        isAuthenticated.value = !token.isNullOrBlank()
    }

    override suspend fun exchange(authCode: String, codeVerifier: String): ExchangeResult {
        val response: TokenResponse = try {
            api.exchangeAuthToken(TokenExchangeRequest(authCode = authCode, codeVerifier = codeVerifier))
        } catch (e: FeedmanException) {
            return mapExchangeException(e)
        }
        tokenStore.save(toTokenSet(response))
        isAuthenticated.value = true
        return ExchangeResult.Success
    }

    override suspend fun refresh(): RefreshResult {
        // 単一飛行の入口: 進行中の呼び出しがあれば await して同一結果を返す。
        val (deferred, isLeader) = refreshMutex.withLock {
            val existing = refreshInFlight
            if (existing != null) {
                existing to false
            } else {
                val fresh = CompletableDeferred<RefreshResult>()
                refreshInFlight = fresh
                fresh to true
            }
        }
        if (!isLeader) {
            return deferred.await()
        }
        val result = try {
            executeRefresh()
        } catch (t: Throwable) {
            // 通常 executeRefresh は RefreshResult を返すが、想定外の throwable は
            // 待機中の呼び出し側にも伝搬させる必要がある。
            refreshMutex.withLock { refreshInFlight = null }
            deferred.completeExceptionally(t)
            throw t
        }
        refreshMutex.withLock { refreshInFlight = null }
        deferred.complete(result)
        return result
    }

    private suspend fun executeRefresh(): RefreshResult {
        val stored = tokenStore.read()
        val refreshToken = stored?.refreshToken
        if (refreshToken.isNullOrBlank()) {
            // Req 2.6: 保存済み refresh token が無い → ネットワークリクエスト発行せず AuthRequired。
            isAuthenticated.value = false
            return RefreshResult.AuthRequired
        }
        val response: TokenResponse = try {
            api.refreshAuthToken(RefreshTokenRequest(refreshToken = refreshToken))
        } catch (e: FeedmanException) {
            return mapRefreshException(e)
        }
        tokenStore.save(toTokenSet(response))
        isAuthenticated.value = true
        return RefreshResult.Success
    }

    override suspend fun revoke() {
        val stored = tokenStore.read()
        val refreshToken = stored?.refreshToken
        if (!refreshToken.isNullOrBlank()) {
            try {
                api.revokeAuthToken(RevokeTokenRequest(refreshToken = refreshToken))
            } catch (_: FeedmanException) {
                // best-effort: ネットワーク失敗・サーバーエラーでもローカル消去は実行する（Req 3.2）。
            }
        }
        // Req 3.2 / 3.3: revoke 呼び出しが完了したら、リクエスト発行有無に関わらず TokenStore を消去。
        tokenStore.clear()
        isAuthenticated.value = false
    }

    override suspend fun currentUser(): CurrentUserResult {
        return try {
            CurrentUserResult.Success(userRepository.getCurrentUser())
        } catch (e: FeedmanException) {
            if (e.code == FeedmanException.CODE_NETWORK_ERROR) {
                CurrentUserResult.NetworkFailure(e)
            } else {
                CurrentUserResult.Failure(code = e.code, httpStatus = e.httpStatus, message = e.errorMessage)
            }
        }
    }

    override fun observeIsAuthenticated(): StateFlow<Boolean> = isAuthenticated

    private fun toTokenSet(response: TokenResponse): TokenSet {
        // SERVER.md §1.4: expires_in は秒。TokenSet は端末ローカル時刻（epochMillis）で
        // 失効時刻を保持する規約（TokenSet.kt の KDoc 参照）。
        val expiresAt = clock.millis() + response.expiresIn * 1000L
        return TokenSet(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            accessTokenExpiresAtEpochMillis = expiresAt,
        )
    }

    private fun mapExchangeException(e: FeedmanException): ExchangeResult {
        return if (e.code == FeedmanException.CODE_NETWORK_ERROR) {
            ExchangeResult.NetworkFailure(e)
        } else {
            // Req 1.3: サーバーエラーで TokenStore に書き込まない（書き込み箇所は成功パスのみ）。
            ExchangeResult.ServerError(code = e.code, httpStatus = e.httpStatus, message = e.errorMessage)
        }
    }

    private suspend fun mapRefreshException(e: FeedmanException): RefreshResult {
        if (e.code == FeedmanException.CODE_NETWORK_ERROR) {
            // Req 2.5: ネットワーク失敗時は TokenStore を維持。
            return RefreshResult.NetworkFailure(e)
        }
        if (e.code == CODE_INVALID_REFRESH_TOKEN || e.httpStatus == 401) {
            // Req 2.4: INVALID_REFRESH_TOKEN → TokenStore 消去 + AuthRequired。
            tokenStore.clear()
            isAuthenticated.value = false
            return RefreshResult.AuthRequired
        }
        // それ以外のサーバーエラー（5xx 等）は TokenStore を保持したまま透過。
        return RefreshResult.ServerError(code = e.code, httpStatus = e.httpStatus, message = e.errorMessage)
    }

    companion object {
        /** SERVER.md §1.3 `POST /api/auth/refresh` の 401 応答で返される `error.code`。 */
        const val CODE_INVALID_REFRESH_TOKEN: String = "INVALID_REFRESH_TOKEN"
    }
}
