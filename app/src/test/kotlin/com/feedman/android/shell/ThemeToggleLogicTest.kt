package com.feedman.android.shell

import com.feedman.android.core.designsystem.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #31 / Req 3.1〜3.3 単体テスト。
 *
 * - Req 3.1: ライト現在時に moon アイコンを返す
 * - Req 3.2: ダーク現在時に sun アイコンを返す
 * - Req 3.3: トグル後のモードが反対側へ移る
 */
class ThemeToggleLogicTest {

    @Test
    fun `現在ライト時はダークへ切り替わる_Req 3_3`() {
        // Arrange / Act
        val next = nextThemeMode(currentMode = ThemeMode.LIGHT, currentlyDark = false)
        // Assert
        assertEquals(ThemeMode.DARK, next)
    }

    @Test
    fun `現在ダーク時はライトへ切り替わる_Req 3_3`() {
        // Arrange / Act
        val next = nextThemeMode(currentMode = ThemeMode.DARK, currentlyDark = true)
        // Assert
        assertEquals(ThemeMode.LIGHT, next)
    }

    @Test
    fun `FOLLOW_SYSTEM かつ画面が暗い場合はライト固定に切り替わる_Req 3_3_境界`() {
        // Arrange / Act
        val next = nextThemeMode(currentMode = ThemeMode.FOLLOW_SYSTEM, currentlyDark = true)
        // Assert
        assertEquals(ThemeMode.LIGHT, next)
    }

    @Test
    fun `FOLLOW_SYSTEM かつ画面が明るい場合はダーク固定に切り替わる_Req 3_3_境界`() {
        // Arrange / Act
        val next = nextThemeMode(currentMode = ThemeMode.FOLLOW_SYSTEM, currentlyDark = false)
        // Assert
        assertEquals(ThemeMode.DARK, next)
    }

    @Test
    fun `ライト現在時のアイコンはダーク切替を表す Moon になる_Req 3_1`() {
        // Arrange / Act
        val icon = resolveThemeToggleIcon(currentlyDark = false)
        // Assert
        assertEquals(ThemeToggleIcon.MoonIndicatingSwitchToDark, icon)
    }

    @Test
    fun `ダーク現在時のアイコンはライト切替を表す Sun になる_Req 3_2`() {
        // Arrange / Act
        val icon = resolveThemeToggleIcon(currentlyDark = true)
        // Assert
        assertEquals(ThemeToggleIcon.SunIndicatingSwitchToLight, icon)
    }
}
