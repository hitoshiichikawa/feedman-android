# Requirements Document

## Introduction

#30 で導入したドロワーのフィード一覧は、現状 Fake 実装（`FakeSubscriptionRepository`）が返す固定モックデータを描画している。
本 Issue ではドロワーのデータソースを Fake から実 API（SPEC §4.2 `GET /api/subscriptions`）へ切り替え、ユーザーが実際に購読しているフィードのタイトル・favicon・未読件数・状態（active / stopped / error）をドロワーに反映できるようにする。
取得失敗時はドロワー内のフィードセクションのみエラー / 再試行を提示し、アプリシェル全体（メイン項目・フッタ・トップバー）が壊れない動作を保証する。
モックモード（`AppConfig.mockMode = true`）では引き続き Fake を束ねることで、表示確認・テスト動線を維持する。
購読の変更操作（解除 / 設定更新 / 再開 / 手動フェッチ）は #42 / #43 のスコープとし、本 Issue では取り扱わない。

## Requirements

### Requirement 1: 実 API による購読一覧の取得

**Objective:** As an アプリ利用者, I want ドロワーに自分が実際に購読しているフィード一覧を表示してほしい, so that Fake ではなく自分のアカウントの購読状態を確認できる

#### Acceptance Criteria

1. When ドロワーが Subscription Repository を購読したとき, the Subscription Repository shall サーバーの `GET /api/subscriptions` を呼び出して購読フィードの一覧を取得する
2. When `GET /api/subscriptions` が 2xx 応答を返したとき, the Subscription Repository shall SPEC §4.2 の `Subscription` 型として応答を decode し、observe 中の購読者へ流す
3. The Subscription Repository shall 取得したフィードのうち `feed_id` / `feed_title` / `favicon_url`（nullable）/ `unread_count` / `feed_status` を観測可能なリストに含める
4. The Subscription Repository shall サーバーが返したフィードの順序を変更せずそのままの順序で観測者へ流す
5. While サーバーが購読フィードを 1 件も返さないとき, the Subscription Repository shall 空のフィードリストを観測者へ流す

### Requirement 1.1: 取得結果のドロワー反映

#### Acceptance Criteria

1. When Subscription Repository が新しい購読フィードリストを発行したとき, the Drawer Feed List shall 追加の手動更新操作なしで次の再描画時に新しいリストを反映する
2. The Drawer Feed List shall 各フィード行の未読バッジ表示に Subscription Repository が返した `unread_count` を用いる
3. The Drawer Feed List shall 各フィード行の状態アイコン（active / stopped / error）の判定に Subscription Repository が返した `feed_status` を用いる
4. Where favicon_url が `data:<mime>;base64,...` 形式の data URL である場合, the Drawer Feed List shall 当該文字列を favicon 描画に渡す
5. Where favicon_url が null である場合, the Drawer Feed List shall レターアバターフォールバックを用いて当該行を描画する

### Requirement 2: 取得失敗時のドロワー内エラー表示と再試行

**Objective:** As an アプリ利用者, I want フィード一覧の取得に失敗してもアプリ全体が壊れず原因と再試行手段が分かるようにしてほしい, so that 一時的なネットワーク障害から自力で復帰できる

#### Acceptance Criteria

1. If `GET /api/subscriptions` が非 2xx 応答またはネットワーク失敗で完了したとき, the Subscription Repository shall ドロワーが識別可能な失敗状態として観測者へ通知する
2. If 取得失敗状態が観測されたとき, the Drawer Feed List shall ドロワー内のフィード一覧セクションにエラー表示と再試行操作を提示する
3. If 取得失敗状態が観測されたとき, the Drawer Feed List shall ドロワーのメイン項目（「すべての新着」「お気に入り」）・フッタ項目・トップバーの表示を継続する
4. When ユーザーが再試行操作をタップしたとき, the Subscription Repository shall `GET /api/subscriptions` の再取得を実行する
5. While 再試行による取得が進行中のとき, the Drawer Feed List shall ロード中であることが識別できる状態をフィード一覧セクションに提示する
6. If エラー応答が SPEC §4.3 の統一エラー（`code` / `message`）を含むとき, the Drawer Feed List shall そのメッセージをユーザー向け文言として表示に用いる

