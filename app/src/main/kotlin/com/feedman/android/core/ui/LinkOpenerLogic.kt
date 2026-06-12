package com.feedman.android.core.ui

/**
 * [CustomTabsLinkOpener] の起動判定ロジック（Issue #37 / Req 3.x / Req 4.x / NFR 3.1 / NFR 3.2）。
 *
 * Android 依存（[android.content.Context] / [android.content.Intent] / Custom Tabs SDK）から
 * 起動経路選択の意思決定を切り離し、JVM 単体テストで網羅できるようにする（NFR 3.2）。
 *
 * Custom Tabs 起動可否・ACTION_VIEW フォールバック可否は呼び出し側が `PackageManager` で
 * 解決した結果を入力 [LaunchPreflight] として渡し、本オブジェクトは「どの経路を取るか」と
 * 「最終的にどの [OpenLinkResult] を返すか」だけを決める。
 */
internal object LinkOpenerLogic {

    /**
     * 端末の起動可否情報（Activity 解決結果）。
     *
     * @property customTabsAvailable Custom Tabs 対応ブラウザのサービスが解決できる
     * @property fallbackAvailable ACTION_VIEW で URL を開けるアプリが解決できる
     */
    data class LaunchPreflight(
        val customTabsAvailable: Boolean,
        val fallbackAvailable: Boolean,
    )

    /**
     * 起動判定の結果（[CustomTabsLinkOpener] が次に行う Android 副作用）。
     */
    sealed class LaunchPlan {
        /** Custom Tabs を起動する。結果は [OpenLinkResult.OpenedWithCustomTabs]。 */
        object UseCustomTabs : LaunchPlan()

        /** ACTION_VIEW で起動する。結果は [OpenLinkResult.OpenedWithFallback]。 */
        object UseFallback : LaunchPlan()

        /**
         * 起動しない。`result` をそのまま返す。
         *
         * - URL バリデーションエラー → [OpenLinkResult.InvalidUrl]
         * - どちらの経路も使えない → [OpenLinkResult.NoAppToHandle]
         */
        data class DoNothing(val result: OpenLinkResult) : LaunchPlan()
    }

    /**
     * URL バリデーション結果と端末の解決結果から起動計画を立てる。
     *
     * 優先順:
     * 1. URL が不正（[UrlValidation.ValidationResult.Invalid]）→ [LaunchPlan.DoNothing] +
     *    [OpenLinkResult.InvalidUrl]（Req 4.1 / 4.2 / 4.3）
     * 2. Custom Tabs 対応ブラウザがある → [LaunchPlan.UseCustomTabs]（Req 1.1 / 2.1）
     * 3. ACTION_VIEW で開けるアプリがある → [LaunchPlan.UseFallback]（Req 3.1 / 3.2）
     * 4. どちらも無い → [LaunchPlan.DoNothing] + [OpenLinkResult.NoAppToHandle]（Req 3.3）
     *
     * @param validation [UrlValidation.validate] の結果
     * @param preflight 端末の解決結果
     */
    fun decide(
        validation: UrlValidation.ValidationResult,
        preflight: LaunchPreflight,
    ): LaunchPlan {
        when (validation) {
            is UrlValidation.ValidationResult.Invalid -> {
                return LaunchPlan.DoNothing(OpenLinkResult.InvalidUrl(validation.reason))
            }
            is UrlValidation.ValidationResult.Valid -> Unit // 続行
        }

        return when {
            preflight.customTabsAvailable -> LaunchPlan.UseCustomTabs
            preflight.fallbackAvailable -> LaunchPlan.UseFallback
            else -> LaunchPlan.DoNothing(OpenLinkResult.NoAppToHandle)
        }
    }
}
