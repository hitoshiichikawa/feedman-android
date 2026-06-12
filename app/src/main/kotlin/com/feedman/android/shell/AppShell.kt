package com.feedman.android.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.feedman.android.R
import com.feedman.android.core.model.AppConfig
import com.feedman.android.feature.login.LoginPlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Boot routing for the app (Req 4.1, 4.3, 4.5).
 *
 * Inspects the Hilt-provided [AppConfig.mockMode] flag:
 * - `false` (default per Req 5.4) → renders the login placeholder.
 * - `true` (set via `-Pfeedman.mockMode=true`) → renders the drawer shell with the
 *   mock timeline route.
 */
@Composable
fun AppShell() {
    val viewModel: AppShellViewModel = hiltViewModel()
    if (viewModel.appConfig.mockMode) {
        MockModeShell()
    } else {
        LoginPlaceholderScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockModeShell() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { DrawerContent() },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.timeline_title)) },
                )
            },
        ) { padding ->
            Navigation(modifier = Modifier.padding(padding))
        }
    }
}

/**
 * Lightweight [ViewModel] that exposes [AppConfig] to [AppShell].
 *
 * Wrapping the read in a Hilt ViewModel avoids relying on `BuildConfig` from Composable
 * code, which keeps Compose previews and unit tests independent of generated code.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    val appConfig: AppConfig,
) : ViewModel()
