# 実装ノート — Issue #51 Account deletion confirmation flow

## サマリ

- アカウントシートに「退会（アカウント削除）」導線を追加し、二段確認（説明 → 最終確認）を介して
  `DELETE /api/users/me` を 1 回送信する経路を実装した。
- 成功時はローカルクレデンシャル消去 + ユーザースコープキャッシュリセット +
  SessionState 経由のログイン画面復帰、失敗時はローカル状態を温存しエラーダイアログを
  提示する。

## 主要な実装判断

### 1. revoke は呼ばない（独立 Coordinator 化）

Issue 指示文の中で「revoke は不要 — アカウント自体が消えるためローカル消去のみで良い」との
判断が示されていた。これを採用し、退会専用の `AccountDeletionCoordinator` を新設した。

`LogoutCoordinator` を流用しない設計上の理由:

- **revoke を呼ばない**: アカウント自体が消えるためサーバーへの revoke 通信は無駄。
  ネットワーク失敗による不必要な遅延を避けられる（NFR 1.1 の体感）。
- **失敗時の挙動が逆**: LogoutCoordinator は失敗してもローカル消去を強行する（ログアウト
  意図の確定 / best-effort）。退会は失敗時にローカル状態を温存しなければならない
  （Req 5.1〜5.3）。両者を同じ実装で表現すると判断分岐が複雑になる。

### 2. TokenStore 消去後の SessionState 同期

`tokenStore.clear()` のみでは `AuthRepositoryImpl.observeIsAuthenticated` の StateFlow が
更新されない（テストで失敗を観測）。`AuthRepositoryImpl.refreshAuthenticatedState()` を
明示的に呼び出して StateFlow を最新の TokenStore 状態と同期する経路を追加した。
本呼び出しはサーバー通信を伴わず（read + StateFlow 更新のみ）、ログイン画面復帰
（Req 4.3 / 4.4）の AppShell 描画切替を成立させる。

DI 上は `AuthRepository` インターフェースに `refreshAuthenticatedState` が存在しないため、
`AccountDeletionCoordinatorImpl` は実装型 `AuthRepositoryImpl` を直接受け取る設計とした
（LogoutCoordinatorImpl が `SubscriptionRepositoryImpl` を実装型で受けるのと同じパターン）。

### 3. 失敗時のエラーメッセージ解決

Issue 指示文の Open Questions への回答に従い:

- `FeedmanException.errorMessage` が非空なら採用（サーバー由来文言を優先）
- 空文字なら code 別フォールバック:
  - `NETWORK_ERROR` → `FALLBACK_NETWORK_MESSAGE`（既存汎用ネットワーク文言）
  - その他 → `FALLBACK_UNKNOWN_MESSAGE`（既存汎用文言）

サーバーエラー時とネットワーク失敗時を文言で区別するのではなく、サーバーが提供した
文言があればそれを優先するという既存の AccountSheetViewModel / Login 画面と同一の流儀。
UI 側にはエラーダイアログで文言を表示するだけに留め、リトライ操作は「退会ボタンから
やり直し」というシンプルなフロー（Req 5.5）。

### 4. 退会成功直後のログイン画面の補助メッセージは無し

Issue Open Questions の通り Out of Scope 準拠で、退会成功直後の補助メッセージは
追加しない。SessionState 遷移経路で AppShell が LoginScreen を描画するのみ。

### 5. 二段確認の UI 状態モデル

`DeletionState` 階層（Idle / ConfirmExplanation / ConfirmFinal / InProgress / Error）で
表現。各遷移は ViewModel 公開メソッドで明示制御:

- `startDeletion`: Idle/Error → ConfirmExplanation
- `proceedToFinalConfirm`: ConfirmExplanation → ConfirmFinal
- `cancelDeletion`: ConfirmExplanation/ConfirmFinal/Error → Idle（InProgress では no-op）
- `confirmDeletion`: ConfirmFinal → InProgress → Success(Hidden) or Error

退会フロー中はログアウト操作を `disabled` にする（Req 3.3）。Composable 上では
`isInFlight()` helper で判定。

### 6. close() と deletionJob の関係

シートを close（バック / スクリム / ドラッグ下げ）した場合の deletionJob は明示的に
キャンセルしない。理由:

- 進行中の DELETE 要求はサーバー側のトランザクションを完了させるべき（途中キャンセル
  すると整合性が損なわれる）
- ViewModel の生存期間中に launch されたコルーチンは background で完了し、Hidden 状態の
  ためコールバックで `mid != null` チェックを行って UI 反映をスキップする

ただし、確認段（ConfirmExplanation / ConfirmFinal）で close した場合は単に Hidden に戻り、
次に open しても DeletionState は Idle として復元される（Visible 状態の data class 値が
新規構築されるため）。

## Requirement ID → テスト対応表

