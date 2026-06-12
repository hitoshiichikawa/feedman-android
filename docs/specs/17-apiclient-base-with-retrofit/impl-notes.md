# 実装ノート: Issue #17 APIClient base with Retrofit and kotlinx.serialization

## 概要

`core/network` 配下に Retrofit ベースの `FeedmanApi`、`ApiClientFactory`、
`FeedmanErrorMappingInterceptor`、`FeedmanApiProxy` を新規追加し、Hilt の
`NetworkModule` で配線した。SPEC §4.2 の全エンドポイントを suspend 関数として
宣言し、kotlinx.serialization（`ignoreUnknownKeys = true`）で decode する。
非 2xx 応答と IOException は #16 の `FeedmanErrorMapper` を経由して
`FeedmanException` に統一変換され、`FeedmanApi` 呼び出し元から直接 catch できる。

## 設計判断

### エラー変換層のアーキテクチャ（Interceptor + dynamic proxy）

要件 Req 3.x は「`FeedmanApi` 呼び出し元へ `FeedmanException` を throw する」ことを
求めるが、`FeedmanException` は `RuntimeException` のサブクラス（#16 で確定済み）
であり、OkHttp `Interceptor.intercept()` の throw 契約（`IOException` のみ）と
直接両立しない。検討した代替案は以下:

1. **Interceptor のみで `IOException` を throw（FeedmanException を捨てる）**
   - 上位の repository が `IOException` を catch して再変換する必要があり、SPEC §4.3
     の `code` 透過が repository 層に漏れる。**不採用**。
2. **`FeedmanException` が `IOException` を継承するように #16 を変更**
   - #16 既存テストが `ex as RuntimeException` を assert しており破壊変更。**不採用**。
3. **CallAdapter.Factory で suspend 呼び出しを wrap**
   - Retrofit 2.x の suspend サポートは内部 `KotlinExtensions.await()` が
     `Call.await()` を直接呼んでおり、外部 CallAdapter を suspend 関数の戻り経路に
     挿入するには Retrofit private API への hack が必要。**不採用**。
4. **Interceptor + dynamic proxy + Continuation 差し替え（採用）**
   - Interceptor が非 2xx を `FeedmanIOException`（`IOException` サブクラスで
     `FeedmanException` を運ぶ）として throw。
   - dynamic proxy（`Proxy.newProxyInstance`）が `FeedmanApi` の suspend 関数呼び出しを
     intercept し、JVM 上で末尾引数となる `Continuation<T>` を `ErrorConvertingContinuation`
     に差し替える。
   - Retrofit の `await()` が `cont.resumeWithException(FeedmanIOException)` を呼んだ
     瞬間に proxy 側 Continuation が unwrap して `FeedmanException` を rethrow する。

これにより、

- suspend 関数の呼び出し元からは `FeedmanException` を直接 catch できる（Req 3.2 / 3.5）
- 変換層が OkHttp の interceptor / authenticator チェーンと独立した位置に置かれ、
  認証層が後付けされても変換挙動は一貫する（Req 4.4）
- 既存 `FeedmanException` の型階層（RuntimeException）を維持できる（#16 後方互換）

### OkHttp interceptor の配置順

`addInterceptor` した順に application interceptor チェーンが構築され、最初に追加
した interceptor がレスポンス経路の最後に評価される。`FeedmanErrorMappingInterceptor`
を **先頭** に置くことで、後段で追加される `additionalInterceptors`（後続 Issue #21 の
`AuthInterceptor` 等）が Bearer 付与を済ませた **後** の最終レスポンスを観測して
エラー変換できる。

### Json 設定

`Json { ignoreUnknownKeys = true }` のみ明示し、その他はデフォルト挙動に任せた:

- `explicitNulls = true`（既定）: 値が `null` のフィールドは明示的に出力される
- `encodeDefaults = false`（既定）: 値がデフォルト値と一致するフィールドは省略される

`ItemStateUpdateRequest(isRead = true, isStarred = null)` の例では `isStarred` の値が
デフォルト `null` と一致するため body から省略される。これは SPEC §4.2 の
「`{ is_read?: bool|null, is_starred?: bool|null }`（部分更新）」契約と整合する。

### FeedmanApi に request body 型を同居

