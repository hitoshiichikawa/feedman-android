# Issue #37 実装ノート — Open original article via Chrome Custom Tabs

## 概要

記事の元 URL を Chrome Custom Tabs で開く共通の `LinkOpener` 抽象を導入し、
詳細シートのフッタ「元記事を開く」とタイムラインカードの外部リンクアイコンの
双方から呼び出せるよう結線した。Custom Tabs 非対応端末では標準 `ACTION_VIEW`
にフォールバックし、未対応スキーム（http/https 以外）は安全側に倒してユーザーへ
エラー通知する。

## requirement ID → テスト対応表

| Req ID | AC 要旨 | 対応テスト |
|---|---|---|
| 1.1 | 詳細シートで Custom Tabs で開く | `LinkOpenerLogicTest`「Custom Tabs 対応ブラウザがあれば UseCustomTabs」「Custom Tabs 対応がありフォールバック解決不可でも UseCustomTabs」+ AppShell 結線（手動確認） |
| 1.2 | 開いた時点で既読化 | `ArticleDetailViewModelTest`（既存）「markReadOnOpenExternal は未読のときのみ既読化」/ ArticleDetailSheet 結線で開く成功時のみ呼ぶ（手動確認） |
| 1.3 | 重複起動抑止 | 既存 ViewModel 側の冪等ガード（`markReadOnOpenExternal` は既読時 no-op）/ 設計上 Custom Tabs Intent 二重発火は OS 側のシングルトップ動作で実質抑止される（impl-notes 確認事項参照） |
| 1.4 | 既読化失敗時のロールバック + メッセージ | `ArticleDetailViewModelTest`（既存）「既読化サーバー反映失敗で isRead を false に戻し MarkReadFailed」/ ArticleDetailSheet が `MarkReadFailed` イベントを snackbar 表示 |
| 2.1 | タイムラインカードで Custom Tabs で開く | `TimelineViewModelTest`「markReadOnExternalOpen で updateState」/ `LinkOpenerLogicTest`（同上） |
| 2.2 | タイムライン側既読化（即時遷移） | `TimelineViewModelTest`「markReadOnExternalOpen で updateState(isRead=true) を呼ぶ」/ カード表示同期は #38 のスコープのため次 refresh 任せ（下記「確認事項」参照） |
| 2.3 | 同一カードのタップで詳細シート起動しない | 既存 ArticleCard のクリック分離（onOpenLink タップは onOpen には伝播しない）/ #33 で実装済み |
| 2.4 | 既読化失敗時のロールバック + メッセージ | `TimelineViewModelTest`「markReadOnExternalOpen で updateState が失敗すると MarkReadFailed を流す」 |
| 3.1 | フォールバック（ACTION_VIEW） | `LinkOpenerLogicTest`「Custom Tabs 非対応で ACTION_VIEW 解決可能なら UseFallback」 |
| 3.2 | フォールバック経路でも既読化 | ArticleDetailSheet / TimelineScreen で `OpenedWithFallback` も成功扱いで既読化を呼ぶ分岐（コードレビュー） |
| 3.3 | 開けるアプリ不在時のエラー通知 + 既読化しない | `LinkOpenerLogicTest`「Custom Tabs もフォールバックも不可なら NoAppToHandle」/ `TimelineViewModelTest`「notifyExternalLinkFailed で OpenLinkFailed が流れる」/ ArticleDetailSheet の when 分岐は既読化を呼ばない |
| 4.1 | 未対応スキーマ拒否 + メッセージ | `UrlValidationTest`「javascript/mailto/file/intent スキーマは UnsupportedScheme として拒否」/ `LinkOpenerLogicTest`「URL が不正なら DoNothing + InvalidUrl」 |
| 4.2 | 空文字 / 不正構文の拒否 + メッセージ | `UrlValidationTest`「空文字列 / 空白のみ / スペース含む / host を持たない URL」/ `LinkOpenerLogicTest`「Blank も DoNothing + InvalidUrl_Blank」 |
| 4.3 | 拒否時に既読化しない | ArticleDetailSheet / TimelineScreen の when 分岐（`InvalidUrl` / `NoAppToHandle` 系では既読化を呼ばない / `TimelineViewModelTest`「notifyExternalLinkFailed」）|
| 5.1 | 画面間整合（タイムライン↔詳細） | **本 Issue 範囲外（#38）**。詳細シート起動時の既読化（#36 既実装）と本 Issue の既読化 API 呼び出しを通じてサーバー側状態は整合する。UI 上の他画面への即時反映は #38 で実装される（requirements の "Out of Scope" 整合）|
| 5.2 | 楽観表示 | 詳細シート側は #36 の Content.isRead=true で楽観表示。タイムライン側はカード in-memory mutable state を持たず、次 refresh で反映（下記「確認事項」） |
| 5.3 | 失敗時にロールバック | 詳細シートは #36 で実装済み。タイムライン側は内部 mutable state を持たないため、ロールバック対象なし（次 refresh で正本反映） |
| NFR 1.1 | ツールバー色がテーマ追従 | `CustomTabsLinkOpener.buildCustomTabsIntent` で `setColorScheme(COLOR_SCHEME_SYSTEM)` + `setDefaultColorSchemeParams` / `setColorSchemeParams(COLOR_SCHEME_DARK)` でライト/ダーク双方の `surface` 色を渡す（コードレビュー） |
| NFR 1.2 | テーマ切替後の整合 | 同上。`COLOR_SCHEME_SYSTEM` により次回起動時のシステム設定に追従（手動確認） |
| NFR 2.1 | 300ms 以内に発火 | `CustomTabsLinkOpener.open` は同期的に Intent 構築 + `startActivity`、PackageManager 解決のみで非 IO（コードレビュー） |
| NFR 2.2 | エラー 1 秒以内 | snackbar は `FeedmanSnackbar.show` を `Short` duration で即時表示（コードレビュー） |
| NFR 3.1 | URL バリデーション・既読化・起動アクション決定の JVM テスト可能化 | `UrlValidationTest` / `TimelineViewModelTest`（既読化）/ `LinkOpenerLogicTest`（起動アクション決定） |
| NFR 3.2 | Custom Tabs 起動可否判定の入力ベース JVM テスト | `LinkOpenerLogicTest` で `LaunchPreflight(customTabsAvailable, fallbackAvailable)` を入力として網羅 |

