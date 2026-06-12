package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * [RelativeTimeFormatter] の境界網羅テスト（Issue #27 / Req 3, NFR 1.1, 1.2）。
 *
 * プロト `design/mobile/fm-data.jsx` の `fmFormatDate` を正本とし、
 * `h = floor(diffMs / 3_600_000)` / `d = floor(diffMs / 86_400_000)` の境界
 * （0 分 / 59 分 59 秒 / ちょうど 60 分 / 23 時間 59 分 / 24 時間ちょうど /
 *   6 日 23 時間 / 7 日ちょうど）を検証する。
 *
 * 現在時刻は [Clock.fixed] で注入し、System clock を参照しない（Req 3.7 / NFR 1.1）。
 */
class RelativeTimeFormatterTest {

    private val locale = Locale.JAPAN

    /** テストの基準時刻: 2026-06-12T12:00:00Z（fmFormatDate と同様 UTC 比較）。 */
    private val now: Instant = Instant.parse("2026-06-12T12:00:00Z")

    /** 注入用の固定 Clock（NFR 1.1）。 */
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun isoAtOffset(millisBeforeNow: Long): String =
        now.minusMillis(millisBeforeNow).toString()

    // ─── Req 3.1: h < 1 → "1時間以内" ────────────────────────────────────────

    @Test
    fun `Req 3_1 zero minute diff returns within-hour label`() {
        // Arrange
        val published = isoAtOffset(0L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("1時間以内", result)
    }

    @Test
    fun `Req 3_1 fifty-nine minutes diff returns within-hour label`() {
        // Arrange — 59 分 59.999 秒
        val published = isoAtOffset(59L * 60_000L + 59_999L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("1時間以内", result)
    }

    // ─── Req 3.2: 1h <= diff < 24h → "${h}時間前" ────────────────────────────

    @Test
    fun `Req 3_2 exactly one hour returns 1 hour ago`() {
        // Arrange — ちょうど 60 分（3_600_000ms）
        val published = isoAtOffset(60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("1時間前", result)
    }

    @Test
    fun `Req 3_2 twenty-three hours fifty-nine minutes returns 23 hour ago`() {
        // Arrange — 23 時間 59 分
        val published = isoAtOffset((23L * 60L + 59L) * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("23時間前", result)
    }

    // ─── Req 3.3: 24h <= diff < 7d → "${d}日前" ──────────────────────────────

    @Test
    fun `Req 3_3 exactly twenty-four hours returns 1 day ago`() {
        // Arrange
        val published = isoAtOffset(24L * 60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("1日前", result)
    }

    @Test
    fun `Req 3_3 six days twenty-three hours returns 6 days ago`() {
        // Arrange
        val published = isoAtOffset((6L * 24L + 23L) * 60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("6日前", result)
    }

    // ─── Req 3.4: diff >= 7d → ja-JP の日付文字列 ────────────────────────────

    @Test
    fun `Req 3_4 exactly seven days returns ja date string`() {
        // Arrange — 7 日前ちょうど（2026-06-05T12:00:00Z）
        val published = isoAtOffset(7L * 24L * 60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert — ja-JP の year/month/day を含む（具体的フォーマットは ICU 依存のため部分検証）
        // 期待: "2026" を含み、月日のいずれかを含む（"6" or "06"）
        assert(result.contains("2026")) { "expected date string to contain year 2026, got '$result'" }
        assert(result.contains("6")) { "expected date string to contain month 6, got '$result'" }
        assert(result.contains("5")) { "expected date string to contain day 5, got '$result'" }
        // 相対表現に戻っていないことを確認
        assert(!result.contains("日前")) { "expected absolute date, got relative '$result'" }
        assert(!result.contains("時間")) { "expected absolute date, got relative '$result'" }
        assert(!result.contains("以内")) { "expected absolute date, got relative '$result'" }
    }

    // ─── Req 3.5: is_date_estimated=true → "(推定)" サフィックス ──────────────

    @Test
    fun `Req 3_5 estimated date appends suffix to within-hour label`() {
        // Arrange
        val published = isoAtOffset(30L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = true, clock = clock, locale = locale)
        // Assert
        assertEquals("1時間以内 (推定)", result)
    }

    @Test
    fun `Req 3_5 estimated date appends suffix to day label`() {
        // Arrange
        val published = isoAtOffset(3L * 24L * 60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = true, clock = clock, locale = locale)
        // Assert
        assertEquals("3日前 (推定)", result)
    }

    // ─── Req 3.6: is_date_estimated=false → サフィックスなし ─────────────────

    @Test
    fun `Req 3_6 non-estimated date does not append suffix`() {
        // Arrange
        val published = isoAtOffset(5L * 60L * 60_000L)
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert
        assertEquals("5時間前", result)
        assert(!result.contains("(推定)"))
    }

    // ─── Req 3.7 / NFR 1.1: 固定 Clock が System clock より優先される ────────

    @Test
    fun `NFR 1_1 different fixed clocks yield different relative labels`() {
        // Arrange — 同一 published_at を、異なる fixed clock 2 つで整形
        val published = "2026-06-12T11:30:00Z" // 30 分前 or 60 時間前 など clock 次第
        val clockA = Clock.fixed(Instant.parse("2026-06-12T12:00:00Z"), ZoneOffset.UTC)
        val clockB = Clock.fixed(Instant.parse("2026-06-14T11:30:00Z"), ZoneOffset.UTC)
        // Act
        val resultA = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clockA, locale = locale)
        val resultB = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clockB, locale = locale)
        // Assert
        assertEquals("1時間以内", resultA)
        assertEquals("2日前", resultB)
    }

    // ─── 異常系: 不正な published_at は呼び出し側にとって不便なので IllegalArgument を投げる ─

    @Test(expected = IllegalArgumentException::class)
    fun `Req 3 throws when published_at is malformed`() {
        RelativeTimeFormatter.format("not-an-iso-date", isDateEstimated = false, clock = clock, locale = locale)
    }

    // ─── 境界: 未来時刻（時計巻き戻りや時刻ズレ）は fmFormatDate 同様 within-hour 扱い ──

    @Test
    fun `future published_at is treated as within-hour`() {
        // Arrange — published_at が now より先（diff が負）
        val published = "2026-06-12T13:00:00Z"
        // Act
        val result = RelativeTimeFormatter.format(published, isDateEstimated = false, clock = clock, locale = locale)
        // Assert — fmFormatDate と同様、h < 1 のため "1時間以内"
        assertEquals("1時間以内", result)
    }
}
