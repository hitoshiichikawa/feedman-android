# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-24-impl-launch-auth-restoration
- HEAD commit: 1ceaca5
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `AuthRepositorySessionStateProvider`: `MutableStateFlow(SessionState.Restoring)` を初期値で公開（`SessionState.kt` `Restoring` 追加）。テスト `Req 1_1 initial state is Restoring before restore coroutine resumes` が StandardTestDispatcher 下で `provider.state.value == Restoring` を検証
- 1.2 — `performRestore()` で `RefreshResult.Success` → `SessionState.LoggedIn`。テスト `Req 1_2 stored token and refresh success transitions to LoggedIn`
- 1.3 — `performRestore()` で `hasAccessToken == false` の場合は refresh を呼ばずに `LoggedOut`。テスト `Req 1_3 no stored token transitions Restoring to LoggedOut without network call` が `repo.refreshCallCount == 0` を assert
- 1.4 — `performRestore()` で `RefreshResult.AuthRequired` → `LoggedOut`（TokenStore 消去は AuthRepositoryImpl 側契約）。テスト `Req 1_4 INVALID_REFRESH_TOKEN clears tokens and transitions to LoggedOut` が `tokenStore.read() == null` と `LoggedOut` を assert
- 1.5 — `performRestore()` で `NetworkFailure` / `ServerError` → `LoggedIn` フォールバック（TokenStore 保持）。テスト `Req 1_5 network failure during refresh keeps token and falls back to LoggedIn` が `tokenStore.read() == sampleTokenSet()` と `LoggedIn` を assert
- 2.1 — `AppShell.kt` `when (sessionState) { Restoring -> RestoringSplash() }` 分岐 + `RestoringSplash` Composable（`Box + Column(CircularProgressIndicator + Text(stringResource(session_restoring_description)))`）
- 2.2 / 2.3 — `RestoringSplash()` は `LoginScreen` / `LoggedInShell` を一切含まず、`when` 分岐により他経路が排他的に選択される構造
- 3.1 / 3.2 — `AppShell.kt` `when` 分岐 `LoggedIn -> LoggedInShell()`、`Restoring` 分岐は別 Composable で同一 Composition には共存しない
- 3.3 — `Req 1_2` テストで保存トークン + refresh 成功経路の LoggedIn 確定を担保（再ログイン要求が発生しないことは AppShell の構造で保証）
- 4.1 / 4.2 — `AppShell.kt` `when` 分岐 `LoggedOut -> LoginScreen(linkOpener = viewModel.linkOpener)`
- 4.3 — `restoreAndFollow()` Phase 2 `authRepository.observeIsAuthenticated().drop(1).collect`。テスト `Req 4_3 after restore LoggedIn transitions to LoggedOut when isAuthenticated flips to false` が復元後の `flip(false)` で LoggedOut への遷移を検証
- 4.4 — `LoggedInShell` 全体が Composition から外れる構造（impl-notes.md `確認事項 3.` 参照）。UI 単体テストは Compose UI Test 必要のため別 Issue 推奨という方針に同意
- 5.1 — `SessionState.kt` の sealed class に `Restoring` / `LoggedIn` / `LoggedOut` 3 つの `data object` を定義
- 5.2 — `AuthRepositorySessionStateProvider` が `@Singleton` 修飾 + `AuthModule.provideSessionStateProvider` も `@Singleton` バインドで単一ソースを保証（`AuthModule.kt:67-77`）
- 5.3 — テスト `Req 5_3 after restore LoggedOut transitions to LoggedIn when login succeeds` が exchange 成功相当 (`flip(true)`) の通知を検証
- NFR 1.1 — `withTimeoutOrNull(restoreTimeoutMillis)` (デフォルト 5_000ms) + `fallbackOnTimeoutOrFailure`。テスト 2 件 `NFR 1_1 refresh timeout falls back to LoggedIn when token is stored` / `NFR 1_1 refresh timeout falls back to LoggedOut when token is missing`（`refreshDelayMillis = 10_000L`, `advanceTimeBy(6_000L)`）
- NFR 1.2 — Req 1.3 テスト内で `repo.refreshCallCount == 0` を assert
- NFR 2.1 — Hilt `@Singleton` バインド（既存）
- NFR 2.2 — `StateFlow` 公開 + 既存 `MockModeSessionStateProviderTest` の StateFlow 即時値テスト
- NFR 3.1 — Req 1.4 テスト内で `tokenStore.read() == null` を assert
- NFR 3.2 — 既存 Issue #21 / #22 系のテストでカバー（本 Issue では Provider 側の追従挙動を Req 4_3 でカバー）

## Findings

なし

## Summary

requirements.md の全 AC（Req 1.1〜5.3 / NFR 1.1〜3.2）に対し、`AuthRepositorySessionStateProvider` の Phase 1 復元シーケンスと Phase 2 追従、`AppShell` の Restoring/LoggedIn/LoggedOut 3 分岐、`SessionState` の 3 状態 sealed class、`@Singleton` 単一ソースが揃っており、テストも 8 件追加で網羅されている。`drop(1)` による初期値破棄は Req 1.5 フォールバックを実現するために必要な設計で、impl-notes.md の解説と整合する。境界は `core/auth/` / `shell/AppShell.kt` / `strings.xml` / `app/src/test/` に閉じており、Issue #50 のログアウト UI には踏み込んでいない。`./gradlew :app:testDebugUnitTest` も BUILD SUCCESSFUL。

RESULT: approve
