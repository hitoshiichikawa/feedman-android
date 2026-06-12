# Requirements Document

## Introduction

ナビゲーションドロワーのフィード一覧を、実 API 統合（#39）を待たずに先行実装するための要件を定める。
購読フィードのデータソースを抽象化したリポジトリと、その Fake 実装を用意することで、後続の実装（実 API 統合・購読設定シート）と独立してドロワー UI を完成させる。
視覚・挙動は `design/mobile/fm-screens.jsx` のドロワー行構成（favicon + タイトル + 状態アイコン + 未読バッジ + 設定アイコン）と `design/SPEC.md` §5.0 を正とする。
本 Issue のスコープはドロワー内のフィード行リスト部分に限定し、購読設定シート本体（#43）と実 API 統合（#39）は対象外とする。

## Requirements

### Requirement 1: フィード一覧の表示

**Objective:** As an アプリ利用者, I want ドロワーに購読中のフィード一覧を表示してほしい, so that ハンバーガーメニューから各フィードへ素早く遷移できる

#### Acceptance Criteria

1. When ユーザーがハンバーガーメニューをタップしてドロワーを開いたとき, the Drawer Feed List shall リポジトリから取得したフィード行の一覧を表示する
2. The Drawer Feed List shall 各フィード行に「favicon・フィードタイトル・未読バッジ・状態アイコン・設定アイコン」をこの順序で配置する
3. While リポジトリが購読フィードを 1 件も保持していないとき, the Drawer Feed List shall フィード行を 1 件も描画しない
4. The Drawer Feed List shall フィードタイトルが行幅を超える場合に省略表記（末尾省略）で 1 行に収める
5. The Drawer Feed List shall リポジトリが返すフィード順序をそのままの順序で描画する

### Requirement 1.1: 未読バッジ表示

#### Acceptance Criteria

1. While フィードの未読件数が 1 以上のとき, the Drawer Feed List shall 当該フィード行に未読件数を表示するバッジを描画する
2. While フィードの未読件数が 0 のとき, the Drawer Feed List shall 当該フィード行の未読バッジを非表示にする

### Requirement 2: フィード状態インジケータ

**Objective:** As an アプリ利用者, I want フィードが停止中・エラー中の場合に行内で判別できるようにしてほしい, so that 異常があるフィードに気付ける

#### Acceptance Criteria

1. While フィードの状態が stopped のとき, the Drawer Feed List shall 当該フィード行に停止アイコン（pause）を表示する
2. While フィードの状態が error のとき, the Drawer Feed List shall 当該フィード行に警告アイコン（alert、危険色）を表示する
3. While フィードの状態が active（正常）のとき, the Drawer Feed List shall 当該フィード行に状態アイコンを表示しない
4. The Drawer Feed List shall 状態アイコンと未読バッジを同時に表示する場合、状態アイコンを未読バッジの左側に配置する

### Requirement 3: フィード行タップによる遷移

**Objective:** As an アプリ利用者, I want フィード行をタップするとそのフィードの記事一覧へ遷移してほしい, so that 1 アクションで目的のフィードを閲覧できる

#### Acceptance Criteria

1. When ユーザーがフィード行（設定アイコン以外の領域）をタップしたとき, the Drawer Feed List shall 当該フィードの記事一覧画面（feed/{feedId}）への遷移要求を発行する
2. When ユーザーがフィード行をタップして遷移要求を発行したとき, the Drawer Feed List shall ドロワーを閉じる
3. When ユーザーがフィード行をタップしたとき, the Drawer Feed List shall 遷移先のフィード ID にリポジトリから取得した当該フィードの feed_id を用いる

### Requirement 4: 設定アイコンのタップ

**Objective:** As an アプリ利用者, I want 各フィード行の設定アイコンから購読設定を開けるようにしてほしい, so that フィードごとの間隔変更や購読解除に素早くアクセスできる

#### Acceptance Criteria

1. When ユーザーがフィード行の設定アイコンをタップしたとき, the Drawer Feed List shall 当該フィードを引数として購読設定オープンのコールバックを呼び出す
2. When ユーザーがフィード行の設定アイコンをタップしたとき, the Drawer Feed List shall フィード行タップによる記事一覧への遷移を発生させない（同時に発火させない）
3. When ユーザーがフィード行の設定アイコンをタップしたとき, the Drawer Feed List shall ドロワーを開いたままにする

### Requirement 5: 購読フィードのリポジトリ抽象

**Objective:** As an アプリ開発者, I want 購読フィードのデータソースをリポジトリ抽象越しに取得したい, so that 実 API 統合（#39）の前にドロワー UI を独立して実装・テストできる

#### Acceptance Criteria

1. The Subscription Repository shall 購読フィードのリスト（feed_id・タイトル・favicon 情報・未読件数・状態を含む）を観測可能な形で公開する
2. Where Fake 実装が選択されているとき, the Subscription Repository shall 表示確認用のサンプルフィード（active・stopped・error の各状態を 1 件以上含む）を返却する
3. When フィード一覧 UI が初回購読を開始したとき, the Subscription Repository shall 現在保持しているフィードリストを直ちに（追加の手動更新操作なしで）流す
4. The Subscription Repository shall 公開するフィードのデータ構造に「feed_id」「タイトル」「favicon を解決するための情報」「未読件数」「状態（active / stopped / error のいずれか）」を含める

## Non-Functional Requirements

### NFR 1: 視覚仕様への準拠

1. The Drawer Feed List shall ドロワー行の構成・要素配置・状態アイコン種別を `design/mobile/fm-screens.jsx` の `FMFeedListBody` の仕様と一致させる
2. The Drawer Feed List shall フィードタイトルが行幅を超えた場合の改行を許容せず、1 行に省略表記で収める
3. The Drawer Feed List shall 表示文字列をハードコードせず、ローカライズ可能なリソースから解決する

### NFR 2: 状態反映の即時性

1. When リポジトリが新しいフィードリストを発行したとき, the Drawer Feed List shall 追加の手動更新操作なしで次の再描画時に新しいリストを反映する

### NFR 3: テスト容易性

1. The Subscription Repository shall インターフェースと Fake 実装の差替えにより、UI 層のテストが実 API・ネットワークに依存せずに完了できるようにする
2. The Drawer Feed List shall 行タップ・設定アイコンタップのコールバックを外部から注入可能にし、UI テストで遷移／設定オープンの呼出を検証可能にする

## Out of Scope

- 実 API（`GET /api/feeds` 等）に基づく購読フィード取得・キャッシュ（#39 で実装）
- 購読設定ボトムシート本体（間隔変更・再開・購読解除の UI と API 連携、#43 で実装）
- フィード行の長押しメニュー・並び替え・スワイプ操作
- 「すべての新着」「お気に入り」エントリ自体の振る舞い（既存実装または別 Issue 範囲）
- ドロワーフッタ（キーワード通知・アカウント・テーマ切替）の挙動
- 未読件数のリアルタイム更新（記事既読化に伴う差分反映ロジック）
- favicon 画像の取得・キャッシュ機構（表示インターフェースのみ規定）

## Open Questions

- なし

## 関連

- Parent: #5
- Depends on: #29 #26
