# Implementation Notes — Issue #18 Cursor pagination foundation with Paging 3

## 概要

`core/network/paging/` にカーソル方式 PagingSource 基盤を実装した。SPEC §4.1 の
`{ items, next_cursor, has_more }` envelope を Paging 3 に橋渡しする `CursorPagingSource<T>`
を中心に、4 種一覧（横断新着 / フィード別 / スター / 検索）で再利用できる loader 注入型の
設計とした。横断新着固有の `since_time` 固定（Issue #32）と各画面 Repository 配線・UI 表示は
本 Issue の Out of Scope。

## 変更ファイル

- `app/src/main/kotlin/com/feedman/android/core/network/paging/CursorPage.kt` (新規)
- `app/src/main/kotlin/com/feedman/android/core/network/paging/CursorPagingSource.kt` (新規)
- `app/src/test/kotlin/com/feedman/android/core/network/paging/CursorPagingSourceTest.kt` (新規)
- `gradle/libs.versions.toml` (`androidx-paging-common` / `androidx-paging-testing` を追記)
- `app/build.gradle.kts` (`paging-runtime` / `paging-common` を implementation に、
  `paging-testing` を testImplementation に追加)

## requirement ID → テスト対応表

| Requirement ID | 対応テスト（`CursorPagingSourceTest` 内のテスト名要約） | 観点 |
|---|---|---|
| 1.1 | `load returns LoadResult Page carrying next_cursor when has_more is true` / `load forwards next_cursor opaquely to subsequent load` | next_cursor を次キーとして送出 |
| 1.2 | `load with null key on Refresh means initial fetch with no cursor` | 初回はカーソル未指定 |
| 1.3 | `load forwards next_cursor opaquely to subsequent load` / `Req 5-1` の loader 注入テスト | 不透明トークンとして透過 |
| 2.1 | `load returns terminal nextKey when has_more is false` | has_more=false で終端 |
| 2.2 | `load returns terminal nextKey when next_cursor is null` / `load returns terminal nextKey when next_cursor is empty string` | null / 空文字列で終端 |
| 2.3 | `terminal page is not followed by additional load via TestPager` | 終端後 append 発行されない |
| 3.1 | `initial FeedmanException is exposed as LoadResult Error` / `initial IOException is exposed as LoadResult Error` | 初回エラー露出 |
| 3.2 | `append FeedmanException is exposed as LoadResult Error without dropping loaded pages` | 追加ロード失敗時に既読ページ温存 |
| 3.3 | `retry re-issues request with the same cursor after append failure` | 同じカーソルで再要求 |
| 4.1 | `refresh starts from initial cursor null` / `getRefreshKey always returns null so refresh restarts from the top` | リフレッシュは先頭ページから |
| 4.2 | `refresh after successful load applies same terminal rules as Req 1 and 2` | リフレッシュ後も終端規則を再適用 |
| 5.1 | `loader is the only injection point and source is endpoint-agnostic` | loader 差し替え可能 |
| 5.2 | `loader is the only injection point and source is endpoint-agnostic`（任意 T 型で動作） + 上記 Req 1〜4 各テストでも `String` 以外の値も同基盤で扱える設計を担保 | 一覧 4 種で同一挙動を提供 |
| NFR 1.1 | git diff が `core/network/paging` 配下と gradle 配線のみ（既存 Repository / 画面実装に touch せず） | 変更範囲限定 |
| NFR 2.1 | Req 1〜4 各 AC に正常系 + 異常系・境界値（has_more=false / next_cursor=null / next_cursor="" / 初回失敗 / 追加失敗 / リフレッシュ）を最低 1 ケース | テスト網羅性 |

## 設計判断の記録

### Key 型を `String` にした理由

Paging 3 の `PagingSource<Key : Any, Value : Any>` は両型パラメータが non-null 制約を持つ
（最初 `String?` を試したが `Type argument is not within its bounds` で reject された）。
- `LoadParams<Key>.key` 自身は nullable で、Refresh 時は null を保持する。
- このため初回ロード判定は「`params.key == null` のとき loader に `cursor=null` を渡す」で
  Req 1.2 を満たし、Key 型 non-null との両立が問題にならない。
- 公開 API の Key 型を `String?` にすると Paging の generic 制約に違反するため、`String` を
  採用した。

### `CursorPage<T>` を独立型として導入した理由

- `core/model/Page<T>`（kotlinx.serialization の `@Serializable` 付きデータクラス）と
  `CrossFeedPage`（`since_time` を含む別 envelope）を同じ基盤で扱う必要がある（Req 5.2）。
- サーバー envelope を直接受けるのではなく中間型 `CursorPage<T>` を通すことで、
  - 横断新着の `since_time` 固定（#32）が本基盤の公開 API に波及しない
  - 将来 envelope に項目追加があっても Repository 側の詰め替えで吸収できる
