package com.feedman.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ThemeMode] enum 自体の不変条件（Issue #25 / Req 3.1, 3.2）。
 */
class ThemeModeTest {

    @Test
    fun `Req 3_1 exposes three selectable modes`() {
        val modes = ThemeMode.values().toSet()
        assertEquals(
            setOf(ThemeMode.FOLLOW_SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
            modes,
        )
    }

    @Test
    fun `Req 3_2 default is FOLLOW_SYSTEM`() {
        assertEquals(ThemeMode.FOLLOW_SYSTEM, ThemeMode.DEFAULT)
    }

    @Test
    fun `Req 3_1 enum names are stable and parseable`() {
        // Persistence layer relies on `name`; assert it does not silently change.
        assertEquals("FOLLOW_SYSTEM", ThemeMode.FOLLOW_SYSTEM.name)
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)
        assertTrue(ThemeMode.valueOf("FOLLOW_SYSTEM") == ThemeMode.FOLLOW_SYSTEM)
    }
}
