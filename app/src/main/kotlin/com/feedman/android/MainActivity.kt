package com.feedman.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.feedman.android.core.designsystem.FeedmanTheme
import com.feedman.android.shell.AppShell
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity hosting the Compose UI (Req 2.5).
 *
 * Routing between login placeholder and mock timeline is delegated to [AppShell],
 * which inspects the Hilt-provided `AppConfig.mockMode` (Req 4.1, 4.3, 4.5).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeedmanTheme {
                AppShell()
            }
        }
    }
}
