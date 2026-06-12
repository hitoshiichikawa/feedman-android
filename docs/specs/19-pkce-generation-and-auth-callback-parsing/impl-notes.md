# 実装メモ — Issue #19 PKCE generation and auth callback parsing

## 成果物

- `app/src/main/kotlin/com/feedman/android/core/auth/Pkce.kt`
  - `PkceGenerator` インターフェース（`generate()` / `METHOD_S256` 定数）と `PkcePair` data class
  - `PkceGenerator.default(random: SecureRandom)` で乱数源を注入可能（NFR 2.1）
  - 既定実装 `DefaultPkceGenerator` は 64 バイト乱数 → Base64URL(no padding) で `code_verifier`
    （86 文字、RFC 7636 §4.1 の 43..128 範囲内）を生成
  - `deriveCodeChallenge(verifier)` 関数（package-internal）で SHA-256(ASCII バイト) →
    Base64URL(no padding) を計算
- `app/src/main/kotlin/com/feedman/android/core/auth/AuthCallback.kt`
  - `AuthCallbackParser.parse(input: String): AuthCallbackResult` の object API
  - `AuthCallbackResult` sealed class（`Success(authCode)` / `Failure(error)`）+
    `AuthCallbackError` enum（`Malformed` / `SchemeMismatch` / `HostOrPathMismatch` /
    `MissingAuthCode`）の型付きエラー（CLAUDE.md コード規約「silent fail を作らない」/ Req 2.3..2.7）
  - `java.net.URI` ベースで android.net.Uri 非依存（NFR 1.1）
  - 自前の RFC 3986 percent-decoder を実装（`URLDecoder` は form-encoded 扱いで `+` を space
    に変換するため非採用。OAuth callback は generic URI なので `+` を literal として保持する）
- `app/src/test/kotlin/com/feedman/android/core/auth/PkceTest.kt`
- `app/src/test/kotlin/com/feedman/android/core/auth/AuthCallbackParserTest.kt`

## requirement ID → テスト対応表

### Requirement 1: PKCE ペア生成

| Req ID | 検証テスト |
|---|---|
| 1.1 | `PkceTest`「Req 1_1 generate returns S256 pair with both fields populated」 |
| 1.2 | `PkceTest`「Req 1_2 generated verifier sits within 43 to 128 characters」/「Req 1_2 generated verifier uses only RFC 7636 unreserved characters」/「Req 1_2 verifier has no Base64 padding character」 |
| 1.3 | `PkceTest`「Req 1_3 RFC 7636 Appendix B sample verifier produces documented challenge」（既知ベクタ `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`）/「Req 1_3 challenge is Base64URL no-padding of SHA-256 of injected verifier」 |
| 1.4 | 既定実装は `java.security.SecureRandom`（暗号論的乱数源）を使用。`PkceGenerator.default()` のシグネチャ（`SecureRandom = SecureRandom()`）で公開され、テスト用には決定論的 stub を注入できる構造 |
| 1.5 | `PkceTest`「Req 1_5 two consecutive generate calls return distinct verifiers」/「Req 1_5 ten consecutive generate calls return all-unique verifiers」 |
| 1.6 | `PkceTest`「Req 1_6 method constant is exactly S256」+ コード上 `PkcePair.method` は `METHOD_S256` 既定で他の方式を提供しない（API 自体に S256 以外の入口なし） |

### Requirement 2: コールバック URI パース

| Req ID | 検証テスト |
|---|---|
| 2.1 | `AuthCallbackParserTest`「Req 2_1 canonical callback returns Success with the auth_code value」 |
| 2.2 | `AuthCallbackParserTest`「Req 2_2 additional query parameters do not interfere with auth_code extraction」 |
| 2.3 | `AuthCallbackParserTest`「Req 2_3 non-feedman scheme returns SchemeMismatch failure」/「Req 2_3 scheme matching is case-insensitive」 |
| 2.4 | `AuthCallbackParserTest`「Req 2_4 wrong host returns HostOrPathMismatch failure」/「Req 2_4 wrong path returns HostOrPathMismatch failure」/「Req 2_4 missing path returns HostOrPathMismatch failure」 |
| 2.5 | `AuthCallbackParserTest`「Req 2_5 missing auth_code query parameter returns MissingAuthCode failure」/「Req 2_5 no query string at all returns MissingAuthCode failure」 |
| 2.6 | `AuthCallbackParserTest`「Req 2_6 empty auth_code value returns MissingAuthCode failure」/「Req 2_6 auth_code key with no equals sign returns MissingAuthCode failure」 |
| 2.7 | `AuthCallbackParserTest`「Req 2_7 malformed URI returns Malformed failure without throwing」/「Req 2_7 empty input string returns failure without throwing」 |
| 2.8 | `AuthCallbackParserTest`「Req 2_8 percent-encoded auth_code is decoded once before being returned」/「Req 2_8 literal plus character in auth_code is preserved as plus」/「Req 2_8 percent-encoded multibyte UTF-8 sequence decodes correctly」 |

