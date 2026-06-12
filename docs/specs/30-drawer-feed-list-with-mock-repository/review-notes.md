# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-30-impl-drawer-feed-list-mock
- HEAD commit: 5b9d3b1
- Compared to: origin/main..HEAD
- 変更ファイル: `core/data/SubscriptionRepository.kt`（新規）/ `core/data/fake/FakeSubscriptionRepository.kt`（新規）/ `di/RepositoryModule.kt` / `shell/AppShell.kt` / `shell/DrawerContent.kt` / `shell/DrawerFeedRow.kt`（新規）/ `shell/DrawerViewModel.kt`（新規）/ `res/values/strings.xml` / `app/src/test/` 配下 3 ファイル / spec 2 ファイル
- 検証: `./gradlew testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（追加テスト含む全 JVM 単体テスト green）

## Verified Requirements

- 1.1 — `DrawerViewModel.uiState` を `DrawerContent` が `collectAsStateWithLifecycle` で購読し `DrawerFeedRowItem` を描画。`DrawerViewModelTest.uiState はリポジトリの順序のまま行へ変換する_Req 1_5` + `FakeSubscriptionRepositoryTest.observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3` で経路を担保
- 1.2 — `DrawerContent.DrawerFeedRowItem` Row 内の並び（favicon → title → status → unread badge → settings IconButton）で構成。`DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4` で構成要素抽出を検証
- 1.3 — `DrawerViewModelTest.リポジトリが空のとき uiState_rows も空_Req 1_3` で空リスト時の rows 空が検証されている
- 1.4 — `DrawerContent.kt` の Title `Text` に `maxLines = 1` + `TextOverflow.Ellipsis` を適用（Composable 直適用のため JVM テスト不要）
- 1.5 — `DrawerViewModel` は `map` のみで並び替えなし。`DrawerViewModelTest` で feedId 順序を、`FakeSubscriptionRepositoryTest.複数回購読しても同じ順序のスナップショットが返る_Req 1_5` でリポジトリ側順序安定性を検証
- 1.1.1 — `DrawerFeedRow.shouldShowUnreadBadge` + UI 条件分岐。`DrawerFeedRowTest.unread が 1 以上のときは未読バッジを表示する_Req 1_1_1`
- 1.1.2 — `DrawerFeedRowTest.unread が 0 のときは未読バッジを非表示にする_Req 1_1_2`（負値境界も追加検証）
- 2.1 — `FeedStatusIcon.from("stopped") = Stopped`。`DrawerFeedRowTest.feed_status stopped のとき停止アイコンを選択する_Req 2_1`
- 2.2 — `FeedStatusIcon.from("error") = Error` → `Icons.Filled.Warning` + `colorScheme.error`。`DrawerFeedRowTest.feed_status error のとき警告アイコンを選択する_Req 2_2`
- 2.3 — `FeedStatusIcon.from("active") = None` + `DrawerFeedRowTest.feed_status active のとき状態アイコンを表示しない_Req 2_3`
- 2.4 — `DrawerFeedRowItem` Row の並びで `FeedStatusIconView` を unread `UnreadBadge` の左に配置（構造的保証）
- 3.1 — `AppShell.onSelectFeed` で `navController.navigate(AppRoute.Feed.path(row.feedId))`。Row 全体の `clickable` で発火
- 3.2 — `AppShell.onSelectFeed` 内 `coroutineScope.launch { drawerState.close() }`
- 3.3 — `DrawerFeedRow.from` が `subscription.feedId` をそのまま渡す。`DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4` で `feedId = "feed-42"` を検証
- 4.1 — `IconButton(onClick = { onSelectFeedSettings(row) })` を `DrawerContent.kt` で配線。AppShell が no-op コールバックを注入
- 4.2 — Row の `clickable` と `IconButton` を独立 click target にすることで構造的に排他化（Compose 標準動作）
- 4.3 — `AppShell.onSelectFeedSettings` が no-op で `drawerState.close()` を呼ばない
- 5.1 — `SubscriptionRepository.observeSubscriptions(): Flow<List<Subscription>>`。`FakeSubscriptionRepositoryTest.observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3`
- 5.2 — `FakeSubscriptionRepositoryTest.モックデータに active stopped error の状態がそれぞれ 1 件以上含まれる_Req 5_2`
- 5.3 — `flowOf(MOCK_SUBSCRIPTIONS)` による単発即時 emit。同上テストで担保
- 5.4 — 公開フィールドは `Subscription` モデル（`feedId` / `feedTitle` / `faviconUrl` / `unreadCount` / `feedStatus`）をそのまま流用。`FakeSubscriptionRepositoryTest.モックデータに favicon data URL と null の両方が含まれる_Req 5_4` + `DrawerFeedRowTest` の変換テストで全フィールド抽出を検証
- NFR 1.1 — `DrawerFeedRowItem` の並び順を `design/mobile/fm-screens.jsx` FMFeedListBody に合わせる構造（人間視覚レビュー）
- NFR 1.2 — `Text(maxLines = 1, overflow = TextOverflow.Ellipsis)` 適用
- NFR 1.3 — `strings.xml` に `drawer_feed_*` 5 件追加。ハードコード文字列なし
- NFR 2.1 — `DrawerViewModelTest.リポジトリが新しいリストを emit すると uiState に反映される_NFR 2_1` で MutableStateFlow 経由の差分反映を検証
- NFR 3.1 — `RepositoryModule` で `SubscriptionRepository` を Fake にバインド。テストでは `StubSubscriptionRepository` 差替で検証
- NFR 3.2 — `DrawerContentStateless` を internal 抽出し `onSelectFeed` / `onSelectFeedSettings` コールバックを引数化。Compose UI Test 追加は別 Issue 委ね妥当

## Findings

なし

## Boundary 検証

変更ファイルはいずれも許可範囲内に閉じている:
- `core/data/SubscriptionRepository.kt` / `core/data/fake/FakeSubscriptionRepository.kt` — core/data 境界内
- `di/RepositoryModule.kt` — di 境界内（Fake バインドの 1 行追加のみ）
- `shell/AppShell.kt` / `shell/DrawerContent.kt` / `shell/DrawerFeedRow.kt` / `shell/DrawerViewModel.kt` — shell 境界内
- `res/values/strings.xml` — strings.xml 境界内
- `app/src/test/` 配下 3 ファイル — test 境界内
- `docs/specs/30-…/` の `impl-notes.md` / `requirements.md` — spec 境界内

#39 実 API 統合・#43 設定シート本体への踏み込みなし（設定アイコンタップは no-op コールバックでの配線のみ。`AppShell` のコメントで #43 に委譲する旨を明示）。

## Summary

全 numeric requirement ID（1.1〜1.5 / 1.1.1〜1.1.2 / 2.1〜2.4 / 3.1〜3.3 / 4.1〜4.3 / 5.1〜5.4 / NFR 1.1〜1.3 / NFR 2.1 / NFR 3.1〜3.2）が実装またはテストでカバーされている。JVM 単体テストは追加分含め全 green。境界逸脱・missing test なし。

RESULT: approve
