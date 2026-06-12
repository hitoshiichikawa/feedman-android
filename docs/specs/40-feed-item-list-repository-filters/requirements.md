# Requirements Document

## Introduction

Feedman Android アプリのフィード別記事一覧画面（`design/SPEC.md` §5.2）は、サーバーの `GET /api/feeds/{id}/items?filter=all|unread|starred`（SPEC §4.2）からカーソル方式で記事一覧を取得する。本 Issue では、当該エンドポイントを呼び出す `core/data` 配下のフィード別記事一覧リポジトリを実装し、フィルタ（all / unread / starred）の切替に応じてページングが先頭から再開され、終端およびエラーがページング状態として上位レイヤーへ露出することを保証する。ページングの共通基盤（次キー解決・終端判定・エラー伝播・リフレッシュ）は Issue #18 で確立済みの `core/network/paging` を利用し、本 Issue ではフィード別記事一覧固有のリクエスト組み立てとフィルタ遷移時の再生成挙動のみを扱う。フィード別画面 UI（#41）や手動フェッチ（#42）は本 Issue のスコープ外とする。

## Requirements

### Requirement 1: フィルタ別フィード記事一覧の取得

**Objective:** As a Feedman Android アプリ利用者, I want 任意のフィードについて「すべて／未読／スター」の各フィルタで記事一覧を取得できること, so that 関心のある状態の記事のみを画面に表示できる

#### Acceptance Criteria

1. When フィルタ `all` でフィード ID を指定した記事一覧取得が要求されたとき, the Feed Item List Repository shall `GET /api/feeds/{id}/items` に対し `filter=all` クエリを付与してリクエストを送出する
2. When フィルタ `unread` でフィード ID を指定した記事一覧取得が要求されたとき, the Feed Item List Repository shall `GET /api/feeds/{id}/items` に対し `filter=unread` クエリを付与してリクエストを送出する
3. When フィルタ `starred` でフィード ID を指定した記事一覧取得が要求されたとき, the Feed Item List Repository shall `GET /api/feeds/{id}/items` に対し `filter=starred` クエリを付与してリクエストを送出する
4. The Feed Item List Repository shall リクエストパス内のフィード ID を呼び出し側から受け取った値そのままで構成し、本リポジトリで書き換えない

### Requirement 2: フィルタ変更時のページング再生成

**Objective:** As a Feedman Android アプリ利用者, I want フィルタを切り替えたときに一覧が先頭ページから読み直されること, so that 直前のフィルタの蓄積が混在せず、新しいフィルタの一覧をすぐ確認できる

#### Acceptance Criteria

1. When 同一フィード ID に対して直前と異なる `filter` 値での記事一覧取得が要求されたとき, the Feed Item List Repository shall 新しいフィルタ条件で先頭ページ（カーソル未指定）から取得を再開する
2. When フィルタが変更され先頭ページから再取得が開始されたとき, the Feed Item List Repository shall 直前のフィルタ条件で読み込み済みのページ蓄積を後続のページング状態に持ち越さない
3. While 同一フィード ID かつ同一 `filter` 値での取得が継続している間, the Feed Item List Repository shall 直前ページのレスポンスに含まれる `next_cursor` を次ページ要求のカーソルとしてそのまま搬送する

### Requirement 3: ページング基盤との整合（カーソル搬送・終端・エラー）

**Objective:** As a Feedman Android アプリ開発者, I want フィード別記事一覧でも他の一覧画面（横断新着 / スター / 検索）と同一のページング挙動を再利用できること, so that 終端条件・エラー伝播・リフレッシュ挙動の不整合を避けられる

#### Acceptance Criteria

1. When サーバーレスポンスの `has_more` が `false` であるとき, the Feed Item List Repository shall 後続ページが存在しない旨をページング状態に反映する
2. When サーバーレスポンスの `next_cursor` が `null` または空文字列であるとき, the Feed Item List Repository shall 後続ページが存在しない旨をページング状態に反映する
3. While 終端に到達している状態, the Feed Item List Repository shall 追加の次ページ要求を発行しない
4. If 初回ページ取得が失敗したとき, the Feed Item List Repository shall 当該エラーをページング状態のエラーとして露出し、UI 層から再試行の起点として参照可能にする
5. If 追加ページ取得が失敗したとき, the Feed Item List Repository shall それまで読み込み済みのページ内容を破棄せず、追加ロード分のエラーをページング状態のエラーとして露出する
6. When エラー状態でページング層に対し再試行が指示されたとき, the Feed Item List Repository shall 失敗したページ要求を同一フィード ID・同一 `filter`・同一カーソルで再発行する

### Requirement 4: リフレッシュ挙動

**Objective:** As a Feedman Android アプリ利用者, I want 一覧をリフレッシュしたときに現在のフィルタで先頭ページから読み直されること, so that 最新の状態に揃った一覧を見られる

#### Acceptance Criteria

1. When 現在のフィルタを保ったまま一覧のリフレッシュが要求されたとき, the Feed Item List Repository shall 既存のページ蓄積を破棄し、先頭ページ（カーソル未指定・同一 `filter`）から取得を再開する
2. When リフレッシュ後の先頭ページ取得が成功したとき, the Feed Item List Repository shall 取得結果の `next_cursor` および `has_more` を Requirement 3 と同じ規則で扱う

## Non-Functional Requirements

### NFR 1: 変更範囲の限定

1. The Feed Item List Repository implementation shall ソース変更を `core/data` 配下のフィード別記事一覧リポジトリ追加および当該リポジトリのテストコードに閉じ、画面実装・既存 Repository・他レイヤーの公開 API を本 Issue では変更しない

### NFR 2: テスト網羅性

1. The Feed Item List Repository test suite shall フィルタ 3 値（all / unread / starred）それぞれについて、リクエストクエリが期待値で送出されることを MockWebServer を用いて検証する
2. The Feed Item List Repository test suite shall Requirement 2〜4 の各 AC に対して、正常系および異常系・境界値（フィルタ変更時の先頭ページ再取得 / `has_more=false` / `next_cursor=null` / `next_cursor=""` / 初回失敗 / 追加ロード失敗 / リフレッシュ）を最低 1 ケースずつ網羅する

## Out of Scope

- フィード別記事一覧画面の UI 実装（フィルタタブ・カード表示・空状態・終端表示・エラー表示。Issue #41 で扱う）
- フィード別 Pull-to-refresh による手動フェッチ（`POST /api/subscriptions/{id}/fetch`。Issue #42 で扱う）
- フィードのステータス（`active` / `stopped` / `error`）に応じた警告バナー表示や `POST .../resume` の呼び出し（購読設定系の別 Issue で扱う）
- 既読化・スターのトグル等、記事状態同期（Epic #7 系で扱う）
- 横断新着タイムライン・スター一覧・横断検索の各リポジトリ（別 Issue）
- カーソル文字列の内部解釈や先読み・ジャンプ等の最適化（不透明トークンとして扱う方針は #18 を継承）

## Open Questions

- なし

## 関連

- Parent: #8
- Depends on: #18
