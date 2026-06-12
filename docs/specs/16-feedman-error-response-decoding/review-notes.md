# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-16-impl-feedman-error-response-decoding
- HEAD commit: 0381af6
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（CLAUDE.md `**採否**: opt-out`）→ flag 観点の追加判定なし

## Verified Requirements

- 1.1 — `FeedmanErrorMapper.fromHttpResponse` が `FeedmanErrorBody` を decode し `code` / `message` / `category` / `action` を `FeedmanException` に展開（テスト `Req 1-1 maps standard 4xx body to FeedmanException with full fields`）
- 1.2 — `httpStatus` 引数をそのまま `FeedmanException.httpStatus` に格納（テスト `Req 1-2 preserves original HTTP status code on FeedmanException`、fixture 503 で確認）
- 1.3 — `Json { ignoreUnknownKeys = true }` で root / details の未知フィールドを無視（テスト `Req 1-3 ignores unknown top-level and details fields without throwing`、fixture `error_with_unknown_fields.json` の `trace_id` / `details.feed_id` で確認）
- 1.4 — 全 4xx / 5xx で単一型 `FeedmanException` 返却（テスト `Req 1-4 returns FeedmanException for all non-2xx HTTP responses including 500`、戻り値型自体が `FeedmanException` で型保証）
- 2.1 — `details.retry_after_seconds=30` を取り出し（テスト `Req 2-1 extracts retry_after_seconds from details when present as integer`）
- 2.2 — `details` 欠落 / `retry_after_seconds` キー欠落の双方で null（テスト `Req 2-2 returns null retryAfterSeconds when details lacks retry_after_seconds` / `Req 2-2 returns null retryAfterSeconds when error body has no details object`）
- 2.3 — `parseRetryAfterHeader` フォールバック + body 優先（テスト `Req 2-3 uses Retry-After header when body lacks retry_after_seconds` / `Req 2-3 body details takes precedence over Retry-After header`）
- 2.4 — `toIntOrNull()` 失敗時 null、例外を投げない（テスト `Req 2-4 sets retryAfterSeconds to null when Retry-After header is not an integer`：HTTP-date / `Req 2-4 ... when Retry-After header is blank`）
- 3.1 — `body.isNullOrBlank()` で `synthesizeUnknown` 経由 `UNKNOWN_ERROR`（テスト `Req 3-1 returns UNKNOWN_ERROR when body is empty string` / `Req 3-1 returns UNKNOWN_ERROR when body is null`）
- 3.2 — `SerializationException` / `IllegalArgumentException` を catch して `UNKNOWN_ERROR` 合成（テスト `Req 3-2 returns UNKNOWN_ERROR when body is malformed JSON without throwing` / `Req 3-2 ... error field is missing` / `Req 3-2 ... error field is wrong type`）
- 3.3 — `synthesizeUnknown` が `httpStatus` を保持し、`FALLBACK_UNKNOWN_MESSAGE` を非空メッセージとして設定（テスト `Req 3-3 synthetic UNKNOWN_ERROR preserves original HTTP status and provides non-empty message`）
- 3.4 — フォールバック合成時 `category` / `action` / `retryAfterSeconds` すべて null（テスト `Req 3-4 synthetic UNKNOWN_ERROR sets category action and retryAfterSeconds to null`）
- 4.1 — `fromIoException` が `CODE_NETWORK_ERROR` を返却（テスト `Req 4-1 maps IOException to NETWORK_ERROR FeedmanException`）
- 4.2 — NETWORK_ERROR では `httpStatus = null`（テスト `Req 4-2 sets httpStatus to null for NETWORK_ERROR`）
- 4.3 — NETWORK_ERROR では `category` / `action` / `retryAfterSeconds` すべて null（テスト `Req 4-3 sets category action and retryAfterSeconds to null for NETWORK_ERROR`）
- 4.4 — `FALLBACK_NETWORK_MESSAGE` を非空メッセージとして提供（テスト `Req 4-4 provides non-empty fallback message for NETWORK_ERROR`）
- NFR 1.1 — `cause` の引き継ぎ（テスト `NFR 1-1 preserves IOException as cause on NETWORK_ERROR` / `NFR 1-1 preserves provided cause on http response mapping`）
- NFR 1.2 — `FeedmanErrorMapper.kt` 実装に `Log.*` / `println` 等のログ出力 API 呼び出しなし（コード検査で確認）
- NFR 2.1 — `code` が free-form string のまま透過（テスト `NFR 2-1 accepts any new code string without code changes`）
- NFR 2.2 — `Req 1-3` で root と details の両方の未知フィールド無視を検証
- NFR 3.1 — 変更ファイルは `app/src/main/kotlin/.../core/network/` 3 ファイル + `app/src/test/kotlin/.../core/network/` 2 ファイル + `app/src/test/resources/fixtures/` 7 ファイル + spec ドキュメント 2 ファイルに閉じる（`git diff --stat` で確認）
- NFR 3.2 — Retrofit service / repository / ViewModel / UI への変更なし（diff に該当パスなし）

## Findings

なし

## Summary

`FeedmanErrorMapper` / `FeedmanException` / `FeedmanErrorBody` が SPEC §4.3 統一エラーボディと
IOException を単一型 `FeedmanException` に変換する純粋ロジックを境界 `core/network` 内で完結
して実装しており、全 AC（Req 1〜4、NFR 1〜3）に対応する単体テストが揃っている。`./gradlew :app:testDebugUnitTest`
で `FeedmanErrorMapperTest` 25 ケースすべて pass。boundary 逸脱・missing test なし。

RESULT: approve
