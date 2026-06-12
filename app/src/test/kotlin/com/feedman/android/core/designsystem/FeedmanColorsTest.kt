package com.feedman.android.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [FeedmanColors] の ARGB 定数が fm-data.jsx FM_THEME の oklch 値から正しく
 * 換算されていることを担保する単体テスト（Issue #25 / Req 1.1, 1.2, 1.5 / NFR 1.1）。
 *
 * 期待 ARGB は `impl-notes.md` の換算メモにある Python リファレンス計算
 * （CSS Color Module Level 4 §16.7 の OKLCh → sRGB 行列）から得た値を固定する。
 * 換算スクリプトの再現性を保つため、ここでは個別チャンネル単位ではなく ARGB 値全体で
 * 一致確認する（小数誤差は計算側で round によって解決される）。
 */
class FeedmanColorsTest {

    @Test
    fun `Req 1_1 light bg derives from oklch(0_985 0 0) as FAFAFA`() {
        assertEquals(0xFFFAFAFA.toInt(), FeedmanColors.LightBg.toArgb())
    }

    @Test
    fun `Req 1_1 light surface derives from oklch(1 0 0) as FFFFFF`() {
        assertEquals(0xFFFFFFFF.toInt(), FeedmanColors.LightSurface.toArgb())
    }

    @Test
    fun `Req 1_1 light surface2 derives from oklch(0_975 0 0) as F7F7F7`() {
        assertEquals(0xFFF7F7F7.toInt(), FeedmanColors.LightSurface2.toArgb())
    }

    @Test
    fun `Req 1_1 light fg derives from oklch(0_205 0 0) as 171717`() {
        assertEquals(0xFF171717.toInt(), FeedmanColors.LightFg.toArgb())
    }

    @Test
    fun `Req 1_1 light muted derives from oklch(0_97 0 0) as F5F5F5`() {
        assertEquals(0xFFF5F5F5.toInt(), FeedmanColors.LightMuted.toArgb())
    }

    @Test
    fun `Req 1_1 light mutedFg derives from oklch(0_556 0 0) as 737373`() {
        assertEquals(0xFF737373.toInt(), FeedmanColors.LightMutedFg.toArgb())
    }

    @Test
    fun `Req 1_1 light border derives from oklch(0_922 0 0) as E5E5E5`() {
        assertEquals(0xFFE5E5E5.toInt(), FeedmanColors.LightBorder.toArgb())
    }

    @Test
    fun `Req 1_1 light borderStrong derives from oklch(0_87 0 0) as D4D4D4`() {
        assertEquals(0xFFD4D4D4.toInt(), FeedmanColors.LightBorderStrong.toArgb())
    }

    @Test
    fun `Req 1_1 light star derives from oklch(0_78 0_16 84) as E7AD01`() {
        assertEquals(0xFFE7AD01.toInt(), FeedmanColors.LightStar.toArgb())
    }

    @Test
    fun `Req 1_1 light danger derives from oklch(0_577 0_245 27) as E7000F`() {
        assertEquals(0xFFE7000F.toInt(), FeedmanColors.LightDanger.toArgb())
    }

    @Test
    fun `Req 1_1 light scrim is 32 percent black 52000000`() {
        assertEquals(0x52000000, FeedmanColors.LightScrim.toArgb())
    }

    @Test
    fun `Req 1_2 light accent is Indigo derived from oklch(0_55 0_17 264) as 3C6AD3`() {
        assertEquals(0xFF3C6AD3.toInt(), FeedmanColors.LightAccent.toArgb())
    }

    @Test
    fun `Req 1_2 light accentOn is white`() {
        assertEquals(0xFFFFFFFF.toInt(), FeedmanColors.LightAccentOn.toArgb())
    }

    @Test
    fun `Req 1_2 light accentSoft uses mix accent 12 percent with white`() {
        assertEquals(0xFFE6EDFB.toInt(), FeedmanColors.LightAccentSoft.toArgb())
    }

    @Test
    fun `Req 1_1 dark bg derives from oklch(0_145 0 0) as 0A0A0A`() {
        assertEquals(0xFF0A0A0A.toInt(), FeedmanColors.DarkBg.toArgb())
    }

    @Test
    fun `Req 1_1 dark surface derives from oklch(0_205 0 0) as 171717`() {
        assertEquals(0xFF171717.toInt(), FeedmanColors.DarkSurface.toArgb())
    }

