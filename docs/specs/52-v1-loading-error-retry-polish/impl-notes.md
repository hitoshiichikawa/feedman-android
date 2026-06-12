# 実装ノート: Issue #52 v1 loading/error/retry ポリッシュ

## 概要

v1 主要画面・シート群（#33〜#48 完了）の loading / empty / error / retry / 終端 / dead-end の
実装を Req 1〜6 / NFR 1〜3 に照らして網羅的に監査した。発見した不揃いは購読設定シートの
初回データ取得時 dead-end 1 件のみで、その他は既に統一基準を満たしていた。

監査の進め方:

1. `core/ui/StateViews.kt` / `core/ui/ListFooterState.kt` の共通プリミティブを正本として
   参照する全画面・全シートを Read で確認
2. 一覧 4 画面（timeline / feed / starred / search）の状態判定（`resolveTimelineScreenState`
   / `resolveListFooterState`）使用状況を grep + Read で確認
3. シート 4 種（articledetail / subscriptionsettings / registerfeed / account）の Loading /
   Error / Retry 経路を Read で確認
4. `R.string.state_end_of_list` の参照を grep し、`EndOfListFooter` 経由で全画面が単一文字列
   リソースを共有していることを確認

## 監査マトリクス（画面・シート × 観点）

凡例: ✅ 適合 / 🔧 是正対象 / ⚠ 不適合だが本 Issue スコープ外

### 一覧 4 画面（横断タイムライン / フィード別 / スター / 検索）

| 観点 | timeline | feed | starred | search | 適用 Req |
|---|---|---|---|---|---|
| 初回 Loading 全画面表示 (`LoadingFullScreen`) | ✅ | ✅ | ✅ | ✅ | 1.1 |
| 追加 Loading フッタ (`LoadingFooter`) | ✅ | ✅ | ✅ | ✅ | 1.2 |
| Loading と他状態の同時非描画 (`resolveTimelineScreenState` 排他) | ✅ | ✅ | ✅ | ✅ (if-else 排他) | 1.3 / 1.4 |
| 初回 Error 全画面 + retry (`ErrorFullScreen`) | ✅ | ✅ | ✅ | ✅ | 2.1 / 2.2 / 2.5 |
| 追加 Error フッタ + retry (`ErrorFooter`) | ✅ | ✅ | ✅ | ✅ | 2.3 / 2.4 |
| API エラー `message` を本文化（FeedmanException.errorMessage 経由） | ✅ | ✅ | ✅ | ✅ | 2.6 / 2.7 |
| 空状態 (`EmptyState` + DefaultEmptyStateIcon) | ✅ (timeline_empty) | ✅ (feed_empty_title + subtitle) | ✅ (starred_empty_title + subtitle) | ✅ (search_empty_title + subtitle) | 3.1 / 3.2 / 3.3 |
| 検索クエリ未入力時のサジェスト表示（エラー非表示） | — | — | — | ✅ (`SuggestionChips`) | 3.4 |
| 終端フッタ (`EndOfListFooter` / `state_end_of_list`) | ✅ | ✅ | ✅ | ✅ | 4.1 / 4.2 / 4.3 |
| フッタ排他判定 (`resolveListFooterState`) | ✅ | ✅ | ✅ | ✅ | 4.2 / NFR 2.1 |

### シート 4 種

| 観点 | articledetail | subscriptionsettings | registerfeed | account | 適用 Req |
|---|---|---|---|---|---|
| 初回データ取得中の Loading 表示 | ✅ (`SheetLoadingBody` / `CircularProgressIndicator`) | 🔧 → ✅ (本 Issue で `Loading` 状態追加) | ✅ (フェッチ動作なし。フォーム表示は即時) | ✅ (`AccountSheetUserStatusLine.Loading`) | 5.1 |
| 初回データ取得失敗時の Error + retry + close 露出 | ✅ (`SheetErrorBody` + `ErrorFullScreen`) | 🔧 → ✅ (本 Issue で `NotFound` 状態追加) | ✅ (該当する初回 fetch なし) | ✅ (`AccountSheetUserStatusLine.Error` + retry ボタン) | 5.2 / 5.3 |
| 送信系エラー時の可視通知 + 自動 close 抑止 | ✅ (snackbar 経由 / シート保持) | ✅ (`errorMessage` フィールド表示 / シート保持) | ✅ (`OutlinedTextField.isError` + 文言表示) | ✅ (退会失敗ダイアログ / Idle 復帰) | 5.4 |

### Req 6: フィード別 Pull-to-refresh クールダウン

