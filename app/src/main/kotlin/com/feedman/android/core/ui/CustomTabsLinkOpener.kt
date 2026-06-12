package com.feedman.android.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.feedman.android.core.designsystem.FeedmanColors

/**
 * [LinkOpener] の本実装（Issue #37 / Req 1.1, 2.1, 3.1, 3.2, 3.3, 4.x / NFR 1.1, 1.2, 2.1, 3.2）。
 *
 * 起動経路選択は純粋関数 [LinkOpenerLogic.decide] に委ね、本クラスは Android 副作用
 * （Custom Tabs / ACTION_VIEW の起動）と端末解決（[PackageManager]）のみを扱う。
 *
 * ## ツールバー色（NFR 1.1 / 1.2）
 *
 * [CustomTabsIntent.Builder] の `setDefaultColorSchemeParams` でアプリのテーマ色
 * （[FeedmanColors.LightSurface] / [FeedmanColors.DarkSurface]）を渡し、
 * `setColorScheme(COLOR_SCHEME_SYSTEM)` でシステムテーマに追従させる。これにより
 * 端末のダークモード切替時にツールバー色が切替後のテーマに整合する（NFR 1.2）。
 *
 * ## フォールバック（Req 3.1 / 3.2）
 *
 * Custom Tabs 対応ブラウザのサービスが解決できないとき、[CustomTabsIntent] が内部で
 * 構築する `Intent.ACTION_VIEW` を生のまま `startActivity` する。[ActivityNotFoundException]
 * を捕捉して [OpenLinkResult.NoAppToHandle] へ畳む（Req 3.3）。
 *
 * ## 単体テスト境界
 *
 * 本クラスは [Context] / [PackageManager] に依存するため JVM 単体テストでは扱わない。
 * 代わりに [LinkOpenerLogic] が判定ロジックを保持し、そちらを網羅検証する（NFR 3.2）。
 */
class CustomTabsLinkOpener : LinkOpener {

    override fun open(context: Context, url: String): OpenLinkResult {
        val validation = UrlValidation.validate(url)

        // 端末解決: Custom Tabs / ACTION_VIEW の両方を試す。
        // 注意: 端末解決は URL が valid であっても行う（次回呼び出しのキャッシュ等が無いため
        // 各呼び出しで確実な現状を取る）。Invalid のときは LaunchPlan.DoNothing が選ばれるため
        // 起動は走らない。
        val resolvedUrl = (validation as? UrlValidation.ValidationResult.Valid)?.url ?: url
        val preflight = preflight(context = context, url = resolvedUrl)

        val plan = LinkOpenerLogic.decide(validation = validation, preflight = preflight)
        return execute(context = context, url = resolvedUrl, plan = plan)
    }

    /**
     * 端末状態を解決する。Custom Tabs 対応サービスの存在と、ACTION_VIEW 解決可否を返す。
     */
    private fun preflight(context: Context, url: String): LinkOpenerLogic.LaunchPreflight {
        val pm = context.packageManager
        val customTabsAvailable = resolveCustomTabsPackage(context) != null
        val fallbackAvailable = resolveViewIntent(pm = pm, url = url)
        return LinkOpenerLogic.LaunchPreflight(
            customTabsAvailable = customTabsAvailable,
            fallbackAvailable = fallbackAvailable,
        )
    }

    /**
     * Custom Tabs 対応ブラウザのパッケージ名を取得（無ければ null）。
     *
     * Androidx の [CustomTabsClient.getPackageName] は端末の `CustomTabsService` を解決して
     * 最適なパッケージを返す公式 API。null のとき Custom Tabs 非対応とみなす（Req 3.1）。
     */
    private fun resolveCustomTabsPackage(context: Context): String? {
        return try {
            CustomTabsClient.getPackageName(context, /* packages = */ null)
        } catch (e: Exception) {
            // Robustness: PackageManager が一時的にエラーを返すケースを silent fail させない
            Log.w(TAG, "Custom Tabs パッケージ解決に失敗: ${e.message}", e)
            null
        }
    }

    /**
     * ACTION_VIEW 解決可否。`PackageManager.resolveActivity` で 1 件以上の解決があれば true。
     */
    private fun resolveViewIntent(pm: PackageManager, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            pm.resolveActivity(intent, 0) != null
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_VIEW 解決に失敗: ${e.message}", e)
            false
        }
    }

    /**
     * 起動計画を実行する。
     */
    private fun execute(
        context: Context,
        url: String,
        plan: LinkOpenerLogic.LaunchPlan,
    ): OpenLinkResult {
        return when (plan) {
            is LinkOpenerLogic.LaunchPlan.DoNothing -> plan.result
            is LinkOpenerLogic.LaunchPlan.UseCustomTabs -> launchCustomTabs(context = context, url = url)
            is LinkOpenerLogic.LaunchPlan.UseFallback -> launchFallback(context = context, url = url)
        }
    }

    /**
     * Custom Tabs を起動する。`ActivityNotFoundException` 等で失敗したらフォールバックへ。
     */
    private fun launchCustomTabs(context: Context, url: String): OpenLinkResult {
        return try {
            val intent = buildCustomTabsIntent(context = context).intent.apply {
                data = Uri.parse(url)
            }
            context.startActivity(intent)
            OpenLinkResult.OpenedWithCustomTabs
        } catch (e: ActivityNotFoundException) {
            // 競合状態（probe 後にブラウザが削除等）でフォールバックを試行する
            Log.w(TAG, "Custom Tabs 起動失敗、ACTION_VIEW にフォールバック: ${e.message}", e)
            launchFallback(context = context, url = url)
        }
    }

    /**
     * ACTION_VIEW で外部ブラウザを起動する。失敗時は [OpenLinkResult.NoAppToHandle]。
     */
    private fun launchFallback(context: Context, url: String): OpenLinkResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            OpenLinkResult.OpenedWithFallback
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ACTION_VIEW 起動失敗: ${e.message}", e)
            OpenLinkResult.NoAppToHandle
        }
    }

    /**
     * [CustomTabsIntent] をテーマ色付きで構築する（NFR 1.1 / 1.2）。
     *
     * `COLOR_SCHEME_SYSTEM` でシステム dark/light に追従させ、各カラースキームのツールバー色
     * を FeedmanTheme の `surface` 色（[FeedmanColors.LightSurface] / [FeedmanColors.DarkSurface]）
     * に合わせる。
     */
    private fun buildCustomTabsIntent(context: Context): CustomTabsIntent {
        val lightParams = androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
            .setToolbarColor(FeedmanColors.LightSurface.toArgb())
            .build()
        val darkParams = androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
            .setToolbarColor(FeedmanColors.DarkSurface.toArgb())
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setDefaultColorSchemeParams(lightParams)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, darkParams)
            .setShowTitle(true)
            .build()

        // CustomTabsService が解決できるなら setPackage で固定する（NFR 2.1 の安定起動）。
        // 解決できないケースでは LaunchPlan.UseFallback が選ばれているため、ここに来ない。
        resolveCustomTabsPackage(context)?.let { pkg ->
            customTabsIntent.intent.setPackage(pkg)
        }

        return customTabsIntent
    }

    private companion object {
        const val TAG = "CustomTabsLinkOpener"
    }
}
