# v1 スモークチェックリスト

`design/SPEC.md` §10「受け入れ基準（v1 / 抜粋）」を 1 項目ずつ追跡するための手動チェックリストです。
リリース判定担当者は、対応する自動テストの実行結果と、本書の手動確認手順の実施結果を照合し、
チェックボックスをすべて埋めてください。

- 想定実施時期: v1 全機能 Issue（#16〜#53）が main にマージされ、Bearer トークン認証サーバー
  （`hitoshiichikawa/feedman` 側の追加実装）がステージング環境（`https://stg-feed.markte-river.net`）に
  デプロイされた直後
- 想定実施環境: 実機（API 26 以上）/ Android Studio で `app` モジュールを `Run`、もしくは
  `./gradlew assembleDebug` で生成した APK を `adb install`
- 関連ドキュメント: [`design/SPEC.md`](../design/SPEC.md) / [`design/SERVER.md`](../design/SERVER.md) /
  [`docs/GRAND-DESIGN.md`](GRAND-DESIGN.md) / [`README.md`](../README.md)

> **凡例**
> - **自動テストで担保**: JVM 単体テストでロジックが回帰確認されている項目。担当テストクラスを併記
> - **手動確認**: 実機（または Android Studio エミュレータ）で UI 操作して確認する項目
> - **サーバーデプロイ後に実施**: 実トークン発行・実 API 連携を伴う項目。サーバー未デプロイ時点では
>   モックモード（`feedman.mockMode=true`）で代替可能な範囲のみ確認

## 0. ローカル検証（サーバー不要）

リリース判定の前提として、リポジトリローカルで以下が exit code 0 で完了することを確認します。

- [ ] **`./gradlew build` が成功する**
  - compile / Android Lint / JVM 単体テスト（`app/src/test/`）が一括で通る
  - 区分: 自動テストで担保（GitHub Actions の `Build and Unit Test` ジョブと同等）
- [ ] **`./gradlew test` が成功する**
  - JVM 単体テストのみを実行
  - 区分: 自動テストで担保
- [ ] **`./gradlew assembleDebug -Pfeedman.mockMode=true` が成功する**
  - モックモード APK のビルドが通る
  - 区分: 自動テストで担保（コンパイル成功 = pass）

## 1. Google ログイン → 横断タイムライン表示まで到達できる（SPEC §10 第1項）

- [ ] **1.1 ログイン UI 表示**
  - 区分: 自動テストで担保（`feature.login.LoginViewModelTest` / `feature.login.AuthorizationUrlBuilderTest`）+ 手動確認
  - 手順: アプリ起動 → ログイン画面で「Google でログイン」ボタンが表示されること
- [ ] **1.2 OAuth コールバック（`feedman://auth/callback`）受領**
  - 区分: 自動テストで担保（`core.auth.AuthCallbackParserTest` / `core.auth.AuthCallbackDispatcherTest` /
    `core.auth.PkceTest`）
  - 手順: 「Google でログイン」タップ → Chrome Custom Tabs で Google 認証 → `feedman://auth/callback?...`
    でアプリに戻ること
  - **サーバーデプロイ後に実施**: 実トークン発行は Bearer トークン認証エンドポイントが必要。
    サーバー未デプロイ時点では `feedman.mockMode=true` でログイン placeholder をスキップし、
    ドロワー + モックタイムライン表示までの導線のみ確認
- [ ] **1.3 トークン取得・保存**
  - 区分: 自動テストで担保（`core.auth.AuthRepositoryImplTest` / `core.auth.AuthInterceptorTest` /
    `core.network.TokenAuthenticatorTest` / `core.auth.fake.InMemoryTokenStoreTest`）
  - 手順: ログイン成功直後にトークンが `EncryptedSharedPreferences` に保存されること（ログ確認 or
    再起動で再ログインを求められないこと）
  - **サーバーデプロイ後に実施**
- [ ] **1.4 セッション復元と横断タイムライン到達**
  - 区分: 自動テストで担保（`core.auth.AuthRepositorySessionStateProviderTest` /
    `core.auth.MockModeSessionStateProviderTest` / `shell.AppShellViewModelTest`）+ 手動確認
  - 手順: ログイン後に横断タイムライン（ドロワー左上「すべて」/ Top）が表示されること

## 2. 横断タイムラインが無限スクロールし、`since_time` がセッション中固定される（SPEC §10 第2項）

- [ ] **2.1 `since_time` がセッション中固定**
  - 区分: 自動テストで担保（`core.data.CrossFeedRepositoryImplTest` / `core.network.FeedmanApiTest`）
  - 手順: タイムラインを開いた直後の `since_time` がセッション中に変化しないこと（ログ確認）
- [ ] **2.2 カーソル方式の無限スクロール**
  - 区分: 自動テストで担保（`core.network.paging.CursorPagingSourceTest` /
    `feature.timeline.TimelineViewModelTest` / `feature.timeline.TimelineCardModelMapperTest`）+ 手動確認
  - 手順: タイムライン末尾までスクロール → 自動で次ページを読み込み、`cursor` 形式で連続表示されること
