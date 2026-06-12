package com.feedman.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Minimal Material3 theme wrapper for the skeleton.
 *
 * Selects light / dark color scheme based on system setting. The full design-token
 * mapping (oklch → ARGB, accent Indigo, read-opacity 0.55 etc. per
 * `docs/GRAND-DESIGN.md` §5.5) is deferred to a subsequent Issue.
 */
@Composable
fun FeedmanTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) FeedmanDarkColors else FeedmanLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
