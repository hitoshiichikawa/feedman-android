# 実装ノート（Issue #49: Account sheet and current user loading）

## 実装方針サマリ

ドロワーフッタ「アカウント」導線から開くアカウントシートで、現在ログイン中ユーザーの
email を表示する。`GET /auth/me` の取得・状態遷移・認証失効時の挙動を実装した。
ログアウトボタン（#50）と退会フロー（#51）は別 Issue のため本実装に含まない。

主な構成要素:

- `core/data/UserRepository(.kt)` + `UserRepositoryImpl(.kt)`: `FeedmanApi.getCurrentUser()`
  への薄い委譲層
- `feature/account/AccountSheetUiState.kt`: `Hidden` / `Visible(LoadState)` の sealed UI 状態
- `feature/account/AccountSheetViewModel.kt`: open / close / retry を持つ ViewModel。
  Loaded を ViewModel 内 cache に保持し、再 open での再フェッチを抑止する（Req 1.4）
- `feature/account/AccountSheet.kt`: `FeedmanSheet` で包んだ Composable。
  ユーザー領域（アバター + "You" + email or loading or error + 再試行）+ 区切り線
- `shell/AppShell.kt`: `accountSheetViewModel` を LoggedInShell スコープに保持し、
  ドロワーの `onAccountAreaTap` で `open()` を呼ぶ。`AccountSheet` を Scaffold 直下に配置

## requirement ID → テスト対応表

| Req ID | 内容 | 対応テスト |
|---|---|---|
| 1.1 | アカウント項目タップでシート表示 | `AccountSheetViewModelTest::初期状態は Hidden_Req 1_1` + `AppShell` 結線（`onAccountAreaTap` → `accountSheetViewModel.open()`） |
| 1.2 | 開時に現在ユーザー取得を 1 回開始 | `AccountSheetViewModelTest::open で取得成功すると Visible Loaded になる_Req 1_2 Req 2_1 Req 3_3`（callCount=1 を assert）、`UserRepositoryImplTest::Req 1-2 getCurrentUser issues GET to auth me` |
| 1.3 | プロト構成（ユーザー領域 / 区切り線 / 閉じるボタン）で描画 | `AccountSheet.kt` のレイアウトコードで担保（FMAccountSheet を Compose で再現）。AC は構成検査の性質上 instrumented テストの領分なので JVM テストでは VM 出力（Visible 状態）の存在で代替担保 |
| 1.4 | 同一セッション内の再オープンで再フェッチしない | `AccountSheetViewModelTest::Loaded 後に close して再 open しても再フェッチしない_Req 1_4` |
| 2.1 | email を表示 | `AccountSheetViewModelTest::open で取得成功すると Visible Loaded になる_Req 1_2 Req 2_1 Req 3_3`（Loaded.user.email を assert）、`UserRepositoryImplTest::Req 2-1 getCurrentUser decodes 200 response into User with email` |
| 2.2 | email が空 / 欠落時に代替文言 | `AccountSheetViewModelTest::email が空文字でも Loaded として user_email を保持_Req 2_2`、`UserRepositoryImplTest::Req 2-2 getCurrentUser decodes empty email as empty string`、UI 側は `AccountSheet.kt::AccountSheetUserStatusLine` で `email.isNotBlank()` 判定 → `R.string.account_sheet_email_missing` |
| 2.3 | 見出しラベル "You" を表示 | `AccountSheet.kt` のヘッダで `R.string.account_sheet_user_label` を常時表示 |
| 3.1 | 取得中ローディングインジケータ | `AccountSheetViewModelTest::open で進行中のあいだは Loading 状態_Req 3_1 Req 3_2`、UI は `LoadState.Loading` 分岐で `CircularProgressIndicator` を表示 |
| 3.2 | 取得中は確定値としての email / 代替を出さない | 同上テスト + `AccountSheet.kt::AccountSheetUserStatusLine` で Loading 分岐は Text email を一切描画しない |
| 3.3 | 取得完了でローディング → 成功 / 失敗 表示へ | `AccountSheetViewModelTest::open で取得成功すると Visible Loaded になる_Req 1_2 Req 2_1 Req 3_3` + `回復可能エラーで Visible Error に遷移する_Req 4_1` |
| 4.1 | 回復可能エラー（ネットワーク / 5xx）でエラー + 再試行表示 | `AccountSheetViewModelTest::回復可能エラーで Visible Error に遷移する_Req 4_1`、`errorMessage が空のときは code 別フォールバック文言を採用する_Req 4_1`、`UserRepositoryImplTest::Req 4-1 network failure surfaces FeedmanException with NETWORK_ERROR code` + `Req 4-1 5xx response surfaces FeedmanException with server code` |
| 4.2 | 再試行操作で再フェッチ | `AccountSheetViewModelTest::retry で再フェッチ成功すると Visible Loaded に遷移する_Req 4_2 Req 4_3`（callCount=2 を assert） |
| 4.3 | 再試行成功で Req 2 表示へ | 同上テスト（Loaded.user を assert） |
| 4.4 | エラー表示中も閉じるボタンを受け付ける | `AccountSheet.kt` のヘッダで close IconButton は LoadState に依存せず常時表示・有効 |
| 5.1 | 認証エラーでシートを閉じる | `AccountSheetViewModelTest::UNAUTHORIZED 時は Hidden に戻り UnauthorizedRedirect イベントを発火する_Req 5_1 Req 5_2 Req 5_3`、`UserRepositoryImplTest::Req 5-1 401 UNAUTHORIZED response surfaces FeedmanException with UNAUTHORIZED code` |
| 5.2 | 認証エラーでログイン画面へ遷移 | `AccountSheetViewModelTest::...UnauthorizedRedirect イベントを発火する...`（イベント発火を assert）。実際の画面切替は AppShell の `when(sessionState)` 観測経由で行う（SessionStateProvider が LoggedOut を流せば自動切替）。Open Question 判断（人間決定方針）に従い追加トースト等の補助フィードバックは出さない |
| 5.3 | 認証エラーは Req 4 通常エラー表示には積まない | `AccountSheetViewModelTest::UNAUTHORIZED 時は Visible Error として表示しない_Req 5_3` |
| NFR 1.1 | 1 秒以内にローディング提示 | `open()` 同期処理内で即座に `Visible(Loading)` に遷移するため、Compose 再構成オーバーヘッドのみ（数 ms オーダー） |
| NFR 1.2 | スクリーンリーダー読み上げ可能 | `AccountSheet.kt` の各要素に `contentDescription` を付与、ユーザー領域は `LiveRegionMode.Polite` を設定 |
| NFR 2.1 | 取得した email をログ出力しない | 本実装では User 情報を `android.util.Log` 経由で出力する箇所を一切持たない（ViewModel / Repository のいずれも `Log` 呼び出しなし） |
| NFR 2.2 | 端末永続ストレージに平文保存しない | `cachedUser` は ViewModel インスタンスのプロセス内メモリのみで保持（DataStore / SharedPreferences 等への永続化なし）。アプリ終了時に破棄される |