## 設計判断

### 1. LinkOpener interface の場所と単位

GRAND-DESIGN.md §5.7 の指示通り、`core/ui/LinkOpener.kt` 配下に単独の interface として定義した。`core/data/` 側に置く案も検討したが:

- LinkOpener は HTTP / DB を扱わず、Android Intent を直接扱う「UI 寄りのアクション抽象」である
- Hilt の Module で provide するだけの薄い抽象であり、Repository 実装の家族には含めない方が分離が分かりやすい

そのため `core/ui/` に配置した。将来 `core/external/` のようなパッケージを切る場合は移動候補。

### 2. URL バリデーションは `java.net.URI` のみで実装

Android の `Uri.parse` は緩く、`javascript:alert(1)` などを「scheme=javascript」として通してしまうため、検証用途では使わない。`java.net.URI` を使うことで JVM 単体テスト可能（NFR 3.1）かつ scheme と host の妥当性を検出できる。

### 3. `LaunchPreflight` で Android 解決と判定を分離

`CustomTabsLinkOpener` は `PackageManager` で Custom Tabs サービス（`CustomTabsClient.getPackageName`）と ACTION_VIEW（`resolveActivity`）を解決し、その結果を `LinkOpenerLogic.decide` に純粋値として渡す。これにより:

- 判定ロジック自体は JVM 単体テストで網羅可能（NFR 3.2）
- 実機依存の解決部分のみ実装に閉じる

`LinkOpenerLogic` 自体は内部 object として公開せず（`internal`）、`CustomTabsLinkOpener` のテスト容易化のためだけに存在することを明示する。

### 4. ツールバー色

`setDefaultColorSchemeParams(lightParams)` + `setColorSchemeParams(COLOR_SCHEME_DARK, darkParams)` + `setColorScheme(COLOR_SCHEME_SYSTEM)` で、システムテーマに追従させる。これにより端末のダークモード切替時にツールバー色が切替後のテーマに整合する（NFR 1.2）。アプリ側でユーザーが明示的にテーマ override している場合（`AppShellViewModel.toggleTheme`）も、Custom Tabs は OS の dark/light 設定を参照するため必ずしもアプリ表示と一致しない点はトレードオフ。`COLOR_SCHEME_LIGHT` / `_DARK` を明示的にアプリ表示テーマに合わせて切り替える代替案もあるが、現状の挙動はプロト『FMNetworkBoundCustomTabs』設計（Web 標準 PWA に近い「OS テーマで開く」UX）と整合する。

### 5. AppShell 経由で LinkOpener を渡す

`LinkOpener` は `AppShellViewModel` に `@Inject val linkOpener: LinkOpener` として注入し、Composable から `viewModel.linkOpener` で取得する。これにより:

- `LinkOpener` は 1 つの Hilt スコープ（Singleton）から各 Composable に共有される
- テスト時は `AppShellViewModel` に fake LinkOpener を渡せる（`AppShellViewModelTest` の `NoopLinkOpener`）

直接 Composable で `hiltViewModel()` 外から取得する `EntryPoint` 経由は採用しなかった（型安全性とテスト容易性を優先）。

### 6. ArticleDetailSheet 側の既読化順序

要件 4.3「未対応 URL によりブラウザ起動が拒否されたとき、当該記事の既読状態を変更しない」を遵守するため、`onOpenExternalRequested` 内で **LinkOpener.open() の結果を見てから** `markReadOnOpenExternal()` を呼ぶ順序に変更した。これに伴い `onOpenExternal: (url: String) -> OpenLinkResult` と返り値型を変更した。

