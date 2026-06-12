package com.feedman.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.feedman.android.core.ui.OpenLinkResult
import com.feedman.android.feature.feed.FeedScreen
import com.feedman.android.feature.search.SearchScreen
import com.feedman.android.feature.starred.StarredScreen
import com.feedman.android.feature.timeline.TimelineScreen

/**
 * App Shell が宣言する NavHost（Issue #29 / Req 1.1〜1.5）。
 *
 * `requirements.md` Requirement 1.2 のとおり、宣言するのは [AppRoute] で定義した 4
 * ルートのみ。記事詳細・フィード登録・購読設定・アカウントは NavHost に登録せず、
 * ボトムシート前提として後続 Issue が呼び出し元画面（各ルート）上に直接表示する
 * （Req 1.5）。
 *
 * 起動時の初期表示は `timeline` ルート（Req 1.1）。
 *
 * フィード別・スター・検索の画面実体は #30 / 既存 placeholder で順次差し替えられる
 * 前提のため、本 Issue では暫定 placeholder Composable を内側で描画する。
 *
 * @param navController 上位 [AppShell] が保持する [NavHostController]。ドロワーからの
 *   遷移はすべてこのコントローラを介して行われ、シェル枠は遷移をまたいで保持される
 *   （Req 1.4）。
 */
@Composable
fun Navigation(
    navController: NavHostController,
    onOpenItemDetail: (itemId: String) -> Unit = {},
    onOpenExternalLink: (url: String) -> OpenLinkResult = { OpenLinkResult.NoAppToHandle },
    onOpenSubscriptionSettings: (feedId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Timeline.id,
        modifier = modifier,
    ) {
        composable(AppRoute.Timeline.id) {
            // Issue #33: タイムラインカードからのコールバックを結線する。
            // - onOpenItemDetail: Issue #36 でシェル直下の ArticleDetailSheet を起動
            //   （open(itemId) を渡す）
            // - onOpenExternalLink: Issue #37 で Custom Tabs 起動 + 既読化を担当（AppShell 経由）
            TimelineScreen(
                onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) },
                onOpenExternalLink = onOpenExternalLink,
            )
        }
        composable(
            route = AppRoute.Feed.id,
            arguments = listOf(
                navArgument(AppRoute.Feed.ARG_FEED_ID) { type = NavType.StringType },
            ),
        ) {
            // Issue #41: 実画面に置換。feedId は FeedScreenViewModel が SavedStateHandle
            // 経由で受け取るため、ここで backStackEntry から再取得する必要はない。
            FeedScreen(
                onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) },
                onOpenExternalLink = onOpenExternalLink,
                onOpenSettings = onOpenSubscriptionSettings,
            )
        }
        composable(AppRoute.Starred.id) {
            // Issue #46: スター一覧画面を起動。記事タップで AppShell 直下の
            // ArticleDetailViewModel に open(itemId) を伝える（Req 4.1）。
            StarredScreen(
                onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) },
            )
        }
        composable(AppRoute.Search.id) {
            // Issue #48: 横断検索画面の結線。横断タイムライン / スター一覧と同じく、記事タップで
            // AppShell 直下の ArticleDetailViewModel.open(itemId) を呼び、外部リンクアイコンで
            // 共通 LinkOpener 経由の起動 + 既読化を行う（Req 1.1 / 2.1）。
            SearchScreen(
                onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) },
                onOpenExternalLink = onOpenExternalLink,
            )
        }
    }
}
