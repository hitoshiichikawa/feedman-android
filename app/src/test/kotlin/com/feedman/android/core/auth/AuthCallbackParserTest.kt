package com.feedman.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AuthCallbackParser] (Issue #19 Req 2).
 *
 * All cases are exercised with `String` inputs (NFR 2.2) and the parser is
 * exercised on the JVM without `android.net.Uri` (NFR 1.1).
 */
class AuthCallbackParserTest {

    @Test
    fun `Req 2_1 canonical callback returns Success with the auth_code value`() {
        // Arrange
        val input = "feedman://auth/callback?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("ABC123"), result)
    }

    @Test
    fun `Req 2_2 additional query parameters do not interfere with auth_code extraction`() {
        // Arrange
        val input = "feedman://auth/callback?state=xyz&auth_code=ABC123&debug=1"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("ABC123"), result)
    }

    @Test
    fun `Req 2_3 non-feedman scheme returns SchemeMismatch failure`() {
        // Arrange
        val input = "https://auth/callback?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.SchemeMismatch),
            result,
        )
    }

    @Test
    fun `Req 2_3 scheme matching is case-insensitive`() {
        // Arrange — RFC 3986 says scheme comparison is case-insensitive; Android
        // canonicalises to lowercase but other producers may not. Accept both.
        val input = "Feedman://auth/callback?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("ABC123"), result)
    }

    @Test
    fun `Req 2_4 wrong host returns HostOrPathMismatch failure`() {
        // Arrange
        val input = "feedman://other/callback?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.HostOrPathMismatch),
            result,
        )
    }

    @Test
    fun `Req 2_4 wrong path returns HostOrPathMismatch failure`() {
        // Arrange
        val input = "feedman://auth/redirect?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.HostOrPathMismatch),
            result,
        )
    }

    @Test
    fun `Req 2_4 missing path returns HostOrPathMismatch failure`() {
        // Arrange
        val input = "feedman://auth?auth_code=ABC123"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.HostOrPathMismatch),
            result,
        )
    }

    @Test
    fun `Req 2_5 missing auth_code query parameter returns MissingAuthCode failure`() {
        // Arrange
        val input = "feedman://auth/callback?state=xyz"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode),
            result,
        )
    }

    @Test
    fun `Req 2_5 no query string at all returns MissingAuthCode failure`() {
        // Arrange
        val input = "feedman://auth/callback"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode),
            result,
        )
    }

    @Test
    fun `Req 2_6 empty auth_code value returns MissingAuthCode failure`() {
        // Arrange
        val input = "feedman://auth/callback?auth_code="

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode),
            result,
        )
    }

    @Test
    fun `Req 2_6 auth_code key with no equals sign returns MissingAuthCode failure`() {
        // Arrange
        val input = "feedman://auth/callback?auth_code"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(
            AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode),
            result,
        )
    }

    @Test
    fun `Req 2_7 malformed URI returns Malformed failure without throwing`() {
        // Arrange — control characters and unescaped spaces are syntactically
        // invalid per RFC 3986 and reliably trigger URISyntaxException.
        val input = "feedman://auth/call back?auth_code=ABC"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertTrue(
            "expected Malformed failure but got $result",
            result is AuthCallbackResult.Failure &&
                result.error == AuthCallbackError.Malformed,
        )
    }

    @Test
    fun `Req 2_7 empty input string returns failure without throwing`() {
        // Arrange
        val input = ""

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert — empty input has no scheme; it should not throw.
        assertTrue(
            "expected typed failure for empty input but got $result",
            result is AuthCallbackResult.Failure,
        )
    }

    @Test
    fun `Req 2_8 percent-encoded auth_code is decoded once before being returned`() {
        // Arrange — `%2F` -> '/', `%2B` -> '+', `%3D` -> '='.
        val input = "feedman://auth/callback?auth_code=A%2FB%2BC%3D"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("A/B+C="), result)
    }

    @Test
    fun `Req 2_8 literal plus character in auth_code is preserved as plus`() {
        // Arrange — the OAuth callback uses RFC 3986 percent-encoding, not
        // application/x-www-form-urlencoded. A literal '+' must NOT decode to ' '.
        val input = "feedman://auth/callback?auth_code=AB+CD"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("AB+CD"), result)
    }

    @Test
    fun `Req 2_8 percent-encoded multibyte UTF-8 sequence decodes correctly`() {
        // Arrange — UTF-8 bytes for "あ" are E3 81 82.
        val input = "feedman://auth/callback?auth_code=%E3%81%82"

        // Act
        val result = AuthCallbackParser.parse(input)

        // Assert
        assertEquals(AuthCallbackResult.Success("あ"), result)
    }
}
