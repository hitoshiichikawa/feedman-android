package com.feedman.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Unit tests for [PkceGenerator] and [deriveCodeChallenge] (Issue #19 Req 1).
 *
 * Tests use JVM-only primitives (no Android dependency, NFR 1.1) and inject a
 * deterministic [SecureRandom] subclass so the RFC 7636 Appendix B known-answer
 * vector and other contract assertions are reproducible (NFR 2.1).
 */
class PkceTest {

    /** Pattern for the RFC 7636 unreserved character set (Req 1.2). */
    private val unreservedRegex = Regex("^[A-Za-z0-9._~-]+$")

    @Test
    fun `Req 1_3 RFC 7636 Appendix B sample verifier produces documented challenge`() {
        // Arrange — Appendix B known-answer vector from RFC 7636.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        // Act
        val challenge = deriveCodeChallenge(verifier)

        // Assert — RFC 7636 §Appendix B expected value.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun `Req 1_1 generate returns S256 pair with both fields populated`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val pair = generator.generate()

        // Assert
        assertTrue("verifier is not blank", pair.codeVerifier.isNotBlank())
        assertTrue("challenge is not blank", pair.codeChallenge.isNotBlank())
        assertEquals(PkceGenerator.METHOD_S256, pair.method)
    }

    @Test
    fun `Req 1_2 generated verifier sits within 43 to 128 characters`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val pair = generator.generate()

        // Assert — RFC 7636 §4.1 length window.
        assertTrue(
            "verifier length ${pair.codeVerifier.length} must be in [43, 128]",
            pair.codeVerifier.length in 43..128,
        )
    }

    @Test
    fun `Req 1_2 generated verifier uses only RFC 7636 unreserved characters`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val pair = generator.generate()

        // Assert
        assertTrue(
            "verifier '${pair.codeVerifier}' contains disallowed characters",
            unreservedRegex.matches(pair.codeVerifier),
        )
    }

    @Test
    fun `Req 1_2 verifier has no Base64 padding character`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val pair = generator.generate()

        // Assert — '=' is not in the unreserved set; doubly enforce here.
        assertFalse("verifier must not contain padding", pair.codeVerifier.contains('='))
    }

    @Test
    fun `Req 1_3 challenge is Base64URL no-padding of SHA-256 of injected verifier`() {
        // Arrange — inject a deterministic random source so we know which verifier
        // will be produced (64 zero bytes → known Base64URL string).
        val zeroSeeded = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                for (i in bytes.indices) bytes[i] = 0
            }
        }
        val generator = PkceGenerator.default(zeroSeeded)

        // Act
        val pair = generator.generate()
        val expectedChallenge = deriveCodeChallenge(pair.codeVerifier)

        // Assert — challenge matches the package-level derivation function.
        assertEquals(expectedChallenge, pair.codeChallenge)
        // Sanity: no padding.
        assertFalse("challenge must not contain padding", pair.codeChallenge.contains('='))
    }

    @Test
    fun `Req 1_5 two consecutive generate calls return distinct verifiers`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val a = generator.generate()
        val b = generator.generate()

        // Assert
        assertNotEquals(a.codeVerifier, b.codeVerifier)
        assertNotEquals(a.codeChallenge, b.codeChallenge)
    }

    @Test
    fun `Req 1_5 ten consecutive generate calls return all-unique verifiers`() {
        // Arrange
        val generator = PkceGenerator.default(SecureRandom())

        // Act
        val verifiers = (0 until 10).map { generator.generate().codeVerifier }

        // Assert
        assertEquals(verifiers.size, verifiers.toSet().size)
    }

    @Test
    fun `Req 1_6 method constant is exactly S256`() {
        assertEquals("S256", PkceGenerator.METHOD_S256)
    }
}
