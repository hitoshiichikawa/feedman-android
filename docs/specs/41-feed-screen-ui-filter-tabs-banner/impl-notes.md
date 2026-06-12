# Implementation Notes - Issue #41 フィード別画面 UI

## 概要

`docs/specs/41-feed-screen-ui-filter-tabs-banner/requirements.md` の Requirement 1〜4 / NFR 1〜3 に
対して、以下の 3 commit で実装した。

| commit | 概要 |
|---|---|
| `feat(data): SubscriptionRepository に resume / observeFeed を追加` | API 層 / 単一フィード観測点 |
| `feat(feed): FeedScreenViewModel と純粋状態モデルを追加` | ViewModel + 純粋ロジック + 単体テスト |
| `feat(feed): FeedScreen Composable とナビゲーション結線` | UI 層 + Navigation 置換 + strings.xml |

## 設計判断

### 1. ViewModel の責務集約

`FeedScreenViewModel` を 1 つだけ用意し、フィルタタブ・購読情報・記事ページング・再開アクション・
イベント通知をすべて単一の VM に集約した。理由は (a) スコープが画面単位で閉じる (b) Composable
側を stateless に保てる (c) TimelineScreen と同じ流儀で見渡しやすい、ため。

### 2. フィルタ切替時のページング再起動

要件 2.3 / 2.4 で「フィルタ切替で再取得 + スクロール先頭リセット」が求められる。`flatMapLatest`
で `currentFilter` の変化に応じて `FeedItemsRepository.pagingData(feedId, filter)` を呼び直す
構造とした。LazyPagingItems が新しい PagingData を受け取ると LazyColumn の itemCount が 0 から
再構築されるため、スクロール位置は自然に先頭へ戻る。明示的な `scrollToItem(0)` 呼び出しは
不要と判断した。

### 3. 警告バナーの純粋モデル化

`resolveBanner(subscription, resumeInProgress)` を純粋関数として切り出し、JVM 単体テストで
全分岐を網羅した。state は `Hidden` / `Visible(kind, message, resumeInProgress)` のシンプルな
sealed interface とし、文言解決（strings.xml）は呼び出し側（Composable）が担う。
`FallbackMessage` 識別子で「null/blank の `error_message` を状態別の既定文言に置換する」責務分担を明示。

### 4. フィード未存在判定（Req 4.3）

`subscription` Flow が `null` を流す状態は (a) フィード未存在 / (b) 取得未完了 のどちらかと
区別できない。本実装では Composable 側で「`subscription == null` かつ paging refresh が
NotLoading に到達 かつ itemCount = 0」のときに「フィードが見つかりません」表示にした。
取得中（refresh=Loading）は LoadingFullScreen で判定保留する。

### 5. SubscriptionRepository インターフェース拡張

`resume(subscriptionId)` と `observeFeed(feedId)` を `SubscriptionRepository` interface に追加した。
既存実装（FakeSubscriptionRepository / 4 つの匿名テスト double）の破壊を避けるため `resume` は
`throw UnsupportedOperationException` を返す default 実装を持たせ、`observeFeed` は
`observeSubscriptions().mapToSingleByFeedId(feedId)` の既定実装を提供した（NFR 1.1 後方互換）。

## requirement ID → テスト対応表