- [ ] **2.3 リフレッシュ挙動**
  - 区分: 自動テストで担保（`core.ui.TimelineScreenStateTest` / `core.ui.ListFooterStateTest`）+ 手動確認
  - 手順: Pull-to-refresh で先頭が再取得されること（横断は GET 再取得のみ。SPEC §10 付録 A-5 と整合）

## 3. フィード別一覧でフィルタが `filter` クエリで切り替わる（SPEC §10 第3項）

- [ ] **3.1 すべて / 未読 / スターのトグル**
  - 区分: 自動テストで担保（`feature.feed.FeedScreenViewModelTest` / `feature.feed.FeedStatusBannerTest` /
    `core.data.FeedItemsRepositoryImplTest`）+ 手動確認
  - 手順: ドロワーからフィードを選択 → 上部のフィルタタブで「すべて」「未読」「スター」を順に切替 →
    一覧内容が `filter` クエリ変更分のみ更新されること

## 4. 記事タップで部分シートが開き、開いた時点で既読化される（SPEC §10 第4項）

- [ ] **4.1 部分シート表示**
  - 区分: 自動テストで担保（`feature.articledetail.ArticleDetailViewModelTest` /
    `feature.articledetail.ArticleDetailContentPolicyTest` / `core.ui.FeedmanSheetLabelTest`）+ 手動確認
  - 手順: タイムラインで任意の記事をタップ → 部分（プレビュー）シートが開いてサマリ / 抜粋が表示されること
- [ ] **4.2 既読化（楽観的更新 + サーバー反映）**
  - 区分: 自動テストで担保（`core.data.ItemStateStoreTest` / `core.data.ItemDetailRepositoryImplTest` /
    `feature.timeline.TimelineCardModelMapperTest`）+ 手動確認
  - 手順: シートを開いた瞬間に一覧の既読バッジが落ちること、シートを閉じても既読状態が維持されること

## 5. 「元記事を開く」で Custom Tabs が起動し、当該記事が既読化される（SPEC §10 第5項）

- [ ] **5.1 Chrome Custom Tabs 起動**
  - 区分: 自動テストで担保（`core.ui.LinkOpenerLogicTest` / `core.ui.UrlValidationTest`）+ 手動確認
  - 手順: 詳細シートの「元記事を開く」ボタンタップ → 外部ブラウザではなく Chrome Custom Tabs で開くこと
- [ ] **5.2 元記事閲覧時の既読化**
  - 区分: 自動テストで担保（`core.data.ItemStateStoreTest` /
    `feature.articledetail.ArticleDetailViewModelTest`）+ 手動確認
  - 手順: Custom Tabs を閉じてアプリに戻った際、当該記事が既読扱いになっていること

## 6. スターのトグルが一覧 / 詳細 / スター一覧で整合する（SPEC §10 第6項）

- [ ] **6.1 スター ON → スター一覧に反映**
  - 区分: 自動テストで担保（`core.data.StarredItemsRepositoryImplTest` /
    `feature.starred.StarredViewModelTest` / `feature.starred.StarredCardModelMapperTest` /
    `core.data.ItemStateStoreTest`）+ 手動確認
  - 手順: 一覧または詳細でスターを ON → ドロワーから「スター」を開く → 当該記事が表示されること
- [ ] **6.2 スター OFF → 一覧 / 詳細から整合した除外**
  - 区分: 自動テストで担保（同上）+ 手動確認
  - 手順: スター一覧で OFF にして戻る → 元の一覧 / 詳細でもスター OFF 表示になっていること

## 7. フィード別 Pull-to-refresh が `POST .../fetch` を呼び、`FEED_COOLDOWN` 時に `retry_after_seconds` を案内する（SPEC §10 第7項）

- [ ] **7.1 `POST /api/subscriptions/{id}/fetch` 呼び出し**
  - 区分: 自動テストで担保（`feature.feed.FeedScreenViewModelTest` /
    `core.data.SubscriptionRepositoryImplTest` / `core.network.FeedmanApiTest`）+ 手動確認
  - 手順: フィード別一覧で Pull-to-refresh → サーバーログまたは MockWebServer 系テストで
    `POST /api/subscriptions/{id}/fetch` が発行されることを確認
- [ ] **7.2 `FEED_COOLDOWN`（429）応答時のスナックバー案内**
  - 区分: 自動テストで担保
    （`feature.feed.FeedScreenViewModelTest.onPullToRefresh が FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2` /
    `feature.feed.FeedScreenViewModelTest.onPullToRefresh が FEED_COOLDOWN かつ retryAfterSeconds 欠落のとき null を流す_Issue42 Req 3_3` /
    `core.network.FeedmanErrorMapperTest`）+ 手動確認
  - 手順: クールダウン中（前回 fetch から `FEED_COOLDOWN` 期間内）に Pull-to-refresh →
    `retry_after_seconds` 値（例: 「30 秒後に再試行できます」）がスナックバー / バナーで案内されること

## 8. フィード登録・購読解除・間隔変更・再開が各エンドポイントで成功する（SPEC §10 第8項）

