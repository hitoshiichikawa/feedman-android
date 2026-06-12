# Implementation Notes — Issue #40 Feed item list repository with filters and pagination

## 概要

`GET /api/feeds/{id}/items?filter=all|unread|starred`（SPEC §4.2）を Pager 経由で読み出す
`FeedItemsRepository` を `core/data` に追加した。フィルタは `FeedItemFilter` enum（ALL /
UNREAD / STARRED）で表現し、`queryValue` プロパティで `?filter=` クエリ文字列へ 1:1 で射影する。
ページング基盤は Issue #18 の `core/network/paging`（`CursorPagingSource` / `CursorPage`）に
委譲し、フィード別固有の責務は「リクエスト組み立て（feedId / filter / cursor）」と
「フィルタ変更時の Pager 再生成（呼び出し側 API）」のみに閉じる。

## 設計判断

### 1. `pagingData(feedId, filter)` は呼び出しごとに新しい Pager を返す

requirements.md 本文中に「filter ごとに pager 生成する API 設計で可。#32 の流儀に揃える」と
明記された方針に従い、`FeedItemsRepository#pagingData` は引数の (feedId, filter) を受けて
**呼び出しごとに新しい `Pager` を生成**する設計とした。

- 利点: UI 側が filter 切替時に `pagingData` を呼び直すだけで Req 2.1 / 2.2（フィルタ変更時の
  先頭ページ再取得 / 前フィルタ蓄積を持ち越さない）が自然に満たされる。横断新着リポジトリ
  （Issue #32 `CrossFeedRepositoryImpl#pagingData()` — 引数なし）と粒度的に一貫している。
- 検討した別案: 「filter を内部に持たせ `setFilter()` メソッドで切り替える」案は、状態が
  Repository に閉じ込められて UI の単方向データフロー（CLAUDE.md「単方向データフロー」）と
  噛み合わず、テスト時の filter 変更観測も難しくなるため不採用。

### 2. ページング基盤への委譲範囲

Req 3.1〜3.3（終端判定: `has_more=false` / `next_cursor=null` / 空文字列 / 終端後の追加 no-op）と
Req 3.4 / 3.5（エラー透過）は `CursorPagingSource` がすでに satisfy しており、本リポジトリでは
loader 関数を渡すだけで再利用できた（Req 3.6 の retry も Paging 3 が同一 key で `load()` を
再呼び出しする規約に乗っている）。横断新着の `since_time` のようなセッション固有クエリは
本エンドポイントには存在しないため、`CrossFeedRepositoryImpl` のような状態フィールド
（`sessionSinceTime`）は持たない。

### 3. `feedId` のパス埋め込み

Retrofit `@Path("id") feedId: String` にそのまま渡している。`FeedmanApi.getFeedItems` は
既存実装で Path / Query 構造が SPEC §4.2 と一致しており、本 Issue では FeedmanApi 側に
変更を加えていない（NFR 1: 変更範囲限定の遵守）。Req 1.4「リポジトリで書き換えない」は
コード上で feedId に対する文字列操作（normalize / trim / encode）を一切行わないことで担保。

## Requirement ID → 検証テスト対応表

すべての検証は `app/src/test/kotlin/com/feedman/android/core/data/FeedItemsRepositoryImplTest.kt`
（MockWebServer + ApiClientFactory による実 HTTP 経路）で実施。

| Requirement ID | 検証テスト |
|---|---|
| Req 1.1 (filter=all) | `Req 1-1 initial load with ALL filter forwards filter=all on the request` |
| Req 1.2 (filter=unread) | `Req 1-2 initial load with UNREAD filter forwards filter=unread on the request` |
| Req 1.3 (filter=starred) | `Req 1-3 initial load with STARRED filter forwards filter=starred on the request` |
| Req 1.4 (feedId 改変なし) | `Req 1-4 feed id is embedded into request path verbatim without modification` |
| Req 2.1 (filter 変更で先頭から) | `Req 2-1 changing filter starts from head with new cursor-less request` |
| Req 2.2 (前フィルタの蓄積を持ち越さない) | `Req 2-2 changing filter does not carry previous accumulated pages into new paging state` |
| Req 2.3 (next_cursor の搬送) | `Req 2-3 same filter subsequent load forwards previous next_cursor as cursor query` |
| Req 3.1 (has_more=false 終端) | `Req 3-1 has_more false on response terminates paging with null nextKey` |
| Req 3.2 (next_cursor=null / 空文字列) | `Req 3-2 null next_cursor terminates paging even if has_more true` / `Req 3-2 empty string next_cursor terminates paging even if has_more true` |
| Req 3.3 (終端後 no-op) | `Req 3-3 no further request is issued once terminal reached via TestPager` |
| Req 3.4 (初回失敗 → Error 露出) | `Req 3-4 initial load failure surfaces as LoadResult Error with FeedmanException` / `Req 3-error network failure surfaces FeedmanException with NETWORK_ERROR code`（ネットワーク異常系の境界値） |
| Req 3.5 (追加ロード失敗で既存ページ温存) | `Req 3-5 subsequent load failure surfaces error without discarding previous page` |
| Req 3.6 (retry で同一クエリ再発行) | `Req 3-6 retry after failure reissues request with same feedId filter and cursor` |
| Req 4.1 (リフレッシュは先頭から / 同 filter) | `Req 4-1 refresh on same filter restarts from head with cursor unset and same filter` |
| Req 4.2 (refresh 後も Req 3 規則を踏襲) | `Req 4-2 after refresh next_cursor and has_more are handled by the same paging rules` |
| NFR 2.1 (filter 3 値の MockWebServer 検証) | Req 1-1 / 1-2 / 1-3 で網羅 |
| NFR 2.2 (Req 2〜4 の異常系・境界値) | Req 2-1〜2-3 / 3-1〜3-6 / 4-1〜4-2 で網羅 |

## 確認事項（PR 本文で言及推奨）

- 本 Issue では design.md / tasks.md は無く requirements.md のみが前提だった。実装は
  `CrossFeedRepositoryImpl`（Issue #32）の流儀（Pager + CursorPagingSource 委譲、internal
  `newPagingSource` をテスト用に公開）に揃えており、将来別の一覧 Repository を追加する際の
  雛形になる。
- `FeedItemFilter` enum は `core/data` に置いた（フィルタ条件は data 層が API クエリへ射影する
  関心事のため）。UI（Issue #41）で同じ enum を Tab の選択状態として直接消費する想定だが、
  将来 enum を model 層に移したくなった場合の名前空間衝突は無い。
- フィードステータス警告バナー（active/stopped/error）や `POST .../resume` 呼び出しは
  Out of Scope（requirements.md 明記）。本リポジトリは Subscription の情報を取得しない。

## 実行結果

- `./gradlew build`: 成功（lint / unit test / release ビルドすべて通過）
- 新規追加テストすべて Green（16 ケース）

STATUS: complete
