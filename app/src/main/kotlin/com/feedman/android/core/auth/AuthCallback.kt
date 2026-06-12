package com.feedman.android.core.auth

import java.net.URI
import java.net.URISyntaxException

/**
 * Parser for the native OAuth callback deep link defined in `design/SERVER.md`
 * §1.2: `feedman://auth/callback?auth_code=<one-time>`.
 *
 * The parser intentionally avoids `android.net.Uri` so that the full contract
 * is unit-testable on the JVM (NFR 1.1, NFR 2.2). All failure modes surface as
 * typed [AuthCallbackResult.Failure] variants rather than thrown exceptions
 * (Req 2.3..2.7); callers (MainActivity / future token-exchange flow) must
 * pattern-match the result.
 */
object AuthCallbackParser {

    /** Expected URI scheme for the OAuth callback deep link. */
    const val EXPECTED_SCHEME: String = "feedman"

    /** Expected URI host for the OAuth callback deep link. */
    const val EXPECTED_HOST: String = "auth"

    /** Expected URI path for the OAuth callback deep link. */
    const val EXPECTED_PATH: String = "/callback"

    /** Query parameter name carrying the one-time auth code. */
    const val PARAM_AUTH_CODE: String = "auth_code"

    /**
     * Parses [input] as the OAuth callback deep link.
     *
     * @return [AuthCallbackResult.Success] containing the (percent-decoded)
     *   `auth_code` when [input] matches `feedman://auth/callback?auth_code=<value>`
     *   with a non-empty value, otherwise a categorised
     *   [AuthCallbackResult.Failure] (Req 2.3..2.7).
     */
    fun parse(input: String): AuthCallbackResult {
        val uri = try {
            URI(input)
        } catch (_: URISyntaxException) {
            return AuthCallbackResult.Failure(AuthCallbackError.Malformed)
        } catch (_: IllegalArgumentException) {
            // URI(String) can throw IAE on some malformed inputs as well.
            return AuthCallbackResult.Failure(AuthCallbackError.Malformed)
        }

        val scheme = uri.scheme
        if (scheme == null || !scheme.equals(EXPECTED_SCHEME, ignoreCase = true)) {
            return AuthCallbackResult.Failure(AuthCallbackError.SchemeMismatch)
        }

        if (!matchesHostAndPath(uri)) {
            return AuthCallbackResult.Failure(AuthCallbackError.HostOrPathMismatch)
        }

        val rawQuery = uri.rawQuery
            ?: return AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode)

        val authCode = extractAuthCode(rawQuery)
            ?: return AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode)

        if (authCode.isEmpty()) {
            return AuthCallbackResult.Failure(AuthCallbackError.MissingAuthCode)
        }

        return AuthCallbackResult.Success(authCode)
    }

    /**
     * The deep link is conventionally written as `feedman://auth/callback`.
     * `java.net.URI` parses this as `scheme=feedman`, `host=auth`,
     * `path=/callback`. Some edge representations yield `host=null` with the
     * authority/path tucked into the scheme-specific part. We accept either as
     * long as it resolves to the same host + path pair.
     */
    private fun matchesHostAndPath(uri: URI): Boolean {
        val host = uri.host?.takeIf { it.isNotEmpty() }
        val path = uri.path.orEmpty()

        if (host != null) {
            return host.equals(EXPECTED_HOST, ignoreCase = true) &&
                path.equals(EXPECTED_PATH, ignoreCase = true)
        }

        // Fallback: parse the scheme-specific part for opaque/edge representations.
        val ssp = uri.schemeSpecificPart.orEmpty()
        val withoutQuery = ssp.substringBefore('?')
        // Expected forms: "//auth/callback" or "auth/callback".
        val trimmed = withoutQuery.trimStart('/')
        return trimmed.equals("$EXPECTED_HOST$EXPECTED_PATH", ignoreCase = true)
    }

    /**
     * Returns the percent-decoded value of the first `auth_code` query parameter
     * in [rawQuery], or `null` if no such parameter is present.
     *
     * Parses the raw (still-encoded) query ourselves rather than delegating to
     * `URI.getQuery()` so that percent-encoded values (Req 2.8) decode exactly
     * once and ambiguous `+` characters are preserved as literal `+` (the OAuth
     * callback uses RFC 3986 percent-encoding, not `application/x-www-form-
     * urlencoded`, so `+` must NOT be treated as a space).
     */
    private fun extractAuthCode(rawQuery: String): String? {
        if (rawQuery.isEmpty()) return null
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            if (key != PARAM_AUTH_CODE) continue
            val rawValue = if (eq >= 0) pair.substring(eq + 1) else ""
            return percentDecode(rawValue)
        }
        return null
    }

    /**
     * Decodes RFC 3986 percent-encoded triplets in [value]. `+` is preserved as
     * a literal `+` (we deliberately do not delegate to `java.net.URLDecoder`
     * because it implements `application/x-www-form-urlencoded` semantics).
     */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val bytes = ByteArray(value.length * 4)
        var byteLen = 0
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hi = hexValue(value[i + 1])
                val lo = hexValue(value[i + 2])
                if (hi < 0 || lo < 0) {
                    bytes[byteLen++] = '%'.code.toByte()
                    i++
                } else {
                    bytes[byteLen++] = ((hi shl 4) or lo).toByte()
                    i += 3
                }
            } else {
                val s = c.toString().toByteArray(Charsets.UTF_8)
                for (b in s) bytes[byteLen++] = b
                i++
            }
        }
        return String(bytes, 0, byteLen, Charsets.UTF_8)
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}

/**
 * Result of [AuthCallbackParser.parse]. Typed sum (sealed class) instead of
 * exceptions so callers always handle every failure category (Req 2.3..2.7).
 */
sealed class AuthCallbackResult {
    /** Successful parse; [authCode] is the percent-decoded one-time code. */
    data class Success(val authCode: String) : AuthCallbackResult()

    /** Failure with a typed [error] category. */
    data class Failure(val error: AuthCallbackError) : AuthCallbackResult()
}

/** Failure categories returned from [AuthCallbackParser.parse]. */
enum class AuthCallbackError {
    /** Input is not a syntactically valid URI (Req 2.7). */
    Malformed,

    /** URI scheme is not `feedman` (Req 2.3). */
    SchemeMismatch,

    /** URI host or path does not equal `auth/callback` (Req 2.4). */
    HostOrPathMismatch,

    /** `auth_code` query parameter is absent or empty (Req 2.5, 2.6). */
    MissingAuthCode,
}
