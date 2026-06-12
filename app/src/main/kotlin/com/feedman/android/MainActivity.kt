package com.feedman.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.designsystem.FeedmanTheme
import com.feedman.android.core.designsystem.ThemeMode
import com.feedman.android.core.designsystem.ThemeModeRepository
import com.feedman.android.shell.AppShell
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Single Activity hosting the Compose UI (Req 2.5).
 *
 * 現在のテーマモード（[ThemeMode]）を観測し、ライト / ダーク双方の [FeedmanTheme] に
 * `useDarkTheme` を適用する（Issue #25 / Req 3.3 / Req 3.5）。テーマ切替 UI 自体は
 * 本 Issue のスコープ外（後続 Issue で扱う）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            FeedmanTheme(useDarkTheme = themeMode.shouldUseDarkTheme()) {
                AppShell()
            }
        }
    }
}

/**
 * `MainActivity` が観測する [ThemeMode] の保持 ViewModel（Req 3.3 / 3.5）。
 *
 * [ThemeModeRepository.observe] を `stateIn` で `StateFlow<ThemeMode>` 化し、
 * 初期値は [ThemeMode.DEFAULT]（Req 3.2）。
 */
@HiltViewModel
class ThemeModeViewModel @Inject constructor(
    repository: ThemeModeRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ThemeMode.DEFAULT,
    )

    private companion object {
        /** Lifecycle の一時停止に追従できる程度の余裕（Compose のおすすめ慣習に準ずる）。 */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * [ThemeMode] から `useDarkTheme` Boolean を解決する Composable ヘルパ（Req 3.3 / 3.5）。
 *
 * - [ThemeMode.FOLLOW_SYSTEM] のときのみ [isSystemInDarkTheme] を参照して端末追従。
 * - [ThemeMode.LIGHT] / [ThemeMode.DARK] は端末設定に関わらず固定。
 */
@Composable
fun ThemeMode.shouldUseDarkTheme(): Boolean = when (this) {
    ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