### Non-Functional Requirements

| Req ID | 担保方法 |
|---|---|
| NFR 1.1 | 両クラスは `java.security` / `java.net.URI` / `java.util.Base64` のみに依存。`app/src/test/`（JVM 単体テスト）で全 AC をカバー（`./gradlew :app:testDebugUnitTest` で実行確認） |
| NFR 1.2 | 変更は `core/auth/Pkce.kt`・`core/auth/AuthCallback.kt` の新規追加と、対応テスト 2 ファイルのみ。`AuthRepository.kt` をはじめ他レイヤーの公開 API は無変更 |
| NFR 2.1 | `PkceGenerator.default(random: SecureRandom = SecureRandom())` で乱数源を注入可能。`PkceTest`「Req 1_3 challenge is Base64URL no-padding of SHA-256 of injected verifier」で決定論的 stub を注入して挙動を検証 |
| NFR 2.2 | `AuthCallbackParser.parse(input: String)` は文字列入力のみ受け取り、Android Intent / Uri を受け取らない。全テストは String 入力で全分岐を網羅 |

## 判断記録

- **乱数バイト数 64** を採用（→ 86 文字の Base64URL no-pad）。43 文字下限は満たし、128 文字上限の
  半分強で「過長による URL 制限抵触」リスクも避けられる。`requirements.md` Req 1.2 の範囲内
  で安全側に倒した選択。
- **`AuthCallbackError` の粒度** は Req 2.3..2.7 のカテゴリ要求に 1:1 対応する 4 種類に絞った
  （`Malformed` / `SchemeMismatch` / `HostOrPathMismatch` / `MissingAuthCode`）。
  Req 2.5 と 2.6（欠落 vs 空値）は「`auth_code` が使えない」という意味で運用上は同等カテゴリ
  と判断し、`MissingAuthCode` に統合（Req 2.6 でも「Req 2.5 と同等カテゴリ」を許容する文面）。
- **`URI` の `host`/`path` 解釈** について、`feedman://auth/callback` は Java の `URI` パーサで
  `host="auth"`, `path="/callback"` に分解される標準的挙動を前提にしつつ、念のため
  scheme-specific-part からの fallback パスも実装。`URI("feedman://auth?auth_code=...")` は
  `host="auth"`, `path=""` となるため `HostOrPathMismatch` を返す（Req 2.4 をカバー）。
- **percent-decode を自前実装** した理由: `java.net.URLDecoder.decode` は
  `application/x-www-form-urlencoded` 規格で `+` を空白に変換する。OAuth deep link は generic
  URI（RFC 3986）で `+` は literal なので、`URLDecoder` 使用だと一部の `auth_code` 値を破壊する
  可能性がある。`URI.getQuery()` を使うと「すでに decode 済み」の値を再度パースしてしまい
  曖昧性が生じるため、`rawQuery` から自前で `%xx` のみを decode する方針を採用。
- **scheme 比較は case-insensitive**（RFC 3986 §3.1）。ホストも同様に case-insensitive で比較。
- **生成された PkcePair の method フィールド**は `METHOD_S256` 既定値を持ち、コンストラクタで
  他値の指定は技術的には可能だが、利用 API（`PkceGenerator.generate()`）は常に既定値で構築する。
  「S256 のみサポート」（Req 1.6）の運用上の境界はジェネレータ側で担保。

## 確認事項（PR 本文向け）

- 後続 Issue #21（トークン交換）は `PkcePair.codeVerifier` を `POST /api/auth/token` に渡し、
  `AuthCallbackResult.Success.authCode` を `auth_code` フィールドに渡す前提。
- 後続 Issue #23（Custom Tabs 起動）は `PkcePair.codeChallenge` と `PkcePair.method` を
  Google OAuth ログイン URL の query parameters として組み立てる前提。
- MainActivity への deep link Intent filter 登録は本 Issue のスコープ外。受信した
  `Intent.data?.toString()` を `AuthCallbackParser.parse(...)` に渡すラッパが後続で必要。
- 設計書（`docs/specs/19-.../design.md`）は無いため、`docs/GRAND-DESIGN.md` §3 と
  `design/SERVER.md` §1.2 を参照して構造を決めた。本実装の設計判断はすべて当該成果物の範囲に
  閉じている（他レイヤーへの副作用なし）。

## 派生タスク（次の Issue 候補）

- `TokenStore`（EncryptedSharedPreferences + Keystore）実装 → Issue #20
- `AuthRepository` の具体実装（exchangeAuthCode / refresh / revoke / me）→ Issue #21
- Chrome Custom Tabs ログイン URL 構築 + 起動 → Issue #23
- MainActivity の Intent filter `<data android:scheme="feedman" />` 設定 + 受信ラッパ
  （上記 #20/#21/#23 のいずれかにまとめるか別 Issue 化）

## ビルド・テスト結果

- `./gradlew build` → BUILD SUCCESSFUL（lint / unit tests / assemble まで含む）
- `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.auth.*"` → 緑

STATUS: complete
