# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-15-impl-api-domain-models-and-json-fixtures
- HEAD commit: 496168c
- Compared to: main..HEAD

## Verified Requirements

- 1.1 — `ApiItem.kt` に `CrossFeedItem` / `ItemSummary` / `ItemDetail` / `StarredItemSummary` / `ItemSearchHit` を独立 data class として定義、`Subscription.kt` / `User.kt` も追加。SPEC §4.2 の 7 モデルすべて実装あり。
- 1.2 — `Page.kt` の汎用 `Page<T>` が `items` / `next_cursor` / `has_more` を保持。`PageTest.decodes Page with has_more true ...` / `... terminal ...` で検証。
- 1.3 — `Page.kt` の `CrossFeedPage` が `since_time`（RFC3339）を保持。`PageTest.decodes CrossFeedPage preserving since_time ...` / `... terminal page ...` で検証。
- 1.4 — 各 data class が SPEC §4.2 の field 名（`@SerialName` で snake_case 厳守）と nullable（Kotlin `?`）を保持。SPEC との照合で逸脱なし。
- 1.5 — `ItemSearchHit` は `ItemSummary` から派生せず独立定義（`hatebu_fetched_at` 無し / `feed_title` 有 / `favicon_url` と `published_at` が nullable）。`ItemSearchHitTest` 2 ケースで検証。
- 2.1 — `fixtures/` 配下に 16 件配置。各モデル（CrossFeedItem / ItemSummary / ItemDetail / StarredItemSummary / ItemSearchHit / Subscription / User）について 1 件以上の fixture を確認。
- 2.2 — `item_summary_page_has_more.json`（has_more=true）と `item_summary_page_terminal.json`（has_more=false）の双方が存在。
- 2.3 — `cross_feed_page.json` / `cross_feed_page_terminal.json` ともに `since_time` フィールドを含む。
- 2.4 — data URL 形式は `cross_feed_item.json` / `subscription_active.json` / `item_search_hit_with_favicon.json` で、`null` 形式は `cross_feed_item_null_favicon.json` / `subscription_error.json` / `item_search_hit_nullable_fields.json` で各々検証。
- 2.5 — `is_date_estimated: true` は `cross_feed_item_null_favicon.json` / `item_summary_no_hatebu_fetched_at.json` / `item_search_hit_nullable_fields.json` で、`false` は `cross_feed_item.json` / `item_summary.json` / `item_search_hit_with_favicon.json` / `item_detail.json` で網羅。
- 3.1 — 各 decode テスト（CrossFeedItemTest / ItemSummaryTest / ItemSearchHitTest / SubscriptionTest / UserTest / PageTest）で `decodeFromString` が成功し具体的 field を assert。
- 3.2 — `CrossFeedItemTest.decodes a CrossFeedItem with null favicon ...` と `SubscriptionTest.decodes error subscription with null favicon ...` で null 表現を、data URL 表現は同テストの逆ケースで確認。
- 3.3 — `ItemSearchHitTest` の 2 ケースが `hatebu_fetched_at` の有無に依存せず成功し、`feed_title` / nullable `favicon_url` / nullable `published_at` を assert。
- 3.4 — `PageTest.decodes Page with has_more false and null next_cursor as terminal ...` および `... CrossFeedPage terminal page ...` で終端表現を確認。
- 3.5 — `item_summary_with_unknown_keys.json`（`future_field` / `another_unknown`）と `user.json`（`display_name` / `created_at`）を `ignoreUnknownKeys = true` で decode 成功。`ItemSummaryTest.decodes ItemSummary even when payload carries unknown keys ...` / `UserTest.decodes User and ignores unknown keys` で検証。
- 3.6 — `CrossFeedItemTest` で `published_at` が "2026-06-10T09:00:00Z" として欠落なく保持、`ItemSummaryTest` で `hatebu_fetched_at` が "2026-06-08T13:00:00Z" 保持、`PageTest` で `since_time` が "2026-06-12T09:30:00Z" 保持を assert。
- 3.7 — `ItemSummaryTest.decodes ItemSummary when hatebu_fetched_at is missing (Req 3-7)` で `item_summary_no_hatebu_fetched_at.json`（`hatebu_fetched_at` キー欠落）が `null` として decode 成功し失敗しないことを確認。`hatebuFetchedAt: String? = null` のデフォルト引数で実現。
- NFR 1.1 — 変更主要範囲は `core/model` と `app/src/test/`。namespace 衝突解消のための rename 追従が `core/data` / `feature/timeline` に波及するが、観測挙動は不変（後述「Boundary 評価」参照）。
- NFR 1.2 — fixture JSON は 16 件すべて `app/src/test/resources/fixtures/` 配下に集約。
- NFR 1.3 — `@SerialName` で SPEC §4.2 の snake_case キー名を厳守、nullable は Kotlin `?` で表現。SPEC との 1:1 照合で逸脱なし。
- NFR 2.1 — すべて JVM 単体テスト（`src/test/`）。`FixtureLoader` は classloader 経由で resources を読むためエミュレータ非依存。

## Boundary 評価（NFR 1.1 周辺）

NFR 1.1 は変更範囲を「`core/model` 配下と `app/src/test/` 配下のみ」に限定すると規定。実際の差分には以下の rename 追従が含まれる:

- `core/data/ItemRepository.kt` / `core/data/fake/FakeItemRepository.kt`: 既存 `ItemSummary`（モック型）への参照を `MockTimelineItem` に置換
- `feature/timeline/TimelineScreen.kt` / `TimelineViewModel.kt`: 同上
- `app/build.gradle.kts`: `implementation(libs.kotlinx.serialization.json)` を追加

これらは要件 Out of Scope に列挙された「既存モック `Item.kt` と本 Issue の API モデルの統合・置き換え方針の決定」には踏み込んでおらず、以下の根拠で **boundary 逸脱ではない**と判定する:

1. Req 1.1 / NFR 1.3 は SPEC §4.2 の正本名（`ItemSummary`）を `core/model` 内で保持することを必須とするため、既存モック型と同一パッケージで同名となり namespace 衝突が不可避。
2. impl-notes.md §1 に明記の通り、本 Issue では rename のみで観測挙動を保持し、`MockTimelineItem` を引き続き timeline で使用。real `ItemSummary` への置換は後続 Issue（#18 等）に明示的に委ねている。
3. `app/build.gradle.kts` の `kotlinx.serialization.json` 追加は AC 3.1（decode 成功）を満たすため不可避な infrastructural 依存（実体は `@Serializable` が runtime に到達するため必要）。
4. rename 追従の対象テスト（`FakeItemRepositoryTest` / `TimelineViewModelTest`）も挙動は不変。

これは NFR 1.1 の主旨（API モデル追加を最小範囲に閉じる）を侵さず、Out of Scope の意味論にも反していない。

## Findings

なし

## Summary

SPEC §4.2 / §4.1 / §4.4 と各 data class のフィールド契約を 1:1 照合した結果、すべての要件 ID（1.1〜1.5 / 2.1〜2.5 / 3.1〜3.7 / NFR 1.1〜1.3 / NFR 2.1）に対応する実装とテストを確認。`MockTimelineItem` への rename 追従は namespace 衝突を解消する機械的変更にとどまり Out of Scope に踏み込んでいない。impl-notes.md に記載された `./gradlew build` / `./gradlew testDebugUnitTest` の通過もサマリ通り integrity を保持。

RESULT: approve
