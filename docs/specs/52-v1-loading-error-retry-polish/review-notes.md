# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-52-impl-loading-error-polish
- HEAD commit: 10cefb8
- Compared to: origin/main..HEAD
- 変更ファイル:
  - `app/src/main/kotlin/com/feedman/android/feature/subscriptionsettings/SubscriptionSettingsSheet.kt`
  - `app/src/main/kotlin/com/feedman/android/feature/subscriptionsettings/SubscriptionSettingsUiState.kt`
  - `app/src/main/kotlin/com/feedman/android/feature/subscriptionsettings/SubscriptionSettingsViewModel.kt`
  - `app/src/main/res/values/strings.xml`（`subscription_settings_not_found` 1 件追加）
  - `app/src/test/kotlin/com/feedman/android/feature/subscriptionsettings/SubscriptionSettingsViewModelTest.kt`
  - `docs/specs/52-v1-loading-error-retry-polish/{requirements,impl-notes}.md`

## Verified Requirements

本 Issue は「監査 + 是正」型のため、(a) 監査マトリクスの裏取り（既存実装が統一基準を満たすこと）と、
(b) 是正実装（購読設定シートの Loading / NotFound 追加）の両面で検証した。

### Req 1（初回ローディング統一）

- 1.1 / 1.2 / 1.3 / 1.4 — 一覧 4 画面（timeline / feed / starred / search）で `core/ui/StateViews.kt` の
  `LoadingFullScreen` / `LoadingFooter` を使用、`resolveTimelineScreenState` / `resolveListFooterState`
  により排他描画が ViewModel 側で確定（`core.ui.TimelineScreenStateTest` / `ListFooterStateTest` で
  網羅検証済み）。grep で 4 画面とも primitive 利用を確認。

### Req 2（エラー表示と再試行統一）

- 2.1 / 2.2 / 2.5 — 4 画面とも `ErrorFullScreen(onRetry = pagingItems.retry())` を経由。
- 2.3 / 2.4 — `ErrorFooter` がリスト末尾フッタ排他状態として `resolveListFooterState` から派生。
- 2.6 / 2.7 — `FeedmanException.errorMessage` 経由で API `message` を本文化、null 時は
  `state_error_default_message` を fallback（`core.network.FeedmanErrorMapper*Test` 既存検証）。

### Req 3（空状態統一）

- 3.1 / 3.2 / 3.3 — 4 画面とも `EmptyState` を使用、title を画面別文字列で供給。
- 3.4 — `SearchScreen` で `submittedQuery == null` 時に `SuggestionChips` のみ描画
  （既存 `SearchViewModelTest` カバー）。

### Req 4（終端表示統一）

- 4.1 / 4.2 / 4.3 — 全画面が `EndOfListFooter` 経由で `R.string.state_end_of_list` を共有。
  grep で `state_end_of_list` の本文参照が `StateViews.kt` 1 箇所のみであることを確認。

### Req 5（シートのローディング・エラー統一）— 本 Issue の是正対象

- 5.1 — articledetail（`SheetLoadingBody` / `CircularProgressIndicator`、既存）、
  **subscriptionsettings（本 Issue で `Loading(feedId)` 状態 + `SubscriptionSettingsLoadingBody` 追加）**、
  registerfeed（初回 fetch 動作なし）、account（`AccountSheetUserStatusLine.Loading`、既存）。
  → `SubscriptionSettingsViewModelTest#Issue 52 Req 5_1 open 直後で観測前は Loading を経由する`
  でテスト担保。
- 5.2 — articledetail（`SheetErrorBody` + `ErrorFullScreen`、既存）、
  **subscriptionsettings（本 Issue で `NotFound(feedId)` 状態 + `SubscriptionSettingsNotFoundBody`
  追加。`ErrorFullScreen` を再利用、`subscription_settings_not_found` 文言、閉じる + 再試行を露出）**、
  registerfeed（該当する初回 fetch なし）、account（`AccountSheetUserStatusLine.Error` + retry、既存）。
  → `Issue 52 Req 5_2 Repository が未存在 feedId を返したら NotFound に遷移する` でテスト担保。
  既存「Hidden のまま」テストは「NotFound に遷移」へ正しく更新済み。
