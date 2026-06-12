# Requirements Document

## Introduction

本機能は、保存済みトークンを使ってアプリ起動時に認証セッションを復元し、全画面で観測可能な
セッション状態 (`Restoring` / `LoggedIn` / `LoggedOut`) を 1 か所に集約する。これまでの暫定実装
（Issue #21〜#23 で導入された `LoggedOut` / `LoggedIn` の 2 状態 + `AuthRepository` 連動の
`SessionStateProvider`）では、起動時のトークン復元と「復元中」ローディング表示が表現できず、
保存済みトークンがあっても起動直後は一瞬ログイン画面が見えてしまう問題があった。本 Issue で
`Restoring` 状態を加えた 3 状態に拡張し、起動シーケンス・refresh 失敗時のフォールバック・
401 起因の自動ログアウトを App Shell から一貫して扱えるようにする。

## Requirements

### Requirement 1: 起動時のセッション復元シーケンス

**Objective:** As an アプリ利用者, I want アプリ起動時に前回のログイン状態が自動復元されること, so that 毎回ログイン操作を繰り返さずに記事閲覧を再開できる

#### Acceptance Criteria

1. When アプリが起動する, the Session State Module shall 初期状態として Restoring を発行する
2. When 起動時に保存済みトークンが存在しかつ復元処理が成功した場合, the Session State Module shall Restoring から LoggedIn に遷移する
3. When 起動時に保存済みトークンが存在しない場合, the Session State Module shall Restoring から LoggedOut に遷移する
4. If 起動時の復元処理で認証切れ (refresh 失敗による再認証要求) が発生した場合, the Session State Module shall 保存済みトークンを消去した上で Restoring から LoggedOut に遷移する
5. If 起動時の復元処理がネットワーク失敗で完了できない場合, the Session State Module shall 保存済みトークンを保持したまま Restoring から LoggedIn に遷移する

### Requirement 2: 復元中の UI 表示 (ちらつき回避)

**Objective:** As an アプリ利用者, I want 起動直後の不確定な状態でログイン画面と認証済み画面がちらつかないこと, so that 視覚的に安定したアプリ起動体験を得られる

#### Acceptance Criteria

1. While セッション状態が Restoring である間, the App Shell shall ローディング表示 (スプラッシュ相当の中間画面) を描画する
2. While セッション状態が Restoring である間, the App Shell shall ログイン画面を描画しない
3. While セッション状態が Restoring である間, the App Shell shall 認証済みシェル (ドロワー付きシェル) を描画しない

### Requirement 3: 認証済みセッション確立後の画面遷移

**Objective:** As an アプリ利用者, I want 保存済みトークンで起動した直後に再ログイン無しで認証済みシェルに到達すること, so that 起動から最短で記事一覧を閲覧開始できる

#### Acceptance Criteria

1. When セッション状態が LoggedIn に遷移した, the App Shell shall 認証済みシェル (ドロワー付きシェル) を描画する
2. When セッション状態が LoggedIn に遷移した, the App Shell shall ログイン画面およびローディング表示を描画しない
3. When 起動時の復元結果として LoggedIn が確定した, the App Shell shall ユーザーに再ログイン操作を要求しない

### Requirement 4: 未認証セッションへの遷移

**Objective:** As an アプリ利用者, I want どの画面を表示中であっても認証が切れた瞬間に自動的にログイン画面へ戻ること, so that 認証切れに気づかないまま操作を続けて失敗する状況を避けられる

#### Acceptance Criteria

1. When セッション状態が LoggedOut に遷移した, the App Shell shall ログイン画面を描画する
2. When セッション状態が LoggedOut に遷移した, the App Shell shall 認証済みシェルおよびローディング表示を描画しない
3. If 認証済みシェル表示中に 401 起因の refresh 失敗で認証が切れた場合, the Session State Module shall 保存済みトークンを消去した上で LoggedIn から LoggedOut に遷移する
4. When 認証済みシェル上で開いていたボトムシート (記事詳細・アカウント・購読設定・フィード登録) が表示中に LoggedOut へ遷移した, the App Shell shall シート表示を破棄してログイン画面に戻る

### Requirement 5: 全画面共通のセッション状態観測

**Objective:** As a 後続機能の開発者, I want 認証状態の参照元が単一であること, so that 画面ごとに認証分岐ロジックを再実装せずに済む

#### Acceptance Criteria

1. The Session State Module shall Restoring / LoggedIn / LoggedOut の 3 状態を区別して公開する
2. The Session State Module shall App Shell および全画面が観測可能な単一のセッション状態ソースを提供する
3. When セッション状態が遷移した, the Session State Module shall 観測中のすべての購読者に新しい状態を通知する

## Non-Functional Requirements

### NFR 1: 起動時応答性

1. While 起動時の復元処理が継続している間, the Session State Module shall 最大 5 秒以内に Restoring から LoggedIn または LoggedOut のいずれかへ遷移する (ネットワーク失敗時のフォールバックを含む)
2. The Session State Module shall 起動時に保存済みトークンが存在しないことを確認した場合、ネットワーク I/O を発生させずに LoggedOut へ遷移する

### NFR 2: セッション状態の単一ソース

1. The Session State Module shall 同一プロセス内で観測される現在のセッション状態が常に一意になるよう、単一インスタンスとして公開される
2. While セッション状態を観測する複数の購読者が存在する間, the Session State Module shall すべての購読者に同一の現在値を返す

### NFR 3: トークン破棄の確実性

1. If 起動時 refresh が認証切れと判定された場合, the Session State Module shall LoggedOut 通知前に保存済みトークン (access / refresh) を消去する
2. If 認証済みセッション中に 401 起因の認証切れが確定した場合, the Session State Module shall LoggedOut 通知前に保存済みトークン (access / refresh) を消去する

## Out of Scope

- ログアウト操作の UI 提供 (Issue #50 で扱う)
- ログイン画面そのものの実装 (Issue #23 で実装済み)
- 401 自動 refresh + リクエストリプレイの HTTP 層実装 (Issue #22 で実装済み)
- トークン保管庫 (EncryptedSharedPreferences) の実装 (Issue #20 で実装済み)
- 退会・アカウント削除の UI 経路 (Issue #49 などで扱う)
- 「Restoring 中に明示的なネットワーク要求 (起動同期) を行う」設計 (次フェーズ)

## Open Questions

- なし
