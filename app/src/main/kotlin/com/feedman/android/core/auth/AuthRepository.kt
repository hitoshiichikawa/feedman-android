package com.feedman.android.core.auth

import com.feedman.android.core.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * 認証境界（Issue #21 / requirements.md）。
 *
 * OAuth コールバックで受領した一時 `auth_code` を本トークンに交換し、refresh によるアクセストークン
 * 再発行とローテーション、ログアウト時の revoke、現在ログイン中ユーザー取得を担う。トークン保管庫
 * （[TokenStore]）への永続化責務は本インターフェースの実装に集約され、上位レイヤ
 * （#22 認証セッション・#23 ログイン UI など）はトークン寿命や HTTP 契約の詳細を意識せずに
 * 認証フローを駆動できる。
 *
 * ## 設計判断
 *
 * - `refresh()` は単一飛行（NFR 2.1 / NFR 2.2）。並行呼び出しは同一結果を共有する。
 * - 失敗系は型付きの [RefreshResult] / [ExchangeResult] / [CurrentUserResult] で表現し、
 *   呼び出し側が「トークン消去が必要な認証切れ」と「再試行可能なネットワーク失敗」を分岐できる。
 * - [observeIsAuthenticated] は TokenStore のアクセストークン保持有無を反映する StateFlow を返し、
 *   呼び出し側が「ログイン中 / 未ログイン」を観測できるようにする（NFR 3.1）。
 *
 * ## スコープ外
 *
 * - 401 自動 refresh + リクエストリプレイ（#22 で扱う）
 * - ログイン画面 UI / Custom Tabs 起動（#23 で扱う）
 * - セッション状態を Navigation に反映する責務（上位レイヤ）
 */
interface AuthRepository {

    /**
     * `auth_code` を本トークンに交換する（Req 1）。成功時は TokenStore に保存し、
     * [observeIsAuthenticated] の値が `true` に遷移する。
     */
    suspend fun exchange(authCode: String, codeVerifier: String): ExchangeResult

    /**
     * 保存済み refresh token でアクセストークンを再発行する（Req 2）。
     *
     * - 成功時はローテーション結果で TokenStore を **上書き保存**（Req 2.2）。
     * - `INVALID_REFRESH_TOKEN`（401）応答時は TokenStore を消去し
     *   [RefreshResult.AuthRequired] を返す（Req 2.4）。
     * - 単一飛行: 同時呼び出しは同一の最終結果を共有する（Req 2.3 / NFR 2.1 / NFR 2.2）。
     */
    suspend fun refresh(): RefreshResult

    /**
     * ログアウト要求（Req 3）。サーバーへ revoke 要求を送るが、ネットワーク失敗・サーバー
     * エラーでも TokenStore は必ず消去する（best-effort）。
     */
    suspend fun revoke()

    /**
     * 現在ログイン中ユーザーを取得する（Req 5）。
     */
    suspend fun currentUser(): CurrentUserResult

    /**
     * TokenStore のアクセストークン保持有無を反映する観測可能な状態（NFR 3.1）。
     *
     * `true` のとき「ログイン中」（access token が TokenStore に存在）。
     */
    fun observeIsAuthenticated(): StateFlow<Boolean>
}

/**
 * [AuthRepository.exchange] の結果型。
 */
sealed interface ExchangeResult {
    data object Success : ExchangeResult

    /** サーバーがエラー応答（INVALID_GRANT 等）を返した場合 / Req 1.3 */
    data class ServerError(val code: String, val httpStatus: Int?, val message: String) : ExchangeResult

    /** ネットワーク失敗 / Req 1.4 */
    data class NetworkFailure(val cause: Throwable) : ExchangeResult
}

/**
 * [AuthRepository.refresh] の結果型。
 */
sealed interface RefreshResult {
    data object Success : RefreshResult

    /**
     * 認証が必要な状態（refresh token 未保持 / INVALID_REFRESH_TOKEN 応答 / Req 2.4 / Req 2.6）。
     * 本結果が返された時点で TokenStore は空である。
     */
    data object AuthRequired : RefreshResult

    /** ネットワーク失敗（TokenStore は保持されたまま） / Req 2.5 */
    data class NetworkFailure(val cause: Throwable) : RefreshResult

    /** その他のサーバーエラー（5xx 等） */
    data class ServerError(val code: String, val httpStatus: Int?, val message: String) : RefreshResult
}

/**
 * [AuthRepository.currentUser] の結果型。
 */
sealed interface CurrentUserResult {
    data class Success(val user: User) : CurrentUserResult
    data class Failure(val code: String, val httpStatus: Int?, val message: String) : CurrentUserResult
    data class NetworkFailure(val cause: Throwable) : CurrentUserResult
}
