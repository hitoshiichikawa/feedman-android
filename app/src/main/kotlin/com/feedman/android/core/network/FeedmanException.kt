package com.feedman.android.core.network

/**
 * Feedman API 呼び出しが返した非 2xx 応答、もしくは I/O 失敗を統一的に表現する例外。
 *
 * SPEC §4.3 のエラーボディ `{ "error": { code, message, category, action, details? } }` を
 * [FeedmanErrorMapper] が本例外に変換し、Repository / ViewModel 層は `code` で分岐しつつ
 * `message` をユーザー表示の基本にできるようにする（GRAND-DESIGN.md §5.1 / Req 1.1〜1.4）。
 *
 * ボディが欠落・破損・パース不能だった場合や、レスポンス自体が得られない IOException が
 * 発生した場合は、合成 `code`（[CODE_UNKNOWN_ERROR] / [CODE_NETWORK_ERROR]）でラップする
 * フォールバック規約を持つ（Req 3 / Req 4）。
 *
 * @property code SPEC §4.3 の `error.code`。フォールバック時は [CODE_UNKNOWN_ERROR] /
 *   [CODE_NETWORK_ERROR]。
 * @property errorMessage SPEC §4.3 の `error.message`。フォールバック時はフォールバック UI 表示用の
 *   非空メッセージ（Req 3.3 / Req 4.4）。`Throwable.message` も同値を返す。
 * @property category SPEC §4.3 の `error.category`。サーバー未提供 or フォールバック時は `null`。
 * @property action SPEC §4.3 の `error.action`。サーバー未提供 or フォールバック時は `null`。
 * @property retryAfterSeconds `error.details.retry_after_seconds` の値。
 *   それが未提供の場合は HTTP `Retry-After` ヘッダから整数秒として復元される（Req 2.3）。
 *   いずれも得られない場合 / NETWORK_ERROR 時は `null`（Req 2.2 / Req 4.3）。
 * @property httpStatus 元レスポンスの HTTP ステータスコード。レスポンスを得られない
 *   NETWORK_ERROR では `null`（Req 1.2 / Req 4.2）。
 * @param cause ログ・クラッシュレポートでのトレース用に元の原因を保持する（NFR 1.1）。
 */
class FeedmanException(
    val code: String,
    val errorMessage: String,
    val category: String? = null,
    val action: String? = null,
    val retryAfterSeconds: Int? = null,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(errorMessage, cause) {

    companion object {
        /**
         * レスポンスボディが欠落 / 破損 / スキーマ不一致だった場合に用いる合成 `code`。
         * GRAND-DESIGN.md §5.1 の例示に整合する。
         */
        const val CODE_UNKNOWN_ERROR: String = "UNKNOWN_ERROR"

        /**
         * IOException（オフライン・接続切断など、レスポンスを得られない失敗）の合成 `code`。
         */
        const val CODE_NETWORK_ERROR: String = "NETWORK_ERROR"

        /**
         * SPEC §4.3 のクールダウン応答（HTTP 429 / `POST /api/subscriptions/{id}/fetch`）の
         * `code`。Issue #42 Req 3.1 で UI 側のスナックバー文言分岐に使う。
         */
        const val CODE_FEED_COOLDOWN: String = "FEED_COOLDOWN"

        /**
         * [CODE_UNKNOWN_ERROR] フォールバック時にユーザー表示として返す既定文言（Req 3.3）。
         */
        const val FALLBACK_UNKNOWN_MESSAGE: String =
            "サーバーから不明なエラー応答を受信しました。時間をおいて再度お試しください。"

        /**
         * [CODE_NETWORK_ERROR] フォールバック時にユーザー表示として返す既定文言（Req 4.4）。
         */
        const val FALLBACK_NETWORK_MESSAGE: String =
            "ネットワークに接続できませんでした。接続状況を確認して再度お試しください。"
    }
}
