# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-41-impl-feed-screen-ui
- HEAD commit: 6c8d4eb
- Compared to: origin/main..HEAD
- 対象 commit 群:
  - `642ad5e feat(data): SubscriptionRepository に resume / observeFeed を追加`
  - `b343125 feat(feed): FeedScreenViewModel と純粋状態モデルを追加`
  - `c6ce786 feat(feed): FeedScreen Composable とナビゲーション結線`
  - `6c8d4eb docs(spec): impl-notes.md 追加`

## Verified Requirements

### Requirement 1: フィード別画面の表示と記事カード
- 1.1 — `FeedScreen.kt`（Column で警告バナー＋フィルタタブ＋一覧の縦並びレイアウト）／`FeedScreenViewModelTest.feedId が SavedStateHandle から取得される_Req 1_1`
- 1.2 — `FeedScreenViewModel.kt::toCardModel`（`ItemSummary` → `ArticleCardModel` 変換）／`ArticleCard` 共通コンポーネント再利用／`FeedScreenViewModelTest.cardPagingData は ItemSummary を ArticleCardModel に変換する_Req 1_2`
- 1.3 — `FeedScreen.kt::FeedScreenListContent` の `TimelineScreenState.InitialLoading → LoadingFullScreen` 経路（既存 `resolveTimelineScreenState` 流用）
- 1.4 — `FeedScreenListContent` の `TimelineScreenState.Empty → EmptyState`（`feed_empty_title` / `feed_empty_subtitle`）／`FeedScreenViewModelTest.空 PagingData も伝播する_Req 1_4`
- 1.5 — `FeedScreenListContent` の `TimelineScreenState.InitialError → ErrorFullScreen(onRetry = pagingItems.retry())`
- 1.6 — `FeedScreen` の `onOpenItemDetail` が `Navigation.kt` の Feed ルートから `onOpenItemDetail` パラメータ経由でシェル直下の `ArticleDetailViewModel.open` に伝搬

### Requirement 2: フィルタタブによる絞り込み
- 2.1 — `FeedFilterTabsRow` が `FeedFilter.values()` を `ALL → UNREAD → STARRED` 順で描画（`feed_filter_all|unread|starred`）／`FeedStatusBannerTest.FeedFilter は API の FeedItemFilter へ 1対1 で射影される_Req 1_2 2_3`
- 2.2 — `FeedFilter.DEFAULT = ALL`／`FeedScreenViewModelTest.初期 currentFilter は ALL_Req 2_2`
- 2.3 — `FeedScreenViewModel.selectFilter` + `flatMapLatest` で新 Pager 起動／`FeedScreenViewModelTest.selectFilter で currentFilter が更新される_Req 2_3` / `フィルタを変えると pagingData が新しい filter で再呼び出しされる_Req 2_3 2_4`
- 2.4 — Paging 3 の Pager 再生成（`flatMapLatest`）で itemCount=0 から再構築されるため `LazyColumn` のスクロールが自然に先頭に戻る設計／同テスト
- 2.5 — `resolveTimelineScreenState(refresh=Loading, itemCount=0) → LoadingFullScreen` 経路を共有
- 2.6 — `feed_empty_subtitle` = "フィルタを変えるか、引っ張って更新してください" を `EmptyState` で描画
- 2.7 — `FeedScreenViewModel` は ViewModel ライフサイクル内で `_currentFilter` を保持、別 feedId 遷移 = 新 VM 生成で `DEFAULT=ALL` から開始する設計

