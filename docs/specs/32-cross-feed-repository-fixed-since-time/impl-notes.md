# 実装ノート: Issue #32 — Cross-feed repository with cursor pagination and fixed since_time

## 概要

横断新着タイムライン（SPEC §5.1）のデータ層 `CrossFeedRepository` を実装。
`GET /api/items/cross-feed`（SPEC §4.2）を Issue #18 の `CursorPagingSource` 経由で
読み出す `Pager<String, CrossFeedItem>` を提供し、初回レスポンスの `since_time` を
セッション中固定（SPEC §4.1）して後続ページに引き継ぐ。

## 変更ファイル

- `app/src/main/kotlin/com/feedman/android/core/data/CrossFeedRepository.kt`（新規・interface）
- `app/src/main/kotlin/com/feedman/android/core/data/CrossFeedRepositoryImpl.kt`（新規・実装）
- `app/src/main/kotlin/com/feedman/android/core/network/FeedmanApi.kt`（`getCrossFeed` に `@Query("since_time")` 追加）
- `app/src/main/kotlin/com/feedman/android/di/RepositoryModule.kt`（Hilt @Binds 追加）
- `app/src/test/kotlin/com/feedman/android/core/data/CrossFeedRepositoryImplTest.kt`（新規・全 17 ケース）
- `app/src/test/resources/fixtures/cross_feed_page_second.json`（新規・2 ページ目 fixture）

## 受入基準 → テスト対応表

| Requirement ID | テストメソッド | 検証内容 |
|---|---|---|
| Req 1.1 | `Req 1-1 initial load issues GET cross-feed with limit 50 and no cursor` | 初回は cursor / since_time 無し + limit=50 で GET /api/items/cross-feed |
| Req 1.2 | `Req 1-2 initial response since_time is captured into session state` | 初回 since_time を `currentSinceTime` に固定 |
| Req 1.3 | `Req 1-3 initial page items are returned to Paging as LoadResult Page data` | items を Paging の `LoadResult.Page.data` として返す |
| Req 1.4 | `Req 1-4 missing since_time on initial response is reported as FeedmanException` | 空 since_time → `FeedmanException(CODE_UNKNOWN_ERROR)` で `LoadResult.Error` |
| Req 2.1 | `Req 2-1 subsequent load forwards cursor and session since_time as query params` | 後続リクエストに cursor + since_time + limit=50 を付与 |
| Req 2.2 | `Req 2-2 session since_time is not overwritten by subsequent response since_time` | 後続レスポンスの since_time（異なる値）でセッション保持値を上書きしない |
| Req 2.3 | `Req 2-3 next_cursor is updated to subsequent response value` | 次キーが 2 ページ目レスポンスの next_cursor に更新（終端なら null） |
| Req 2.4 | `Req 2-1 ...` 内アサート | `limit=50` を各リクエストで付与 |
| Req 3.1 | `Req 3-1 has_more false on initial response terminates paging` | `has_more=false` で `nextKey=null` |
| Req 3.2 | `Req 2-3 ...` 内アサート | `next_cursor=null` で終端 |
| Req 3.3 | `Req 3-3 no further request is issued once terminal reached via TestPager` | 終端後の `append()` は no-op、HTTP リクエストが追加発行されない |
| Req 4.1 | `Req 4-1 refresh discards session since_time and cursor` | 新 PagingSource 生成時に `sessionSinceTime = null` |
| Req 4.2 | `Req 4-2 after refresh new initial response since_time becomes session held value` | リフレッシュ後の新 since_time が新セッションの保持値になる |
| Req 4.3 | `Req 4-3 after refresh subsequent request uses the new since_time` | リフレッシュ後の append が新 since_time を送信 |
| Req 5.1 | `Req 5-1 non-2xx response surfaces FeedmanException carrying code and message` | 401 等のエラーが `FeedmanException`（code / message / httpStatus 保持）として露出 |
| Req 5.2 | `Req 5-2 network failure surfaces FeedmanException with NETWORK_ERROR code` | ソケット切断時に `FeedmanException(CODE_NETWORK_ERROR)` |
| Req 5.3 | `Req 5-3 subsequent failure preserves session since_time` | 後続ページ取得失敗時もセッションの since_time を保持 |
| NFR 1.1 | （構造上満たす） | `FeedmanApi`（Retrofit interface）をモックせず MockWebServer で実 HTTP 経路を検証 |
| NFR 1.2 | `NFR 1-2 currentSinceTime is observable from tests without time stubbing` | `currentSinceTime` を public val として公開、時刻 stub 不要で検証可能 |
| NFR 2.1 | （構造上満たす） | `CursorPagingSource`（#18）を loader 注入で利用 |
| NFR 2.2 | （変更範囲） | 変更は `core/data` / `core/network/FeedmanApi.kt`（`@Query` 1 行追加）/ `di/RepositoryModule.kt` に限定 |

## 設計上の判断

