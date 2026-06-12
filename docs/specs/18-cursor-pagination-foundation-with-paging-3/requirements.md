# Requirements Document

## Introduction

Feedman Android アプリの一覧画面（横断新着タイムライン / フィード別記事一覧 / スター一覧 / 横断検索）はすべて、サーバーから `{ items, next_cursor, has_more }` 形式のカーソル方式ページネーション（`design/SPEC.md` §4.1）でデータを取得する。本 Issue では、これら 4 種類の一覧で共通利用するページング基盤を `core/network/paging` パッケージに実装し、次キー解決・終端判定・エラー伝播・リフレッシュの挙動を一元化する。本基盤は不透明なカーソル文字列の搬送・終端条件・初回ロード失敗時のエラー露出といった共通責務のみを扱い、画面固有の表示や `since_time` の固定保持（cross-feed 固有・#32 で扱う）は対象外とする。

## Requirements

### Requirement 1: 次ページ要求とカーソル搬送

**Objective:** As a Feedman Android アプリ利用者, I want 一覧画面で末尾までスクロールしたときに自動で次のページが追加読み込みされること, so that 大量の新着記事を途切れずに閲覧できる

#### Acceptance Criteria

1. When 直前のページ取得レスポンスが `has_more=true` かつ `next_cursor` に空でない文字列を含む状態で次ページ取得が要求されたとき, the Cursor Paging Source shall 当該 `next_cursor` の値を次ページ要求のカーソルとして送出する
2. When 初回ページ取得が要求されたとき, the Cursor Paging Source shall カーソル未指定（初期状態）として取得を行う
3. The Cursor Paging Source shall サーバーから受領した `next_cursor` 文字列をパース・解釈せず不透明トークンとして次回要求にそのまま受け渡す

### Requirement 2: 終端判定

**Objective:** As a Feedman Android アプリ利用者, I want 一覧の最後まで読み終えた時点で追加読み込みが停止し「最後まで読みました」相当の終端状態が示せること, so that 無限にローディングが回り続けることなく終端を認識できる

#### Acceptance Criteria

1. When ページ取得レスポンスの `has_more` が `false` であるとき, the Cursor Paging Source shall 後続ページが存在しない旨をページング状態に反映する
2. When ページ取得レスポンスの `next_cursor` が `null` または空文字列であるとき, the Cursor Paging Source shall 後続ページが存在しない旨をページング状態に反映する
3. While 終端に到達している状態, the Cursor Paging Source shall 追加の次ページ要求を発行しない

### Requirement 3: エラー伝播と再試行

**Objective:** As a Feedman Android アプリ利用者, I want 通信失敗時にエラー状態を視認でき、復旧操作で再試行できること, so that ネットワーク不調から手動で復帰できる

#### Acceptance Criteria

1. If 初回ページ取得が `FeedmanException` で失敗したとき, the Cursor Paging Source shall 当該例外をページング状態のエラーとして露出し、UI 層から再試行起点として参照可能にする
2. If 追加ページ取得が `FeedmanException` で失敗したとき, the Cursor Paging Source shall それまで読み込み済みのページ内容を破棄せず、追加ロード分のエラーをページング状態のエラーとして露出する
3. When エラー状態でページング層に対し再試行が指示されたとき, the Cursor Paging Source shall 失敗した位置のページ要求を同じカーソルで再発行する

### Requirement 4: リフレッシュ挙動

**Objective:** As a Feedman Android アプリ利用者, I want 一覧をリフレッシュしたときに先頭ページから読み直されること, so that 最新の状態に揃った一覧を見られる

#### Acceptance Criteria

1. When 一覧に対してリフレッシュが要求されたとき, the Cursor Paging Source shall 既存のページ蓄積を破棄し、先頭ページ（カーソル未指定）から取得を再開する
2. When リフレッシュ後の先頭ページ取得が成功したとき, the Cursor Paging Source shall 取得結果の `next_cursor` および `has_more` を Requirement 1 / Requirement 2 と同じ規則で扱う

### Requirement 5: 4 種類の一覧での共通利用

**Objective:** As a Feedman Android アプリの開発者, I want 4 種の一覧（横断新着 / フィード別 / スター / 検索）に対して同一のページング基盤を再利用できること, so that 終端条件・エラー伝播・リフレッシュ挙動の不整合や重複実装を避けられる

#### Acceptance Criteria

1. The Cursor Paging Source shall ページ取得ロジック（具体的なエンドポイント呼び出し）を呼び出し側から差し替え可能な形で受け取り、本基盤自身は特定の一覧種別に依存しない
2. The Cursor Paging Source shall `{ items, next_cursor, has_more }` を返すすべての一覧 API に対して Requirement 1〜4 の挙動を同一に提供する

## Non-Functional Requirements

### NFR 1: 変更範囲の限定

1. The Cursor Paging Source implementation shall ソース変更を `core/network/paging` パッケージ配下および当該パッケージのテストコードに閉じ、画面実装・既存 Repository・他レイヤーの公開 API を本 Issue では変更しない

### NFR 2: テスト網羅性

1. The Cursor Paging Source test suite shall Requirement 1〜4 の各 AC に対して、正常系および異常系・境界値（`has_more=false` / `next_cursor=null` / `next_cursor=""` / 初回失敗 / 追加ロード失敗 / リフレッシュ）を最低 1 ケースずつ網羅する

## Out of Scope

- 横断新着タイムライン固有の `since_time` セッション内固定および Pager 再生成によるリセット挙動（Issue #32 で扱う）
- 各一覧画面（cross-feed / feed items / starred / search）の実装、画面ごとの Repository 配線、UI 側の終端／エラー／リトライ表示
- カーソル文字列の中身に依存した最適化（先読み・ジャンプ等）
- 一括既読・起動同期など、ページング以外の API 追加

## Open Questions

- なし
