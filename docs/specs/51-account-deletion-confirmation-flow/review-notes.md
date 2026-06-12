# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-51-impl-account-deletion
- HEAD commit: 40b4f8c
- Compared to: origin/main..HEAD

差分構成（6 commits）:

- `docs(spec): Issue #51 退会確認フローの要件定義を追加`
- `feat(data): UserRepository に deleteMe (DELETE /api/users/me) を追加`
- `feat(auth): AccountDeletionCoordinator を追加 (退会 + ローカル消去)`
- `feat(account): 退会フロー (二段確認 + 進行中 + 失敗) を ViewModel と UiState に追加`
- `feat(account): 退会導線ボタンと二段確認ダイアログの Compose UI を追加`
- `docs(spec): Issue #51 退会フロー実装ノート (impl-notes.md) を追加`

変更ファイル: `core/data/UserRepository(.kt|Impl.kt)` / `core/auth/AccountDeletionCoordinator.kt` /
`feature/account/AccountSheet.kt` / `AccountSheetUiState.kt` / `AccountSheetViewModel.kt` /
`res/values/strings.xml` / `app/src/test/...` 配下のテスト 3 ファイル / `docs/specs/51-...`。
すべて Issue #51 で許可された境界（feature/account, core/data, core/auth, strings.xml,
app/src/test）の範囲内。

## Verified Requirements

