# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-48-impl-search-detail-bridge
- HEAD commit: 1e03c5d
- Compared to: origin/main..HEAD
- 変更ファイル:
  - `app/src/main/kotlin/com/feedman/android/feature/search/SearchExternalLinkEvent.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/feature/search/SearchScreen.kt`
  - `app/src/main/kotlin/com/feedman/android/feature/search/SearchViewModel.kt`
  - `app/src/main/kotlin/com/feedman/android/shell/Navigation.kt`（最小結線）
  - `app/src/test/kotlin/com/feedman/android/feature/search/SearchViewModelBridgeTest.kt`（新規）
  - `app/src/test/kotlin/com/feedman/android/feature/search/SearchViewModelTest.kt`
  - `docs/specs/48-search-result-to-detail-bridge/{requirements,impl-notes}.md`

## Verified Requirements

- 1.1 — `SearchScreen.ResultsList` の `ArticleCard.onOpen = { id -> onOpenItemDetail(id) }` ＋ `Navigation.kt` での `onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) }` 結線で AppShell 直下の `ArticleDetailViewModel.open(itemId)` に委譲（タイムライン / スター一覧と同経路）
- 1.2 — AppShell 直下の同一 `ArticleDetailViewModel` を共通利用するため、表示・操作仕様が同一になる結線。`ArticleDetailViewModelTest`（#36 既存）が担保
- 1.3 — `ArticleDetailViewModel.open(itemId)` 側の責務（#36）に委譲。検索画面側に追加抑止ロジック無し（適切な責務分離）
- 1.4 — NavHost backstack + `cardPagingData.cachedIn(viewModelScope)` でスクロール位置とキーワード保持。`submittedQuery` も VM scope で生存
- 1.5 — 詳細取得失敗は `ArticleDetailUiState` に閉じ、検索結果 paging 側へは影響しない（コード読み取り上問題なし）
- 2.1 — `SearchScreen.onExternalLinkClicked` が `onOpenExternalLink(link)` を呼び、`Navigation.kt` が AppShell の共通 `LinkOpener` に接続
- 2.2 — `ArticleCard` 内で `onOpenLink` click は `onOpen` と独立して扱われる（既存 #33 / #37 規約）。`ResultsList` も別 callback として渡しているのみ
- 2.3 — `markReadOnExternalOpen` を success 時のみ呼ぶ実装（`SearchScreen.kt:onExternalLinkClicked`）。テスト: `SearchViewModelBridgeTest > Req 2-3 markReadOnExternalOpen は ItemStateStore_markRead 経由でサーバー反映する` ＋ 冪等性テスト
- 2.4 — `OpenLinkResult.InvalidUrl` / `NoAppToHandle` で `notifyExternalLinkFailed()` を呼び、success でない限り `markReadOnExternalOpen` を呼ばない。テスト: `Req 2-4 notifyExternalLinkFailed で OpenLinkFailed が流れる` ＋ `markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される`
- 3.1 — `SearchViewModel.cardPagingData` が `combineWithOverlays(itemStateStore.overlays)` 経由で共通ストリームを購読
- 3.2 — `combine` 経由の overlay 反映で追加 fetch 不要。`SearchViewModelBridgeTest` で `RecordingItemDetailRepository.updateStateCalls` がカウントアップしないことを担保
- 3.3 — `Req 3-3 overlay isRead=true の上書き値が検索ヒットの isRead=false を上書きする`
- 3.4 — `Req 3-4 overlay isStarred=true の上書き値が検索ヒットの isStarred=false を上書きする` ＋ `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る`
- 3.5 — `Req 3-5 overlay が無いとき検索ヒットの isRead isStarred はそのまま反映される`
- 3.6 — `Req 3-6 追加ページ読込後も overlay 上書き値は新規ヒット側で維持される`
- 4.1 — 変更ファイルは `feature/search/*` ＋ `shell/Navigation.kt`（既存 callback への結線追加のみ）＋ `app/src/test/.../search/*` に限定。`feature/articledetail` 配下は本 PR で未改変
- 4.2 — `SearchViewModelTest`（#47 観点）に修正は ItemStateStore 注入のみで、入力欄 / クリア / サジェスト / cursor paging の挙動検証はそのまま green
- 4.3 — 記事詳細シート本体・LinkOpener 本体への変更なし
- 4.4 — フィード内検索 UI への変更なし
- 4.5 — キーワード通知関連の追加コードなし
- NFR 1.1 — `onOpenItemDetail` は AppShell ViewModel への同期 dispatch（追加 I/O 無し）
- NFR 1.2 — `ItemStateStore.overlays` は `StateFlow` + `combine` 即時反映
- NFR 1.3 — overlay 経路で `RecordingItemDetailRepository.updateStateCalls` がカウントアップしないことを `Req 3-3/3-4/3-5/3-6` で担保
- NFR 2.1 — `SearchViewModelTest`（既存）＋ `cachedIn` ＋ NavHost backstack の組合せで担保（カードタップ自体は callback 委譲のみで instrumented 領分）
- NFR 2.2 — `ArticleCard` の click 消費仕様（#33）に乗せた（既存 ArticleCard テストが担保）
- NFR 2.3 — `SearchViewModelBridgeTest` の Req 3-3 / 3-5 / 3-6 が 1 ケースずつ担保

## Test 実行確認

`./gradlew :app:testDebugUnitTest --tests "com.feedman.android.feature.search.SearchViewModelBridgeTest" --tests "com.feedman.android.feature.search.SearchViewModelTest" --rerun-tasks` を実行し BUILD SUCCESSFUL を確認した。

## Findings

なし

## Summary

Issue #48 の要件 1〜4 と NFR 1 / 2 のすべてが、`feature/search` 配下の結線追加と `shell/Navigation.kt` の最小配線変更で網羅されている。タイムライン / スター画面と同一の `ItemStateStore` overlay 合成パターンに揃えられ、テスト（`SearchViewModelBridgeTest` 8 ケース + 更新済み `SearchViewModelTest`）も green。境界（feature/search + shell 最小結線 + app/src/test）からの逸脱なし。

RESULT: approve
