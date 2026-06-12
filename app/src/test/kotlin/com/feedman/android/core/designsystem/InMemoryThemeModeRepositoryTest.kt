package com.feedman.android.core.designsystem

import app.cash.turbine.test
import com.feedman.android.core.designsystem.fake.InMemoryThemeModeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [InMemoryThemeModeRepository] の挙動を担保（Issue #25 / Req 3 / NFR 3.2）。
 *
 * 永続化層を起動せずに ThemeMode の保存・読み出し・既定値を検証できることを確認する。
 */
class InMemoryThemeModeRepositoryTest {

    @Test
    fun `Req 3_2 default value before any setMode is FOLLOW_SYSTEM`() = runTest {
        // Arrange
        val repo = InMemoryThemeModeRepository()

        // Act / Assert
        repo.observe().test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_4 setMode value is observable in downstream flow`() = runTest {
        val repo = InMemoryThemeModeRepository()

        repo.observe().test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            repo.setMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            repo.setMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_1 all three modes can be persisted and observed`() = runTest {
        val repo = InMemoryThemeModeRepository()
        for (mode in ThemeMode.values()) {
            repo.setMode(mode)
            repo.observe().test {
                assertEquals(mode, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `Req 3_2 explicit initial value overrides default`() = runTest {
        val repo = InMemoryThemeModeRepository(initial = ThemeMode.LIGHT)
        repo.observe().test {
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
