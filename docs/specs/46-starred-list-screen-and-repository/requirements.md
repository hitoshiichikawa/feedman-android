# Requirements Document

## Introduction

Feedman Android のスター一覧（`design/SPEC.md` §5.3、§4.2）は、ユーザーがスターを付けた記事を全フィード横断で一望できる画面である。サーバーの `GET /api/feeds/starred/items` は `StarredItemSummary`（`ItemSummary` に `feed_title` を加えた型）を返し、`feed_title` をソース表示に用いる。本 Issue ではドロワー「お気に入り」エントリから到達するスター一覧画面と、`core/data` 配下の対応リポジトリ（cursor paging）を実装し、`ItemStateStore`（#38 で確立した楽観的更新オーバーレイ）を購読してスター操作と一覧表示の整合を保つ。

スター解除直後の挙動は判断ポイントだったが、プロトタイプ（`design/mobile/fm-screens.jsx` `FMStarredScreen`）はモックデータに対する単純フィルタで「即時除去」を表現しているのに対し、本実装は cursor paging とオーバーレイ合成を前提とするため、UX 上は **セッション中はリスト上に残してスターアイコンを outline 化（グレーアウト相当）し、Pull-to-refresh または画面再入場で除去** する方針を採用する。理由は (a) 誤タップしたスター解除をその場でリトグルで取り消せること、(b) cursor 連続性が崩れない（ページ穴が出ない）こと、(c) 楽観的更新オーバーレイの合成ルール（#38 Requirement 3）と矛盾しないことである。

横断検索（#47）は本 Issue のスコープ外である。

## Requirements

### Requirement 1: ドロワー導線とスター一覧画面表示

**Objective:** As a Feedman Android アプリ利用者, I want ドロワーの「お気に入り」エントリからスター一覧画面に遷移できること, so that スターを付けた記事を全フィード横断で一望できる

#### Acceptance Criteria

1. When ユーザーがドロワーの「お気に入り」エントリを選択したとき, the Starred List Screen shall 当該画面をメイン領域に表示する
2. When スター一覧画面が表示されたとき, the Starred List Screen shall 画面ヘッダーにスター一覧であることが分かるタイトル表示を提供する
3. When スター一覧画面が表示されたとき, the Starred List Screen shall サーバーから取得した記事一覧をスクロール可能な縦リストとして提示する
4. When スター一覧画面の各行が表示されるとき, the Starred List Screen shall 当該記事の `feed_title` をソース表示として行内に提示する

### Requirement 2: スター一覧の取得と cursor paging

**Objective:** As a Feedman Android アプリ利用者, I want スター一覧をスクロールしながら追加読み込みできること, so that 蓄積したスター記事を末尾までさかのぼれる

#### Acceptance Criteria

1. When スター一覧画面が初回表示されたとき, the Starred List Repository shall `GET /api/feeds/starred/items` をカーソル未指定で呼び出して先頭ページを取得する
2. When 直前ページのレスポンスに次ページを示すカーソルが含まれているとき, the Starred List Repository shall 当該カーソルを次ページ要求にそのまま搬送する
3. When サーバーレスポンスが次ページの存在しない旨を示したとき, the Starred List Repository shall 後続ページが存在しない旨をページング状態に反映する
4. While 終端に到達している状態, the Starred List Repository shall 追加の次ページ要求を発行しない
5. If 初回ページ取得が失敗したとき, the Starred List Repository shall 当該エラーをページング状態のエラーとして露出し UI 層から再試行できるようにする
6. If 追加ページ取得が失敗したとき, the Starred List Repository shall それまで読み込み済みのページ内容を破棄せず、追加ロード分のエラーをページング状態のエラーとして露出する

### Requirement 3: 空状態とリフレッシュ

**Objective:** As a Feedman Android アプリ利用者, I want スター記事が 0 件のときと最新状態に更新したいときに適切な表示・操作が用意されていること, so that 状態を理解しつつ意図したタイミングで一覧を再取得できる

#### Acceptance Criteria

1. When スター一覧が 0 件で読み込み完了したとき, the Starred List Screen shall スター記事が無い旨を案内する空状態表示を提示する
2. When ユーザーがスター一覧で Pull-to-refresh ジェスチャを行ったとき, the Starred List Repository shall 既存のページ蓄積を破棄して先頭ページから再取得する
3. When リフレッシュ後の先頭ページ取得が成功したとき, the Starred List Repository shall 取得結果のカーソル・終端判定を Requirement 2 と同じ規則で扱う
4. If リフレッシュ時の先頭ページ取得が失敗したとき, the Starred List Screen shall エラーが発生した旨をユーザーに提示する

