# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-49-impl-account-sheet
- HEAD commit: 9ffdfe9
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `AppShell.kt` の `onAccountAreaTap = { accountSheetViewModel.open() }` 結線 + `AccountSheetViewModelTest::初期状態は Hidden_Req 1_1`（初期 Hidden の挙動）+ `AccountSheet` Composable が `Visible` 状態時のみ `FeedmanSheet` を表示する分岐
- 1.2 — `AccountSheetViewModel.startFetch()` で `repository.getCurrentUser()` を 1 回起動。`AccountSheetViewModelTest::open で取得成功すると Visible Loaded になる_Req 1_2 Req 2_1 Req 3_3`（callCount=1 を assert）+ `UserRepositoryImplTest::Req 1-2 getCurrentUser issues GET to auth me`
- 1.3 — `AccountSheet.kt` がユーザー領域（アバター + "You" + ステータス行 + 閉じるアイコン）+ `HorizontalDivider` を Compose で構成。JVM での構成検査は instrumented 領分のため Composable 実装で担保（impl-notes.md の取り決め通り、本 Reviewer も missing test 扱いとしない）
- 1.4 — `cachedUser` に Loaded 結果を保持し、再 open 時に再フェッチ抑止。`AccountSheetViewModelTest::Loaded 後に close して再 open しても再フェッチしない_Req 1_4`
- 2.1 — `AccountSheet.kt::AccountSheetUserStatusLine` の Loaded 分岐で `loadState.user.email` を Text 表示。`AccountSheetViewModelTest::open で取得成功すると...` + `UserRepositoryImplTest::Req 2-1 ...decodes 200 response into User with email`
- 2.2 — 同 Composable で `email.isNotBlank()` 判定 → `R.string.account_sheet_email_missing` を選択。`AccountSheetViewModelTest::email が空文字でも Loaded として user_email を保持_Req 2_2` + `UserRepositoryImplTest::Req 2-2 ...empty email as empty string`
- 2.3 — `AccountSheetHeader` で `R.string.account_sheet_user_label`（"You"）を常時描画
- 3.1 — `startFetch()` 内で同期的に `Visible(LoadState.Loading)` に遷移、Composable は Loading 分岐で `CircularProgressIndicator` を描画。`AccountSheetViewModelTest::open で進行中のあいだは Loading 状態_Req 3_1 Req 3_2`
- 3.2 — 同テスト + Composable の Loading 分岐は Text(email) を一切描画せず、確定値は出さない
- 3.3 — `startFetch()` の try/catch で Loading → Loaded / Error への遷移を実装。`AccountSheetViewModelTest::open で取得成功すると...` + `回復可能エラーで Visible Error に遷移する_Req 4_1`
- 4.1 — UNAUTHORIZED 以外の `FeedmanException` を `Visible(Error(message))` に積み、Composable Error 分岐で再試行 TextButton を表示。`AccountSheetViewModelTest::回復可能エラーで Visible Error に遷移する_Req 4_1` + `errorMessage が空のときは code 別フォールバック文言を採用する_Req 4_1` + `UserRepositoryImplTest::Req 4-1 network failure / 5xx response`
- 4.2 — `AccountSheetViewModel.retry()` が Error 状態時のみ `startFetch()` を再実行。`AccountSheetViewModelTest::retry で再フェッチ成功すると Visible Loaded に遷移する_Req 4_2 Req 4_3`（callCount=2 を assert）
- 4.3 — 同テスト（成功 Loaded 遷移）
- 4.4 — `AccountSheetHeader` の close `IconButton` は `LoadState` 分岐外に配置されており、Error 状態でも常時クリック可能（実装で担保）
- 5.1 — `startFetch()` の UNAUTHORIZED 分岐で `_uiState.value = Hidden`。`AccountSheetViewModelTest::UNAUTHORIZED 時は Hidden に戻り UnauthorizedRedirect イベントを発火する...` + `UserRepositoryImplTest::Req 5-1 401 UNAUTHORIZED response...`
- 5.2 — `AccountSheetEvent.UnauthorizedRedirect` を SharedFlow で発火、`AppShell` の `AccountSheet(onUnauthorized = {})` 経路で接続。実際のログイン画面遷移は SessionState 観測経路に委譲（impl-notes.md の判断通り）
- 5.3 — UNAUTHORIZED 時は `Visible(Error)` に積まず Hidden に直接遷移。`AccountSheetViewModelTest::UNAUTHORIZED 時は Visible Error として表示しない_Req 5_3`
- NFR 1.1 — `open()` 同期処理内で即座に `Visible(Loading)` に遷移するため、ローディング提示は再構成オーバーヘッド分のみ（1 秒以内を満たす）
- NFR 1.2 — `account_sheet_avatar_description` / `account_sheet_close_description` / `account_sheet_loading_description` の contentDescription + ユーザー領域に `LiveRegionMode.Polite` を設定
- NFR 2.1 — ViewModel / Repository いずれも `android.util.Log` 呼び出し無しを diff で確認
- NFR 2.2 — `cachedUser` は ViewModel フィールド（プロセス内メモリ）のみ。DataStore / SharedPreferences への永続化なし

## Boundary 確認

差分は `core/data/{UserRepository,UserRepositoryImpl}.kt` / `di/RepositoryModule.kt` / `feature/account/{AccountSheet,AccountSheetUiState,AccountSheetViewModel}.kt` / `shell/AppShell.kt` / `res/values/strings.xml` / `app/src/test/...` および spec ファイル（`requirements.md` / `impl-notes.md`）に閉じている。`feature/logout` / 退会フロー（#50 / #51）への侵食なし。`AppShellSheet.Account` enum は後方互換のため温存され、`AppShellViewModelTest` への影響を回避（impl-notes.md の派生タスク候補として明示）。

## テスト実行確認

`./gradlew :app:testDebugUnitTest --tests AccountSheetViewModelTest --tests UserRepositoryImplTest` を実行し BUILD SUCCESSFUL を確認。

## Findings

なし。

## Summary

`/auth/me` 取得 / ローディング / 回復可能エラー + 再試行 / 認証エラー時のシート閉鎖と Unauthorized イベント発火が AC 単位の単体テストでカバーされており、Boundary も `feature/account` + 最小限の `core/data` / `di` / `shell` 結線 + `strings.xml` / `app/src/test` に閉じている。#50（ログアウト）/ #51（退会）への侵食はなし。NFR（ローディング即時提示 / a11y / ログ抑止 / 永続化なし）も実装で満たされている。

RESULT: approve
