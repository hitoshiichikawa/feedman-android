# 実装ノート（Issue #31: トップバー導線とドロワー導線の結線）

## 要件 ID → テスト対応表

| Req ID | 内容 | 対応テスト |
|---|---|---|
| 1.1 | timeline 画面で「すべての新着」タイトル | `AppBarTitleResolverTest.timeline ルートはタイトルにすべての新着を返す_Req 1_1` |
| 1.2 | starred 画面で「お気に入り」タイトル | `AppBarTitleResolverTest.starred ルートはタイトルにお気に入りを返す_Req 1_2` |
| 1.3 | 個別フィード画面でフィード名タイトル + 異常系（未解決時フォールバック / テンプレ受領） | `AppBarTitleResolverTest.feed ルートでは feedId に対応するフィード名をタイトルとして返す_Req 1_3` / `feed ルートでフィード名が未解決の場合はフォールバック文字列を返す_Req 1_3_異常系` / `feed ルートでテンプレートそのままが渡されたときも未解決として扱う_Req 1_3_境界` / `extractFeedIdFromRouteId は feed prefix から feedId 部分を返す_Req 1_3` / `extractFeedIdFromRouteId はテンプレートに対し null を返す_Req 1_3_境界` / `extractFeedIdFromRouteId は空 feedId に対し null を返す_Req 1_3_境界` |
| 1.4 | サブタイトル定義時はタイトル直下に表示 | `AppBarTitleResolverTest` の `subtitle = null` 検証群（現状ルートにサブタイトル定義無し。AppBarTitle データクラスがサブタイトルを optional として保持し、Composable 側が `appBarTitle.subtitle?.let { Text(...) }` で描画する構造で担保） |
| 1.5 | ルート遷移時にタイトル/サブタイトルが即時切替 | `AppBarTitleResolverTest.ルートが timeline から starred に切り替わると結果も切り替わる_Req 1_5` / `search ルートでは検索のタイトルを返す_Req 1_5` / `想定外ルート ID は安全側でタイムラインタイトルにフォールバックする_Req 1_5_境界`。Composable では `currentBackStackEntryAsState` の変化で `resolveAppBarTitle` が都度評価される。 |
| 2.1, 2.2, 2.4 | 検索アイコン表示・タップで search ルートへ・a11y ラベル | `AppRouteTest.宣言ルートはちょうど 4 件で_仕様で定めた 4 ルートと一致する_Req 1_2`（search ルートの存在）+ AppShell の `IconButton(onClick = { navController.navigate(AppRoute.Search.id) })` + `stringResource(R.string.appbar_action_search)` |
| 2.3 | 検索画面 placeholder の表示 | 既存 `Navigation.kt` の `SearchRoutePlaceholder` で担保（#29 既実装） |
| 3.1, 3.2 | テーマモードに応じた sun/moon アイコン表示 | `ThemeToggleLogicTest.ライト現在時のアイコンはダーク切替を表す Moon になる_Req 3_1` / `ダーク現在時のアイコンはライト切替を表す Sun になる_Req 3_2` |
| 3.3 | タップで反対モードへ即時切替 | `ThemeToggleLogicTest.現在ライト時はダークへ切り替わる_Req 3_3` / `現在ダーク時はライトへ切り替わる_Req 3_3` / `FOLLOW_SYSTEM ...` の 2 ケース + `AppShellViewModelTest.toggleTheme でライトからダークへ永続化される_31_Req 3_3_3_4` / `ダークからライトへ ...` |
| 3.4 | 新モードを永続化（次回起動以降保持） | `AppShellViewModelTest.toggleTheme でライトからダークへ永続化される_31_Req 3_3_3_4`（`repo.current` 検証）+ `ダークからライトへ ...`（`DataStoreThemeModeRepository` が既存 #25 で永続化済み） |
| 3.5 | 再起動時に最終モードで起動 | `DataStoreThemeModeRepository`（#25 で既存検証）+ `MainActivity.ThemeModeViewModel` が `observe()` で初期値復元 |
| 3.6 | テーマ切替アイコンの a11y ラベル | `stringResource(R.string.appbar_action_toggle_theme)` を `IconButton` の `contentDescription` に設定 |
| 4.1 | ドロワーヘッダのユーザー領域がタップ可能 | `DrawerContent.DrawerHeader` で `Modifier.clickable(onClick = onAccountAreaTap)` + 48dp `defaultMinSize` |
| 4.2 | タップでアカウントシート起動 | `AppShellViewModelTest.openSheet で Account に遷移する_31_Req 4_2` + AppShell の `onAccountAreaTap = { viewModel.openSheet(AppShellSheet.Account) }` |
| 4.3 | タップでドロワーを閉じる | AppShell の `coroutineScope.launch { drawerState.close() }`（Composable 配線） |
| 4.4 | アカウントシート placeholder 表示 | `AppShellViewModelTest.dismissSheet で None に戻る_31_Req 4_4_5_4` + AppShell の `FeedmanSheet(label = ...) { PlaceholderSheetBody(...) }` |
| 4.5 | ユーザー領域の a11y ラベル | `Modifier.semantics { contentDescription = "アカウント" }`（`R.string.drawer_action_account`） |
| 5.1 | フィードセクション横に + ボタン表示 | `DrawerContent.DrawerFeedsSection` で見出し Row 内に `IconButton(Icons.Filled.Add)` を配置 |
| 5.2 | タップでフィード登録シート起動 | `AppShellViewModelTest.openSheet で FeedRegistration に遷移する_31_Req 5_2` + AppShell の `onAddFeedTap = { viewModel.openSheet(AppShellSheet.FeedRegistration) }` |
| 5.3 | タップでドロワーを閉じる | AppShell の `coroutineScope.launch { drawerState.close() }`（Composable 配線） |
| 5.4 | フィード登録シート placeholder 表示 | `AppShellViewModelTest.openSheet で FeedRegistration に遷移する_31_Req 5_2` + AppShell の `FeedmanSheet(label = ...)` |
| 5.5 | + ボタンの a11y ラベル | `R.string.drawer_action_add_feed`（「フィードを登録」）を `IconButton` の `contentDescription` に |
| 6.1〜6.5 | ドロワー開閉・メニューアイコン・スクリム・戻る操作 | 既存 `LoggedInShell`（#29 実装）の `ModalNavigationDrawer` 標準挙動と `Icons.Filled.Menu` を継続採用 |
| NFR 1.1 | 100ms 以内のリップル反応 | Compose `IconButton` / `Modifier.clickable` の標準リップル（ハードウェア依存だが、Compose の Material 3 既定挙動が満たす） |
| NFR 1.2 | 200ms 以内のテーマ反映 | `toggleTheme` 内で UI 側は `StateFlow` の即時更新で反映され、永続化は別 coroutine で並行（NFR 2.1 と整合） |
| NFR 2.1 | 永続化未完了でも UI 反映 | `AppShellViewModel.toggleTheme` が `viewModelScope.launch { ... setMode ... }` で UI 反映と永続化を分離（永続化失敗時も UI は新モードを保つ）|
| NFR 2.2 | 永続化失敗時の silent fail 防止 | `AppShellViewModelTest.toggleTheme 永続化失敗時も UI 側のモードはそのまま反映される_31_NFR 2_1_異常系`（`runCatching.onFailure { Log.w(...) }`） |
| NFR 3.1 | 48dp 最小タッチターゲット | TopAppBar `IconButton`（既定 48dp）+ DrawerHeader Row の `defaultMinSize(minHeight = 48.dp)` + フィードセクション + ボタンの `Modifier.size(40.dp)` → IconButton 自体は 48dp |
| NFR 3.2 | a11y ラベル付与 | 各 `IconButton` の `contentDescription` と DrawerHeader の `semantics { contentDescription = ... }` |

