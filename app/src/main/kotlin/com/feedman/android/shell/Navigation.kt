package com.feedman.android.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.feedman.android.core.ui.OpenLinkResult
import com.feedman.android.feature.feed.FeedScreen
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
            )
        }
        composable(AppRoute.Starred.id) {
            StarredRoutePlaceholder()
        }
        composable(AppRoute.Search.id) {
            SearchRoutePlaceholder()
        }
    }
}

@Composable
private fun StarredRoutePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("starred")
    }
}

@Composable
private fun SearchRoutePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("search")
    }
}
