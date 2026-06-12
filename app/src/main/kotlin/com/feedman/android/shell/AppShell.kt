package com.feedman.android.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.feedman.android.core.ui.LinkOpener
import com.feedman.android.feature.account.AccountSheet
import com.feedman.android.feature.account.AccountSheetViewModel
import com.feedman.android.feature.articledetail.ArticleDetailSheet
import com.feedman.android.feature.articledetail.ArticleDetailViewModel
import com.feedman.android.feature.registerfeed.RegisterFeedSheet
import com.feedman.android.feature.registerfeed.RegisterFeedViewModel
import com.feedman.android.feature.subscriptionsettings.SubscriptionSettingsSheet
import com.feedman.android.feature.subscriptionsettings.SubscriptionSettingsViewModel
import androidx.compose.ui.platform.LocalContext
import com.feedman.android.core.ui.FeedmanSnackbar
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
    val viewModel: AppShellViewModel = hiltViewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentRouteId = navController.currentBackStackEntryAsState().value
        ?.destination?.route ?: AppRoute.Timeline.id
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    // Issue #43 Req 1.1 / 1.2: 購読設定シートは LoggedInShell のスコープで 1 つ保持し、
    // ドロワー（DrawerContent.onSelectFeedSettings）と FeedScreen の設定導線の双方から
    // open(feedId) で対象を切替える。
    val subscriptionSettingsViewModel: SubscriptionSettingsViewModel = hiltViewModel()
    // Issue #44: フィード登録シートも AppShell スコープで 1 つ保持し、
    // ドロワーの + ボタンから open() で起動する。登録成功時のトーストは AppShell の
    // snackbar host で表示する（シェル直下に shellSnackbarHostState を配置）。
    val registerFeedViewModel: RegisterFeedViewModel = hiltViewModel()
    // Issue #49: アカウントシートも AppShell スコープで 1 つ保持し、ドロワーフッタの
    // 「アカウント」項目から open() で起動する。SessionState 観測経由でログイン画面に
    // 切り替わるため、UI 側で追加 navigate は行わない（onUnauthorized は no-op）。
    val accountSheetViewModel: AccountSheetViewModel = hiltViewModel()
    val shellSnackbarHostState = remember { SnackbarHostState() }
    val registerSucceededMessage = stringResource(R.string.register_feed_succeeded)
    // Issue #45 Req 3.1〜3.3: 登録は成功したが直後の購読一覧再取得が失敗したことを
    // 非ブロッキングに通知するための snackbar 文言。
    val subscriptionRefreshFailedMessage = stringResource(
        R.string.register_feed_subscription_refresh_failed,
    )

    // Req 1.1〜1.5: タイトル/サブタイトルは純粋関数で解決し、現在ルートに追従させる
    val appBarStrings = AppBarStrings(
        timelineTitle = stringResource(R.string.timeline_title),
        starredTitle = stringResource(R.string.appbar_title_starred),
        searchTitle = stringResource(R.string.appbar_title_search),
        feedFallbackTitle = stringResource(R.string.appbar_title_feed_fallback),
    )
    // Req 1.3: 個別フィードルート時のフィード名解決には Drawer の購読リストを再利用する
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val feedTitleLookup: (String) -> String? = remember(drawerUi.rows) {
        { feedId -> drawerUi.rows.firstOrNull { it.feedId == feedId }?.title }
    }
    val appBarTitle = resolveAppBarTitle(
        routeId = currentRouteId,
        feedTitleLookup = feedTitleLookup,
        strings = appBarStrings,
    )

    // Req 3.1, 3.2: 現在の表示が暗色かを解決
    val systemDark = isSystemInDarkTheme()
    val currentlyDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> systemDark
    }

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
                // #30 Req 4.1, 4.2, 4.3 / #43 Req 1.1: 設定アイコンで購読設定シートを起動 + ドロワー閉。
                onSelectFeedSettings = { row ->
                    subscriptionSettingsViewModel.open(row.feedId)
                    coroutineScope.launch { drawerState.close() }
                },
                // #31 Req 4.2, 4.3 / #49 Req 1.1 / 1.2: ヘッダのユーザー領域タップで
                // アカウントシート起動 + ドロワー閉。AccountSheetViewModel.open() が
                // 内部で /auth/me フェッチを開始する（Req 1.2）。
                onAccountAreaTap = {
                    accountSheetViewModel.open()
                    coroutineScope.launch { drawerState.close() }
                },
                // #31 Req 5.2, 5.3 / #44 Req 1.1: + ボタンでフィード登録シート起動 + ドロワー閉
                onAddFeedTap = {
                    registerFeedViewModel.open()
                    coroutineScope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(appBarTitle.title)
                            // Req 1.4: サブタイトルが定義されていれば 1 段下に表示する
                            appBarTitle.subtitle?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    navigationIcon = {
                        // Req 6.1, 6.2, 6.5
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.appbar_open_drawer),
                            )
                        }
                    },
                    actions = {
                        // Req 2.1, 2.2, 2.4: 検索アイコン → search ルートへ navigate
                        IconButton(onClick = {
                            navController.navigate(AppRoute.Search.id) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.appbar_action_search),
                            )
                        }
                        // Req 3.1, 3.2, 3.3, 3.6: テーマ切替アイコン
                        val toggleIcon = resolveThemeToggleIcon(currentlyDark)
                        IconButton(onClick = { viewModel.toggleTheme(currentlyDark) }) {
                            Icon(
                                imageVector = when (toggleIcon) {
                                    ThemeToggleIcon.SunIndicatingSwitchToLight -> Icons.Filled.LightMode
                                    ThemeToggleIcon.MoonIndicatingSwitchToDark -> Icons.Filled.DarkMode
                                },
                                contentDescription = stringResource(R.string.appbar_action_toggle_theme),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            // Issue #36 Req 1.1: 一覧から記事タップで詳細シートを起動する。
            // ArticleDetailViewModel は LoggedInShell のスコープで 1 つだけ保持し、
            // 全ルートから同じインスタンスへ open(id) を依頼する（シェル単位の起動点）。
            val articleDetailViewModel: ArticleDetailViewModel = hiltViewModel()
            // Issue #37 Req 1.1 / 2.1: 詳細シート・タイムラインの両方で共通の LinkOpener を
            // 用いる。Context は Activity/Application どちらでも startActivity 可能なので
            // `LocalContext.current` をそのまま渡す（Composable スコープに閉じる）。
            val context = LocalContext.current
            val linkOpener = viewModel.linkOpener
            Navigation(
                navController = navController,
                onOpenItemDetail = { itemId -> articleDetailViewModel.open(itemId) },
                onOpenExternalLink = { url -> linkOpener.open(context, url) },
                // Issue #43 Req 1.2: フィード別画面の設定導線から購読設定シートを開く。
                onOpenSubscriptionSettings = { feedId ->
                    subscriptionSettingsViewModel.open(feedId)
                },
                modifier = Modifier.padding(padding),
            )
            // Issue #36 Req 1.1, 1.4, 1.5: 詳細シートを LoggedInShell 直下に配置することで、
            // ルート遷移をまたいでも同じ ViewModel を使えるようにする。
            // Issue #37: onOpenExternal で LinkOpener.open(...) を呼び OpenLinkResult を
            // シートに返す。詳細シート側は失敗結果を snackbar で通知する（Req 4.3）。
            ArticleDetailSheet(
                onOpenExternal = { url -> linkOpener.open(context, url) },
                viewModel = articleDetailViewModel,
            )
            // Issue #44: フィード登録シート。LoggedInShell の Scaffold 内に配置し、
            // 登録成功時には AppShell スコープの snackbar host にトーストを流す（Req 4.2）。
            RegisterFeedSheet(
                onRegistrationSucceeded = {
                    coroutineScope.launch {
                        FeedmanSnackbar.show(shellSnackbarHostState, registerSucceededMessage)
                    }
                },
                // Issue #45 Req 3.1〜3.3: 登録後の購読一覧再取得が失敗した場合、登録成功
                // フィードバックを抑止せず追加で非ブロッキング snackbar を出す。drawer の
                // フィードセクションは独自に observeLoadState を購読しているため、本
                // snackbar は AppShell 全体の補助通知として機能する。
                onSubscriptionRefreshFailed = {
                    coroutineScope.launch {
                        FeedmanSnackbar.show(
                            shellSnackbarHostState,
                            subscriptionRefreshFailedMessage,
                        )
                    }
                },
                viewModel = registerFeedViewModel,
            )
            // AppShell スコープの snackbar host を Scaffold 内に配置する。NavHost の上に
            // 重ねて描画されるため、フィード登録シートが閉じた直後でも表示が継続する。
            SnackbarHost(hostState = shellSnackbarHostState)
            // Issue #49: アカウントシート。LoggedInShell の Scaffold 内に配置し、
            // ドロワーフッタからの起動を accountSheetViewModel.open() で受ける。
            // 認証切れ時はシート内部で Hidden に戻り、ログイン画面遷移は
            // SessionStateProvider 観測経由（AppShell の when(sessionState)）で行われる
            // ため、onUnauthorized は補助的な no-op として残す（Req 5.2 / 5.3）。
            AccountSheet(
                onUnauthorized = {
                    // SessionState 観測ベースで切り替わる。追加 navigate は不要。
                },
                viewModel = accountSheetViewModel,
            )
            // Issue #43: 購読設定シート。LoggedInShell の Scaffold 内（NavHost と同じ
            // スコープ）に配置し、解除成功時に現在の feed/{feedId} ルートと照合できる
            // ようにする（Req 4.5）。
            SubscriptionSettingsSheet(
                onUnsubscribed = { unsubscribedFeedId ->
                    // Req 4.5: 当該フィードの記事一覧を表示中なら timeline へ退避する。
                    // 現在のルート ID から feedId を抽出して比較する。
                    val currentFeedId = navController.currentBackStackEntry
                        ?.arguments?.getString(AppRoute.Feed.ARG_FEED_ID)
                    if (currentFeedId == unsubscribedFeedId) {
                        navController.navigate(AppRoute.Timeline.id) {
                            // feed/{feedId} を back stack から外し、戻る操作で戻らないようにする
                            popUpTo(AppRoute.Timeline.id) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onUnauthorized = {
                    // Req 5.3: 認証切れ時はログイン導線へ。SessionStateProvider が LoggedOut
                    // を流せば AppShell が自動でログイン画面に切替わるため、ここでは追加の
                    // 明示的な navigate は不要（観測ベースで切り替わる）。
                    // 認証セッション更新は別 Issue の AuthRepository 配下で行うため、
                    // 本実装では設定シートを閉じるだけに留める（VM 側で close 済み）。
                },
                viewModel = subscriptionSettingsViewModel,
            )
        }
    }

    // Account / FeedRegistration の placeholder シート起動経路は #44 / #49 で本実装に
    // 置き換え済み（起動点はそれぞれ registerFeedViewModel.open() / accountSheetViewModel.open()）。
    // AppShellSheet enum と activeSheet は後方互換のため残置するが、AppShellViewModel.openSheet
    // 経由での起動はいずれも参照解除済みで、本 when は実質 no-op となる。
    when (activeSheet) {
        AppShellSheet.None -> Unit
        AppShellSheet.Account -> Unit // #49 で本実装に置換済み（AccountSheet を直接起動）
        AppShellSheet.FeedRegistration -> Unit // #44 で本実装に置換済み（RegisterFeedSheet を直接起動）
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
    /**
     * Issue #37: 「元記事を開く」（詳細シートのフッタ／タイムラインカードの外部リンク）の
     * 起動を担う共通オープナー。ViewModel が保持する理由は、Composable から直接
     * `hiltViewModel()` で取れる単一のシェルスコープに集約するため。LinkOpener 自体は
     * Singleton として Hilt 上で共有される（DI / NFR 1.1）。
     */
    val linkOpener: LinkOpener,
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
