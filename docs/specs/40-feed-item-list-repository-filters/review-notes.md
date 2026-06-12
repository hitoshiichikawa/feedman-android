# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-40-impl-feed-item-list-repository
- HEAD commit: 1311aad
- Compared to: origin/main..HEAD
- 変更ファイル: `app/src/main/kotlin/com/feedman/android/core/data/FeedItemsRepository.kt`（新規）、
  `…/FeedItemsRepositoryImpl.kt`（新規）、`app/src/main/kotlin/com/feedman/android/di/RepositoryModule.kt`（追記）、
  `app/src/test/kotlin/com/feedman/android/core/data/FeedItemsRepositoryImplTest.kt`（新規）、
  `app/src/test/resources/fixtures/item_summary_page_second.json`（新規）、本 Issue spec 配下 2 ファイル
- Feature Flag Protocol: opt-out（CLAUDE.md `## Feature Flag Protocol` 節）→ flag 観点の確認は適用外

## Verified Requirements

- 1.1 — `FeedItemFilter.ALL.queryValue = "all"` を `api.getFeedItems(filter = filter.queryValue)` で送出
  （`FeedItemsRepositoryImpl.loadPage`）。テスト `Req 1-1 initial load with ALL filter forwards filter=all on the request` が
  `recorded.requestUrl?.queryParameter("filter") == "all"` を実 HTTP 経路で検証
- 1.2 — `FeedItemFilter.UNREAD.queryValue = "unread"`。テスト `Req 1-2 …forwards filter=unread on the request`
- 1.3 — `FeedItemFilter.STARRED.queryValue = "starred"`。テスト `Req 1-3 …forwards filter=starred on the request`
- 1.4 — `loadPage` は `feedId` をそのまま `api.getFeedItems(feedId = feedId)` に渡し、normalize / trim / encode を行わない。
  テスト `Req 1-4 feed id is embedded into request path verbatim without modification` が 2 種類の生 feedId
  （ULID 風 / ハイフン入り）でパス埋め込みを検証
- 2.1 — `pagingData(feedId, filter)` 呼び出しごとに新規 `Pager` を返し、`pagingSourceFactory` が新 `PagingSource` を生成。
  新 PagingSource の初回 `LoadParams.Refresh(key = null)` で cursor なしリクエストになる。
  テスト `Req 2-1 changing filter starts from head with new cursor-less request` が ALL→UNREAD 切替で
  2 番目のリクエストが `filter=unread` かつ `cursor` 未指定であることを確認
- 2.2 — `flowAll` と `flowUnread` が別 `Pager.flow` であることをアサート（`Req 2-2 …does not carry previous accumulated pages …`）。
  別 Flow = 別 Pager = 蓄積を共有しない設計
- 2.3 — `loadPage` が前ページの `nextCursor` をそのまま次回 `LoadParams.Append(key)` から `cursor` クエリに搬送。
  テスト `Req 2-3 same filter subsequent load forwards previous next_cursor as cursor query` が
  `cursor=2026-06-10T08:00:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3P2` の搬送を検証
- 3.1 — `CursorPagingSource.resolveNextKey` が `!page.hasMore` で `null` を返す既存実装に委譲。
  テスト `Req 3-1 has_more false on response terminates paging with null nextKey`
- 3.2 — 同 `resolveNextKey` が `next.isNullOrEmpty()` で `null` を返す。
  テスト `Req 3-2 null next_cursor terminates …` と `Req 3-2 empty string next_cursor terminates …` の 2 ケース
- 3.3 — `TestPager.append()` が `nextKey == null` 時に no-op になる Paging 3 規約。
  テスト `Req 3-3 no further request is issued once terminal reached via TestPager` が `server.requestCount == 2` を確認
- 3.4 — 初回 4xx で `LoadResult.Error(FeedmanException)` が露出。
  テスト `Req 3-4 initial load failure surfaces as LoadResult Error with FeedmanException`（401 / UNAUTHORIZED）と
  `Req 3-error network failure surfaces FeedmanException with NETWORK_ERROR code`（接続切断時の境界値）
- 3.5 — 1 ページ目成功 → 2 ページ目 500 のシナリオで、1 ページ目データ（`firstResult.data.size == 2`）を破棄せず
  `LoadResult.Error` を露出。テスト `Req 3-5 subsequent load failure surfaces error without discarding previous page`
- 3.6 — 失敗時と同じ `LoadParams.Append(key = nextKey)` で `source.load` を再呼び出しした際、
  feedId / filter / cursor が完全一致することをアサート。
  テスト `Req 3-6 retry after failure reissues request with same feedId filter and cursor`
- 4.1 — リフレッシュ = 同 filter で `newPagingSource` を再生成して `LoadParams.Refresh(key = null)` で再開。
  テスト `Req 4-1 refresh on same filter restarts from head with cursor unset and same filter` が
  `filter=starred` 維持と `cursor` 未指定を検証
- 4.2 — リフレッシュ後の先頭ページの `has_more=false` で `nextKey` が `null` になる。
  テスト `Req 4-2 after refresh next_cursor and has_more are handled by the same paging rules`
- NFR 1 — 差分は `core/data`（リポジトリ 2 ファイル）/ `di/RepositoryModule.kt`（追記のみ）/ `app/src/test` /
  spec docs に限定。`FeedmanApi` / `core/network/paging` / 既存 Repository / feature 配下に手を入れていないことを
  `git diff --stat origin/main..HEAD` で確認
- NFR 2.1 — 3 値の MockWebServer 検証は Req 1-1 / 1-2 / 1-3 で網羅
- NFR 2.2 — Req 2〜4 の異常系・境界値（フィルタ変更時の先頭再取得 / `has_more=false` / `next_cursor=null` /
  `next_cursor=""` / 初回失敗 / 追加ロード失敗 / 再試行 / リフレッシュ / NETWORK_ERROR）を 13 テストで網羅

加えて `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.data.FeedItemsRepositoryImplTest"` を
再実行し BUILD SUCCESSFUL（FROM-CACHE = impl-notes.md の Green 表明と整合）を確認。

## Findings

なし

## Summary

3 値の `?filter=` 送出（Req 1.1–1.3）、feedId のパス埋め込み無改変（Req 1.4）、フィルタ変更時の
先頭ページ再取得と蓄積非持ち越し（Req 2.1–2.2）、`next_cursor` 搬送（Req 2.3）、終端判定 3 系統
（Req 3.1–3.3）、エラー透過 / 既存ページ温存 / 再試行同一クエリ（Req 3.4–3.6）、リフレッシュ
（Req 4.1–4.2）のすべてが MockWebServer 経路で 1 対 1 にカバーされており、AC 未カバー / missing test /
boundary 逸脱のいずれも検出されない。差分は `core/data` + `di` + `app/src/test` に閉じ、#41 UI と
#42 手動フェッチには触れていない。`./gradlew :app:testDebugUnitTest` で全 16 ケース Green。

RESULT: approve
