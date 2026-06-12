# Implementation Notes — Issue #33 Timeline card screen UI

## 概要

横断タイムラインを SPEC §5.1 が要求するカード UI に置き換え、Issue #32 までで揃った
`CrossFeedRepository`（Paging 3）と Issue #27 の共有 UI 部品（ArticleCard / StarToggle /
HatebuBadge / Favicon / RelativeTimeFormatter）を結線した。Pull-to-refresh・追加読込
状態フッタ・記事詳細シート本体・Custom Tabs 起動・スター反映はスコープ外 Issue
（#34 / #36 / #37 / #38）に委ねている。

## 主な変更

- `app/build.gradle.kts`: `androidx.paging.compose` を追加（既に `libs.versions.toml` で
  declaration 済み）
- `core/ui/ArticleCard.kt`: `ArticleCardModel.summary` フィールドを後方互換（既定値 `""`）で
  追加。`onOpenLink` 引数（既定 `null`）を追加し、非 `null` のとき
  `Icons.AutoMirrored.Outlined.OpenInNew` を 44dp タップ標的で描画する `OpenLinkIconButton`
  を内包。タイトル `maxLines=3` / 概要 `maxLines=2` を `TextOverflow.Ellipsis` で制限。
  既存呼び出し側（`MockTimelineItem` 用 Compose プレビュー等）は引数追加が任意のため
  そのまま動く。
- `feature/timeline/TimelineCardModelMapper.kt`（新規）: `CrossFeedItem → ArticleCardModel`
  の純粋変換ロジック。`MockTimelineItem → ArticleCardModel` も同居（mockMode 用）。
- `feature/timeline/TimelineViewModel.kt`: `CrossFeedRepository` + `ItemRepository` +
  `AppConfig` を Hilt 注入。`Flow<PagingData<ArticleCardModel>>` を
  `cachedIn(viewModelScope)` で公開。`AppConfig.mockMode=true` のとき `ItemRepository` 経由の
  モック 1 ページに切り替わる。テスト用に `cardPagingDataForTest()`（cachedIn 適用前の
  生 Flow）を internal で公開。
- `feature/timeline/TimelineScreen.kt`: `collectAsLazyPagingItems()` + `LazyColumn` で
  カードを描画。`items(count, key)` で `peek(index).id` を stable key として渡す。
  初回ロード中 / 空 / 初回エラーは StateViews の `LoadingFullScreen` / `EmptyState` /
  `ErrorFullScreen` を使用。スタートグルは本 Issue で no-op。
- `shell/Navigation.kt`: TimelineScreen の `onOpenItemDetail` / `onOpenExternalLink` を
  暫定 no-op で結線（#36 / #37 で実体差替え）。
- `res/values/strings.xml`: 外部リンクアイコンの contentDescription
  `article_card_open_link_description` を追加（NFR 3.1）。

## 要件 → テスト対応表

