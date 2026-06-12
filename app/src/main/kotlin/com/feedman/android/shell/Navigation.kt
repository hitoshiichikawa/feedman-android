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
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Timeline.id,
        modifier = modifier,
    ) {
        composable(AppRoute.Timeline.id) {
            TimelineScreen()
        }
        composable(
            route = AppRoute.Feed.id,
            arguments = listOf(
                navArgument(AppRoute.Feed.ARG_FEED_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            // Req 1.3: パスパラメータ feedId を遷移先に渡す。本 Issue では実画面が無いため
            // placeholder で受領を可視化するだけに留める。実画面は #30 / 個別 Issue で接続。
            val feedId = backStackEntry.arguments?.getString(AppRoute.Feed.ARG_FEED_ID).orEmpty()
            FeedRoutePlaceholder(feedId = feedId)
        }
        composable(AppRoute.Starred.id) {
            StarredRoutePlaceholder()
        }
        composable(AppRoute.Search.id) {
            SearchRoutePlaceholder()
        }
    }
}

/**
 * フィード別ルートの placeholder（実画面は別 Issue が担当）。
 *
 * `feedId` を画面に表示するのは Req 1.3 のパスパラメータ受領を視認できるようにするため。
 */
@Composable
private fun FeedRoutePlaceholder(feedId: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("feed: $feedId")
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
