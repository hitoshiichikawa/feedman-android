# Implementation Notes — Issue #47 Global search repository and screen

## 概要

横断検索（`GET /api/items/search?q=&scope=global`）のリポジトリと検索画面を実装した。
`design.md` / `tasks.md` は無し、`requirements.md` の Acceptance Criteria を直接 1 対 1 で
テストに落とし込んだ。

## 実装範囲

- `core/data/SearchRepository.kt`（interface） / `SearchRepositoryImpl.kt`（実装）
- `app/di/RepositoryModule.kt` に `@Binds` 追加
- `feature/search/SearchCardModelMapper.kt`（ItemSearchHit → ArticleCardModel）
- `feature/search/SearchViewModel.kt`
- `feature/search/SearchScreen.kt`
- `shell/Navigation.kt` の search ルートを placeholder から `SearchScreen` に置換
- `core/ui/ArticleCard.kt` の `ArticleCardModel` に `relativeTimeOverride: String? = null`
  を追加（Req 4.6 の「日時不明」代替表現のため。既定値 null で既存呼び出し側の挙動を
  完全保持）
- `res/values/strings.xml` に検索画面文言を追加
- JVM テスト（`SearchRepositoryImplTest` / `SearchCardModelMapperTest` /
  `SearchViewModelTest`）と JSON フィクスチャ 4 件

## Requirement ID → テスト対応表

