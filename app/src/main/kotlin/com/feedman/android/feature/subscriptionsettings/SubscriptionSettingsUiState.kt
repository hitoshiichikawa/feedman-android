package com.feedman.android.feature.subscriptionsettings

import com.feedman.android.core.model.Subscription

/**
 * 購読設定シートの UI 状態（Issue #43 / Req 1, 2, 3, 4, 5）。
 *
 * `Hidden` 以外のときシートが画面に表示される。`Hidden` のときは描画しない（Req 1.4）。
 *
 * `Visible` は対象 Subscription の最新スナップショット（subscription）と、ユーザー操作の
 * 一時状態（選択中 interval / 保存進行中 / エラーメッセージ / 解除確認ダイアログ / 解除進行中）を
 * 1 つの不変モデルにまとめる。Repository 側の Subscription 更新は ViewModel が観測して
 * 自動的に `subscription` を最新化する。
 *
 * @property subscription 対象購読の最新スナップショット。Repository から流れてくる
 * @property selectedIntervalMinutes ユーザーが UI 上で選択中の interval。`null` は「未選択」
 *   状態（Req 2.3）。初期値は Subscription.fetchIntervalMinutes が 30/60/180/360 のいずれかに
 *   一致すればその値、そうでなければ `null`（Req 2.2 / 2.3）。
 * @property saveInProgress true のとき保存ボタンを進行中状態にし追加保存操作を受け付けない（Req 2.5）
 * @property resumeInProgress true のとき再開ボタンを進行中状態にする（Req 3.x / NFR 1.1）
 * @property errorMessage 直近の操作失敗時にユーザーに見せるエラー文言（Req 2.6 / 3.5 / 4.7 / 5.1）
 * @property confirmUnsubscribeOpen true のとき購読解除確認ダイアログを表示する（Req 4.1）
 * @property unsubscribeInProgress true のとき解除リクエスト送信中で、追加操作を受け付けない（Req 4.6）
 */
sealed interface SubscriptionSettingsUiState {

    /** 初期状態 / シート未表示。 */
    data object Hidden : SubscriptionSettingsUiState

    /**
     * シート表示中。対象 Subscription とユーザー操作の一時状態を保持する。
     */
    data class Visible(
        val subscription: Subscription,
        val selectedIntervalMinutes: Int?,
        val saveInProgress: Boolean = false,
        val resumeInProgress: Boolean = false,
        val errorMessage: String? = null,
        val confirmUnsubscribeOpen: Boolean = false,
        val unsubscribeInProgress: Boolean = false,
    ) : SubscriptionSettingsUiState {
        /**
         * 保存ボタンが有効かどうか（Req 2.4 / 2.5）。
         *
         * - 進行中（save / unsubscribe）のとき無効
         * - selectedIntervalMinutes が null のとき無効（Req 2.3 未選択）
         * - selectedIntervalMinutes が現在値と同一のとき無効（不要な PUT を避ける）
         */
        val canSave: Boolean
            get() = !saveInProgress &&
                !unsubscribeInProgress &&
                selectedIntervalMinutes != null &&
                selectedIntervalMinutes != subscription.fetchIntervalMinutes

        /**
         * 再開アクションが表示されるかどうか（Req 3.1 / 3.4）。
         */
        val showResumeAction: Boolean
            get() = subscription.feedStatus == STATUS_STOPPED ||
                subscription.feedStatus == STATUS_ERROR
    }

    companion object {
        /** SPEC §4.2 / Issue #41 と整合する状態値（stopped）。 */
        const val STATUS_STOPPED: String = "stopped"

        /** SPEC §4.2 / Issue #41 と整合する状態値（error）。 */
        const val STATUS_ERROR: String = "error"

        /**
         * Req 2.1: フェッチ間隔セグメントとして UI に表示する 4 値（30 / 60 / 180 / 360 分）。
         *
         * 2026-06-12 人間決定（要件 Introduction）に従い、サーバー側バリデーション
         * （30〜720 分・30 分刻み）の中から運用上意味のある 4 値に固定。
         */
        val ALLOWED_INTERVAL_MINUTES: List<Int> = listOf(30, 60, 180, 360)

        /**
         * 現在の Subscription.fetchIntervalMinutes から初期選択値を解決する（Req 2.2 / 2.3）。
         *
         * - 30 / 60 / 180 / 360 のいずれかに一致 → その値
         * - それ以外（サーバーが将来別値を返した場合 / 規格外） → null（未選択）
         */
        fun resolveInitialSelection(current: Int): Int? =
            if (current in ALLOWED_INTERVAL_MINUTES) current else null
    }
}

/**
 * 購読設定シートの one-shot 通知イベント（Issue #43）。
 *
 * `SubscriptionSettingsViewModel.events` SharedFlow 経由で UI 側へ流す（replay = 0）。
 */
sealed interface SubscriptionSettingsEvent {

    /** Req 2.4: 保存成功（UI 側で snackbar 表示 + シートを閉じる）。 */
    data object SettingsSaved : SubscriptionSettingsEvent

    /** Req 3.3: 再開成功（UI 側で snackbar 表示）。 */
    data object ResumeSucceeded : SubscriptionSettingsEvent

    /**
     * Req 4.4 / 4.5: 購読解除成功。UI 側はシートを閉じ、対象フィードの画面が表示中
     * （feed/{feedId}）なら timeline へ退避する。
     *
     * @property feedId 解除された購読の `Subscription.feedId`（NavHost 側で照合）
     */
    data class Unsubscribed(val feedId: String) : SubscriptionSettingsEvent

    /**
     * Req 5.3: 認証切れ（UNAUTHORIZED）が検知された。UI 側はシートを閉じ、
     * ログイン導線へ誘導する。
     */
    data object UnauthorizedRedirect : SubscriptionSettingsEvent
}
