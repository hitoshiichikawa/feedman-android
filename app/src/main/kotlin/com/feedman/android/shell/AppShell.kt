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
import com.feedman.android.core.designsystem.ThemeMode
import com.feedman.android.core.designsystem.ThemeModeRepository
import com.feedman.android.feature.login.LoginPlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                // #30 Req 3.1, 3.2, 3.3: フィード行タップで feed/{feedId} へ遷移 + ドロワー閉。
                onSelectFeed = { row ->
                    navController.navigate(AppRoute.Feed.path(row.feedId)) {
                        launchSingleTop = true
                    }
                    coroutineScope.launch { drawerState.close() }
                },
                // #30 Req 4.1, 4.2, 4.3: 設定アイコンは #43 のシート起動入口として配線のみ。
                // 本 Issue ではコールバックだけを受けて no-op（ドロワーは開いたまま / Req 4.3）。
                onSelectFeedSettings = { /* #43 で設定シートを起動する。配線済み no-op。 */ },
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
 * App Shell が起動するボトムシートの種別（Issue #31 / Req 4.2, 5.2）。
 *
 * - [None]: 起動中シートなし（初期値）
 * - [Account]: ドロワーヘッダのユーザー領域から起動する placeholder シート（Req 4.2, 4.4）
 * - [FeedRegistration]: ドロワーの + ボタンから起動する placeholder シート（Req 5.2, 5.4）
 *
 * 本実装（#49 / #44）が入るまでの間、UI シェルとして閉じた状態を担保する placeholder
 * シートを表示するための起動点として使う。
 */
enum class AppShellSheet {
    None,
    Account,
    FeedRegistration,
}

/**
 * [AppShell] が観測する [SessionStateProvider] 経由のセッション状態と、シート起動状態を
 * 保持する ViewModel（Req 3.5 / NFR 2.2 / Issue #31 Req 4.2, 5.2）。
 *
 * `StateFlow` 化は `stateIn` で行い、初期値はテストで明示的に観測されるまでの暫定値
 * として [SessionState.LoggedOut] を採用する（未認証時はログイン画面が出る安全側）。
 *
 * シート起動状態 [activeSheet] は [MutableStateFlow] で UI から書き換え可能とし、
 * - [openSheet] で指定種別のシートを起動
 * - [dismissSheet] で `None` に戻す
 *
 * テーマモード [themeMode] は [ThemeModeRepository] を観測し、[toggleTheme] で現在の表示色
 * （`currentlyDark`）を引数に取って次モードへ即時切替 + 永続化する（Issue #31 Req 3.3, 3.4）。
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    sessionStateProvider: SessionStateProvider,
    private val themeModeRepository: ThemeModeRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionStateProvider.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = sessionStateProvider.state.value,
    )

    val themeMode: StateFlow<ThemeMode> = themeModeRepository.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ThemeMode.DEFAULT,
    )

    private val _activeSheet = MutableStateFlow(AppShellSheet.None)
    val activeSheet: StateFlow<AppShellSheet> = _activeSheet.asStateFlow()

    /** 指定種別の placeholder シートを起動する（Req 4.2 / 5.2）。 */
    fun openSheet(sheet: AppShellSheet) {
        _activeSheet.value = sheet
    }

    /** シートを閉じる（ドラッグ下げ / スクリムタップ / ハードウェアバック）。 */
    fun dismissSheet() {
        _activeSheet.value = AppShellSheet.None
    }

    /**
     * テーマを次モードへ即時切替し、永続化する（Req 3.3, 3.4）。
     *
     * @param currentlyDark 現在の表示が暗色であれば `true`。FOLLOW_SYSTEM 時の判定に使う。
     */
    fun toggleTheme(currentlyDark: Boolean) {
        val next = nextThemeMode(themeMode.value, currentlyDark)
        // Req 3.3: 画面表示には先行して新モードを適用させる。Flow が直後に同じ値を流すため、
        // UI 側の `themeMode` も同じ値に追従する。永続化は viewModelScope 内で非同期に行う
        // （NFR 2.1: 永続化未完了状態でも UI を新モードに更新する）。
        viewModelScope.launch {
            // NFR 2.2: silent fail を避けるため try/catch でログを残しつつ UI 側は新モードを保つ
            runCatching { themeModeRepository.setMode(next) }
                .onFailure { android.util.Log.w(TAG, "テーマモードの永続化に失敗: ${it.message}", it) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "AppShellViewModel"
    }
}
