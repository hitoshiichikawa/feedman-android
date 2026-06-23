package com.feedman.android.feature.login

import java.net.URLEncoder

/**
 * `flow=native` の Google OAuth 開始 URL を組み立てる純粋関数（Issue #23 / Req 2.1, 2.2, 2.3）。
 *
 * `design/SERVER.md` §1.2 で定義されたネイティブ OAuth フローでは、以下のクエリパラメータを
 * 持つ `<baseUrl>/auth/google/login` を Custom Tabs で開く必要がある:
 *
 * - `flow=native`: ネイティブフロー指定（サーバーが Cookie 発行ではなくアプリスキームへ
 *   リダイレクトする / Req 2.3）
 * - `code_challenge=<challenge>`: PKCE S256 challenge（Req 2.2）
 * - `code_challenge_method=S256`: challenge 方式（PkceGenerator.METHOD_S256 と一致）
 *
 * 本クラスは Android 依存を持たないため JVM 単体テストで完全に検証できる。`android.net.Uri`
 * を使う代わりに `URLEncoder.encode` でクエリパラメータをパーセントエンコードする
 * （Android 上でも同等の結果を返す）。
 */
object AuthorizationUrlBuilder {

    /** SERVER.md §1.2 の OAuth 開始エンドポイントのパス。 */
    const val PATH_AUTH_GOOGLE_LOGIN: String = "/auth/google/login"

    /** ネイティブフローを示すクエリパラメータの値（Req 2.3）。 */
    const val FLOW_NATIVE: String = "native"

    /** PKCE S256 のクエリパラメータ値。 */
    const val CODE_CHALLENGE_METHOD_S256: String = "S256"

    /**
     * `<baseUrl>/auth/google/login?flow=native&code_challenge=<challenge>&code_challenge_method=S256`
     * を組み立てる。
     *
     * - [baseUrl] 末尾の `/` 重複は除去（`https://example.com/` でも `https://example.com` でも
     *   同じ結果を返す）
     * - [codeChallenge] / [method] はパーセントエンコードしてから URL に埋め込む
     *
     * @param baseUrl SERVER.md §1.2 のサーバー base URL（例: `https://stg-feed.market-river.net`）
     * @param codeChallenge PKCE S256 challenge（[com.feedman.android.core.auth.PkcePair.codeChallenge]）
     * @param method challenge method。既定は `S256`。
     * @return Custom Tabs に渡す完全な URL 文字列
     * @throws IllegalArgumentException [baseUrl] が空文字の場合
     */
    fun build(
        baseUrl: String,
        codeChallenge: String,
        method: String = CODE_CHALLENGE_METHOD_S256,
    ): String {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(codeChallenge.isNotBlank()) { "codeChallenge must not be blank" }
        val trimmedBase = baseUrl.trimEnd('/')
        val params = listOf(
            "flow" to FLOW_NATIVE,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to method,
        )
        val query = params.joinToString(separator = "&") { (key, value) ->
            "$key=${encode(value)}"
        }
        return "$trimmedBase$PATH_AUTH_GOOGLE_LOGIN?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