- 1.1 — `AccountSheetDeleteSection` を `AccountSheetBody` 内で常時 Compose 階層に配置（`AccountSheet.kt` `Column` 内）。`AccountSheetViewModelTest#Issue51 Req 1_1 初期 deletion 状態は Idle` で初期状態の整合性を担保
- 1.2 — `AccountSheetDeleteSection` で `MaterialTheme.colorScheme.error` を採用し warning カラーで破壊性を表示（`AccountSheet.kt`）
- 1.3 — `AccountSheetViewModel.startDeletion()` → `DeletionState.ConfirmExplanation` 遷移。`Issue51 Req 1_3 startDeletion で ConfirmExplanation に遷移する` / `Hidden 状態での startDeletion は no-op` / `ログアウト進行中は startDeletion を受け付けない`
- 1.4 — 二段確認完了前の DELETE 送信なし。`Issue51 Req 1_4 startDeletion 単体では Coordinator perform は呼ばれない` / `ConfirmExplanation 状態で confirmDeletion を呼んでも Coordinator は呼ばれない`
- 2.1 — `strings.xml` `account_sheet_delete_confirm_message` で「すべての購読」「既読・スター状態」「取り消せない」を明示
- 2.2 — `AccountSheetDeleteExplanationDialog` に `confirmButton`(次へ進む) と `dismissButton`(キャンセル) を両方配置
- 2.3 — `AccountSheetViewModel.proceedToFinalConfirm()` で `ConfirmFinal` 遷移。`Issue51 Req 2_3 proceedToFinalConfirm で ConfirmFinal に遷移する`
- 2.4 — `AccountSheetDeleteFinalConfirmDialog` に「退会する」「キャンセル」両ボタン配置
- 2.5 — `cancelDeletion()` の挙動。`Issue51 Req 2_5 1段目で cancelDeletion すると Idle に戻り Coordinator perform は呼ばれない` / `2段目で cancelDeletion すると Idle に戻り Coordinator perform は呼ばれない`
- 2.6 — `confirmDeletion()` → `AccountDeletionCoordinator.perform()` 1 回 + `UserRepository.deleteMe()` で `DELETE /api/users/me` 1 回送信。`Issue51 Req 2_6 confirmDeletion で Coordinator perform が 1 回呼ばれる` / `UserRepositoryImplTest#Issue51 Req 2-6 deleteMe issues DELETE to api users me` / `AccountDeletionCoordinatorTest#Req 2_6 perform は DELETE api users me を 1 回呼ぶ` / `revoke は呼ばない`
- 3.1 — `InProgress` 状態 + `CircularProgressIndicator` 表示。`Issue51 Req 3_1 confirmDeletion 中は InProgress 状態である`
- 3.2 — 「退会する」ボタン `enabled = !inProgress` + ViewModel 側多重起動防止。`Issue51 Req 3_2 InProgress 中の再 confirmDeletion は無視される` / `InProgress 中の cancelDeletion は無視される`
- 3.3 — `AccountSheetLogoutSection` で `disabled = logoutInProgress || deletionInFlight` + ViewModel 側 `logout()` ガード。`Issue51 Req 3_3 退会フロー中は logout 操作が受付不可になる`
- 4.1 — `AccountDeletionCoordinatorImpl.perform()` で `tokenStore.clear()`。`AccountDeletionCoordinatorTest#Req 4_1 成功で TokenStore は空になる`
- 4.2 — `userScopedCaches.forEach { reset() }`。`Req 4_2 成功で ItemStateStore overlay が空になる`
- 4.3 — `authRepository.refreshAuthenticatedState()` で StateFlow 同期 → `observeIsAuthenticated = false`。`Req 4_3 成功で observeIsAuthenticated が false に遷移する`
- 4.4 — 既存 AppShell が `LoggedOut` → LoginScreen 描画する経路を流用（本 Issue で経路変更なし）。Coordinator 側で `observeIsAuthenticated = false` 反転を担保（4.3 の仕組み）
- 4.5 — `confirmDeletion()` 成功時 `_uiState.value = AccountSheetUiState.Hidden`。`Issue51 Req 4_1 4_5 confirmDeletion 成功で Hidden に戻り cachedUser が破棄される`
- 5.1 — 失敗パスで `tokenStore.clear()` を一切呼ばない。`AccountDeletionCoordinatorTest#Req 5_1 5_2 サーバーエラーで TokenStore は維持される` / `Req 5_1 サーバーエラーでも ItemStateStore overlay は維持される`
- 5.2 — 同上のテストで `observeIsAuthenticated = true` のまま維持
- 5.3 — `AccountDeletionCoordinatorTest#Req 5_3 ネットワーク失敗で TokenStore と SessionState は維持される` / `UserRepositoryImplTest#Issue51 Req 5-3 deleteMe network failure surfaces FeedmanException with NETWORK_ERROR`
- 5.4 — `DeletionResult.Failure.message` で非空文言を保持し `DeletionState.Error(message)` に遷移。`Issue51 Req 5_1 5_4 confirmDeletion 失敗で Error 状態に遷移し message を保持する` / `Req 5_4 errorMessage 空のサーバーエラーは code 別フォールバック文言を採用する`
- 5.5 — `Error` 状態から `startDeletion()` で再起動可能。`Issue51 Req 5_5 失敗後に startDeletion で再度二段確認をやり直せる` / `Req 5_5 失敗後に再 perform で成功する_リエントランシ`
- NFR 1.1 — 同期的に `InProgress` 遷移 → 次フレームで `CircularProgressIndicator` 描画。`Issue51 Req 3_1 confirmDeletion 中は InProgress 状態である` で間接担保
- NFR 1.2 — Retrofit/OkHttp デフォルトタイムアウト経由で `FeedmanException(NETWORK_ERROR)` → 即時 Error 遷移。`UserRepositoryImplTest#Issue51 Req 5-3` でネットワーク失敗 → 失敗確定の経路を担保
- NFR 2.1 — `AccountDeletionCoordinatorImpl` / `UserRepositoryImpl` / `AccountSheetViewModel` に `Log.*` 呼び出し無し（差分内 grep で確認）
- NFR 2.2 — 成功テスト群（Req 4_1 / 4_2 / 4_3）と失敗テスト群（Req 5_1 / 5_2 / 5_3）の対比的検証で取り違え防止を担保
- NFR 3.1 — email 等識別情報をログに出力する箇所が実装に存在しない（差分内 grep で確認）

## Findings

なし。

## Summary

Issue #51 の全 AC（Req 1.1〜5.5 / NFR 1〜3）について、`AccountDeletionCoordinator` + `AccountSheetViewModel` + Compose UI + `UserRepository.deleteMe()` の経路で観測可能な実装と JVM 単体テストが揃っている。境界は `feature/account` / `core/auth` / `core/data` / `strings.xml` / `app/src/test` に閉じており、`tasks.md` 相当の許可境界からの逸脱なし。Compose ダイアログ描画自体の instrumented 検証は本 Issue のスコープ外（impl-notes 派生タスク候補としても明記）であり、JVM 単体テストでカバーする方針は判定基準と整合する。`impl-notes.md` の Requirement ID → テスト対応表が各 AC を網羅し、実装と一致している。

RESULT: approve