### Requirement 3: モックモード時の Fake 継続利用

**Objective:** As an アプリ開発者 / QA, I want モックモードでは引き続き Fake のサンプル購読が表示されるようにしてほしい, so that ネットワーク・実サーバー無しで UI 全分岐（active / stopped / error / favicon あり / なし）を確認できる

#### Acceptance Criteria

1. Where モックモードが有効（`AppConfig.mockMode = true`）であるとき, the Subscription Repository 依存解決 shall Fake 実装（#30 で導入された Fake 購読データを返す実装）を用いる
2. Where モックモードが有効であるとき, the Subscription Repository shall `GET /api/subscriptions` を呼び出さない
3. Where モックモードが無効（`AppConfig.mockMode = false`）であるとき, the Subscription Repository 依存解決 shall 実 API 実装を用いる
4. The Subscription Repository shall モックモードの有無に関わらず、ドロワーが依存する公開インターフェース（観測可能な購読フィードリスト）を同一に保つ

### Requirement 4: 認証エラー時の挙動

**Objective:** As an アプリ利用者, I want 認証切れがフィード一覧の致命的エラーとして残らず、適切な再認証導線に乗るようにしてほしい, so that ログインし直せばドロワーが復帰する

#### Acceptance Criteria

1. If `GET /api/subscriptions` が 401 応答を返したとき, the Subscription Repository shall 既存の共通認証層が行う再認証フローの結果に従う
2. If 共通認証層によるトークン更新後に再試行が成功したとき, the Subscription Repository shall 成功時の購読一覧を観測者へ流す
3. If 共通認証層によるトークン更新後も 401 が継続したとき, the Subscription Repository shall ドロワーが識別可能な認証エラー状態として観測者へ通知する

## Non-Functional Requirements

### NFR 1: 変更範囲

1. The 本 Issue の変更 shall `core/data`（Subscription Repository 実装の追加）・`di`（依存バインド切替）・`shell/DrawerContent`（エラー / 再試行 / ローディング表示の追加）に閉じ、`core/network` の API 契約・`core/model` の `Subscription` 型・`feature/*` の他画面のソースを変更しない
2. The Subscription Repository の公開インターフェース（#30 で定義された観測 API）の互換性 shall 維持され、`DrawerViewModel` の利用箇所が機械的な書き換えなしに動作する

### NFR 2: テスト容易性

1. The Subscription Repository 実装 shall HTTP 層をモックして `GET /api/subscriptions` の正常系・失敗系・空応答を単体テストで検証できる
2. The Drawer Feed List のエラー / ローディング / 成功表示 shall UI 層のテストで状態を注入して検証できる
3. The Subscription Repository 実装 shall モックモード（Fake バインド）と実 API モードの切替を、依存バインドの単一箇所の差し替えで再現できる構成にする

### NFR 3: シェル全体の頑健性

1. While ドロワーのフィード一覧取得が失敗しているとき, the App Shell shall ドロワー外の画面（タイムライン・記事詳細など）の表示と操作性を維持する
2. While ドロワーが再試行中であるとき, the App Shell shall ユーザーの他のドロワー操作（メイン項目選択・フッタ項目選択・ドロワークローズ）を引き続き受け付ける

## Out of Scope

- 購読解除（`DELETE /api/subscriptions/{id}`）の UI / 配線（#42 / #43）
- 購読設定更新（`PUT /api/subscriptions/{id}/settings`）・再開（`POST /api/subscriptions/{id}/resume`）・手動フェッチ（`POST /api/subscriptions/{id}/fetch`）（#42 / #43）
- フィード登録（`POST /api/feeds`）の動線（別 Issue）
- 未読件数のリアルタイム差分更新（記事既読化に伴う `unread_count` の自動デクリメント）
- 購読一覧のローカル永続化・オフラインキャッシュ
- favicon 画像の取得・キャッシュ機構（#26 で導入済みの描画経路を再利用するのみ）
- ドロワー以外の画面（タイムライン / 記事詳細 / 検索）における購読データの利用

## Open Questions

- なし（取得失敗時の表示文言・再試行ボタンの具体的なラベルは `design/mobile/fm-screens.jsx` の既存パターンを踏襲し、Architect / Developer の領分とする）

## 関連

- Parent: #8
- Depends on: #17 #30
