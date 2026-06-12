# Requirements Document

## Introduction

Feedman Android アプリの主画面は「すべての新着」を時系列で並べる横断タイムラインであり、
SPEC §5.1 で採用案 `timeline=cards` のカード UI を実装することが確定している。Issue #32 までで
データ層（横断フィードの `since_time` 固定 + cursor 追従ページング）と共有メタ部品
（ArticleCard / スタートグル / はてブ数バッジ / 相対日時フォーマッタ）は揃っているが、
タイムライン画面側はまだスケルトン段階の `MockTimelineItem` 用テキスト一覧であり、SPEC §5.1
の視覚仕様（カード構成・既読減衰・外部リンクアイコン）と無限スクロール対応が未達である。

本機能では、横断タイムラインを SPEC §5.1 が要求するカード UI に置き換え、ユーザー操作
（カードタップ / 外部リンクアイコンタップ）を Epic #7 が結線する詳細シート起動・外部リンク
オープンのコールバックとして公開するところまでをスコープとする。Pull-to-refresh やロード
状態表示は Issue #34 で、詳細シート・Custom Tabs の実体は Issue #36 / #37 で扱う。

## Requirements

### Requirement 1: タイムラインカードの構成要素

**Objective:** As a Feedman Android ユーザー, I want 横断タイムラインの各記事をカード形式で
パッと識別したい, so that ソース・新しさ・要点・反響を 1 つのカードで判別できる

#### Acceptance Criteria

1. When タイムラインカードが描画される, the Timeline Card UI shall ソース行にフィードの
   favicon とフィード名を表示する
2. When タイムラインカードが描画される, the Timeline Card UI shall 公開時刻を「相対日時」
   表記で表示する（境界仕様は Issue #27 の Relative Time Formatter に従う）
3. When タイムラインカードが描画される, the Timeline Card UI shall タイトルを表示し、
   タイトルの折り返し最大行数を 3 行に制限する（4 行目以降は省略）
4. When タイムラインカードが描画される記事の概要文字列が空でない, the Timeline Card UI shall
   概要を表示し、概要の折り返し最大行数を 2 行に制限する（3 行目以降は省略）
5. When タイムラインカードが描画される記事の概要文字列が空である, the Timeline Card UI shall
   概要行を表示せず、その分のレイアウト領域を確保しない
6. When タイムラインカードが描画される, the Timeline Card UI shall はてブ数バッジを表示する
   （表記規約は Issue #27 の共有はてブ数バッジに従う）
7. When タイムラインカードが描画される, the Timeline Card UI shall スタートグルを表示する
   （表記規約・タップ規約は Issue #27 の共有スタートグルに従う）
8. When タイムラインカードが描画される, the Timeline Card UI shall 外部リンクアイコンを
   1 個表示する
9. The Timeline Card UI shall 1 枚のカードで上記 1〜8 の要素を 1 つずつ表示する（同一要素を
   重複表示しない）

### Requirement 2: 既読・未読カードの視覚差分

**Objective:** As a Feedman Android ユーザー, I want 既読のカードを未読より控えめに見せて
ほしい, so that 未読の記事を効率的に拾い読みできる

#### Acceptance Criteria

1. When タイムラインカードが既読（is_read=true）の記事を描画する, the Timeline Card UI shall
   カード全体の不透明度を 0.55 に設定する
2. When タイムラインカードが未読（is_read=false）の記事を描画する, the Timeline Card UI shall
   カード全体の不透明度を 1.0 に設定する
3. While カードが既読状態で表示されている, the Timeline Card UI shall カードタップおよび
   外部リンクアイコンタップを引き続き受け付ける

### Requirement 3: カードタップによる詳細シート起動コールバック

**Objective:** As a Feedman Android ユーザー, I want カードをタップしたら記事詳細を読み始め
られる導線が呼び出されてほしい, so that 気になった記事をすぐ深掘りできる

#### Acceptance Criteria

1. When ユーザーがタイムラインカードの本体領域をタップする, the Timeline Screen shall 「記事
   詳細を開く」コールバックを当該カードの記事 ID を引数として 1 回だけ呼び出す
2. When ユーザーが同一カード内のスタートグルをタップする, the Timeline Screen shall 「記事
   詳細を開く」コールバックを呼び出さない
3. When ユーザーが同一カード内の外部リンクアイコンをタップする, the Timeline Screen shall
   「記事詳細を開く」コールバックを呼び出さない
4. The 「記事詳細を開く」コールバック shall 引数として記事 ID（文字列）のみを受け取り、
   実体の遷移先・遷移方法は本要件では規定しない

### Requirement 4: 外部リンクアイコンによるリンクオープンコールバック

**Objective:** As a Feedman Android ユーザー, I want 外部リンクアイコンをタップして元記事を
すぐ開ける導線が呼び出されてほしい, so that 詳細シートを経由せず元記事に飛べる

#### Acceptance Criteria

