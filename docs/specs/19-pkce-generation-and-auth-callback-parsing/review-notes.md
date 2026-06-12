# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:45:00Z -->

## Reviewed Scope

- Branch: claude/issue-19-impl-pkce-generation-and-auth-callback
- HEAD commit: 410d00b
- Compared to: origin/main..HEAD
- 対象ファイル: `app/src/main/kotlin/com/feedman/android/core/auth/Pkce.kt` /
  `app/src/main/kotlin/com/feedman/android/core/auth/AuthCallback.kt` /
  `app/src/test/kotlin/com/feedman/android/core/auth/PkceTest.kt` /
  `app/src/test/kotlin/com/feedman/android/core/auth/AuthCallbackParserTest.kt` /
  `docs/specs/19-.../requirements.md` / `docs/specs/19-.../impl-notes.md`
- Feature Flag Protocol: `**採否**: opt-out` → flag 観点の判定は不実施

## Verified Requirements

### Requirement 1: PKCE ペア生成

- 1.1 — `PkceGenerator.generate()` が `PkcePair(codeVerifier, codeChallenge, method)`
  を返却（`Pkce.kt:69-73`）。テスト: `PkceTest`「Req 1_1 generate returns S256 pair
  with both fields populated」
- 1.2 — 64 バイト乱数を Base64URL(no padding) で 86 文字エンコード（`Pkce.kt:75-79`、
  `Pkce.kt:33` VERIFIER_RANDOM_BYTES=64）。テスト 3 本: 長さ 43..128、unreserved 文字集合
  正規表現、padding 不在
- 1.3 — `deriveCodeChallenge()` が SHA-256(ASCII bytes) → Base64URL no-padding
  （`Pkce.kt:93-97`）。テスト: **RFC 7636 Appendix B 既知ベクタ**
  (`dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`)
  と決定論的 stub 注入による Base64URL no-pad 検証の 2 本
- 1.4 — `PkceGenerator.default(random: SecureRandom = SecureRandom())` で `java.security.SecureRandom`
  を採用（`Pkce.kt:42-43`）
- 1.5 — `PkceTest`「Req 1_5 two consecutive ... distinct verifiers」と「ten consecutive ...
  all-unique verifiers」の 2 本
- 1.6 — `METHOD_S256 = "S256"` 定数化、`PkcePair.method` 既定値固定、generate API は
  常に S256 で構築。テスト「Req 1_6 method constant is exactly S256」

### Requirement 2: コールバック URI パース

- 2.1 — `AuthCallback.kt:38-68` の正常系。テスト: `feedman://auth/callback?auth_code=ABC123`
  → `Success("ABC123")`
- 2.2 — `extractAuthCode()`（`AuthCallback.kt:104-115`）が複数 query param を走査し
  `auth_code` のみ抽出。テスト: `state=xyz&auth_code=ABC123&debug=1`
- 2.3 — `AuthCallback.kt:49-51` の scheme 比較（case-insensitive）、`SchemeMismatch` 返却。
  テスト 2 本（`https://`、`Feedman://` の case 検証）
- 2.4 — `matchesHostAndPath()`（`AuthCallback.kt:77-92`）。`HostOrPathMismatch` を 3 ケース
  カバー: 違うホスト、違うパス、パス欠落（`feedman://auth?...`）
- 2.5 — `rawQuery == null` または `auth_code` キー欠落で `MissingAuthCode`
  （`AuthCallback.kt:57-61`）。テスト 2 本
- 2.6 — 空値（`auth_code=`）と key-only（`auth_code` のみ）の双方を `MissingAuthCode`
  に統合（`AuthCallback.kt:63-65, 108-112`）。テスト 2 本。要件文面が「2.5 と同等カテゴリ」を
  許容しているため統合は適合
- 2.7 — `URISyntaxException` / `IllegalArgumentException` を catch して `Malformed`
  （`AuthCallback.kt:39-46`）。テスト 2 本（不正 URI、空文字列）
- 2.8 — 自前の RFC 3986 percent-decoder（`AuthCallback.kt:122-146`）。テスト 3 本:
  `%2F`/`%2B`/`%3D` 復号、`+` リテラル保持（form-encoded 仕様ではない）、UTF-8 multibyte
  (`%E3%81%82` → `あ`)

### Non-Functional Requirements

- NFR 1.1 — 両実装は `java.security` / `java.net.URI` / `java.util.Base64` のみ。`android.*`
  import なし。JVM 単体テスト（`app/src/test/`）で全 AC を検証可能
- NFR 1.2 — `git diff --stat origin/main..HEAD` 確認結果、変更は `core/auth/Pkce.kt`・
  `core/auth/AuthCallback.kt`（新規）と対応テスト 2 本、および `docs/specs/19-*` のみ。
  `core/network` / `core/data` / `feature/*` / `shell` / `di` 等の他レイヤーへの変更なし
- NFR 2.1 — `PkceGenerator.default(random: SecureRandom = SecureRandom())` で乱数源を
  注入可能。テストで決定論的 stub を注入して挙動を検証
- NFR 2.2 — `AuthCallbackParser.parse(input: String)` は String のみ受け取り、Android
  Intent / Uri を引数にしない

### サーバー契約整合（design/SERVER.md §1.2-1.3）

- callback URI 形式 `feedman://auth/callback?auth_code=<one-time>` と一致
- PKCE 必須・S256 のみ・`code_challenge`/`code_verifier` の関係（SERVER.md §1.2 line 57）
  と一致
- 後続 Issue #21 (`POST /api/auth/token { auth_code, code_verifier }`) と #23 (Custom Tabs)
  のための公開 API として `PkcePair.codeVerifier` / `PkcePair.codeChallenge` /
  `AuthCallbackResult.Success.authCode` を提供

## 検証手段

- `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.auth.*"` →
  BUILD SUCCESSFUL（PkceTest 9 件 + AuthCallbackParserTest 16 件、計 25 件、failures=0 / errors=0）
- `git diff --stat origin/main..HEAD` で boundary 範囲を確認

## Findings

なし

## Summary

全 NumericID（Req 1.1-1.6 / Req 2.1-2.8 / NFR 1.1-1.2 / NFR 2.1-2.2）について実装と
テストが 1:1 で対応している。RFC 7636 Appendix B の既知ベクタによる SHA-256 → Base64URL
no-padding 検証、`+` リテラル保持を含む RFC 3986 準拠の percent-decoder、`SchemeMismatch` /
`HostOrPathMismatch` / `MissingAuthCode` / `Malformed` の 4 カテゴリ型付きエラーがすべて
網羅されている。boundary は `core/auth` パッケージと対応テストに完全に閉じており、他
レイヤーへの副作用なし。`./gradlew :app:testDebugUnitTest` も緑。

RESULT: approve
