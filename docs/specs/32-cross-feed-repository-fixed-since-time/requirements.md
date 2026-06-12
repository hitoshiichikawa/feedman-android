# Requirements Document

## Introduction

横断新着タイムライン（SPEC §5.1）のデータ層として、`ItemRepository` に cross-feed Pager を実装する。
バックエンド `GET /api/items/cross-feed`（SPEC §4.2）は初回レスポンスに `since_time`（RFC3339）を含み、
これを**セッション中固定**して以降のページ取得に渡すことで、無限スクロール中に新着判定の基準時刻がブレるのを防ぐ
（SPEC §4.1、受け入れ基準 §10 第 2 項）。本 Issue は #18（共通 cursor PagingSource 基盤）を前提に、
cross-feed 専用の Pager 生成・`since_time` セッション保持・リフレッシュ時のリセットを ItemRepository に組み込む。
タイムライン UI（#33）・状態変更（#38）はスコープ外で、変更範囲は `core/data`（ItemRepository）と
`core/network/paging` に閉じる。

## Requirements

### Requirement 1: Cross-feed Pager の生成と初回読み込み

**Objective:** As a タイムライン画面の利用者, I want 横断新着の最初のページを安定して取得できる, so that 起動直後に新着一覧を読み始められる

#### Acceptance Criteria

1. When タイムライン画面が cross-feed Pager を購読する, the ItemRepository shall `GET /api/items/cross-feed` を `limit=50` で呼び出して最初のページを返す
2. When 最初のページのレスポンスが届く, the ItemRepository shall レスポンスの `since_time` を当該 Pager セッションに保持する
3. When 最初のページのレスポンスが届く, the ItemRepository shall レスポンスの `items` を Paging 3 のページとして UI 層に流す
4. If 最初のページのレスポンスに `since_time` が含まれていない, the ItemRepository shall `FeedmanException`（合成コード）を発行して読み込みを中断する

### Requirement 2: 後続ページ取得時の since_time / cursor 引き継ぎ

**Objective:** As a タイムライン画面の利用者, I want 無限スクロール中も新着判定基準がブレない, so that 読み進めても重複や欠落のない一覧を見られる

#### Acceptance Criteria

1. When 後続ページが要求される, the ItemRepository shall `GET /api/items/cross-feed` に `cursor=<前回レスポンスの next_cursor>` と `since_time=<セッション保持値>` を付与してリクエストする
2. While 同一 Pager セッションが継続している, the ItemRepository shall `since_time` をサーバーから再取得した値で上書きせず初回値を維持する
3. When 後続ページのレスポンスが届く, the ItemRepository shall `next_cursor` を次回リクエスト用に更新する
4. The ItemRepository shall 1 回のリクエストにつき `limit` を 50 件として送信する

### Requirement 3: 終端判定

**Objective:** As a タイムライン画面の利用者, I want 一覧の末尾に到達したことを知る, so that 「最後まで読みました」表示が出る

#### Acceptance Criteria

1. When レスポンスの `has_more` が `false` である, the ItemRepository shall 以降のページが無い旨を Paging 3 へ通知する
2. When レスポンスの `next_cursor` が `null` または空文字である, the ItemRepository shall 以降のページが無い旨を Paging 3 へ通知する
3. While 終端到達後である, the ItemRepository shall 追加の `GET /api/items/cross-feed` を発行しない

### Requirement 4: リフレッシュによる since_time リセット

**Objective:** As a タイムライン画面の利用者, I want Pull-to-refresh で新しい新着を取り込める, so that 最新の記事から読み直せる

#### Acceptance Criteria

1. When Pager 再生成（リフレッシュ）が要求される, the ItemRepository shall 既存セッションの `since_time` と `next_cursor` を破棄する
2. When リフレッシュ後の最初のページのレスポンスが届く, the ItemRepository shall 新しい `since_time` をセッション保持値として採用する
3. When リフレッシュ後の最初のページのレスポンスが届く, the ItemRepository shall 以降のページ取得に新しい `since_time` を使用する

### Requirement 5: エラー応答時の挙動

**Objective:** As a タイムライン画面の利用者, I want 通信エラーや API エラーを把握できる, so that 再試行や原因確認ができる

#### Acceptance Criteria

1. If `GET /api/items/cross-feed` が非 2xx 応答を返す, the ItemRepository shall 統一エラーフォーマット（SPEC §4.3）の `code` / `message` を保持した `FeedmanException` を発行する
2. If `GET /api/items/cross-feed` がネットワーク断によって失敗する, the ItemRepository shall ネットワーク由来であることを示す合成コードを持つ `FeedmanException` を発行する
3. If 後続ページ取得が失敗する, the ItemRepository shall セッションの `since_time` を破棄せず保持したままにする

## Non-Functional Requirements

### NFR 1: テスト可能性

1. The ItemRepository shall MockWebServer 上で `since_time` と `cursor` の受け渡しを検証できるよう、HTTP 層を実装差し替え可能な形で公開する
2. The ItemRepository shall 時刻に依存しない単体テストが可能であるよう、`since_time` をテストから観測可能な状態として公開する

### NFR 2: 依存関係

1. The ItemRepository shall 共通カーソル PagingSource 基盤（#18 で導入済み）を利用して cross-feed Pager を構築する
2. The ItemRepository shall 変更範囲を `core/data`（ItemRepository）および `core/network/paging` に限定する

## Out of Scope

- 横断タイムライン UI（カード描画・Pull-to-refresh ジェスチャー・「最後まで読みました」表示）の実装（#33）
- 記事の既読／スター状態の更新と楽観的更新ロジック（#38、`ItemStateStore` 連携）
- フィード別記事一覧・スター一覧・検索の Pager 実装（別 Issue）
- 認証トークンの取得・更新（既に `AuthInterceptor` / `TokenAuthenticator` 経由で付与済み前提）
- `since_time` の永続化（プロセス再起動を跨いで保持しない。セッション中＝Pager 生存中のみ保持）
- 横断 Pull-to-refresh での `POST .../fetch` 連打（SPEC §4.2 注意で「横断は GET 再取得のみ」と確定済み）

## Open Questions

なし

## 関連

- Parent: #6
- Depends on: #18
