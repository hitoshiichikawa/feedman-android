# Implementation Notes — Issue #21 AuthRepository token exchange / refresh / revoke

本実装ノートは `requirements.md` の各 numeric requirement ID が、どの実装・テストで担保され
たかのトレーサビリティを記録する。

## 成果物

### 新規ファイル

- `app/src/main/kotlin/com/feedman/android/core/auth/AuthRepositoryImpl.kt`
  - [AuthRepository] の本実装。exchange / refresh / revoke / currentUser / observeIsAuthenticated。
  - 単一飛行は `Mutex` + `CompletableDeferred<RefreshResult>` で実現。
- `app/src/main/kotlin/com/feedman/android/core/auth/AuthInterceptor.kt`
  - 認証必須リクエストに `Authorization: Bearer <access_token>` を付与する OkHttp interceptor。
  - 認証不要エンドポイント（`/api/auth/token`, `/api/auth/refresh`）を path で除外。
- `app/src/test/kotlin/com/feedman/android/core/auth/AuthRepositoryImplTest.kt`（18 テスト）
- `app/src/test/kotlin/com/feedman/android/core/auth/AuthInterceptorTest.kt`（6 テスト）

### 改修ファイル

- `app/src/main/kotlin/com/feedman/android/core/auth/AuthRepository.kt`
  - 空 interface（#1 の skeleton）を本実装契約に拡張。`ExchangeResult` / `RefreshResult` /
    `CurrentUserResult` の sealed result 型を追加。
- `app/src/main/kotlin/com/feedman/android/core/network/FeedmanApi.kt`
  - `exchangeAuthToken` / `refreshAuthToken` / `revokeAuthToken` の Retrofit 宣言と、
    `TokenExchangeRequest` / `RefreshTokenRequest` / `RevokeTokenRequest` / `TokenResponse` DTO 追加。
- `app/src/main/kotlin/com/feedman/android/di/NetworkModule.kt`
  - `additionalInterceptors = listOf(authInterceptor)` で配線。
- `app/src/main/kotlin/com/feedman/android/di/AuthModule.kt`
  - `AuthRepositoryImpl` を `AuthRepository` にバインド。`Clock.systemUTC()` を provide。

## 設計判断

### 1. 認証エンドポイントを既存 `FeedmanApi` に統合し、専用 Retrofit インターフェースを作らなかった

候補:
- **(A) 専用 `FeedmanAuthApi` interface + 別 OkHttpClient（AuthInterceptor 無し）**
- **(B) 既存 `FeedmanApi` に追加 + `AuthInterceptor` 側で path 除外**

採用は **(B)**。理由:
- `FeedmanApiProxy.wrap()` の dynamic proxy / `FeedmanErrorMappingInterceptor` の恩恵
  （非 2xx → `FeedmanException` への一元変換）を全エンドポイントで等しく受けたい。
- (A) を選ぶと proxy / error mapping / Retrofit converter を 2 系統メンテすることになり、
  契約変更時の差分が二重に発生する。
- 401 ループ懸念は (B) でも `AuthInterceptor.isAuthExemptPath` が `/api/auth/refresh` で
  Bearer を付けないことで解消（path 完全一致でテスト済み）。
- `/api/auth/revoke` は SERVER.md §1.3 で「Bearer 認証下」と明記されているため、除外しない。
  これも `AuthInterceptorTest#Bearer is attached to revoke endpoint ...` で担保。

### 2. `currentUser()` は `UserRepository` 経由で `GET /auth/me` を呼ぶ（重複しない）

`AuthRepositoryImpl` は `UserRepository` をコンストラクタ注入し、`getCurrentUser()` を委譲する。
重複した `GET /auth/me` 実装を避けるため。`UserRepositoryImpl` 自体は #49 で実装済み。

`AuthRepositoryImpl.currentUser()` の責務は (1) `FeedmanException` の `CurrentUserResult` への
マップ、(2) NETWORK_ERROR / Failure の分岐のみ。Req 5.2 に従い、認証エラー時の TokenStore 消去
は本機能の責務には含めない（#22 のセッション層で処理する）。

### 3. refresh の単一飛行: `Mutex` + `CompletableDeferred` のハイブリッド