| Req ID | テストクラス | テスト名 |
|---|---|---|
| 1.1 (シート表示中 退会 UI 要素) | AccountSheetViewModelTest | `Issue51 Req 1_1 初期 deletion 状態は Idle`（UI 統合: AccountSheetDeleteSection が常時 Compose 階層に存在） |
| 1.2 (破壊的視覚表現) | （UI 実装直接検証 / `MaterialTheme.colorScheme.error` 採用 = コード review 観点） | — |
| 1.3 (押下で説明ダイアログ) | AccountSheetViewModelTest | `Issue51 Req 1_3 startDeletion で ConfirmExplanation に遷移する` / `Issue51 Req 1_3 Hidden 状態での startDeletion は no-op` / `Issue51 Req 1_3 ログアウト進行中は startDeletion を受け付けない` |
| 1.4 (確認段中は DELETE 送らず) | AccountSheetViewModelTest | `Issue51 Req 1_4 startDeletion 単体では Coordinator perform は呼ばれない` / `Issue51 Req 1_4 ConfirmExplanation 状態で confirmDeletion を呼んでも Coordinator は呼ばれない` |
| 2.1 (説明ダイアログの提示内容) | （UI 実装の strings.xml 文言 = `account_sheet_delete_confirm_message` で全購読・既読/スター削除・取消不可を明示） | — |
| 2.2 (説明段の次へ進む / キャンセル両方提示) | AccountSheetViewModelTest | `Issue51 Req 2_3 proceedToFinalConfirm で ConfirmFinal に遷移する` / `Issue51 Req 2_5 1段目で cancelDeletion すると Idle に戻り Coordinator perform は呼ばれない` |
| 2.3 (説明段 次へ進む → 最終確認段) | AccountSheetViewModelTest | `Issue51 Req 2_3 proceedToFinalConfirm で ConfirmFinal に遷移する` |
| 2.4 (最終確認段の退会実行 / キャンセル両方提示) | AccountSheetViewModelTest | `Issue51 Req 2_5 2段目で cancelDeletion すると Idle に戻り Coordinator perform は呼ばれない` / `Issue51 Req 2_6 confirmDeletion で Coordinator perform が 1 回呼ばれる` |
| 2.5 (いずれかでキャンセル → DELETE 送らず Idle へ) | AccountSheetViewModelTest | `Issue51 Req 2_5 1段目で cancelDeletion ...` / `Issue51 Req 2_5 2段目で cancelDeletion ...` |
| 2.6 (最終段確定 → DELETE 1 回送信) | AccountSheetViewModelTest | `Issue51 Req 2_6 confirmDeletion で Coordinator perform が 1 回呼ばれる` |
| 2.6 (DELETE /api/users/me HTTP 契約) | UserRepositoryImplTest | `Issue51 Req 2-6 deleteMe issues DELETE to api users me` |
| 2.6 (revoke を呼ばない) | AccountDeletionCoordinatorTest | `Req 2_6 perform は revoke を呼ばない_アカウント自体が消えるため不要` |
| 3.1 (送信中の視覚表現) | AccountSheetViewModelTest | `Issue51 Req 3_1 confirmDeletion 中は InProgress 状態である` |
| 3.2 (退会実行ボタンの再受付不可) | AccountSheetViewModelTest | `Issue51 Req 3_2 InProgress 中の再 confirmDeletion は無視される` / `Issue51 Req 3_2 InProgress 中の cancelDeletion は無視される` |
| 3.3 (ログアウト操作の再受付不可) | AccountSheetViewModelTest | `Issue51 Req 3_3 退会フロー中は logout 操作が受付不可になる` |
| 4.1 (token 消去) | AccountDeletionCoordinatorTest | `Req 4_1 成功で TokenStore は空になる` |
| 4.2 (ユーザースコープキャッシュ初期化) | AccountDeletionCoordinatorTest | `Req 4_2 成功で ItemStateStore overlay が空になる` |
| 4.3 (SessionState LoggedOut 遷移) | AccountDeletionCoordinatorTest | `Req 4_3 成功で observeIsAuthenticated が false に遷移する` |
| 4.4 (App Shell ログイン画面描画) | 既存 AppShellViewModelTest / AuthRepositorySessionStateProviderTest が `observeIsAuthenticated = false → LoggedOut` 経路を担保（本 Issue で経路を新規追加していないため流用）。Coordinator 側の `observeIsAuthenticated` 反転で間接検証 | — |
| 4.5 (シート閉じる) | AccountSheetViewModelTest | `Issue51 Req 4_1 4_5 confirmDeletion 成功で Hidden に戻り cachedUser が破棄される` |
| 5.1 (サーバーエラーで token 維持) | AccountDeletionCoordinatorTest | `Req 5_1 5_2 サーバーエラーで TokenStore は維持される_観測 isAuthenticated は true のまま` / `Req 5_1 サーバーエラーでも ItemStateStore overlay は維持される` |
| 5.2 (サーバーエラーで LoggedIn 維持) | AccountDeletionCoordinatorTest | `Req 5_1 5_2 サーバーエラーで TokenStore は維持される_観測 isAuthenticated は true のまま` |
| 5.3 (ネットワーク失敗で token / state 維持) | AccountDeletionCoordinatorTest | `Req 5_3 ネットワーク失敗で TokenStore と SessionState は維持される` / UserRepositoryImplTest `Issue51 Req 5-3 deleteMe network failure surfaces FeedmanException with NETWORK_ERROR` |
| 5.4 (失敗エラーメッセージ表示) | AccountSheetViewModelTest | `Issue51 Req 5_1 5_4 confirmDeletion 失敗で Error 状態に遷移し message を保持する` / AccountDeletionCoordinatorTest `Req 5_4 errorMessage 空のサーバーエラーは code 別フォールバック文言を採用する` |
| 5.5 (再度退会開始可能) | AccountSheetViewModelTest | `Issue51 Req 5_5 失敗後に startDeletion で再度二段確認をやり直せる` / AccountDeletionCoordinatorTest `Req 5_5 失敗後に再 perform で成功する_リエントランシ` |
| NFR 1.1 (1 秒以内に進行中表現) | AccountSheetViewModelTest | `Issue51 Req 3_1 confirmDeletion 中は InProgress 状態である`（同期的に状態遷移 → 描画は次フレーム） |
| NFR 1.2 (30 秒以内にネットワーク失敗エラー表示) | （Retrofit/OkHttp デフォルトタイムアウトに依存。既存 FeedmanException 経路で観測可能 — UserRepositoryImplTest のネットワーク失敗テストで失敗確定 → 即時 Error 遷移を保証） | — |
| NFR 2.1 (token をログに出さない) | （明示的なログ出力を行わない実装 = コード review 観点。AccountDeletionCoordinator / UserRepositoryImpl / ViewModel で `Log.*` 呼び出し無し） | — |
| NFR 2.2 (成功/失敗で挙動取り違えない) | AccountDeletionCoordinatorTest | 成功テスト群（4_1 / 4_2 / 4_3）と失敗テスト群（5_1 / 5_2 / 5_3）で対比的に検証 |
| NFR 3.1 (email 等識別情報をログに含めない) | （明示的なログ出力を行わない実装 = コード review 観点） | — |

