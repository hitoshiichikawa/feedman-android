# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-34-impl-timeline-refresh-scroll
- HEAD commit: 86a29e72d69bbb0fd4c01dd88b40be9ab889aaa6
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `TimelineScreen.kt` `PullToRefreshBox(onRefresh = { items.refresh() })` で先頭ページ再取得を起動
- 1.2 — `isRefreshing = refresh is LoadState.Loading && items.itemCount > 0` により上部 indicator 表示
- 1.3 — `isRefreshing` が `NotLoading` で false に戻り、PullToRefreshBox の Compose 規約により indicator が消える
- 1.4 — `items.refresh()` で PagingSource が再生成され、`CrossFeedRepositoryImpl`（#32）の規約として `sessionSinceTime` がリセット → 新しい初回レスポンスで再固定
- 1.5 — TimelineScreen は CrossFeedRepository 経由のみで API を叩く構造（per-feed fetch 呼び出しは差分に存在しない）
- 2.1 — Paging 3 / LazyPagingItems の規約による末尾近接時の append 自動起動
- 2.2 — `resolveListFooterState` が Loading を返したとき `LoadingFooter()` を `item(key = "footer-loading")` で挿入
- 2.3 — `resolveListFooterState` が `None` を返すことで Loading 表示が消える（既存 ListFooterStateTest でカバー）
- 2.4 — `isEndOfPagination = append is LoadState.NotLoading && append.endOfPaginationReached` を `EndOfListFooter` に変換
- 2.5 — Paging 3 規約（`endOfPaginationReached=true` で append 要求が発行されない）により構造的に担保
- 3.1 — `TimelineScreenStateTest."Req 3_1 refresh loading and item count zero returns InitialLoading"` ＋ `LoadingFullScreen()` 描画
- 3.2 — `TimelineScreenStateTest."Req 3_2 refresh error and item count zero returns InitialError"` ＋ `ErrorFullScreen(onRetry = items::retry)` 描画
- 3.3 — `ErrorFullScreen(onRetry = { items.retry() })` ＋ Paging 3 の `retry()` 規約（refresh エラーは refresh から再起動）
- 3.4 — `TimelineScreenStateTest."boundary item count one returns Content regardless of refresh state"` で itemCount=1 が常に Content
- 4.1 — `ErrorFooter(onRetry = { items.retry() })` を `item(key = "footer-error")` で挿入
- 4.2 — `resolveTimelineScreenState` が `itemCount > 0` を最優先で Content に倒すため、append エラー時も一覧を保持
- 4.3 — `ErrorFooter` の onRetry が `items.retry()` を呼び、append エラーは Paging 3 の規約で append から再開
- 4.4 — append 成功で `resolveListFooterState` が `None` を返し ErrorFooter が消える（既存 ListFooterStateTest でカバー）
- 5.1 — refresh が `Error` に遷移したとき `isRefreshing` が false になり PullToRefreshBox の indicator が消える
- 5.2 — `LaunchedEffect` で Loading→Error の遷移かつ itemCount>0 のときに `FeedmanSnackbar.show(snackbarHostState, refreshErrorMessage)` を発火（`R.string.timeline_refresh_error` を strings.xml に追加）
- 5.3 — `TimelineScreenStateTest."Req 5_3 refresh error with positive item count keeps Content"` + CrossFeedRepository 側で LoadResult.Error 時にデータ・since_time を破棄しない既存規約（#32）
- 6.1 — `TimelineScreenStateTest."Req 6_1 refresh not loading and item count zero returns Empty"` ＋ `EmptyState` 描画
- 6.2 — `EmptyState` を `PullToRefreshBox` の内側に配置することで empty 状態でも pull-to-refresh を受け付ける
- 6.3 — `resolveTimelineScreenState` が `itemCount > 0` で Content を返すため、refresh 成功で空状態が解除される（テストでカバー）
- NFR 1.1 / 1.2 — TimelineScreen は CrossFeedRepository 以外の API を呼ばない（構造的担保）
- NFR 2.1 — `TimelineScreenState` sealed interface ＋ `when` 分岐で画面全体状態を排他化
- NFR 2.2 — `PullToRefreshBox` の上部 indicator と `LoadingFooter` の末尾 indicator が別 Composable として視覚的に区別される
- NFR 3.1 / 3.2 — `LoadingFullScreen` / `ErrorFullScreen` / `EmptyState` / `LoadingFooter` / `ErrorFooter` / `EndOfListFooter`（#28 / core/ui）をそのまま再利用

## Findings

なし

## Test Execution

- `./gradlew :app:testDebugUnitTest --tests TimelineScreenStateTest --tests ListFooterStateTest` を独立に再実行 → `BUILD SUCCESSFUL`（全 pass）
- `TimelineScreenStateTest` は refresh × itemCount の全組合せ（境界 itemCount=1 / refresh=Error+itemCount>0 / refresh=Loading+itemCount>0 / NotLoading の endOfPaginationReached 真偽両方）を網羅
- Composable 描画（PullToRefreshBox の indicator / Snackbar 発火 / フッタ挿入の視覚検証）は instrumented test の領分のため JVM 単体テストの対象外（判定基準により missing test とはしない）

## Boundary Check

差分対象ファイルは以下に限定され、すべて Issue #34 の許容境界（feature/timeline / core/ui の後方互換拡張 / strings.xml / app/src/test / docs/specs）に閉じている:

- `app/src/main/kotlin/com/feedman/android/core/ui/ListFooterState.kt`（既存ファイルへの `TimelineScreenState` / `resolveTimelineScreenState` 追加。既存 API には非破壊）
- `app/src/main/kotlin/com/feedman/android/feature/timeline/TimelineScreen.kt`（feature/timeline 内）
- `app/src/main/res/values/strings.xml`（snackbar 文言を追加）
- `app/src/test/kotlin/com/feedman/android/core/ui/TimelineScreenStateTest.kt`（新規 JVM 単体テスト）
- `docs/specs/34-timeline-refresh-and-infinite-scroll/{requirements,impl-notes}.md`

#38 楽観更新スコープへの踏み込みなし（`onStarToggle = { _, _ -> /* no-op */ }` のまま）。per-feed fetch (`POST /api/feeds/{id}/fetch`) への参照なし（grep 確認済み）。

## Summary

全 numeric requirement ID（1.1〜6.3 / NFR 1.1〜3.2）に対応する実装または既存規約による構造的担保が確認でき、状態解決の純粋ロジック `resolveTimelineScreenState` には refresh × itemCount の全組合せ＋境界（itemCount=1）の JVM 単体テストが追加されている。境界違反・per-feed fetch 呼び出し・#38 スコープへの踏み込みは検出されず、`./gradlew :app:testDebugUnitTest` も green。

RESULT: approve
