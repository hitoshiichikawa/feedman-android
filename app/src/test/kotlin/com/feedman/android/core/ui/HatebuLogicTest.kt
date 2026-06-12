package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HatebuLogic] の表示判定テスト（Issue #27 / Req 2.1, 2.2, 2.3, 2.4, 5.5）。
 *
 * プロト `design/mobile/fm-ui.jsx` の `FMHatebu`（L75-L89）を正本とする:
 *   val = hatebu_fetched_at == null ? '−' : hatebu_count
 *   hot = hatebu_count >= 100
 */
class HatebuLogicTest {

    // ─── Req 2.1: hatebu_fetched_at が non-null → 数値表示 ───────────────────

    @Test
    fun `Req 2_1 fetched_at non-null returns numeric display`() {
        // Arrange
        val display = HatebuLogic.compute(hatebuCount = 42, hatebuFetchedAt = "2026-06-12T10:00:00Z")
        // Act / Assert
        assertEquals(HatebuLogic.Display.Numeric(count = 42, isHot = false), display)
    }

    // ─── Req 2.2: hatebu_fetched_at == null → "−"（U+2212） ────────────────

    @Test
    fun `Req 2_2 fetched_at null returns unavailable dash`() {
        // Arrange / Act
        val display = HatebuLogic.compute(hatebuCount = 5, hatebuFetchedAt = null)
        // Assert
        assertEquals(HatebuLogic.Display.Unavailable, display)
    }

    @Test
    fun `Req 2_2 unavailable label is the unicode minus sign`() {
        // Arrange / Act / Assert — U+2212 MINUS SIGN
        assertEquals("−", HatebuLogic.UNAVAILABLE_LABEL)
    }

    // ─── Req 2.3: hatebu_count >= 100 → hot 表示 ─────────────────────────────

    @Test
    fun `Req 2_3 count exactly 100 is hot`() {
        val display = HatebuLogic.compute(hatebuCount = 100, hatebuFetchedAt = "2026-06-12T10:00:00Z")
        assertEquals(HatebuLogic.Display.Numeric(count = 100, isHot = true), display)
    }

    @Test
    fun `Req 2_3 count well above threshold is hot`() {
        val display = HatebuLogic.compute(hatebuCount = 318, hatebuFetchedAt = "2026-06-12T10:00:00Z")
        assertEquals(HatebuLogic.Display.Numeric(count = 318, isHot = true), display)
    }

    // ─── Req 2.4: hatebu_count < 100 → non-hot ───────────────────────────────

    @Test
    fun `Req 2_4 count just below threshold is not hot`() {
        val display = HatebuLogic.compute(hatebuCount = 99, hatebuFetchedAt = "2026-06-12T10:00:00Z")
        assertEquals(HatebuLogic.Display.Numeric(count = 99, isHot = false), display)
    }

    @Test
    fun `Req 2_4 count zero is not hot`() {
        val display = HatebuLogic.compute(hatebuCount = 0, hatebuFetchedAt = "2026-06-12T10:00:00Z")
        assertEquals(HatebuLogic.Display.Numeric(count = 0, isHot = false), display)
    }

    // ─── Req 5.5: 検索結果のように fetched_at が無い場合は "−" ─────────────────

    @Test
    fun `Req 5_5 search hit without fetched_at falls back to unavailable`() {
        // Arrange — 検索結果は hatebu_count があっても fetched_at が無いケースを想定
        val display = HatebuLogic.compute(hatebuCount = 250, hatebuFetchedAt = null)
        // Assert
        assertEquals(HatebuLogic.Display.Unavailable, display)
    }

    // ─── 派生プロパティの確認（isHot のテスト容易性） ────────────────────────

    @Test
    fun `Display Numeric exposes isHot helper`() {
        val hot = HatebuLogic.Display.Numeric(count = 200, isHot = true)
        val cold = HatebuLogic.Display.Numeric(count = 1, isHot = false)
        assertTrue(hot.isHot)
        assertFalse(cold.isHot)
    }
}
