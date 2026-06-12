package com.feedman.android.feature.starred

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.feedman.android.R
import com.feedman.android.core.data.ItemStateFailure
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
import com.feedman.android.core.ui.TimelineScreenState
import com.feedman.android.core.ui.resolveListFooterState
import com.feedman.android.core.ui.resolveTimelineScreenState
import java.time.Clock

/**
 * スター一覧画面（Issue #46 / SPEC §5.3 / `design/mobile/fm-screens.jsx` `FMStarredScreen`）。
 *
 * - `StarredViewModel` から `Flow<PagingData<ArticleCardModel>>` を購読し、共通 [ArticleCard]
 *   でカードを描画する（Req 1.3 / 1.4）
 * - pull-to-refresh と無限スクロールに伴う状態表示（初回ロード / 空 / 追加ロード / 追加エラー /
 *   終端 / refresh エラー通知）を、横断タイムライン・フィード別画面と同等の流儀で行う
 *   （Req 2.x / 3.x）
 * - スター解除は overlay 即時反映だが行はリスト上に残置される（StarredViewModel 側の責務 /
 *   Req 5.3）。リフレッシュ後の除去はサーバー filter で自動成立（Req 5.4）
 *
 * @param onOpenItemDetail 記事詳細を開くコールバック（Req 4.1 / 4.2。実体は AppShell 直下の
 *        `ArticleDetailViewModel.open(itemId)`）
 */
@Composable
fun StarredScreen(
    modifier: Modifier = Modifier,
    onOpenItemDetail: (itemId: String) -> Unit = {},
    viewModel: StarredViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.cardPagingData.collectAsLazyPagingItems()
    val clock = remember { Clock.systemDefaultZone() }
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorMessage = stringResource(id = R.string.starred_refresh_error)
    val starUpdateFailedMessage = stringResource(id = R.string.article_detail_star_update_failed)
    val markReadFailedMessage = stringResource(id = R.string.article_detail_mark_read_failed)

    // Req 5.5: ItemStateStore の楽観的更新失敗を snackbar 通知（TimelineScreen / FeedScreen と同等）
    LaunchedEffect(viewModel) {
        viewModel.itemStateFailures.collect { failure ->
            val message = when (failure.kind) {
                ItemStateFailure.Kind.Star -> starUpdateFailedMessage
                ItemStateFailure.Kind.Read -> markReadFailedMessage
            }
            FeedmanSnackbar.show(snackbarHostState, message)
        }
    }

    StarredListContent(
        items = pagingItems,
        clock = clock,
        snackbarHostState = snackbarHostState,
        refreshErrorMessage = refreshErrorMessage,
        onOpenItemDetail = onOpenItemDetail,
        onStarToggle = { itemId, newState, baseline ->
            viewModel.toggleStar(itemId = itemId, newState = newState, baselineStarred = baseline)
        },
        modifier = modifier,
    )
}

/**
 * ステートレスなスター一覧本体。Compose UI テスト / プレビューが [LazyPagingItems] を
 * 差し替えて再利用できるよう分離する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarredListContent(
    items: LazyPagingItems<ArticleCardModel>,
    clock: Clock,
    snackbarHostState: SnackbarHostState,
    refreshErrorMessage: String,
    onOpenItemDetail: (itemId: String) -> Unit,
    onStarToggle: (itemId: String, newState: Boolean, baselineStarred: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = items.loadState.refresh
    val append = items.loadState.append
    val screenState = resolveTimelineScreenState(refresh = refresh, itemCount = items.itemCount)

    // Req 3.4: 既存一覧表示中（itemCount > 0）に refresh が Error へ遷移したタイミングで
    // snackbar 通知を発火する。Loading → Error のトランジション検出のため
    // rememberSaveable で「前回の refresh が Loading だったか」を保持する。
    var wasRefreshing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(refresh, items.itemCount) {
        val isRefreshing = refresh is LoadState.Loading
        if (wasRefreshing && refresh is LoadState.Error && items.itemCount > 0) {
            FeedmanSnackbar.show(snackbarHostState, refreshErrorMessage)
        }
        wasRefreshing = isRefreshing
    }

    // Req 3.2: pull-to-refresh は LazyPagingItems.refresh() を呼ぶ → Pager invalidate →
    // 新しい PagingSource が先頭ページから取得し直す。蓄積中のページは破棄される。
    val pullState = rememberPullToRefreshState()
    val isRefreshing = refresh is LoadState.Loading && items.itemCount > 0

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxSize(),
        state = pullState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (screenState) {
                TimelineScreenState.InitialLoading -> {
                    LoadingFullScreen()
                }

                TimelineScreenState.InitialError -> {
                    // Req 2.5: 初回失敗をエラー表示 + 再試行ボタンで露出
                    val errorMessage = (refresh as? LoadState.Error)?.error?.message
                    ErrorFullScreen(
                        onRetry = { items.retry() },
                        message = errorMessage,
                    )
                }

                TimelineScreenState.Empty -> {
                    // Req 3.1: 0 件読み込み完了時の空状態
                    EmptyState(
                        icon = DefaultEmptyStateIcon,
                        title = stringResource(id = R.string.starred_empty_title),
                        subtitle = stringResource(id = R.string.starred_empty_subtitle),
                    )
                }

                TimelineScreenState.Content -> {
                    StarredList(
                        items = items,
                        append = append,
                        clock = clock,
                        onOpenItemDetail = onOpenItemDetail,
                        onStarToggle = onStarToggle,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 一覧表示中の LazyColumn 本体。フッタ状態は [resolveListFooterState] で決定し、追加読込中 /
 * 追加エラー / 終端のいずれかを LazyColumn の末尾アイテムとして挿入する（Req 2.x）。
 */
@Composable
private fun StarredList(
    items: LazyPagingItems<ArticleCardModel>,
    append: LoadState,
    clock: Clock,
    onOpenItemDetail: (itemId: String) -> Unit,
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
            // スター一覧では外部リンクアイコンを表示しない（プロト FMStarredScreen 準拠 +
            // 詳細シート経由で「元記事を開く」できるため）。onOpenLink を null にする。
            ArticleCard(
                model = card,
                onOpen = { id -> onOpenItemDetail(id) }, // Req 4.1
                onStarToggle = { id, newState ->
                    // Req 5.1 / 5.3: 楽観的更新は ItemStateStore 経由（VM 委譲）。
                    // baseline には現在のカード値（overlay 合成済み）を渡す。
                    onStarToggle(id, newState, card.isStarred)
                },
                clock = clock,
                onOpenLink = null,
            )
        }
        // フッタ状態に応じて末尾アイテムを挿入する（排他）。
        when (footerState) {
            ListFooterState.Loading -> item(key = "footer-loading") {
                LoadingFooter()
            }
            ListFooterState.Error -> item(key = "footer-error") {
                ErrorFooter(onRetry = { items.retry() })
            }
            ListFooterState.EndOfList -> item(key = "footer-end") {
                EndOfListFooter()
            }
            ListFooterState.None -> Unit
        }
    }
}
