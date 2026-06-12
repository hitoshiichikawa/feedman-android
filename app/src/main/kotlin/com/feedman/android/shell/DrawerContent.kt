package com.feedman.android.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.ui.Favicon

/**
 * App Shell のナビゲーションドロワー本体（Issue #29 / Issue #30 / Issue #31）。
 *
 * `design/mobile/fm-screens.jsx` の `FMDrawer` / `FMFeedListBody` を骨格として、以下の
 * セクションを並べる:
 *
 * 1. ヘッダ（アプリ名 + ユーザー領域。Issue #31: ユーザー領域はタップ可能でアカウントシートを起動）
 * 2. メイン項目（[drawerMainItems]）— 「すべての新着」「お気に入り」（#29）
 * 3. **フィード一覧**（#30 / #31）— セクション見出し横に + ボタン（Issue #31 Req 5.1）
 * 4. フッタ（[drawerFooterItems]）— アカウント / テーマ切替
 *
 * フィード行（[DrawerFeedRowItem]）の構成（Req 1.2 / NFR 1.1）:
 * `Favicon` + タイトル（省略表記）+ 状態アイコン + 未読バッジ + 設定アイコン
 *
 * @param currentRouteId 現在表示中のルート ID（active 表示の判定）。
 * @param onSelectMainItem メイン項目が選択されたときのコールバック（#29 Req 2.4, 2.5）。
 * @param onSelectFooterItem フッタ項目が選択されたときのコールバック（#29 Req 4.x）。
 * @param onSelectFeed フィード行がタップされたときのコールバック。AppShell が受け取って
 *   `feed/{feedId}` への遷移とドロワークローズを実行する（#30 Req 3.1, 3.2, 3.3）。
 * @param onSelectFeedSettings 設定アイコンがタップされたときのコールバック。本 Issue では
 *   no-op として配線のみを行い、#43 でシート本体を実装する（#30 Req 4.1, 4.2, 4.3）。
 * @param onAccountAreaTap ヘッダのユーザー領域タップ時のコールバック（#31 Req 4.2, 4.3）。
 * @param onAddFeedTap フィードセクション + ボタンタップ時のコールバック（#31 Req 5.2, 5.3）。
 */
@Composable
fun DrawerContent(
    currentRouteId: String,
    onSelectMainItem: (DrawerMainItem) -> Unit,
    onSelectFooterItem: (DrawerFooterItem) -> Unit,
    onSelectFeed: (DrawerFeedRow) -> Unit,
    onSelectFeedSettings: (DrawerFeedRow) -> Unit,
    onAccountAreaTap: () -> Unit,
    onAddFeedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DrawerViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DrawerContentStateless(
        currentRouteId = currentRouteId,
        rows = uiState.rows,
        onSelectMainItem = onSelectMainItem,
        onSelectFooterItem = onSelectFooterItem,
        onSelectFeed = onSelectFeed,
        onSelectFeedSettings = onSelectFeedSettings,
        onAccountAreaTap = onAccountAreaTap,
        onAddFeedTap = onAddFeedTap,
        modifier = modifier,
    )
}

/**
 * Composable 起動なしのプレビュー / テストから利用するための stateless 実体（NFR 3.2）。
 */
