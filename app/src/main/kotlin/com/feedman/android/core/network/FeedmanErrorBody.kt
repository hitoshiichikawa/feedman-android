package com.feedman.android.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SPEC §4.3 の統一エラーレスポンスボディ。
 *
 * Feedman サーバーは 4xx / 5xx 応答時に
 * `{ "error": { code, message, category, action, details? } }` の形で本ペイロードを返す。
 * 本 DTO は network 層内でのみ利用し、上位レイヤーには [FeedmanException] に変換した形で
 * 渡す（Req 1.4 / NFR 3.2）。
 *
 * 未知フィールドの扱いは [FeedmanErrorMapper] が `ignoreUnknownKeys = true` の [kotlinx.serialization.json.Json]
 * を用いることで forward-compatibility を確保する（Req 1.3 / NFR 2.2）。
 *
 * @property error 必須。サーバーが返す `error` オブジェクト本体。
 */
@Serializable
internal data class FeedmanErrorBody(
    @SerialName("error") val error: FeedmanErrorPayload,
)

/**
 * `error` オブジェクトの中身。SPEC §4.3 の `code` / `message` / `category` / `action` /
 * `details` に対応する。
 *
 * `code` は v1 ではクローズドな enum 化を避け、サーバーが追加した未知の文字列も
 * そのまま透過する（NFR 2.1）。
 *
 * @property code エラー識別子（例: `FEED_COOLDOWN`、`UNAUTHORIZED`）。
 * @property message ユーザー向け表示文字列（SPEC §4.3 によりサーバー側で日本語整形済み）。
 * @property category エラー区分（例: `client` / `server` / `rate_limit`）。サーバー仕様により nullable。
 * @property action 推奨アクション（例: `wait_and_retry` / `fix_input`）。サーバー仕様により nullable。
 * @property details 追加情報。`retry_after_seconds` 等を含む。未知キーは無視される。
 */
@Serializable
internal data class FeedmanErrorPayload(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("category") val category: String? = null,
    @SerialName("action") val action: String? = null,
    @SerialName("details") val details: FeedmanErrorDetails? = null,
)

/**
 * `error.details` オブジェクト。SPEC §4.3 の例では `retry_after_seconds` のみ規定されているが、
 * 今後追加される構造化詳細を破壊せずに保持できるよう、必要なフィールドのみを optional として
 * 宣言する（NFR 2.2 / Req 1.3）。
 *
 * @property retryAfterSeconds `FEED_COOLDOWN` 等で返るリトライ可能秒数。未提供時は `null`。
 */
@Serializable
internal data class FeedmanErrorDetails(
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)
