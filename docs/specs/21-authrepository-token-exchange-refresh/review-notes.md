# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-21-impl-authrepository
- HEAD commit: 784990f86615d56300764e188bd8e9427ea8fd92
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `AuthRepositoryImpl.exchange()` の成功パスで `tokenStore.save(toTokenSet(response))` を実行。
  テスト: `AuthRepositoryImplTest#Req 1-1 exchange success persists TokenSet to TokenStore`
  （`fixedClock(1_000_000ms) + expires_in(900s) = 1_900_000ms` を含む保存内容を assert）。
- 1.2 — `exchangeAuthToken(TokenExchangeRequest(authCode, codeVerifier))` を発行。
  テスト: `AuthRepositoryImplTest#Req 1-2` が POST `/api/auth/token` の body に
  `"auth_code":"code-X"` / `"code_verifier":"verifier-Y"` を含むことを検証。
- 1.3 — `mapExchangeException()` が ServerError を返し成功パス側でのみ save() を呼ぶため書き換え無し。
  テスト: `AuthRepositoryImplTest#Req 1-3` が 400 INVALID_GRANT で既存 TokenSet が維持されることを assert。
- 1.4 — `FeedmanException` の `CODE_NETWORK_ERROR` を `NetworkFailure` にマップ。
  テスト: `AuthRepositoryImplTest#Req 1-4`（`SocketPolicy.DISCONNECT_AT_START`）で TokenStore 維持を確認。
- 2.1 — `executeRefresh()` が保存済み refresh token を `RefreshTokenRequest` に詰めて送信。
  テスト: `AuthRepositoryImplTest#Req 2-1 Req 2-2` が body に `"refresh_token":"old-RT"` を含むことを assert。
- 2.2 — 同 success パスで `tokenStore.save(toTokenSet(response))` を実行（旧トークンを上書き）。
  テスト: 同上テストが `rotated-AT` / `rotated-RT` の上書きを検証。
- 2.3 / NFR 2.1 / 2.2 — `Mutex` + `CompletableDeferred<RefreshResult>` による単一飛行
  （`refreshInFlight` がリーダーのみ API 発行、フォロワーは `.await()`）。
  テスト: `AuthRepositoryImplTest#Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result`
  が 5 並行呼び出しで `server.requestCount == 1` かつ全結果 `Success` 同一を確認。
- 2.4 — `mapRefreshException()` の `CODE_INVALID_REFRESH_TOKEN || httpStatus == 401` ブランチで
  `tokenStore.clear()` + `isAuthenticated.value = false` + `AuthRequired` 返却。
  テスト: `AuthRepositoryImplTest#Req 2-4` が 401 INVALID_REFRESH_TOKEN で TokenStore null と
  `observeIsAuthenticated().value == false` を assert。
- 2.5 — `NETWORK_ERROR` 分岐で TokenStore を消さず `NetworkFailure` を返す。
  テスト: `AuthRepositoryImplTest#Req 2-5` が保存済み TokenSet の維持を確認。
- 2.6 — `executeRefresh()` 冒頭で `refreshToken.isNullOrBlank()` のとき API 発行せず
  `AuthRequired` を返し、`isAuthenticated.value = false`。
  テスト: `AuthRepositoryImplTest#Req 2-6` が `server.requestCount == 0` を assert。
- 3.1 — `revoke()` 内で `api.revokeAuthToken(RevokeTokenRequest(refreshToken))` を発行。
  テスト: `AuthRepositoryImplTest#Req 3-1 Req 3-2` が POST `/api/auth/revoke` と body の
  `"refresh_token":"RT"` を確認。
- 3.2 — `try { ... } catch (_: FeedmanException) { }` で握り潰し後、無条件で `tokenStore.clear()`。
  テスト: success / server 500 / network failure の 3 系統で TokenStore null を assert。
- 3.3 — refresh token 未保存時は API 発行スキップして clear のみ実行。
  テスト: `AuthRepositoryImplTest#Req 3-3` が `requestCount == 0` を assert。
- 4.1 — `AuthInterceptor.intercept()` が非除外パスで `Authorization: Bearer <accessToken>` を付与。
  テスト: `AuthInterceptorTest#Req 4-1 Bearer is attached when access token is stored` が
  `Bearer access-abc` の付与を確認。
