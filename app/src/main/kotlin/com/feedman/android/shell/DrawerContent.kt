package com.feedman.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
 * App Shell のナビゲーションドロワー本体（Issue #29 / Req 2.4, 2.5, 4.1, 4.3）。
 *
 * `design/mobile/fm-screens.jsx` の `FMDrawer` を骨格として、以下のセクションを並べる:
 *
 * 1. ヘッダ（アプリ名 + ユーザー placeholder）
 * 2. メイン項目（[drawerMainItems]）— 「すべての新着」「お気に入り」
 * 3. フィード一覧プレースホルダ — #30 の領分のため静的 placeholder
 * 4. フッタ（[drawerFooterItems]）— アカウント / テーマ切替（**v1 では
 *    キーワード通知を出さない / Req 4.1, 4.2**）
 *
 * @param currentRouteId 現在表示中のルート ID（active 表示の判定）。
 * @param onSelectMainItem メイン項目が選択されたときのコールバック。AppShell が受け取って
 *   `navController.navigate` と `drawerState.close()` を実行する（Req 2.4, 2.5）。
 * @param onSelectFooterItem フッタ項目が選択されたときのコールバック。本 Issue では
 *   配線先（Account / ThemeToggle）はまだ実装されていないため、AppShell が暫定の
 *   no-op ラムダを渡す（後続 Issue で配線）。
 */
@Composable
fun DrawerContent(
    currentRouteId: String,
    onSelectMainItem: (DrawerMainItem) -> Unit,
    onSelectFooterItem: (DrawerFooterItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DrawerHeader()
            HorizontalDivider()

            // メイン項目（Req 2.4, 2.5）
            drawerMainItems.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label()) },
                    selected = currentRouteId == item.targetRouteId(),
                    onClick = { onSelectMainItem(item) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // フィード一覧プレースホルダ（#30 の領分）
            Spacer(modifier = Modifier.padding(top = 8.dp))
            DrawerFeedsPlaceholder()

            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()

            // フッタ項目（Req 4.1, 4.2, 4.3）
            drawerFooterItems.forEach { footerItem ->
                NavigationDrawerItem(
                    label = { Text(footerItem.label()) },
                    selected = false,
                    onClick = { onSelectFooterItem(footerItem) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.drawer_account_placeholder_email),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DrawerFeedsPlaceholder() {
    // #30 でフィード一覧（未読バッジ・状態アイコン・設定）を実装するため、
    // 本 Issue では領域だけを確保した静的 placeholder を置く。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.drawer_section_feeds),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = stringResource(R.string.drawer_feeds_placeholder_pending),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** メイン項目の表示文言を返す（Composable から localized 文字列を取得）。 */
@Composable
private fun DrawerMainItem.label(): String = when (this) {
    DrawerMainItem.Timeline -> stringResource(R.string.drawer_timeline)
    DrawerMainItem.Starred -> stringResource(R.string.drawer_starred)
}

/** フッタ項目の表示文言を返す。 */
@Composable
private fun DrawerFooterItem.label(): String = when (this) {
    DrawerFooterItem.Account -> stringResource(R.string.drawer_footer_account)
    DrawerFooterItem.ThemeToggle -> stringResource(R.string.drawer_footer_theme_toggle)
}

@Suppress("UnusedPrivateMember")
private val DRAWER_ITEM_PADDING: PaddingValues = NavigationDrawerItemDefaults.ItemPadding