| Req ID | 検証先テスト |
|---|---|
| 1.1 (画面表示構造) | `FeedScreenViewModelTest.feedId が SavedStateHandle から取得される` / `init で SubscriptionRepository_refresh が呼ばれる`（ViewModel 起動時の挙動）+ Composable 構造は build / lint で間接的に検証 |
| 1.2 (共通カード描画) | `FeedScreenViewModelTest.cardPagingData は ItemSummary を ArticleCardModel に変換する` |
| 1.3 (ローディング) | `core/ui/TimelineScreenStateTest` を流用（`resolveTimelineScreenState` を Composable で適用） |
| 1.4 (空状態) | `FeedScreenViewModelTest.空 PagingData も伝播する` + Composable 側で `EmptyState` 描画 |
| 1.5 (エラー状態) | `core/ui/TimelineScreenStateTest` 流用 + Composable 側 `ErrorFullScreen` 描画 |
| 1.6 (カードタップ詳細シート) | Composable 側で `onOpenItemDetail` を `Navigation` → `articleDetailViewModel.open` 配線（既存 TimelineScreen 流儀） |
| 2.1 (タブ表示) | Composable / strings.xml（`feed_filter_all|unread|starred`）+ `FeedStatusBannerTest.FeedFilter は API の FeedItemFilter へ 1対1 で射影される` |
| 2.2 (初期 ALL) | `FeedScreenViewModelTest.初期 currentFilter は ALL` |
| 2.3 (タブタップで再取得) | `FeedScreenViewModelTest.selectFilter で currentFilter が更新される` / `フィルタを変えると pagingData が新しい filter で再呼び出しされる` |
| 2.4 (スクロール先頭) | Paging 3 の Pager 再生成で itemCount=0 → 自然にスクロール先頭。Composable 設計上保証 |
| 2.5 (フィルタ切替中ロード) | Composable 側 `resolveTimelineScreenState(refresh=Loading, itemCount=0)` → LoadingFullScreen 経路 |
| 2.6 (空状態文言) | strings.xml `feed_empty_title` / `feed_empty_subtitle` + Composable |
| 2.7 (直前選択を引き継がない) | `FeedScreenViewModel` は ViewModel ライフサイクルで `_currentFilter` を持つため、別 feedId へ遷移→新規 VM 生成で常に DEFAULT=ALL から開始する設計 |
| 3.1 (stopped/error でバナー表示) | `FeedStatusBannerTest.feed_status が stopped のとき Visible STOPPED を返す` / `error のとき Visible ERROR` |
| 3.2 (アイコン + 本文 + 再開ボタンの 1 行) | Composable `FeedStatusBannerRow` + FeedStatusBanner.Kind 別アイコン |
| 3.3 (error_message 欠落時の既定文言) | `FeedStatusBannerTest.error_message が null のとき` / `空文字のとき` + strings.xml `feed_banner_default_*_message` |
| 3.4 (active でバナー非表示) | `FeedStatusBannerTest.active のとき Hidden を返す` / `FeedScreenViewModelTest.banner は active のとき Hidden` |
| 3.5 (再開ボタンタップ) | `FeedScreenViewModelTest.onResumeBannerTap で SubscriptionRepository_resume が呼ばれ ResumeSucceeded を流す` |
| 3.6 (再開進行中 disabled) | `FeedStatusBannerTest.resumeInProgress true が Visible へ伝搬する` / `FeedScreenViewModelTest.onResumeBannerTap 進行中フラグが banner に伝搬する` |
| 3.7 (成功でバナー非表示 + 一覧再取得) | `FeedScreenViewModelTest.onResumeBannerTap で SubscriptionRepository_resume が呼ばれ ResumeSucceeded を流す` / Composable `LaunchedEffect` で `pagingItems.refresh()` 呼び出し |
| 3.8 (失敗で snackbar) | `FeedScreenViewModelTest.onResumeBannerTap 失敗時は ResumeFailed をエラーメッセージ付きで流す` |
| 3.9 (本文・アイコン部はタップ無視) | Composable: `Row` 自体に `clickable` を付けず、`TextButton` のみがイベントを受ける構造 |
| 4.1 (起動時に購読情報 + 記事一覧取得) | `FeedScreenViewModelTest.subscription Flow は SubscriptionRepository_observeFeed のスナップショットを公開する` + `SubscriptionRepositoryImplTest.Issue41 Req 4_1 observeFeed で feedId に一致する Subscription を取り出せる` |
| 4.2 (画面再訪時の整合性) | StateFlow 経由で常に最新値を観測する設計。`init` で `refresh()` を起動する（`FeedScreenViewModelTest.init で SubscriptionRepository_refresh が呼ばれる`） |
| 4.3 (フィード未存在表示) | `FeedScreenViewModelTest.subscription が未存在のとき null を流す` + `SubscriptionRepositoryImplTest.Issue41 Req 4_3 observeFeed は未存在 feedId に対して null を流す` + Composable 側のフィード未存在判定（refresh NotLoading + null + itemCount=0） |
| NFR 1.1 / 1.2 (応答性) | Composable: `selectFilter` / `onResumeBannerTap` は同期的に StateFlow を即時更新（Compose の状態反映は ms オーダー） |
| NFR 2.1 (a11y ラベル) | strings.xml の `feed_banner_icon_*` / `feed_banner_resume_button` / `feed_filter_*` を Composable で `contentDescription` / 通常表示文言として参照 |
| NFR 2.2 (タップ標的 44dp) | Composable: フィルタタブ `Modifier.sizeIn(minHeight = 44.dp)` + 再開ボタン `Modifier.sizeIn(minHeight = 44.dp)` |
| NFR 3.1 (文字列リソース外部化) | strings.xml の `feed_*` キー群（追加 12 件） |

