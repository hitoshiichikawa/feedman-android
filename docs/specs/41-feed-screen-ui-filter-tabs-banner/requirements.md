# Requirements Document

## Introduction

フィード別画面（FeedScreen）は、ユーザーが特定のフィードに絞り込んで記事を消化するための主要画面である。
ドロワーや横断タイムラインからフィードを選択した際に表示され、上部フィルタタブで「すべて / 未読 / スター」を
切り替えながら、購読が停止・エラー状態のときは警告バナーで状況と復旧導線を提示する。
本要件は SPEC §5.2 を正本とし、視覚・挙動はプロトタイプ `fm-screens.jsx` の `FMFeedScreen` を基準とする。
購読リポジトリと記事一覧の取得経路はそれぞれ親 Issue #40 / #39 で整備済みのため、本要件は **画面 UI と画面固有の
状態管理** に集中する。

## Requirements

### Requirement 1: フィード別画面の表示と記事カード

**Objective:** As a 購読者, I want 選択したフィードの記事一覧をタイムラインと同じ見た目で確認したい, so that フィード横断と同じ操作感で消化できる

#### Acceptance Criteria

1. When ユーザーがフィードを選択して Feed Screen に遷移したとき, the Feed Screen shall 当該フィードに紐づく記事一覧を取得し、フィルタタブ・警告バナー（条件を満たす場合）・記事カード・無限スクロール終端を縦並びで表示する
2. The Feed Screen shall 記事カードを横断タイムラインで使用される共通カード部品で描画する
3. When 記事一覧の取得中で初回ロードが完了していないとき, the Feed Screen shall ローディングインジケータを表示する
4. While 取得結果が 0 件であるとき, the Feed Screen shall プロトタイプ `FMEmpty` 相当の空状態メッセージ（タイトルと補助テキスト）を表示する
5. If 記事一覧の取得に失敗したとき, the Feed Screen shall エラー状態（エラーメッセージと再試行手段）を表示する
6. When ユーザーが記事カード本体をタップしたとき, the Feed Screen shall 当該記事の共有詳細シート（Issue #36 で実装済み）を開く

### Requirement 2: フィルタタブによる絞り込み

**Objective:** As a 購読者, I want すべて／未読／スターをタブで切り替えたい, so that 興味のある状態の記事だけを素早く絞り込める

#### Acceptance Criteria

1. The Feed Screen shall 画面上部に「すべて」「未読」「スター」の 3 つのフィルタタブを左から順に表示する
2. While 画面初期表示時, the Feed Screen shall 「すべて」タブを選択状態として表示し、対応する記事一覧を表示する
3. When ユーザーがフィルタタブをタップしたとき, the Feed Screen shall 選択タブを視覚的にアクティブ表示へ切り替え、当該フィルタに対応する記事一覧を再取得して表示する
4. When フィルタタブを切り替えたとき, the Feed Screen shall 一覧のスクロール位置を先頭に戻す
5. When フィルタが切り替わって新しい一覧の取得中であるとき, the Feed Screen shall ローディングインジケータを表示する
6. If 切り替えたフィルタで取得結果が 0 件のとき, the Feed Screen shall 「フィルタを変えるか、引っ張って更新してください」相当の空状態メッセージを表示する
7. When ユーザーが同じフィード画面に留まったままフィルタを再度切り替えたとき, the Feed Screen shall 直前に選択されていたフィルタの状態を引き継がず、最後にタップされたフィルタの結果を表示する

### Requirement 3: 停止／エラー時の警告バナーと再開アクション

**Objective:** As a 購読者, I want フィードの停止・エラー状況と復旧手段を画面上部で把握したい, so that 取得が止まっていることに気付かず古い記事を読み続けることがない

#### Acceptance Criteria