### Requirement 4: 記事詳細シートとの連携

**Objective:** As a Feedman Android アプリ利用者, I want スター一覧の行をタップしたときに記事詳細シートが開くこと, so that 一覧画面から離れずに本文プレビューと外部リンクへ進める

#### Acceptance Criteria

1. When ユーザーがスター一覧の行をタップしたとき, the Starred List Screen shall 当該記事の記事詳細シートを開く
2. When 記事詳細シートがスター一覧から開かれたとき, the Article Detail Sheet shall 当該記事を一意に識別できる情報をもとに既存の詳細シート挙動（既読化・スター操作・外部リンク導線）を提供する

### Requirement 5: スター操作の楽観的更新と一覧整合

**Objective:** As a Feedman Android アプリ利用者, I want スター一覧の行または記事詳細シートからスターをトグルした直後に表示が即時更新されること, so that サーバー往復の待ち時間にブロックされず操作を続けられる

#### Acceptance Criteria

1. When ユーザーがスター一覧の行に表示されたスターアイコンをトグルしたとき, the Starred List Screen shall 当該記事のスター状態を `ItemStateStore` の overlay 経由で即時に更新する
2. When 記事詳細シート上でスター状態がトグルされ、当該記事がスター一覧に表示されているとき, the Starred List Screen shall 追加のサーバー再取得を待たずに同じ記事の表示状態を更新する
3. When スター解除（star=true → false）の楽観的更新が overlay に適用されたとき, the Starred List Screen shall 当該記事をその場ではリストから除去せず、スターアイコンを未スター状態の表示に切り替えて当該行はリスト上に残置する
4. When スター解除後のリフレッシュまたはスター一覧画面への再入場が発生したとき, the Starred List Screen shall スター解除済みの記事を一覧から除外した状態で再描画する
5. If 楽観的更新に対するサーバー反映が失敗し overlay がロールバックされたとき, the Starred List Screen shall 当該記事のスター表示を直前の状態に戻す

### Requirement 6: スコープ境界

**Objective:** As a Feedman Android プロジェクト関係者, I want 本 Issue の変更影響範囲が明示されていること, so that 他 Issue との二重実装やスコープ膨張を避けられる

#### Acceptance Criteria

1. The Starred List feature shall ソース変更を `feature/starred` 配下のスター一覧画面追加および `core/data` 配下のスター一覧リポジトリ追加に限定する
2. The Starred List feature shall スター一覧内のキーワード検索 UI / フィード内検索 UI を含まない
3. The Starred List feature shall スター一覧画面に対する既読／未読フィルタやソート切替 UI を含まない

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーがスター一覧の行に表示されたスターアイコンをトグルしたとき, the Starred List Screen shall 100 ミリ秒以内に新しい表示状態へ更新する
2. While スター一覧画面が表示されているとき, the Starred List Screen shall 追加ページの読み込み中であっても既に表示済みの行のスクロール操作を阻害しない

### NFR 2: テスト網羅性

1. The Starred List Repository test suite shall Requirement 2 の正常系・終端到達・初回失敗・追加ロード失敗・リフレッシュ成功・リフレッシュ失敗を最低 1 ケースずつ網羅する
2. The Starred List Screen test suite shall Requirement 5 のうちスター解除時の残置挙動・リフレッシュ後の除去挙動・ロールバック時の表示復元を最低 1 ケースずつ検証する
3. The Starred List Repository test suite shall サーバーレスポンスの `feed_title` が各行の表示用に呼び出し元へ伝達されることを検証する

## Out of Scope

- 横断検索画面およびスター一覧内のキーワード検索 UI（#47 で扱う）
- スター一覧画面に対する既読／未読フィルタやソート切替 UI
- スター記事のオフラインキャッシュ・全文ローカル保存
- スターをまとめて解除する一括操作
- 記事詳細シート自体の改修（既存挙動を流用する。新規シート挙動は本 Issue では扱わない）
- 既読／未読の表示透過度などスター以外の状態に関する表現変更
- スター一覧専用のプッシュ通知・バッジ表示

## Open Questions

- なし（スター解除時の挙動はプロトタイプの「即時除去」表現とオーバーレイ合成方針との整合性を比較した上で、本 Issue の Introduction に記したとおり「セッション中はグレーアウトで残置、リフレッシュで除去」を確定した）

## 関連

- Parent: #10
- Depends on: #18 #29 #27 #38
