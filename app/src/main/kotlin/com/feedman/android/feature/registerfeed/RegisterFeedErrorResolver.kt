package com.feedman.android.feature.registerfeed

import com.feedman.android.core.network.FeedmanException

/**
 * フィード登録時のサーバーエラー応答をユーザー向け文言に変換する純粋関数群
 * （Issue #44 / Req 5.1〜5.6）。
 *
 * SPEC §4.3 / SERVER に登録専用エラー `code` 文字列が未列挙のため、Open Questions の
 * 設計判断（requirements.md と本 Issue prompt）に従い **httpStatus 主導 + サーバー
 * `errorMessage` フォールバック** で分岐する。将来 `code` が確定したら本関数内で
 * 優先的に分岐するよう拡張する余地を残す（NFR 3.2: HTML / 制御文字は呼び出し元の
 * Composable がプレーンテキスト表示することで担保）。
 *
 * UI 文言テンプレートは [RegisterFeedErrorTexts] に集約し、`strings.xml` の解決結果を
 * 呼び出し側が組み立てて渡す（本オブジェクトは Android 依存を持たない / JVM 単体テスト対象）。
 */
object RegisterFeedErrorResolver {

    /**
     * 例外をユーザー向け文言に変換する。
     *
     * 分岐方針:
     * - httpStatus 409 → 重複登録（Req 5.1）。サーバー `errorMessage` を優先、欠落時は既定文言
     * - httpStatus 429 → レート制限（Req 5.3 / 5.4）。`retryAfterSeconds` があれば残時間付き
     *   文言、無ければ汎用再試行文言
     * - httpStatus 400 または 422 → URL 不正・フィード未検出（Req 5.2）。サーバー `errorMessage`
     *   を優先、欠落時は既定文言
     * - code = NETWORK_ERROR → ネットワーク到達不可（Req 5.6）
     * - その他 4xx / 5xx → サーバー `errorMessage` を優先、欠落時は汎用エラー文言（Req 5.5）
     */
    fun resolve(exception: FeedmanException, texts: RegisterFeedErrorTexts): String {
        // NETWORK_ERROR（httpStatus = null）優先判定。Req 5.6
        if (exception.code == FeedmanException.CODE_NETWORK_ERROR) {
            return texts.networkUnreachable
        }
        return when (exception.httpStatus) {
            409 -> exception.serverMessageOrFallback(texts.duplicate)
            429 -> resolveRateLimit(exception, texts)
            400, 422 -> exception.serverMessageOrFallback(texts.invalidUrl)
            else -> exception.serverMessageOrFallback(texts.genericFallback)
        }
    }

    private fun resolveRateLimit(
        exception: FeedmanException,
        texts: RegisterFeedErrorTexts,
    ): String {
        val seconds = exception.retryAfterSeconds
        return if (seconds != null && seconds > 0) {
            texts.rateLimitWithSeconds(seconds)
        } else {
            texts.rateLimitGeneric
        }
    }

    /**
     * サーバー応答の `errorMessage` を優先し、空白なら fallback 文言を返す。
     *
     * NFR 3.2 への対応: 制御文字 / HTML タグ / 改行はプレーンテキストとして扱う方針のため、
     * 本関数ではサニタイズせず、Composable 側の `Text` で素のテキスト表示する。
     */
    private fun FeedmanException.serverMessageOrFallback(fallback: String): String =
        errorMessage.ifBlank { fallback }
}

/**
 * ユーザー向け文言テンプレート（Issue #44）。
 *
 * `RegisterFeedViewModel` から `strings.xml` 解決済み文字列を注入することで、
 * [RegisterFeedErrorResolver] を Android 依存なしの純粋関数として保てる。
 *
 * @property duplicate Req 5.1: 重複登録時の既定文言（サーバー message が空のとき）
 * @property invalidUrl Req 5.2: URL 不正 / フィード未検出時の既定文言
 * @property rateLimitWithSeconds Req 5.3: レート制限時、retryAfterSeconds を組み込んだ文言を返す関数
 * @property rateLimitGeneric Req 5.4: レート制限時、retryAfterSeconds が無いときの汎用文言
 * @property genericFallback Req 5.5: その他 4xx / 5xx 時の汎用フォールバック文言
 * @property networkUnreachable Req 5.6: ネットワーク到達不可時の文言
 */
data class RegisterFeedErrorTexts(
    val duplicate: String,
    val invalidUrl: String,
    val rateLimitWithSeconds: (Int) -> String,
    val rateLimitGeneric: String,
    val genericFallback: String,
    val networkUnreachable: String,
)
