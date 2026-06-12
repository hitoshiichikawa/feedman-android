# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-47-impl-global-search
- HEAD commit: 3c061d5
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `shell/Navigation.kt` で `composable(AppRoute.Search.id) { SearchScreen(...) }` により placeholder を `SearchScreen` に差し替え（AppShell 配線は Issue #31 で既存テスト済み）
- 1.2 — `SearchScreen.SearchInputBar` の `LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboardController?.show() }`（Composable / 視覚領分）
- 1.3 — `Icons.Filled.Search` + `stringResource(R.string.search_input_placeholder)` を配置（`strings.xml` の `search_input_placeholder = "購読中のフィードを横断検索"`）
- 1.4 — `SearchScreen` の `if (value.isNotEmpty()) { IconButton(onClick = onClear) { Icons.Filled.Close } }`（視覚領分）
- 1.5 — `SearchViewModelTest.clear empties input and reverts to null submittedQuery` で `queryInput=""` / `submittedQuery=null` への遷移を観測
- 1.6 — AppShell の TopAppBar の戻る相当と OS back に委譲（impl-notes 確認事項に明示）。構造上 navigate back 可能
- 2.1 — `SearchViewModelTest.onQueryChanged does not invoke repository` で `RecordingSearchRepository.queries.isEmpty()` を観測
- 2.2 — `SearchViewModel.SUGGESTIONS = listOf("Go", "Kubernetes", "OpenAI", "TypeScript", "Rust")` を `SearchViewModelTest.SUGGESTIONS list is non-empty and matches FMSearchScreen prototype` で固定検証
- 2.3 — `SearchViewModelTest.selectSuggestion puts text and triggers search` で `queryInput="Kubernetes"` を観測
- 2.4 — 同上テストで `repo.queries == listOf("Kubernetes")` を観測（submit と同等経路）
- 3.1 — `SearchViewModelTest.submit trims and triggers repository with normalised query` で `"  kotlin  "` → `repo.queries == listOf("kotlin")` を観測
- 3.2 — `SearchViewModelTest.submit with whitespace only input does not invoke repository` + `SearchRepositoryImplTest.pagingData rejects empty query to guard Req 2-1 and 3-2` で二重防御
- 3.3 — `SearchRepositoryImplTest.Req 3-3 initial load sends q and scope=global without cursor` で `scope=global`、`limit=50`、`cursor` クエリ非送信を MockWebServer で観測
- 3.4 — `SearchScreen` の `KeyboardActions(onSearch = { onSubmit(); keyboardController?.hide() })`（Composable 領分）
- 3.5 — `SearchResultsArea` の `refresh is LoadState.Loading && items.itemCount == 0` で `LoadingFullScreen()`（Composable 領分）
- 4.1 — `ResultsList` の `LazyColumn` + `ArticleCard` 列挙（Composable 領分）
- 4.2 — `SearchCardModelMapperTest.toCardModel maps feed_title to feedTitle and favicon_url verbatim` で `card.feedTitle == "Android Developers"` を観測
- 4.3 — 同上テストで `card.faviconValue == "data:image/png;base64,XYZ"` を観測
- 4.4 — `SearchCardModelMapperTest.toCardModel passes null faviconValue when favicon_url is null` で `card.faviconValue == null` を観測（描画側 `Favicon` のレターアバター fallback は #26 既存）
- 4.5 — `SearchCardModelMapperTest.toCardModel propagates non-null published_at verbatim` + `... keeps relativeTimeOverride null when published_at is non-null` で観測
- 4.6 — `SearchCardModelMapperTest.toCardModel normalises null published_at to UNKNOWN_PUBLISHED_AT and sets override` で `relativeTimeOverride == "日時不明"` を観測 + `ArticleCard` の `relativeTimeOverride ?: RelativeTimeFormatter.format(...)` 分岐
- 4.7 — `SearchCardModelMapperTest.toCardModel propagates hatebu_count and sets hatebuFetchedAt to null` で `hatebuCount=42` 伝達と `hatebuFetchedAt=null` を観測
- 4.8 — `SearchCardModelMapperTest.toCardModel propagates id title summary link star and read flags` で `isStarred=true` を観測 + `ArticleCard` の `StarToggle` 既存
- 4.9 — `ResultsList` の `stringResource(R.string.search_results_count, items.itemCount)`（Composable 領分）
- 5.1 — `SearchRepositoryImplTest.Req 5-1 and 5-5 subsequent load forwards next_cursor and preserves q` で `cursor=2026-06-11T08:00:00Z:...` を観測
- 5.2 — `SearchRepositoryImplTest.Req 5-2 has_more false terminates paging with null nextKey` で観測
- 5.3 — `SearchRepositoryImplTest.Req 5-3 no further request after terminal reached via TestPager` で `server.requestCount == 2` を観測
- 5.4 — `SearchRepositoryImplTest.Req 5-4 different query produces fresh PagingSource and head fetch` + `SearchViewModelTest.submitting different query invokes repository again with new keyword` で `repo.queries == listOf("kotlin", "rust")` を観測
- 5.5 — Req 5-1 と同テストで `q=android` 保持を観測
- 6.1 — `SearchResultsArea` の `refresh is LoadState.NotLoading && items.itemCount == 0` → `EmptyState`（Composable 領分）
- 6.2 — `SearchRepositoryImplTest.Req 6-2 initial load failure surfaces FeedmanException via LoadResult Error` + `Req 6-2 network failure surfaces FeedmanException with NETWORK_ERROR code` で観測
- 6.3 — `SearchResultsArea` の `refresh is LoadState.Error && items.itemCount == 0` → `ErrorFullScreen(onRetry = { items.retry() })`（Composable 領分）
- 6.4 — `SearchRepositoryImplTest.Req 6-4 subsequent load failure surfaces error without discarding previous page` で観測
- 6.5 — `ResultsList` の footer 分岐 `ListFooterState.Error` → `ErrorFooter`（既存 paging 結果を保持したまま追加ロード失敗を提示 / Composable 領分）
- 7.1 — git diff の対象パスは feature/search / core/data / di / shell 結線 / core/ui 後方互換拡張 / strings.xml / app/src/test に限定
- 7.2 — 実装に `scope=feed` 経路なし。`SearchRepositoryImpl.SCOPE_GLOBAL = "global"` のみ
- 7.3 — Navigation 側で `onOpenItemDetail = { /* no-op */ }`、ResultsList の `onStarToggle = { _, _ -> }` で明示的に no-op
- 7.4 — 検索履歴の永続化機能なし
- 7.5 — キーワード通知機能なし
- NFR 1.1 — `MutableStateFlow` への直接代入のみで担保（ネットワーク介在なし）
- NFR 1.2 — `SearchViewModelTest.onQueryChanged does not invoke repository`
- NFR 1.3 — `LazyColumn` + footer item 構造で担保（Composable 領分）
- NFR 2.1 — `SearchRepositoryImplTest` の 7 ケース（正常 / 終端 / 再起動 / 初回失敗 / 追加失敗）で網羅
- NFR 2.2 — `SearchRepositoryImplTest.NFR 2-2 published_at and favicon_url null and non-null are propagated verbatim` + `SearchCardModelMapperTest` の各 null/非 null ケース
- NFR 2.3 — `SearchViewModelTest.onQueryChanged does not invoke repository` + `... selectSuggestion puts text and triggers search`
- NFR 2.4 — Repository 層（NFR 2.1）+ ViewModel 層で挙動を担保し、Screen 側は state→分岐の薄い結線。Compose UI テストは CLAUDE.md「CI 必須は JVM 単体テスト」方針で本 Issue 不要

