package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LinkOpenerLogic] の単体テスト（Issue #37 / Req 1.1, 2.1, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3 /
 * NFR 3.2）。
 *
 * 起動経路選択ロジックを Android 依存（Context / Intent / Custom Tabs SDK）から
 * 切り離して網羅検証する。
 */
class LinkOpenerLogicTest {

    private val validUrl = UrlValidation.ValidationResult.Valid("https://example.com/article")
    private val invalidUrl = UrlValidation.ValidationResult.Invalid(InvalidUrlReason.UnsupportedScheme)

    // ── Req 4.1 / 4.2 / 4.3: URL バリデーション失敗 ──────────────────────────

    @Test
    fun `URL が不正なら端末状態に関わらず DoNothing と InvalidUrl を返す_Req 4_3`() {
        // Arrange
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = true,
            fallbackAvailable = true,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = invalidUrl, preflight = preflight)

        // Assert
        assertTrue(plan is LinkOpenerLogic.LaunchPlan.DoNothing)
        val result = (plan as LinkOpenerLogic.LaunchPlan.DoNothing).result
        assertTrue(result is OpenLinkResult.InvalidUrl)
        assertEquals(
            InvalidUrlReason.UnsupportedScheme,
            (result as OpenLinkResult.InvalidUrl).reason,
        )
    }

    @Test
    fun `URL が Blank の場合も DoNothing + InvalidUrl_Blank を返す_Req 4_2`() {
        // Arrange
        val blank = UrlValidation.ValidationResult.Invalid(InvalidUrlReason.Blank)
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = true,
            fallbackAvailable = true,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = blank, preflight = preflight)

        // Assert
        assertTrue(plan is LinkOpenerLogic.LaunchPlan.DoNothing)
        val result = (plan as LinkOpenerLogic.LaunchPlan.DoNothing).result
        assertTrue(result is OpenLinkResult.InvalidUrl)
        assertEquals(InvalidUrlReason.Blank, (result as OpenLinkResult.InvalidUrl).reason)
    }

    // ── Req 1.1 / 2.1: Custom Tabs 利用 ──────────────────────────────────────

    @Test
    fun `Custom Tabs 対応ブラウザがあれば UseCustomTabs を返す_Req 1_1`() {
        // Arrange
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = true,
            fallbackAvailable = true,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = validUrl, preflight = preflight)

        // Assert
        assertEquals(LinkOpenerLogic.LaunchPlan.UseCustomTabs, plan)
    }

    @Test
    fun `Custom Tabs 対応がありフォールバック解決不可でも UseCustomTabs を選ぶ`() {
        // Arrange: 万一 ACTION_VIEW 解決が失敗しても Custom Tabs があれば優先される
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = true,
            fallbackAvailable = false,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = validUrl, preflight = preflight)

        // Assert
        assertEquals(LinkOpenerLogic.LaunchPlan.UseCustomTabs, plan)
    }

    // ── Req 3.1 / 3.2: フォールバック ─────────────────────────────────────────

    @Test
    fun `Custom Tabs 非対応で ACTION_VIEW 解決可能なら UseFallback_Req 3_1`() {
        // Arrange
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = false,
            fallbackAvailable = true,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = validUrl, preflight = preflight)

        // Assert
        assertEquals(LinkOpenerLogic.LaunchPlan.UseFallback, plan)
    }

    // ── Req 3.3: どちらも開けない ────────────────────────────────────────────

    @Test
    fun `Custom Tabs もフォールバックも不可なら NoAppToHandle を返す_Req 3_3`() {
        // Arrange
        val preflight = LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = false,
            fallbackAvailable = false,
        )

        // Act
        val plan = LinkOpenerLogic.decide(validation = validUrl, preflight = preflight)

        // Assert
        assertTrue(plan is LinkOpenerLogic.LaunchPlan.DoNothing)
        val result = (plan as LinkOpenerLogic.LaunchPlan.DoNothing).result
        assertEquals(OpenLinkResult.NoAppToHandle, result)
    }
}
