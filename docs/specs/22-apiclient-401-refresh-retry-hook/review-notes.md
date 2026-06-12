# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-22-impl-401-refresh-retry
- HEAD commit: 5d244fe
- Compared to: origin/main..HEAD
- 変更ファイル: 5 件 / +835 -3
  - `app/src/main/kotlin/com/feedman/android/core/network/TokenAuthenticator.kt`（新規 135 行）
  - `app/src/main/kotlin/com/feedman/android/di/NetworkModule.kt`（+8/-3）
  - `app/src/test/kotlin/com/feedman/android/core/network/TokenAuthenticatorTest.kt`（新規 496 行）
  - `docs/specs/22-apiclient-401-refresh-retry-hook/requirements.md`
  - `docs/specs/22-apiclient-401-refresh-retry-hook/impl-notes.md`

## Verified Requirements

- 1.1 — `Req 1-1 transparent refresh and retry returns success response to caller`：401 → /api/auth/refresh → 再試行で `rotated-AT` が使われることを MockWebServer + PathDispatcher で確認
- 1.2 — `Req 1-2 caller receives the successful response transparently after refresh`：呼び出し元には 200 応答が透過的に返ることを確認
- 1.3 — `Req 1-3 caller receives non-401 error from retried request unchanged`：再試行で 404 が返った場合 `FeedmanException(code=NOT_FOUND, httpStatus=404)` が伝搬
- 1.4 — `Req 1-4 NFR 1-1 NFR 1-2 retry is limited to once per request`：再試行 401 で refresh 1 回 / /auth/me 2 回に限定。`responseCount halts retry beyond 1 even with chained priorResponse` で priorResponse 境界も追検証
- 2.1 — `Req 2-1 no refresh token in store skips refresh and propagates 401`：TokenStore 空時に /api/auth/refresh 呼び出し 0 回 / /auth/me 1 回のみ
- 2.2 — `Req 2-2 Req 2-3 Req 2-4 refresh 401 clears tokens flips session false...`：refresh 401（INVALID_REFRESH_TOKEN）応答後に `tokenStore.read() == null`
- 2.3 — 同上テストで `authRepository.observeIsAuthenticated().value == false` を assert（AuthRepositoryImpl 側の状態遷移は #21 で検証済、本 Issue は委譲）
- 2.4 — 同上テストで呼び出し元に `FeedmanException(httpStatus=401)` が伝搬し再試行ループに入らないことを確認
- 3.1 — `Req 3-1 Req 3-2 Req 3-4 concurrent 401s share single refresh and all retry once`：4 並行 + refresh `setBodyDelay(200ms)` で refresh は 1 回のみ
- 3.2 — 同テストで 4 件全てが新 access token (`shared-AT`) で再試行され 200 を受け取る
- 3.3 — impl-notes.md にて AuthRepositoryImplTest（#21）の `Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result` へ委譲を明記。本 Issue では `Req 2-2 ...` で refresh 失敗時に retry されないことを単独経路で確認
- 3.4 — 同テストで refresh 進行中の新規 401 が新たな refresh を開始しない（refresh 1 回計測）ことを確認
- 4.1 — `Req 1-1` で再試行が新 access token (`rotated-AT`) で送られ、`tokenStore.read().accessToken == "rotated-AT"` であることを確認。トークン保存自体は #21 の AuthRepositoryImpl で実装済（委譲）
- 4.2 — AuthRepositoryImpl の refresh 失敗系で AuthRequired 経路へ合流（#21 担保）。本 authenticator は `RefreshResult !is Success` で null を返す経路で同経路に乗ることを確認
- NFR 1.1 — `Req 1-4` で /auth/me 2 回 + /api/auth/refresh 1 回 = 計 3 回以内を assert
- NFR 1.2 — `Req 1-4` / `responseCount halts retry...` で再試行 401 時に 2 回目の refresh を開始しないことを確認
- NFR 2.1 — 「401→refresh 成功→200」「401→refresh 失敗→401」「並行 401→refresh 1 回→全件再試行」の 3 シナリオが MockWebServer + 実 OkHttp / Retrofit / AuthRepositoryImpl 経路で自動テスト化

加えて、認証エンドポイント自己除外の境界も `auth refresh endpoint is exempted...` / `auth token endpoint is exempted...` で確認（無限ループ防止）。

## Boundary 確認

- 変更領域: `core/network` + `di` + `app/src/test` + `docs/specs/22-*` のみ
- Issue #24 領域（`feature/*` の画面遷移・SessionState 購読 UI）への変更なし
- AuthRepository / TokenStore など #21 で確定した境界の書き換えなし（委譲のみ）

## Feature Flag Protocol 確認

CLAUDE.md `## Feature Flag Protocol` 節は `**採否**: opt-out` 宣言。`.claude/rules/feature-flag.md` の細目（旧パス温存 / `if (flag)` 分岐 / flag-off mutation / flag 命名）は本 Issue では適用対象外。

## テスト実行確認

- impl-notes.md: `./gradlew build` 成功（lint + debug/release unit test 含む 2m 27s）
- Reviewer 再実行: `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.network.TokenAuthenticatorTest"` → `BUILD SUCCESSFUL`（FROM-CACHE 経由で全 11 テスト green）

## Findings

なし。

## Summary

Req 1.1〜1.4 / 2.1〜2.4 / 3.1〜3.4 / 4.1〜4.2 / NFR 1.1〜1.2 / NFR 2.1 の全 numeric ID について、本 Issue の差分または #21 への明示的委譲のいずれかで観測可能なテストカバレッジが確認できた。boundary 逸脱なし、missing test なし。MockWebServer + 実 OkHttp/Retrofit/AuthRepositoryImpl 経路で `runBlocking` + `dagger.Lazy` を含む本番に近い結線が検証されており、CLAUDE.md「Retrofit インターフェースをモックしない」要件にも合致。

RESULT: approve
