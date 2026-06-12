# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-33-impl-timeline-card-screen-ui
- HEAD commit: f4a17b3
- Compared to: origin/main..HEAD
- 対象差分: 11 ファイル / +1003 / -105
- Feature Flag Protocol: opt-out（CLAUDE.md 宣言）— flag 観点の細目チェックは適用なし

## Verified Requirements

### Requirement 1: タイムラインカードの構成要素
- 1.1 — `TimelineCardModelMapper.toCardModel()` が `feedTitle` / `feedFaviconUrl` を `ArticleCardModel` に転写し、`ArticleCard` が Favicon + Text を描画。`TimelineCardModelMapperTest`「フィード名と favicon と相対日時の元値が ArticleCardModel に転写される」「feedFaviconUrl が null のときも null のまま転写される」
- 1.2 — `publishedAt` / `isDateEstimated` を転写、ArticleCard が Issue #27 の RelativeTimeFormatter に委譲。`TimelineCardModelMapperTest`「is_date_estimated true は相対日時の(推定)表示根拠として転写される」
- 1.3 — `ArticleCard.kt` の `Text(title, maxLines = 3, overflow = TextOverflow.Ellipsis)`
- 1.4 — `ArticleCard.kt` の `Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)`
- 1.5 — `ArticleCard.kt` の `if (model.summary.isNotEmpty()) { Text(...) }` で Composable 自体を生成せずレイアウト領域を確保しない。`TimelineCardModelMapperTest`「summary が空文字列のときも空文字列のまま転写される」
- 1.6 — `hatebuCount` を転写し HatebuBadge に委譲。`hatebu_fetched_at` 欠落対策として `publishedAt` を流用（数値表示維持。判断記録 §2 に明文化、PR 確認事項 §1 に明記）。`TimelineCardModelMapperTest`「hatebuCount が転写される」「hatebuFetchedAt は CrossFeedItem に無いため publishedAt が代用される」
- 1.7 — `isStarred` を転写し StarToggle に委譲。`TimelineCardModelMapperTest`「is_starred はスタートグルの初期状態として転写される」
- 1.8 — `ArticleCard.kt` の `OpenLinkIconButton`（`Icons.AutoMirrored.Outlined.OpenInNew`、`onOpenLink != null` のとき描画）
- 1.9 — `ArticleCard.kt` の Composable 構造で各要素を 1 つずつ配置（Row/Column）

### Requirement 2: 既読・未読カードの視覚差分
- 2.1 — `ArticleCard.kt` の既存 `cardAlpha = if (isRead) readForegroundAlpha else 1.0f`（Issue #27 で 0.55 が定義済み）。`TimelineViewModelTest`「is_read true な CrossFeedItem は ArticleCardModel に isRead=true で伝播する」
- 2.2 — 同上（unread 分岐）。`TimelineCardModelMapperTest`「is_read false は ArticleCardModel に伝播し未読不透明度の根拠になる」
- 2.3 — `clickable { onOpen(model.id) }` は `alpha` の影響を受けない（Compose 仕様）。実装で担保

### Requirement 3: カードタップによる詳細シート起動コールバック
- 3.1 — `TimelineScreen.kt` の `onOpen = { id -> onOpenItemDetail(id) }`
- 3.2 — StarToggle が IconButton として click 消費（Issue #27 Req 1.7 既存挙動）
- 3.3 — `OpenLinkIconButton` も IconButton として click 消費
- 3.4 — `onOpenItemDetail: (itemId: String) -> Unit` シグネチャ

### Requirement 4: 外部リンクアイコンによるリンクオープンコールバック
- 4.1 — `TimelineScreen.kt` の `onOpenLink = { id -> onOpenExternalLink(id) }`、`OpenLinkIconButton.onClick = { onOpenLink(id) }`
- 4.2 — IconButton click 消費（3.3 と同根拠）
- 4.3 — `onOpenExternalLink: (itemId: String) -> Unit` シグネチャ。Custom Tabs 実体には踏み込んでいない（#37 領分を尊重）

### Requirement 5: 無限スクロールとリスト安定性
- 5.1 — Paging 3 の prefetch（`CrossFeedRepositoryImpl` の既存挙動を継承）
- 5.2 — Paging 3 APPEND 仕様 + `cachedIn(viewModelScope)`。`TimelineViewModelTest`「cardPagingData は CrossFeedRepository の items を ArticleCardModel に変換する」（順序検証あり）
- 5.3 — `TimelineScreen.kt` の `items(count, key = { index -> items.peek(index)?.id ?: index })`
- 5.4 — LazyColumn + stable key + cachedIn の標準挙動

### Requirement 6: 空状態・初期読み込み中状態の最低限の表示
- 6.1 — `LoadState.Refresh = Loading && itemCount == 0 → LoadingFullScreen`
- 6.2 — `LoadState.Refresh = NotLoading && itemCount == 0 → EmptyState`。`TimelineViewModelTest`「cardPagingData は空の PagingData も伝播する」
- 6.3 — `LoadState.Refresh = Error && itemCount == 0 → ErrorFullScreen(onRetry = { items.retry() })`

### Non-Functional Requirements
- NFR 1.1 — タイトル 3 行 / 概要 2 行 / 既読 0.55 / 外部リンクアイコン配置がプロト準拠（実装で担保）
- NFR 1.2 — Favicon / StarToggle / HatebuBadge / RelativeTimeFormatter を Issue #27 から再利用（再実装なし）
- NFR 2.1 — `cachedIn(viewModelScope)` + stable key で既存カード破棄回避
- NFR 2.2 — Composable 軽量（IconButton + Text、画像は Coil async デコード）
- NFR 3.1 — `R.string.article_card_open_link_description = "元記事をブラウザで開く"` を `contentDescription` で付与
- NFR 3.2 — `Modifier.sizeIn(minWidth = 44dp, minHeight = 44dp)` で minTapTarget 確保

### 境界遵守の確認
- 変更ファイルは `feature/timeline/*` / `core/ui/ArticleCard.kt`（後方互換な引数追加 — `summary = ""` / `onOpenLink = null` 既定値）/ `shell/Navigation.kt`（暫定 no-op の最小結線）/ `app/build.gradle.kts`（paging-compose 配線のみ）/ `strings.xml` / `app/src/test/`
- #34 refresh / #36 詳細シート実体 / #37 Custom Tabs 実体 / #38 サーバー反映には踏み込んでいない（TODO コメントで明示）
- 既存 ArticleCard 呼び出し側（プレビュー含む）は引数追加が任意のため後方互換
- 旧 `TimelineUiState` / `TimelineList` の削除に伴う他モジュールへの影響なし（grep 確認、テスト全件 green）

### テスト実行
- `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.feature.timeline.*"`: BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest`（全件）: BUILD SUCCESSFUL（既存テスト regression なし）

## Findings

なし

## Summary

横断タイムラインのカード UI 化が SPEC §5.1 / 要件定義のすべての numeric ID に対して観測可能な実装またはテストで裏打ちされている。Mapper / ViewModel に JVM 単体テストが追加されており、Compose 描画側の視覚検証は instrumented 領分として明示的にスコープ外（CLAUDE.md「JVM 単体テストを最優先」方針に整合）。`core/ui/ArticleCard` の拡張は既定値による後方互換で、shell 結線は #36 / #37 への暫定 no-op に留まり境界逸脱なし。

RESULT: approve
