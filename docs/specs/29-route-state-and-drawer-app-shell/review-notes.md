# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-29-impl-route-state-drawer-app-shell
- HEAD commit: 0de7a53
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `Navigation.kt` の `NavHost(startDestination = AppRoute.Timeline.id, …)` で起動時に `timeline` を初期表示。`AppRouteTest.timeline ルート ID は 'timeline' である_Req 1_1` で ID 固定検証。
- 1.2 — `AppRoute.declaredRouteIds` が `timeline / feed/{feedId} / starred / search` の 4 件に固定。`AppRouteTest.宣言ルートはちょうど 4 件で…_Req 1_2` で件数と順序を検証。`Navigation.kt` の NavHost に登録されている `composable(...)` 呼び出しも当該 4 ルートに限定。
- 1.3 — `AppRoute.Feed.id = "feed/{feedId}"` + `Feed.path(feedId)` + `Navigation.kt` の `navArgument(ARG_FEED_ID)`。`AppRouteTest` に正常系（`feed/abc-123` 展開）と異常系（空 feedId で `IllegalArgumentException`）を完備。
- 1.4 — `LoggedInShell` 内で `rememberNavController()` が 1 本だけ生成され、その同一 `NavHostController` が `Navigation(...)` に渡る。`ModalNavigationDrawer` + `Scaffold` + `TopAppBar` が NavHost を内側に含む形でルート切替をまたいで保持される（Composable 構造で担保。動的検証は androidTest 範疇）。
- 1.5 — `AppRoute` sealed class の派生に記事詳細 / フィード登録 / 購読設定 / アカウントが存在しない。`AppRouteTest.記事詳細-フィード登録-購読設定-アカウントはルートに含まれない_Req 1_5` が `forbiddenIds` で構造的に検証。
- 2.1 — `AppShell.kt` の `IconButton(onClick = { coroutineScope.launch { drawerState.open() } })` で開閉ハンドラを配線。スクリム描画は `ModalNavigationDrawer` 既定挙動（Compose UI 観点は androidTest 範疇）。
- 2.2 — `ModalNavigationDrawer` 標準挙動（scrim タップで close）に委譲。Composable 単位の検証は androidTest 範疇のため JVM 単体テスト不要。
- 2.3 — `ModalNavigationDrawer` 標準挙動（左スワイプで close）に委譲。同上。
- 2.4 — `DrawerContent` 内の `onSelectMainItem(item)` → `AppShell` 側で `navController.navigate(item.targetRouteId())` + `drawerState.close()`。`DrawerItemsTest.メイン項目 Timeline は timeline ルートに対応する_Req 2_4` で純粋データ部分を検証。
- 2.5 — 同上で Starred → `starred` ルートを `DrawerItemsTest.メイン項目 Starred…_Req 2_5` で検証。
- 2.6 — `ModalNavigationDrawer` 標準挙動（ドロワー前面 + 外側入力遮断）に委譲。androidTest 範疇。
- 3.1 — `AppShell.kt` の `when (sessionState) { SessionState.LoggedOut -> LoginPlaceholderScreen() … }` で画面全体差し替え。`AppShellViewModelTest.LoggedOut を返す Provider を渡すと初期 state が LoggedOut になる_Req 3_1` で観測可能性を検証。
- 3.2 — `when` 分岐の `SessionState.LoggedIn -> LoggedInShell()`。`AppShellViewModelTest.LoggedIn を返す Provider…_Req 3_2`。
- 3.3 — `AppShellViewModelTest.LoggedOut から LoggedIn への遷移が観測される_Req 3_3` で `FakeSessionStateProvider` の値変更が `StateFlow` 経由で UI 観測値に伝播することを検証。`LoggedInShell` の `LaunchedEffect(Unit) { drawerState.close() }` で再構築時にドロワー閉状態を保証。
- 3.4 — `AppShellViewModelTest.LoggedIn から LoggedOut への遷移が観測される_Req 3_4` で逆遷移を検証。LoggedOut 分岐は `LoginPlaceholderScreen()` のみを描画するためルート / ドロワー状態は取り下げられる。
- 3.5 — `SessionStateProvider` interface + `AuthModule` の `@Binds bindSessionStateProvider` で抽象化。テストは `FakeSessionStateProvider` を渡して両状態を強制（NFR 2.2 と統合検証）。`MockModeSessionStateProviderTest` が暫定 mockMode 連動を担保。
- 4.1 — `DrawerFooterItem` enum に `KeywordNotification` を含めない構造。`DrawerItemsTest.drawerFooterItems にキーワード通知エントリが含まれない_Req 4_1_4_2` で enum 名と表示順を二重検証。
- 4.2 — フッタ遷移ハンドラ `when` 式（`DrawerContent.kt` の `DrawerFooterItem.label()`）に `KeywordNotification` 枝が生じない構造で担保（enum 列挙非存在）。同テストで網羅。
- 4.3 — `drawerFooterItems` リスト 1 箇所のみが表示順 / 表示対象を決定する。`DrawerItemsTest.drawerFooterItems は DrawerFooterItem の全列挙の部分集合である_Req 4_3` で「リスト書き換え 1 箇所で切替」できる構造を検証。
- NFR 1.1 / 1.2 — `ModalNavigationDrawer` 既定アニメーション（Compose Material3 標準 250–500ms 内）に委譲。固有計測は androidTest 範疇。
- NFR 2.1 — 現在ルート ID（`navController.currentBackStackEntryAsState`）、ドロワー開閉（`drawerState.currentValue`）、セッション状態（`AppShellViewModel.sessionState: StateFlow<SessionState>`）が UI テストから観測可能。
- NFR 2.2 — `SessionStateProvider` 経由でテスト差し替え可能。`AppShellViewModelTest.FakeSessionStateProvider` が実証。
- NFR 3.1 — `AppShell.kt` 側に `appConfig.mockMode` 直接参照を残置していない（`MockModeSessionStateProvider` のみが mockMode を読む構造）。`shell` パッケージ内 grep 確認済み。

## Findings

なし

## Summary

requirements.md の全 numeric ID（1.1〜1.5 / 2.1〜2.6 / 3.1〜3.5 / 4.1〜4.3 / NFR 1.1〜1.2 / NFR 2.1〜2.2 / NFR 3.1）が JVM 単体テスト・実装構造・Compose 標準挙動委譲のいずれかで裏付けられている。境界面（shell / core/auth / di / strings.xml / app/src/test / docs/specs）に限定されており、#30 のフィード一覧描画・#31 の上部アプリバー右側アクションへの踏み込みは無い。`./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL を確認。

RESULT: approve
