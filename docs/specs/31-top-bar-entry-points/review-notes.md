# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-31-impl-top-bar-entry-points
- HEAD commit: cd66834
- Compared to: origin/main..HEAD
- 変更ファイル: shell 配下 4 ファイル / strings.xml / 単体テスト 3 ファイル / docs/specs 配下 2 ファイル

## Verified Requirements

- 1.1 — `AppBarTitleResolver.resolveAppBarTitle`（ROUTE_TIMELINE 分岐）+ `AppBarTitleResolverTest.timeline ルートはタイトルにすべての新着を返す_Req 1_1`
- 1.2 — `resolveAppBarTitle`（ROUTE_STARRED 分岐）+ `starred ルートはタイトルにお気に入りを返す_Req 1_2`
- 1.3 — `resolveAppBarTitle`（feed/ prefix + feedTitleLookup）+ `feed ルートでは feedId に対応するフィード名をタイトルとして返す_Req 1_3` / 異常系 / 境界 + `extractFeedIdFromRouteId` 系テスト 4 件
- 1.4 — `AppBarTitle.subtitle: String? = null` + AppShell の `appBarTitle.subtitle?.let { Text(...) }` 描画。現状ルートに subtitle 定義はないが構造的に optional 対応済み
- 1.5 — AppShell の `navController.currentBackStackEntryAsState()` から都度 `resolveAppBarTitle` が評価される構造 + `ルートが timeline から starred に切り替わると結果も切り替わる_Req 1_5` / `search ルートでは検索のタイトルを返す_Req 1_5` / `想定外ルート ID は安全側でタイムラインタイトルにフォールバックする_Req 1_5_境界`
- 2.1 — AppShell の `TopAppBar.actions` 内 検索 IconButton（`Icons.Filled.Search`）
- 2.2 — 検索 IconButton の `onClick = { navController.navigate(AppRoute.Search.id) }`
- 2.3 — 既存 `SearchRoutePlaceholder`（#29 実装、Navigation.kt）が search ルートで描画される
- 2.4 — `contentDescription = stringResource(R.string.appbar_action_search)`（「検索」）
- 3.1 — `resolveThemeToggleIcon(currentlyDark = false) → MoonIndicatingSwitchToDark` + `ライト現在時のアイコンはダーク切替を表す Moon になる_Req 3_1`
- 3.2 — `resolveThemeToggleIcon(currentlyDark = true) → SunIndicatingSwitchToLight` + `ダーク現在時のアイコンはライト切替を表す Sun になる_Req 3_2`
- 3.3 — `nextThemeMode` 純粋関数 + `AppShellViewModel.toggleTheme` + `toggleTheme でライトからダークへ永続化される_31_Req 3_3_3_4` / `ダークからライトへ ...` / FOLLOW_SYSTEM 2 ケース
- 3.4 — `viewModelScope.launch { themeModeRepository.setMode(next) }` で永続化 + `repo.current` 検証
- 3.5 — 既存 `DataStoreThemeModeRepository` (#25) + `MainActivity.ThemeModeViewModel` の `observe()` で初期値復元（本 PR で破壊されていないことを diff で確認）
- 3.6 — `contentDescription = stringResource(R.string.appbar_action_toggle_theme)`（「テーマ切替」）
- 4.1 — `DrawerContent.DrawerHeader` 内の `Row` に `clickable` + `defaultMinSize(minHeight = 48.dp)` 適用
- 4.2 — `AppShell.onAccountAreaTap = { viewModel.openSheet(AppShellSheet.Account) }` + `openSheet で Account に遷移する_31_Req 4_2`
- 4.3 — `onAccountAreaTap` 内 `coroutineScope.launch { drawerState.close() }`（AppShell.kt L154）
- 4.4 — `FeedmanSheet(label = ...) { PlaceholderSheetBody(...) }` で placeholder 描画 + `dismissSheet で None に戻る_31_Req 4_4_5_4`
- 4.5 — DrawerHeader Row の `semantics { contentDescription = "アカウント" }`（`R.string.drawer_action_account`）
- 5.1 — `DrawerFeedsSection` 見出し Row 内の `IconButton(Icons.Filled.Add)`
- 5.2 — `AppShell.onAddFeedTap = { viewModel.openSheet(AppShellSheet.FeedRegistration) }` + `openSheet で FeedRegistration に遷移する_31_Req 5_2`
- 5.3 — `onAddFeedTap` 内 `coroutineScope.launch { drawerState.close() }`（AppShell.kt L159）
- 5.4 — `AppShellSheet.FeedRegistration` 分岐で placeholder 描画 + `dismissSheet で None に戻る_31_Req 4_4_5_4`
- 5.5 — `contentDescription = stringResource(R.string.drawer_action_add_feed)`（「フィードを登録」）
- 6.1 — TopAppBar の `navigationIcon = IconButton(Icons.Filled.Menu)`（既存 #29 + 本 PR で contentDescription 維持）
- 6.2 — `onClick = { coroutineScope.launch { drawerState.open() } }`
- 6.3 — `ModalNavigationDrawer` 標準挙動（スクリムタップで close）
- 6.4 — `ModalNavigationDrawer` 標準挙動（システムバックで close）
- 6.5 — `contentDescription = stringResource(R.string.appbar_open_drawer)`（「メニューを開く」）
- NFR 1.1 — Compose `IconButton` / `Modifier.clickable` 標準リップル
- NFR 1.2 — `themeMode` `StateFlow` の即時更新（`AppShellViewModel.themeMode` 由来）
- NFR 2.1 — `viewModelScope.launch` で永続化と UI 反映を分離（`toggleTheme 永続化失敗時も UI 側のモードはそのまま反映される_31_NFR 2_1_異常系`）
- NFR 2.2 — `runCatching { ... }.onFailure { Log.w(TAG, ...) }` で silent fail 防止 + テストで `writeAttempts > 0` 検証
- NFR 3.1 — TopAppBar `IconButton`（既定 48dp）+ DrawerHeader Row の `defaultMinSize(minHeight = 48.dp)` + ドロワー + ボタンの `Modifier.size(40.dp)`（IconButton 内蔵 48dp タッチターゲット）
- NFR 3.2 — 全 IconButton の `contentDescription` + DrawerHeader の `semantics { contentDescription = ... }`

## Boundary 検証

- 変更ファイルは全て shell 配下 / strings.xml / app/src/test / docs/specs 配下に限定されており、本 Issue のスコープ（シェル結線 + placeholder）から逸脱なし
- #47 検索画面・#49 アカウントシート・#44 フィード登録シートの本実装には踏み込んでおらず、placeholder シート / 既存 SearchRoutePlaceholder で代替
- Feature Flag Protocol は `opt-out` のため flag 観点の追加チェックは不要

## テスト実行確認

- `./gradlew :app:testDebugUnitTest --tests "...AppBarTitleResolverTest" --tests "...ThemeToggleLogicTest" --tests "...AppShellViewModelTest" --rerun-tasks` を BUILD SUCCESSFUL で確認
- 純粋関数 (`resolveAppBarTitle` / `nextThemeMode` / `resolveThemeToggleIcon`) と ViewModel (`AppShellViewModel`) のテストはすべて green

## Findings

なし

## Summary

全 AC (Req 1〜6 + NFR 1〜3) に対応する実装と JVM 単体テストが揃っており、純粋関数化されたタイトル解決・テーマトグルロジックがテスト網羅されている。Boundary 逸脱なし、placeholder シート方針も要件通り。JVM テスト green を確認。

RESULT: approve
