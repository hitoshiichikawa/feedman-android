package com.feedman.android.core.model

/**
 * Application-wide configuration sourced from `BuildConfig` (Gradle properties).
 *
 * Wrapping `BuildConfig` in a value object keeps ViewModels and Composables independent
 * of the generated `BuildConfig` class, which simplifies unit testing.
 *
 * @property baseUrl Backend API base URL, surfaced as `BuildConfig.BASE_URL` (Req 5.1, 5.3).
 * @property mockMode When `true`, the app boots into the drawer shell + mock timeline
 *   instead of the login placeholder (Req 4.3 / 4.5, Req 5.2 / 5.4).
 */
data class AppConfig(
    val baseUrl: String,
    val mockMode: Boolean,
)
