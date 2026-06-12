# Requirements Document

## Introduction

Feedman Android は Web 版と異なりブラウザオリジンを持たないため、SERVER.md §1 で定められた自前トークン
（アクセス JWT 15 分 + 不透明 refresh 30 日）方式で API を呼び出す必要がある。本機能は OAuth コールバックで
受領した `auth_code` を本トークンへ交換し、refresh のローテーションを反映し、ログアウト時に revoke を
要求し、保存済みトークンを全 API リクエストの `Authorization: Bearer` として付与する責務を AuthRepository に
集約する。これにより上位の UI / セッション状態管理（#22 / #23）が、トークン寿命やローテーションの詳細を
意識せずに認証済み API 呼び出しと「ログイン中 / 未ログイン」状態の遷移だけを扱えるようにする。

実装範囲は `core/auth`（AuthRepository / AuthInterceptor）/ `core/network` / `di` のみで、ログイン画面・
Custom Tabs 連携・401 自動リトライ（refresh + リプレイ）はそれぞれ #23 / #22 の別 Issue で扱う。前提となる
TokenStore（#17）/ JSON モデル（#19）/ HTTP クライアント基盤（#20）は既に merge 済みである。

## Requirements

### Requirement 1: auth_code から本トークンへの交換と永続化

**Objective:** As a Feedman Android user, I want OAuth コールバックで受領した一時 auth_code が確実に本トークンへ交換され、後続の API 呼び出しに使われる状態として保存されること, so that 1 度のログイン操作で以降の API 利用に必要な認証情報が端末に揃う

#### Acceptance Criteria

1. When the Authentication Module receives `exchange(authCode, codeVerifier)` and the server returns a success response, the Authentication Module shall 返却された access token / refresh token / token type / expires_in を Token Store に保存する
2. When the Authentication Module receives `exchange(authCode, codeVerifier)`, the Authentication Module shall リクエスト body に `auth_code` と `code_verifier` を含めて token 交換エンドポイントへ送信する
3. If the server returns an error response（INVALID_GRANT などのエラー code を含む）to the exchange request, the Authentication Module shall Token Store に何も書き込まずに当該エラーを呼び出し元へ伝搬する
4. If a network failure occurs during the exchange request, the Authentication Module shall Token Store の既存内容を変更せずに失敗を呼び出し元へ伝搬する

### Requirement 2: refresh によるアクセストークン再発行とローテーション保存

**Objective:** As a Feedman Android user, I want アクセストークン期限切れ後も再ログインなしに API 利用が継続できる一方で refresh トークンの再利用検知を妨げないこと, so that 30 日間のスライディング期間内で安全にセッションを継続できる

#### Acceptance Criteria

1. When the Authentication Module receives `refresh()` and Token Store に保存済みの refresh token が存在する, the Authentication Module shall その refresh token を refresh エンドポイントへ送信する
2. When the Authentication Module receives a success response from the refresh endpoint, the Authentication Module shall 返却された新しい access token / refresh token / expires_in で Token Store の値を上書き保存する
3. While a `refresh()` call is in flight, when 追加の `refresh()` 呼び出しが発生する, the Authentication Module shall 新規ネットワークリクエストを発行せず、進行中の呼び出しと同じ結果を返す
4. If the server returns an INVALID_REFRESH_TOKEN error to the refresh request, the Authentication Module shall Token Store から access token と refresh token を消去し、認証が必要な状態であることを呼び出し元から観測できるようにする
5. If a network failure occurs during the refresh request, the Authentication Module shall Token Store の内容を維持したまま失敗を呼び出し元へ伝搬する
6. If `refresh()` is called while Token Store に refresh token が保存されていない, the Authentication Module shall ネットワークリクエストを発行せず認証が必要な状態であることを呼び出し元から観測できるようにする

### Requirement 3: revoke によるサーバー失効依頼とローカルトークン消去

**Objective:** As a Feedman Android user, I want ログアウト操作の結果がサーバー側にも届くよう試みつつ、ネットワーク状況に関わらず端末上の認証情報が確実に消えること, so that 端末を手放す前にログアウトしておけば端末側にトークンが残らないことを保証できる

