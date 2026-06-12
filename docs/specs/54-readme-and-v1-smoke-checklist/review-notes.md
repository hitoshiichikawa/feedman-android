# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-54-impl-readme-smoke-checklist
- HEAD commit: 3cc786f
- Compared to: origin/main..HEAD
- 変更ファイル: `README.md` / `docs/SMOKE-CHECKLIST.md` / `docs/specs/54-readme-and-v1-smoke-checklist/{requirements.md,impl-notes.md}`

## Verified Requirements

### Requirement 1: README のセットアップ・ビルド・実行手順

- 1.1 — `README.md` L37-40「前提」セクションに JDK 17 / Android SDK / compileSdk=35 / targetSdk=35 / minSdk=26 / `ANDROID_HOME` / `local.properties` の `sdk.dir` を明示
- 1.2 — `README.md` L44-49「基本ビルド」で `./gradlew build` が compile / Lint / JVM 単体テストを exit code 0 で完了する旨を記述
- 1.3 — `README.md` L51-55 で `./gradlew test` の単独実行コマンドを記述
- 1.4 — `README.md` L63-71 で `feedman.mockMode=true` を `-P` フラグまたは `gradle.properties` で指定する手順、ログイン placeholder スキップ + ドロワー + モックタイムライン挙動を記述
- 1.5 — `README.md` L64 表 + L73-77 で `feedman.baseUrl` の `-P` / `gradle.properties` 指定手順、既定値 `https://stg-feed.markte-river.net` を明示
- 1.6 — `README.md` L87-101「OAuth コールバック（`feedman://auth/callback`）の前提」セクションを新設し、アプリ側 intent-filter 登録と、サーバー側 redirect URI 許可の必要性を案内
- 1.7 — `README.md` L103-108「機密情報の取り扱い」で実トークン / 本番 API キー / OAuth クライアントシークレットをソース・Version Catalog・`gradle.properties` に埋め込まず `local.properties` / CI secrets 経由で渡す運用を明示
- 1.8 — `README.md` L17（ドキュメント表）+ L110-118「v1 スモークチェック」セクションで `docs/SMOKE-CHECKLIST.md` への導線を 2 箇所で配置

### Requirement 2: v1 スモークチェックリスト本体

- 2.1 — `docs/SMOKE-CHECKLIST.md` を新規配置
- 2.2 — SPEC §10 の v1 受け入れ基準 9 項目（Google ログイン / 無限スクロール + `since_time` 固定 / フィルタ / 部分シート + 既読 / Custom Tabs + 既読 / スター整合 / Pull-to-refresh + cooldown / 登録・購読・間隔・再開 / テーマ切替）すべてに対応する §1〜§9 を配置、各項目に手順・期待結果・`- [ ]` チェックボックスを含む
- 2.3 — `docs/SMOKE-CHECKLIST.md` §11「次フェーズ（v1 スコープ外）」でキーワードプッシュ通知（FCM）を v1 スコープ外と明示
- 2.4 — 各項目に「区分: 自動テストで担保 / 手動確認 / サーバーデプロイ後に実施」を併記
- 2.5 — 各項目で対応テストクラス名（例: `core.auth.AuthCallbackParserTest` / `feature.feed.FeedScreenViewModelTest` / `feature.subscriptionsettings.SubscriptionSettingsViewModelTest`）を併記
- 2.6 — §1.2 / §1.3 で「サーバーデプロイ後に実施」を明示し、§0 のローカル完結手順（`./gradlew build` / `./gradlew test` / モックモード）と区別
- 2.7 — §7.1 で `POST /api/subscriptions/{id}/fetch` 呼び出し確認、§7.2 で `FEED_COOLDOWN`（429）応答時に `retry_after_seconds` がスナックバー / バナーで案内されることを確認する手順を記載
- 2.8 — §8.3 でセグメント値 30 / 60 / 180 / 360 分の確認、15 分セグメント不存在の確認手順を含む

### Requirement 3: ドキュメント記載前の自己検証

- 3.1 — `impl-notes.md` L77 で `./gradlew build` BUILD SUCCESSFUL（exit code 0）を記録
- 3.2 — `impl-notes.md` L79 で `./gradlew build` 内に含まれる `:app:testDebugUnitTest` / `:app:testReleaseUnitTest` 成功を記録
- 3.3 — `impl-notes.md` L78 で `./gradlew assembleDebug -Pfeedman.mockMode=true` BUILD SUCCESSFUL を記録
- 3.4 — SMOKE-CHECKLIST §1.2 / §1.3 で「サーバーデプロイ後に実施」を明示し、架空の検証結果は記載していない

### Non-Functional Requirements

