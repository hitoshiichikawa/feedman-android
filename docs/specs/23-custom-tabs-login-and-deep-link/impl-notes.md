# Issue #23 実装ノート — Custom Tabs ログイン画面とディープリンク受領

## 概要

未ログイン状態のユーザーがアプリ起動 → Google ログインボタン押下 → Custom Tabs で
Google 認可 → `feedman://auth/callback?auth_code=...` ディープリンク受領 →
`AuthRepository.exchange` 成功 → 認証済みシェル遷移、までの 1 本のユーザー動線を
結線した。

## 主要な実装要素

| ファイル | 役割 |
|---|---|
| `feature/login/AuthorizationUrlBuilder.kt` | `<baseUrl>/auth/google/login?flow=native&code_challenge=...` を組み立てる純粋関数（JVM 単体テスト可） |
| `feature/login/LoginViewModel.kt` | PKCE 生成 → URL 組み立て → SavedStateHandle に code_verifier 保持 → AuthRepository.exchange 呼び出し → UI 状態管理 |
| `feature/login/LoginScreen.kt` | fm-sheets.jsx FMLogin 準拠の Compose UI（ブランドカード + Google ボタン + エラー + ローディング） |
| `core/auth/AuthCallbackDispatcher.kt` | MainActivity ↔ LoginViewModel 間のディープリンク URI 配信（Hilt singleton + SharedFlow replay=1） |
| `core/auth/AuthRepositorySessionStateProvider.kt` | `AuthRepository.observeIsAuthenticated()` を `SessionState` にマップして AppShell に流す（mockMode=false 時の本番経路） |
| `MainActivity.kt` | `onCreate` / `onNewIntent` で intent.data を AuthCallbackDispatcher.dispatch に流す |
| `AndroidManifest.xml` | feedman://auth/callback の intent-filter + launchMode="singleTask" |
| `di/AuthModule.kt` | SessionStateProvider を AppConfig.mockMode で動的に切替 |
| `di/PkceModule.kt` | PkceGenerator の Hilt binding（本番では SecureRandom backed） |

## requirement ID → テスト対応表

### Requirement 1（未ログイン時のログイン画面表示）

| ID | テスト | 検証内容 |
|---|---|---|
| 1.1 | `LoginViewModelTest.Req 1_1 initial uiState is Idle` | 初期状態が Idle で UI が描画されることを保証 |
| 1.1 | （静的）`LoginScreen.kt` | `R.string.login_title` / `R.string.login_body` / `R.string.login_google_button` がコンポーザブル内に存在 |
| 1.2 | （静的）`AppShell.kt` の `when (sessionState)` 分岐 | LoggedOut → LoginScreen / LoggedIn → LoggedInShell の二択（既存テスト `AppShellViewModelTest` でカバー） |
| 1.3 | （静的）`LoginScreen.kt` で `MaterialTheme.colorScheme` を参照 | テーマトークン経由のためライト/ダーク追従は FeedmanTheme テスト群で担保（#25） |

### Requirement 2（Custom Tabs 起動）

| ID | テスト | 検証内容 |
|---|---|---|
| 2.1 | `LoginViewModelTest.Req 2_1 to 2_3 startGoogleLogin emits authorization URL...` | startGoogleLogin で `<baseUrl>/auth/google/login?...` を openCustomTabs に emit |
| 2.1 | `AuthorizationUrlBuilderTest.Req 2_1 build appends path to baseUrl...` / `...returns full URL...` | URL 組み立ての正確性 |
| 2.2 | `LoginViewModelTest.Req 2_1 to 2_3` + `AuthorizationUrlBuilderTest.Req 2_2 build includes code_challenge...` | code_challenge + S256 method がクエリに含まれる |
| 2.3 | `AuthorizationUrlBuilderTest.Req 2_3 build includes flow=native query parameter` | flow=native の付与 |
| 2.4 | `LoginViewModelTest.Req 2_4 startGoogleLogin stores generated code_verifier in SavedStateHandle` | code_verifier が SavedStateHandle に保存される |
| 2.5 | `LoginViewModelTest.Req 2_5 startGoogleLogin while LaunchingCustomTabs is no-op` | 二重押下時に PKCE 生成が走らず Custom Tabs が二重起動しない |

