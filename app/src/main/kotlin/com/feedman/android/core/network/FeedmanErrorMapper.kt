package com.feedman.android.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * HTTP 非 2xx レスポンス（ステータス + ボディ + 任意の `Retry-After` ヘッダ）および
 * IOException を [FeedmanException] に一元変換する純粋関数群。
 *
 * 本オブジェクトは Retrofit / OkHttp に直接依存しない（Issue #17 で Retrofit との配線が
 * 行われる際に CallAdapter / Interceptor からそれぞれ呼び出される想定）。
 *
 * すべての変換は同期的かつ副作用なしで行う。ログ出力は NFR 1.2 によりここでは行わない
 * （上位レイヤーで `code` / `httpStatus` / `category` のみログするのが望ましい）。
 */
object FeedmanErrorMapper {

    /**
     * 未知フィールドを許容する Json 構成。
     * SPEC §4.3 への将来的なフィールド追加（NFR 2.2）と Issue #15 で確立した model 層の方針に整合する。
     */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * HTTP 非 2xx レスポンスを [FeedmanException] に変換する。
     *
     * デコード方針:
     * - ボディが SPEC §4.3 の正規スキーマに一致するなら、その内容を例外に展開する（Req 1.1）。
     * - 未知フィールドは無視する（Req 1.3 / NFR 2.2）。
     * - ボディが空、または JSON として解釈できない / スキーマ不一致なら、合成 `code = UNKNOWN_ERROR`
     *   でフォールバックする（Req 3.1 / Req 3.2）。
     * - `details.retry_after_seconds` が無く [retryAfterHeader] が整数解釈できる場合のみ、
     *   ヘッダ由来値を採用する（Req 2.3）。ヘッダがパース不能な場合は `null` のまま（Req 2.4）。
     *
     * @param httpStatus 元レスポンスの HTTP ステータスコード（4xx / 5xx）。
     * @param body 元レスポンスボディ文字列。`null` / 空文字列の場合はフォールバック扱い。
     * @param retryAfterHeader HTTP `Retry-After` ヘッダ値。未提供時は `null`。
     * @param cause 上位レイヤーで掴んでいる originating throwable（Retrofit の HttpException など）。
     * @return SPEC §4.3 の `code` を保持した [FeedmanException]（フォールバック時は合成 `code`）。
     */
    fun fromHttpResponse(
        httpStatus: Int,
        body: String?,
        retryAfterHeader: String? = null,
        cause: Throwable? = null,
    ): FeedmanException {
        if (body.isNullOrBlank()) {
            return synthesizeUnknown(httpStatus = httpStatus, cause = cause)
        }
        val parsed: FeedmanErrorBody = try {
            json.decodeFromString(FeedmanErrorBody.serializer(), body)
        } catch (parseFailure: SerializationException) {
            return synthesizeUnknown(httpStatus = httpStatus, cause = cause ?: parseFailure)
        } catch (parseFailure: IllegalArgumentException) {
            // kotlinx.serialization は型不一致時に IllegalArgumentException を投げる場合がある
            return synthesizeUnknown(httpStatus = httpStatus, cause = cause ?: parseFailure)
        }

        val retryAfter = parsed.error.details?.retryAfterSeconds
            ?: parseRetryAfterHeader(retryAfterHeader)

        return FeedmanException(
            code = parsed.error.code,
            errorMessage = parsed.error.message,
            category = parsed.error.category,
            action = parsed.error.action,
            retryAfterSeconds = retryAfter,
            httpStatus = httpStatus,
            cause = cause,
        )
    }

    /**
     * I/O 失敗（[IOException]）を [FeedmanException] に変換する（Req 4.1〜4.4）。
     *
     * `code = NETWORK_ERROR` / `httpStatus = null` / `category` / `action` / `retryAfterSeconds`
     * すべて `null` の合成例外として返す。
     *
     * @param cause 元の IOException。`Throwable.cause` として保持する（NFR 1.1）。
     * @return [FeedmanException]（[FeedmanException.CODE_NETWORK_ERROR]）。
     */
    fun fromIoException(cause: IOException): FeedmanException {
        return FeedmanException(
            code = FeedmanException.CODE_NETWORK_ERROR,
            errorMessage = FeedmanException.FALLBACK_NETWORK_MESSAGE,
            category = null,
            action = null,
            retryAfterSeconds = null,
            httpStatus = null,
            cause = cause,
        )
    }

    /**
     * `Retry-After` ヘッダ値を整数秒として解釈する（Req 2.3 / 2.4）。
     *
     * 仕様上 HTTP 日付（RFC1123）も受け付け得るが、本 v1 ではサーバーが秒数のみを返す
     * 前提（SPEC §4.3）。整数として解釈できない場合は `null` を返し、例外は投げない。
     */
    private fun parseRetryAfterHeader(headerValue: String?): Int? {
        if (headerValue.isNullOrBlank()) return null
        return headerValue.trim().toIntOrNull()
    }

    /**
     * `UNKNOWN_ERROR` フォールバック例外を生成する（Req 3.1〜3.4）。
     *
     * `category` / `action` / `retryAfterSeconds` をすべて `null` にし、`message` には
     * フォールバック UI 表示に耐える非空文字列を入れる。
     */
    private fun synthesizeUnknown(httpStatus: Int, cause: Throwable?): FeedmanException {
        return FeedmanException(
            code = FeedmanException.CODE_UNKNOWN_ERROR,
            errorMessage = FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
            category = null,
            action = null,
            retryAfterSeconds = null,
            httpStatus = httpStatus,
            cause = cause,
        )
    }
}
