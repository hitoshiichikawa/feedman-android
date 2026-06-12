package com.feedman.android.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.feedman.android.R
import com.feedman.android.core.auth.SessionState
import com.feedman.android.core.auth.SessionStateProvider
import com.feedman.android.feature.login.LoginPlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * アプリケーション全体のシェル（Issue #29 / Req 1〜4）。
 *
 * 観測した [SessionState] に応じて、画面全体を以下のいずれかに切り替える:
 * - [SessionState.LoggedOut] → [LoginPlaceholderScreen] に差し替え（Req 3.1, 3.4）
 * - [SessionState.LoggedIn]  → [LoggedInShell]（ModalNavigationDrawer + Scaffold +
 *   TopAppBar + NavHost）を描画（Req 1.1〜1.4, 2.1〜2.6, 3.2, 3.3）
 *
 * 状態は [SessionStateProvider] から `StateFlow` で観測するため、Issue #24 で本実装に
 * 差し替えても本 Composable の構造は変わらない（Req 3.5 / NFR 2.2）。
 */
@Composable
fun AppShell() {
    val viewModel: AppShellViewModel = hiltViewModel()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    when (sessionState) {
        SessionState.LoggedOut -> LoginPlaceholderScreen()
        SessionState.LoggedIn -> LoggedInShell()
    }
}

/**
 * ログイン済みユーザー向けのドロワー付きシェル（Req 1.1〜1.4, 2.1〜2.6）。
 *
 * - `rememberNavController()` で 1 本の [NavHostController] を保持し、ルート切り替えを
 *   またいで TopAppBar + ドロワーを共有する（Req 1.4）。
 * - 上部アプリバー左端のメニューボタンでドロワーを開閉する（Req 2.1）。スクリム
 *   タップ / 横スワイプでの close は [ModalNavigationDrawer] 標準挙動に従う
 *   （Req 2.2, 2.3, 2.6）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoggedInShell() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentRouteId = navController.currentBackStackEntryAsState().value
        ?.destination?.route ?: AppRoute.Timeline.id

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentRouteId = currentRouteId,
                onSelectMainItem = { item ->
                    // Req 2.4 / 2.5: ナビゲート + ドロワー閉。
                    navController.navigate(item.targetRouteId()) {
                        // ルートをまたいで back stack を肥大化させないため、同一トップ
                        // ルート間の遷移ではトップ位置を再利用する。
                        launchSingleTop = true
                    }
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectFooterItem = {
                    // 本 Issue ではフッタ配線（Account / ThemeToggle）の遷移先が未実装。
                    // ドロワーだけは閉じて UX を破綻させない。
                    coroutineScope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.timeline_title)) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.appbar_open_drawer),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Navigation(
                navController = navController,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // Req 3.3 / 3.4: LoggedIn セッションが新たに確立されたタイミングで、ドロワー状態を
    // 確実に閉じた状態へリセットする（前回 LoggedOut へ遷移する直前にドロワーが
    // 開いていた可能性に備える）。
    LaunchedEffect(Unit) {
        drawerState.close()
    }
}

/**
 * [AppShell] が観測する [SessionStateProvider] 経由のセッション状態を保持する
 * ViewModel（Req 3.5 / NFR 2.2）。
 *
 * `StateFlow` 化は `stateIn` で行い、初期値はテストで明示的に観測されるまでの暫定値
 * として [SessionState.LoggedOut] を採用する（未認証時はログイン画面が出る安全側）。
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    sessionStateProvider: SessionStateProvider,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionStateProvider.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = sessionStateProvider.state.value,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
