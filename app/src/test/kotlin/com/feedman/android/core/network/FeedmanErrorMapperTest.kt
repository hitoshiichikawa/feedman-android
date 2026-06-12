package com.feedman.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [FeedmanErrorMapper] が SPEC §4.3 のエラーボディおよび I/O 失敗を
 * [FeedmanException] に変換することを検証する単体テスト群。
 *
 * 各テストは Issue #16 の requirements.md の AC ID（Req 1.x / 2.x / 3.x / 4.x、NFR 1.x / 2.x）と
 * 1 対 1 に対応する。
 */
class FeedmanErrorMapperTest {

    // --- Requirement 1: 統一エラーボディの型付きデコード ---

    @Test
    fun `Req 1-1 maps standard 4xx body to FeedmanException with full fields`() {
        // Arrange
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 400, body = body)

        // Assert
        assertEquals("INVALID_REQUEST", ex.code)
        assertEquals("リクエストパラメータが不正です。", ex.errorMessage)
        assertEquals("リクエストパラメータが不正です。", ex.message)
        assertEquals("client", ex.category)
        assertEquals("fix_input", ex.action)
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 1-2 preserves original HTTP status code on FeedmanException`() {
        // Arrange
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 503, body = body)

        // Assert
        assertEquals(503, ex.httpStatus)
    }

    @Test
    fun `Req 1-3 ignores unknown top-level and details fields without throwing`() {
        // Arrange: trace_id（root の未知フィールド）と details.feed_id（details の未知フィールド）を含む fixture
        val body = FixtureLoader.load("error_with_unknown_fields.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 404, body = body)

        // Assert
        assertEquals("FEED_NOT_FOUND", ex.code)
        assertEquals("指定されたフィードが見つかりません。", ex.errorMessage)
        assertEquals("client", ex.category)
        assertEquals("go_back", ex.action)
        // details.retry_after_seconds は null だが他フィールドは無視されること
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 1-4 returns FeedmanException for all non-2xx HTTP responses including 500`() {
        // Arrange: 5xx 系でも同一型 (FeedmanException) で返ることを確認する
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = body)

