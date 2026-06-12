# Requirements Document

## Introduction

Feedman Android のネイティブ OAuth フロー（SERVER.md §1.2）では、Custom Tabs でサーバー側のログイン URL を開き、
Google 認可後にカスタムスキーム `feedman://auth/callback?auth_code=<one-time-code>` で auth_code を受領し、
本トークンへの交換を完了することでログインが完了する。本機能は、未ログイン状態のユーザーに表示されるログイン
画面の起動操作から、Custom Tabs での認可、ディープリンク受領、AuthRepository.exchange による本トークン交換、
認証済みシェルへの遷移までを 1 本のユーザー動線として結線する責務を担う。

前提となる AuthRepository.exchange / refresh / revoke の契約（#21）および Material 3 テーマトークン（#25）は
merge 済みで、本 Issue ではログイン UI + Custom Tabs 起動 + ディープリンク受領 + 失敗時の再試行ガードのみを
扱う。起動時のトークン復元（#24）およびログアウト操作（#50）は別 Issue で扱う。

## Requirements

### Requirement 1: 未ログイン時のログイン画面表示

**Objective:** As a 未ログイン状態の Feedman Android ユーザー, I want アプリ起動直後に Google でログインするための導線が見える画面が表示されること, so that 最初に何をすればアプリを使い始められるかが一目で分かる

#### Acceptance Criteria

1. While セッション状態が未ログインである, when ユーザーがアプリを起動する, the Login Screen shall サービス名・短い案内文・Google でログインするためのボタンを表示する
2. While セッション状態が未ログインである, the Login Screen shall 認証済みシェル（タイムライン等の機能画面）を背後・前面のどちらにも露出させない
3. The Login Screen shall 端末のライト／ダークテーマ設定に追従した配色で描画する

### Requirement 2: Google ログインボタン押下による Custom Tabs 起動

**Objective:** As a 未ログイン状態の Feedman Android ユーザー, I want Google ログインボタンを押すと Google の認可画面が即座に開くこと, so that 余計な遷移なしに Google アカウントでのログインを開始できる

#### Acceptance Criteria

1. When ユーザーが Login Screen の Google ログインボタンを押下する, the Login Screen shall サーバーのネイティブ OAuth 開始エンドポイント（`/auth/google/login`）を Custom Tabs で開く
2. When ユーザーが Google ログインボタンを押下する, the Login Screen shall PKCE 用の code_verifier を新規生成し対応する code_challenge を S256 方式でクエリパラメータとして Custom Tabs に渡す URL に含める
3. When ユーザーが Google ログインボタンを押下する, the Login Screen shall Custom Tabs に渡す URL にネイティブフローであることを示すクエリパラメータ（`flow=native`）を含める
4. When ユーザーが Google ログインボタンを押下する, the Login Screen shall 生成した code_verifier を後続の token 交換まで保持し、Custom Tabs を閉じた後やアプリプロセス再生成後にも参照できる状態にする
5. While Custom Tabs 起動処理が進行中である, the Login Screen shall Google ログインボタンの連打による Custom Tabs の二重起動を抑止する

### Requirement 3: ディープリンクによる auth_code 受領と本トークン交換

**Objective:** As a 未ログイン状態の Feedman Android ユーザー, I want Google 認可が完了して Feedman に戻ってきたら、追加操作なしでログインが完了して機能画面に到達できること, so that 認可後の体感としてシームレスに利用開始できる

#### Acceptance Criteria

1. When ユーザーが `feedman://auth/callback?auth_code=<value>` 形式のディープリンクで Feedman Android に戻る, the Main Activity shall そのインテントを受領し auth_code を抽出する
2. When auth_code が抽出される, the Login Flow shall 保持している code_verifier と組み合わせて AuthRepository.exchange を呼び出す
3. When AuthRepository.exchange が成功する, the Login Flow shall セッション状態をログイン中に遷移させ、認証済みシェルへ画面を切り替える
4. When AuthRepository.exchange が成功する, the Login Flow shall 保持していた code_verifier と一時状態（進行中フラグ等）を破棄する
5. While AuthRepository.exchange の応答待ちである, the Login Screen shall 処理中であることが分かる表示を提示し、Google ログインボタンの再押下による交換の多重発行を抑止する

