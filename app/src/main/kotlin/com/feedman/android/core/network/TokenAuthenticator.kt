package com.feedman.android.core.network

import com.feedman.android.core.auth.AuthRepository
import com.feedman.android.core.auth.RefreshResult
import com.feedman.android.core.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 401 を受けた API 呼び出しを「refresh → 元リクエスト 1 回再試行」で透過的に復旧させる
 * OkHttp [Authenticator]（Issue #22 / requirements.md）。
 *
 * ## 振る舞い（受け入れ基準対応）
 *
 * 1. **再試行は 1 回限り**（Req 1.4 / NFR 1.1 / NFR 1.2）: 元リクエスト → 401 → refresh →
 *    再試行 → 再度 401 の場合、authenticator は null を返して 401 を呼び出し元へ伝搬する。
 *    再試行回数は [responseCount] で `Response.priorResponse` のチェーン長を数えて判定する。
 *    既に 1 回再試行済みの 401 が来たら null（再試行しない）。
 * 2. **認証エンドポイント自身は対象外**（refresh 失敗の 401 で再帰的にループしないため）:
 *    リクエスト URL のパスが `/api/auth/token` / `/api/auth/refresh` の場合は null を返す。
 *    `/api/auth/revoke` は Bearer 認証下なので対象に含めるが、refresh で 401 になった revoke は
 *    後段の 401 ループ防止（priorResponse カウント）で自然に止まる。
 * 3. **refresh は [AuthRepository] に委譲**（Req 3 単一飛行）: [AuthRepository.refresh] が
 *    Mutex + Deferred で並行 refresh を 1 回に集約する（#21 で実装済み）。本 authenticator は
 *    同期 API のため `runBlocking` で suspend を呼ぶ（OkHttp の dispatcher スレッド前提）。
 * 4. **失敗系は null 返却**（Req 1 / Req 2 / NFR 1.2）:
 *    - refresh token 未保存 → `RefreshResult.AuthRequired`
 *    - refresh で 401 / INVALID_REFRESH_TOKEN → `RefreshResult.AuthRequired` + TokenStore 消去
 *    - refresh ネットワーク失敗 → `RefreshResult.NetworkFailure`
 *    - refresh サーバーエラー → `RefreshResult.ServerError`
 *    いずれも null を返し、元の 401 応答を呼び出し元に伝播させる。トークン破棄と
 *    `observeIsAuthenticated` の `false` 遷移は AuthRepository 側で行うため、本 authenticator
 *    では重複実装しない（Req 2.2 / 2.3 を委譲）。
 *
 * ## runBlocking について
 *
 * OkHttp の Authenticator は同期 API であり、AuthRepository / TokenStore は suspend で I/O する。
 * `runBlocking` は OkHttp の専用 dispatcher スレッド上で実行される前提に依存しており、UI スレッド
 * からの直接呼び出しは想定していない（Repository / ViewModel はすべて Coroutine ベースで API を
 * 呼ぶため、実行スレッドはバックグラウンドの OkHttp Dispatcher）。同じ慣習を
 * [com.feedman.android.core.auth.AuthInterceptor] でも採用済み。
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1) 1 つの元リクエストに対する自動 refresh + 再試行は 1 回限り（Req 1.4 / NFR 1.1 / NFR 1.2）。
        //    priorResponse チェーンを数え、既に 1 回以上再試行済みなら諦める。
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            return null
        }

        // 2) 認証エンドポイント自身（token 交換 / refresh）は対象外。
        //    refresh 失敗の 401 で本 authenticator が再帰的に refresh を呼ぶと無限ループになる。
        if (isAuthEndpoint(response.request.url.encodedPath)) {
            return null
        }

        // 3) refresh を AuthRepository に委譲（並行 401 は #21 の単一飛行で 1 回に集約される / Req 3）。
        val refreshResult = runBlocking { authRepository.refresh() }
        if (refreshResult !is RefreshResult.Success) {
            // Req 2.1 / Req 2.4 / Req 3.3 / NFR 1.2 等: 再試行せず 401 を呼び出し元へ伝搬。
            // トークン破棄と observeIsAuthenticated=false 遷移は AuthRepository 側の責務（#21）。
            return null
        }

        // 4) 新しい access token で元リクエストを 1 回再試行（Req 1.1）。
        val newAccessToken = runBlocking { tokenStore.read()?.accessToken }
        if (newAccessToken.isNullOrBlank()) {
            // refresh 直後に TokenStore が空になる経路は通常存在しないが、防御的に null 返却。
            return null
        }

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$newAccessToken")
            .build()
    }

    /**
     * `Response.priorResponse` のチェーン長を数える。authenticate() が呼ばれた時点で
     * 受信中の 401 自体は引数 [response] であり、これより前に何回 retry したかを priorResponse
     * の連鎖で表現する OkHttp の慣用パターン。1 回再試行 → 401 のときは priorResponse が
     * 1 つ繋がっている = count == 1。
     */
    private fun responseCount(response: Response): Int {
        var count = 0
        var current = response.priorResponse
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }

    /**
     * 認証エンドポイント自身（401 → refresh が無意味、もしくはループになる経路）の判定。
     * AuthInterceptor の `isAuthExemptPath` と同じ完全一致ルールを採用する（末尾スラッシュ正規化）。
     */
    private fun isAuthEndpoint(encodedPath: String): Boolean {
        val normalized = encodedPath.trimEnd('/')
        return normalized == "/api/auth/token" || normalized == "/api/auth/refresh"
    }

    companion object {
        /** 1 つの元リクエストに対する自動 refresh + 再試行の上限回数（Req 1.4 / NFR 1.1）。 */
        private const val MAX_RETRY_COUNT: Int = 1

        private const val HEADER_AUTHORIZATION: String = "Authorization"
        private const val BEARER_PREFIX: String = "Bearer "
    }
}