## 補足ノート

### Feature Flag Protocol

対象 repo の CLAUDE.md は `**採否**: opt-out` のため、本 Issue は通常の単一実装パスで
実装した（旧パス温存 / 両系統テスト / flag 名宣言は不要）。

### 追加依存

なし（既存の Retrofit / OkHttp / Material 3 AlertDialog / Hilt の範囲で実装可能）。

### 確認事項（PR レビュワーへ）

- **退会成功直後の補助メッセージ**: Issue Open Questions の通り Out of Scope 準拠で実装。
  必要であれば別 Issue で扱う（design/SPEC.md §5.7 にも補助メッセージは無い）。
- **退会失敗時の文言**: サーバーエラー時とネットワーク失敗時を区別せず、`FeedmanException.errorMessage`
  → code 別フォールバックの順で解決。既存の AccountSheetViewModel と同一の流儀。
- **AuthRepositoryImpl 直接受け取り**: `AccountDeletionCoordinatorImpl` が
  `AuthRepository` 抽象ではなく実装型 `AuthRepositoryImpl` を受け取る設計について。
  抽象に `refreshAuthenticatedState` を昇格させる選択肢もあったが、本メソッドは内部初期化用途
  （AuthRepository.kt KDoc にもそう書かれている）であり、公開 IF に出さない方が責務境界が
  明確と判断した。LogoutCoordinatorImpl が `SubscriptionRepositoryImpl` を実装型で受ける
  既存パターンに整合させた。
- **DELETE 時の `Cache-Control` 等のヘッダ**: SPEC.md §5.7 が DELETE /api/users/me の
  詳細 HTTP ヘッダ要件を規定していないため、既存 `FeedmanApi` 経路の標準ヘッダのみで
  実装した。サーバー側で追加ヘッダ要件があれば別 Issue で扱う。

### 派生タスク候補

- 退会成功後のスナックバー表示（Open Questions の通り別 Issue 化）
- 退会フローへの instrumented UI test（Compose UI Test。本 Issue は JVM 単体テストで
  ViewModel + Coordinator 経路を検証）

## ビルド結果

- `./gradlew build` 成功（lint / unit tests / build すべて green）。
- 追加・修正単体テスト 36 件（UserRepositoryImplTest +4 / AccountDeletionCoordinatorTest 新規 10 /
  AccountSheetViewModelTest +16 + helper +1）すべて green。

STATUS: complete