- 代替案として `Page<T>` を直接 loader 戻り値にする案も検討したが、`CrossFeedPage` が
  `Page<T>` と継承関係を持たない設計（`core/model/Page.kt` 既存コメントで明文化済）と
  整合させるため、独立した中間型を選んだ。

### `getRefreshKey` を常に `null` にした理由

- カーソル方式は anchor item から「ひとつ前のページのカーソル」を機械的に逆算する手段が
  ない（next_cursor は不透明トークンで、逆走しない）。
- Req 4.1（リフレッシュは先頭ページから取得を再開する）と整合させるため、
  `getRefreshKey` は常に null を返し、Pager 側の Refresh が key=null で `load()` を
  呼ぶ動作にそのまま揃えた。

### 終端判定で `next_cursor=null/空` を `has_more=true` より優先した理由

サーバーが矛盾した envelope（`has_more=true` かつ `next_cursor=null/""`）を返した場合、
本基盤は次キーを発行しないことで終端扱いに倒す。理由:
- 不正な不透明トークン（`null` / `""`）で再要求しても 4xx になるだけで価値がない
- 「無限にローディングが回り続けない」（Req 2 Objective）を機械的に担保する保守側挙動
- SPEC §4.1 自身が「`has_more === false` または `next_cursor` が null/空 のとき終端」と
  明記しており、論理 OR で判定するのが SPEC 準拠

### エラー伝播で `FeedmanException` / `IOException` のみを LoadResult.Error に詰める理由

- `FeedmanException` は `FeedmanErrorMappingInterceptor` / `FeedmanApiCallAdapter` が
  正規化した「API 由来のエラー」を表す唯一の型（#17）。Req 3.1 / 3.2 はこれを露出する。
- `IOException` は Retrofit/OkHttp が低レイヤで投げる接続失敗。Paging 3 の retry セマンティクスに
  乗せるためにキャッチして Error に変換する。
- その他の `Throwable`（プログラミングバグ等）はキャッチせず再 throw し、SDK 側の uncaught
  経路に乗せる（silent fail 回避）。

### Paging 3 の `retry()` セマンティクスへの委譲

Req 3.3「失敗した位置のページ要求を同じカーソルで再発行する」を独自実装せず、Paging 3 の
retry 機構に委ねた。理由:
- `LoadResult.Error` を返した場合、`PagingDataAdapter.retry()` は **同じ `LoadParams`**（=
  同じ key）で `load()` を再呼び出しする契約。
- これを直接実装で再現するより、Paging 3 が既に提供する仕組みに乗る方が安全（仕様逸脱が
  起きない）。
- テストでは TestPager の `append()` が同 key で再 load する挙動を直接呼び出して再現
  （`retry re-issues request with the same cursor after append failure`）。

## 追加した依存とその理由

| 依存 | 用途 | 配置 |
|---|---|---|
| `androidx.paging:paging-runtime:3.3.4` | 本番コードで `PagingSource` / `LoadResult` を参照するため | implementation |
| `androidx.paging:paging-common:3.3.4` | JVM 単体テスト側でも `PagingSource` を参照するため（paging-runtime は Android 依存） | implementation（テストからも推移的に利用） |
| `androidx.paging:paging-testing:3.3.4` | `TestPager` で refresh / append / retry の結合挙動を JVM テストから検証するため | testImplementation |

paging のメジャーバージョン `3.3.4` は既に `libs.versions.toml` に宣言済みで、本 Issue で
追加したのは `paging-common` / `paging-testing` の 2 artifact のみ（CLAUDE.md「GA stable 採用」
方針に準拠）。

## 確認事項（レビュワーへ）

- **`CursorPage<T>` を `core/network/paging` 配下に置いた**: SPEC §4.1 の envelope 型は
  `core/model` 配下の `Page<T>` がカノニカルだが、本中間型はネットワーク層内部の
  「Paging 基盤と loader の契約型」であり、UI / Repository から直接参照されない位置付け
  として `core/network/paging` 配下に同居させた。後続 Issue（#28 横断新着 Repository / #32
  cross-feed 用 PagingSource）で配線する際に位置を変更したくなった場合は別 Issue で議論
  したい。
- **`String` 型 Key の妥当性**: SPEC §4.1 は `next_cursor` を `<string|null>` と定義し、
  サーバー側で base64 等の不透明文字列を返す前提。本基盤は中身を解釈しない（Req 1.3）ため、
  `String` で十分。
- **検索一覧（`/api/items/search`）の `q` / `scope` をどう loader に渡すか**: 本基盤は
  loader を `(cursor) -> page` と固定しており、エンドポイント固有のクエリパラメータは
  loader をクロージャでキャプチャするか、Repository 側で都度 loader を再生成する設計を
  想定している。具体的な配線方針は #29 検索 Repository / #28 一覧 Repository の各 Issue で
  決める前提でよいか確認したい。

## 検証コマンド

```
./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.network.paging.CursorPagingSourceTest"
./gradlew build
```

いずれも BUILD SUCCESSFUL を確認済み。

STATUS: complete
