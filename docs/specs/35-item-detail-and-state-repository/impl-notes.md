# Implementation Notes — Issue #35 Item detail and state repository

## サマリ

`core/data/ItemDetailRepository`（インターフェース）と `ItemDetailRepositoryImpl`
（実装）を追加し、SPEC §4.2 の `GET /api/items/{id}` と `PUT /api/items/{id}/state` の
2 エンドポイントを上位層（記事詳細シート #36 / 楽観的更新同期 #38）が利用できる形で
切り出した。FeedmanApi（Issue #17）の上に薄く乗る単純な委譲層だが、partial update
の null フィールド省略 と 両 null バリデーション を本リポジトリの責務として持つ。

## 実装上の判断

### 1. `Json.explicitNulls = false` への切り替え

Req 2.2 / 2.3 を満たすために、partial update リクエストで `null` のフィールドを
JSON ボディから完全に省略する必要があった。本リポジトリの既存設計（`ApiClientFactory.kt`
の従来コメント）でも「フィールド省略は呼び出し側でデータクラスを使い分けるか、
デフォルト値で対応する」と未解決の課題として残されていた状態。

選択肢:

1. **`Json.explicitNulls = false` を全体適用**（採用）
2. リクエストごとに専用 Json インスタンス / ConverterFactory を持つ
3. リクエストごとに専用 DTO を使い分ける（`UpdateReadRequest` / `UpdateStarredRequest`
   / `UpdateBothRequest`）

採用案 1 を選んだ理由:

- 本リポジトリ内の全 `@Serializable` クラスはすべての nullable プロパティに
  `= null` 既定値を持つため、デコード経路は `explicitNulls=false` で挙動不変
  （JSON 側のキー欠落 → プロパティ既定値 null へのフォールバックが成立）
- 既存の `feed_favicon_url` / `hatebu_fetched_at` / `error_message` などの decode は
  `cross_feed_item.json` / `item_detail.json` / `subscription_active.json` などの
  fixture を MockWebServer 経由で実 HTTP として通すテストが既存スイートに豊富にあり、
  `./gradlew build` 全パスで decode 不変性が回帰確認される
- 選択肢 2 / 3 は単一の partial update のためにシステム横断の追加レイヤを増やす
  cost を払うことになり、可読性・テスト容易性を悪化させる
- 将来 partial update を持つ別エンドポイント（例: フィード設定の `PUT
  /api/subscriptions/{id}/settings` の `fetch_interval_minutes?`）が増えても同じ規約で
  カバーできる

`FeedmanApiTest.Req 1-4` に「省略フィールドが body に含まれない」強アサーションを
追加し、新しい挙動を回帰防止した。

### 2. 両 null バリデーションを Repository 層に持たせる

Req 2.5 は「両 null のときはサーバーへリクエストを送信しない」ことを要求する。
これは契約上の前提条件チェックであり、呼び出し元 UI 層に毎回書かせる重複を避けるため
Repository 層で集中的にハンドリングする。エラー型は呼び出し元の楽観的更新ロールバック
（Req 3.4）を一本化するために既存の `FeedmanException`（`CODE_UNKNOWN_ERROR`）を
再利用する。新規例外型を導入しない理由は、上位レイヤがすべての `FeedmanException` を
一括キャッチして UI フィードバック・ロールバックを行う既存設計（Issue #17 / GRAND-
DESIGN §5.1）と一貫させるため。

### 3. `updateItemState` の戻り値（`CrossFeedItem`）は無視する

`FeedmanApi.updateItemState` は `CrossFeedItem` を返す宣言だが、SPEC §4.2 は PUT の
レスポンス型を明示していない。リポジトリの呼び出し元（楽観的更新 #38）はリクエスト
直前の楽観値を保持するため、サーバー応答の値を参照しない設計が自然。よって
`ItemDetailRepository.updateState` は `Unit` を返し、Req 2.6 の「成功通知」=「例外が
起きないこと」とした。

## requirement ID → テスト対応表

