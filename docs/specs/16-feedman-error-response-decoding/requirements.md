# Requirements Document

## Introduction

Feedman サーバーは 4xx / 5xx 応答時に統一エラーボディ
`{ "error": { code, message, category, action, details? } }`（SPEC §4.3）を返す。Android アプリ
側は、この型情報を失わずに UI 層・Repository 層・テスト層から一貫して扱えるようにする必要がある。
本 Issue では、network 層（`core/network`）を境界として上記エラーボディを `FeedmanException`
（`code` / `message` / `category` / `action` / `retryAfterSeconds` / `httpStatus` を保持する独自例外）
へ一元的に変換する仕組みを整備する。これにより、後続の Retrofit クライアント（#17）や画面側の
エラー表示（#28）が `code` ベースで安全に分岐できるようになる。

## Requirements

### Requirement 1: 統一エラーボディの型付きデコード

**Objective:** As a Feedman Android アプリの呼び出し側（Repository / ViewModel）, I want
4xx / 5xx の統一エラーボディを型情報付きの例外として受け取りたい, so that エラーコードによる
分岐とユーザー向けメッセージ表示を解釈ズレなく実装できる

#### Acceptance Criteria

1. When the server returns a 4xx or 5xx response whose body matches `{ "error": { code, message, category, action } }`, the Network Error Layer shall throw a FeedmanException whose `code` / `message` / `category` / `action` fields are populated from the body.
2. When a FeedmanException is thrown by the Network Error Layer, the Network Error Layer shall set `httpStatus` to the original HTTP status code (4xx / 5xx) of the failing response.
3. When the error body contains string fields beyond the documented set (forward compatibility), the Network Error Layer shall ignore unknown fields without throwing.
4. The Network Error Layer shall expose FeedmanException as a single exception type for all non-2xx HTTP responses processed through this layer.

### Requirement 2: `details.retry_after_seconds` の取り出し

**Objective:** As a 呼び出し側, I want クールダウン応答（`FEED_COOLDOWN` 等）で
サーバーが指定した待機秒数を構造化された形で受け取りたい, so that
ユーザー向けの「N 秒後に再試行できます」案内を一意に実装できる

#### Acceptance Criteria

1. When the error body includes `details.retry_after_seconds` as an integer, the Network Error Layer shall expose its value as FeedmanException.retryAfterSeconds.
2. If the error body has no `details.retry_after_seconds`, the Network Error Layer shall set FeedmanException.retryAfterSeconds to null.
3. When the response carries an HTTP `Retry-After` header and the body does not include `details.retry_after_seconds`, the Network Error Layer shall populate FeedmanException.retryAfterSeconds from the `Retry-After` header value parsed as integer seconds.
4. If the `Retry-After` header value is not a parseable integer, the Network Error Layer shall set FeedmanException.retryAfterSeconds to null without throwing.

### Requirement 3: エラーボディが欠落・破損している場合のフォールバック

**Objective:** As a 呼び出し側, I want 想定外のエラーボディが返ってきた場合でも例外として
安全に受け取れるようにしたい, so that アプリがクラッシュせず最低限のエラーメッセージを表示できる

#### Acceptance Criteria

1. If the response body for a non-2xx response is empty, the Network Error Layer shall throw a FeedmanException with a synthetic `code` value of `UNKNOWN_ERROR`.
2. If the response body for a non-2xx response cannot be parsed as the documented error schema (malformed JSON / missing `error` field / wrong type), the Network Error Layer shall throw a FeedmanException with a synthetic `code` value of `UNKNOWN_ERROR` instead of propagating a deserialization exception.
3. When a synthetic FeedmanException is constructed under fallback, the Network Error Layer shall set `httpStatus` to the original HTTP status code and provide a non-empty `message` suitable for fallback UI display.
4. When a synthetic FeedmanException is constructed under fallback, the Network Error Layer shall set `category`, `action`, and `retryAfterSeconds` to null.

### Requirement 4: ネットワーク断（IOException）のマッピング

**Objective:** As a 呼び出し側, I want オフライン・接続切断などの I/O エラーも同じ
FeedmanException 型で受け取りたい, so that 上位レイヤーが try/catch を一本化できる

#### Acceptance Criteria

1. If the underlying HTTP call fails with an IOException before a response is received, the Network Error Layer shall throw a FeedmanException with a synthetic `code` value of `NETWORK_ERROR`.
2. When a FeedmanException with `code = NETWORK_ERROR` is thrown, the Network Error Layer shall set `httpStatus` to null.
3. When a FeedmanException with `code = NETWORK_ERROR` is thrown, the Network Error Layer shall set `category`, `action`, and `retryAfterSeconds` to null.
4. When a FeedmanException with `code = NETWORK_ERROR` is thrown, the Network Error Layer shall provide a non-empty `message` suitable for fallback UI display.

## Non-Functional Requirements

### NFR 1: 観測性とトレーサビリティ

1. The Network Error Layer shall preserve the original underlying cause (HTTP failure / IOException / parse failure) as the FeedmanException `cause`, so that 開発者は logcat やクラッシュレポートで原因を追跡できる.
2. The Network Error Layer shall not log raw response bodies that could contain access tokens or personal data; if logging is performed, only the `code` / `httpStatus` / `category` fields shall be logged.

### NFR 2: 互換性と拡張性

1. The Network Error Layer shall accept any new `code` string returned by the server without code changes (the `code` field shall be a free-form string, not a closed enum at the network layer).
2. The Network Error Layer shall remain forward-compatible with additional optional fields under `error.details` by ignoring unknown keys.

### NFR 3: スコープ境界（変更影響範囲）

1. The change scope shall be limited to `core/network`（`FeedmanException` および統一エラーボディの DTO 相当）と、対応する fixture JSON / 単体テストに閉じる.
2. The change shall not introduce or modify Retrofit service interfaces, repositories, ViewModels, or UI code (これらは #17 / #28 等の後続 Issue 範囲).

## Out of Scope

- Retrofit クライアント本体（OkHttp / Retrofit / kotlinx.serialization の構成）の実装（#17 の領分）
- エラー表示 UI（トースト / バナー / 再試行ボタンの文言）の実装（#28 等の領分）
- 401 応答時のトークンリフレッシュフロー（`TokenAuthenticator`）の実装（認証系 Issue の領分）
- サーバー側エラーフォーマット仕様の変更提案（`feedman` リポジトリの所掌）
- ローカライズ済みエラーメッセージ辞書の整備（v1 ではサーバー `message` をそのまま表示する SPEC §4.3 / §6 方針）

## Open Questions

- なし（SPEC §4.3 のフォーマットと Issue #16 本文の受入基準で曖昧点は解消済み。`UNKNOWN_ERROR` /
  `NETWORK_ERROR` という合成 `code` 名はグランドデザイン §5.1 の例示に整合）
