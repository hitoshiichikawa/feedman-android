# Requirements Document

## Introduction

新着横断タイムライン（`/api/items/cross-feed` を表示する画面）に対し、ユーザーが最新記事を能動的に取り直すための pull-to-refresh と、過去記事を継続的に読み進めるための無限スクロールに伴う各種ローディング・終端・エラー・空状態の表示挙動を確定する。SPEC §5.1 / §6 / §4.2 注意（横断タイムラインの更新は GET 再取得のみで、フィード単位の手動フェッチを叩いてはならない）を遵守し、既に存在する状態表示部品（`StateViews` / `ListFooterState` — Issue #28 で導入済み）とカード UI（Issue #33）の上にユーザー可視の挙動として組み上げる。本要件は楽観的更新（既読・スター）の挙動は扱わない（Issue #38 のスコープ）。

## Requirements

### Requirement 1: Pull-to-refresh による先頭再取得

**Objective:** As a タイムラインを閲覧するユーザー, I want 画面を下に引っ張って最新記事を取り直す, so that 直近の新着を能動的にすぐ確認できる

#### Acceptance Criteria

1. When ユーザーがタイムライン先頭で下方向に引っ張って指を離したとき, the Timeline Screen shall 横断タイムラインの先頭ページから記事一覧を再取得する
2. While 先頭再取得が進行中, the Timeline Screen shall pull-to-refresh のローディングインジケータを画面上部に表示する
3. When 先頭再取得が完了したとき, the Timeline Screen shall pull-to-refresh のローディングインジケータを消し、取得結果を一覧として反映する
4. When 先頭再取得が成功したとき, the Timeline Module shall 新たに取得した先頭ページのレスポンスに含まれる新着判定の基準時刻を以降のページング基準として固定する
5. The Timeline Module shall 横断タイムラインの pull-to-refresh としてフィード単位の手動フェッチ要求を発行してはならない（横断更新は新着横断取得 API の再取得のみで行う）

### Requirement 2: 無限スクロールによる追加ページ読込

**Objective:** As a タイムラインを閲覧するユーザー, I want 一覧を下までスクロールすると自動で次の記事が読み込まれる, so that 明示的な操作なしに過去の記事を続けて読める

#### Acceptance Criteria

1. When ユーザーが一覧の末尾付近までスクロールしたとき, the Timeline Screen shall 次ページの読込を自動で開始する
2. While 次ページの読込が進行中, the Timeline Screen shall 一覧フッタにローディング表示を出す
3. When 次ページの読込が成功したとき, the Timeline Screen shall フッタのローディング表示を消し、取得した記事を既存一覧の末尾に追記する
4. When すべてのページを読み終えた終端に達したとき, the Timeline Screen shall フッタに「最後まで読みました」相当の終端メッセージを表示する
5. While 終端メッセージが表示されている間, the Timeline Screen shall 追加ページの読込要求を発行してはならない

### Requirement 3: 初回ロードと再試行

**Objective:** As a タイムラインを開いたユーザー, I want 初回読込中・失敗時の状態が画面全体で把握できる, so that 待つべきか再試行すべきかを判断できる

#### Acceptance Criteria

1. While 初回ロードが進行中で表示すべき記事がまだ無い状態, the Timeline Screen shall 画面全体のローディング表示を出す
2. If 初回ロードが失敗したとき, the Timeline Screen shall 画面全体のエラー表示と再試行アクションを表示する
3. When ユーザーが画面全体エラー上の再試行アクションを操作したとき, the Timeline Screen shall 先頭ページからの再取得を開始する
4. When 初回ロードが成功し記事が 1 件以上取得できたとき, the Timeline Screen shall 画面全体のローディング表示・エラー表示を解除し、記事一覧を表示する

### Requirement 4: 追加ページのエラーと再試行