### Requirement 3（ディープリンクと exchange）

| ID | テスト | 検証内容 |
|---|---|---|
| 3.1 | `AuthCallbackDispatcherTest.Req 3_1 dispatch emits URI to intents flow` / `...cold start dispatch is replayed...` | dispatcher が URI を配信する（MainActivity からの cold start replay 含む） |
| 3.1 | `LoginViewModelTest.Req 3_1 AuthCallbackDispatcher emit triggers exchange via onDeepLink` | dispatcher 経由で onDeepLink がトリガされる |
| 3.2 | `LoginViewModelTest.Req 3_2 onDeepLink success calls AuthRepository_exchange...` | auth_code + 保持中の code_verifier で exchange が呼ばれる |
| 3.3 | `LoginViewModelTest.Req 3_3 onDeepLink success transitions uiState to Idle` | 成功で UiState=Idle に戻る。SessionState 切替は `AuthRepositorySessionStateProviderTest.Req 3_3 transitions to LoggedIn when AuthRepository emits true` で別途検証 |
| 3.4 | `LoginViewModelTest.Req 3_4 NFR 1_3 onDeepLink success clears stored verifier` | 成功で code_verifier が削除される |
| 3.5 | `LoginViewModelTest.Req 3_5 startGoogleLogin while Exchanging is no-op` | exchange 進行中の二重押下抑止 |

### Requirement 4（失敗時の表示と再試行）

| ID | テスト | 検証内容 |
|---|---|---|
| 4.1 | `LoginViewModelTest.Req 4_1 onDeepLink INVALID_GRANT transitions to Error_Server` | INVALID_GRANT で Error.Server に遷移 |
| 4.2 | `LoginViewModelTest.Req 4_2 onDeepLink network failure transitions to Error_Network` | ネットワーク失敗で Error.Network に遷移 |
| 4.3 | `LoginViewModelTest.Req 4_3 startGoogleLogin after Error generates fresh code_verifier` | Error 状態から再押下で新しい PKCE が生成される |
| 4.4 | `LoginViewModelTest.Req 4_4 NFR 1_3 onDeepLink failure clears stored verifier` | 失敗時にも code_verifier が削除される（NFR 1.3） |

### Requirement 5（Custom Tabs を閉じた場合）

| ID | テスト | 検証内容 |
|---|---|---|
| 5.1 | `LoginViewModelTest.Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange` | ディープリンクが来ない場合は Error 表示しない（auth_code 無しコールバックでも no-op = ログイン画面のまま） |
| 5.2 | `LoginViewModelTest.Req 4_3 startGoogleLogin after Error generates fresh code_verifier` を流用 | Custom Tabs を閉じた後の再押下で新しい PKCE が生成される（Idle / Error どちらからも再押下できる） |
| 5.3 | `LoginViewModelTest.Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange` / `Req 5_3 onDeepLink without saved verifier does not call exchange` | auth_code を含まないコールバックでは exchange を呼ばない |

### Non-Functional Requirements

| ID | テスト | 検証内容 |
|---|---|---|
| NFR 1.1 | コードレビュー（静的） | `code_verifier` をログ・クラッシュレポート・解析イベントに渡さない（出力なし） |
| NFR 1.2 | `LoginViewModelTest.Req 2_4` / `Req 3_4` / `Req 4_4` | SavedStateHandle 経由のプロセス常駐保存。永続ストアに書かない |
| NFR 1.3 | `LoginViewModelTest.Req 3_4 NFR 1_3` / `Req 4_4 NFR 1_3` | exchange の成否確定時に code_verifier を破棄 |
| NFR 2.1 | `LoginViewModelTest.NFR 2_1 onDeepLink with non-feedman scheme does not call exchange` / `...wrong host or path...` | feedman://auth/callback 以外を認証フロー入力にしない |
| NFR 2.2 | `LoginViewModelTest.Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange` | auth_code 欠落時に exchange を呼ばない |
| NFR 3.1 | （静的）`onDeepLink` の suspending exchange を viewModelScope で起動 | プロセス生存中は exchange 待機を維持 |
| NFR 3.2 | 既存 `AuthRepositoryImplTest`（#21）+ Retrofit/OkHttp デフォルト timeout 30s | exchange の応答は OkHttp 既定 timeout に従う |

