package com.feedman.android.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.feedman.android.R

/**
 * Static login placeholder screen shown when the app launches without credentials and
 * mock mode is disabled (Req 4.1, 4.2, 4.5).
 *
 * IMPORTANT: this Composable must NOT invoke any real OAuth flow nor inject
 * `AuthRepository`. Real Google login (Custom Tabs + PKCE) lands in a subsequent
 * Issue per `docs/GRAND-DESIGN.md` §5.3.
 */
@Composable
fun LoginPlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.login_placeholder_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.login_placeholder_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            )
            // Disabled button: real auth flow is intentionally NOT wired here (Req 4.2).
            Button(onClick = {}, enabled = false) {
                Text(stringResource(R.string.login_placeholder_button))
            }
        }
    }
}
