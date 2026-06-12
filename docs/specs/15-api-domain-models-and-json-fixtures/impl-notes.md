# Issue #15 実装ノート（API ドメインモデルと JSON fixtures）

## サマリー

SPEC §4.2 / §4.1 / §4.4 を正本として、core/model 配下に
kotlinx.serialization の `@Serializable` data class 群を追加し、
app/src/test/resources/fixtures/ に SPEC §4.2 の実形に即した fixture JSON を
配置した。fixture → モデル decode を JVM 単体テストで検証する形で
Requirement 1〜3 を担保する。

## 追加・変更ファイル

### main 側（core/model）

- `app/src/main/kotlin/com/feedman/android/core/model/ApiItem.kt`（新規）
  - `CrossFeedItem` / `ItemSummary` / `ItemDetail` / `StarredItemSummary` /
    `ItemSearchHit` を 5 種定義（SPEC §4.2）。
- `app/src/main/kotlin/com/feedman/android/core/model/Subscription.kt`（新規）
- `app/src/main/kotlin/com/feedman/android/core/model/User.kt`（新規）
- `app/src/main/kotlin/com/feedman/android/core/model/Page.kt`（新規）
  - 共通の `Page<T>`（items / next_cursor / has_more）と、
    横断新着専用の `CrossFeedPage`（共通フィールド + since_time）を別 data class で定義。
- `app/src/main/kotlin/com/feedman/android/core/model/Item.kt`（変更）
  - 既存モック専用 `ItemSummary` を `MockTimelineItem` に rename。
- `app/src/main/kotlin/com/feedman/android/core/data/ItemRepository.kt`（変更：rename 追従）
- `app/src/main/kotlin/com/feedman/android/core/data/fake/FakeItemRepository.kt`（変更：rename 追従）
- `app/src/main/kotlin/com/feedman/android/feature/timeline/TimelineViewModel.kt`（変更：rename 追従）
- `app/src/main/kotlin/com/feedman/android/feature/timeline/TimelineScreen.kt`（変更：rename 追従）
- `app/build.gradle.kts`（変更）
  - `implementation(libs.kotlinx.serialization.json)` を追記（Version Catalog では Issue #1 で宣言済）。

### test 側

- `app/src/test/resources/fixtures/` 配下に 16 件の fixture JSON を追加。
- `app/src/test/kotlin/com/feedman/android/core/model/` に 6 件の decode 検証テスト
  + 1 件の `FixtureLoader` ヘルパを追加（16 個の新規テストケース）。
- 既存 `FakeItemRepositoryTest` / `TimelineViewModelTest` を rename に追従。

## requirement ID → テスト対応表