## 実装上の判断

### SessionStateProvider を `Provider<T>` で動的切替

`AppConfig.mockMode` で `MockModeSessionStateProvider` と
`AuthRepositorySessionStateProvider` のいずれかを `@Provides` で返す形にした。
`@Binds` 不可（実装が 2 つあるため）と `Provider<T>` 利用で「必要な側だけ初期化」を
両立させた。mockMode = true 環境では `AuthRepositorySessionStateProvider` が一切
初期化されないため、Application scope の長寿命 collect が走らない。

### `AuthCallbackDispatcher` を新設した理由

MainActivity（Activity スコープ）と LoginViewModel（Composable スコープ）を疎結合に
保つため、Hilt singleton の `SharedFlow(replay=1)` を間に挟む。cold start で
ディープリンクから起動された場合に LoginViewModel の init が遅れても 1 件 replay
されるので取り逃がしがない。

### `launchMode="singleTask"` 採用

Custom Tabs から `feedman://auth/callback?...` で戻る経路で、新規 Activity が
作られると ViewModel が再構築されて SavedStateHandle が空になり code_verifier を
失う。`singleTask` は同 task 内で唯一の Activity を保証し、`onNewIntent` で確実に
既存 Activity に届く。`singleTop` でも onNewIntent は呼ばれるが、Task / Affinity の
取り回しを単純化するため singleTask を採用した。

### SessionState の起動時復元は #24 に委ねる

`AuthRepositoryImpl.observeIsAuthenticated()` の初期値は `false` のため、cold start
時に既存トークンがあっても LoggedIn にならない（仕様どおり）。本 Issue では
「ログイン直後の LoggedOut → LoggedIn 遷移が AppShell に反映される」までを実装し、
起動時 refresh は #24 で扱う。

### 既存テストとの整合

`AppShellViewModelTest` のドキュメントコメントだけ `LoginPlaceholderScreen` →
`LoginScreen` に文言更新。テスト本体（SessionStateProvider fake で挙動を検証）は
そのまま流用できる。

## 確認事項

- **`launchMode="singleTask"` の影響**: タスクが既に存在する場合に singleTask が
  Custom Tabs 起動済みの状態をクリアする可能性。実機検証で Custom Tabs から戻る
  経路の挙動を念のため確認したい（PR 本文にも記載予定）。
- **`SessionState` cold start の挙動**: 既存トークンがあるユーザーが cold start
  すると一瞬ログイン画面が表示される（spec 上は #24 のスコープ）。UX 上問題に
  ならないか、#24 で復元処理を実装する際に併せて確認したい。
- **`AuthCallbackDispatcher` の `replay=1`**: 古い URI が再起動後に再送される可能性
  （process kill → 再起動 → 古い auth_code が 1 件 replay）。本 Issue 範囲では
  `auth_code` 自体が 60 秒 / 単回交換のため再 replay でも 2 回目は INVALID_GRANT で
  失敗するが、UX として一瞬エラーが見えるリスクがある。改善余地として記録。

## 派生タスク候補

- 起動時のトークン復元（#24）で `AuthRepositoryImpl` に suspend 初期化フックを
  ApplicationScope から呼んで `observeIsAuthenticated()` の初期値を反映する経路を
  整備する。
- 詳細な OAuth エラー（INVALID_GRANT 以外のサーバー応答）について個別のメッセージ
  / ユーザー操作（例: 退会済みアカウント検知）を扱う Issue は別途切り出す。
- Compose UI Test（androidTest）で LoginScreen の状態遷移を実機検証する Issue を
  起票する余地あり（本 Issue では JVM 単体テストに限定）。

## ビルド結果

`./gradlew build` 成功（lint / unit test / release 含む）。

STATUS: complete
