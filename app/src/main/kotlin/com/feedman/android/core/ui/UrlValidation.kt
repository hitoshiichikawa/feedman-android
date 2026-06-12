package com.feedman.android.core.ui

import java.net.URI
import java.net.URISyntaxException

/**
 * 元記事 URL の検証ロジック（Issue #37 / Req 4.1 / Req 4.2 / Req 4.3 / NFR 3.1）。
 *
 * Android 依存（[android.net.Uri] 等）を持たない純粋関数として実装することで、
 * JVM 単体テストから直接呼び出して網羅検証できるようにする（NFR 3.1）。
 *
 * ## 検証規約
 *
 * - 空文字 / 空白のみ → [InvalidUrlReason.Blank]
 * - [URI] として解釈できない → [InvalidUrlReason.Malformed]
 * - scheme が `http` または `https` 以外（大文字小文字を区別しない）→ [InvalidUrlReason.UnsupportedScheme]
 *   - 該当例: `javascript:` / `mailto:` / `file:` / `data:` / `intent:` / `content:` 等
 * - 上記いずれにも該当しない → [ValidationResult.Valid]
 */
internal object UrlValidation {

    /** 検証結果。pure な値オブジェクトで sealed として扱う。 */
    sealed class ValidationResult {
        data class Valid(val url: String) : ValidationResult()
        data class Invalid(val reason: InvalidUrlReason) : ValidationResult()
    }

    /**
     * 与えられた URL 文字列を検証する。
     *
     * @param url 検証対象（trim は内部で行わない。呼び出し側で trim 済みである必要は無い）
     * @return 検証結果
     */
    fun validate(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Invalid(InvalidUrlReason.Blank)
        }

        val parsed = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return ValidationResult.Invalid(InvalidUrlReason.Malformed)
        }

        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ValidationResult.Invalid(InvalidUrlReason.UnsupportedScheme)
        }

        // host が無い http/https は実用上開けないので Malformed として扱う
        // 例: "http:///path"（host が空）
        if (parsed.host.isNullOrBlank()) {
            return ValidationResult.Invalid(InvalidUrlReason.Malformed)
        }

        return ValidationResult.Valid(url)
    }
}