#### Acceptance Criteria

1. When the Authentication Module receives `revoke()` and Token Store に保存済みの refresh token が存在する, the Authentication Module shall その refresh token を revoke エンドポイントへ送信する
2. When the revoke request completes（成功・サーバーエラー・ネットワーク失敗のいずれであっても）, the Authentication Module shall Token Store から access token と refresh token を消去する
3. When the Authentication Module receives `revoke()` and Token Store に refresh token が存在しない, the Authentication Module shall ネットワークリクエストを発行せず Token Store の消去のみを実行する

### Requirement 4: 認証済み API への Bearer 付与

**Objective:** As a Feedman Android user, I want ログイン後に行う全 API 呼び出しが自動で認証ヘッダ付きで送信されること, so that 各画面の Repository が認証ヘッダの組み立てを意識せずに済む

#### Acceptance Criteria

1. While Token Store にアクセストークンが保存されている, when the Network Module が認証対象 API リクエストを送信する, the Network Module shall `Authorization` ヘッダに `Bearer <access token>` を付与する
2. While Token Store にアクセストークンが保存されていない, when the Network Module が認証対象 API リクエストを送信する, the Network Module shall `Authorization` ヘッダを付与せずにリクエストを送信する
3. Where 認証不要のリクエスト（token 交換・refresh エンドポイントへの呼び出し）, the Network Module shall `Authorization` ヘッダを付与しない

### Requirement 5: 現在ログイン中ユーザーの取得

**Objective:** As a Feedman Android user, I want ログイン状態のときに自分のアカウント情報がアプリから参照できること, so that アカウント画面や UI 表示でユーザー名やメールが表示できる

#### Acceptance Criteria

1. When the Authentication Module receives `currentUser()`, the Authentication Module shall 認証必須の me エンドポイントを呼び出し、レスポンスをアプリ内のユーザーモデルへ変換して呼び出し元へ返す
2. If the me エンドポイントが認証エラーを返す, the Authentication Module shall そのエラーを呼び出し元へ伝搬する（Token Store の消去は本機能の責務に含めない）

## Non-Functional Requirements

### NFR 1: トークン保管の機密性

1. The Authentication Module shall access token と refresh token を平文の永続ストアに書き出さない
2. If Token Store への保存が失敗する, the Authentication Module shall 部分的に書き込まれた状態が残らないように access token と refresh token を整合した状態（両方更新 or 両方据え置き）で終える

### NFR 2: 同時呼び出しの抑制と整合性

1. While 1 件の `refresh()` 呼び出しが進行中, the Authentication Module shall 同時に発生する追加の `refresh()` 呼び出しに対して新規ネットワークリクエストを発行しない（単一飛行）
2. When 並行する `refresh()` 呼び出しすべてが完了する, the Authentication Module shall いずれの呼び出し元にも同一の最終結果（成功 or 同じエラー）を返す

### NFR 3: 観測可能なログイン状態

1. The Authentication Module shall access token / refresh token の保存・消去が反映された後、呼び出し元が「ログイン中 / 未ログイン」を判定できる手段を提供する（具体的な公開形式は設計で定める）

## Out of Scope

- 401 応答に対する自動 refresh + リクエストリプレイ（#22 で扱う）
- ログイン画面 UI および Custom Tabs を用いた OAuth 起動（#23 で扱う）
- セッション状態（`LoggedIn` / `LoggedOut`）をアプリ全体の Navigation に反映する責務（#22 / #23 の上位レイヤ）
- 退会・アカウント削除フロー（別 Issue）
- refresh のスケジューリング（期限前プロアクティブ更新）。本 Issue では呼び出された時のみ refresh する
- 複数アカウント切替（v1 スコープ外）
- TokenStore 自体の実装（#17 で完了済み）/ JSON モデル定義（#19 で完了済み）/ HTTP クライアント基盤（#20 で完了済み）

## Open Questions

なし（SERVER.md §1.3 のエンドポイント契約、GRAND-DESIGN §5.3 の単一飛行制約で必要な情報は揃っている）