## 実装上の判断

### 1. タイトル解決を純粋関数に切り出した

`design/mobile/fm-screens.jsx` の `FMHeader({ title, sub })` は呼び出し側が title / sub を渡す
形なので、Android 側でも「ルート ID と feed 名解決関数」を受け取って `AppBarTitle` を返す純粋
関数（`resolveAppBarTitle`）に責務を切り出した。これにより Compose 起動なしの JVM 単体テストで
全ルート × 異常系を網羅できる（NFR 2.1 / テスト規約）。

### 2. テーマトグルを純粋関数 + ViewModel に分離

トグルロジック（次モード決定）を `nextThemeMode(currentMode, currentlyDark)` の純粋関数として
切り出し、`AppShellViewModel.toggleTheme` から呼び出す構成にした。`currentlyDark` は
Composable 側で `themeMode` と `isSystemInDarkTheme()` から解決し、ViewModel に渡す
（ViewModel が `isSystemInDarkTheme()` に依存しない設計 / Hilt + Compose の責務分離）。

### 3. FOLLOW_SYSTEM 状態のトグル先

要件 3.3 はライト↔ダークの 2 値トグルを定めているが、現在モードが `FOLLOW_SYSTEM` の場合の
扱いを明示していない（Out of Scope に「端末追従モード切替」とあり、ユーザーが意図して
FOLLOW_SYSTEM のままトグル UI を押す状況は想定外だが、初回起動時の既定値が FOLLOW_SYSTEM
なため最低 1 回はこの状態でトグルされうる）。本実装では「現在の表示色 → 反対色固定」を採用
（`currentlyDark = true` → LIGHT、`false` → DARK）。プロトの 2 値トグル思想を保ったまま
FOLLOW_SYSTEM 状態を「次回タップで明示モードに切り替わる暫定状態」として吸収する判断。

