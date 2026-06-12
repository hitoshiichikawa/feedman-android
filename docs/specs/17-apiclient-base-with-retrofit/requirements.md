# Requirements Document

## Introduction

Feedman Android アプリ v1 では、サーバー（`hitoshiichikawa/feedman`）の REST API を流用して全機能を実装する。本 Issue では、各機能 Issue（タイムライン #18、購読 #19 など）が共通利用する HTTP クライアント基盤として、Retrofit インターフェース `FeedmanApi` と `ApiClientFactory` を `core/network` 配下に整備する。SPEC §4.2 のエンドポイント契約をシグネチャに転記し、JSON は kotlinx.serialization（`ignoreUnknownKeys`）で扱う。非 2xx 応答は #16 で導入済みの `FeedmanException` へ統一変換し、後続 Issue（#21/#22 のトークン認証・401 リフレッシュ）が OkHttp に interceptor / authenticator を後付けできる構成にする。本 Issue 自体は認証ヘッダ付与・トークンリフレッシュ・Paging 基盤を含まない。

## Requirements

### Requirement 1: FeedmanApi（Retrofit インターフェース）

**Objective:** As a feature module developer, I want a typed Retrofit interface that mirrors the server's REST contract, so that 各 Repository が SPEC §4.2 のエンドポイントを型安全に呼び出せる

#### Acceptance Criteria

1. The FeedmanApi shall expose suspend 関数として SPEC §4.2 に列挙された全エンドポイント（認証 / ユーザー / 横断新着 / 購読 / フィード / 記事 / 検索）を宣言する
2. The FeedmanApi shall declare 一覧系エンドポイント（cross-feed / feed items / starred / search）について `cursor` と `limit` クエリパラメータを受け取れる形で宣言する
3. The FeedmanApi shall declare 各レスポンス型を `core/model` で定義された `@Serializable` データクラス（`CrossFeedItem` / `ItemSummary` / `ItemDetail` / `Subscription` / ページレスポンス共通型など）として返す
4. Where 記事状態更新（`PUT /api/items/{id}/state`）が呼び出される場合, the FeedmanApi shall リクエストボディ `{ is_read?, is_starred? }` を nullable フィールドとして受け取れる契約を提供する
5. The FeedmanApi shall 認証系・状態変更系（`POST /auth/logout` / `DELETE /api/users/me` / `PUT /api/users/me/cross-feed-last-seen` / `POST /api/subscriptions/{id}/fetch` ほか SPEC §4.2 に列挙されたもの）を含む全エンドポイントをカバーする

### Requirement 2: ApiClientFactory（OkHttp + Retrofit + JSON 構成）

**Objective:** As a DI module author, I want a factory that wires OkHttp、Retrofit、kotlinx.serialization の構成を 1 か所に集約する, so that 全 Repository が単一の `FeedmanApi` インスタンスを共有でき、認証/ログ等の追加設定が後付け可能になる

#### Acceptance Criteria

1. When ApiClientFactory が FeedmanApi を生成する場合, the ApiClientFactory shall BASE_URL を `BuildConfig.BASE_URL` から取得して Retrofit に設定する
2. The ApiClientFactory shall JSON デコーダを kotlinx.serialization で構成し、`ignoreUnknownKeys = true` を有効化する
3. The ApiClientFactory shall JSON デコーダで null フィールド（例: `feed_favicon_url` / `error_message` / `since_time`）を nullable プロパティへ正しくマップできるよう構成する
4. The ApiClientFactory shall OkHttp クライアント生成時に 0 個以上の interceptor および 0 個または 1 個の authenticator を外部から注入できる API を提供する
5. While 追加 interceptor / authenticator が注入されていない場合, the ApiClientFactory shall それらを付与せずに動作する FeedmanApi を返す
6. The ApiClientFactory shall 同一構成入力に対して再生成された FeedmanApi が同じエンドポイント契約で動作することを保証する（構成の決定性）

### Requirement 3: エラー応答の FeedmanException 変換

**Objective:** As a repository layer caller, I want non-2xx HTTP responses to be normalized into FeedmanException, so that 上位層は HTTP コードではなくサーバー定義の `code` で分岐できる

