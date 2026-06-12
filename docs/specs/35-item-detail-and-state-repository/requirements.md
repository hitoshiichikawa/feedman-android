# Requirements Document

## Introduction

記事詳細シート（#36）と楽観的更新同期（#38）など複数の上位画面・機能から共有して利用される、記事詳細取得と既読／スター状態更新のためのデータ層を整備する。SPEC §4.2 で正本として定義された `GET /api/items/{id}` および `PUT /api/items/{id}/state` のエンドポイント契約を Kotlin の repository インターフェースとして表現し、後続 Issue が UI ／同期ロジックを安心して実装できる基盤を提供する。本 Issue のスコープは core/data 層と対応する fixture / テストに限定され、UI 表示や楽観的更新の状態管理は別 Issue に委ねる。

## Requirements

### Requirement 1: 記事詳細取得

**Objective:** As a 上位画面（記事詳細シート・横断検索詳細など）の呼び出し元, I want 記事 ID から記事本文を含む詳細データを取得できる, so that ユーザーに本文・著者などの完全な記事情報を提示できる

#### Acceptance Criteria

1. When 呼び出し元が記事 ID を指定して getItem を実行したとき, the Item Repository shall SPEC §4.2 で定義された記事詳細エンドポイントに対して当該 ID 付きの GET リクエストを発行する
2. When 記事詳細エンドポイントが 2xx で成功レスポンスを返したとき, the Item Repository shall レスポンス JSON を ItemDetail にデコードし、ItemSummary が持つ全フィールドに加えて sanitized HTML としての本文と著者名を含めた値を呼び出し元に返却する
3. When レスポンス JSON の `is_date_estimated` が true であるとき, the Item Repository shall その状態を ItemDetail 上で保持し、相対日時表示側で「(推定)」を表示できる形で呼び出し元に伝搬する
4. Where レスポンス JSON のフィールドが null を許容する（`hatebu_fetched_at` 等）とき, the Item Repository shall そのフィールドを欠落として扱わず null として保持したまま ItemDetail に格納する

### Requirement 2: 既読／スター状態の更新

**Objective:** As a 既読化・スターのトグルを発火する呼び出し元, I want 既読フラグとスターフラグを個別に、または同時に更新できる, so that UI 側で楽観的更新やバルク更新を選択して実装できる

#### Acceptance Criteria

1. When 呼び出し元が記事 ID と更新したいフラグを指定して updateState を実行したとき, the Item Repository shall SPEC §4.2 で定義された記事状態更新エンドポイントに対して当該 ID 付きの PUT リクエストを発行する
2. When 呼び出し元が isRead のみを指定し isStarred を null（未指定）にしたとき, the Item Repository shall リクエストボディに `is_read` フィールドのみを含め、`is_starred` フィールドを送信しない
3. When 呼び出し元が isStarred のみを指定し isRead を null（未指定）にしたとき, the Item Repository shall リクエストボディに `is_starred` フィールドのみを含め、`is_read` フィールドを送信しない
4. When 呼び出し元が isRead と isStarred を同時に指定したとき, the Item Repository shall リクエストボディに `is_read` と `is_starred` の両方を含めて送信する
5. If 呼び出し元が isRead と isStarred の双方を null（未指定）にして updateState を呼び出したとき, the Item Repository shall 呼び出し元にバリデーションエラーを返し、サーバーへのリクエストを送信しない
6. When 状態更新エンドポイントが 2xx で成功レスポンスを返したとき, the Item Repository shall 呼び出し元に成功を通知し、上位レイヤーが楽観的更新を確定できる結果を返却する

### Requirement 3: エラー伝搬

**Objective:** As a 楽観的更新を行う上位レイヤー, I want データ層から構造化されたエラーを受け取れる, so that ロールバックやリトライ、ユーザー向けメッセージ表示を一貫した形で実装できる

#### Acceptance Criteria

1. If 記事詳細エンドポイントがエラーレスポンス（4xx / 5xx）を返したとき, the Item Repository shall サーバーが返した統一エラーフォーマットの `code` と `message` を保持した FeedmanException として呼び出し元に伝搬する
2. If 状態更新エンドポイントがエラーレスポンス（4xx / 5xx）を返したとき, the Item Repository shall サーバーが返した統一エラーフォーマットの `code` と `message` を保持した FeedmanException として呼び出し元に伝搬する
3. If ネットワーク不達・タイムアウトなど通信レイヤーで失敗したとき, the Item Repository shall サーバー由来でない通信失敗を識別可能な FeedmanException として呼び出し元に伝搬する
4. If エラー伝搬時に状態が部分的にも更新されないことを保証する必要があるとき, the Item Repository shall 例外を投げた時点で副作用（ローカルキャッシュ変更等）を残さない

## Non-Functional Requirements

### NFR 1: テスト可観測性

1. The Item Repository shall MockWebServer で実レスポンス JSON を返した結果として記事詳細取得・状態更新の正常系および異常系を JVM 単体テストから検証可能な公開 API として提供する
2. The Item Repository test suite shall 各 AC（少なくとも Requirement 1 / 2 / 3 のそれぞれの分岐）に対応するテストケースを最低 1 件ずつ含む

### NFR 2: 契約整合性

1. The Item Repository shall SPEC §4.2 の `GET /api/items/{id}` および `PUT /api/items/{id}/state` 契約と、リクエストパス・メソッド・ボディ構造・レスポンス型のすべての観点で 1 対 1 に対応する
2. If SPEC §4.2 の契約に矛盾するレスポンス（例: 必須フィールド欠落）が観測されたとき, the Item Repository shall silent fail せず FeedmanException を伝搬する

## Out of Scope

- 記事詳細シート UI（#36 で扱う）
- 楽観的更新の同期・ロールバック制御（#38 で扱う）
- 一覧側 ItemSummary を返すエンドポイント群（横断タイムライン #18 / フィード別一覧 #28 等の責務）
- ローカルキャッシュ・オフライン保存（v1 スコープ外。SPEC §1.3）
- 認証トークンの取得・更新（#17 で完了済み。本 Issue は同 repository の前提として利用するのみ）

## Open Questions

- なし

