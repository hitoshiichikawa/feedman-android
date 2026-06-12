# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-18-impl-cursor-pagination-foundation
- HEAD commit: 7bed8a4
- Compared to: origin/main..HEAD
- 変更ファイル:
  - `app/src/main/kotlin/com/feedman/android/core/network/paging/CursorPage.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/core/network/paging/CursorPagingSource.kt`（新規）
  - `app/src/test/kotlin/com/feedman/android/core/network/paging/CursorPagingSourceTest.kt`（新規）
  - `gradle/libs.versions.toml`（`androidx-paging-common` / `androidx-paging-testing` 追記）
  - `app/build.gradle.kts`（`paging-runtime` / `paging-common` を implementation、`paging-testing` を testImplementation 追加）
  - `docs/specs/18-cursor-pagination-foundation-with-paging-3/requirements.md` / `impl-notes.md`
- ビルド・テスト: `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.network.paging.CursorPagingSourceTest"` BUILD SUCCESSFUL

## Verified Requirements

- **1.1** — `CursorPagingSource.resolveNextKey` が `hasMore=true` かつ `nextCursor` 非空のとき当該値を nextKey として返す。`load returns LoadResult Page carrying next_cursor when has_more is true (Req 1-1, 2-1)` および `load forwards next_cursor opaquely to subsequent load (Req 1-1, 1-3)` で append 時に同カーソルが loader に渡ることを検証。
- **1.2** — `load()` 内で `params.key` を `cursor: String?` として loader に渡し、Refresh の初期 key=null は null として透過。`load with null key on Refresh means initial fetch with no cursor (Req 1-2)` で確認。
- **1.3** — `next_cursor` をパースせず文字列として透過。テストでは `OPAQUE::eyJ0eXAiOiJ4In0.ABCDE-_==` を loader に逆流させて bit-equal を assert。
- **2.1** — `if (!page.hasMore) return null`。`load returns terminal nextKey when has_more is false (Req 2-1)` で `next_cursor` 非 null でも nextKey が null になることを確認。
- **2.2** — `if (next.isNullOrEmpty()) return null` で null / 空文字列の両方を終端化。`load returns terminal nextKey when next_cursor is null (Req 2-2)` と `... when next_cursor is empty string (Req 2-2)` の 2 ケース。SPEC §4.1 line 113「`has_more === false` または `next_cursor` が null/空 のとき終端」と論理 OR で整合。
- **2.3** — `terminal page is not followed by additional load via TestPager (Req 2-3)` で `TestPager.append()` が終端到達後 null を返し、loader 呼び出し回数が 2 で止まることを assert。
- **3.1** — `catch (e: FeedmanException)` と `catch (e: IOException)` の両方が `LoadResult.Error` に詰める。`initial FeedmanException is exposed as LoadResult Error` / `initial IOException is exposed as LoadResult Error` で同一インスタンスが露出することを `assertSame` で検証。
- **3.2** — `append FeedmanException is exposed as LoadResult Error without dropping loaded pages (Req 3-2)` で `TestPager.getPages()` に refresh 結果が残存することを確認。
- **3.3** — `retry re-issues request with the same cursor after append failure (Req 3-3)` で append 失敗後の再 `source.load(Append(failedKey))` が同じ `failedKey` で loader を呼ぶことを `loader.calls = [null, "c2", "c2"]` で assert。Paging 3 の `retry()` 契約に委譲する設計と整合。
- **4.1** — `getRefreshKey` は常に `null` 固定。`getRefreshKey always returns null so refresh restarts from the top (Req 4-1)` および `refresh starts from initial cursor null (Req 4-1)` で 2 回連続 Refresh が key=null から再開することを確認。
- **4.2** — `refresh after successful load applies same terminal rules as Req 1 and 2 (Req 4-2)` で refresh 後の先頭ページが `next_cursor=""` のとき終端化されることを assert。
- **5.1** — コンストラクタ引数は `loader: suspend (String?) -> CursorPage<T>` のみで特定のエンドポイントを知らない。`loader is the only injection point and source is endpoint-agnostic (Req 5-1)` で `T=Int` の任意 loader を注入して動作確認。
- **5.2** — `CursorPagingSource<T : Any>` の generic 設計と `T=String` / `T=Int` の両テスト併存で 4 種一覧（横断新着 / フィード別 / スター / 検索）への横展開可能性を担保。
- **NFR 1.1** — `git diff --stat origin/main..HEAD` で変更が `core/network/paging` パッケージ配下、対応する `app/src/test/.../paging`、gradle 配線（`libs.versions.toml` / `app/build.gradle.kts`）、`docs/specs/18-...` 配下に閉じることを確認。既存 Repository・画面・他レイヤーの公開 API を改変していない。
- **NFR 2.1** — 13 ケースで Req 1〜4 各 AC を正常系 + 異常系・境界値（`has_more=false` / `next_cursor=null` / `next_cursor=""` / 初回 `FeedmanException` 失敗 / 初回 `IOException` 失敗 / append 失敗 / リフレッシュ）に最低 1 件ずつ網羅。

## Findings

なし。

## Summary

requirements.md の全 numeric ID（1.1〜5.2、NFR 1.1 / 2.1）について実装と AC 単位テストの双方が確認できた。SPEC §4.1 の終端条件（`has_more=false` または `next_cursor` が null/空）と論理 OR で整合し、Paging 3 の `retry()` 契約に委ねた Req 3.3 も `TestPager`/直接 load 呼び出しで再現確認済み。変更は `core/network/paging` + gradle 配線に閉じており boundary 逸脱も無い。`./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL。

RESULT: approve