| Req ID | 内容 | 担保するテスト |
|---|---|---|
| 1.1 | SPEC §4.2 の各モデルを 1 つずつ持つ | `CrossFeedItemTest` / `ItemSummaryTest` (ItemSummary, ItemDetail, StarredItemSummary) / `ItemSearchHitTest` / `SubscriptionTest` / `UserTest` の各 decode で型の存在を担保 |
| 1.2 | 共通ページ envelope（items / next_cursor / has_more）を持つ汎用 `Page<T>` | `PageTest.decodes Page with has_more true ...` / `decodes Page with has_more false ...` |
| 1.3 | 横断新着 envelope が `since_time`（RFC3339）を保持できる | `PageTest.decodes CrossFeedPage preserving since_time ...` / `... terminal page ...` |
| 1.4 | 各モデルの全フィールドを field 名・nullable 性を保ったまま保持 | 各 decode テストで主要フィールドを assert、null/data URL/欠落の全ケースを fixture で網羅 |
| 1.5 | `ItemSearchHit` は ItemSummary から派生させず、差分（hatebu_fetched_at 無し / feed_title 有 / favicon_url・published_at が nullable）を表現 | `ItemSearchHitTest`（独立 data class として定義し、published_at / favicon_url が null の fixture で decode 成功を確認） |
| 2.1 | 各モデルについて少なくとも 1 件の fixture JSON | `fixtures/` 配下に各モデル 1 件以上を配置（cross_feed_item.json, item_summary.json, item_detail.json, starred_item.json, item_search_hit_*.json, subscription_*.json, user.json） |
| 2.2 | 一覧系 envelope について has_more=true と false の双方 | `item_summary_page_has_more.json` / `item_summary_page_terminal.json` / `cross_feed_page.json` / `cross_feed_page_terminal.json` |
| 2.3 | 横断新着 envelope について `since_time` を含む fixture | `cross_feed_page.json` / `cross_feed_page_terminal.json` + `PageTest` |
| 2.4 | `feed_favicon_url` / `favicon_url` の data URL と null の双方 | data URL: `cross_feed_item.json` / `subscription_active.json` / `item_search_hit_with_favicon.json`、null: `cross_feed_item_null_favicon.json` / `subscription_error.json` / `item_search_hit_nullable_fields.json` |
| 2.5 | is_date_estimated true / false の双方 | true: `cross_feed_item_null_favicon.json` / `item_summary_no_hatebu_fetched_at.json` / `item_search_hit_nullable_fields.json`、false: `cross_feed_item.json` / `item_summary.json` / `item_search_hit_with_favicon.json` / `item_detail.json` |
| 3.1 | 各 fixture が例外なく decode 成功 | 全 decode テスト（CrossFeedItemTest / ItemSummaryTest / ItemSearchHitTest / SubscriptionTest / UserTest / PageTest）で `decodeFromString` が成功し具体的フィールドを assert |
| 3.2 | favicon が null と data URL の双方で decode 成功し nullable を保つ | `CrossFeedItemTest.decodes ... with data URL favicon ...` / `decodes ... with null favicon ...`、`SubscriptionTest.decodes active ...` / `decodes error ...` |
| 3.3 | ItemSearchHit が `hatebu_fetched_at` の有無に依存せず成功し、feed_title / nullable favicon_url / nullable published_at を保持 | `ItemSearchHitTest`（モデル定義に hatebu_fetched_at を持たないため構造的に確認、両 fixture で feed_title / favicon_url / published_at を assert） |
| 3.4 | has_more=false かつ next_cursor=null で終端表現に正しくマップ | `PageTest.decodes Page with has_more false ...` / `decodes CrossFeedPage terminal page ...` |
| 3.5 | 未知キーがあっても decode 失敗せず既知フィールドを保持 | `ItemSummaryTest.decodes ItemSummary even when payload carries unknown keys ...`、`UserTest.decodes User and ignores unknown keys` |
| 3.6 | RFC3339 文字列を欠落なく保持 | `CrossFeedItemTest.decodes ... full fields` の `published_at` assert、`ItemSummaryTest.decodes ItemSummary with hatebu_fetched_at populated` の `hatebu_fetched_at` assert、`PageTest.decodes CrossFeedPage preserving since_time ...` |
| 3.7 | nullable フィールドが欠落していたら null として扱い decode 失敗させない | `ItemSummaryTest.decodes ItemSummary when hatebu_fetched_at is missing`（`hatebuFetchedAt` のデフォルト引数 `null` で表現） |
| NFR 1.1 | 変更を core/model と app/src/test/ に限定 | 変更ファイル一覧の通り。既存 timeline / fake repository は同名型の rename 追従のみ |
| NFR 1.2 | fixture JSON を app/src/test/resources/fixtures/ に集約 | 16 件すべて当該ディレクトリ配下に配置 |
| NFR 1.3 | フィールド名・nullable 性・必須/任意を SPEC §4.2 から逸脱させない | 各モデルで `@SerialName` を SPEC §4.2 の snake_case JSON キーに合わせ、nullable は Kotlin `?` で表現 |
| NFR 2.1 | Req 3 の AC を JVM 単体テストで再現可能 | すべて JVM 単体テスト（`./gradlew testDebugUnitTest` のみで完結）。エミュレータ依存無し |

合計 16 件の新規テスト（CrossFeedItemTest 2, ItemSummaryTest 5, ItemSearchHitTest 2,
SubscriptionTest 2, UserTest 1, PageTest 4）。

## 設計上の判断

### 1. 既存モック `ItemSummary` を `MockTimelineItem` に rename した理由

Issue #1 では `core/model/Item.kt` にモックタイムライン用の data class が `ItemSummary` 名で
導入されていた。本 Issue で SPEC §4.2 の `ItemSummary` を core/model に追加するにあたり、
同一パッケージ内で同名の型を 2 つ持つことはできない。

