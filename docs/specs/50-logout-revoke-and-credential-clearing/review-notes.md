# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-50-impl-logout
- HEAD commit: 129fa9b
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（追加の flag 観点判定は行わない）

## Verified Requirements

- 1.1 — `AccountSheet.kt` `AccountSheetLogoutSection` が Visible 状態で常時描画される
- 1.2 — `AccountSheetViewModelTest`「Issue50 Req 1_2 logout で LogoutCoordinator perform が 1 回呼ばれる」/「Hidden 状態での logout は no-op」
- 1.3 — UI 側 `TextButton(enabled = !logoutInProgress)` + VM `logoutJob?.isActive == true` no-op、テスト「Issue50 Req 1_3 logout 中の再 logout 呼び出しは無視される」
- 1.4 — `CircularProgressIndicator` 表示 + テスト「Issue50 Req 1_3 1_4 logout 中は logoutInProgress true_完了で Hidden」
- 2.1 — `LogoutCoordinatorTest`「Req 2_1 perform は revoke を 1 回呼び TokenStore を消去する」（MockWebServer が `POST /api/auth/revoke` を 1 件確認）
- 2.2 — `LogoutCoordinatorTest`「Req 2_2 revoke のサーバーエラーでも TokenStore 消去とキャッシュリセットを行う」（500 応答）
- 2.3 — `LogoutCoordinatorTest`「Req 2_3 revoke のネットワーク失敗でも …」（DISCONNECT_AT_START）
- 2.4 — 上記 3 ケースで `assertNull(tokenStore.read())`
- 3.1 — `LogoutCoordinatorTest`「Req 3_1 perform は ItemStateStore overlay を空にする」+ 個別の `ItemStateStoreTest` / `SubscriptionRepositoryImplTest` / `CrossFeedRepositoryImplTest` の reset テスト
- 3.2 — 観測可能挙動として 3.1 のリセット群でカバー（新セッションでは ViewModel 再生成 + 空状態 StateFlow）。Coordinator 内のリセット完了が事前条件
- 3.3 — `AccountSheetViewModelTest`「Issue50 Req 3_3 logout 後の再 open では cachedUser が再現せず再フェッチが走る」
- 4.1 — `LogoutCoordinatorTest`「Req 4_1 perform 完了後に observeIsAuthenticated が false に遷移」。`AuthRepositorySessionStateProvider` がこれを観測して LoggedOut を流す経路は Issue #24 の既存テストで担保
- 4.2 — AppShell の `when(sessionState)` 既存実装 + Req 4.1 経由で間接担保
- 4.3 — `AccountSheetViewModelTest`「完了で Hidden」
- 4.4 — SessionState 遷移経路で LoggedInShell 配下 Composable / ViewModel が再生成されるため、表示中の他ボトムシート（記事詳細 / 購読設定 / フィード登録）は AppShell 既存契約により破棄される。本 Issue で新たに追加された挙動ではないため新規テストは不要
- 5.1 — `LogoutCoordinatorTest`「Req 5_1 perform は例外を投げない_キャッシュ reset が独立に呼ばれる」+ Req 2_2 / 2_3 群
- 5.2 — `AccountSheetUiState` は logout 経路で `Error` を持たない設計（`logoutInProgress: Boolean` のみ）。UI 側でエラーメッセージを出す導線が存在しないことで満たされる
- NFR 1.1 — `AccountSheetViewModel.logout()` 冒頭で `_uiState.value = current.copy(logoutInProgress = true)` を同期的に設定（即時反映、suspend 開始前）
- NFR 1.2 — `LogoutCoordinatorTest`「NFR 1_2 REVOKE_TIMEOUT_MILLIS は 10 秒に設定されている」+ `LogoutCoordinatorImpl.perform()` の `withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS)`
- NFR 2.1 — `runCatching { authRepository.revoke() }` + `runCatching { cache.reset() }` で各工程の例外を独立に握り潰し、`AuthRepository.revoke` 内の `tokenStore.clear()` が保証
- NFR 2.2 — `LogoutCoordinator` / `LogoutCoordinatorImpl` / `AccountSheetViewModel.logout()` いずれもログ出力なし。トークン値を引数で受け取らない設計
- NFR 3.1 — Coordinator / VM ともに email を扱わない（cachedUser は `null` 代入で破棄するのみで、内容をログに出さない）

## Boundary 検証

変更ファイルは以下に限定されており、許可された境界（core/auth / core/data 最小 / feature/account / strings.xml / app/src/test）に閉じている。

- `core/auth/LogoutCoordinator.kt`（新規 / DI Module も同ファイル内 inline。di/ 配下は未変更）
- `core/data/{UserScopedCache.kt 新規, ItemStateStore.kt, SubscriptionRepositoryImpl.kt, CrossFeedRepositoryImpl.kt}`（既存ストアに `UserScopedCache` 実装 + `reset()` 追加。既存挙動は不変）
- `feature/account/{AccountSheet.kt, AccountSheetViewModel.kt, AccountSheetUiState.kt}`（ログアウト UI + VM）
- `app/src/main/res/values/strings.xml`（logout 関連文言 2 件追加）
- `app/src/test/kotlin/...`（テストのみ）
- `docs/specs/50-...`（spec / impl-notes）

退会（`DELETE /api/users/me`、Issue #51 領分）には踏み込んでいない。`AuthRepository` 本体や `AuthRepositoryImpl` の挙動変更もなし（既存 `revoke()` 契約を利用するのみ）。

## テスト実行結果

`./gradlew test -x lint` を実行。`testDebugUnitTest` / `testReleaseUnitTest` ともに UP-TO-DATE / BUILD SUCCESSFUL。追加された主要テストの個別カウント:

- `LogoutCoordinatorTest`: tests=7 failures=0 errors=0
- `AccountSheetViewModelTest`: tests=20 failures=0 errors=0
- `ItemStateStoreTest` / `SubscriptionRepositoryImplTest` / `CrossFeedRepositoryImplTest`: いずれも reset テスト追加分込みで green

## Findings

なし

## Summary

requirements.md の Req 1〜5 / NFR 1〜3 すべてに対応する実装とテストを確認。MockWebServer + 実 AuthRepository / 実ストア群で組み立てた `LogoutCoordinatorTest` が revoke 成功 / 500 / ネットワーク失敗 の 3 パスで TokenStore 消去 + キャッシュリセット + `observeIsAuthenticated=false` を観測可能挙動として検証しており、CLAUDE.md テスト規約（Retrofit / TokenStore をモックしない）にも準拠している。境界違反・テスト不足は検出されず、ユニットテストも全て green。

RESULT: approve
