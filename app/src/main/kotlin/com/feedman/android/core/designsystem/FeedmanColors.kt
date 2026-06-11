package com.feedman.android.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Placeholder color tokens for the skeleton (Req 2.2).
 *
 * The real design tokens (oklch grayscale + Indigo accent per `docs/GRAND-DESIGN.md`
 * §5.5) will be introduced in a subsequent design-system Issue. For now we provide a
 * minimal Material3 `ColorScheme` so that `MaterialTheme` can render.
 */
private val IndigoAccent = Color(0xFF3F51B5)

internal val FeedmanLightColors = lightColorScheme(
    primary = IndigoAccent,
    onPrimary = Color.White,
)

internal val FeedmanDarkColors = darkColorScheme(
    primary = IndigoAccent,
    onPrimary = Color.White,
)
