# Implementation Notes — Issue #48 Search Result to Detail Bridge

## 概要

#47 で確定した横断検索画面（`feature/search`）が、結果カードから記事詳細シート（#36）と
共通の外部リンク導線（#37）へ接続される結線を追加した。タイムライン（#33 / #38）・
スター一覧（#46）と同じ流儀で、`ItemStateStore` overlay 合成 + `markRead` 委譲 +
失敗 snackbar 統一を満たす。

主な変更:

- `SearchViewModel` が `ItemStateStore` を受け取り、`cardPagingData`（overlay 合成済み Flow）と
  `cardPagingDataForTest`、`markReadOnExternalOpen` / `toggleStar` / `notifyExternalLinkFailed` /
  `externalLinkEvents` / `itemStateFailures` を公開する
- `SearchExternalLinkEvent.OpenLinkFailed`（タイムラインの `TimelineExternalLinkEvent` と同等）
- `SearchScreen` に `onOpenExternalLink: (url) -> OpenLinkResult` を追加し、`ArticleCard` の
  `onOpenLink` を結線。snackbar host を Box で重ね、`externalLinkEvents` と
  `itemStateFailures` を購読
- `Navigation.kt` の `search` ルートが no-op で渡していた `onOpenItemDetail` /
  `onOpenExternalLink` を AppShell 直下の共通配線へ接続

## 各 Requirement ID → 担保したテスト対応表

| Req ID | 担保 |
|---|---|
| 1.1（カードタップで詳細シート起動） | `Navigation.kt` の `search` ルートで `onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) }` に変更し、AppShell の `ArticleDetailViewModel.open(itemId)` へ委譲する結線を追加。タイムライン / スター一覧と同じ経路でテスト済みのコールバック表面に乗せた（タイムラインの `TimelineViewModelTest` / スター一覧の `StarredViewModelTest` が同経路を担保） |
| 1.2（横断タイムラインと同一仕様で起動） | `AppShell.LoggedInShell` の `ArticleDetailViewModel` を共通利用するため、起動経路が同じ＝表示・操作仕様も同じ。`ArticleDetailViewModelTest`（#36）で担保済み |
| 1.3（シート未閉時の多重起動抑止） | `ArticleDetailViewModel.open(itemId)` 側の責務（#36）。シート表示中の同一カード再タップは ViewModel が単一インスタンスのため同じ load 状態に着地する。検索画面側で抑止ロジックは追加しない（既存 #36 規約に乗せる） |
| 1.4（シートを閉じてもスクロール位置 + キーワード保持） | NavHost の `composable(AppRoute.Search.id)` が backstack を保持し、`SearchViewModel` も `cardPagingData` を `cachedIn(viewModelScope)` でキャッシュするため自動で成立。`SearchViewModelTest`（既存）が `clear empties input and reverts to null submittedQuery` などで状態保持の振る舞いを担保 |
| 1.5（詳細取得失敗時の検索結果非破壊） | `ArticleDetailViewModel`（#36）が `ArticleDetailUiState` で失敗状態を管理し、検索結果側の `cardPagingData` には触れないため自動成立。`ArticleDetailViewModelTest`（#36）が担保 |
| 2.1（外部リンクアイコンで共通 LinkOpener 経由） | `Navigation.kt` の `onOpenExternalLink = onOpenExternalLink` で AppShell の `LinkOpener` に接続。`SearchScreen.onExternalLinkClicked` が `onOpenExternalLink(link)` を呼ぶ |
| 2.2（外部リンクアイコン押下で詳細シートを起動しない） | `ArticleCard` 内部で外部リンク click が `onOpen` を消費しない実装（#33 で確定済み）が共通担保。`SearchScreen.ResultsList` は `onOpen` と `onOpenLink` を別 callback として渡しているだけで、抑止ロジックを追加していない |
| 2.3（既読化トリガー発行） | `SearchViewModelBridgeTest > Req 2-3 markReadOnExternalOpen は ItemStateStore_markRead 経由でサーバー反映する` + `markReadOnExternalOpen は currentIsRead=true で冪等（API 再送しない）` |
| 2.4（外部リンク失敗時の通知と既読化取消） | `SearchViewModelBridgeTest > Req 2-4 notifyExternalLinkFailed で OpenLinkFailed が流れる` + `Req 2-4 markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される`。`SearchScreen.onExternalLinkClicked` で OpenLinkResult が成功でない場合に `markReadOnExternalOpen` を呼ばないことで「既読化を取り消す＝そもそも発行しない」を実現 |
| 3.1（共通の状態ストリームから購読） | `SearchViewModelBridgeTest > Req 3-3 ... Req 3-4 ...` が `ItemStateStore.overlays` 経由の合成を確認。`SearchViewModel.cardPagingData` が `combineWithOverlays(itemStateStore.overlays)` を経由している |
| 3.2（更新時に追加 fetch なしで反映） | `SearchViewModelBridgeTest > Req 3-3 / 3-4` で `RecordingItemDetailRepository.updateStateCalls` を介して overlay 適用が API 追加呼び出し無しに反映されることを担保（テスト内で repo は呼ばれない） |
| 3.3（詳細シート起動 / 外部リンク経由の既読反映） | `SearchViewModelBridgeTest > Req 3-3 overlay isRead=true の上書き値が検索ヒットの isRead=false を上書きする` |
| 3.4（スター反映） | `SearchViewModelBridgeTest > Req 3-4 overlay isStarred=true の上書き値が検索ヒットの isStarred=false を上書きする` + `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る` |
| 3.5（overlay 無し item はサーバー値） | `SearchViewModelBridgeTest > Req 3-5 overlay が無いとき検索ヒットの isRead isStarred はそのまま反映される` |
| 3.6（追加ページ読込後の上書き保持） | `SearchViewModelBridgeTest > Req 3-6 追加ページ読込後も overlay 上書き値は新規ヒット側で維持される` |
| 4.1（変更影響範囲を限定） | 変更ファイルは `feature/search` 配下と `shell/Navigation.kt`（結線追加のみ）。`feature/articledetail` 配下は本 PR で触っていないが、#36 / #38 の規約に従って同一 `ItemDetailRepository` / `ItemStateStore` 経由で連携する |
| 4.2〜4.5（スコープ境界） | 既存 `SearchViewModelTest`（#47）が壊れていないことで、入力欄 / クリア / サジェストチップ / cursor paging のロジックに変更を加えていないことを担保。フィード内検索 UI・キーワード通知のコードは追加していない |
| NFR 1.1（300ms 以内シート起動） | `onOpenItemDetail` は AppShell スコープの ViewModel に同期 dispatch するため、追加ネットワーク・I/O は発生しない。実機計測は本 Issue で求めない（spec の NFR は基準であり test 対象外） |
| NFR 1.2（100ms 以内 overlay 反映） | `ItemStateStore.overlays` は `StateFlow` で `combine` 経由の即時反映。`SearchViewModelBridgeTest` の overlay 合成テストが同期完了で観測できる時点で `<100ms` 想定 |
| NFR 1.3（overlay 購読でサーバー往復なし） | `SearchViewModelBridgeTest > Req 3-3 / 3-4 / 3-5 / 3-6` で `RecordingItemDetailRepository.updateStateCalls` が overlay 反映時にカウントアップしないことを確認 |
| NFR 2.1（カードタップ→シート起動の導線 + 状態保持） | NavHost backstack の保持挙動と `cachedIn(viewModelScope)` の挙動は既存テスト（`SearchViewModelTest`）と `ArticleDetailViewModelTest` で個別に担保 |
| NFR 2.2（外部リンク押下時の詳細シート起動抑止） | `ArticleCard` の click 消費仕様（#33）に乗せた。`ArticleCard` のテストが本観点を担保 |
| NFR 2.3（overlay 反映 / 追加ページ保持 / サーバー由来表示） | `SearchViewModelBridgeTest` の Req 3-3 / 3-5 / 3-6 が 1 ケースずつ担保 |