既存のシート起動時自動既読化（#36 Req 3.1）は変更していないため、シート開時点で既に既読化済みなら `markReadOnOpenExternal` は no-op で済む（冪等）。

### 7. タイムライン側の表示同期は次 refresh 任せ

要件 2.2 はタイムラインカードの即時既読表示切替を要求するが、`CrossFeedRepository` は `PagingData` を返す read-only な層で in-memory mutable state を持たない。横断既読同期は `ItemStateStore`（#38）が担う設計のため、本 Issue 範囲では `ItemDetailRepository.updateState(isRead=true)` でサーバーに反映するに留め、UI 上の即時 alpha 切替は次回 refresh で反映する形にした。

この判断の根拠:
- 本 Issue タスク指示「画面間同期の一般化は #38 なので、ここではタイムライン側の表示更新は次リフレッシュ任せで良い」
- requirements.md "Out of Scope" には記述が無いが、Issue #38 が画面間同期を担う形で IDD-CLAUDE-ISSUES.md にプランニングされている

→ **下記「確認事項」参照**

## 追加した依存

- **androidx.browser 1.8.0**（GA stable、2024-04 リリース）
  - Chrome Custom Tabs SDK（`CustomTabsIntent` / `CustomTabsClient` / `CustomTabColorSchemeParams`）
  - 1.9.x 系統は 2026-06 時点で alpha のみのため、CLAUDE.md「GA stable のみ」方針に従い 1.8.0 を採用
  - 用途は本 Issue のリンクオープナーのみ。CCT セッション warmup / Trusted Web Activity は使用しない（requirements.md "Out of Scope"）

## 確認事項（PR 本文転記候補）

### Q1. タイムラインカードのカード表示即時切替（Req 2.2 / 5.2 解釈）

要件 Req 2.2 は「タイムラインカードの既読表示を即時切替」、Req 5.2 は「楽観的に既読状態を表示」を要求します。
詳細シート側は #36 の `Content.isRead=true` で楽観表示を満たしますが、タイムラインカード側は
`CrossFeedRepository` が `PagingData` 返却の read-only 層で、in-memory mutable state を持たないため、
**本 Issue 範囲では即時 alpha 切替を実装せず、サーバー反映のみで UI は次 refresh 任せ**にしました。

これは本 Issue 着手時の指示（「画面間同期の一般化は #38 なので、ここではタイムライン側の表示更新は
次リフレッシュ任せで良い」）と整合しますが、Req 2.2 の文言とは差分があります。

- そのまま許容 → #38 で `ItemStateStore` 経由の即時切替を実装する想定で OK
- 厳密に Req 2.2 を満たすべき → 本 Issue で簡易な in-memory mutable map を追加する案あり
（ただし #38 の `ItemStateStore` と重複実装になるため非推奨）

### Q2. Custom Tabs ツールバー色のテーマ整合（NFR 1.1 解釈）

NFR 1.1「アプリのテーマ（ライト／ダーク）に追従したツールバー色」について、現状の実装は `COLOR_SCHEME_SYSTEM`
（OS のダーク設定に追従）を用いています。アプリ側でユーザーが明示的にテーマ override（`AppShellViewModel.toggleTheme`）
した状態と OS 設定が異なる場合、Custom Tabs ツールバー色は OS 側に従います。

- そのまま許容 → 多くのアプリは OS テーマに準ずる UX を採用しているため一般的な挙動
- 厳密にアプリ表示テーマに合わせるべき → `AppShellViewModel.themeMode` の解決結果に応じて
  `COLOR_SCHEME_LIGHT` / `_DARK` を明示する追加実装が必要

### Q3. 重複起動抑止（Req 1.3 解釈）

Req 1.3「同じアクションの重複起動を抑止する」について、現状の `CustomTabsLinkOpener` は呼び出しごとに
即座に `startActivity` を呼ぶため、ユーザーがダブルタップした場合は OS 側のシングルトップ動作に依存します
（多くの実装で 1 タップ分の Custom Tabs しか表示されない）。

ボタン側で debouncing する案もありますが、本 Issue では Button の連打抑制は実装せず、OS の挙動に
任せています。要件解釈として問題なければそのまま、要するなら今後の polish Issue で扱います。

## 補足ノート

- 本 Issue では `ArticleDetailSheet.onOpenExternal` の型を `(url: String) -> Unit` から
  `(url: String) -> OpenLinkResult` に変更しました。既存テスト（`ArticleDetailViewModelTest`）は
  ViewModel 層のみを扱うため影響なし。Compose UI テストは現状無いため非影響。
- `ArticleCardModel.link` フィールドを追加しました（既定値 `""` で後方互換）。
- `TimelineScreen.onOpenExternalLink` の型を `(itemId) -> Unit` から `(url) -> OpenLinkResult` に変更しました。
  内部で `itemId` は `card.link` 経由のラムダで保持しています。

STATUS: complete