### Requirement 4: 交換失敗時のエラー表示と再試行

**Objective:** As a 未ログイン状態の Feedman Android ユーザー, I want トークン交換に失敗した場合に原因の概要が分かり、アプリを再起動せずにもう一度ログインを試せること, so that 一時的な失敗で詰まずに済む

#### Acceptance Criteria

1. If AuthRepository.exchange が INVALID_GRANT を含む業務エラーで失敗する, the Login Screen shall ユーザーが読めるエラーメッセージを表示し、未ログイン状態のままログイン画面に留まる
2. If AuthRepository.exchange がネットワーク失敗で完了しない, the Login Screen shall ネットワーク失敗である旨を示すエラーメッセージを表示し、未ログイン状態のままログイン画面に留まる
3. When 交換失敗のエラーメッセージが表示されている状態でユーザーが Google ログインボタンを再度押下する, the Login Screen shall 前回のエラーメッセージを消去し、新しい code_verifier を生成してログインフローを最初からやり直す
4. If AuthRepository.exchange が失敗する, the Login Flow shall 前回保持していた code_verifier をログインフローの再開始まで残さない

### Requirement 5: Custom Tabs を完了せず閉じた場合の挙動

**Objective:** As a 未ログイン状態の Feedman Android ユーザー, I want 認可画面を途中で閉じた場合にエラー表示で混乱せず、もう一度ログインボタンを押せばやり直せること, so that 誤操作や気が変わった場合にも自然に戻れる

#### Acceptance Criteria

1. When ユーザーが Custom Tabs を完了せず閉じて Feedman Android に戻る（ディープリンクが届かない）, the Login Screen shall エラーメッセージを表示せず未ログイン状態のままログイン画面を表示する
2. When ユーザーが Custom Tabs を閉じた後に再度 Google ログインボタンを押下する, the Login Screen shall 新しい code_verifier を生成して Custom Tabs を再起動する
3. If ディープリンクのクエリに auth_code が含まれていない, the Main Activity shall AuthRepository.exchange を呼び出さず、ログイン画面を未ログイン状態のまま維持する

## Non-Functional Requirements

### NFR 1: PKCE / code_verifier の機密保持

1. The Login Flow shall code_verifier をログ出力・クラッシュレポート・解析イベントに含めない
2. The Login Flow shall code_verifier を平文の永続ストアに長期保存せず、ログインフロー（ボタン押下から交換完了または再開始まで）の範囲内でのみ参照可能な状態に保つ
3. The Login Flow shall AuthRepository.exchange の成否（成功・業務エラー・ネットワーク失敗）が確定した時点で保持していた code_verifier を破棄する

### NFR 2: ディープリンク受領の安全性

1. The Main Activity shall `feedman://auth/callback` 以外のスキーム・ホスト・パスで起動されたインテントを認証フローの入力として扱わない
2. If auth_code を含まないコールバックインテントが届く, the Main Activity shall AuthRepository.exchange を呼び出さない

### NFR 3: 認可フロー時間の上限

1. While ログインフローが Custom Tabs の起動から auth_code 受領までを待機している, the Login Flow shall アプリプロセスが生存している間は待機状態を維持する
2. While AuthRepository.exchange の応答を待機している, the Login Screen shall 30 秒以内に成功・失敗いずれかの結果を画面上に反映する（タイムアウトとして失敗扱いにする場合を含む）

## Out of Scope

- アプリ起動時に保存済みトークンからログイン状態を復元する処理（#24 で扱う）
- ログアウト操作および revoke API 呼び出し（#50 で扱う）
- AuthRepository.exchange / refresh / revoke の内部実装と契約（#21 で完了済み）
- 401 応答の自動 refresh + リクエストリプレイ（#22 で扱う）
- 退会・アカウント削除フロー（別 Issue）
- 複数 Google アカウント切替・アカウント選択 UI（v1 スコープ外）
- ログイン画面の利用規約・プライバシーポリシー本文の表示や個別画面遷移（文言固定表示までを本 Issue の範囲とする）

## Open Questions

なし（SERVER.md §1.2 のネイティブ OAuth フロー、GRAND-DESIGN §5.3 の認証フロー、#21 で確定済みの
AuthRepository.exchange 契約により必要な情報は揃っている）