- 5.3 — **subscriptionsettings（本 Issue で `retry()` 関数追加。NotFound / Loading / Visible いずれの
  feedId からも `open(feedId)` を再実行可能）**、他シートは既存の retry 機構。
  → `Issue 52 Req 5_3 NotFound から retry で再観測する` でテスト担保。
- 5.4 — articledetail（snackbar + シート保持、既存）、subscriptionsettings（`errorMessage` フィールド
  既存）、registerfeed（`OutlinedTextField.isError` 既存）、account（退会失敗ダイアログ → Idle 復帰、
  既存）。送信系の自動 close 抑止は本 Issue で破壊されていない。

### Req 6（フィード別 Pull-to-refresh クールダウン）

- 6.1 / 6.2 / 6.3 — `FeedScreenViewModel` の `FeedScreenEvent.FetchCooldown(retryAfterSeconds)` /
  `FetchFailed(message)` 経路 + `fetchInProgress` クリアで実装済み（`FeedScreenViewModelTest` 既存）。

### Req 7（API 契約・採用案の不変）

- 7.1 / 7.2 / 7.3 — 変更ファイルは subscriptionsettings 配下のみで、API 層 / 採用案 / v1 スコープ外
  機能の追加なし。

### NFR

- NFR 1.1 — 是正で追加した `SubscriptionSettingsLoadingBody` は Material 3 標準
  `CircularProgressIndicator`、`SubscriptionSettingsNotFoundBody` は `core/ui` の `ErrorFullScreen`
  を再利用。共通プリミティブ集約方針を維持。
- NFR 1.2 — `SubscriptionSettingsPlaceholderHeader` の閉じる IconButton で
  `MaterialTheme.feedmanDimens.minTapTarget` を採用しタップ標的 ≥44dp を維持。
- NFR 1.3 — `MaterialTheme.colorScheme.primary` / `onSurface` のみ使用で両テーマ対応。
- NFR 2.1 / 2.2 — `SubscriptionSettingsUiState` を `Hidden` / `Loading` / `NotFound` / `Visible` の
  排他 sealed interface に拡張、`when` 分岐で唯一の描画を選択（一意判定可能）。
- NFR 3.1 / 3.2 — 既存テスト全通過（`./gradlew :app:testDebugUnitTest --tests
  SubscriptionSettingsViewModelTest` で BUILD SUCCESSFUL を本レビュー中に再確認）。API 契約変更なし。

## Findings

なし。

監査内容に関する判断ポイント:

- impl-notes「確認事項 1〜4」（FeedScreen の subscription 未存在時 retry の実効性 / 検索の
  itemCount>0 + refresh エラー時の通知 / AccountSheet の Loading 配置 / timeline の空状態
  subtitle）は、いずれも Req 1〜5 の AC 文言の最低限の要件は満たしており（再試行ボタン露出 /
  シート内に進行中表示 / 主題テキスト等）、本 Issue のスコープ外の派生改善として扱う Developer
  判断は妥当。AC 違反ではないため reject 対象とはしない。
- 是正実装の `GateRepository` テストヘルパで `observeFeed` を override し「最初の null を流さず
  Loading を維持する」ことで Req 5.1 を検証している点も、ViewModel の挙動契約と整合（VM 側は
  initial を Loading に固定、その後の null で NotFound へ遷移）。

## Summary

監査マトリクスは Req 1〜6 / NFR 1〜3 を画面 × 状態の格子で網羅しており、4 一覧画面の状態統一は
既存実装で達成済み、是正の購読設定シート Loading / NotFound 追加で Req 5.1 / 5.2 / 5.3 の
dead-end 解消も完了。boundary は feature/subscriptionsettings / strings.xml / app/src/test に
閉じ、API 契約・採用案・v1 スコープへの侵食なし。是正箇所には Req 5.1 / 5.2 / 5.3 各々の単体
テストが追加され、既存テストは正しく更新されている。`./gradlew :app:testDebugUnitTest
--tests SubscriptionSettingsViewModelTest` は BUILD SUCCESSFUL。

RESULT: approve