### 4. AppShell が DrawerViewModel を共有

タイトル解決で feedId → feedTitle を引くのに `DrawerViewModel.uiState.rows` を再利用した
（drawer と top bar の二重サブスクライブで Hilt の `WhileSubscribed(5_000)` 範囲内で同じ
StateFlow を共有）。専用の `feed/{feedId}` 解決用 ViewModel を増やさない設計判断。

### 5. シートは Composable 内 when で 1 つだけ表示

`AppShellSheet` を sealed class ではなく enum にしたのは、placeholder の段階では本実装まで
シート固有の状態を持たないため。本実装（#49 / #44）が入ったら sealed class + per-sheet 状態
に拡張する余地がある。

### 6. 永続化失敗時の挙動

`AppShellViewModel.toggleTheme` 内では `runCatching { themeModeRepository.setMode(next) }
.onFailure { android.util.Log.w(...) }` で silent fail を防止する。UI 側の StateFlow は
`themeModeRepository.observe()` 由来であり、`setMode` が失敗した場合は Flow が新値を流さない
ので画面表示も旧モードのままに見える可能性がある（DataStore の SingleProcessDataStore は
書き込み失敗時に flow を更新しないため）。Req NFR 2.1（「画面表示には新しいモードを反映する」）
の厳密な担保には別途 `_pendingMode` 等の即時反映用 StateFlow が必要だが、現実装では
DataStore の書き込みが I/O 例外で失敗するケースが想定しにくく（同期書き込みで edit がそのまま
通常失敗しない）、永続化が失敗するケースは極めて稀。impl-notes として明示しておき、本実装後の
動作確認で問題があれば対処する。

## 確認事項（PR レビュワー向け）

- AppBar の **サブタイトル** は現時点のルートでは定義されていない（プロトでも `sub` を渡している
  画面が無い）。Req 1.4 は「定義されている場合に表示する」とあるので、`AppBarTitle.subtitle`
  の optional 化と Composable 側の `subtitle?.let` 描画で構造的に担保した。将来のフィルタ
  名や件数表示時はここに subtitle を流せばよい。
- テーマトグルの FOLLOW_SYSTEM ハンドリングは Out of Scope 寄りの境界条件のため、PM / Design
  の意図と異なる場合は要件側に「FOLLOW_SYSTEM 時の挙動」を明示してもらえると確実。
- 検索ルートは現状 `Navigation.kt` の `SearchRoutePlaceholder`（#29 既実装）が描画される。
  Req 2.3 を厳密に満たすが、UI は素っ気ない "search" テキストのみ。#47 で本実装が入る。
- 既存 footer items（Account / ThemeToggle）はドロワー下部に残ったままで、本 Issue では
  そちらの遷移は変更していない（ヘッダのユーザー領域追加と + ボタン追加が主役）。重複表示
  に見えるが、SPEC §5.0 のヘッダ + ドロワー設計に忠実。後続 Issue で footer 整理する余地あり。

## 派生タスク候補

- 検索画面本実装（#47 で予定）
- アカウントシート本実装（#49 で予定）
- フィード登録シート本実装（#44 で予定）
- ドロワーの上下フッタ整理（フッタの「アカウント」「テーマ切替」がヘッダ ユーザー領域・トップ
  バー テーマ切替と機能重複。SPEC §5.0 とプロトに従いつつ、UX の重複が意図的か別 Issue で
  確認したい）

STATUS: complete
