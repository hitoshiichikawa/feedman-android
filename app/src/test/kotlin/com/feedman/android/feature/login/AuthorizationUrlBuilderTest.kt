package com.feedman.android.feature.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AuthorizationUrlBuilder] の単体テスト（Issue #23 / Req 2.1, 2.2, 2.3）。
 *
 * 純粋関数のため Android ランタイム不要で全分岐を検証できる（CLAUDE.md テスト規約準拠）。
 */
class AuthorizationUrlBuilderTest {

    @Test
    fun `Req 2_1 build appends path to baseUrl without trailing slash`() {
        // Arrange
        val baseUrl = "https://stg-feed.market-river.net"

        // Act
        val url = AuthorizationUrlBuilder.build(baseUrl = baseUrl, codeChallenge = "abc")

        // Assert
        assertTrue(
            "URL must start with `<baseUrl>/auth/google/login`",
            url.startsWith("https://stg-feed.market-river.net/auth/google/login?"),
        )
    }

    @Test
    fun `Req 2_1 build collapses trailing slash on baseUrl`() {
        // Arrange
        val baseUrl = "https://stg-feed.market-river.net/"

        // Act
        val url = AuthorizationUrlBuilder.build(baseUrl = baseUrl, codeChallenge = "abc")

        // Assert: 末尾スラッシュが除去され二重スラッシュにならない
        assertTrue(
            "URL must not contain `//auth/google/login`",
            !url.contains("//auth/google/login"),
        )
        assertTrue(url.startsWith("https://stg-feed.market-river.net/auth/google/login?"))
    }

    @Test
    fun `Req 2_3 build includes flow=native query parameter`() {
        // Act
        val url = AuthorizationUrlBuilder.build(
            baseUrl = "https://example.invalid",
            codeChallenge = "challenge-1",
        )

        // Assert
        assertTrue("URL must contain flow=native", url.contains("flow=native"))
    }

    @Test
    fun `Req 2_2 build includes code_challenge and S256 method`() {
        // Act
        val url = AuthorizationUrlBuilder.build(
            baseUrl = "https://example.invalid",
            codeChallenge = "challenge-xyz",
        )

        // Assert
        assertTrue(
            "URL must contain code_challenge=challenge-xyz",
            url.contains("code_challenge=challenge-xyz"),
        )
        assertTrue(
            "URL must contain code_challenge_method=S256",
            url.contains("code_challenge_method=S256"),
        )
    }

    @Test
    fun `Req 2_2 build percent-encodes code_challenge value`() {
        // Arrange: PKCE Base64URL は安全な文字のみだが、念のためエンコードが効くことを確認
        val challenge = "abc def+/="

        // Act
        val url = AuthorizationUrlBuilder.build(
            baseUrl = "https://example.invalid",
            codeChallenge = challenge,
        )

        // Assert: スペースは + または %20、+ は %2B、/ は %2F、= は %3D にエンコード
        assertTrue(
            "code_challenge must be percent-encoded (raw value=`$url`)",
            url.contains("code_challenge=abc+def%2B%2F%3D") ||
                url.contains("code_challenge=abc%20def%2B%2F%3D"),
        )
    }

    @Test
    fun `Req 2_1 build returns full URL with three query parameters separated by ampersand`() {
        // Act
        val url = AuthorizationUrlBuilder.build(
            baseUrl = "https://example.invalid",
            codeChallenge = "C",
        )

        // Assert
        assertEquals(
            "https://example.invalid/auth/google/login?flow=native&code_challenge=C&code_challenge_method=S256",
            url,
        )
    }

    @Test
    fun `build rejects blank baseUrl with IllegalArgumentException`() {
        // Act + Assert
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizationUrlBuilder.build(baseUrl = "  ", codeChallenge = "C")
        }
    }

    @Test
    fun `build rejects blank codeChallenge with IllegalArgumentException`() {
        // Act + Assert
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizationUrlBuilder.build(baseUrl = "https://example.invalid", codeChallenge = " ")
        }
    }
}