        // Assert
        // 単一例外型として FeedmanException が返る（戻り値型自体が FeedmanException でもあるが、
        // RuntimeException としても扱えること = 上位 try/catch を一本化できることを併せて検証する）
        assertTrue(
            "Network Error Layer は全 4xx/5xx で FeedmanException を返す（RuntimeException 互換）",
            (ex as RuntimeException) is FeedmanException,
        )
        assertEquals(500, ex.httpStatus)
    }

    // --- Requirement 2: details.retry_after_seconds の取り出し ---

    @Test
    fun `Req 2-1 extracts retry_after_seconds from details when present as integer`() {
        // Arrange
        val body = FixtureLoader.load("error_feed_cooldown.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 429, body = body)

        // Assert
        assertEquals("FEED_COOLDOWN", ex.code)
        assertEquals(30, ex.retryAfterSeconds)
        assertEquals("rate_limit", ex.category)
    }

    @Test
    fun `Req 2-2 returns null retryAfterSeconds when details lacks retry_after_seconds`() {
        // Arrange: details オブジェクトはあるが retry_after_seconds キーが無い
        val body = FixtureLoader.load("error_details_without_retry_after.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 400, body = body)

        // Assert
        assertEquals("VALIDATION_FAILED", ex.code)
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 2-2 returns null retryAfterSeconds when error body has no details object`() {
        // Arrange: details オブジェクト自体が無い
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 400, body = body)

        // Assert
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 2-3 uses Retry-After header when body lacks retry_after_seconds`() {
        // Arrange
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = 429,
            body = body,
            retryAfterHeader = "120",
        )

        // Assert
        assertEquals(120, ex.retryAfterSeconds)
    }

    @Test
    fun `Req 2-3 body details takes precedence over Retry-After header`() {
        // Arrange: body には retry_after_seconds=30、ヘッダには 999
        val body = FixtureLoader.load("error_feed_cooldown.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = 429,
            body = body,
            retryAfterHeader = "999",
        )

        // Assert
        // body 優先で 30 を返す
        assertEquals(30, ex.retryAfterSeconds)
    }

    @Test
    fun `Req 2-4 sets retryAfterSeconds to null when Retry-After header is not an integer`() {
        // Arrange: HTTP-date 形式は v1 でサポート対象外（SPEC §4.3）
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = 429,
            body = body,
            retryAfterHeader = "Wed, 21 Oct 2026 07:28:00 GMT",
        )

        // Assert
        // 例外を投げず null
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 2-4 sets retryAfterSeconds to null when Retry-After header is blank`() {
        // Arrange
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = 429,
            body = body,
            retryAfterHeader = "   ",
        )

        // Assert
        assertNull(ex.retryAfterSeconds)
    }

    // --- Requirement 3: ボディ欠落・破損のフォールバック ---

    @Test
    fun `Req 3-1 returns UNKNOWN_ERROR when body is empty string`() {
        // Arrange & Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = "")

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
    }

    @Test
    fun `Req 3-1 returns UNKNOWN_ERROR when body is null`() {
        // Arrange & Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 502, body = null)

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
        assertEquals(502, ex.httpStatus)
    }

    @Test
    fun `Req 3-2 returns UNKNOWN_ERROR when body is malformed JSON without throwing`() {
        // Arrange: 不完全 JSON
        val body = FixtureLoader.load("error_malformed.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = body)

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
        // 元の SerializationException が cause として保持される（NFR 1.1）
        assertNotNull(ex.cause)
    }

    @Test
    fun `Req 3-2 returns UNKNOWN_ERROR when error field is missing`() {
        // Arrange
        val body = FixtureLoader.load("error_missing_error_field.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = body)

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
    }

    @Test
    fun `Req 3-2 returns UNKNOWN_ERROR when error field is wrong type`() {
        // Arrange: error が string になっている
        val body = FixtureLoader.load("error_wrong_type.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = body)

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
    }

    @Test
    fun `Req 3-3 synthetic UNKNOWN_ERROR preserves original HTTP status and provides non-empty message`() {
        // Arrange & Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 418, body = "<<not json>>")

        // Assert
        assertEquals(418, ex.httpStatus)
        assertTrue(
            "fallback message must be non-empty for UI display",
            ex.errorMessage.isNotBlank(),
        )
    }

    @Test
    fun `Req 3-4 synthetic UNKNOWN_ERROR sets category action and retryAfterSeconds to null`() {
        // Arrange & Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 500, body = "")

        // Assert
        assertNull(ex.category)
        assertNull(ex.action)
        assertNull(ex.retryAfterSeconds)
    }

    // --- Requirement 4: IOException マッピング ---

    @Test
    fun `Req 4-1 maps IOException to NETWORK_ERROR FeedmanException`() {
        // Arrange
        val io = IOException("connection refused")

        // Act
        val ex = FeedmanErrorMapper.fromIoException(io)

        // Assert
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, ex.code)
    }

    @Test
    fun `Req 4-2 sets httpStatus to null for NETWORK_ERROR`() {
        // Arrange
        val io = IOException("network unreachable")

        // Act
        val ex = FeedmanErrorMapper.fromIoException(io)

        // Assert
        assertNull(ex.httpStatus)
    }

    @Test
    fun `Req 4-3 sets category action and retryAfterSeconds to null for NETWORK_ERROR`() {
        // Arrange
        val io = IOException("timed out")

        // Act
        val ex = FeedmanErrorMapper.fromIoException(io)

        // Assert
        assertNull(ex.category)
        assertNull(ex.action)
        assertNull(ex.retryAfterSeconds)
    }

    @Test
    fun `Req 4-4 provides non-empty fallback message for NETWORK_ERROR`() {
        // Arrange
        val io = IOException("dns failure")

        // Act
        val ex = FeedmanErrorMapper.fromIoException(io)

        // Assert
        assertTrue(
            "NETWORK_ERROR fallback message must be non-empty for UI display",
            ex.errorMessage.isNotBlank(),
        )
    }

    // --- NFR 1.1: cause の保持 ---

    @Test
    fun `NFR 1-1 preserves IOException as cause on NETWORK_ERROR`() {
        // Arrange
        val io = IOException("socket reset")

        // Act
        val ex = FeedmanErrorMapper.fromIoException(io)

        // Assert
        assertSame(io, ex.cause)
    }

    @Test
    fun `NFR 1-1 preserves provided cause on http response mapping`() {
        // Arrange
        val originating = RuntimeException("Retrofit HttpException")
        val body = FixtureLoader.load("error_invalid_request.json")

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(
            httpStatus = 400,
            body = body,
            cause = originating,
        )

        // Assert
        assertSame(originating, ex.cause)
    }

    // --- NFR 2.1: code は free-form string ---

    @Test
    fun `NFR 2-1 accepts any new code string without code changes`() {
        // Arrange: 仕様未定義の架空 code でも透過
        val body = """
            {"error":{"code":"BRAND_NEW_FUTURE_CODE","message":"未知のコードでも透過する","category":"x","action":"y"}}
        """.trimIndent()

        // Act
        val ex = FeedmanErrorMapper.fromHttpResponse(httpStatus = 400, body = body)

        // Assert
        assertEquals("BRAND_NEW_FUTURE_CODE", ex.code)
    }
}