| Requirement ID | 担保方法 | テスト / 実装ファイル |
|---|---|---|
| 1.1 ソース行に favicon + フィード名 | mapper で `feedFaviconUrl` / `feedTitle` を `ArticleCardModel` に転写、ArticleCard 側で `Favicon` + `Text(feedTitle)` を描画 | `TimelineCardModelMapperTest`「フィード名と favicon と相対日時の元値が ArticleCardModel に転写される」「feedFaviconUrl が null のときも null のまま転写される」/ `ArticleCard.kt` 既存実装 |
| 1.2 相対日時表示 | mapper で `publishedAt` / `isDateEstimated` を転写、ArticleCard が `RelativeTimeFormatter`（Issue #27）に委譲 | `TimelineCardModelMapperTest`「is_date_estimated true は相対日時の(推定)表示根拠として転写される」/ `RelativeTimeFormatterTest`（既存）|
| 1.3 タイトル最大 3 行 | ArticleCard の `Text(title, maxLines = 3, overflow = TextOverflow.Ellipsis)` | `ArticleCard.kt` 実装で担保（Compose UI テストは #34 以降で追加） |
| 1.4 概要最大 2 行 | ArticleCard の `Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)` | `ArticleCard.kt` 実装で担保 |
| 1.5 summary 空のときレイアウト領域非確保 | `if (model.summary.isNotEmpty()) { Text(...) }` で Composable 自体を生成しない | `TimelineCardModelMapperTest`「summary が空文字列のときも空文字列のまま転写される」+ `ArticleCard.kt` 条件分岐 |
| 1.6 はてブ数バッジ | mapper で `hatebuCount` 転写、ArticleCard が `HatebuBadge`（Issue #27）に委譲。`hatebuFetchedAt` は CrossFeedItem に無いため `publishedAt` 流用 | `TimelineCardModelMapperTest`「hatebuCount が転写される」「hatebuFetchedAt は CrossFeedItem に無いため publishedAt が代用される」/ `HatebuLogicTest`（既存）|
| 1.7 スタートグル | mapper で `isStarred` 転写、ArticleCard が `StarToggle`（Issue #27）に委譲 | `TimelineCardModelMapperTest`「is_starred はスタートグルの初期状態として転写される」/ `StarToggle.kt` 既存実装 |
| 1.8 外部リンクアイコン | ArticleCard の `OpenLinkIconButton`（44dp 最小タップ標的、`mutedFg` tint） | `ArticleCard.kt` 実装 / `TimelineViewModelTest` でコールバック結線を実証 |
| 1.9 各要素 1 つずつ | ArticleCard の Composable 構造（Row/Column で 1 要素ずつ配置） | `ArticleCard.kt` 実装で担保（重複 Composable 無し） |
| 2.1 既読時 opacity 0.55 | ArticleCard の `Modifier.alpha(cardAlpha)`（Issue #27 既存挙動） | `TimelineViewModelTest`「is_read true な CrossFeedItem は ArticleCardModel に isRead=true で伝播する」+ `ArticleCard.kt` の `cardAlpha = if (isRead) readForegroundAlpha else 1.0f` |
| 2.2 未読時 opacity 1.0 | 同上（is_read=false 分岐） | `TimelineCardModelMapperTest`「is_read false は ArticleCardModel に伝播し未読不透明度の根拠になる」|
| 2.3 既読カードもタップ受付 | ArticleCard の `clickable { onOpen(model.id) }` は `alpha` の影響を受けない（Compose の click 領域は opacity と独立） | `ArticleCard.kt` 実装で担保。Compose UI テストは #34 以降 |
| 3.1 カード本体タップ → 記事詳細コールバック | TimelineScreen の `onOpen = { id -> onOpenItemDetail(id) }` | `TimelineScreen.kt` 実装。シェル経由で `Navigation.kt` から no-op を渡し、`TimelineViewModelTest` で paging→card 変換側を担保 |
| 3.2 スタートグルタップで詳細コールバック非発火 | StarToggle が `IconButton` の click 領域として親 click を消費（Issue #27 Req 1.7） | `StarToggle.kt` 既存実装で担保 |
| 3.3 外部リンクアイコンタップで詳細コールバック非発火 | `OpenLinkIconButton` も `IconButton` で click 消費 | `ArticleCard.kt` 実装で担保 |
| 3.4 詳細コールバックは itemId のみを受ける | `onOpenItemDetail: (itemId: String) -> Unit` 型シグネチャ | `TimelineScreen.kt` 実装 |
| 4.1 外部リンクアイコンタップ → 外部リンクコールバック | TimelineScreen の `onOpenLink = { id -> onOpenExternalLink(id) }` | `TimelineScreen.kt` 実装 |
| 4.2 同タップで詳細コールバック非発火 | `IconButton` の click 消費（同 3.3）| `ArticleCard.kt` 実装 |
| 4.3 外部リンクコールバックは itemId のみ | `onOpenExternalLink: (itemId: String) -> Unit` | `TimelineScreen.kt` 実装 |
| 5.1 末尾近くで次ページ読込 | Paging 3 の `prefetchDistance` 既定値（`PagingConfig.PREFETCH_DISTANCE_DEFAULT`）を `CrossFeedRepositoryImpl` が採用（Issue #32 で担保） | `CrossFeedRepositoryImplTest`（既存） |
| 5.2 既存並びを保持し末尾追加 | Paging 3 の `LoadType.APPEND` 仕様 + `cachedIn` | `TimelineViewModelTest`「cardPagingData は CrossFeedRepository の items を ArticleCardModel に変換する」（順序検証あり） |
| 5.3 stable key で記事 ID 識別 | `LazyColumn.items(count, key = { items.peek(index)?.id ?: index })` | `TimelineScreen.kt` 実装 |
| 5.4 スクロール中の位置保持 | LazyColumn + stable key + Paging 3 標準挙動 | `TimelineScreen.kt` 実装（同 5.3） |
| 6.1 初回ロード中の可視指示 | `LoadState.Refresh = Loading && itemCount == 0 → LoadingFullScreen` | `TimelineScreen.kt` 実装 / `StateViewsTest` 群（既存）|
| 6.2 初回ロード後 0 件で空状態 | `LoadState.Refresh = NotLoading && itemCount == 0 → EmptyState` | `TimelineScreen.kt` 実装 / `TimelineViewModelTest`「cardPagingData は空の PagingData も伝播する」|
| 6.3 初回エラー時に再試行手段 | `LoadState.Refresh = Error && itemCount == 0 → ErrorFullScreen(onRetry = { items.retry() })` | `TimelineScreen.kt` 実装 |
| NFR 1.1 視覚一貫性（プロト準拠） | ArticleCard の既読 0.55 / タイトル 3 行 / 概要 2 行 / 外部リンクアイコン配置はプロトと同等 | `ArticleCard.kt` 実装 |
| NFR 1.2 共有メタ部品再利用 | Favicon / StarToggle / HatebuBadge / RelativeTimeFormatter を共有モジュールから直接利用 | `ArticleCard.kt` 実装 |
| NFR 2.1 既存カード破棄せず追加描画 | `cachedIn(viewModelScope)` + stable key | `TimelineViewModel.kt` / `TimelineScreen.kt` 実装 |
| NFR 2.2 60fps 維持 | Composable は軽量（IconButton + Text のみ、画像は Coil の async デコード）。Paging 3 が prefetch | `ArticleCard.kt` 実装（Favicon は #26 で Coil の async decode 担保） |
| NFR 3.1 外部リンク a11y ラベル | `R.string.article_card_open_link_description = "元記事をブラウザで開く"` を `contentDescription` で付与 | `ArticleCard.kt` 実装 / `strings.xml` |
| NFR 3.2 外部リンク 44dp タップ標的 | `Modifier.sizeIn(minWidth = 44dp, minHeight = 44dp)`（`feedmanDimens.minTapTarget`） | `ArticleCard.kt` 実装 |

