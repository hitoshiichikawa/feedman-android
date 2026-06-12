package com.feedman.android.core.ui

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 記事公開時刻の相対表示フォーマッタ（Issue #27 / Req 3, NFR 1.1, 1.2, NFR 3.1）。
 *
 * プロト `design/mobile/fm-data.jsx` の `fmFormatDate` を正本とし、整数時間／整数日数の
 * 単位で境界を判定する:
 *
 * ```
 * h = floor(diffMs / 3_600_000)
 * d = floor(diffMs / 86_400_000)
 * h <  1  → "1時間以内"        (Req 3.1)
 * h < 24  → "${h}時間前"        (Req 3.2)
 * d <  7  → "${d}日前"          (Req 3.3)
 * else    → ja-JP 日付文字列     (Req 3.4)
 * ```
 *
 * `is_date_estimated=true` のときは整形結果の直後に `" (推定)"` を付加する（Req 3.5）。
 *
 * 現在時刻は [Clock] から取得し、[java.lang.System.currentTimeMillis] や
 * [java.time.Instant.now] を直接参照しない（NFR 1.1）。
 */
object RelativeTimeFormatter {

    /** 1 時間（ミリ秒）。fmFormatDate の `3600000` と同値。 */
    private const val ONE_HOUR_MS: Long = 60L * 60L * 1000L

    /** 1 日（ミリ秒）。fmFormatDate の `86400000` と同値。 */
    private const val ONE_DAY_MS: Long = 24L * ONE_HOUR_MS

    /** "(推定)" サフィックスの本体（先頭の半角スペースを含む）。Req 3.5。 */
    private const val ESTIMATED_SUFFIX: String = " (推定)"

    /**
     * 相対日時文字列を返す（Req 3.1〜3.6）。
     *
     * @param publishedAtIso RFC3339 / ISO-8601 形式の published_at（例: `2026-06-12T11:30:00Z`）
     * @param isDateEstimated `true` のとき結果の末尾に `" (推定)"` を付加する
     * @param clock 現在時刻を取得するための [Clock]。テストでは [Clock.fixed] を渡す（NFR 1.1）
     * @param locale 日付フォーマットのロケール。Req 3.4 では `ja-JP` を期待
     * @return 相対表現または絶対日付文字列。`isDateEstimated=true` のときは末尾に推定サフィックス
     * @throws IllegalArgumentException `publishedAtIso` が ISO-8601 として解釈できない場合
     */
    fun format(
        publishedAtIso: String,
        isDateEstimated: Boolean,
        clock: Clock,
        locale: Locale = Locale.JAPAN,
    ): String {
        val published = parsePublishedAt(publishedAtIso)
        val nowMs = clock.millis()
        val diffMs = nowMs - published.toEpochMilli()
        val base = buildRelativeLabel(diffMs = diffMs, published = published, clock = clock, locale = locale)
        return if (isDateEstimated) base + ESTIMATED_SUFFIX else base
    }

    /**
     * fmFormatDate と同等の境界判定を行う（Req 3.1〜3.4）。
     *
     * `diffMs` が負（published_at が未来）になる場合、floor で h も負になり `h < 1` が成立して
     * "1時間以内" を返す。これは fmFormatDate の挙動と一致する。
     */
    private fun buildRelativeLabel(diffMs: Long, published: Instant, clock: Clock, locale: Locale): String {
        // Kotlin の Long 除算は 0 方向への切り捨て（truncation）。負値で fmFormatDate の Math.floor
        // との挙動差が出るのは「-1 未満の負値」に限られ、今回の境界（h < 1, h < 24, d < 7）の
        // 判定にとっては truncation でも floor でも結果が同一になる（負値はすべて h < 1 に該当）。
        val h: Long = diffMs / ONE_HOUR_MS
        if (h < 1L) return "1時間以内"
        if (h < 24L) return "${h}時間前"
        val d: Long = diffMs / ONE_DAY_MS
        if (d < 7L) return "${d}日前"
        return formatAbsoluteDate(published = published, clock = clock, locale = locale)
    }

    /**
     * Req 3.4 — `ja-JP` の year / month / day を含む日付文字列を返す。
     *
     * [Clock.getZone] を利用してタイムゾーンを Clock に追従させる（テスト時は UTC 固定が可能）。
     * フォーマットは [FormatStyle.MEDIUM] を採用し、ja ロケールでは "2026年6月5日" のような
     * year/month/day を含む表現になる（Req 3.4 の文字列パターンを満たす）。
     */
    private fun formatAbsoluteDate(published: Instant, clock: Clock, locale: Locale): String {
        val zone: ZoneId = clock.zone
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return formatter.format(published.atZone(zone))
    }

    private fun parsePublishedAt(publishedAtIso: String): Instant {
        return try {
            Instant.parse(publishedAtIso)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(
                "publishedAtIso must be an ISO-8601 instant (e.g. 2026-06-12T11:30:00Z), got: $publishedAtIso",
                e,
            )
        }
    }
}
