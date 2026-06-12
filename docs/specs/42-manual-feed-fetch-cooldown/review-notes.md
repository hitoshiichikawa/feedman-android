# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-42-impl-manual-fetch-cooldown
- HEAD commit: a75c68fb7d8d1ec794fadae1029c81a61f94d8a2
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `FeedScreen` の `PullToRefreshBox(onRefresh = { viewModel.onPullToRefresh() })` でジェスチャ完了を捕捉し、`FeedScreenViewModel.onPullToRefresh()` が `subscriptionRepository.fetch(currentSub.id)` を呼ぶ（`FeedScreenViewModel.kt`）。検証: `FeedScreenViewModelTest#onPullToRefresh で SubscriptionRepository_fetch が呼ばれ FetchSucceeded を流す_Issue42 Req 1_1 2_1` および `SubscriptionRepositoryImplTest#Issue42 Req 1_1 fetch で api subscriptions id fetch を POST する`
- 1.2 — `_fetchInProgress` を進行中フラグとして公開し、UI 側の `PullToRefreshBox(isRefreshing = fetchInProgress)` で連動。検証: `FeedScreenViewModelTest#onPullToRefresh は進行中の追加起動を抑止する_Issue42 Req 1_4`（true への遷移を観測）
- 1.3 — `PullToRefreshBox` はモーダルではなく overlay インジケータであり、フィルタタブ/バナーは `PullToRefreshBox` の外側（`Column` 直下）に配置されておりタップ可能。一覧側も内部の `FeedScreenListContent` は `LazyColumn` で操作可能なまま
- 1.4 — `onPullToRefresh` 冒頭で `if (_fetchInProgress.value) return`。検証: `FeedScreenViewModelTest#onPullToRefresh は進行中の追加起動を抑止する_Issue42 Req 1_4`（fetch 呼び出しが 1 回に抑制）
- 2.1 — 成功時 `FetchSucceeded` を emit、`FeedScreen` 側の `LaunchedEffect` でイベント受領時に `pagingItems.refresh()` を実行。検証: `FeedScreenViewModelTest#... FetchSucceeded を流す_Issue42 Req 1_1 2_1`（成功イベント発火を観測）
- 2.2 — `try/finally` の finally で `_fetchInProgress.value = false`。検証: `FeedScreenViewModelTest#onPullToRefresh 完了後 fetchInProgress が false に戻る_Issue42 NFR 1_2`
- 2.3 — `SubscriptionRepositoryImpl.fetch()` が `_subscriptions.update` で該当エントリを置換し、`observeSubscriptions` 経由でドロワーへ反映。検証: `SubscriptionRepositoryImplTest#Issue42 Req 2_3 fetch 成功で観測中の Subscription が unread count 更新を反映する`（unread_count 12 → 17 が observe ストリームに伝搬）
- 2.4 — 一覧 Flow は触らず Paging 側 `LazyPagingItems.refresh()` のみで再評価。Paging 3 + `LazyColumn.items(key = id)`（既存）でスクロール位置/同一 ID アイテムが維持される。
- 3.1 — `FeedmanException.code == CODE_FEED_COOLDOWN` で `FetchCooldown(retryAfterSeconds)` を emit、UI が `feed_fetch_cooldown_with_seconds` で snackbar 表示。検証: `FeedScreenViewModelTest#... FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2` および `SubscriptionRepositoryImplTest#Issue42 Req 3_1 fetch がクールダウン応答時 FEED_COOLDOWN と retryAfterSeconds 付きで例外を投げる`（HTTP 429 + retry_after_seconds=30 を MockWebServer 経由で確認）
- 3.2 — `FetchCooldown.retryAfterSeconds` がサーバー値 30 をそのまま透過（同上テスト）。UI は `getString(R.string.feed_fetch_cooldown_with_seconds, seconds)` で format
- 3.3 — `retryAfterSeconds = null` のとき `cooldownNoSecondsMessage`（`feed_fetch_cooldown_no_seconds`）に分岐。検証: `FeedScreenViewModelTest#... retryAfterSeconds 欠落のとき null を流す_Issue42 Req 3_3`
- 3.4 — クールダウン例外も catch で event emit のみ。`SubscriptionRepositoryImpl.fetch` は失敗時 `_subscriptions` を変更しないため購読リスト/一覧が保持される。検証: `SubscriptionRepositoryImplTest#Issue42 Req 3_1 ...`（購読 unread_count が before と同値で保持）
- 4.1 — その他 `FeedmanException` は `FetchFailed(message = e.errorMessage.ifBlank { FALLBACK_UNKNOWN_MESSAGE })` で emit。検証: `FeedScreenViewModelTest#onPullToRefresh がその他エラーのとき FetchFailed を message 付きで流す_Issue42 Req 4_1` および `SubscriptionRepositoryImplTest#Issue42 Req 4_1 fetch その他のエラー時に例外を伝搬し購読リストを変えない`
- 4.2 — 失敗時も `_subscriptions` 不変・一覧 Flow 非介入（同上 Repository テスト）
- 4.3 — `code == CODE_NETWORK_ERROR` 時 `FALLBACK_NETWORK_MESSAGE` を採用。検証: `FeedScreenViewModelTest#onPullToRefresh がネットワークエラーのとき FetchFailed をネットワーク文言で流す_Issue42 Req 4_3`
- 4.4 — `try/finally` で失敗系も `_fetchInProgress.value = false`（success 系と同一 `finally`。コード経路で網羅）
- NFR 1.1 — `_fetchInProgress.value = true` を `viewModelScope.launch` の外側で同期的に設定するため、ジェスチャ完了と同 frame でインジケータが ON になる
- NFR 1.2 — `finally` ブロックで同期的に false 復帰（`FeedScreenViewModelTest#... fetchInProgress が false に戻る_Issue42 NFR 1_2`）
- NFR 2.1 — `PullToRefreshBox` は overlay インジケータのみでモーダルダイアログを表示しない。バナー/フィルタタブを `PullToRefreshBox` の外側に配置することで一覧外操作も継続可能

## Boundary 検証

- 変更ファイルは `core/data/SubscriptionRepository(Impl).kt` / `core/network/FeedmanException.kt`（定数追加のみ）/ `feature/feed/*` / `app/src/main/res/values/strings.xml` / `app/src/test/...`（テスト追加）と `docs/specs/42-...`。`feature/timeline/*`（#34）への変更は無く横断タイムラインの refresh 挙動は保たれている。逸脱なし

## Feature Flag Protocol

- 対象 repo の `CLAUDE.md` の `## Feature Flag Protocol` 採否は `opt-out`。flag 細目の確認は適用せず

## テスト実行

- `./gradlew :app:testDebugUnitTest --tests FeedScreenViewModelTest --tests SubscriptionRepositoryImplTest` → BUILD SUCCESSFUL（FROM-CACHE / 既存 build と一致）

## Findings

なし

## Summary

要件 1〜4 の全 AC・NFR 1.1/1.2/2.1 に対応する実装とテストを確認。Boundary 逸脱なし、テスト緑。

RESULT: approve
