package com.feedman.android.core.designsystem

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [FeedmanLightColorScheme] / [FeedmanDarkColorScheme] / Feedman 拡張トークンの
 * マッピング検証（Issue #25 / Req 2.1, 2.3, 2.4）。
 *
 * `@Composable` 不要な純粋データへの mapping のみを検証し、Compose ランタイム / Robolectric
 * 等の起動を必要としない。
 */
class FeedmanThemeMappingTest {

    @Test
    fun `Req 2_1 light scheme maps primary to LightAccent`() {
        assertEquals(
            FeedmanColors.LightAccent.toArgb(),
            FeedmanLightColorScheme.primary.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 light scheme maps background to LightBg`() {
        assertEquals(
            FeedmanColors.LightBg.toArgb(),
            FeedmanLightColorScheme.background.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 light scheme maps surface to LightSurface`() {
        assertEquals(
            FeedmanColors.LightSurface.toArgb(),
            FeedmanLightColorScheme.surface.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 light scheme maps onSurface to LightFg`() {
        assertEquals(
            FeedmanColors.LightFg.toArgb(),
            FeedmanLightColorScheme.onSurface.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 light scheme maps outline to LightBorder`() {
        assertEquals(
            FeedmanColors.LightBorder.toArgb(),
            FeedmanLightColorScheme.outline.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 dark scheme maps primary to DarkAccent`() {
        assertEquals(
            FeedmanColors.DarkAccent.toArgb(),
            FeedmanDarkColorScheme.primary.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 dark scheme maps background to DarkBg`() {
        assertEquals(
            FeedmanColors.DarkBg.toArgb(),
            FeedmanDarkColorScheme.background.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 dark scheme maps surface to DarkSurface`() {
        assertEquals(
            FeedmanColors.DarkSurface.toArgb(),
            FeedmanDarkColorScheme.surface.toArgb(),
        )
    }

    @Test
    fun `Req 2_1 light and dark schemes are not identical`() {
        assertNotEquals(
            FeedmanLightColorScheme.primary.toArgb(),
            FeedmanDarkColorScheme.primary.toArgb(),
        )
        assertNotEquals(
            FeedmanLightColorScheme.background.toArgb(),
            FeedmanDarkColorScheme.background.toArgb(),
        )
    }

    /**
     * Req 2.3: M3 標準スロットに収まらない独自トークンが拡張プロパティとして公開される。
     * 既読項目用 opacity 0.55 を保持していることと、カード背景が surface と一致していることを検証。
     */
    @Test
    fun `Req 2_3 extended tokens expose read foreground alpha 0_55`() {
        assertEquals(0.55f, LightFeedmanExtendedColors.readForegroundAlpha, 0.0001f)
        assertEquals(0.55f, DarkFeedmanExtendedColors.readForegroundAlpha, 0.0001f)
    }

    @Test
    fun `Req 2_3 extended tokens expose card background per theme`() {
        assertEquals(
            FeedmanColors.LightSurface.toArgb(),
            LightFeedmanExtendedColors.cardBackground.toArgb(),
        )
        assertEquals(
            FeedmanColors.DarkSurface.toArgb(),
            DarkFeedmanExtendedColors.cardBackground.toArgb(),
        )
    }

    /**
     * Req 2.4: star / danger を Material 3 標準ロールに丸めず独立公開する。
     * （M3 の error スロットには Reviewer の利便性のため danger を入れているが、
     *  独自 extended トークンの star / danger も独立して参照できる必要がある）
     */
    @Test
    fun `Req 2_4 extended tokens expose star independently`() {
        assertEquals(
            FeedmanColors.LightStar.toArgb(),
            LightFeedmanExtendedColors.star.toArgb(),
        )
        assertEquals(
            FeedmanColors.DarkStar.toArgb(),
            DarkFeedmanExtendedColors.star.toArgb(),
        )
    }

    @Test
    fun `Req 2_4 extended tokens expose danger independently`() {
        assertEquals(
            FeedmanColors.LightDanger.toArgb(),
            LightFeedmanExtendedColors.danger.toArgb(),
        )
        assertEquals(
            FeedmanColors.DarkDanger.toArgb(),
            DarkFeedmanExtendedColors.danger.toArgb(),
        )
    }
}