    @Test
    fun `Req 1_1 dark surface2 derives from oklch(0_235 0 0) as 1E1E1E`() {
        assertEquals(0xFF1E1E1E.toInt(), FeedmanColors.DarkSurface2.toArgb())
    }

    @Test
    fun `Req 1_1 dark fg derives from oklch(0_985 0 0) as FAFAFA`() {
        assertEquals(0xFFFAFAFA.toInt(), FeedmanColors.DarkFg.toArgb())
    }

    @Test
    fun `Req 1_1 dark muted derives from oklch(0_269 0 0) as 262626`() {
        assertEquals(0xFF262626.toInt(), FeedmanColors.DarkMuted.toArgb())
    }

    @Test
    fun `Req 1_1 dark mutedFg derives from oklch(0_708 0 0) as A1A1A1`() {
        assertEquals(0xFFA1A1A1.toInt(), FeedmanColors.DarkMutedFg.toArgb())
    }

    @Test
    fun `Req 1_1 dark border is white at 12 percent alpha`() {
        assertEquals(0x1FFFFFFF, FeedmanColors.DarkBorder.toArgb())
    }

    @Test
    fun `Req 1_1 dark borderStrong is white at 20 percent alpha`() {
        assertEquals(0x33FFFFFF, FeedmanColors.DarkBorderStrong.toArgb())
    }

    @Test
    fun `Req 1_1 dark star derives from oklch(0_82 0_16 84) as F5BA26`() {
        assertEquals(0xFFF5BA26.toInt(), FeedmanColors.DarkStar.toArgb())
    }

    @Test
    fun `Req 1_1 dark danger derives from oklch(0_704 0_191 22) as FF6468`() {
        assertEquals(0xFFFF6468.toInt(), FeedmanColors.DarkDanger.toArgb())
    }

    @Test
    fun `Req 1_1 dark scrim is 60 percent black 99000000`() {
        assertEquals(0x99000000.toInt(), FeedmanColors.DarkScrim.toArgb())
    }

    @Test
    fun `Req 1_2 dark accent is Indigo derived from oklch(0_68 0_15 264) as 6895F4`() {
        assertEquals(0xFF6895F4.toInt(), FeedmanColors.DarkAccent.toArgb())
    }

    @Test
    fun `Req 1_2 dark accentSoft is accent at 18 percent alpha`() {
        assertEquals(0x2E6895F4, FeedmanColors.DarkAccentSoft.toArgb())
    }

    /**
     * Req 1.5: ライト / ダークの色トークン集合は独立して参照可能で、
     * 一方の値で他方を代用しない（少なくとも fg / bg のような主要トークンで等価ではない）。
     */
    @Test
    fun `Req 1_5 light and dark palettes are independent for primary surfaces`() {
        assertNotEquals(FeedmanColors.LightBg, FeedmanColors.DarkBg)
        assertNotEquals(FeedmanColors.LightFg, FeedmanColors.DarkFg)
        assertNotEquals(FeedmanColors.LightSurface, FeedmanColors.DarkSurface)
        assertNotEquals(FeedmanColors.LightAccent, FeedmanColors.DarkAccent)
        assertNotEquals(FeedmanColors.LightScrim, FeedmanColors.DarkScrim)
    }

    /**
     * NFR 2.1: 本文テキスト色と既定サーフェス色のコントラスト比が WCAG AA を満たす
     * （通常テキスト 4.5:1 以上）。light fg vs surface / dark fg vs surface を検証する。
     */
    @Test
    fun `NFR 2_1 light fg over surface satisfies WCAG AA contrast 4_5_1`() {
        val ratio = contrastRatio(FeedmanColors.LightFg, FeedmanColors.LightSurface)
        assert(ratio >= 4.5) { "Light fg vs surface ratio was $ratio, expected >= 4.5" }
    }

    @Test
    fun `NFR 2_1 dark fg over surface satisfies WCAG AA contrast 4_5_1`() {
        val ratio = contrastRatio(FeedmanColors.DarkFg, FeedmanColors.DarkSurface)
        assert(ratio >= 4.5) { "Dark fg vs surface ratio was $ratio, expected >= 4.5" }
    }

    /** WCAG 2.1 contrast ratio (https://www.w3.org/TR/WCAG21/#dfn-relative-luminance). */
    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val (lo, hi) = if (l1 < l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
}