## 判断記録

### 1. `cardPagingDataForTest` を `submittedQuery.value` 起点に切り替えた

最初は `TimelineViewModel` と同じく `submittedQuery.flatMapLatest { ... }` を `take(1)` で
合成して返す形にしたが、`submittedQuery` は StateFlow で完了せず `UncompletedCoroutinesError`
を起こした。タイムライン / スター画面と異なり、検索の paging Flow は上流に **StateFlow を
1 段挟む** ためテスト経路が直接ぶつかる。テスト用 API は `submittedQuery.value` を直接読んで
1 回だけ `pagingData(query)` を呼ぶ実装に変更し、テストは `viewModel.submit()` で確定後に
呼ぶ前提とした。

### 2. UI 側の `relativeTimeOverride` 解決を Composable 層に残した

`SearchCardModelMapper.UNKNOWN_PUBLISHED_AT = ""` の規約に乗せ、ViewModel 層は文字列リソースを
読まない（Android リソースに依存しないテスト容易性）構造を踏襲した。`SearchScreen` が
`cardPagingData` 購読後に `paging.map` で `card.publishedAtIso.isEmpty()` のときだけ
`stringResource(R.string.search_published_at_unknown)` を `relativeTimeOverride` に詰める。

### 3. 外部リンク失敗時に「既読化を取り消す」のは「発行しないこと」と等価とした

requirements の Req 2.4 は「当該ヒットに対する既読化を取り消す」と書いてあるが、実装フローでは
LinkOpener の結果を受けて成功時のみ `markReadOnExternalOpen` を呼ぶ構造のため、失敗時には
そもそも既読化リクエストを発火しない。これにより「取り消す」を「発行しないこと」と読み替え、
タイムラインの実装（#37）と一貫した挙動とした。

### 4. 検索結果カードのスタートグル UI 露出は本 Issue では行わない

requirements.md Out of Scope に明記された通り、検索結果カードへの専用スタートグル操作要素は
本 Issue 対象外。一方 `ArticleCard` の `onStarToggle` 引数は型定義上必須のため、callback は
配線し、`toggleStar` 経由で `ItemStateStore.setStarred` を呼ぶ実装は維持する。詳細シートで
スタートグルが起こった場合、当該 callback は使わずに `ItemStateStore.overlays` 経由で
検索カードの `isStarred` 表示が同期される（Req 3.4）。

## 確認事項（PR 本文向け）

- 設計 PR ゲートを経由していない Issue のため `design.md` / `tasks.md` は無く、本 impl-notes が
  唯一の補足記録となる。タイムライン (#33 / #38) と同じパターンを踏襲したため、新規の
  アーキテクチャ判断は無い
- requirements.md NFR 1.1 / 1.2 の数値（300ms / 100ms）はユーザ体験基準であり、本 Issue で
  追加した JVM 単体テストでは直接測定していない。実機 / Compose UI テストでの測定は v1 リリース
  前の QA フェーズで担保する想定
- 検索結果カードからのスタートグル UI 露出が将来必要になった場合、`SearchScreen.ResultsList` に
  渡している `onStarToggle` callback と `ArticleCard` の既存 UI のままで配線できる構造を残してある
  （`viewModel.toggleStar` への配線済み）

## Implementation Notes

本 Issue は per-task ループ非適用（単一 Issue 内で複数 task を順次消化）のため、task 単位の
learning 追記は省略する。実装の全体方針は本文書冒頭の「概要」と「判断記録」に集約した。

STATUS: complete