| 観点 | 実装 | 状態 |
|---|---|---|
| `FEED_COOLDOWN` 応答時の `retry_after_seconds` 案内 | `FeedScreenViewModel` → `FeedScreenEvent.FetchCooldown(retryAfterSeconds)` → `feed_fetch_cooldown_with_seconds` で残秒数を含む snackbar | ✅ Req 6.1 |
| その他失敗時のユーザー通知・既存表示保持 | `FeedScreenEvent.FetchFailed(message)` → snackbar、Paging 一覧は invalidate しない | ✅ Req 6.2 |
| Pull-to-refresh 再試行可能状態への復帰 | `fetchInProgress` 解除でジェスチャ再受付 | ✅ Req 6.3 |

## Requirement ID → 担保テスト対応表

`./gradlew test` で実行される JVM 単体テストで以下を担保する。

| Req | 担保するテスト | 備考 |
|---|---|---|
| 1.1 / 1.2 / 1.3 / 1.4 (排他) | `core.ui.TimelineScreenStateTest` 全 8 ケース、`core.ui.ListFooterStateTest` | `resolveTimelineScreenState` / `resolveListFooterState` の状態排他を網羅検証 |
| 2.1 / 2.2 / 2.5 | `core.ui.TimelineScreenStateTest#Req 3_2 refresh error and item count zero returns InitialError`（Issue #34 既存 / Req 2.1 と同義） | UI 側で `ErrorFullScreen(onRetry=items.retry())` を呼ぶ経路は Composable 側で実装 |
| 2.3 / 2.4 | `core.ui.ListFooterStateTest`（`Error` 排他検証） | UI 側で `ErrorFooter(onRetry=items.retry())` を呼ぶ経路は Composable 側で実装 |
| 2.6 / 2.7 | `core.network.FeedmanErrorMapper*Test`（既存）+ FeedmanException の `errorMessage` / `FALLBACK_UNKNOWN_MESSAGE` / `FALLBACK_NETWORK_MESSAGE` 規約 | 各画面は `(refresh as? LoadState.Error)?.error?.message` を渡し、null のとき `state_error_default_message` |
| 3.1 / 3.2 / 3.3 | `core.ui.TimelineScreenStateTest#Req 6_1`（refresh NotLoading + itemCount=0 → Empty） | 各画面で `EmptyState(title=画面固有 string)` を渡す |
| 3.4 | `feature.search.SearchViewModelTest`（既存 `submittedQuery == null` 経路） | `SearchScreenContent` 内 if 分岐で `SuggestionChips` のみ描画 |
| 4.1 / 4.2 / 4.3 | `core.ui.ListFooterStateTest`（`EndOfList` 排他）+ 全画面が `EndOfListFooter` を介して `R.string.state_end_of_list` を共有 | grep で `state_end_of_list` 参照を 1 箇所（StateViews.kt）に集約済み |
| 5.1 (subscriptionsettings) | `feature.subscriptionsettings.SubscriptionSettingsViewModelTest#Issue 52 Req 5_1 open 直後で観測前は Loading を経由する`（本 Issue 追加） | `GateRepository` で observeFeed を Loading に固定し、emit で Visible に遷移 |
| 5.1 (articledetail / account) | `feature.articledetail.ArticleDetailViewModelTest`（Loading 状態遷移、既存） / `feature.account.AccountSheetViewModelTest`（Loading→Loaded/Error、既存） | |
| 5.2 (subscriptionsettings) | `feature.subscriptionsettings.SubscriptionSettingsViewModelTest#Issue 52 Req 5_2 Repository が未存在 feedId を返したら NotFound に遷移する`（本 Issue 追加） | 旧テスト「Hidden のまま」を NotFound に更新 |
| 5.2 (articledetail / account) | `ArticleDetailViewModelTest` / `AccountSheetViewModelTest`（既存の Error 状態遷移） | |
| 5.3 | `SubscriptionSettingsViewModelTest#Issue 52 Req 5_3 NotFound から retry で再観測する`（本 Issue 追加）/ 既存の `ArticleDetailViewModel.retry()` / `AccountSheetViewModel.retry()` テスト | |
| 5.4 | `SubscriptionSettingsViewModelTest` 既存の保存 / 解除 / 再開エラーケース、`ArticleDetailViewModelTest` 既存の star/markRead 失敗、`RegisterFeedViewModelTest` 既存の失敗ケース、`AccountSheetViewModelTest` 退会失敗 → Error ダイアログ | 送信失敗時にシートを自動 close しない契約をすべて検証 |
| 6.1 / 6.2 / 6.3 | `feature.feed.FeedScreenViewModelTest`（fetch クールダウン / 失敗イベント発火 / fetchInProgress クリア、既存） | |

## 是正内容（実装変更）

### 1. 購読設定シートの初回データ取得時 dead-end 解消

#### 観測された問題