- 4.2 — `accessToken.isNullOrBlank()` なら chain.proceed(request) で素通し。
  テスト: `AuthInterceptorTest#Req 4-2` + 境界ケース `Authorization header is omitted when stored access token is blank`。
- 4.3 — `isAuthExemptPath()` が `/api/auth/token` と `/api/auth/refresh` を完全一致で除外。
  テスト: `AuthInterceptorTest#Req 4-3 ... token exchange endpoint` と `Req 4-3 ... refresh endpoint`
  （いずれも Authorization が null）。/api/auth/revoke は Bearer 付与すべきと
  `Bearer is attached to revoke endpoint because revoke requires authentication` で確認。
- 5.1 — `currentUser()` が `userRepository.getCurrentUser()` 経由で `/auth/me` を呼ぶ。
  テスト: `AuthRepositoryImplTest#Req 5-1` が GET `/auth/me` + `Bearer AT` ヘッダ + User decode を assert。
- 5.2 — 401 UNAUTHORIZED でも `tokenStore.clear()` を呼ばず `Failure` で伝搬。
  テスト: `AuthRepositoryImplTest#Req 5-2` が `tokenStore.read()` が既存 TokenSet のままを確認。
- NFR 1.1 — TokenStore 抽象越しに read/save/clear のみ実行（暗号化責務は #20 完了済み TokenStore に閉じる）。
- NFR 1.2 — TokenSet は 1 オブジェクトを 1 回の `save()` で書き込む実装方針。
  exchange/refresh のサーバーエラー時に既存値が維持されることを Req 1.3 / 2.5 系テストで確認。
- NFR 2.1 / 2.2 — 上記 Req 2.3 と同一テストで担保。
- NFR 3.1 — `observeIsAuthenticated(): StateFlow<Boolean>` を公開。
  テスト: `NFR 3-1 observeIsAuthenticated transitions to true after exchange success` と
  `transitions to false after revoke`、加えて Req 2.4 / Req 3 系テストでも遷移を assert。

## Boundary 確認

変更ファイル一覧:

- `app/src/main/kotlin/com/feedman/android/core/auth/AuthInterceptor.kt`（新規）
- `app/src/main/kotlin/com/feedman/android/core/auth/AuthRepository.kt`（拡張）
- `app/src/main/kotlin/com/feedman/android/core/auth/AuthRepositoryImpl.kt`（新規）
- `app/src/main/kotlin/com/feedman/android/core/network/FeedmanApi.kt`（DTO + Retrofit 宣言追加）
- `app/src/main/kotlin/com/feedman/android/di/AuthModule.kt`（Binds + Clock provide）
- `app/src/main/kotlin/com/feedman/android/di/NetworkModule.kt`（AuthInterceptor 注入）
- `app/src/test/kotlin/com/feedman/android/core/auth/{AuthInterceptorTest,AuthRepositoryImplTest}.kt`
- `docs/specs/21-authrepository-token-exchange-refresh/{requirements,impl-notes}.md`

スコープ判定:

- すべて `core/auth` / `core/network` / `di` / `app/src/test` / spec docs に閉じている。
- `NetworkModule` は `authenticator = null` を明示しており、#22 の 401 自動リトライ
  （TokenAuthenticator）には踏み込んでいない。
- ログイン画面 / Custom Tabs（#23）に関わるファイル変更は無い。
- 既存の `SessionStateProvider` / `MockModeSessionStateProvider` / `EncryptedPrefsTokenStore` Binds は
  保持されたまま、追加で `bindAuthRepository` と `provideAuthClock` が並ぶ形（既存 binding 破壊なし）。

boundary 逸脱なし。

## Findings

なし。

## Summary

requirements.md の全 numeric AC（Req 1.1〜5.2 / NFR 1.1〜3.1）について、AuthRepositoryImpl /
AuthInterceptor / FeedmanApi DTO の実装と MockWebServer を用いた単体テスト 24 件で観測可能な
カバレッジが確認できた。並行 refresh の単一飛行は `server.requestCount == 1` を実 OkHttp 経路で
assert している。boundary は core/auth / core/network / di / app/src/test に閉じており、
#22 / #23 のスコープへの侵入はない。

RESULT: approve
