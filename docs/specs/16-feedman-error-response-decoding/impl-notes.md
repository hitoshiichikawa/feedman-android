# Implementation Notes — Issue #16 Feedman error response decoding

## 概要

SPEC §4.3 の統一エラーボディ `{ "error": { code, message, category, action, details? } }` を
`core/network` 配下で型付き例外 `FeedmanException` に変換する純粋ロジックを実装した。
Retrofit / OkHttp との配線は本 Issue のスコープ外（#17 の領分）であり、本 Issue では
「HTTP ステータス + ボディ文字列 + 任意の Retry-After ヘッダ」を入力に取り、
`FeedmanException` を返す純粋関数 `FeedmanErrorMapper` として実装した。

## 追加・変更ファイル

### `app/src/main/kotlin/com/feedman/android/core/network/`
- `FeedmanErrorBody.kt` — SPEC §4.3 の DTO（`FeedmanErrorBody` / `FeedmanErrorPayload` /
  `FeedmanErrorDetails`、いずれも `internal` で network 層内に閉じる @Serializable）
- `FeedmanException.kt` — `code` / `errorMessage` / `category` / `action` /
  `retryAfterSeconds` / `httpStatus` / `cause` を保持する独自例外。フォールバック合成 code
  （`UNKNOWN_ERROR` / `NETWORK_ERROR`）と既定メッセージ定数を `companion object` に集約
- `FeedmanErrorMapper.kt` — `fromHttpResponse(httpStatus, body, retryAfterHeader, cause)` /
  `fromIoException(cause)` の純粋関数。`Json { ignoreUnknownKeys = true }` を mapper 内に
  閉じて持つ

### `app/src/test/kotlin/com/feedman/android/core/network/`
- `FixtureLoader.kt` — `core/network` テスト用の fixture ローダ（Issue #15 が `core/model` 配下に
  置いた同名ヘルパーと等価。共通化は #17 以降の領分のため重複を許容）
- `FeedmanErrorMapperTest.kt` — 25 テストケース（後述の AC 対応表参照）

### `app/src/test/resources/fixtures/`
- `error_feed_cooldown.json` — 429 / `FEED_COOLDOWN` + `details.retry_after_seconds=30`
- `error_invalid_request.json` — 標準的な 4xx エラーボディ（details なし）
- `error_with_unknown_fields.json` — root と details に未知フィールドを混在させた forward
  compatibility 検証用
- `error_details_without_retry_after.json` — `details` はあるが `retry_after_seconds` を含まない
- `error_malformed.json` — 不完全 JSON（破損）
- `error_missing_error_field.json` — `error` フィールドを欠く wrong shape
- `error_wrong_type.json` — `error` が object ではなく string になっている wrong type

## 受入基準 → テスト対応表

| AC ID | テスト関数（`FeedmanErrorMapperTest`） |
|---|---|
| Req 1.1 | `Req 1-1 maps standard 4xx body to FeedmanException with full fields` |
| Req 1.2 | `Req 1-2 preserves original HTTP status code on FeedmanException` |
| Req 1.3 | `Req 1-3 ignores unknown top-level and details fields without throwing` |
| Req 1.4 | `Req 1-4 returns FeedmanException for all non-2xx HTTP responses including 500` |
| Req 2.1 | `Req 2-1 extracts retry_after_seconds from details when present as integer` |
| Req 2.2 | `Req 2-2 returns null retryAfterSeconds when details lacks retry_after_seconds` / `Req 2-2 returns null retryAfterSeconds when error body has no details object` |
| Req 2.3 | `Req 2-3 uses Retry-After header when body lacks retry_after_seconds` / `Req 2-3 body details takes precedence over Retry-After header` |
| Req 2.4 | `Req 2-4 sets retryAfterSeconds to null when Retry-After header is not an integer` / `Req 2-4 sets retryAfterSeconds to null when Retry-After header is blank` |
| Req 3.1 | `Req 3-1 returns UNKNOWN_ERROR when body is empty string` / `Req 3-1 returns UNKNOWN_ERROR when body is null` |
| Req 3.2 | `Req 3-2 returns UNKNOWN_ERROR when body is malformed JSON without throwing` / `Req 3-2 returns UNKNOWN_ERROR when error field is missing` / `Req 3-2 returns UNKNOWN_ERROR when error field is wrong type` |
| Req 3.3 | `Req 3-3 synthetic UNKNOWN_ERROR preserves original HTTP status and provides non-empty message` |
| Req 3.4 | `Req 3-4 synthetic UNKNOWN_ERROR sets category action and retryAfterSeconds to null` |
| Req 4.1 | `Req 4-1 maps IOException to NETWORK_ERROR FeedmanException` |
| Req 4.2 | `Req 4-2 sets httpStatus to null for NETWORK_ERROR` |
| Req 4.3 | `Req 4-3 sets category action and retryAfterSeconds to null for NETWORK_ERROR` |
| Req 4.4 | `Req 4-4 provides non-empty fallback message for NETWORK_ERROR` |
| NFR 1.1 | `NFR 1-1 preserves IOException as cause on NETWORK_ERROR` / `NFR 1-1 preserves provided cause on http response mapping` |
| NFR 1.2 | 実装規約として mapper 内でログ出力を行わない（テスト不能の規範要件のため、コードレベル
  で「ログ出力 API を呼ばない」ことを担保。`FeedmanErrorMapper` の実装に `Log.*` 等の
  呼び出しなし） |