`Mutex.withLock` だけだと並行呼び出しが順次直列実行されるが「同じ結果を共有」する要件
（NFR 2.2）を満たすため、`refreshInFlight: CompletableDeferred<RefreshResult>?` を保護対象に
してリーダーが API 呼び出し、フォロワーが `.await()` する構造にした。

- Mutex は state（refreshInFlight）の読み書きのみを保護（短時間）
- ネットワーク I/O は Mutex 外で実行（refresh 中も他の suspend 関数をブロックしない）
- リーダー完了時に `refreshInFlight = null` をリセットし、`deferred.complete(result)` で全フォロワーに同一結果を配布

並行テスト（`Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result`）
で `server.requestCount == 1` を assertion している。

### 4. 観測可能なログイン状態 `observeIsAuthenticated(): StateFlow<Boolean>`

NFR 3.1「ログイン中 / 未ログイン判定の手段」の具体形として、`StateFlow<Boolean>` を提供。

- exchange 成功 → `true`
- refresh 成功 → `true`
- refresh AuthRequired (INVALID_REFRESH_TOKEN / refresh token 無し) → `false`
- revoke 完了 → `false`

初期値は `false`。`refreshAuthenticatedState()` という suspend init フックを提供しており、
上位レイヤ（#22 の AuthSession 等）が起動直後に呼ぶことで TokenStore の永続値を反映できる。

### 5. `Clock` 注入による expires_at 計算のテスト容易性

`TokenResponse.expires_in` (秒) を端末ローカル時刻 (epochMillis) に変換する箇所で
`Clock` を注入。テストでは `Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)` を渡し、
保存された `accessTokenExpiresAtEpochMillis` の計算結果を決定的に検証している。

### 6. `runBlocking` の使用範囲

`AuthInterceptor` 内で `runBlocking { tokenStore.read()?.accessToken }` を 1 回だけ呼ぶ。
OkHttp interceptor の同期契約と TokenStore の suspend 契約の橋渡しに必要最小限の使用。
範囲は 1 read のみで、decode / 例外ハンドリングを含まない。OkHttp Dispatcher の専用スレッドで
実行されることを前提とする（UI スレッドからは呼ばない）。

## Requirement ID → テスト対応表

