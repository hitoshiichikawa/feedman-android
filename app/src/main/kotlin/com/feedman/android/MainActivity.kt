package com.feedman.android

import android.content.Intent
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
import com.feedman.android.core.auth.AuthCallbackDispatcher
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
 * Single Activity hosting the Compose UI (Req 2.5 / Issue #23 Req 3.1 / NFR 2.1).
 *
 * 現在のテーマモード（[ThemeMode]）を観測し、ライト / ダーク双方の [FeedmanTheme] に
 * `useDarkTheme` を適用する（Issue #25 / Req 3.3 / Req 3.5）。
 *
 * ## ディープリンク受領（Issue #23 Req 3.1 / NFR 2.1）
 *
 * `feedman://auth/callback?...` の intent-filter は [AndroidManifest.xml] で宣言される
 * （scheme / host / path をすべて固定し、無関係なインテントを認証フローに渡さない /
 * NFR 2.1）。本 Activity は `launchMode="singleTask"` なので、すでに前面にいる Activity
 * が `onNewIntent` でディープリンクを受け取り、初回起動時は `onCreate` の `intent` から
 * 受け取る。受け取ったインテントは [AuthCallbackDispatcher] に流し、
 * [com.feedman.android.feature.login.LoginViewModel] が collect して exchange 処理を行う。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeModeViewModel by viewModels()

    /**
     * [AuthCallbackDispatcher] は Hilt singleton（`AuthRepository` 同様にプロセス共有）。
     * `@Inject lateinit var` で取得し、`onCreate` / `onNewIntent` から URI を流す。
     */
    @Inject
    lateinit var authCallbackDispatcher: AuthCallbackDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Issue #23 Req 3.1: cold start で feedman://auth/callback で起動された場合、
        // intent.data が指定されている。LoginViewModel 側でスキーム/ホスト/パスを再検証
        // するため（NFR 2.1）、ここではフィルタなしで配信する。
        intent?.let(::handleDeepLinkIntent)
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            FeedmanTheme(useDarkTheme = themeMode.shouldUseDarkTheme()) {
                AppShell()
            }
        }
    }

    /**
     * Custom Tabs から `feedman://auth/callback?...` で戻ってきた際に呼ばれる
     * （Issue #23 Req 3.1 / NFR 2.1）。`launchMode="singleTask"` のため、新しい Activity
     * を作らず本メソッドが呼ばれる。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity 自体の intent を最新化（Android Lifecycle 標準）。
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /**
     * Intent からディープリンク URI を抽出し、[AuthCallbackDispatcher] に流す。
     *
     * - Intent の data が null（通常起動 / LAUNCHER intent）の場合は何もしない
     * - 認証フロー以外の VIEW intent（スキームが feedman でない等）が届いた場合も流すが、
     *   LoginViewModel 側で AuthCallbackParser によりスキーム/ホスト/パスを検証して
     *   無関係なインテントを no-op で破棄する（NFR 2.1）
     */
    private fun handleDeepLinkIntent(intent: Intent) {
        val data = intent.data ?: return
        authCallbackDispatcher.dispatch(data.toString())
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
