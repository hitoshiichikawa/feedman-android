# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-32-impl-cross-feed-repository
- HEAD commit: c384722
- Compared to: origin/main..HEAD（3 commits: 要件定義 / 実装 / 実装ノート）
- 変更ファイル: 6 件（src 4 + test 2）。境界は `core/data`（新規 `CrossFeedRepository(Impl)`）/
  `core/network/FeedmanApi.kt`（`@Query("since_time")` 1 行追加）/ `di/RepositoryModule.kt`
  （Hilt @Binds 1 件追加）/ `app/src/test/...`（テスト + fixture）に閉じている

## Verified Requirements

- 1.1 — `CrossFeedRepositoryImpl.loadPage` が cursor==null 時 `limit=50` + cursor/since_time なしで
  `api.getCrossFeed` を呼ぶ。`Req 1-1 ...` テストで MockWebServer 受信クエリを実証
- 1.2 — `loadPage` 初回分岐で `sessionSinceTime = response.sinceTime` を保持。`Req 1-2 ...` で
  `currentSinceTime == "2026-06-12T09:30:00Z"` を確認
- 1.3 — `CursorPagingSource` 経由で `LoadResult.Page.data` に items を流す。`Req 1-3 ...` で
  `data[0].id` / `nextKey` を確認
- 1.4 — 空文字 since_time を `FeedmanException(CODE_UNKNOWN_ERROR)` で throw。`Req 1-4 ...` で
  `LoadResult.Error` と code、`currentSinceTime` 保持されないことを確認
- 2.1 — cursor 非 null 時 `sessionSinceTime` を `sinceTime` クエリに付与。`Req 2-1 ...` で
  MockWebServer の `cursor` / `since_time` / `limit=50` クエリを実証
- 2.2 — 後続レスポンスの since_time をセッション保持値で **上書きしない**（`if (cursor == null)`
  内でのみ代入）。`Req 2-2 ...` で fixture の `2099-12-31...` に対しても保持値が初回値の
  `2026-06-12T09:30:00Z` のままであることを確認
- 2.3 — `CursorPage(nextCursor = response.nextCursor)` → `CursorPagingSource.resolveNextKey` が
  次キーを更新。`Req 2-3 ...` で初回 nextKey と 2 ページ目（null）の遷移を確認
- 2.4 — 全ての `api.getCrossFeed` 呼び出しが `limit = pageSize (=50)`。`Req 2-1 ...` 内アサート
- 3.1 — `CursorPagingSource.resolveNextKey` が `!hasMore` で null。`Req 3-1 ...` で
  `cross_feed_page_terminal.json` 利用時 `nextKey == null` を確認
- 3.2 — 同関数が `nextCursor.isNullOrEmpty()` でも null。`Req 2-3 ...` で next_cursor=null
  fixture により確認
- 3.3 — `TestPager.append()` が終端後 `null` を返し、`server.requestCount == 2` で
  追加リクエストが発行されないことを `Req 3-3 ...` で確認
- 4.1 — `newPagingSource()` 冒頭で `sessionSinceTime = null`。`Req 4-1 ...` で破棄を確認
- 4.2 — 新セッションの初回 load 後、`currentSinceTime` が新値（`2099-12-31...`）に。`Req 4-2 ...`
- 4.3 — 新セッション append リクエストの `since_time` クエリが新値。`Req 4-3 ...` で確認
- 5.1 — `CursorPagingSource` の `catch (e: FeedmanException)` を通じ 401 が
  `code="UNAUTHORIZED" / message="認証エラー" / httpStatus=401` で透過。`Req 5-1 ...`
- 5.2 — `SocketPolicy.DISCONNECT_AT_START` で `FeedmanException(CODE_NETWORK_ERROR)`。`Req 5-2 ...`
- 5.3 — 後続 5xx でも `sessionSinceTime` 未破棄（代入は初回分岐内のみ）。`Req 5-3 ...` で
  失敗前後の `currentSinceTime` 同値を確認
- NFR 1.1 — `ApiClientFactory.create(baseUrl)` で実 Retrofit を組み立て、`FeedmanApi` を
  モックせず MockWebServer 経由で since_time / cursor / limit 受け渡しを検証
- NFR 1.2 — `currentSinceTime: String?` を `interface CrossFeedRepository` の `val` として公開し、
  `NFR 1-2 ...` で時刻 stub 無しに観測可能なことを確認
- NFR 2.1 — `CrossFeedRepositoryImpl.newPagingSource` が `CursorPagingSource(loader = ::loadPage)`
  で #18 基盤を利用
- NFR 2.2 — 変更範囲は `core/data` 新規 2 / `core/network/FeedmanApi.kt`（`@Query("since_time")`
  1 行追加、KDoc 追記のみ）/ `di/RepositoryModule.kt`（@Binds 1 件）/ `app/src/test` に閉じる。
  #33（UI）/ #38（状態更新）には踏み込んでいない

実テストは `./gradlew :app:testDebugUnitTest --tests
"com.feedman.android.core.data.CrossFeedRepositoryImplTest"` を実行し BUILD SUCCESSFUL を確認
（17 / 17 PASS。impl-notes.md の宣言と一致）。

## Findings

なし

## Summary

Issue #32 の全 AC（Req 1.1〜5.3 / NFR 1.1〜2.2）に対応する実装とテストが揃っており、
MockWebServer ベースで since_time セッション固定・cursor 引き継ぎ・終端判定・リフレッシュ
リセット・エラー透過・後続失敗時の保持を検証している。境界も `core/data` / `core/network`
最小修正 / `di` / `app/src/test` に閉じ、#33 UI・#38 状態更新には踏み込んでいない。

RESULT: approve
