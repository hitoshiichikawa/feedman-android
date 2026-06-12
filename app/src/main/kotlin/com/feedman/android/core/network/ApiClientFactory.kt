package com.feedman.android.core.network

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * OkHttp + Retrofit + kotlinx.serialization を組み立てて [FeedmanApi] を生成する factory。
 *
 * - JSON は `ignoreUnknownKeys = true`（Req 2.2 / 2.3）。
 * - BASE_URL は引数 [baseUrl] で注入する（呼び出し側で `AppConfig.baseUrl` =
 *   `BuildConfig.BASE_URL` を渡す）。コードに固定 URL を埋め込まない（NFR 2.2）。
 * - 追加 interceptor / authenticator は外部から注入可能（Req 4.1 / 4.2 / 4.3）。
 *   後続 Issue #21 / #22 で AuthInterceptor / TokenAuthenticator を後付けする想定。
 * - エラー変換層（[FeedmanErrorMappingInterceptor] + [FeedmanApiProxy]）は OkHttp 拡張点
 *   とは独立した位置に配置される（Req 4.4）。
 * - 戻り値の [FeedmanApi] は dynamic proxy で包まれており、suspend 関数呼び出しから
 *   直接 [FeedmanException] が throw される（Req 3.2 / 3.3 / 3.5 / 3.6）。
 *
 * 本 factory は **副作用なし・状態を持たない**。同じ入力で再生成された FeedmanApi は
 * 同じエンドポイント契約で動作する（Req 2.6）。
 */
object ApiClientFactory {

    private const val MEDIA_TYPE_JSON = "application/json"

    /**
     * 全 API レスポンス / リクエストで共有する JSON 構成。
     *
     * - `ignoreUnknownKeys = true`: SPEC §4 へのフィールド追加で decode が壊れないようにする（Req 2.2）。
     * - `explicitNulls = false`: `null` フィールドをエンコード時に **完全に省略** する。
     *   `PUT /api/items/{id}/state` の partial update（`{ is_read?, is_starred? }`）を
     *   表現するための要件（Issue #35 Req 2.2 / 2.3）。
     *   デコード側では、JSON 側のキー欠落は対応するプロパティのデフォルト値（本リポジトリの
     *   nullable フィールドはすべて `= null` 既定）にフォールバックするため、既存レスポンスの
     *   `feed_favicon_url` / `hatebu_fetched_at` / `error_message` などの decode 挙動は不変。
     * - 既定の null 扱い（kotlinx.serialization のデフォルト挙動 = `null` を nullable プロパティへ
     *   マップ）で SPEC §4.4 の `feed_favicon_url` / `error_message` / `since_time` などに対応
     *   できることを Issue #15 で確認済み（Req 2.3）。
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * [FeedmanApi] を生成する。
     *
     * @param baseUrl API ベース URL（例: `BuildConfig.BASE_URL`）。末尾 `/` の有無は
     *   Retrofit が吸収するが、慣習として `/` 終端を推奨。
     * @param additionalInterceptors 追加 application interceptor。OkHttp の `addInterceptor`
     *   呼び出し順に従ってチェーンされる（先頭ほど chain.proceed() に近い）。Req 4.1 / 4.2。
     * @param authenticator 401 等の認証チャレンジを処理する authenticator。null の場合は付与しない。
     *   Req 4.3。
     * @return FeedmanException 変換層が組み込まれた [FeedmanApi] インスタンス。
     */
    fun create(
        baseUrl: String,
        additionalInterceptors: List<Interceptor> = emptyList(),
        authenticator: Authenticator? = null,
    ): FeedmanApi {
        val okHttpClient = buildOkHttpClient(
            additionalInterceptors = additionalInterceptors,
            authenticator = authenticator,
        )
        val retrofit = buildRetrofit(baseUrl = baseUrl, okHttpClient = okHttpClient)
        val rawApi = retrofit.create(FeedmanApi::class.java)
        return FeedmanApiProxy.wrap(rawApi)
    }

    /**
     * OkHttp クライアントを構築する。
     *
     * Interceptor 配線順序（Req 4.1 / 4.4 と整合）:
     *
     * OkHttp の application interceptor チェーンは `addInterceptor` した順に並び、
     * 最初に追加された interceptor が最初に `intercept(chain)` を呼ばれ、最後に追加された
     * interceptor が `chain.proceed()` でネットワーク段に最も近い位置に来る
     * （req は先頭から順に渡り、response は逆順に戻る）。
     *
     * 1. [FeedmanErrorMappingInterceptor] を **最初** に addInterceptor する。
     *    こうすると、レスポンス経路ではこれが最後に評価される = 後段で追加される
     *    [additionalInterceptors]（例: AuthInterceptor がリクエストに Bearer を付与する）
     *    の処理が終わった後のレスポンスを見て、非 2xx を [FeedmanIOException] に変換する。
     * 2. additionalInterceptors を順序を保ったまま追加する（Req 4.1）。
     */
    private fun buildOkHttpClient(
        additionalInterceptors: List<Interceptor>,
        authenticator: Authenticator?,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
        // 順序: 先頭ほど proceed() に近い = レスポンス段で最初に評価される
        builder.addInterceptor(FeedmanErrorMappingInterceptor())
        for (interceptor in additionalInterceptors) {
            builder.addInterceptor(interceptor)
        }
        if (authenticator != null) {
            builder.authenticator(authenticator)
        }
        return builder.build()
    }

    private fun buildRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
        val contentType = MEDIA_TYPE_JSON.toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
