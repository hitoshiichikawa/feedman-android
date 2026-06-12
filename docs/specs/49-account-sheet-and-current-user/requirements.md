# Requirements Document

## Introduction

ドロワーフッタ「アカウント」導線から開くアカウントシートで、現在ログイン中のユーザー情報
（メールアドレス等）を表示する。SPEC §5.7 では `GET /auth/me` の表示が要件として定義されて
おり、プロトタイプ（`design/mobile/fm-sheets.jsx` の `FMAccountSheet`）ではダミー文字列
（`you@example.com`）でプレースホルダ表示されている領域を、実 API 取得結果で置き換える。

本 Issue ではアカウントシートを開いた際の現在ユーザー取得・表示と、その取得処理に伴う
ローディング／エラー／認証失効時の挙動だけを対象とする。ログアウトボタン（#50）と
退会フロー（#51）はそれぞれ別 Issue として扱うため本 Issue では実装しない。なお
セッション状態のソースは Issue #29 で導入された `SessionState`（現状は mockMode 連動の
暫定実装で、本格化は #24 系）を経由するが、本要件は観測可能挙動（「認証失効時にログイン
画面へ戻る」）として記述する。

## Requirements

### Requirement 1: アカウントシートの起動と現在ユーザー取得

**Objective:** As an アプリ利用者, I want アカウントシートを開いたときに自分のアカウント情報を確認できる, so that 現在どのアカウントでログインしているかを把握できる

#### Acceptance Criteria

1. When ユーザーがドロワーフッタの「アカウント」項目をタップしたとき, the Account Sheet shall アカウントシートをボトムシートとして表示する
2. When アカウントシートが開いたとき, the Account Sheet shall 現在ユーザー取得処理を 1 回開始する
3. While アカウントシートが開いている間, the Account Sheet shall プロトタイプ `FMAccountSheet`（`design/mobile/fm-sheets.jsx`）と同等の構成（ユーザー領域 / 区切り線 / 閉じるボタン）でユーザー情報領域を描画する
4. When ユーザーが同一セッション内でアカウントシートを開いて閉じる操作を繰り返したとき, the Account Sheet shall 既に取得済みのユーザー情報を再利用して再フェッチを行わない

### Requirement 2: 現在ユーザー情報の表示

**Objective:** As an アプリ利用者, I want 取得した自分のメールアドレスをアカウントシートで確認できる, so that 別アカウントとの取り違えを防げる

#### Acceptance Criteria

1. When 現在ユーザー取得が成功し email が空でないとき, the Account Sheet shall ユーザー領域に当該 email を表示する
2. When 現在ユーザー取得が成功し email が空文字または欠落しているとき, the Account Sheet shall ユーザー領域に「メールアドレス未設定」を表す代替文字列を表示する
3. The Account Sheet shall ユーザー領域の見出し行にプロトタイプと同じプレースホルダラベル（`You`）相当の固定文字列を表示する

### Requirement 3: ローディング状態

**Objective:** As an アプリ利用者, I want 取得中であることが視覚的にわかる, so that 何も表示されない無反応状態と勘違いせずに待てる

#### Acceptance Criteria

1. While 現在ユーザー取得が進行中である間, the Account Sheet shall ローディングインジケータをユーザー領域に表示する
2. While 現在ユーザー取得が進行中である間, the Account Sheet shall 未確定のメールアドレスや空の代替文字列を確定値として表示しない
3. When 現在ユーザー取得が完了したとき, the Account Sheet shall ローディングインジケータを取り下げ、成功時は Requirement 2 の表示に、失敗時は Requirement 4 の表示に切り替える

### Requirement 4: 取得エラー（認証失効以外）

**Objective:** As an アプリ利用者, I want 取得に失敗した理由がわかり、再試行できる, so that 一時的な通信不良で操作不能にならない

#### Acceptance Criteria

1. If 現在ユーザー取得がネットワーク障害またはサーバーエラー（認証失効以外）で失敗したとき, the Account Sheet shall ユーザー領域にエラーメッセージと再試行手段（再試行ボタン）を表示する
2. When ユーザーが再試行手段を操作したとき, the Account Sheet shall 現在ユーザー取得処理を再度実行する
3. If 再試行が成功したとき, the Account Sheet shall エラーメッセージを取り下げ、Requirement 2 の表示に切り替える
4. While エラー表示中である間, the Account Sheet shall ユーザー領域以外（閉じるボタン）の操作を継続して受け付ける

### Requirement 5: 認証失効時の挙動

**Objective:** As an アプリ利用者, I want セッションが切れていたら自動的にログイン画面に戻る, so that 古い認証情報で操作を続けて混乱しない

#### Acceptance Criteria

1. If 現在ユーザー取得が認証エラー（セッション失効）で失敗したとき, the Account Sheet shall アカウントシートを閉じる
2. If 現在ユーザー取得が認証エラー（セッション失効）で失敗したとき, the App Shell shall ログイン画面を表示する状態へ遷移する
3. If 認証エラーが発生したとき, the Account Sheet shall 当該失敗内容を Requirement 4 の通常エラー表示には表示しない（重複表示を避け、ログイン画面遷移を優先する）

## Non-Functional Requirements

### NFR 1: 観測可能なパフォーマンス・体感

1. When アカウントシートが開かれたとき, the Account Sheet shall 1 秒以内にローディングインジケータを画面に提示する（取得自体が 1 秒以上かかってもユーザーが状態を把握できる）
2. The Account Sheet shall 表示中の email を含む画面要素を Talkback などのスクリーンリーダーから読み上げ可能な状態で提供する

### NFR 2: プライバシー

1. The Account Sheet shall 取得した現在ユーザー情報（email 等）をログ出力・クラッシュレポート本文に含めない
2. The Account Sheet shall ユーザー情報を端末永続ストレージへ平文で保存しない（プロセス内メモリで保持し、アプリ終了時に破棄される）

## Out of Scope

- ログアウトボタンの実装と `POST /auth/logout` 呼び出し（Issue #50）
- 退会フローと `DELETE /api/users/me` 呼び出し（Issue #51）
- email 以外の追加ユーザープロフィール項目（アバター画像・表示名等）の表示拡張
- セッション失効後のログイン画面側の Google 認証フロー本体（既存実装 / 別 Issue）
- `SessionState` 本格実装（mockMode 連動からトークン保管・リフレッシュ連動への置換は Issue #24 系）
- ドロワー内のユーザー領域（ヘッダ部）への現在ユーザー情報反映（本 Issue ではアカウントシート内表示のみ）

## Open Questions

- 認証エラー時にトーストやスナックバー等の補助フィードバック（例: 「セッションが切れました。再度ログインしてください」）を併出するかは UX 判断として未定。設計時に SPEC §6 の共通挙動と整合する形で決めるか、Issue コメントで人間に確認する。