- `SubscriptionSettingsViewModel.open(feedId)` 直後、`observeFeed(feedId)` がまだ最初の
  emission を返していない間は `_uiState = Hidden` のままだった
- `SubscriptionSettingsSheet` は `state !is Visible` のとき `return` していたため、シートは
  画面に出ない（ユーザーから見れば「設定アイコンを押したのに何も起きない」）
- `observeFeed` が null を流した（=対象 feedId が repository キャッシュに無かった）場合も
  Hidden のままで、Req 5.2 違反（dead-end）

#### 修正

`SubscriptionSettingsUiState` に以下を追加:

- `Loading(feedId: String)` — open 直後で観測待ち（Req 5.1）
- `NotFound(feedId: String)` — observeFeed が初回 null を流した（Req 5.2）

`SubscriptionSettingsViewModel`:

- `open(feedId)` で直ちに `Loading` に遷移
- observeFeed が non-null → `Visible`
- observeFeed が null:
  - 直前 `Loading` → `NotFound`（初回失敗）
  - 直前 `Visible` → `Hidden`（解除等で消えた、既存挙動）
  - 直前 `NotFound` → そのまま維持
- `retry()` 追加: `NotFound` / `Loading` / `Visible` のいずれの feedId からも `open(feedId)` を
  再実行

`SubscriptionSettingsSheet`:

- `Hidden` 以外（Loading / NotFound / Visible）は `FeedmanSheet` を表示
- `SubscriptionSettingsLoadingBody`: ヘッダ + `CircularProgressIndicator` を中央配置
- `SubscriptionSettingsNotFoundBody`: ヘッダ + `ErrorFullScreen(onRetry=retry, message=subscription_settings_not_found)` を中央配置
- Loading / NotFound の両 body にも常時「閉じる」アイコンを露出（Req 5.2 dead-end 回避）

#### 文字列リソース追加

- `R.string.subscription_settings_not_found = "この購読が見つかりません。ドロワーを開き直すと再試行できます"`

#### テスト

- 既存「Hidden のまま」アサートを「NotFound に遷移」に更新
- `Issue 52 Req 5_1 open 直後で観測前は Loading を経由する` を新規追加（GateRepository で
  Flow 制御）
- `Issue 52 Req 5_3 NotFound から retry で再観測する` を新規追加（MutableRepository で再
  emission を制御）

## 適合と判定した項目

監査時点で Req 1〜6 / NFR 1〜3 を既に満たしていた既存実装:

- **横断タイムライン**（`feature.timeline.TimelineScreen`）: Issue #34 で確立した
  `resolveTimelineScreenState` + `resolveListFooterState` で Req 1.x / 2.x / 3.x / 4.x を全て満たす
- **フィード別**（`feature.feed.FeedScreen`）: 同上 + Req 6.x の `FetchCooldown(retryAfterSeconds)`
  event 経由で残秒数 snackbar を表示。Req 6.2 は `FetchFailed(message)` event で snackbar 表示、
  refresh は `POST /fetch` 経路で Paging の `refresh` を破壊しない契約
- **スター一覧**（`feature.starred.StarredScreen`）: 同 helper 流用で Req 1〜4 を満たす。
  `starred_empty_title` / `starred_empty_subtitle` で空状態文脈テキストを供給（Req 3.2）
- **検索**（`feature.search.SearchScreen`）: `SearchResultsArea` 内の if-else 排他で Req 1.x / 2.x /
  3.1〜3.3 を満たす。空クエリ時は `SuggestionChips` のみ描画して Req 3.4 を満たす
- **記事詳細シート**（`feature.articledetail.ArticleDetailSheet`）: `SheetLoadingBody` / `SheetErrorBody`
  / `SheetContentBody` で Req 5.1 / 5.2 / 5.3 を満たす。送信系（既読化 / スター）失敗は
  snackbar 通知 + シート保持で Req 5.4 を満たす
- **フィード登録シート**（`feature.registerfeed.RegisterFeedSheet`）: 初回 fetch 動作は無く
  フォーム入力中心。送信失敗は `OutlinedTextField.isError` + 文言表示で Req 5.4、シート保持で
  自動 close しない
- **アカウントシート**（`feature.account.AccountSheet`）: `AccountSheetUserStatusLine.Loading`
  で Req 5.1、`AccountSheetUserStatusLine.Error` + retry ボタンで Req 5.2 / 5.3、退会失敗時の
  `AccountSheetDeleteErrorDialog` で Req 5.4

NFR 1.1（共通プリミティブ集約）: 全画面が `core/ui/StateViews.kt` の `LoadingFullScreen` /
`LoadingFooter` / `EmptyState` / `ErrorFullScreen` / `ErrorFooter` / `EndOfListFooter` を経由。
独自実装は購読設定シートの初回 Loading / NotFound のみだが、内部で `CircularProgressIndicator`
（Material 3 標準）と `ErrorFullScreen`（core/ui プリミティブ）を再利用しているため NFR 1.1 を
満たす。

