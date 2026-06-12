# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-38-impl-itemstatestore
- HEAD commit: bca59b2
- Compared to: origin/main..HEAD
- Changed files (10): core/data/ItemStateStore.kt, di/CoroutineScopeModule.kt,
  feature/articledetail/ArticleDetailViewModel.kt, feature/timeline/TimelineScreen.kt,
  feature/timeline/TimelineViewModel.kt, 各 ViewModel/Store の単体テスト 3 ファイル,
  docs/specs 配下 2 ファイル

## Verified Requirements

- 1.1 — `ItemStateStoreTest#setRead で overlay の isRead が即時 true になる_Req 1_1`、`setStarred で overlay の isStarred が即時トグルされ updateState を呼ぶ_Req 1_1_4_4`
- 1.2 — `ItemStateStoreTest#複数購読者が同じ overlay 更新を観測する_Req 4_1_4_2_NFR 2_2`（単一 `overlays: StateFlow<Map>` を購読する複数 collector が同じ更新を受領）
- 1.3 — `ItemStateStoreTest#既読とスターの overlay は同一 item で独立に保持される_Req 1_3`
- 1.4 — `ItemStateStoreTest#overlay 未設定 item は resolve helper でサーバー値をそのまま返す_Req 1_4_3_3`
- 2.1 — `ItemStateStoreTest#setStarred ... updateState を呼ぶ` / `setRead ...`（FakeRepo.updateStateCalls で確認）
- 2.2 — `ItemStateStoreTest#スター更新失敗で overlay を旧値に戻し failure イベントを流す_Req 2_2_2_3_2_5`、`既読更新失敗で...`
- 2.3 — `TimelineViewModelTest#markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される_Issue38 Req 2_3`、`ArticleDetailViewModelTest#既読化サーバー反映失敗で...MarkReadFailed`
- 2.4 — `ItemStateStoreTest#成功時は overlay を維持し追加のロールバックを行わない_Req 2_4`
- 2.5 — `ItemStateStoreTest#スター更新失敗で...`（collectedOverlays で「楽観値 true 観測 → ロールバック後 != true」の順序遷移を assert で観測）
- 3.1 — `ItemStateStoreTest#resolve は overlay 値をサーバー値より優先する_Req 3_1`、`TimelineViewModelTest#cardPagingData は ItemStateStore overlay 値をサーバー値より優先する_Issue38 Req 3_1`
- 3.2 — `ItemStateStoreTest#新しいサーバーページが来ても overlay は維持される_Req 3_2`
- 3.3 — `TimelineViewModelTest#overlay にない item はサーバー由来値をそのまま表示する_Issue38 Req 3_3`
- 3.4 — `ItemStateStoreTest#resolve は overlay 値とサーバー値が一致しても差分を生じさせない_Req 3_4`
- 4.1 — `ArticleDetailViewModelTest#他画面で store_setStarred されたとき詳細シートの isStarred が更新される_Issue38 Req 4_1_4_2`
- 4.2 — 同上（store singleton による単一ストリーム共有を、Detail VM の uiState 更新で観測）
- 4.3 — `ArticleDetailViewModel.uiState` が `combine(_rawState, itemStateStore.overlays)` で overlay 購読していることをテストで観測（同上）
- 4.4 — `TimelineViewModel.cardPagingData` が `cachedIn(viewModelScope).combineWithOverlays(itemStateStore.overlays)` 構成で overlay 購読、`TimelineViewModelTest#cardPagingData は ItemStateStore overlay 値を...` で合成結果を観測
- 5.1 — `ArticleDetailViewModelTest#open は Loading_を経由して Content へ遷移し isRead を true にする_Req 1_1_3_1`（`fetchAndApply` 内で `store.markRead` を発火）
- 5.2 — `TimelineViewModelTest#markReadOnExternalOpen で既読が立っていなければ ItemStateStore_markRead を呼ぶ_Req 2_2`、`ArticleDetailViewModelTest#markReadOnOpenExternal は未読 Content から既読化リクエストを発火する_Req 4_3`
- 5.3 — `ItemStateStoreTest#markRead は既に既読の item に対して API を再送しない_Req 5_3`、`TimelineViewModelTest#markReadOnExternalOpen は既読時には API を再送しない_Issue38 Req 5_3`、`ArticleDetailViewModelTest#既に既読の記事を open しても updateState を再送しない_Req 3_5`
- NFR 1.1 — `MutableStateFlow.update` による同期的 overlay 更新（`ItemStateStore.applyOverlay`）。`setRead` 後 `overlays.first()` で即時値が取れることをテストで観測
- NFR 1.2 — `setRead`/`setStarred` が overlay 更新後に `scope.launch { repository.updateState ... }` を発行し、戻り値を待たずに関数復帰
- NFR 2.1 — `overlays: StateFlow` / `failures: SharedFlow` を公開（ItemStateStore.kt 93/106 行）
- NFR 2.2 — `複数購読者が同じ overlay 更新を観測する` で 2 つの collector への配信を確認
- NFR 3.1 — 変更範囲が ItemStateStore 本体 + Timeline/ArticleDetail の購読接続変更に限定（git diff --name-only で確認）
- NFR 3.2 — スター一覧（feature/starred）/ 検索（feature/search）への変更なし

## Findings

なし。

## 追加観察

- **`cardPagingDataForTest` の test-only entry point について**: 本番経路 `cardPagingData`
  は `cachedIn(viewModelScope).combineWithOverlays(overlays)` で full StateFlow を購読し、
  overlay 更新で再 emit する。`cardPagingDataForTest` は `asSnapshot()` が `combine(stateFlow)`
  と相性が悪い（StateFlow が完了しないため `UncompletedCoroutinesError`）という Paging 3
  の制約に起因し、`overlays.take(1)` で 1 件で完了させる helper にとどまっている。
  本番経路の reactive 性は (i) `ItemStateStoreTest#複数購読者が同じ overlay 更新を観測する`、
  (ii) `ArticleDetailViewModelTest#他画面で store_setStarred されたとき...` の 2 経路で
  カバーされており、「テストを通すために実装ではなく公開 API を弱める」パターンには該当しない
  と判断する（assertion は弱めていない、production と test 用 helper でロジック本体
  `combineWithOverlays` を共有している、impl-notes で意図を明記）。
- **境界遵守**: スター一覧（#46）/ 横断検索（#48）の購読側コードには一切手を加えていない。
  Out of Scope に明記された通り、`store.failures` の購読がない画面で発火した失敗は
  snackbar が出ない可能性があるが、これは本 Issue の責務範囲外（impl-notes 「確認事項」で
  明示）。
- **長寿命 scope の妥当性**: `@ApplicationScope` で `SupervisorJob + Dispatchers.Default`
  を提供することで、ViewModel 寿命を超えて inflight サーバーリクエストが完走する設計。
  これは NFR 2 の「楽観値とサーバー状態の永久乖離回避」に整合する設計判断として妥当。

## Summary

ItemStateStore の追加・Timeline / ArticleDetail VM の購読切り替えともに、requirements.md の
全 numeric AC（Req 1.1〜5.3 + NFR 1〜3）に対応する実装とテストが揃っており、ロールバック
シーケンスの観測可能性（Req 2.5）も Turbine ベースで担保されている。変更範囲は本 Issue の
境界（core/data, di, feature/timeline, feature/articledetail, app/src/test, docs/specs）に
収まっており、#46/#48 の領分には踏み込んでいない。`./gradlew :app:testDebugUnitTest` の
対象 3 クラスは BUILD SUCCESSFUL。

RESULT: approve