### 1. `since_time` クエリ追加（`FeedmanApi.getCrossFeed`）

要件 2.1 で `since_time` を後続リクエストに付与する必要があるが、既存の
`getCrossFeed(cursor, limit)` には `since_time` クエリが無かった。プロンプトで
許可された「core/network は必要時のみ最小修正」に従い、`@Query("since_time") sinceTime: String? = null`
を追加。デフォルト null なので既存呼び出し（`FeedmanApiTest`）は影響なし。

### 2. 「セッション」の単位 = `PagingSource` インスタンスの生存期間

`Pager` が `pagingSourceFactory` を呼ぶたびに新しい `PagingSource` が生成される
（refresh / invalidate でも再生成される）。この生存期間を「セッション」と定義し、
`pagingSourceFactory` のラムダ内で `sessionSinceTime = null` に倒すことで、
Req 4.1（リフレッシュで since_time / cursor 破棄）を自然に表現できる。

ただし `sessionSinceTime` は `CrossFeedRepositoryImpl` の `@Volatile` フィールド
として `@Singleton` インスタンスに保持しているため、複数の `Pager` が同時並行で
動く運用では正しく動作しない。本 Issue のスコープ（タイムライン画面 1 画面 = Pager 1 本）
では十分。将来複数 Pager を同時稼働させる場合は `PagingSource` 内部状態に持たせる
リファクタが必要。**確認事項として PR 本文に記載予定**。

### 3. 初回 since_time 欠落の判定

要件 1.4 は「`since_time` が含まれていない場合」だが、`CrossFeedPage` の `sinceTime`
は `kotlinx.serialization` で **非 nullable な必須プロパティ**として宣言されている
（`core/model/Page.kt` 既存）。サーバーが key を完全欠落させた場合は decode 時に
`MissingFieldException` で `FeedmanException(UNKNOWN_ERROR)` にラップされる
（既存 `FeedmanApiCallAdapter` の挙動）。本実装では追加で「空文字列」もエラーとして
扱うガードを入れた（Req 1.4 の「含まれていない」を「実質値なし」と広めに解釈）。
この解釈は **確認事項**として PR 本文に記載。

### 4. 既存 `ItemRepository` との共存

`core/data/ItemRepository`（Issue #1 のモック用 interface・`FakeItemRepository`
バインド）は触らず、新規 `CrossFeedRepository` として別系統で追加。理由:

- `ItemRepository` は `Flow<List<MockTimelineItem>>` を返す mock 専用 API で、
  実 API の `Flow<PagingData<CrossFeedItem>>` とは型・スコープが異なる
- 一括差し替えは Issue #33（タイムライン UI）または専用 Issue で実施する方が安全
  （UI 側の依存差し替えが伴う）
- 本 Issue の Boundary は「core/data に閉じる」（NFR 2.2）。`ItemRepository` を
  破壊的に変更すると `TimelineViewModel` / `FakeItemRepositoryTest` などにも影響波及

将来 `CrossFeedRepository` が `ItemRepository` を置き換える形に統合する場合は、
別 Issue で UI 層と一緒に差し替える方針。

### 5. テストで `FixtureLoader`（internal）の利用

既存の `FixtureLoader` は `core.model` パッケージの `internal object` として宣言
されているが、同一モジュール内（app）なので Kotlin の `internal` 可視性内で
`core.data.CrossFeedRepositoryImplTest` から利用可能。新たに共通テストユーティリティを
切らずに既存資産を活用した。

## 確認事項（PR 本文「確認事項」に記載予定）

1. **`sessionSinceTime` を `@Singleton` インスタンスに `@Volatile` 保持する方式**:
   現状は Pager 1 本前提。将来複数画面で複数 Pager を同時稼働させる場合は
   PagingSource 内部状態に移譲するリファクタが必要。Issue #33 着手時に再検討。
2. **Req 1.4「since_time が含まれていない」の解釈**: kotlinx.serialization で
   非 nullable 宣言されているため key 完全欠落は decode で UNKNOWN_ERROR になる。
   追加で空文字列もエラーとして扱う実装を入れたが、サーバー実装上「空文字列を返す」
   ケースが想定されているかは仕様側で確認が必要。
3. **既存 `ItemRepository`（モック用）との共存方針**: 本 Issue では別系統として併存
   させた。Issue #33（タイムライン UI）で UI 側を実 API 連携に切替えるタイミングで
   `FakeItemRepository` の扱いを整理することを推奨。

## ビルド・テスト結果

- `./gradlew build` → BUILD SUCCESSFUL（1m 38s）
- `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.data.CrossFeedRepositoryImplTest"` → 17 / 17 PASS

## Feature Flag Protocol

対象 repo `CLAUDE.md` は `**採否**: opt-out` のため、Feature Flag 規約は適用対象外
（通常の単一実装パス）。

## Pending Tasks

なし（全 AC カバー）。

STATUS: complete
