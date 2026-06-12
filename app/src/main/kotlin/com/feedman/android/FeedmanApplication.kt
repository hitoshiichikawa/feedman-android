package com.feedman.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt entry point for the Feedman Android application (Req 2.5, 3.1).
 *
 * Singleton-scoped dependencies are owned by Hilt; the skeleton does not maintain any
 * `object`-based manual singletons (Req 3.4). Subsequent Issues will add lifecycle
 * observers (e.g. session refresh on foreground) here as needed.
 */
@HiltAndroidApp
class FeedmanApplication : Application()