**Objective:** As a 既に一覧が表示されているユーザー, I want 追加ページの読込に失敗しても、既に読めている記事を失わずに復帰できる, so that 一時的なネットワーク障害でタイムラインがリセットされない

#### Acceptance Criteria

1. If 追加ページの読込が失敗したとき, the Timeline Screen shall 一覧フッタにエラーメッセージと再試行アクションを表示する
2. If 追加ページの読込が失敗したとき, the Timeline Screen shall 既に表示している記事一覧をそのまま保持する
3. When ユーザーがフッタの再試行アクションを操作したとき, the Timeline Screen shall 失敗した次ページの再読込を開始する
4. When フッタからの再試行が成功したとき, the Timeline Screen shall フッタのエラー表示を消し、取得した記事を既存一覧の末尾に追記する

### Requirement 5: Pull-to-refresh のエラー時挙動

**Objective:** As a pull-to-refresh を実行したユーザー, I want 更新が失敗しても、現在見えている一覧の状態が壊れない, so that 取得失敗時にもタイムラインの閲覧を継続できる

#### Acceptance Criteria

1. If pull-to-refresh による先頭再取得が失敗したとき, the Timeline Screen shall pull-to-refresh のローディングインジケータを終了する
2. If pull-to-refresh による先頭再取得が失敗したとき, the Timeline Screen shall 再取得失敗をユーザー可視のメッセージとして通知する
3. If pull-to-refresh による先頭再取得が失敗したとき, the Timeline Screen shall 失敗前に表示していた一覧と新着判定の基準時刻を保持する

### Requirement 6: 空状態の表示

**Objective:** As a タイムラインを開いて記事が 0 件だったユーザー, I want 空であることが明確に伝わる, so that 故障や読込中と誤認しない

#### Acceptance Criteria

1. When 初回ロードが成功して取得記事数が 0 件のとき, the Timeline Screen shall 空状態の表示（記事が無いことを示すメッセージ）を表示する
2. While 空状態が表示されている間, the Timeline Screen shall pull-to-refresh による先頭再取得を引き続き受け付ける
3. When 空状態で pull-to-refresh が成功し記事が 1 件以上取得できたとき, the Timeline Screen shall 空状態の表示を解除し、記事一覧を表示する

## Non-Functional Requirements

### NFR 1: 横断タイムラインの API 利用境界

1. The Timeline Module shall 横断タイムラインの更新・追加読込として、横断新着取得 API の GET 再取得のみを発行する
2. The Timeline Module shall 横断タイムラインからフィード単位の手動フェッチ要求を発行してはならない

### NFR 2: 状態の一意性と可視性

1. The Timeline Screen shall 画面全体ローディング・画面全体エラー・空状態・通常一覧・終端メッセージのうち、ユーザーに同時に提示する状態を 1 つに限定する（フッタの追加ページ用ローディング／エラー／終端は一覧表示中の付随状態として併存可）
2. The Timeline Screen shall pull-to-refresh のローディングと一覧フッタの追加ページローディングを視覚的に区別して表示する

### NFR 3: 既存状態表示部品との一貫性

1. The Timeline Screen shall 画面全体のローディング／エラー／空状態の見た目と文言を、アプリ共通の状態表示部品に揃える
2. The Timeline Screen shall 一覧フッタのローディング／エラー／終端メッセージの見た目と文言を、アプリ共通の一覧フッタ状態部品に揃える

## Out of Scope

- 既読・スターの楽観的更新の挙動（Issue #38 のスコープ）
- フィード別画面（§5.2）の pull-to-refresh とクールダウン案内（本要件は横断タイムラインのみが対象）
- スター一覧・検索（§5.3）の状態表示
- 新着横断取得 API のクライアント実装方式・キャッシュ戦略・並行制御の内部設計（design.md の領分）
- 通知バー以外のリトライ手段（自動指数バックオフ等）の導入
- カード UI 自体の見た目・カードタップ時の挙動（Issue #33 で確定済み）

## Open Questions

- なし