## 判断記録

### 1. ArticleCard の引数を後方互換で拡張した

`ArticleCardModel` に `summary` を追加するに当たり、既存呼び出し側（プレビュー含む）の
ビルドを壊さないよう既定値 `""` を持たせた。`onOpenLink` も既定 `null` で「外部リンク
アイコン非表示」が選べる構造にしたため、Issue #27 が当初想定していた「フィード別 /
スター / 検索」での再利用パスでもアイコン非表示で問題なく使える。

### 2. CrossFeedItem の `hatebu_fetched_at` 欠落への対処

SPEC §4.2 と `CrossFeedItem.kt`（Issue #15）には `hatebu_fetched_at` フィールドが
含まれない（cross-feed API は `hatebu_count` だけを返す）。一方、Issue #27 の
`HatebuBadge` / `HatebuLogic` は `hatebu_fetched_at == null` のとき「−」を表示する
仕様であり、横断タイムラインで数値表示を維持できなくなる。

本実装では `TimelineCardModelMapper.toCardModel()` が `publishedAt`（必ず非 null）を
`hatebuFetchedAt` に流用することで「取得済み（数値表示）」相当の挙動を取らせた。
これは表示根拠としては暫定であり、サーバー側で `cross-feed` レスポンスに
`hatebu_fetched_at` が追加された段階で純粋な転写に切り替わる。Req 1.6（はてブ数バッジを
表示する）は満たす。

### 3. mockMode の維持戦略