1. While 選択中のフィードの状態が "stopped" または "error" であるとき, the Feed Screen shall フィルタタブの上に警告バナーを表示する
2. The Feed Screen shall 警告バナーにフィード状態を示すアイコン（停止／エラーで視覚的に区別される）・サーバから返された `error_message` 本文・「再開」ボタンの 3 要素を 1 行で並べて表示する
3. If `error_message` が空または未提供のとき, the Feed Screen shall 状態に応じた既定の説明文（停止または取得エラーが発生している旨）を代わりに表示する
4. While フィードの状態が "active" であるとき, the Feed Screen shall 警告バナーを表示しない
5. When ユーザーが警告バナーの「再開」ボタンをタップしたとき, the Feed Screen shall 当該購読の再開処理（購読再開エンドポイント呼び出し）をトリガーする
6. While 再開処理が実行中であるとき, the Feed Screen shall 「再開」ボタンを連続タップ不可な状態（disabled かつ進行中であることが分かる表示）にする
7. When 再開処理が成功したとき, the Feed Screen shall 警告バナーを非表示にし、フィードの記事一覧を最新の状態で再取得する
8. If 再開処理が失敗したとき, the Feed Screen shall 失敗理由を含むエラー通知をユーザーに提示し、警告バナーを表示したままにする
9. When ユーザーが警告バナーの本文・アイコン部（再開ボタン以外）をタップしたとき, the Feed Screen shall タップを無視する（誤操作で他画面に遷移させない）

### Requirement 4: 画面起動・再訪時の整合性

**Objective:** As a 購読者, I want 他画面から戻ってきたときも常に最新の購読状態と記事一覧を見たい, so that 状態の食い違いによる混乱を避けられる

#### Acceptance Criteria

1. When Feed Screen が起動したとき, the Feed Screen shall 対象フィードの購読情報（状態・`error_message`）と記事一覧の両方を取得し、結果が揃った時点で画面を更新する
2. When 他画面から Feed Screen に戻ってきたとき, the Feed Screen shall 購読情報と記事一覧を再評価し、状態変化があれば警告バナーと一覧表示に反映する
3. If 対象フィードに対応する購読情報が取得できないとき, the Feed Screen shall 画面全体のエラー状態として「フィードが見つかりません」相当のメッセージを表示する

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーがフィルタタブをタップしたとき, the Feed Screen shall タップから 100ms 以内に選択タブのアクティブ表示を切り替える（取得結果の反映は別途進行中表示でカバー）
2. When ユーザーが「再開」ボタンをタップしたとき, the Feed Screen shall タップから 100ms 以内にボタンを進行中状態へ切り替える

### NFR 2: アクセシビリティ

1. The Feed Screen shall フィルタタブ・「再開」ボタンに、スクリーンリーダーが読み上げ可能なラベルを付与する
2. The Feed Screen shall フィルタタブ・「再開」ボタンのタップ標的を最小 44dp 四方で確保する

### NFR 3: ローカライズ

1. The Feed Screen shall 表示する UI 文言（タブ名・空状態メッセージ・既定の警告文・再開ボタン文言）をすべて文字列リソースとして外部化し、ハードコードしない

## Out of Scope

- Pull-to-refresh ジェスチャと手動フェッチ（クールダウン 429 / `FEED_COOLDOWN` ハンドリングを含む） — Issue #42 で扱う
- 購読設定ボトムシート（フェッチ間隔変更・購読解除・設定経由の再開） — Issue #43 で扱う
- ドロワー UI 自体・ドロワーからの遷移経路 — 別 Issue で実装済み
- 記事詳細シートの実装本体・既読化やスター操作の楽観的更新ロジック — Issue #36 / 他 Issue で実装済み
- 横断タイムライン画面・スター一覧画面・検索画面の UI
- フィード状態のサーバ側遷移ロジック・自動再開・エラー復旧ワーカー
- フィード状態変化のプッシュ通知やバッジ更新

## Open Questions

- なし（Issue 本文と SPEC §5.2 で挙動が確定済み。`error_message` が null のときの既定文言は Requirement 3.3 で代替を要求するに留め、具体的な文言は実装時に文字列リソースで定義する）
