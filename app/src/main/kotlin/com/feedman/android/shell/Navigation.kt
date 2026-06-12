package com.feedman.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.feedman.android.feature.timeline.TimelineScreen

/**
 * Skeleton [NavHost] exposing only the timeline route (Req 4.3, 4.4).
 *
 * Subsequent Issues will add `feed/{feedId}`, `starred`, `search` and the bottom-sheet
 * triggers per `docs/GRAND-DESIGN.md` §5.6.
 */
@Composable
fun Navigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = RouteTimeline,
        modifier = modifier,
    ) {
        composable(RouteTimeline) {
            TimelineScreen()
        }
    }
}

internal const val RouteTimeline = "timeline"