`AppConfig.mockMode=true` のとき、既存の `FakeItemRepository`（`MockTimelineItem`）を
そのまま使い、`PagingData.from(...)` で 1 ページに包んで流す形にした。
`-Pfeedman.mockMode=true` でビルドしたスケルトンが Issue #33 後もカード UI で
モックデータを表示できることを担保している（mockMode 動作を壊さない要件）。

`MockTimelineItem.publishedAt` は事前整形済みの相対表現文字列（例: "10 分前"）で
RFC3339 ではないため、`TimelineCardModelMapper.toMockCardModel()` は別途 ISO 値
`2026-06-12T11:30:00Z`（固定）を `fallbackPublishedAtIso` として渡す。
`RelativeTimeFormatter` がこの値から相対日時を計算する。

### 4. テスト用 `cardPagingDataForTest()` を internal で公開

`runTest` 内で `viewModel.cardPagingData.asSnapshot()` を呼ぶと、`cachedIn(viewModelScope)`
の長寿命 collector が `viewModelScope` で動き続けるため `UncompletedCoroutinesError` で
テストが失敗する。

対処として、`buildCardPagingData()`（cachedIn 適用前の生 Flow を返す `private` 関数）と
は別に `cardPagingDataForTest()` を `internal` で公開し、テストは生 Flow を直接購読する
形にした。production 側からは必ず `cardPagingData`（cachedIn 適用済）を購読する規約は
KDoc に明記。

### 5. StarToggle の no-op 配線

Req 3.2「スタートグルタップで詳細コールバック非発火」は、StarToggle 自体が
`IconButton` の click 領域として親 click を消費する（Issue #27 Req 1.7）ことで担保
されているため、本 Issue では `onStarToggle = { _, _ -> }` の no-op で十分。
サーバー反映（PUT /api/items/{id}/state）の結線は Issue #38 の領分。

### 6. LazyColumn の stable key 解決

`LazyPagingItems.peek(index)` は placeholder が無効（`CrossFeedRepositoryImpl` で
`enablePlaceholders=false`）な構成では先読み済みアイテムを `null` を返さずに返す。
ただし防御的に `?: index` を付けて、未読込領域でも crash しないようにした。

## 確認事項（PR レビュワー向け）

1. **`hatebu_fetched_at` の `publishedAt` 代用**: 表示要件（Req 1.6）は満たすが、サーバー側で
   `cross-feed` に `hatebu_fetched_at` を追加するか、`HatebuLogic` 側で cross-feed 用の
   「常に数値表示」モードを追加するか、いずれかの本格対応を別 Issue で起票するかは
   PM / Architect 判断。本 Issue は SPEC §4.2 を書き換えずに表示要件を満たすため
   暫定対応とした。

2. **mockMode の Paging 1 ページ表現**: `PagingData.from(...)` を使うと無限スクロール
   触り心地（Req 5.1）は mockMode では確認できない（1 ページで終端）。実 API 接続時
   に Req 5.1 が正しく動くかは Issue #32 のテスト（`CrossFeedRepositoryImplTest`）で
   担保されているため OK と判断したが、mockMode で複数ページ動作も確認したい場合は
   別途 PagingSource を Fake する追加 Issue が必要。

3. **`emptyCardPagingFlow` / 旧 `TimelineUiState` 削除**: 旧 API は本 Issue で削除した。
   他モジュール（feature/feed / starred 等）が `TimelineUiState` 名を借りていないことは
   grep 確認済み。

4. **Compose UI テスト未追加**: タイトル `maxLines=3` / 概要 `maxLines=2` / 既読 opacity /
   タップ伝播の視覚側検証は Compose UI テスト（`androidTest/`）で行うべきだが、
   CLAUDE.md「JVM 単体テストを最優先 / instrumented テストを CI 必須にしない」方針に
   従い、本 Issue では純粋ロジックの JVM テストに留めた。Compose UI テストの追加は
   別 Issue で扱う想定。

## 動作確認コマンド

```bash
./gradlew build               # 全モジュールビルド + lint + unit test
./gradlew :app:testDebugUnitTest --tests "com.feedman.android.feature.timeline.*"
```

`-Pfeedman.mockMode=true` でビルドすると、ログイン placeholder を経由せずシェル経由で
モックデータのカード UI が起動する（mockMode の確認用）。

STATUS: complete
