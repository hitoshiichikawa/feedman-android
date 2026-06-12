package com.feedman.android.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 認証必須 API への `Authorization: Bearer <access_token>` 付与を担う OkHttp Interceptor
 * （Issue #21 / Req 4）。
 *
 * ## 振る舞い
 *
 * 1. リクエスト URL が「認証不要エンドポイント」（[isAuthExemptPath]）なら Bearer を付与しない
 *    （Req 4.3）。token 交換 / refresh のリクエストはアクセストークン未保持で実行されるため、
 *    また refresh 経路に Bearer を付与すると 401 でループする恐れがあるため。
 * 2. それ以外で TokenStore にアクセストークンが保存されていればヘッダに `Authorization: Bearer <token>` を付与
 *    （Req 4.1）。
 * 3. アクセストークンが保存されていなければヘッダを付与せずに送信する（Req 4.2）。
 *
 * ## runBlocking の使用について
 *
 * OkHttp の Interceptor は同期 API であり、TokenStore は suspend で I/O する設計のため、
 * `runBlocking { tokenStore.read() }` で値を取り出す。これは OkHttp が dispatcher 上の
 * 専用スレッドで interceptor を実行する前提に依存しており、UI スレッドからの直接呼び出しは
 * 想定していない（Repository / ViewModel はすべて Coroutine ベースで API を呼ぶため、
 * 実行スレッドはバックグラウンドの OkHttp Dispatcher）。
 *
 * runBlocking の範囲は **最小限**（[TokenStore.read] 1 回のみ）。decode / 例外ハンドリングは
 * 行わない（TokenStoreException は throw のまま透過させ、上位レイヤで OkHttp が IOException 扱い）。
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isAuthExemptPath(request.url.encodedPath)) {
            // 認証不要エンドポイント（token 交換 / refresh）: Bearer 付与しない（Req 4.3）。
            return chain.proceed(request)
        }
        val accessToken: String? = runBlocking { tokenStore.read()?.accessToken }
        if (accessToken.isNullOrBlank()) {
            // 未ログイン状態: Bearer 付与せず送信（Req 4.2）。
            return chain.proceed(request)
        }
        val authorized = request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(authorized)
    }

    /**
     * 認証不要エンドポイントの判定（Req 4.3）。SERVER.md §1.3 で Bearer 不要と定められている
     * `/api/auth/token` と `/api/auth/refresh` のみが対象。`/api/auth/revoke` は Bearer 認証下なので
     * 含めない。
     *
     * 末尾スラッシュや subpath への誤マッチを避けるため、完全一致で判定する。
     */
    private fun isAuthExemptPath(encodedPath: String): Boolean {
        // OkHttp の encodedPath は常に "/" で始まる。
        val normalized = encodedPath.trimEnd('/')
        return normalized == "/api/auth/token" || normalized == "/api/auth/refresh"
    }
}
