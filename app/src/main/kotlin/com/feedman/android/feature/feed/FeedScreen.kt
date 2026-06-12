package com.feedman.android.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.feedman.android.R
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.ui.ArticleCard
import com.feedman.android.core.ui.ArticleCardModel
import com.feedman.android.core.ui.DefaultEmptyStateIcon
import com.feedman.android.core.ui.EmptyState
import com.feedman.android.core.ui.EndOfListFooter
import com.feedman.android.core.ui.ErrorFooter
import com.feedman.android.core.ui.ErrorFullScreen
import com.feedman.android.core.ui.FeedmanSnackbar
import com.feedman.android.core.ui.ListFooterState
import com.feedman.android.core.ui.LoadingFooter
import com.feedman.android.core.ui.LoadingFullScreen
import com.feedman.android.core.ui.OpenLinkResult
import com.feedman.android.core.ui.TimelineScreenState
import com.feedman.android.core.ui.resolveListFooterState
import com.feedman.android.core.ui.resolveTimelineScreenState
import java.time.Clock

/**
 * フィード別画面（Issue #41 / SPEC §5.2）。
 *
 * 縦並びレイアウト:
 *
 * 1. 警告バナー（stopped / error 時のみ / Req 3.1 / 3.2）
 * 2. フィルタタブ（すべて / 未読 / スター / Req 2.1）
 * 3. 記事一覧（共通 [ArticleCard] / 無限スクロール終端表示）または空 / Loading / Error
 *
 * 詳細シート起動と外部リンク起動は AppShell 直下の `ArticleDetailViewModel` / `LinkOpener`
 * を経由するため、本 Composable は親（Navigation 経由）からコールバックを受ける形にする。
 *
 * @param onOpenItemDetail 記事カードタップ時に詳細シートを開くコールバック（Req 1.6）
 * @param onOpenExternalLink 外部リンク起動コールバック（Issue #37 / 結果を OpenLinkResult で返す）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    onOpenItemDetail: (itemId: String) -> Unit = {},
    onOpenExternalLink: (url: String) -> OpenLinkResult = { OpenLinkResult.NoAppToHandle },
    viewModel: FeedScreenViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.cardPagingData.collectAsLazyPagingItems()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    // Issue #42 Req 1.2: 手動フェッチ進行中フラグ。PullToRefreshBox の isRefreshing に渡す。
    val fetchInProgress by viewModel.fetchInProgress.collectAsStateWithLifecycle()

    val clock = remember { Clock.systemDefaultZone() }
    val snackbarHostState = remember { SnackbarHostState() }

    // 文言（NFR 3.1: ハードコード禁止）
    val resumeSucceededMessage = stringResource(id = R.string.feed_resume_succeeded)
    val markReadFailedMessage = stringResource(id = R.string.article_detail_mark_read_failed)
    val starUpdateFailedMessage = stringResource(id = R.string.article_detail_star_update_failed)
    val openLinkFailedMessage = stringResource(id = R.string.timeline_open_link_failed)
    val cooldownNoSecondsMessage = stringResource(id = R.string.feed_fetch_cooldown_no_seconds)

    // 文言テンプレ参照（Issue #42 Req 3.2: 残り秒数 format）
    val context = androidx.compose.ui.platform.LocalContext.current

    // Req 3.7 / 3.8 / Issue #37 / Issue #42 Req 2.1 / 3.1 / 4.1: イベントを snackbar 通知
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                FeedScreenEvent.ResumeSucceeded -> {
                    // Req 3.7: バナー消去（subscription Flow 経由で自動）+ 一覧再取得
                    pagingItems.refresh()
                    resumeSucceededMessage
                }
                is FeedScreenEvent.ResumeFailed -> event.message
                FeedScreenEvent.OpenLinkFailed -> openLinkFailedMessage
                FeedScreenEvent.FetchSucceeded -> {
                    // Issue #42 Req 2.1: 成功で一覧 refresh（Paging invalidate）
                    pagingItems.refresh()
                    // 成功時は snackbar 表示しない（要件には明示要求なし。UX を簡潔に保つ）
                    null
                }
                is FeedScreenEvent.FetchCooldown -> {
                    val seconds = event.retryAfterSeconds
                    if (seconds != null) {
                        // Req 3.2: 残り秒数を含む文言
                        context.getString(R.string.feed_fetch_cooldown_with_seconds, seconds)
                    } else {
                        // Req 3.3: 残り秒数フォールバック文言
                        cooldownNoSecondsMessage
                    }
                }
                is FeedScreenEvent.FetchFailed -> event.message
            }
            if (message != null) {
                FeedmanSnackbar.show(snackbarHostState, message)
            }
        }
    }

    // Issue #38 Req 2.3: ItemStateStore 楽観的更新失敗を snackbar 通知（TimelineScreen と同等）
    LaunchedEffect(viewModel) {
        viewModel.itemStateFailures.collect { failure ->
            val message = when (failure.kind) {
                ItemStateFailure.Kind.Read -> markReadFailedMessage
                ItemStateFailure.Kind.Star -> starUpdateFailedMessage
            }
            FeedmanSnackbar.show(snackbarHostState, message)
        }
    }

    // Req 2.3 / 2.4: フィルタ切替時、ViewModel 側で flatMapLatest で新しい Pager に切替わり、
    // LazyPagingItems が新しい PagingData を受け取ると LazyColumn の itemCount が 0 から再構築
    // されるため、スクロール位置は自然に先頭へ戻る。本 Composable では追加の scrollTo は不要。

    // Issue #42 Req 1.1 / 1.2 / 1.3 / NFR 2.1: Material3 PullToRefreshBox で一覧領域を包む。
    // 警告バナー / フィルタタブ自体はジェスチャ対象外（本体は記事一覧）にするため、
    // PullToRefreshBox は一覧領域のみをラップする。
    val pullState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 警告バナー
            val bannerState = banner
            if (bannerState is FeedStatusBanner.Visible) {
                FeedStatusBannerRow(
                    state = bannerState,
                    onResumeTap = { viewModel.onResumeBannerTap() },
                )
            }
            // 2. フィルタタブ
            FeedFilterTabsRow(
                current = currentFilter,
                onSelect = { viewModel.selectFilter(it) },
            )
            // 3. 記事一覧 / 状態表示（Pull-to-refresh でラップ）
            PullToRefreshBox(
                isRefreshing = fetchInProgress,
                onRefresh = { viewModel.onPullToRefresh() },
                modifier = Modifier.fillMaxSize(),
                state = pullState,
            ) {
                // Req 4.3: subscription が null かつフィードロード完了済みのときフィード未存在として
                // 表示する。判定は「subscription Flow が StateFlow から取得を完了し、null のまま」と
                // するが、画面初期描画の null と区別が付かないため、本実装では一覧の loadState を
                // 併用する。RefreshLoading or RefreshError ならまだ判定保留、Empty/NotLoading で
                // subscription が null なら未存在と判定する。
                FeedScreenListContent(
                    pagingItems = pagingItems,
                    clock = clock,
                    subscriptionLoaded = subscription != null,
                    snackbarHostState = snackbarHostState,
                    onOpenItemDetail = onOpenItemDetail,
                    onExternalLinkClicked = { itemId, link, currentIsRead ->
                        when (onOpenExternalLink(link)) {
                            OpenLinkResult.OpenedWithCustomTabs,
                            OpenLinkResult.OpenedWithFallback -> {
                                viewModel.markReadOnExternalOpen(
                                    itemId = itemId,
                                    currentIsRead = currentIsRead,
                                )
                            }
                            is OpenLinkResult.InvalidUrl,
                            OpenLinkResult.NoAppToHandle -> {
                                viewModel.notifyExternalLinkFailed()
                            }
                        }
                    },
                    onStarToggle = { itemId, newState, baseline ->
                        viewModel.toggleStar(
                            itemId = itemId,
                            newState = newState,
                            baselineStarred = baseline,
                        )
                    },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 警告バナー本体（Req 3.1 / 3.2 / 3.3 / 3.5 / 3.6 / 3.9 / NFR 2.1 / 2.2）。
 *
 * design/mobile/fm-screens.jsx の FMFeedScreen バナー部を踏襲: アイコン → メッセージ →
 * 「再開」テキストボタンの 1 行レイアウト、background = accentSoft、border-bottom。
 * Req 3.9: アイコン / 本文部はタップを無視（clickable を付けない）。Req 3.5: 「再開」ボタン
 * のみ onResumeTap を発火する。
 */