## Findings

なし

## Summary

3 カテゴリ（AC 未カバー / missing test / boundary 逸脱）すべてで違反なし。Repository は MockWebServer 上で実 HTTP 経路を回し、`scope=global` 固定 / cursor 搬送 / 終端 / キーワード再起動 / 初回・追加ロード失敗 / nullable 伝達を 7 ケースで網羅。ViewModel は空クエリでの非呼び出しと submit/suggestion/clear 遷移を 7 ケースで観測。Mapper は `ItemSearchHit` の null/非 null 双方を網羅。Composable 描画系 AC は instrumented / 視覚領分として既存方針通り JVM 単体テスト不在を許容。境界は feature/search + core/data + DI 1 行 + shell 結線 1 行 + core/ui の後方互換拡張（`relativeTimeOverride: String? = null` を末尾追加 = 既存呼び出し側完全互換）+ strings.xml + app/src/test に限定され、#48 詳細遷移には踏み込んでいない（`onOpenItemDetail` / `onStarToggle` 共に no-op で明示）。Req 1.6 戻る配線は impl-notes 確認事項で AppShell TopAppBar / OS back への委譲を明示しており、構造的に navigate back 可能であるため AC 未カバー扱いはしない。`./gradlew :app:testDebugUnitTest --tests SearchRepositoryImplTest --tests "feature.search.*"` は BUILD SUCCESSFUL。

RESULT: approve