@Composable
internal fun DrawerContentStateless(
    currentRouteId: String,
    rows: List<DrawerFeedRow>,
    onSelectMainItem: (DrawerMainItem) -> Unit,
    onSelectFooterItem: (DrawerFooterItem) -> Unit,
    onSelectFeed: (DrawerFeedRow) -> Unit,
    onSelectFeedSettings: (DrawerFeedRow) -> Unit,
    onAccountAreaTap: () -> Unit,
    onAddFeedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DrawerHeader(onAccountAreaTap = onAccountAreaTap)
            HorizontalDivider()

            // メイン項目（#29 Req 2.4, 2.5）
            drawerMainItems.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label()) },
                    selected = currentRouteId == item.targetRouteId(),
                    onClick = { onSelectMainItem(item) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // フィード一覧（#30 Req 1.1, 1.2, 1.3, 1.5 / #31 Req 5.1）
            Spacer(modifier = Modifier.padding(top = 8.dp))
            DrawerFeedsSection(
                rows = rows,
                onSelectFeed = onSelectFeed,
                onSelectFeedSettings = onSelectFeedSettings,
                onAddFeedTap = onAddFeedTap,
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()

            // フッタ項目（#29 Req 4.x）
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

/**
 * ドロワーヘッダ（Issue #29 / Issue #31 Req 4.1, 4.2, 4.5）。
 *
 * - アプリ名 + ユーザー領域（アイコン + メールアドレス placeholder）を表示する
 * - ユーザー領域は単一の `Row` として `clickable` し、タップで [onAccountAreaTap] を発火する
 *   （Req 4.2: アカウントシート起動 + Req 4.3: ドロワー閉。後者は呼び出し元責務）
 * - 全体タップ領域は最小 48dp 高を `defaultMinSize` で確保する（NFR 3.1: 48dp 四方）
 * - スクリーンリーダー向け a11y ラベルは「アカウント」（Req 4.5）
 */
@Composable
private fun DrawerHeader(onAccountAreaTap: () -> Unit) {
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
        // Req 4.1: タップ可能なユーザー領域。
        val accountDescription = stringResource(R.string.drawer_action_account)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAccountAreaTap)
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 4.dp)
                .semantics { contentDescription = accountDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.drawer_account_placeholder_email),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * ドロワーのフィード一覧セクション（Issue #30 / Req 1.1, 1.3, 1.5 / Issue #31 Req 5.1）。
 *
 * - リポジトリが返した順序のまま行を並べる（Req 1.5）
 * - rows が空のときはフィード行を 1 件も描画しない（Req 1.3 / セクション見出しは残す）
 * - セクション見出し（「フィード」）の横に + ボタンを配置し、フィード登録シートを起動する
 *   入口とする（Issue #31 Req 5.1, 5.5）
 */
@Composable
private fun DrawerFeedsSection(
    rows: List<DrawerFeedRow>,
    onSelectFeed: (DrawerFeedRow) -> Unit,
    onSelectFeedSettings: (DrawerFeedRow) -> Unit,
    onAddFeedTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.drawer_section_feeds),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            // Issue #31 Req 5.1, 5.5 / NFR 3.1: 48dp タッチターゲット確保
            IconButton(
                onClick = onAddFeedTap,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.drawer_action_add_feed),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        rows.forEach { row ->
            DrawerFeedRowItem(
                row = row,
                onSelectFeed = onSelectFeed,
                onSelectFeedSettings = onSelectFeedSettings,
            )
        }
    }
}

/**
 * 1 フィード行の描画（Issue #30 / Req 1.2, 1.4, 2.4, 3.1, 4.1）。
 *
 * 行の配置（左 → 右、Req 1.2 / NFR 1.1）:
 * favicon → タイトル → 状態アイコン → 未読バッジ → 設定アイコン
 *
 * - タイトルは [TextOverflow.Ellipsis] + `maxLines = 1` で省略表記（Req 1.4 / NFR 1.2）
 * - 行（設定アイコン以外）タップで [onSelectFeed] を発火（Req 3.1）
 * - 設定アイコンタップで [onSelectFeedSettings] を発火し、行タップ伝播を抑制（Req 4.2）
 */
@Composable
private fun DrawerFeedRowItem(
    row: DrawerFeedRow,
    onSelectFeed: (DrawerFeedRow) -> Unit,
    onSelectFeedSettings: (DrawerFeedRow) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelectFeed(row) } // Req 3.1: 設定アイコン以外の領域タップで遷移要求
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Favicon(
            faviconValue = row.faviconValue,
            feedTitle = row.title,
            size = MaterialTheme.feedmanDimens.faviconMedium,
            contentDescription = stringResource(R.string.drawer_feed_favicon_description, row.title),
        )
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis, // Req 1.4 / NFR 1.2: 末尾省略 1 行
            modifier = Modifier.weight(1f),
        )
        // 状態アイコン（Req 2.1, 2.2, 2.3, 2.4 — 未読バッジの左に配置）
        FeedStatusIconView(icon = row.statusIcon)
        // 未読バッジ（Req 1.1.1, 1.1.2）
        if (row.showUnreadBadge) {
            UnreadBadge(count = row.unreadCount)
        }
        // 設定アイコン（Req 4.1, 4.2, 4.3）
        IconButton(
            onClick = { onSelectFeedSettings(row) }, // Req 4.2: 行 onClick は親 Row、IconButton は独自 onClick で伝播分離
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(
                    R.string.drawer_feed_settings_description,
                    row.title,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MaterialTheme.feedmanDimens.iconSmall),
            )
        }
    }
}

/**
 * 状態アイコン表示（Req 2.1, 2.2, 2.3）。
 */
@Composable
private fun FeedStatusIconView(icon: FeedStatusIcon) {
    when (icon) {
        FeedStatusIcon.None -> Unit // Req 2.3: 非表示（描画しない）
        FeedStatusIcon.Stopped -> Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = stringResource(R.string.drawer_feed_status_stopped),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MaterialTheme.feedmanDimens.iconSmall),
        )
        FeedStatusIcon.Error -> Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = stringResource(R.string.drawer_feed_status_error),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(MaterialTheme.feedmanDimens.iconSmall),
        )
    }
}

/**
 * 未読バッジ（Req 1.1.1）。簡素な Pill 形のテキストバッジで件数を表示する。
 */
@Composable
private fun UnreadBadge(count: Int) {
    val description = stringResource(R.string.drawer_feed_unread_badge_description, count)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
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