## 実装上の判断

### 認証エラー時の補助フィードバック（Open Question 解決）

要件の Open Question で「トーストやスナックバー等の補助フィードバックを併出するか」が
未決定だったが、SPEC §6 の共通挙動に合わせ、**ログイン画面遷移のみで追加トーストは出さない**
方針を採用した（事前指示）。`AccountSheetEvent.UnauthorizedRedirect` は SharedFlow で
通知され、`AppShell` 側で SessionState の LoggedOut を観測してログイン画面に切り替わる
（`AppShell.AccountSheet(onUnauthorized = {})` は no-op）。

### キャッシュ範囲（Req 1.4）

「同一セッション」のスコープを **ViewModel インスタンスのライフサイクル**（= Activity 単位）
として解釈した。プロセスをまたいだ永続化は NFR 2.2 違反の恐れがあるため、`cachedUser` は
ViewModel フィールドに留め、Activity 再生成時には再フェッチが起きる。本判断は要件の
「セッション」を Activity スコープに丸めることになるが、NFR 2.2（永続ストレージへ平文
保存しない）を優先する判断として妥当と考える。

### `AppShellSheet.Account` enum の温存

`AppShellSheet` enum を削除すると `AppShellViewModelTest` が壊れるため、`Account` /
`FeedRegistration` 値は後方互換のため残置した（実装ブランチは no-op）。enum 値の削除と
`AppShellViewModel.openSheet` の整理は本 Issue のスコープ外（別途リファクタ Issue が必要）。

### `LoadState` のサブクラス構造

`Visible(loadState: LoadState)` という入れ子構造を採用し、`Loading` / `Loaded` / `Error`
の遷移を `LoadState` 内で完結させた。これにより、Visible / Hidden の切替（AppShell からの
起動・閉鎖）とユーザー領域のロード状態（取得・再試行・エラー）の責務を明確に分離できる。

## 確認事項（レビュワー向け）

- AppShell の `onUnauthorized = {}` （no-op）について、将来 SessionStateProvider 本実装
  （#24 系）が入った際に「観測経路で LoggedOut へ自動遷移する」前提が保たれるか。現状
  `MockModeSessionStateProvider` は mockMode 連動でしか LoggedOut を流さないため、
  本 Issue 単体では認証切れ時の画面遷移を end-to-end で観測することはできない。
  `AccountSheetViewModelTest` で UnauthorizedRedirect イベント発火までを担保するに留めた。
- `Req 1.3`（プロト構成での描画）は JVM テストでは構成自体の検査が困難なため、Composable
  実装で担保する形にした。Compose UI Test（instrumented）での担保が必要なら別 Issue で。
- requirements.md の Open Question（補助フィードバック）は「ログイン画面遷移のみ」と
  決定済み（事前指示）。本判断を spec に追記する場合は別 PR（PM 領分）が必要。

## 派生タスク候補

- `AppShellSheet` enum と `AppShellViewModel.openSheet` の整理（Account / FeedRegistration
  が完全に no-op になったため、enum 自体の削除と `AppShellViewModelTest` のリファクタが
  必要だが、本 Issue のスコープ外）
- アカウントシートの instrumented UI テスト（Req 1.3 の構成検査、Talkback 読み上げ検証）

## 実行確認

- `./gradlew build` 成功（lint / unit test 含む）
- 追加した単体テスト:
  - `app/src/test/kotlin/com/feedman/android/core/data/UserRepositoryImplTest.kt`（5 ケース）
  - `app/src/test/kotlin/com/feedman/android/feature/account/AccountSheetViewModelTest.kt`（13 ケース）

STATUS: complete
