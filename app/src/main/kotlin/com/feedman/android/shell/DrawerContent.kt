package com.feedman.android.shell

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.feedman.android.R

/**
 * Minimal drawer body shown by the mock-mode shell (Req 2.4, 4.3).
 *
 * The skeleton only renders the cross-feed timeline entry. Subsequent Issues will
 * add the favorites / per-feed list / footer per `docs/GRAND-DESIGN.md` §3.
 */
@Composable
fun DrawerContent(modifier: Modifier = Modifier) {
    ModalDrawerSheet(modifier = modifier.fillMaxHeight()) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(16.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_timeline)) },
            selected = true,
            onClick = { /* timeline is the only route in the skeleton */ },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_starred)) },
            selected = false,
            onClick = { /* not implemented in skeleton */ },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}