| Requirement | 担保したテスト |
|---|---|
| Req 1.1 (exchange 成功で TokenStore 保存) | `AuthRepositoryImplTest#Req 1-1 exchange success persists TokenSet to TokenStore` |
| Req 1.2 (リクエスト body に auth_code / code_verifier) | `AuthRepositoryImplTest#Req 1-2 exchange sends auth_code and code_verifier in request body` |
| Req 1.3 (exchange サーバーエラー時に TokenStore 書き換え無し) | `AuthRepositoryImplTest#Req 1-3 exchange server error does not write TokenStore` |
| Req 1.4 (exchange ネットワーク失敗時に TokenStore 維持) | `AuthRepositoryImplTest#Req 1-4 exchange network failure preserves TokenStore` |
| Req 2.1 (保存済み refresh token を送信) | `AuthRepositoryImplTest#Req 2-1 Req 2-2 refresh rotates TokenSet using stored refresh token` |
| Req 2.2 (ローテーション結果で上書き保存) | 同上 |
| Req 2.3 (単一飛行で同一結果共有) | `AuthRepositoryImplTest#Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result` |
| Req 2.4 (INVALID_REFRESH_TOKEN で消去 + AuthRequired) | `AuthRepositoryImplTest#Req 2-4 INVALID_REFRESH_TOKEN clears TokenStore and returns AuthRequired` |
| Req 2.5 (refresh ネットワーク失敗時 TokenStore 維持) | `AuthRepositoryImplTest#Req 2-5 refresh network failure preserves TokenStore` |
| Req 2.6 (refresh token 未保存時 net 発行せず AuthRequired) | `AuthRepositoryImplTest#Req 2-6 refresh without stored refresh token does not issue network request and returns AuthRequired` |
| Req 3.1 (revoke 成功で TokenStore 消去) | `AuthRepositoryImplTest#Req 3-1 Req 3-2 revoke success clears TokenStore` |
| Req 3.2 (revoke 失敗でも TokenStore 消去 / best-effort) | `AuthRepositoryImplTest#Req 3-2 revoke server error still clears TokenStore (best effort)` + `Req 3-2 revoke network failure still clears TokenStore (best effort)` |
| Req 3.3 (refresh token 未保存時 net 発行せず消去のみ) | `AuthRepositoryImplTest#Req 3-3 revoke without stored refresh token issues no network request but clears store` |
| Req 4.1 (アクセストークン保存時 Bearer 付与) | `AuthInterceptorTest#Req 4-1 Bearer is attached when access token is stored` |
| Req 4.2 (アクセストークン未保存時 Authorization 付与せず) | `AuthInterceptorTest#Req 4-2 Authorization header is omitted when no access token is stored` + 境界 `Authorization header is omitted when stored access token is blank` |
| Req 4.3 (token 交換 / refresh には Bearer 付与せず) | `AuthInterceptorTest#Req 4-3 Authorization header is not attached to token exchange endpoint` + `Req 4-3 ...refresh endpoint` |
| Req 5.1 (currentUser → me エンドポイント) | `AuthRepositoryImplTest#Req 5-1 currentUser fetches me endpoint and returns user` |
| Req 5.2 (auth エラーを伝搬 / 本機能では消去しない) | `AuthRepositoryImplTest#Req 5-2 currentUser propagates auth error to caller without clearing TokenStore` |
| NFR 1.1 (平文の永続ストアに書き出さない) | TokenStore の暗号化責務は #20 で完了済み。本 Issue では `TokenStore` 抽象越しに save/read/clear を呼ぶのみ。 |
| NFR 1.2 (部分書き込み残らない / 両方更新 or 両方据え置き) | `TokenSet` を 1 つの `save()` で書き込む実装方針。exchange サーバーエラー時に既存維持を `Req 1-3` で確認。 |
| NFR 2.1 / 2.2 (単一飛行 + 同一結果) | `AuthRepositoryImplTest#Req 2-3 ...` |
| NFR 3.1 (観測可能なログイン状態) | `AuthRepositoryImplTest#NFR 3-1 observeIsAuthenticated transitions to true after exchange success` + `transitions to false after revoke` + Req 2.4 / Req 3 系の observe 値検証 |

## 確認事項（レビュワー判断ポイント）

1. **`AuthRepositoryImpl.refreshAuthenticatedState()` 呼び出し責務**: 本実装は構築直後の
   `isAuthenticated` 初期値を `false` にする。上位レイヤ（#22 系の AuthSession）がアプリ起動直後に
   `refreshAuthenticatedState()` を呼んで TokenStore の現状を反映する想定。Issue #21 のスコープでは
   呼び出し責務は上位レイヤに委ねている（本 Issue では `refresh()` / `exchange()` / `revoke()` 経由の
   遷移のみ保証）。

2. **revoke のサーバー失敗を握り潰す範囲**: `FeedmanException` のみ catch している（`Throwable` 全捕捉は
   しない）。FeedmanApi 経由のすべての非 2xx / I/O 失敗は `FeedmanException` に変換済みなので
   実用上問題ないが、`TokenStoreException` で `tokenStore.clear()` が失敗した場合は伝搬する設計。

3. **DI 配線の循環確認**: `FeedmanApi` → `AuthInterceptor` → `TokenStore`、`AuthRepositoryImpl` →
   `FeedmanApi` + `TokenStore` + `UserRepository` + `Clock`。Dagger 上の循環は無い（Hilt build が
   成功）。`UserRepositoryImpl` → `FeedmanApi` も既存どおり。

4. **expires_at の clock source**: 本実装では `Clock.systemUTC()` を `@Provides` で配線。
   `TokenSet.accessTokenExpiresAtEpochMillis` は SPEC のとおりエポックミリ秒で保存する規約に従う。

## 補足

- ktlint は Gradle タスクとして登録されていなかったため、`./gradlew build` の `:app:lint` で
  Android Lint のみが実行される（lint 警告は 0）。
- ./gradlew build SUCCESSFUL（118 tasks）。
- 全 AC が単体テストでカバーされ、新規 24 テストすべて green。既存テスト群も green。

STATUS: complete
