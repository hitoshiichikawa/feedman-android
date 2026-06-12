package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UrlValidation] の単体テスト（Issue #37 / Req 4.1, 4.2, 4.3 / NFR 3.1）。
 *
 * URL バリデーションは Android 依存を持たない純粋関数として実装されており、本テストで
 * JVM 上から直接呼び出して網羅する。
 *
 * カバーする AC:
 * - Req 4.1: http / https 以外のスキーマを拒否
 * - Req 4.2: 空文字 / 不正な構文を拒否
 */
class UrlValidationTest {

    // ── Req 4.2: 空文字 / 空白のみ ─────────────────────────────────────────

    @Test
    fun `空文字列は Blank として拒否される_Req 4_2`() {
        // Arrange / Act
        val result = UrlValidation.validate("")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(InvalidUrlReason.Blank, (result as UrlValidation.ValidationResult.Invalid).reason)
    }

    @Test
    fun `空白のみは Blank として拒否される_Req 4_2`() {
        // Arrange / Act
        val result = UrlValidation.validate("   \t\n")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(InvalidUrlReason.Blank, (result as UrlValidation.ValidationResult.Invalid).reason)
    }

    // ── Req 4.2: 不正な構文 ────────────────────────────────────────────────

    @Test
    fun `スペースを含む URL は Malformed として拒否される_Req 4_2`() {
        // Arrange / Act
        val result = UrlValidation.validate("http://example.com/with space")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.Malformed,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `host を持たない http URL は Malformed として拒否される_Req 4_2`() {
        // Arrange / Act
        val result = UrlValidation.validate("http:///path-only")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.Malformed,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    // ── Req 4.1: http/https 以外のスキーマ ────────────────────────────────

    @Test
    fun `javascript スキーマは UnsupportedScheme として拒否される_Req 4_1`() {
        // Arrange / Act
        val result = UrlValidation.validate("javascript:alert(1)")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.UnsupportedScheme,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `mailto スキーマは UnsupportedScheme として拒否される_Req 4_1`() {
        // Arrange / Act
        val result = UrlValidation.validate("mailto:user@example.com")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.UnsupportedScheme,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `file スキーマは UnsupportedScheme として拒否される_Req 4_1`() {
        // Arrange / Act
        val result = UrlValidation.validate("file:///etc/passwd")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.UnsupportedScheme,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `intent スキーマは UnsupportedScheme として拒否される_Req 4_1`() {
        // Arrange / Act
        val result = UrlValidation.validate("intent://example.com#Intent;scheme=http;end")

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Invalid)
        assertEquals(
            InvalidUrlReason.UnsupportedScheme,
            (result as UrlValidation.ValidationResult.Invalid).reason,
        )
    }

    // ── 正常系: http / https ──────────────────────────────────────────────

    @Test
    fun `http URL は Valid として受理される`() {
        // Arrange
        val url = "http://example.com/article"

        // Act
        val result = UrlValidation.validate(url)

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Valid)
        assertEquals(url, (result as UrlValidation.ValidationResult.Valid).url)
    }

    @Test
    fun `https URL は Valid として受理される`() {
        // Arrange
        val url = "https://example.com/article?id=1"

        // Act
        val result = UrlValidation.validate(url)

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Valid)
        assertEquals(url, (result as UrlValidation.ValidationResult.Valid).url)
    }

    @Test
    fun `大文字 HTTPS スキーマも Valid として受理される（case-insensitive）`() {
        // Arrange
        val url = "HTTPS://Example.COM/path"

        // Act
        val result = UrlValidation.validate(url)

        // Assert
        assertTrue(result is UrlValidation.ValidationResult.Valid)
    }
}