1. When ユーザーがタイムラインカードの外部リンクアイコンをタップする, the Timeline Screen
   shall 「外部リンクを開く」コールバックを当該カードの記事 ID を引数として 1 回だけ呼び出す
2. When ユーザーが外部リンクアイコンをタップする, the Timeline Screen shall 「記事詳細を
   開く」コールバックを呼び出さない
3. The 「外部リンクを開く」コールバック shall 引数として記事 ID（文字列）のみを受け取り、
   実体の URL 解決・ブラウザ起動・既読化反映は本要件では規定しない

### Requirement 5: 無限スクロールとリスト安定性

**Objective:** As a Feedman Android ユーザー, I want タイムラインを下に流すと自動的に次の
記事が読み込まれてほしい, so that 操作を止めずに新着を消化できる

#### Acceptance Criteria

1. When ユーザーがリスト末尾近くまでスクロールする, the Timeline Screen shall 次ページの
   読み込みをデータ層に要求する
2. When 次ページの読み込みが完了する, the Timeline Screen shall 既存のカードの並び順を
   変更せず、新しいカードを末尾に追加する
3. When タイムラインリストが再描画される, the Timeline Screen shall 各カードを記事 ID で
   安定的に識別し、同一記事を同一カードインスタンスにひも付ける
4. While ユーザーがスクロール中である, the Timeline Screen shall スクロール位置を保持し、
   新しい読み込み完了によって表示位置をジャンプさせない

### Requirement 6: 空状態・初期読み込み中状態の最低限の表示

**Objective:** As a Feedman Android ユーザー, I want データが届く前や記事がまったく無い
ときも画面が真っ白にならないでほしい, so that 操作不能と勘違いしない

#### Acceptance Criteria

1. While 初回ロードが完了しておらず未だ 1 件もカードが描画されていない, the Timeline Screen
   shall ユーザーが「読み込み中」だと判別できる視覚的指示を表示する
2. When 初回ロードが完了し記事が 0 件である, the Timeline Screen shall ユーザーが「現在
   新着はない」と判別できる空状態メッセージを表示する
3. If 初回ロードがエラーで終了する, the Timeline Screen shall ユーザーが再試行できる手段
   またはエラー状態であると判別できる視覚的指示を表示する

## Non-Functional Requirements

### NFR 1: 視覚一貫性

1. The Timeline Card UI shall プロトタイプ `design/mobile/fm-ui.jsx` の `timeline=cards`
   バリアント（タイトル最大 3 行 / 概要最大 2 行 / 既読時 opacity 0.55 / 外部リンクアイコン
   配置）と整合する見た目を保つ
2. The Timeline Card UI shall Issue #27 の共有メタ部品（スタートグル / はてブ数バッジ /
   相対日時フォーマッタ / 既読 dim）の視覚規約を再実装せず再利用する

### NFR 2: スクロール性能

1. The Timeline Screen shall 次ページ読み込みの完了時に、既に画面に表示されているカードを
   破棄して再生成せず、新しいカードのみを追加描画する
2. While ユーザーが連続スクロールしている, the Timeline Screen shall 60fps 相当（1 フレーム
   16ms 以内）の描画頻度を維持できる粒度で描画コストを抑える

### NFR 3: アクセシビリティ

1. The Timeline Card UI shall 外部リンクアイコンに対し、スクリーンリーダーが「元記事を
   開く」相当のラベルを読み上げる
2. The Timeline Card UI shall 外部リンクアイコンに対し、44dp 以上のタップ標的サイズを
   確保する

## Out of Scope

- Pull-to-refresh の引っ張り操作と再取得トリガ（Issue #34 で扱う）
- 無限スクロール中の「読み込み中」「最後まで読みました」フッタ・エラー再試行 UI（Issue #34 で扱う）
- 記事詳細シート（ボトムシート）本体の実装と表示・既読化反映（Issue #36 で扱う）
- 外部リンクオープン時の Custom Tabs 起動・既読化反映（Issue #37 で扱う）
- スタートグルタップ時のサーバー反映（`PUT /api/items/{id}/state`、Issue #38 で扱う）
- ソース行右側のキーワード一致タグ表示（v1 スコープ外、SPEC §5.8）
- OGP サムネイル表示
- フィード別記事一覧（SPEC §5.2）、スター一覧・検索（SPEC §5.3）の画面実装
- ドロワー / トップアプリバー（SPEC §5.0）
- 既存スケルトン（`MockTimelineItem` 系）の温存可否・移行方針（実装手段の領分として
  Architect / Developer に委ねる）

## Open Questions

- なし（SPEC §5.1 でカード採用案・要素・既読不透明度が確定しており、Issue 本文の受入基準も
  本要件と齟齬なくマップできる。実体（詳細シート・Custom Tabs・既読化反映）はスコープ外
  Issue で扱う前提を `Out of Scope` に明記済み）

## 関連

- Parent: #6
- Depends on: #29 #26 #27 #32
