package com.feedman.android.core.designsystem.fake

import com.feedman.android.core.designsystem.ThemeMode
import com.feedman.android.core.designsystem.ThemeModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * テスト・モックモード向け in-memory [ThemeModeRepository]（Issue #25 / NFR 3.2）。
 *
 * 永続化層を起動せずに ViewModel / Composable のテーマモード切替挙動を検証できるよう、
 * [MutableStateFlow] で同期的に値を保持する。
 *
 * @param initial 初期値（Req 3.2 と整合させるため既定は [ThemeMode.DEFAULT]）。
 */
class InMemoryThemeModeRepository(
    initial: ThemeMode = ThemeMode.DEFAULT,
) : ThemeModeRepository {

    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<ThemeMode> = state.asStateFlow()

    override suspend fun setMode(mode: ThemeMode) {
        state.value = mode
    }
}
