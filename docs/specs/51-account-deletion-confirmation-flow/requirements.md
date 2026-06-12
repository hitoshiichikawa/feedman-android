# Requirements Document

## Introduction

Feedman Android では Issue #49 でアカウントシート（現在ユーザー表示）が、Issue #50 で
ログアウト動線（revoke + ローカルクレデンシャル消去 + ユーザースコープキャッシュリセット +
ログイン画面復帰）が merge 済みである。本 Issue ではアカウントシート上の「退会」操作を扱い、
SPEC.md §5.7 に定義された `DELETE /api/users/me`（全購読・既読/スター状態が削除される
不可逆な退会）を、ユーザーの誤操作を防ぐ二段確認フローを介して実行する責務を扱う。

退会成功時の事後処理（ローカルクレデンシャル消去 + ログイン画面復帰）は Issue #50 で導入
された LogoutCoordinator 相当の機構を流用する想定で、本要件は「アカウントシートに退会導線を
配置する」「説明ダイアログと最終確認の二段ゲートを経て初めてサーバーへ削除要求を送る」
「成功時はセッションを破棄してログイン画面へ戻す」「失敗時はアカウントを維持してエラーを
ユーザーに通知する」という観測可能な挙動を規定する。

## Requirements

### Requirement 1: アカウントシートからの退会導線

**Objective:** As an アプリ利用者, I want アカウントシート上に退会操作が用意されている, so that 自発的にアカウント削除を開始できる

#### Acceptance Criteria

1. While アカウントシートが表示されている間, the Account Sheet shall 退会操作のための UI 要素を表示する
2. The Account Sheet shall 退会操作の UI 要素を破壊的操作と識別できる視覚表現（ログアウトとは区別される警告色等）で表示する
3. When ユーザーが退会操作の UI 要素を押下した, the Account Sheet shall 退会説明ダイアログ（第 1 段）を表示する
4. While 退会説明ダイアログまたは最終確認ダイアログが表示されている間, the Account Deletion Flow shall サーバーへの削除要求（`DELETE /api/users/me`）を送信しない

### Requirement 2: 二段確認ゲート

**Objective:** As an アプリ利用者, I want 退会前に取り返しのつかない結果を明示され、最終確認をもう 1 段挟まれる, so that 誤操作で全データを失う事故を防げる

#### Acceptance Criteria

1. While 退会説明ダイアログ（第 1 段）が表示されている間, the Account Deletion Flow shall 「全購読が削除される」「既読・スター状態が削除される」「操作は取り消せない」旨をユーザーに提示する
2. While 退会説明ダイアログ（第 1 段）が表示されている間, the Account Deletion Flow shall 「次へ進む（最終確認へ）」操作と「キャンセル」操作の両方を提示する
3. When ユーザーが退会説明ダイアログ（第 1 段）で「次へ進む」操作を選択した, the Account Deletion Flow shall 最終確認ダイアログ（第 2 段）を表示する
4. While 最終確認ダイアログ（第 2 段）が表示されている間, the Account Deletion Flow shall 「退会を実行する」操作と「キャンセル」操作の両方を提示する
5. If ユーザーが退会説明ダイアログまたは最終確認ダイアログのいずれかでキャンセル操作を選択した, the Account Deletion Flow shall サーバーへの削除要求を送信せずに二段確認フローを終了し、アカウントシート表示状態へ戻す
6. When ユーザーが最終確認ダイアログ（第 2 段）で「退会を実行する」操作を選択した, the Account Deletion Flow shall サーバーへの削除要求（`DELETE /api/users/me`）を 1 回送信する

### Requirement 3: 退会処理の進行状態とリエントランシ防止

**Objective:** As an アプリ利用者, I want 退会送信中であることが画面でわかり、二重送信されない, so that 進行中に追加操作して状態が不整合になる不安なく待てる

#### Acceptance Criteria

1. While サーバーへの削除要求が送信中である間, the Account Deletion Flow shall 進行中であることがユーザーに伝わる視覚表現（ローディングインジケータ等）を提示する
2. While サーバーへの削除要求が送信中である間, the Account Deletion Flow shall 「退会を実行する」操作を再受付不可（disabled）の状態にして二重送信を抑止する
3. While サーバーへの削除要求が送信中である間, the Account Deletion Flow shall アカウントシート上のログアウト操作を再受付不可（disabled）の状態にする