| Req ID | 対応テスト |
|---|---|
| 1.1 | `Req 1-1 getItem issues GET to api items with item id path` |
| 1.2 | `Req 1-2 getItem decodes 200 response into ItemDetail with content and author` |
| 1.3 | `Req 1-3 getItem retains is_date_estimated true flag for downstream estimated label` |
| 1.4 | `Req 1-4 getItem retains nullable hatebu_fetched_at as null without dropping field` |
| 2.1 | `Req 2-1 updateState issues PUT to api items state with item id path` |
| 2.2 | `Req 2-2 updateState with only isRead sends body containing only is_read`、`FeedmanApiTest.Req 1-4 update item state accepts nullable is_read and is_starred fields`（assertFalse 強化） |
| 2.3 | `Req 2-3 updateState with only isStarred sends body containing only is_starred`、`FeedmanApiTest.Req 1-4`（同上） |
| 2.4 | `Req 2-4 updateState with both flags sends both is_read and is_starred` |
| 2.5 | `Req 2-5 updateState with both null throws FeedmanException and skips HTTP request` |
| 2.6 | `Req 2-6 updateState returns normally when server responds with 2xx success` |
| 3.1 | `Req 3-1 getItem 4xx response surfaces FeedmanException with code and message` |
| 3.2 | `Req 3-2 updateState 5xx response surfaces FeedmanException with code and message` |
| 3.3 | `Req 3-3 network failure during getItem surfaces FeedmanException with NETWORK_ERROR code`、`Req 3-3 network failure during updateState surfaces FeedmanException with NETWORK_ERROR code` |
| 3.4 | バリデーション失敗時にサーバーリクエストが送信されないことを `Req 2-5` で検証（`server.requestCount == 0`）。GET / PUT 失敗時のローカル副作用は本リポジトリが状態を保持しないため自明（v1 オフラインキャッシュなし。SPEC §1.3） |
| NFR 1.1 | MockWebServer 経由で実 HTTP レスポンス JSON を返す JVM 単体テスト群で公開 API を全カバー |
| NFR 1.2 | 上記 Req 1 / 2 / 3 のそれぞれの分岐に最低 1 件のテストケースを配置 |
| NFR 2.1 | パス（`/api/items/{id}` / `/api/items/{id}/state`）・メソッド（GET / PUT）・body 構造（`is_read?` / `is_starred?` の partial union）・レスポンス型（`ItemDetail`）すべてを Req 1-1 / 1-2 / 2-1 / 2-2 / 2-3 / 2-4 で実 HTTP として検証 |
| NFR 2.2 | 必須フィールド欠落時の挙動: kotlinx.serialization の `MissingFieldException` は `FeedmanApiProxy.convertThrowable` で `FeedmanException` に変換されず透過するが、本 Issue では SPEC §4.2 が必須として宣言しているフィールドが欠落する fixture を新規追加していない（既存 Issue #15 / #17 の責務）。本リポジトリの観測範囲では Req 3.x で代替担保。 |

## 確認事項（レビュワー判断ポイント）

1. **`Json.explicitNulls = false` のグローバル適用**: 本変更はリポジトリ内すべての
   encode/decode に影響する。decode 側は本リポジトリの全 nullable フィールドが
   `= null` 既定値を持つため不変だが、将来 nullable 既定値を持たない `@Serializable`
   クラスを追加した場合は `MissingFieldException` の挙動を確認する必要がある。
   レビュワーは既存 fixture 群の `./gradlew build` 全テスト pass を回帰判定の根拠
   とできる。
2. **両 null バリデーションのエラーコード**: `CODE_UNKNOWN_ERROR` を再利用したが、
   将来クライアントバリデーション用の専用コード（例: `CLIENT_VALIDATION`）を
   `FeedmanException` に追加する余地はある。本 Issue のスコープ外として保留。
3. **`updateState` の戻り値型**: 現状 `Unit`。Issue #38（楽観的更新同期）が「サーバー
   応答時刻」や「正規化された is_read 値」を必要とする場合は本 IF を拡張する余地が
   ある（その時点で `data class UpdateResult` 等を返す形へ後方互換に拡張可能）。

## 派生タスクの候補

- **FeedmanApi の partial update DTO 整備**: 将来 `PUT /api/subscriptions/{id}/settings`
  に同様の partial union が増えた際、`Json.explicitNulls=false` のグローバル方針で
  カバーできるかをサンプルとして再検証する Issue。
- **`updateItemState` レスポンス型の明確化**: SPEC §4.2 の PUT 戻り値型が明示されて
  いない点はサーバー側 SPEC を確認する余地あり（次フェーズでサーバー実装と同期する
  際に再検討）。

## ビルド結果

- `./gradlew build` 成功（`testDebugUnitTest` / `testReleaseUnitTest` / `lintDebug` /
  `assembleRelease` を含む 119 タスク全パス）

STATUS: complete