- [ ] **8.1 フィード登録**
  - 区分: 自動テストで担保（`feature.registerfeed.RegisterFeedViewModelTest` /
    `feature.registerfeed.RegisterFeedErrorResolverTest` / `feature.registerfeed.RegisterFeedUiStateTest` /
    `core.data.FeedRegistrationRepositoryImplTest`）+ 手動確認
  - 手順: ドロワーから「フィード登録」→ URL 入力 → 登録 → 一覧に新フィードが追加されること
- [ ] **8.2 購読解除 / 再開（間隔変更含む）**
  - 区分: 自動テストで担保
    （`feature.subscriptionsettings.SubscriptionSettingsViewModelTest` /
    `core.data.SubscriptionRepositoryImplTest`）+ 手動確認
  - 手順: 各フィードの購読設定シートから「購読解除」/「再開」を切替、変更が API に反映され
    ドロワーから即時反映されること
- [ ] **8.3 フェッチ間隔セグメント = 30 / 60 / 180 / 360 分（15 分は廃止）**
  - 区分: 自動テストで担保
    （`feature.subscriptionsettings.SubscriptionSettingsViewModelTest.現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2`）+ 手動確認
  - 手順: 購読設定シートのセグメントが **30 / 60 / 180 / 360 分** の 4 値のみであること、
    15 分のセグメントが存在しないこと（SPEC §10 付録 A-6 / 2026-06-12 確定と整合）
- [ ] **8.4 横断検索の到達**
  - 区分: 自動テストで担保（`feature.search.SearchViewModelTest` /
    `feature.search.SearchViewModelBridgeTest` / `feature.search.SearchCardModelMapperTest` /
    `core.data.SearchRepositoryImplTest`）+ 手動確認
  - 手順: トップバーの検索アイコンから検索 → キーワード入力 → ヒット件数とカード表示が
    `filter` 違反なく出ること

## 9. ライト / ダーク切替が全画面に反映される（SPEC §10 第9項）

- [ ] **9.1 テーマトグル**
  - 区分: 自動テストで担保（`shell.ThemeToggleLogicTest` / `core.designsystem.ThemeModeTest` /
    `core.designsystem.FeedmanThemeMappingTest` / `core.designsystem.FeedmanColorsTest` /
    `core.designsystem.DataStoreThemeModeRepositoryTest` / `core.designsystem.InMemoryThemeModeRepositoryTest`）+ 手動確認
  - 手順: ドロワーまたは設定からテーマを「ライト / ダーク / システム」で切替 → 起動中の全画面
    （タイムライン / 詳細シート / 検索 / 登録 / 購読設定）が即座に再描画され色が切替わること
- [ ] **9.2 アクセシビリティ（フォントスケール）**
  - 区分: 自動テストで担保（`core.designsystem.FeedmanDimensTest` / `shell.DrawerFeedRowA11yTest`）+ 手動確認
  - 手順: OS 設定でフォントスケールを最大（200%）に上げてアプリを起動 → 主要画面で文字切れや
    タップ領域不足が無いこと

## 10. アカウント管理（ログアウト / 退会）

- [ ] **10.1 ログアウト（revoke + クレデンシャル消去 + キャッシュリセット）**
  - 区分: 自動テストで担保（`core.auth.LogoutCoordinatorTest` /
    `feature.account.AccountSheetViewModelTest`）+ 手動確認
  - 手順: アカウントシートから「ログアウト」→ 再起動して未ログイン状態になっていること
- [ ] **10.2 退会の二段確認**
  - 区分: 自動テストで担保（`core.auth.AccountDeletionCoordinatorTest` /
    `feature.account.AccountSheetViewModelTest`）+ 手動確認
  - 手順: アカウントシートから「退会」→ 二段確認ダイアログ → 確定後にトークン / キャッシュが消去され
    未ログイン状態に戻ること

## 11. 次フェーズ（v1 スコープ外）

以下は v1 スモーク確認の対象外です。SPEC §10 末尾および §7 / SPEC §5.8 で「次フェーズ」と
記述されています。

- [ ] **キーワードプッシュ通知（FCM）**
  - 状態: **次フェーズ実装後に確認**（v1 では実装しない）
  - 関連: `design/SERVER.md` §2 / SPEC §7

---

## 検証結果記録欄

実施者は以下を埋めて Issue / PR にスクリーンショット添付してください。

| 項目 | 実施日 | 実施者 | 環境（端末 / API） | 自動テスト結果 | 手動確認結果 | 備考 |
|---|---|---|---|---|---|---|
| 0. ローカル検証 | | | - | `./gradlew build` exit code | n/a | |
| 1. ログイン到達 | | | | | | サーバーデプロイ後 |
| 2. タイムライン | | | | | | |
| 3. フィルタ | | | | | | |
| 4. 詳細シート | | | | | | |
| 5. Custom Tabs | | | | | | |
| 6. スター整合 | | | | | | |
| 7. Pull-to-refresh + cooldown | | | | | | |
| 8. 登録 / 購読 / 検索 | | | | | | |
| 9. テーマ切替 | | | | | | |
| 10. ログアウト / 退会 | | | | | | |