NFR 1.2（最小タップ標的 44dp）: 既存実装で `sizeIn(minHeight = 44.dp)` / `feedmanDimens.minTapTarget`
を踏襲している `TextButton` / `IconButton` を引き続き使用。本 Issue で追加した
`SubscriptionSettingsPlaceholderHeader` の閉じるアイコンも `MaterialTheme.feedmanDimens.minTapTarget`
を採用。

NFR 1.3（ライト/ダーク両テーマ）: 既存デザイントークン（`MaterialTheme.colorScheme.primary` /
`feedmanColors.mutedFg` / `feedmanColors.border`）のみを使用しているため両テーマ整合性を維持。

NFR 2.1（フッタ排他判定の純粋関数）: `resolveListFooterState` を継続使用。`ListFooterStateTest`
で網羅検証済み。

NFR 2.2（UiState から一意判定可能）: `SubscriptionSettingsUiState` を `Hidden` / `Loading` /
`NotFound` / `Visible` の sealed interface に拡張。各状態は排他で sheet body が `when` 分岐で
唯一の描画を選択する。

NFR 3.x（互換性）: 既存テスト群（#34 / #38 / #42 / #43 / #45 / #46 / #48）はすべて通過。
API 契約・採用案・スコープを変更していない（Req 7.x 維持）。

## 確認事項（レビュワー判断ポイント）

1. **`FeedScreen` のフィード未存在時 retry の有効性**
   - `showFeedNotFound = !subscriptionLoaded && refresh is NotLoading && itemCount == 0`
     で `ErrorFullScreen(onRetry=pagingItems.retry())` を出すが、subscription 自体は
     Paging 経路で取得していないため `retry()` は subscription の再取得を行わない
   - Req 2.5「dead-end を描画しない」には「retry ボタン露出」の意味では適合（ボタンは押せて
     Paging に retry 要求は出る）が、実効的にユーザーが回復するには戻る → ドロワー再表示
     経路が必要。現状は戻る経路 / シェル経路で代替可能なため、本 Issue では仕様変更しない
   - Issue 分割提案: 「フィード未存在画面で subscription 再取得を含めた完全な retry」を別
     Issue として切る余地あり（v1 スコープ外の改善）

2. **検索結果 `submittedQuery` 切替時の状態描画優先順位**
   - `SearchResultsArea` 内の if-else は `refresh is Loading && itemCount == 0` →
     `refresh is Error && itemCount == 0` → `refresh is NotLoading && itemCount == 0` →
     `else (ResultsList)` の順
   - `itemCount > 0` で refresh エラーが起きた場合は `else` 分岐で `ResultsList` を表示し、
     フッタ状態側で append エラーを示さない（refresh エラーなので）。Timeline / Starred の
     ように snackbar 通知を流すパスは検索画面には実装されていない
   - 但し検索は pull-to-refresh が無いため、refresh エラーは初回 submit 失敗（itemCount=0）の
     ケースに収束する想定で、本 Issue では追加実装しない

3. **`AccountSheet` の初回 Loading 表示位置**
   - Loading インジケータはユーザー領域（ヘッダ）の 2 行目に小さく出るのみで、シート全体を
     ローディング表示で覆う設計ではない（Req 5.1 は「シート内に進行中であることを示す表示」を
     要求するので、本配置で適合と判定）
   - ただし設計選択として「シート全体ローディングに統一」する判断もありえる。視覚一貫性
     観点（NFR 1.1）では「シートの中身がローディング種別ごとに少しずつ異なる」が現状

4. **空状態の subtitle 充実度**
   - `timeline_empty` は title のみで subtitle 無し
   - `feed_empty_title` / `feed_empty_subtitle`、`starred_empty_title` / `starred_empty_subtitle`、
     `search_empty_title` / `search_empty_subtitle` は title + subtitle
   - Req 3.2「画面の文脈に合致する主題テキスト」は最低限「title」で満たすため適合と判定したが、
     UX 観点で timeline にも subtitle を追加するなら別 Issue を切る余地あり

## 派生 Issue 提案

- `feat(feed): フィード未存在画面の retry で subscription 自体を再取得する`（Req 2.5 の
  実効性強化）
- `feat(timeline): 空状態に補助テキストを追加して 4 画面で subtitle を揃える`（NFR 1.1 の
  視覚一貫性強化）

## ビルド・テスト結果

- `./gradlew compileDebugKotlin`: BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL
- `./gradlew build`（lint + test + assemble）: BUILD SUCCESSFUL

STATUS: complete