### Requirement 4: 成功時のクレデンシャル消去とログイン画面復帰

**Objective:** As an アプリ利用者, I want 退会に成功したら端末からアカウント情報が消えてログイン画面に戻る, so that 退会済みの状態が画面上も端末上も即座に反映される

#### Acceptance Criteria

1. When サーバーへの削除要求が成功応答で完了した, the Account Deletion Flow shall 端末上の access token と refresh token を消去した状態へ確定する
2. When サーバーへの削除要求が成功応答で完了した, the Account Deletion Flow shall ユーザースコープのキャッシュ状態（既読・スター等を含むユーザー単位インメモリ／プロセス内キャッシュ）を初期化する
3. When サーバーへの削除要求が成功応答で完了した, the Session State Module shall セッション状態を LoggedOut へ遷移させる
4. When セッション状態が LoggedOut へ遷移した, the App Shell shall ログイン画面を描画する
5. When サーバーへの削除要求が成功応答で完了した, the Account Sheet shall アカウントシートと退会確認ダイアログを閉じる

### Requirement 5: 失敗時のアカウント維持とエラー通知

**Objective:** As an アプリ利用者, I want 退会送信が失敗したらアカウントがそのまま使えて、失敗内容がわかる, so that 通信失敗等で勝手にログアウトされる事故を避けつつ再試行できる

#### Acceptance Criteria

1. If サーバーへの削除要求がサーバーエラー応答で完了した, the Account Deletion Flow shall 端末上の access token と refresh token を消去せず維持する
2. If サーバーへの削除要求がサーバーエラー応答で完了した, the Session State Module shall セッション状態を LoggedIn のまま維持する
3. If サーバーへの削除要求がネットワーク失敗で完了した, the Account Deletion Flow shall 端末上の access token と refresh token を消去せず維持し、セッション状態を LoggedIn のまま維持する
4. If サーバーへの削除要求が失敗（サーバーエラーまたはネットワーク失敗）で完了した, the Account Deletion Flow shall ユーザーに失敗を示すエラーメッセージを表示する
5. If サーバーへの削除要求が失敗で完了した, the Account Deletion Flow shall ユーザーが再度退会操作を開始できる状態（二段確認をやり直せる状態）に戻す

## Non-Functional Requirements

### NFR 1: 観測可能なパフォーマンス・体感

1. When ユーザーが最終確認ダイアログで「退会を実行する」操作を選択した, the Account Deletion Flow shall 1 秒以内に送信中であることがわかる視覚表現（ローディングインジケータ等）を画面に提示する
2. When サーバーへの削除要求がネットワークタイムアウトに達した, the Account Deletion Flow shall 最大 30 秒以内に失敗エラーメッセージを画面に提示する

### NFR 2: クレデンシャル取り扱いの安全性

1. While 退会処理が進行している間, the Account Deletion Flow shall access token / refresh token をログ出力・クラッシュレポート本文に含めない
2. The Account Deletion Flow shall 退会成功時にユーザースコープのキャッシュ状態を端末から消去し、退会失敗時には維持する（成功時と失敗時の挙動を取り違えない）

### NFR 3: プライバシー

1. The Account Deletion Flow shall 退会処理の進行・結果に関するログに、対象ユーザーの email 等の識別情報を含めない

## Out of Scope

- プロフィール編集（氏名・メールアドレス変更等）
- 退会後の取り消し・復活機能（サーバー側でアカウントは完全削除される前提）
- サーバー側で実行される購読・記事状態・refresh_tokens / auth_codes の物理削除（サーバー責務）
- 退会失敗時の自動リトライ（ユーザーが明示的に再度退会操作を開始する運用とする）
- 退会成功後のログイン画面で表示する補助メッセージ（「退会が完了しました」等のスナックバー）の有無
- 他デバイスでログイン中のセッションの即時失効通知（サーバー側の責務）
- ログアウト導線（Issue #50 で実装済み）と現在ユーザー表示（Issue #49 で実装済み）

## Open Questions

- 退会失敗時のエラーメッセージ文言（サーバーエラー時とネットワーク失敗時を区別するか、共通文言とするか）は文言レビュー時に確定する。要件としては「失敗を示すメッセージを表示する」という観測可能挙動に限定する。
- 退会成功直後のログイン画面で「退会が完了しました」等の補助メッセージを併出するかは UX 判断として未確定。Out of Scope として本 Issue では扱わず、必要であれば別 Issue で扱う。