`SubscriptionSettingsRequest` / `RegisterFeedRequest` / `PatchFeedRequest` /
`ItemStateUpdateRequest` の 4 つは FeedmanApi の引数型として完結するため、
新規ファイルを切らず `FeedmanApi.kt` 末尾に集約した。core/model 側にあると
v1 スコープ外の `PatchFeedRequest` 等が混ざるため、network 層で抱え込む。

## 追加した依存ライブラリ

- `com.squareup.okhttp3:mockwebserver` を `gradle/libs.versions.toml` の `[libraries]`
  に追加（okhttp と同バージョン `4.12.0`）。testImplementation として配線。
  Issue #17 の APIClient テストで実 HTTP 経路を回すために必要。

既に Version Catalog で宣言済みだった以下を `app/build.gradle.kts` に implementation
配線:

- `com.squareup.retrofit2:retrofit:2.11.0`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `com.squareup.okhttp3:logging-interceptor:4.12.0`
- `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0`

## requirement ID → テスト対応表

すべて `app/src/test/kotlin/com/feedman/android/core/network/FeedmanApiTest.kt`。
`FeedmanErrorMapper` の単体検証（Req 3 の純粋関数部分）は #16 の
`FeedmanErrorMapperTest` に既存。

| Req ID | テスト | 検証観点 |
|---|---|---|
| Req 1.1 | `Req 1-1 cross-feed endpoint decodes 200 response to CrossFeedPage` | suspend 関数 / SPEC §4.2 GET `/api/items/cross-feed` のシグネチャと decode |
| Req 1.1 | `Req 1-3 feed items endpoint decodes Page of ItemSummary` | `/api/feeds/{id}/items` |
| Req 1.1 | `Req 1-3 item detail endpoint decodes ItemDetail with content and author` | `/api/items/{id}` |
| Req 1.1 | `Req 1-3 subscriptions list endpoint decodes List of Subscription` | `/api/subscriptions` |
| Req 1.1 | `Req 1-3 user endpoint decodes User ignoring server-side extra fields` | `/auth/me` |
| Req 1.2 | `Req 1-2 cross-feed accepts cursor and limit query parameters` | 一覧系の cursor / limit |
| Req 1.3 | `Req 1-3 ...`（上記 4 件） | `@Serializable` データクラスへの decode |
| Req 1.4 | `Req 1-4 update item state accepts nullable is_read and is_starred fields` | `PUT /api/items/{id}/state` body の nullable 部分更新 |
| Req 1.4 | `Req 1-4 update item state with both fields non-null sends both` | 両フィールド送信 |
| Req 1.5 | `Req 1-5 logout and delete user and update last seen all reach correct endpoints` | `/auth/logout` / `DELETE /api/users/me` / `PUT .../cross-feed-last-seen` |
| Req 1.5 | `Req 1-5 subscription fetch endpoint is POST and reaches correct path` | `POST /api/subscriptions/{id}/fetch` |
| Req 2.1 | NetworkModule（コード）+ `Req 2-6` テスト | `BuildConfig.BASE_URL` 経由の baseUrl 注入 |
| Req 2.2 | `Req 2-2 decoder ignores unknown JSON fields without throwing` | `ignoreUnknownKeys = true` |
| Req 2.3 | `Req 2-3 decoder maps explicit null to nullable property` | null → nullable プロパティのマップ |
| Req 2.4 | `Req 4-2 additional interceptor is invoked for each request` | 追加 interceptor 注入 API の存在 |
| Req 2.4 | `Req 4-1 interceptor can mutate request headers and they reach the server` | interceptor が request を改変できる |
| Req 2.5 | `Req 2-5 with no extra interceptor or authenticator the api still works` | 注入なしでも動作 |
| Req 2.6 | `Req 2-6 same input produces FeedmanApi with consistent endpoint contract` | 再生成の決定性 |
| Req 3.1 | 全 200 系テスト（`Req 1-1`, `Req 1-3` 他） | 2xx は decode 済みモデルを返す |
| Req 3.2 | `Req 3-2 non-2xx with standard error body throws FeedmanException with server fields` | サーバー定義の `code`/`message`/`category`/`action` 保持 |
| Req 3.3 | `Req 3-3 429 with details retry_after_seconds is preserved on FeedmanException` | `details.retry_after_seconds` 保持 |
| Req 3.4 | `Req 3-4 non-2xx with malformed body falls back to UNKNOWN_ERROR code` | 合成 code フォールバック |
| Req 3.5 | `Req 3-5 IOException during request becomes NETWORK_ERROR FeedmanException` | IOException → NETWORK_ERROR |
| Req 3.6 | `Req 3-6 HTTP status code is exposed on FeedmanException for downstream branching` | HTTP status code 透過 |
| Req 4.1 | `Req 4-1 multiple interceptors are invoked in registration order` | 順序保持 |
| Req 4.2 | `Req 4-2 additional interceptor is invoked for each request` | 全リクエストが interceptor を経由 |
| Req 4.3 | （NetworkModule + ApiClientFactory シグネチャ、`Req 4-4` テスト間接的検証） | authenticator 引数の存在（実 401 リフレッシュは #22 で検証） |
| Req 4.4 | `Req 4-4 error conversion still works even when an authenticator-like interceptor is present` | 拡張点の有無に関わらずエラー変換が一貫 |
| NFR 1.1 | コード変更範囲（`core/network` + `di/NetworkModule.kt` + build 配線のみ） | feature/* 等を変更していない |
| NFR 1.2 | 全テストが MockWebServer ベース | Retrofit インターフェースをモックしない |
| NFR 1.3 | テストは既存 `app/src/test/resources/fixtures/` を再利用 | fixture 集約 |
| NFR 2.1 | （未実装範囲） | Bearer / 401 / Paging / ItemStateStore は別 Issue |
| NFR 2.2 | NetworkModule（コード）| `BuildConfig.BASE_URL` 経由でのみ baseUrl を取得 |

## 確認事項（レビュワー向け）

1. **dynamic proxy + Continuation 差し替えという hack 度の高い手法を採用しているが、
   後続 #21 / #22 で AuthInterceptor / TokenAuthenticator を後付けする際に、
   この変換層と意図せず競合しないか**。
   - `additionalInterceptors` リストに渡された interceptor は OkHttp 拡張点側で動作し、
     dynamic proxy 層には触れないため設計上は競合しない。
   - 401 リフレッシュ後の再試行も OkHttp `Authenticator` の責務で完結し、proxy 層
     からは 1 回の呼び出しに見える。
2. **`FeedmanIOException` を `core/network` の `internal` に置いたが、後続 Issue が
   独自 Interceptor から throw したくなる可能性**。
   - その場合は public 化を別 PR で行う。
3. **Json の `encodeDefaults = false` のままで PUT body から null フィールドが省略される
   挙動は SPEC §4.2 の「`is_read?: bool|null`」契約と整合するが、サーバーが「明示的に
   null を送ったときだけ「変更しない」と解釈する」実装でないかを Architect 段で
   再確認したい**。
   - 現状の `ItemStateUpdateRequest`（両フィールドが `Boolean? = null` デフォルト）では、
     null 値はフィールド省略として表現される。サーバー側が `null` 明示と省略を別扱い
     するなら `encodeDefaults = true` への変更が必要だが、これは Issue #18 以降の
     `ItemRepository` 実装時に SPEC とサーバー実装を突き合わせる。
4. **MockWebServer のバージョンが okhttp と同じ系列であることを `chore(deps)` commit で
   確認済み**だが、CI 等で別系列にズレた場合の影響は ApiClientFactory のテストが
   即座に検知できる範囲。

## 派生タスク候補

- Issue #18（Paging 3 基盤）で `core/network/paging/` を新設するときに、cursor 抽出と
  `has_more` 判定の共通 PagingSource を本 `FeedmanApi` をもとに実装する
- Issue #21 で AuthInterceptor を実装する際、`NetworkModule` の `additionalInterceptors`
  リストへの追加方法（Hilt の Multibinding か `@Provides` 差し替えか）を決定する
- Json の `encodeDefaults` 検討（上記「確認事項 3」）

## ビルド結果

```
$ ./gradlew build
BUILD SUCCESSFUL in 13m 45s
119 actionable tasks: 94 executed, 8 from cache, 17 up-to-date
```

すべての JVM 単体テスト pass / lint pass / kspDebug + kspRelease pass。

STATUS: complete