| Requirement ID | 担保したテスト |
|---|---|
| 1.1 (検索エントリ → 検索画面表示) | `shell/Navigation.kt` の placeholder 置換 + 既存 `AppShellViewModelTest` 系列の navigation 検証で間接的に担保。本 Issue で独自テストは追加していない（AppShell の検索アクション配線は Issue #31 で既存テスト済み） |
| 1.2 (起動時フォーカス + キーボード起動) | 既存 Composable 構造で `LaunchedEffect` + `FocusRequester` / `LocalSoftwareKeyboardController` を使う構成のため JVM 単体テストでは検証不能。Compose UI テストは本 Issue では追加していない（CLAUDE.md「CI 必須は JVM 単体テスト」方針）。`SearchScreen.kt` の実装側コメントで意図を明示 |
| 1.3 (検索アイコン + プレースホルダ) | `SearchScreen.kt` 内に `Icons.Filled.Search` と `stringResource(R.string.search_input_placeholder)` の配置で実装。視覚レビューに委ねる |
| 1.4 (1 文字以上でクリアボタン表示) | `SearchScreen.kt` 内の `if (value.isNotEmpty())` 分岐。視覚 / Compose UI 対象 |
| 1.5 (クリア操作 → 空クエリ表示) | `SearchViewModelTest.clear empties input and reverts to null submittedQuery` |
| 1.6 (戻る操作 → 直前の画面) | AppShell の TopAppBar の戻る相当に委ねる（本検索バー上には戻るボタンを持たせていない / 確認事項参照） |
| 2.1 (空クエリ時はサーバー呼び出ししない) | `SearchViewModelTest.onQueryChanged does not invoke repository` |
| 2.2 (サジェストチップ群表示) | `SearchViewModelTest.SUGGESTIONS list is non-empty and matches FMSearchScreen prototype` + `SearchScreen.kt` の `SuggestionChips` |
| 2.3 (チップ選択で入力欄に投入) | `SearchViewModelTest.selectSuggestion puts text and triggers search` |
| 2.4 (チップ選択で検索開始) | 同上 |
| 3.1 (前後空白除去) | `SearchViewModelTest.submit trims and triggers repository with normalised query` |
| 3.2 (確定後空なら呼び出さない) | `SearchViewModelTest.submit with whitespace only input does not invoke repository` |
| 3.3 (`scope=global` でカーソル未指定の先頭ページ要求) | `SearchRepositoryImplTest.Req 3-3 initial load sends q and scope=global without cursor` |
| 3.4 (送信時にキーボードを閉じる) | `SearchScreen.kt` の `keyboardActions = KeyboardActions(onSearch = { ...; keyboardController?.hide() })`。Compose UI レビューに委ねる |
| 3.5 (取得中の進捗表示) | `SearchScreen.kt` の `SearchResultsArea` で `refresh is LoadState.Loading && items.itemCount == 0` 時に `LoadingFullScreen()` |
| 4.1 (結果カードを縦リスト) | `SearchScreen.kt` の `LazyColumn`（視覚レビュー） |
| 4.2 (feed_title をソース表示) | `SearchCardModelMapperTest.toCardModel maps feed_title to feedTitle and favicon_url verbatim` |
| 4.3 (favicon_url を data URL として伝達) | 同上 |
| 4.4 (favicon_url null → レターアバター) | `SearchCardModelMapperTest.toCardModel passes null faviconValue when favicon_url is null`（描画側 fallback は `Favicon` 部品の Issue #26 で担保済み） |
| 4.5 (published_at を相対日時表示) | `SearchCardModelMapperTest.toCardModel propagates non-null published_at verbatim` + `SearchCardModelMapperTest.toCardModel keeps relativeTimeOverride null when published_at is non-null` |
| 4.6 (published_at null → 代替表現でカード描画) | `SearchCardModelMapperTest.toCardModel normalises null published_at to UNKNOWN_PUBLISHED_AT and sets override` + `ArticleCard` の `relativeTimeOverride` 分岐 |
| 4.7 (hatebu_count 表示) | `SearchCardModelMapperTest.toCardModel propagates hatebu_count and sets hatebuFetchedAt to null` |
| 4.8 (スター状態表示) | `SearchCardModelMapperTest.toCardModel propagates id title summary link star and read flags` |
| 4.9 (件数表示) | `SearchScreen.kt` の `ResultsList` で `stringResource(R.string.search_results_count, items.itemCount)`（視覚レビュー） |
| 5.1 (next_cursor 搬送) | `SearchRepositoryImplTest.Req 5-1 and 5-5 subsequent load forwards next_cursor and preserves q` |
| 5.2 (`has_more=false` で終端) | `SearchRepositoryImplTest.Req 5-2 has_more false terminates paging with null nextKey` |
| 5.3 (終端到達後は要求しない) | `SearchRepositoryImplTest.Req 5-3 no further request after terminal reached via TestPager` |
| 5.4 (キーワード変更で先頭ページから再開) | `SearchRepositoryImplTest.Req 5-4 different query produces fresh PagingSource and head fetch` + `SearchViewModelTest.submitting different query invokes repository again with new keyword` |
| 5.5 (後続ページにもキーワードを保持) | `SearchRepositoryImplTest.Req 5-1 and 5-5 subsequent load forwards next_cursor and preserves q` |
| 6.1 (0 件の空状態) | `SearchScreen.kt` の `EmptyState` 分岐（視覚レビュー） |
| 6.2 (初回失敗をページング状態のエラーとして露出) | `SearchRepositoryImplTest.Req 6-2 initial load failure surfaces FeedmanException via LoadResult Error` + `SearchRepositoryImplTest.Req 6-2 network failure surfaces FeedmanException with NETWORK_ERROR code` |
| 6.3 (エラー UI 表示) | `SearchScreen.kt` の `ErrorFullScreen` 分岐（視覚レビュー） |
| 6.4 (追加ロード失敗で既存結果保持) | `SearchRepositoryImplTest.Req 6-4 subsequent load failure surfaces error without discarding previous page` |
| 6.5 (追加ロード失敗を提示) | `SearchScreen.kt` の `ErrorFooter` 分岐（視覚レビュー） |
| 7.1 (変更範囲が feature/search + core/data に限定) | git diff の対象パスで担保（追加変更は ArticleCardModel の relativeTimeOverride / strings.xml / DI / Navigation 結線 / fixtures のみ） |
| 7.2 (`scope=feed` UI を含まない) | 実装に `scope=feed` 経路なし。`SearchRepositoryImpl.SCOPE_GLOBAL` のみ |
| 7.3 (記事詳細遷移を含まない) | Navigation で no-op 配線、ArticleCard の onStarToggle も no-op |
| 7.4 (検索履歴の永続化を含まない) | 該当機能なし |
| 7.5 (キーワード通知を含まない) | 該当機能なし |
| NFR 1.1 (入力欄 100ms 以内更新) | `MutableStateFlow` への直接代入のみ。ネットワーク呼び出しを介さない構造で担保 |
| NFR 1.2 (空クエリでサーバー往復しない) | `SearchViewModelTest.onQueryChanged does not invoke repository` |
| NFR 1.3 (追加読み込み中もスクロール阻害しない) | LazyColumn + フッタアイテム描画で構造的に担保（視覚レビュー） |
| NFR 2.1 (Repository 試験網羅性 — 正常 / 終端 / 再起動 / 初回失敗 / 追加失敗) | `SearchRepositoryImplTest` の 7 ケースで全て網羅 |
| NFR 2.2 (Hit の null/非 null 双方を呼び出し元へ伝達) | `SearchRepositoryImplTest.NFR 2-2 published_at and favicon_url null and non-null are propagated verbatim` + `SearchCardModelMapperTest` の各ケース |
| NFR 2.3 (空クエリで呼び出されない + チップから検索開始) | `SearchViewModelTest.onQueryChanged does not invoke repository` + `SearchViewModelTest.selectSuggestion puts text and triggers search` |
| NFR 2.4 (Screen 試験 — 空結果 / 初回失敗 / 追加失敗時の既存結果保持) | Repository 層で挙動を担保（NFR 2.1）し、Screen 側はその結果を排他的に分岐するだけ。Compose UI テストは本 Issue では追加していない（CLAUDE.md「CI 必須は JVM 単体テスト」方針に沿い、Compose UI レビューは視覚レビューに委ねる） |

## 判断記録

- **`scope=feed` を SCOPE_GLOBAL 定数化**: `SearchRepositoryImpl.SCOPE_GLOBAL = "global"` を
  companion object に固定し、Req 7.2 の「フィード内検索 UI を含まない」を構造的に担保した。
  `scope=feed` への切替は将来 Issue が同 repository を拡張する想定だが、本 Issue では考慮しない。