### Requirement 3: 停止／エラー時の警告バナーと再開アクション
- 3.1 — `resolveBanner` の `stopped`/`error` 分岐／`FeedStatusBannerTest.feed_status が stopped のとき Visible STOPPED を返す_Req 3_1 3_2` / `feed_status が error のとき Visible ERROR を返す_Req 3_1 3_2`
- 3.2 — `FeedStatusBannerRow` のアイコン (PauseCircle / WarningAmber) + メッセージ + 「再開」TextButton を `Row` で 1 行レイアウト
- 3.3 — `resolveBanner` で `errorMessage?.takeIf { it.isNotBlank() }`、`Visible.fallbackMessage` 識別子を UI 側で `feed_banner_default_stopped_message` / `feed_banner_default_error_message` に解決／`FeedStatusBannerTest.error_message が null のとき` / `空文字のとき`
- 3.4 — `resolveBanner` の `active → Hidden` 早期 return／`FeedStatusBannerTest.feed_status が active のとき Hidden を返す_Req 3_4` / `FeedScreenViewModelTest.banner は active のとき Hidden_Req 3_4`
- 3.5 — `FeedScreenViewModel.onResumeBannerTap` が `subscriptionRepository.resume(currentSub.id)` を呼ぶ／`FeedScreenViewModelTest.onResumeBannerTap で SubscriptionRepository_resume が呼ばれ ResumeSucceeded を流す_Req 3_5 3_7` / `SubscriptionRepositoryImplTest.Issue41 Req 3_5 resume で api subscriptions id resume を POST する`
- 3.6 — `_resumeInProgress` を `combine` で banner に反映 + UI で `TextButton(enabled = !state.resumeInProgress)` + CircularProgressIndicator／`FeedScreenViewModelTest.onResumeBannerTap 進行中フラグが banner に伝搬する_Req 3_6` / `FeedStatusBannerTest.resumeInProgress true が Visible へ伝搬する_Req 3_6`
- 3.7 — `ResumeSucceeded` イベント + `LaunchedEffect` で `pagingItems.refresh()`／`subscription` Flow が active に切替わって banner が自動的に Hidden に／`SubscriptionRepositoryImplTest.Issue41 Req 3_7 resume 成功で観測中の Subscription が active に更新される`
- 3.8 — `catch (FeedmanException)` で `ResumeFailed(message)` を流し snackbar 表示、banner はそのまま／`FeedScreenViewModelTest.onResumeBannerTap 失敗時は ResumeFailed をエラーメッセージ付きで流す_Req 3_8` / `SubscriptionRepositoryImplTest.Issue41 Req 3_8 resume 失敗時は例外を伝搬し購読リストを変えない`
- 3.9 — `FeedStatusBannerRow` の Row 自体に `clickable` を付けず、`TextButton` のみがタップを受ける構造（コード読みで確認）

### Requirement 4: 画面起動・再訪時の整合性
- 4.1 — `FeedScreenViewModel.init { viewModelScope.launch { subscriptionRepository.refresh() } }` + `observeFeed(feedId)` を `stateIn`／`SubscriptionRepositoryImplTest.Issue41 Req 4_1 observeFeed で feedId に一致する Subscription を取り出せる`
- 4.2 — `SharingStarted.WhileSubscribed` で再評価・`init` での `refresh` 起動／`FeedScreenViewModelTest.init で SubscriptionRepository_refresh が呼ばれる_Req 4_2`
- 4.3 — `FeedScreenListContent` の `showFeedNotFound = !subscriptionLoaded && refresh is NotLoading && itemCount == 0` で `ErrorFullScreen(feed_not_found)`／`SubscriptionRepositoryImplTest.Issue41 Req 4_3 observeFeed は未存在 feedId に対して null を流す` / `FeedScreenViewModelTest.subscription が未存在のとき null を流す_Req 4_3`

### Non-Functional Requirements
- NFR 1.1 / 1.2 — `selectFilter` / `onResumeBannerTap` ともに即時 StateFlow 更新（同期反映）
- NFR 2.1 — `feed_banner_icon_stopped|error` を `contentDescription` に、`feed_banner_resume_button` / `feed_filter_*` を可読ラベルとして使用
- NFR 2.2 — フィルタタブ `Modifier.sizeIn(minHeight = 44.dp)`、再開ボタン `sizeIn(minWidth = 64.dp, minHeight = 44.dp)`
- NFR 3.1 — 追加文言はすべて `strings.xml` に `feed_*` キーで外部化（12 件）

## Findings

なし。

## Summary

3 commit にまたがる実装と 3 つのテストクラス（`FeedScreenViewModelTest` 13 ケース + `FeedStatusBannerTest` 9 ケース + `SubscriptionRepositoryImplTest` 5 ケース追加）により Req 1〜4 / NFR 1〜3 すべてが観測可能な実装またはテストで裏付けされている。境界変更は feature/feed 配下の新規ファイル群、core/data の最小拡張（`observeFeed` / `resume` 追加、デフォルト実装で後方互換維持）、shell/Navigation の placeholder 置換、strings.xml 追加、app/src/test 配下に限定されており、tasks.md の `_Boundary:_` 制約と整合する。#42 (pull-to-refresh) / #43 (購読設定シート) には踏み込んでいない。Feature Flag Protocol は opt-out のため flag 観点の確認は適用外。

RESULT: approve