@Composable
internal fun FeedStatusBannerRow(
    state: FeedStatusBanner.Visible,
    onResumeTap: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    val iconTint = when (state.kind) {
        FeedStatusBanner.Kind.STOPPED -> feedman.mutedFg
        FeedStatusBanner.Kind.ERROR -> feedman.danger
    }
    val icon = when (state.kind) {
        FeedStatusBanner.Kind.STOPPED -> Icons.Filled.PauseCircle
        FeedStatusBanner.Kind.ERROR -> Icons.Filled.WarningAmber
    }
    val iconDescription = when (state.kind) {
        FeedStatusBanner.Kind.STOPPED -> stringResource(id = R.string.feed_banner_icon_stopped)
        FeedStatusBanner.Kind.ERROR -> stringResource(id = R.string.feed_banner_icon_error)
    }
    val resolvedMessage = state.message ?: when (state.fallbackMessage) {
        FeedStatusBanner.FallbackMessage.STOPPED_DEFAULT ->
            stringResource(id = R.string.feed_banner_default_stopped_message)
        FeedStatusBanner.FallbackMessage.ERROR_DEFAULT ->
            stringResource(id = R.string.feed_banner_default_error_message)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(feedman.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = resolvedMessage,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f),
        )
        // Req 3.5 / 3.6 / NFR 2.1 / 2.2: 「再開」テキストボタン。進行中は disabled。
        TextButton(
            onClick = onResumeTap,
            enabled = !state.resumeInProgress,
            modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 44.dp),
        ) {
            if (state.resumeInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(id = R.string.feed_banner_resume_button),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * フィルタタブ行（Req 2.1 / 2.3 / NFR 1.1 / 2.1 / 2.2）。
 *
 * design/mobile/fm-screens.jsx の FMFilterTabs を踏襲: pill 形ボタンの横並び、選択中は
 * background = fg / color = bg、非選択は背景透明 + border。
 */
@Composable
internal fun FeedFilterTabsRow(
    current: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FeedFilter.values().forEach { filter ->
            val active = filter == current
            val label = stringResource(
                id = when (filter) {
                    FeedFilter.ALL -> R.string.feed_filter_all
                    FeedFilter.UNREAD -> R.string.feed_filter_unread
                    FeedFilter.STARRED -> R.string.feed_filter_starred
                },
            )
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = 44.dp) // NFR 2.2 タップ標的
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelect(filter) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.surface else feedman.mutedFg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 一覧領域（Req 1.1 / 1.3 / 1.4 / 1.5 / 2.5 / 2.6 / 4.3）。
 *
 * TimelineScreen の [resolveTimelineScreenState] / [resolveListFooterState] を流用して、
 * 初回 Loading / 初回 Error / Empty / Content / 終端フッタを排他的に描画する。
 * フィード未存在（Req 4.3）の判定は subscription Flow と paging refresh 状態の組み合わせで
 * 行う: refresh が NotLoading に到達した時点で `subscription == null` ならフィード未存在。
 */
@Composable
private fun FeedScreenListContent(
    pagingItems: LazyPagingItems<ArticleCardModel>,
    clock: Clock,
    subscriptionLoaded: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenItemDetail: (itemId: String) -> Unit,
    onExternalLinkClicked: (itemId: String, link: String, currentIsRead: Boolean) -> Unit,
    onStarToggle: (itemId: String, newState: Boolean, baselineStarred: Boolean) -> Unit,
) {
    val refresh = pagingItems.loadState.refresh
    val append = pagingItems.loadState.append
    val screenState = resolveTimelineScreenState(refresh = refresh, itemCount = pagingItems.itemCount)

    // Req 4.3: refresh が NotLoading に達した時点で subscription が読み込めていないなら
    // 「フィードが見つかりません」表示にする。Loading 中は判定保留にする。
    val showFeedNotFound = !subscriptionLoaded &&
        refresh is LoadState.NotLoading &&
        pagingItems.itemCount == 0

    if (showFeedNotFound) {
        ErrorFullScreen(
            onRetry = { pagingItems.retry() },
            message = stringResource(id = R.string.feed_not_found),
        )
        return
    }

    when (screenState) {
        TimelineScreenState.InitialLoading -> {
            // Req 1.3 / 2.5: ローディングインジケータ
            LoadingFullScreen()
        }
        TimelineScreenState.InitialError -> {
            // Req 1.5: 初回エラー
            val errorMessage = (refresh as? LoadState.Error)?.error?.message
            ErrorFullScreen(
                onRetry = { pagingItems.retry() },
                message = errorMessage,
            )
        }
        TimelineScreenState.Empty -> {
            // Req 1.4 / 2.6: 空状態
            EmptyState(
                icon = DefaultEmptyStateIcon,
                title = stringResource(id = R.string.feed_empty_title),
                subtitle = stringResource(id = R.string.feed_empty_subtitle),
            )
        }
        TimelineScreenState.Content -> {
            // 記事一覧
            FeedItemList(
                items = pagingItems,
                append = append,
                clock = clock,
                onOpenItemDetail = onOpenItemDetail,
                onOpenExternalLink = onExternalLinkClicked,
                onStarToggle = onStarToggle,
            )
        }
    }
}

@Composable
private fun FeedItemList(
    items: LazyPagingItems<ArticleCardModel>,
    append: LoadState,
    clock: Clock,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String, link: String, currentIsRead: Boolean) -> Unit,
    onStarToggle: (itemId: String, newState: Boolean, baselineStarred: Boolean) -> Unit,
) {
    val footerState = resolveListFooterState(
        isAppendLoading = append is LoadState.Loading,
        isAppendError = append is LoadState.Error,
        isEndOfPagination = append is LoadState.NotLoading && append.endOfPaginationReached,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.id ?: index },
        ) { index ->
            val card = items[index] ?: return@items
            ArticleCard(
                model = card,
                onOpen = { id -> onOpenItemDetail(id) },
                onStarToggle = { id, newState ->
                    onStarToggle(id, newState, card.isStarred)
                },
                clock = clock,
                onOpenLink = { id -> onOpenExternalLink(id, card.link, card.isRead) },
            )
        }
        when (footerState) {
            ListFooterState.Loading -> item(key = "footer-loading") { LoadingFooter() }
            ListFooterState.Error -> item(key = "footer-error") {
                ErrorFooter(onRetry = { items.retry() })
            }
            ListFooterState.EndOfList -> item(key = "footer-end") { EndOfListFooter() }
            ListFooterState.None -> Unit
        }
    }
}
