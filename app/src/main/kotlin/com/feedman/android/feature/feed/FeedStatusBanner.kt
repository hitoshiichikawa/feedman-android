package com.feedman.android.feature.feed

import com.feedman.android.core.model.Subscription

/**
 * フィード別画面の上部警告バナー状態（Issue #41 / Req 3.1〜3.4）。
 *
 * design/mobile/fm-screens.jsx の `feed.feed_status !== 'active'` 時に表示するバナーを
 * 状態としてモデル化する。表示要否（[Hidden] vs [Visible]）と、Visible 時に必要なアイコン
 * 種別・本文・再開ボタン進行状態を排他的に保持する。
 *
 * Req 3.4: active 状態ではバナーは表示しない（[Hidden]）。
 * Req 3.1 / 3.2: stopped または error のときに [Visible] でアイコン種別と本文を提示する。
 * Req 3.3: `error_message` が空 / null のときは [Visible.message] に状態ごとの既定文言を
 *   詰める（既定文言の解決は UI 側 stringResource ではなく、本 ViewModel/[resolveBanner]
 *   の呼び出し元で行うため、本モデル自体は null/blank を許容して [fallbackMessage] を
 *   1 つ持つ形にする）。
 * Req 3.6: 再開処理進行中は [resumeInProgress] が true となり、UI 側で disabled 表示にする。
 */
sealed interface FeedStatusBanner {

    /** 警告バナーを表示しない（active 状態 or フィード未取得時）。 */
    data object Hidden : FeedStatusBanner

    /**
     * 警告バナーを表示する。
     *
     * @property kind 状態種別（停止 / エラー）。アイコン分岐に使う（Req 3.2）。
     * @property message サーバ提供の `error_message`（非 null / 非 blank）。未提供時は
     *   [fallbackMessage] を呼び出し側で使う（Req 3.3）。
     * @property resumeInProgress 再開処理進行中なら true（Req 3.6）。UI 側で disabled 化。
     */
    data class Visible(
        val kind: Kind,
        val message: String?,
        val resumeInProgress: Boolean,
    ) : FeedStatusBanner {

        /**
         * Req 3.3: `error_message` が null/blank のときに UI 側で使う「状態に応じた既定の
         * 説明文」を選ぶための識別子。文言自体は strings.xml に定義し、本識別子で UI が
         * 解決する（モデルは文言解決を持たない）。
         */
        val fallbackMessage: FallbackMessage
            get() = when (kind) {
                Kind.STOPPED -> FallbackMessage.STOPPED_DEFAULT
                Kind.ERROR -> FallbackMessage.ERROR_DEFAULT
            }
    }

    /** バナーが示すフィード状態種別。 */
    enum class Kind { STOPPED, ERROR }

    /** Req 3.3: 状態別の既定文言識別子。 */
    enum class FallbackMessage { STOPPED_DEFAULT, ERROR_DEFAULT }
}

/**
 * Subscription（または取得未完了時の null）と再開進行フラグから [FeedStatusBanner] を導出する
 * 純粋関数（Issue #41 Req 3.1 / 3.2 / 3.3 / 3.4 / 3.6 / 4.1 / 4.2）。
 *
 * - Subscription が `null`（取得未完了 or 未存在）→ [FeedStatusBanner.Hidden]
 * - `feed_status == "active"` → [FeedStatusBanner.Hidden]（Req 3.4）
 * - `feed_status == "stopped"` → [FeedStatusBanner.Visible](kind = STOPPED) （Req 3.1 / 3.2）
 * - `feed_status == "error"` → [FeedStatusBanner.Visible](kind = ERROR) （Req 3.1 / 3.2）
 * - 上記以外の未知文字列 → [FeedStatusBanner.Hidden]（防衛的: 将来サーバが新状態を追加して
 *   も既存バナーは出さない / Req 3.4 と整合）
 */
fun resolveBanner(
    subscription: Subscription?,
    resumeInProgress: Boolean,
): FeedStatusBanner {
    if (subscription == null) return FeedStatusBanner.Hidden
    val kind = when (subscription.feedStatus) {
        "stopped" -> FeedStatusBanner.Kind.STOPPED
        "error" -> FeedStatusBanner.Kind.ERROR
        "active" -> return FeedStatusBanner.Hidden
        else -> return FeedStatusBanner.Hidden
    }
    return FeedStatusBanner.Visible(
        kind = kind,
        message = subscription.errorMessage?.takeIf { it.isNotBlank() },
        resumeInProgress = resumeInProgress,
    )
}
