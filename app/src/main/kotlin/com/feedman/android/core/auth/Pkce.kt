package com.feedman.android.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (Proof Key for Code Exchange / RFC 7636) generator for the native Google
 * OAuth flow defined in `design/SERVER.md` §1.2.
 *
 * The generator is intentionally framework-free (no Android dependency) so that
 * the full contract is unit-testable on the JVM (NFR 1.1). The random source is
 * injectable to allow deterministic verification of the `code_verifier` /
 * `code_challenge` relationship (NFR 2.1).
 *
 * Only the S256 challenge method is supported (Req 1.6); the `method` field is
 * surfaced on [PkcePair] as a stable constant for downstream callers (#21 /
 * #23).
 */
interface PkceGenerator {
    /** Generates a fresh [PkcePair]. Successive calls return distinct verifiers (Req 1.5). */
    fun generate(): PkcePair

    companion object {
        /** PKCE challenge method (RFC 7636 §4.3). Only S256 is supported (Req 1.6). */
        const val METHOD_S256: String = "S256"

        /**
         * Number of raw random bytes used to derive a `code_verifier`. With Base64URL
         * encoding (no padding) this yields a 86-character string, which sits within
         * the RFC 7636 §4.1 range of 43..128 characters (Req 1.2).
         */
        internal const val VERIFIER_RANDOM_BYTES: Int = 64

        /**
         * Returns a [PkceGenerator] backed by [SecureRandom] (Req 1.4).
         *
         * Tests can supply a custom [SecureRandom] (e.g. a deterministic stub) to
         * verify the verifier → challenge derivation against the RFC 7636 Appendix B
         * known-answer vector.
         */
        fun default(random: SecureRandom = SecureRandom()): PkceGenerator =
            DefaultPkceGenerator(random)
    }
}

/**
 * Immutable PKCE pair returned from [PkceGenerator.generate].
 *
 * @property codeVerifier RFC 7636 unreserved-character string, 43..128 chars (Req 1.2).
 * @property codeChallenge Base64URL(no padding) of SHA-256(verifier ASCII bytes) (Req 1.3).
 * @property method Challenge method. Always [PkceGenerator.METHOD_S256] (Req 1.6).
 */
data class PkcePair(
    val codeVerifier: String,
    val codeChallenge: String,
    val method: String = PkceGenerator.METHOD_S256,
)

/**
 * Default [PkceGenerator] implementation. Draws 64 random bytes from the supplied
 * [SecureRandom] and encodes them with Base64URL (no padding) to obtain the
 * `code_verifier`. The challenge is SHA-256(verifier ASCII) → Base64URL no padding.
 */
internal class DefaultPkceGenerator(
    private val random: SecureRandom,
) : PkceGenerator {

    override fun generate(): PkcePair {
        val verifier = generateCodeVerifier()
        val challenge = deriveCodeChallenge(verifier)
        return PkcePair(codeVerifier = verifier, codeChallenge = challenge)
    }

    private fun generateCodeVerifier(): String {
        val raw = ByteArray(PkceGenerator.VERIFIER_RANDOM_BYTES)
        random.nextBytes(raw)
        return BASE64_URL_NO_PAD.encodeToString(raw)
    }

    private companion object {
        private val BASE64_URL_NO_PAD: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

/**
 * Computes the PKCE S256 `code_challenge` for a given [codeVerifier] string per
 * RFC 7636 §4.2: `BASE64URL(SHA256(ASCII(code_verifier)))` (Req 1.3).
 *
 * Exposed at package level so tests can verify against the RFC 7636 Appendix B
 * known-answer vector without instantiating a generator.
 */
internal fun deriveCodeChallenge(codeVerifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