要件 1.1 / NFR 1.3 は SPEC §4.2 の名称を canonical として保持することを要求しており、
「`ItemSummary`」の名前空間を SPEC モデル側に明け渡す必要があった。一方、要件 Out of Scope は
「既存モック `Item.kt` と本 Issue の API モデルの統合・置き換え方針の決定」を後続タスクに
委ねている。

両者は別物（モック型の意味的な統廃合 vs 名前空間の機械的な解消）なので、本 Issue では:
- モック型のクラス名のみを `MockTimelineItem` に rename（用途を名称に反映）
- データフロー（ItemRepository / Fake / TimelineViewModel）は引き続き `MockTimelineItem` を
  使う（モックタイムラインの観察可能な挙動は同一）
- SPEC §4.2 の API モデル群は別ファイル `ApiItem.kt` に新規追加

という方針を採った。これにより requirements の Out of Scope（「統合・置き換え方針の決定」）を
触らずに、要件 1.1 / NFR 1.3 を満たせる。

### 2. ItemDetail / StarredItemSummary を継承で定義しなかった理由

SPEC §4.2 の表記では `ItemDetail extends ItemSummary` / `StarredItemSummary extends ItemSummary`
となっているが、Kotlin の `data class` は継承を許容しない。kotlinx.serialization の階層型
`sealed` パターンは polymorphism を扱うためのもので、共通フィールドの差分追加を表現する目的に
は最小フィットしない。

そこで両者を、共通フィールドを並列で保持する独立 data class として定義した。要件 1.5 でも
「派生させずに表現する」ことが許容されている（ItemSearchHit に対する明示的指針）。Field 名・
nullable 性は SPEC から逸脱していない（NFR 1.3 適合）。

### 3. `Page<T>` を Generic にした理由 / CrossFeedPage を別 class にした理由

一覧系の 4 系統（feed items / starred / search / その他）は共通の envelope を持つので、
`Page<T>` をジェネリック化することでテスト・呼び出し側のコードを共通化できる
（要件 1.2）。

一方、横断新着のみ `since_time` を追加で持つため、`Page<CrossFeedItem>` を継承させる代わりに
`CrossFeedPage` という別 data class として定義した。理由:
- kotlinx.serialization のジェネリック継承はジェネリックパラメータ解決の周辺で
  decode 仕様が曖昧になりやすい
- 呼び出し側（Issue #18 以降の Repository / Paging）は cross-feed 用 Pager で
  扱うため `Page<CrossFeedItem>` との interchangeability は不要
- since_time は要件 1.3 で「横断新着のみ」と明示されており、共通型から派生させると
  意図と乖離する

### 4. 共通 Json 設定オブジェクトを今回作らなかった理由

Issue prompt に明示された通り、共通 `Json { ignoreUnknownKeys = true, ... }` の本格配置は
Issue #17（`core/network`）の領分である。本 Issue では NFR 1.1 に従い変更範囲を core/model と
app/src/test/ に限定するため、共通設定オブジェクトを main 側に置かず、テスト内に
`Json { ignoreUnknownKeys = true }` を直接生成する形にした。

## 確認事項

- **モック `Item.kt` の最終的な扱い**: 本 Issue では `MockTimelineItem` として残置したが、
  Issue #18 以降で本物の `CrossFeedItem` を timeline 表示に流す段階で `MockTimelineItem` の
  撤廃可否を判定する必要がある（Out of Scope: 「統合・置き換え方針の決定」）。Reviewer / PM は
  Issue #18 / #38 などで本判定を求めるかどうかを確認のこと。
- **共通 `Json` 設定の置き場**: テスト側に閉じた本 Issue の判断は NFR 1.1 を守ったが、
  Issue #17 で `core/network` に共通 `Json` を追加する際にテスト側の重複定義を集約する
  かどうか（または model テスト固有の設定を保持するか）を判断する必要がある。

## ./gradlew build 結果

`BUILD SUCCESSFUL`（lint / unit tests / build 全て通過）。
- `./gradlew testDebugUnitTest`: 23 件すべて green（新規 16 + 既存 7）
- `./gradlew build`: 成功（lint 警告も既存ベースラインを超えていない）

## flag 採用なし

`CLAUDE.md` の Feature Flag Protocol は `**採否**: opt-out` のため、本 Issue では
feature flag を導入していない（NFR 1.1 / 仕様通り）。

STATUS: complete