| NFR 2.1 | `NFR 2-1 accepts any new code string without code changes` |
| NFR 2.2 | `Req 1-3 ignores unknown top-level and details fields without throwing`（root と details の
  両方で未知フィールド無視を検証） |
| NFR 3.1 | 変更ファイルは `core/network` 配下の新規 3 ファイル + `app/src/test/resources/fixtures/`
  下のエラー fixture 7 件 + `app/src/test/kotlin/.../core/network/` 配下のテスト 2 ファイルに
  閉じる |
| NFR 3.2 | Retrofit / Repository / ViewModel / UI は一切変更していない |

## 判断記録

- **`FeedmanException` の `message` プロパティ**: `RuntimeException(message, cause)` の
  super 引数に `errorMessage` を渡すことで `Throwable.message` も同値を返す。`val errorMessage` を
  別途公開しているのは Kotlin の `Throwable.message` が nullable `String?` を返すため、
  呼び出し側で null 安全に扱える非 null アクセサを提供する意図。
- **`category` / `action` を nullable に**: SPEC §4.3 の例示では必ず値が入っているように
  見えるが、サーバー側の category / action 列挙体が今後拡張されることを想定し、
  `null` フォールバック互換にしている（Req 3.4 / Req 4.3 で `null` を明示的に要求しており、
  本体仕様上もこの可動域は許容される）。SPEC への影響は無い。
- **`Json` の `isLenient = false`**: 不正 JSON は素直に `SerializationException` を投げさせ、
  mapper でキャッチして `UNKNOWN_ERROR` に変換する。lenient で銀の弾丸的にパースが通って
  しまうと「壊れたボディなのに success として扱う」リスクが出るため false で固定。
- **`Retry-After` ヘッダの HTTP-date 形式**: RFC 7231 で許容されているが、SPEC §4.3 が
  「秒数 (integer)」を前提としているため、v1 では HTTP-date は **解釈せず `null` 扱い**
  （Req 2.4 のテストで明文化）。将来必要になった場合は `parseRetryAfterHeader` を拡張すれば良い。
- **fixture ローダの重複**: Issue #15 が `core/model/FixtureLoader.kt` に同等の object を
  既に置いていたが、本 Issue の境界（`core/network`）に閉じるため再宣言した。共通化は
  Retrofit 配線が入る Issue #17 でまとめて実施する想定。

## 確認事項（PR 本文向け）

- mapper はステートレスな `object`。Hilt 経由で注入する形にせず、Issue #17 で
  Retrofit `CallAdapter` / OkHttp `Interceptor` 配線時に静的に呼び出す前提。注入方針を
  変えたい場合は #17 でリファクタしてほしい。
- `FeedmanErrorMapper.fromHttpResponse` のシグネチャは引数順 `(httpStatus, body,
  retryAfterHeader, cause)` で、後ろ 2 つはデフォルト引数 `null`。Retrofit `HttpException`
  からの呼び出し時に `errorBody().string()` と `headers().get("Retry-After")` を渡すことを
  想定している。

## 完了確認

- `./gradlew build` 成功
- 25 unit tests（network 配下）すべて pass
- requirements / design / tasks の書き換えなし（本 Issue は design / tasks 不存在）
- Conventional Commits 準拠

STATUS: complete
