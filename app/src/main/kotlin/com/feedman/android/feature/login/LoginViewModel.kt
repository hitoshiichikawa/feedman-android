package com.feedman.android.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.auth.AuthCallbackDispatcher
import com.feedman.android.core.auth.AuthCallbackError
import com.feedman.android.core.auth.AuthCallbackParser
import com.feedman.android.core.auth.AuthCallbackResult
import com.feedman.android.core.auth.AuthRepository
import com.feedman.android.core.auth.ExchangeResult
import com.feedman.android.core.auth.PkceGenerator
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.network.FeedmanException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ログイン画面 ViewModel（Issue #23 / Req 1〜5 / NFR 1〜3）。
 *
 * SERVER.md §1.2 のネイティブ OAuth フローを駆動する:
 *
 * 1. [startGoogleLogin]: PKCE pair を生成 → `code_verifier` を [SavedStateHandle] に保持
 *    （プロセス再生成に追従 / Req 2.4） → 開始 URL を `_openCustomTabs` で 1 回 emit
 *    （Composable が `collectAsStateWithLifecycle` で受け取り Custom Tabs を起動）
 *    （Req 2.1〜2.3 / 2.5: 進行中は重複起動を抑止）
 * 2. [onDeepLink]: feedman://auth/callback ディープリンクを受領 → AuthCallbackParser で
 *    `auth_code` を抽出（Req 3.1 / 5.3 / NFR 2.1〜2.2）→ AuthRepository.exchange を呼び出す
 *    （Req 3.2）。成功時は code_verifier を破棄（Req 3.4 / NFR 1.3）し、SessionStateProvider
 *    経由で AppShell が自動的に LoggedIn に遷移する（Req 3.3）。
 * 3. 失敗時は [LoginUiState.Error] に遷移し、再押下で [startGoogleLogin] が新しい PKCE pair を
 *    生成してフローを最初からやり直す（Req 4.1〜4.4）。
 *
 * ## 状態モデル
 *
 * - [LoginUiState.Idle]: 未押下 / 押下前の初期状態（Req 1.1）
 * - [LoginUiState.LaunchingCustomTabs]: Custom Tabs 起動中（Custom Tabs を閉じた / コールバックが
 *   来た時点で Idle / Exchanging / Error / 成功時は自動シェル遷移へ）
 * - [LoginUiState.Exchanging]: `AuthRepository.exchange` 応答待ち（Req 3.5）
 * - [LoginUiState.Error]: 交換失敗（業務エラー / ネットワーク / リンク不正）。
 *   再押下で新しい PKCE pair を生成して Idle に戻る経路を取らずに直接 Custom Tabs を起動する。
 *
 * ## SavedStateHandle の使い方（Req 2.4 / NFR 1.2）
 *
 * - キー [KEY_CODE_VERIFIER] に文字列で保持。Bundle に直接書かれるため `process death` で
 *   復元される（Android Lifecycle 規約）。
 * - 平文の long-term ストアではない（プロセス常駐のみ / NFR 1.2）。
 * - 成功 / 失敗のいずれが確定しても [clearStoredVerifier] で削除する（NFR 1.3）。
 *
 * ## Composable との接続
 *
 * - [uiState]: 画面描画用の StateFlow。
 * - [openCustomTabs]: 1 回限りの起動指示を流す SharedFlow（URL 文字列）。Composable は
 *   `collect` して [com.feedman.android.core.ui.LinkOpener.open] を呼ぶ。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val pkceGenerator: PkceGenerator,
    private val authRepository: AuthRepository,
    private val appConfig: AppConfig,
    authCallbackDispatcher: AuthCallbackDispatcher,
) : ViewModel() {

    init {
        // MainActivity が onNewIntent / 起動時 intent で配信したディープリンク URI を購読し、
        // [onDeepLink] に転送する。replay = 1 のため、ViewModel が後から起動された場合でも
        // 最新の 1 件は受領できる（プロセス cold start で deep link 起動された場合に必要）。
        viewModelScope.launch {
            authCallbackDispatcher.intents.collect { uri ->
                onDeepLink(uri)
            }
        }
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    /** UI 描画用の状態（Req 1.1 / 3.5 / 4.x / 5.x）。 */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Custom Tabs を起動するための 1 回限りの URL emit。
     *
     * `MutableSharedFlow(replay = 0)` を採用し、`collect` 開始前に emit された値は破棄される。
     * Composable は `LaunchedEffect` 内で `collect` し、新しい起動指示が来たときだけ
     * `LinkOpener.open` を呼ぶ（同じ URL を 2 回流しても 2 回 Custom Tabs が開く挙動を意図的に保つ）。
     */
    private val _openCustomTabs = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val openCustomTabs: SharedFlow<String> = _openCustomTabs.asSharedFlow()

    /**
     * Google ログインボタン押下のエントリ（Req 2.1〜2.5）。
     *
     * - 進行中（LaunchingCustomTabs / Exchanging）なら no-op（Req 2.5 / 3.5: 二重起動・二重交換抑止）
     * - 新規 PKCE pair を生成し code_verifier を SavedStateHandle に保存（Req 2.4）
     * - AuthorizationUrlBuilder で開始 URL を組み立て、[openCustomTabs] に流す
     * - 状態を [LoginUiState.LaunchingCustomTabs] に遷移
     */
    fun startGoogleLogin() {
        val state = _uiState.value
        if (state is LoginUiState.LaunchingCustomTabs || state is LoginUiState.Exchanging) {
            // Req 2.5 / Req 3.5: 二重起動・二重交換を抑止する。
            return
        }
        val pair = pkceGenerator.generate()
        // Req 2.4 / NFR 1.2: code_verifier をプロセス再生成にまたいで保持する。
        savedStateHandle[KEY_CODE_VERIFIER] = pair.codeVerifier
        val url = AuthorizationUrlBuilder.build(
            baseUrl = appConfig.baseUrl,
            codeChallenge = pair.codeChallenge,
            method = pair.method,
        )
        _uiState.value = LoginUiState.LaunchingCustomTabs
        _openCustomTabs.tryEmit(url)
    }

    /**
     * `feedman://auth/callback?...` ディープリンクを MainActivity 経由で受領する
     * （Req 3.1 / NFR 2.1 / NFR 2.2）。
     *
     * - AuthCallbackParser で auth_code を抽出（NFR 2.1: スキーム/ホスト/パスが一致しない場合は no-op）
     * - 保持中の code_verifier と組み合わせ AuthRepository.exchange を呼ぶ（Req 3.2）
     * - 成功時は code_verifier を破棄（Req 3.4 / NFR 1.3）
     * - 失敗時は LoginUiState.Error に遷移（Req 4.1 / 4.2 / 4.4 / NFR 1.3）
     *
     * @param deepLinkUri 受領したインテントの data URI を文字列化したもの
     */
    fun onDeepLink(deepLinkUri: String) {
        val parsed = AuthCallbackParser.parse(deepLinkUri)
        if (parsed is AuthCallbackResult.Failure) {
            // NFR 2.1 / NFR 2.2 / Req 5.3: スキーム/ホスト/パス不一致は無視する。
            // auth_code が無いコールバックも exchange を呼ばない（Req 5.3 / NFR 2.2）。
            // ただし、parser が Malformed / MissingAuthCode を返した場合、Custom Tabs が
            // 完了せず閉じられたパターン（Req 5.1）と判別不可能なため、ログイン画面の
            // 状態を保持したまま no-op とする。
            return
        }
        val success = parsed as AuthCallbackResult.Success
        val authCode = success.authCode
        val codeVerifier = savedStateHandle.get<String>(KEY_CODE_VERIFIER)
        if (codeVerifier.isNullOrBlank()) {
            // Req 2.4 の保持に失敗 / 期待しないコールバック（PKCE 未生成状態でディープリンクが
            // 届いた場合）。安全側に倒し、何もしない（PKCE 検証ができないため exchange は不可）。
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Exchanging
            val result = authRepository.exchange(authCode = authCode, codeVerifier = codeVerifier)
            // Req 3.4 / NFR 1.3: 成否確定時に code_verifier を破棄
            clearStoredVerifier()
            when (result) {
                ExchangeResult.Success -> {
                    // Req 3.3: SessionStateProvider が LoggedIn を流し AppShell が
                    // 自動的にシェルに切替わる。LoginViewModel は state を Idle に戻して終了。
                    _uiState.value = LoginUiState.Idle
                }
                is ExchangeResult.NetworkFailure -> {
                    // Req 4.2: ネットワーク失敗
                    _uiState.value = LoginUiState.Error(LoginError.Network)
                }
                is ExchangeResult.ServerError -> {
                    // Req 4.1: 業務エラー（INVALID_GRANT 等）
                    _uiState.value = LoginUiState.Error(
                        LoginError.Server(
                            code = result.code,
                            httpStatus = result.httpStatus,
                            message = result.message,
                        ),
                    )
                }
            }
        }
    }

    private fun clearStoredVerifier() {
        savedStateHandle.remove<String>(KEY_CODE_VERIFIER)
    }

    companion object {
        /** SavedStateHandle に code_verifier を保存する際のキー。 */
        internal const val KEY_CODE_VERIFIER: String = "login.code_verifier"
    }
}

/**
 * ログイン画面の UI 状態（Issue #23）。
 */
sealed class LoginUiState {

    /** 初期状態（ボタン押下前 / 失敗ダイアログ無し）（Req 1.1）。 */
    data object Idle : LoginUiState()

    /**
     * Custom Tabs 起動中（Req 2.5）。
     *
     * 押下直後から、コールバックが届く or Custom Tabs が閉じられて Activity に戻るまで
     * の間継続する。本状態の間はボタンは disabled（Req 2.5）。
     */
    data object LaunchingCustomTabs : LoginUiState()

    /**
     * AuthRepository.exchange 応答待ち（Req 3.5）。本状態の間はボタンは disabled。
     */
    data object Exchanging : LoginUiState()

    /** 交換失敗（Req 4.1 / 4.2）。本状態でも再押下は許可される（Req 4.3）。 */
    data class Error(val error: LoginError) : LoginUiState()
}

/**
 * [LoginUiState.Error] の理由（Req 4.1 / 4.2）。
 */
sealed class LoginError {

    /** ネットワーク失敗（Req 4.2）。 */
    data object Network : LoginError()

    /**
     * サーバーが業務エラー応答を返した（Req 4.1）。INVALID_GRANT を含む。
     *
     * @property code SPEC §4.3 の `error.code`
     * @property httpStatus HTTP ステータスコード
     * @property message SPEC §4.3 の `error.message`（UI 表示の元文言）
     */
    data class Server(
        val code: String,
        val httpStatus: Int?,
        val message: String,
    ) : LoginError() {

        /** [code] が `INVALID_GRANT` か（テスト・UI 文言判断用）。 */
        fun isInvalidGrant(): Boolean = code == CODE_INVALID_GRANT
    }

    companion object {
        /** SERVER.md §1.3 の `error.code`。 */
        const val CODE_INVALID_GRANT: String = "INVALID_GRANT"
    }
}

/**
 * [FeedmanException] のヘルパー的アクセサ（VM 外部のテストから ServerError.code を見るとき用）。
 * 本ファイルでは未使用だが、将来の Reviewer 経由テスト追加で参照する余地を残す。
 */
internal fun LoginError.Server.toExceptionForLog(): Throwable =
    FeedmanException(code = code, errorMessage = message, httpStatus = httpStatus)