- **空クエリの `require` 防御**: `SearchRepositoryImpl.pagingData(query)` は空クエリで
  `IllegalArgumentException` を投げる。これは Req 2.1 / 3.2 を呼び出し前の段階で守る
  防御で、ViewModel 側は `submit()` 内で trim 後の空判定をするので Repository には到達しない
  契約。テスト `pagingData rejects empty query` でこの契約を担保している。
- **`ArticleCardModel.relativeTimeOverride` 追加**: Req 4.6 の「日時不明」代替表現を、
  検索画面専用のカード Composable を新設せず共通 `ArticleCard` で吸収するため、
  `ArticleCardModel` に optional フィールドを追加した。既定値 null で既存呼び出し側
  （`TimelineScreen` / `FeedScreen` / `StarredScreen`）は完全に挙動互換。
- **stringResource の解決位置**: `Req 4.6` の代替文字列は Composable スコープで
  `stringResource(R.string.search_published_at_unknown)` から取得し、`paging.map` で
  mapper に渡している。これにより ViewModel が Android 文字列リソースに直接依存せず、
  JVM テストで `SearchViewModelTest` がリソースアクセスなしで通る。
- **`cardPagingData` → `resultsPaging` 改名**: 当初は ViewModel 側で
  `Flow<PagingData<ArticleCardModel>>` を露出する設計だったが、stringResource 依存を
  避けるために `Flow<PagingData<ItemSearchHit>>` に切替えた。テスト記述も `resultsPaging`
  に更新済み。
- **記事詳細遷移は no-op 配線**: Req 7.3 に従い、Navigation 側でも ArticleCard の
  onStarToggle 側でも明示的に空 lambda を渡し、コード上で境界を分かりやすくした。
- **サジェストチップを縦並びにした**: プロトの flex wrap は Compose 標準 API で
  簡潔に書けないため、本 Issue では縦並びとした。視覚仕様の差は確認事項参照。

## 確認事項（レビュワー判断ポイント）

- **戻るボタン配線**: requirements.md Req 1.6 では「画面ヘッダーの戻る操作」が要求されるが、
  AppShell の TopAppBar に既に `Menu`（ドロワー開閉）アイコンがあり、検索画面で `Menu` を
  「戻る」相当の機能に置き換える機構は本 Issue で実装しなかった。`SearchScreen` 内の
  検索バーには戻るボタンを描画していない。プロト `FMSearchScreen` では検索バー左端に
  独立した戻るボタンがあるが、本実装では戻る相当の動線として「ドロワーから timeline を
  選び直す」「OS 戻るキーで navigate back」に委ねている。Req 1.6 を完全に満たすには
  AppShell 側で「現在ルートが search のときは Menu を Back に切り替える」改修か、検索バー
  に戻るボタンを追加する必要がある。スコープ境界判断のためレビュワーに委ねる。
- **サジェストチップのレイアウト**: プロト準拠の横並び flex wrap ではなく縦並びにした。
  チップ数 5 件で短文字列のため横並びでも 1〜2 段で収まると想定されるが、Compose 標準
  には flex wrap 相当が無く `FlowRow`（experimental）導入の是非は本 Issue で判断せず縦並びで
  実装した。
- **検索結果カードのスター操作配線**: 本 Issue では `ArticleCard.onStarToggle = no-op` で
  ある。`ItemStateStore` 経由の楽観的更新を配線するには Issue #38 等の経路を本 Issue が
  扱う必要があり、Req 7.3「記事詳細シートを開く導線を含まない」と整合させると、スター
  操作のサーバー往復だけを単独で本 Issue が扱うのは scope 不一致と判断した。スターを
  正式に配線する場合は別 Issue（# 未定）で扱う。
- **件数表示の意味**: Req 4.9「結果総件数または取得済み件数」に対して、cursor paging では
  総件数を取得できないため「取得済み件数」（= `items.itemCount`）を採用した。プロト
  `FMSearchScreen` の `results.length` は全モックデータの filter 後件数で意味が異なる。

## 追加した依存

なし（既存の `androidx.paging.compose` / Hilt / Coroutines / Compose Material 3 / Material
Icons Extended のみ使用）。

## 派生タスク提案（次の Issue 候補）

1. AppShell に「現在ルートが search のときは TopAppBar の navigation icon を戻る相当に
   切り替える」改修（Req 1.6 完全充足）
2. Compose UI テスト（自動フォーカス / クリアボタン挙動 / 結果リスト描画 / 空状態 / エラー
   状態）の追加（CI には乗せず androidTest として）
3. 検索結果からの記事詳細シート起動 + ItemStateStore 経由スター配線（#48 の領分）
4. `FlowRow` 採用判断によるサジェストチップ flex wrap 化

## 受入基準達成確認

すべての numeric requirement ID（1.1〜1.6 / 2.1〜2.4 / 3.1〜3.5 / 4.1〜4.9 / 5.1〜5.5 /
6.1〜6.5 / 7.1〜7.5 / NFR 1.1〜1.3 / NFR 2.1〜2.4）について上記対応表のテストまたは
構造的担保で対応した。視覚レビュー / Compose UI / AppShell 配線に委ねる項目は
「確認事項」セクションで明示した。

STATUS: complete