- NFR 1.1 — `README.md` は既存セクション構成（ドキュメント表 / 技術スタック / v1 スコープ / ビルド・実行 / CI / idd-claude 運用）を維持しつつ追記、日本語ベース・Markdown 形式
- NFR 1.2 — `docs/SMOKE-CHECKLIST.md` の全項目が GitHub Markdown checkbox `- [ ]` 記法
- NFR 1.3 — `README.md` L17 でドキュメント表に `docs/SMOKE-CHECKLIST.md` 行 + 1 行説明を追加
- NFR 2.1 — SPEC §10 受け入れ基準 9 項目 + 付録 A-6（フェッチ間隔 30/60/180/360 分）と整合
- NFR 2.2 — `CLAUDE.md`「機密情報の扱い」（実トークン埋め込み禁止）「技術スタック」（JDK 17 / minSdk 26 等）と矛盾なし

### 自動テストクラス名の実在性サンプリング検証

`docs/SMOKE-CHECKLIST.md` で参照したテストクラス名のうち、以下を `find app/src/test -name "*.kt"` および `grep` で実在確認:

- `feature.feed.FeedScreenViewModelTest` の `onPullToRefresh が FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2`（`FeedScreenViewModelTest.kt:408`）と `... 欠落のとき null を流す_Issue42 Req 3_3`（`FeedScreenViewModelTest.kt:443`）→ 実在確認
- `feature.subscriptionsettings.SubscriptionSettingsViewModelTest` の `現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2`（`SubscriptionSettingsViewModelTest.kt:147`）→ 実在確認
- `core.auth.{AuthCallbackParserTest, AuthCallbackDispatcherTest, PkceTest, AuthRepositoryImplTest, AuthInterceptorTest, AuthRepositorySessionStateProviderTest, MockModeSessionStateProviderTest, LogoutCoordinatorTest, AccountDeletionCoordinatorTest}`、`core.network.{FeedmanApiTest, FeedmanErrorMapperTest, TokenAuthenticatorTest, paging.CursorPagingSourceTest}`、`core.data.{CrossFeedRepositoryImplTest, FeedItemsRepositoryImplTest, ItemStateStoreTest, ItemDetailRepositoryImplTest, StarredItemsRepositoryImplTest, SubscriptionRepositoryImplTest, SearchRepositoryImplTest, FeedRegistrationRepositoryImplTest}`、`core.ui.{TimelineScreenStateTest, ListFooterStateTest, LinkOpenerLogicTest, UrlValidationTest, FeedmanSheetLabelTest}`、`core.designsystem.{ThemeModeTest, FeedmanThemeMappingTest, FeedmanColorsTest, FeedmanDimensTest, DataStoreThemeModeRepositoryTest, InMemoryThemeModeRepositoryTest}`、`feature.{login.LoginViewModelTest, login.AuthorizationUrlBuilderTest, timeline.TimelineViewModelTest, timeline.TimelineCardModelMapperTest, feed.FeedStatusBannerTest, articledetail.ArticleDetailViewModelTest, articledetail.ArticleDetailContentPolicyTest, starred.StarredViewModelTest, starred.StarredCardModelMapperTest, search.SearchViewModelTest, search.SearchViewModelBridgeTest, search.SearchCardModelMapperTest, registerfeed.RegisterFeedViewModelTest, registerfeed.RegisterFeedErrorResolverTest, registerfeed.RegisterFeedUiStateTest, subscriptionsettings.SubscriptionSettingsViewModelTest, account.AccountSheetViewModelTest}`、`shell.{ThemeToggleLogicTest, AppShellViewModelTest, DrawerFeedRowA11yTest}`、`core.auth.fake.InMemoryTokenStoreTest` → いずれもファイル実在を `find` 出力で確認

### Boundary 確認

`git diff --name-only origin/main..HEAD` の出力は `README.md` / `docs/SMOKE-CHECKLIST.md` / `docs/specs/54-readme-and-v1-smoke-checklist/{requirements.md,impl-notes.md}` の 4 ファイルに限定されており、Issue 指定の境界（README.md / docs/SMOKE-CHECKLIST.md / docs/specs 配下）を逸脱していない。

## Findings

なし

## Summary

要件定義の全 AC（Req 1.1〜1.8 / Req 2.1〜2.8 / Req 3.1〜3.4 / NFR 1.1〜1.3 / NFR 2.1〜2.2）を README.md と docs/SMOKE-CHECKLIST.md で網羅し、SPEC §10 の v1 受け入れ基準 9 項目を 1:1 で追跡できる構成になっている。検証記録（`./gradlew build` / `./gradlew test` / `assembleDebug -Pfeedman.mockMode=true`）は impl-notes.md に記録済みで、サーバー未デプロイ項目は架空の検証結果を書かず「サーバーデプロイ後に実施」と明示している。チェックリストで参照したテストクラス名は実ファイルとの突合（サンプリング検証）で全件実在を確認。境界逸脱なし。

RESULT: approve