## 確認事項（PR レビューワー向け）

1. **Composable 側の UI テスト未追加**: 本 Issue では JVM 単体テスト（ViewModel + 純粋ロジック）に
   絞り、Compose UI Test（`androidTest/`）は追加していない。理由: Issue 本文の指示「ViewModel /
   純粋ロジックの JVM テスト」に従ったこと、および CLAUDE.md「CI 必須は JVM 単体テストのみ」の
   方針。Compose UI Test を別 Issue で扱う方針で良いか確認願いたい。

2. **`SubscriptionRepository.resume` の default 実装**: 既存 `FakeSubscriptionRepository` と
   テスト double の破壊を避けるため `UnsupportedOperationException` を投げる default 実装を
   付けた。FeedScreenViewModel 側は明示的に try-catch で拾い `FeedScreenEvent.ResumeFailed` に
   流す。Fake 経路は対象 ID に対応するモックフィードを `active` に書き換えた仮想スナップショットを
   返すよう実装した（モック UI 確認の挙動を維持するため）。

3. **`SubscriptionRepository.observeFeed` の `distinctUntilChanged`**: 一覧側の他フィード変更で
   再 emit されないように `distinctUntilChanged` を付けた。`Subscription` data class は等値比較
   なのでフィードフィールド全体に対する差分検出となり、当該フィードのみが変化したケースで適切に
   再 emit する。`Subscription.equals` で誤判定が出るパターン（特に `errorMessage = null` ↔ 空文字）
   は呼び出し側の責任範囲とする。

4. **Req 2.4「スクロール先頭」の保証**: フィルタ切替時の Pager 再生成で LazyColumn の itemCount が
   0 → N に再構築されるため、`LazyListState` のスクロール位置は自然に先頭へ戻る。Compose
   public API 上の `LazyListState.scrollToItem(0)` を明示的に呼ばなくても OK と判断したが、
   レビューワー判断で必要なら別 Issue で `LaunchedEffect(currentFilter) { scrollToItem(0) }` の
   追加が可能。

5. **`FeedScreenListContent.subscriptionLoaded` 判定の確実性**: `subscription == null` かつ
   refresh が `LoadState.NotLoading` に到達したタイミングで「フィードが見つかりません」表示にした。
   実 API では並行する `subscription` 取得が完了する前に paging の初回 refresh が完了する可能性が
   ある（実 API では `pagingData` は別エンドポイント `GET /api/feeds/{id}/items`）。実運用では
   稀にフラッシュ的にフィード未存在表示が出てから subscription が確定する瞬間がありうる。重大では
   ないが、より厳密な判定が必要な場合は別 Issue でリファクタしたい。

## 動作確認

```text
$ ./gradlew build
BUILD SUCCESSFUL in 3m
119 actionable tasks: 47 executed, 3 from cache, 69 up-to-date
```

build / lint / 全 unit test がパス。

STATUS: complete
