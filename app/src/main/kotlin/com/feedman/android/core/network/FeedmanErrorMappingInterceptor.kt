package com.feedman.android.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 非 2xx HTTP レスポンスを [FeedmanException] に変換する OkHttp Interceptor（Req 3.2 / 3.3 / 3.4）。
 *
 * 設計判断: 「変換層を OkHttp 拡張点と独立した位置に配置する」要件（Req 4.4）を満たしつつ、
 * 認証 interceptor / authenticator の有無に関わらず常に最後に動作させたい。よって本
 * Interceptor は **application interceptor チェーンの最終段**（== 最も chain.proceed() に近い側、
 * = `OkHttpClient.Builder.addInterceptor` のリストに **最初に追加** されたもの）として配置する。
 * これにより `AuthInterceptor`（後続 Issue #21）が `chain.request().newBuilder()` で Bearer を
 * 付与した後、本 Interceptor がそのリクエストの実行結果を見ることになる。
 *
 * 変換方針:
 * - 2xx 応答: そのまま `Response` を返し、Retrofit converter に decode を委ねる（Req 3.1）。
 * - 非 2xx 応答: body 文字列と `Retry-After` ヘッダを読み出し、[FeedmanErrorMapper.fromHttpResponse]
 *   で [FeedmanException] を構築する。OkHttp Interceptor の契約上 throwable は `IOException`
 *   のみ許可されるため、`FeedmanException` を `cause` に持つ [FeedmanIOException] でラップして
 *   投げる。後段の [FeedmanApiInvocationHandler] が unwrap する。
 *
 * 本 Interceptor は IOException 自体を変換しない。connect/read 失敗等の IOException は
 * 透過させ、suspend 呼び出し側で [FeedmanErrorMapper.fromIoException] が呼ばれる
 * （Req 3.5 / 3.6 の責務は [FeedmanApiInvocationHandler] と分担）。
 */
internal class FeedmanErrorMappingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) {
            return response
        }
        // peekBody で body をログ / 解析用に複製する代わりに、`source().use { ... }` で本体を
        // 一度だけ読み切り、後段の Retrofit converter が読まないことを前提とする
        // （非 2xx で例外を投げるため converter は呼ばれない）。
        val bodyString: String? = try {
            response.body?.string()
        } catch (e: IOException) {
            // body 読み出し失敗は cause として保持し、フォールバックの UNKNOWN_ERROR に倒す。
            response.close()
            val ex = FeedmanErrorMapper.fromHttpResponse(
                httpStatus = response.code,
                body = null,
                retryAfterHeader = response.header("Retry-After"),
                cause = e,
            )
            throw FeedmanIOException(ex)
        }
        response.close()
        val feedmanException = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = response.code,
            body = bodyString,
            retryAfterHeader = response.header("Retry-After"),
            cause = null,
        )
        throw FeedmanIOException(feedmanException)
    }
}

/**
 * OkHttp Interceptor の throw 契約（IOException のみ可）に従いつつ、[FeedmanException] を
 * 上位レイヤーへ運ぶための薄いラッパー。
 *
 * [FeedmanApiInvocationHandler] が suspend 呼び出しの catch 節でこれを unwrap し、
 * 呼び出し側には [FeedmanException] を直接 throw する。アプリケーションコードから本クラスが
 * 見えることは無く、`internal` 可視性に閉じる。
 *
 * @property feedmanException ラップ対象の例外。`Throwable.cause` 経由でも同じインスタンスが取得できる。
 */
internal class FeedmanIOException(
    val feedmanException: FeedmanException,
) : IOException(feedmanException.errorMessage, feedmanException)