#### Acceptance Criteria

1. When エンドポイントが 2xx を返し本体が想定モデルである場合, the FeedmanApi shall デコード済みモデルを呼び出し元に返す
2. If エンドポイントが SPEC §4.3 の統一エラー本体（`{ "error": { "code", "message", "category", "action", "details"? } }`）を伴う非 2xx 応答を返した場合, the FeedmanApi shall サーバー定義の `code` / `message` / `category` / `action` を保持した FeedmanException を throw する
3. If 非 2xx 応答が 429 で `details.retry_after_seconds` を含む場合, the FeedmanApi shall その値を保持した FeedmanException を throw する
4. If 非 2xx 応答のボディが統一エラー形式としてデコードできない場合, the FeedmanApi shall 合成コード（NETWORK_ERROR 等の既定値）を持つ FeedmanException を throw し silent fail させない
5. If 通信中にネットワーク断や I/O 例外が発生した場合, the FeedmanApi shall 合成コードを持つ FeedmanException を throw する
6. The FeedmanException 変換層 shall HTTP ステータスコードを FeedmanException に保持して呼び出し元から参照可能にする

### Requirement 4: 拡張点（後続 Issue 用の構成）

**Objective:** As an upstream Issue author（#21 AuthInterceptor / #22 TokenAuthenticator）, I want the OkHttp client to accept additional interceptors and an authenticator without rewriting the factory, so that 認証層を本 Issue とは独立した PR で追加できる

#### Acceptance Criteria

1. The ApiClientFactory shall 追加 interceptor のリスト（順序保持）と単一 authenticator を引数または DI 経由で受け取る公開 API を提供する
2. When 1 個以上の追加 interceptor が注入された場合, the ApiClientFactory shall 全 HTTP リクエスト/レスポンスが当該 interceptor を経由する OkHttp クライアントを構築する
3. When authenticator が注入された場合, the ApiClientFactory shall その authenticator を OkHttp クライアントに登録する
4. The ApiClientFactory shall エラー変換層（Requirement 3）が認証 interceptor / authenticator の有無に関わらず一貫して動作するよう、変換層を OkHttp 拡張点とは独立した位置に配置する

## Non-Functional Requirements

### NFR 1: 変更範囲とテスト容易性

1. The 本 Issue の変更 shall `core/network` パッケージおよび `di`（Hilt module）に閉じ、`feature/*` および他の `core/*` サブパッケージ（`auth` / `data` / `ui` 等）のソースを変更しない
2. The FeedmanApi の動作 shall MockWebServer + 実 Retrofit インターフェースの組み合わせで検証可能（Retrofit インターフェースをモックしない CLAUDE.md テスト規約準拠）
3. The fixture JSON shall `app/src/test/resources/fixtures/` 配下に配置され、後続 Issue のテストから再利用できる

### NFR 2: スコープ境界

1. The 本 Issue shall `Authorization: Bearer` ヘッダの付与・401 応答時のリフレッシュ・Paging 3 基盤・記事状態オーバーレイ（ItemStateStore）を含めない
2. The ApiClientFactory shall BASE_URL を `BuildConfig.BASE_URL` 経由で取得し、ソースコードに固定値の URL を埋め込まない

## Out of Scope

- `Authorization: Bearer <access_token>` の付与（#21 で実装）
- 401 応答時の自動リフレッシュ / TokenAuthenticator（#22 で実装）
- TokenStore / EncryptedSharedPreferences によるトークン保管（別 Issue）
- Paging 3 ベースのカーソルページネーション基盤（#18 で実装）
- 各 Repository（ItemRepository / SubscriptionRepository ほか）の実装（後続機能 Issue）
- 記事状態の楽観的更新オーバーレイ ItemStateStore（後続 Issue）
- Coil の data URL 対応（`core/ui` 側 Issue）
- モックモード（`MOCK_MODE`）における Fake リポジトリ束ね（DI 側 Issue）

## Open Questions

- なし（API 契約は SPEC §4.2 / §4.3 を正本として転記。追加 interceptor / authenticator の DI 形態は Architect の領分とする）
