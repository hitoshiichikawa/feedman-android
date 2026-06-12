# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-37-impl-custom-tabs-link-opener
- HEAD commit: 53bb2a9
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `ArticleDetailSheet.onOpenExternalRequested` が `onOpenExternal(detail.link)` 経由で `LinkOpener.open()` を呼び、`UseCustomTabs` 経路は `LinkOpenerLogicTest`「Custom Tabs 対応ブラウザがあれば UseCustomTabs」で網羅
- 1.2 — `OpenedWithCustomTabs` / `OpenedWithFallback` の when 分岐内で `viewModel.markReadOnOpenExternal()` を呼ぶ。既存 `ArticleDetailViewModelTest`（#36）で `markReadOnOpenExternal` の冪等動作・既読化呼び出しを検証済み
- 1.3 — `markReadOnOpenExternal` の冪等性（既読時 no-op）と OS のシングルトップ動作で実質抑止する設計判断を impl-notes に明示。Button 側の追加抑止は polish スコープに送る判断
- 1.4 — `ArticleDetailEvent.MarkReadFailed` 発火と既存ロールバック（#36 実装）を流用。`ArticleDetailSheet` の `events` 購読で `markReadFailedMsg` を snackbar 表示
- 2.1 — `TimelineScreen.onExternalLinkClicked` が `onOpenExternalLink(link)` を呼び `card.link` を渡す。`ArticleCardModel.link` を追加（既定値 `""` で後方互換）
- 2.2 — 即時 alpha 切替は #38 `ItemStateStore` のスコープと整理し、サーバー側 `is_read:true` 反映は `TimelineViewModel.markReadOnExternalOpen` で実施。`TimelineViewModelTest`「markReadOnExternalOpen で updateState_isRead_true_を呼ぶ」で検証
- 2.3 — `ArticleCard.OpenLinkIconButton` の `IconButton` が click を消費し本体の `clickable { onOpen(id) }` には伝播しない（#33 実装の流用）
- 2.4 — `TimelineExternalLinkEvent.MarkReadFailed` 発火を `TimelineViewModelTest`「updateState が失敗すると MarkReadFailed を流す」で検証。UI 側内部 mutable 状態を持たないためロールバック対象は無し（design 判断）
- 3.1 — `LinkOpenerLogic.decide` の `UseFallback` 経路を `LinkOpenerLogicTest`「Custom Tabs 非対応で ACTION_VIEW 解決可能なら UseFallback」で検証
- 3.2 — `OpenedWithFallback` も `OpenedWithCustomTabs` と同じ when ブランチで既読化（`ArticleDetailSheet.kt:146-149` / `TimelineScreen.kt:113-116`）
- 3.3 — `LinkOpenerLogicTest`「Custom Tabs もフォールバックも不可なら NoAppToHandle」+ UI 側 `InvalidUrl` / `NoAppToHandle` ブランチで既読化を呼ばず snackbar のみ通知
- 4.1 — `UrlValidationTest`「javascript / mailto / file / intent スキーマは UnsupportedScheme として拒否」+ `LinkOpenerLogicTest`「URL が不正なら DoNothing + InvalidUrl」
- 4.2 — `UrlValidationTest`「空文字列 / 空白のみ / スペース含む / host を持たない URL」+ `LinkOpenerLogicTest`「Blank も DoNothing + InvalidUrl_Blank」
- 4.3 — `ArticleDetailSheet.onOpenExternalRequested` / `TimelineScreen.onExternalLinkClicked` の when 分岐で `InvalidUrl` / `NoAppToHandle` は既読化を呼ばない（コード経路で担保）
- 5.1 — 本 Issue 範囲外（#38）。サーバー側の `is_read:true` 反映で正本は整合。requirements.md "Out of Scope" との関係を impl-notes Q1 で明示
- 5.2 — 詳細シート側は #36 の `Content.isRead=true` 楽観表示で実現。タイムライン側は impl-notes Q1 で明示の通り #38 に委譲
- 5.3 — 詳細シートは #36 のロールバック実装を流用。タイムライン側は in-memory mutable 状態を持たないため対象なし
- NFR 1.1 — `CustomTabsLinkOpener.buildCustomTabsIntent` で `setDefaultColorSchemeParams(lightParams)` + `setColorSchemeParams(COLOR_SCHEME_DARK, darkParams)` + `setColorScheme(COLOR_SCHEME_SYSTEM)` を設定し、`FeedmanColors.LightSurface` / `DarkSurface` を渡す
- NFR 1.2 — `COLOR_SCHEME_SYSTEM` で OS テーマ追従。アプリ override 時の差分は impl-notes Q2 で確認事項として残置
- NFR 2.1 — `CustomTabsLinkOpener.open` は同期的に Intent 構築 + `startActivity`、PackageManager 解決のみで非 IO
- NFR 2.2 — `FeedmanSnackbar.show` を `Short` duration で同期的に表示
- NFR 3.1 — `UrlValidation` / `LinkOpenerLogic` を Android 依存なしの `internal object` として切り出し、JVM 単体テストで網羅検証
- NFR 3.2 — `LaunchPreflight(customTabsAvailable, fallbackAvailable)` を入力とする `LinkOpenerLogic.decide` をテーブル駆動で網羅（4 ケース）

## Findings

なし

## Summary

requirements.md の全 numeric ID（1.1〜1.4 / 2.1〜2.4 / 3.1〜3.3 / 4.1〜4.3 / 5.1〜5.3 /
NFR 1.1〜1.2, 2.1〜2.2, 3.1〜3.2）について、対応する実装または JVM 単体テスト、もしくは
明示的な scope 委譲判断（#38）が確認できた。

タスク境界は `core/ui` / `di` / `feature/articledetail` / `feature/timeline` / `shell` /
`gradle` / `strings.xml` / `app/src/test` に閉じており、#38 の画面間同期一般化（`ItemStateStore`
等）には踏み込んでいない（タイムライン側即時 alpha 切替を意図的に保留し impl-notes Q1 で
明示）。`./gradlew :app:testDebugUnitTest` を当該 4 テストクラスで実行し全件 pass を確認した。

スキーマ検証は `java.net.URI`、ツールバー色は `COLOR_SCHEME_SYSTEM` + Light/Dark params、
Custom Tabs 非対応時の ACTION_VIEW fallback と `NoAppToHandle` までの 3 段階フォールバック
チェーンが揃っており、AC 未カバー / missing test / boundary 逸脱 のいずれも検出しない。

RESULT: approve
