package com.feedman.android.feature.timeline

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
 * 横断タイムライン画面（Issue #33 / #34 / SPEC §5.1, §4.2, §6）。
 *
 * `CrossFeedRepository` から流れる `Flow<PagingData<ArticleCardModel>>` を
 * [LazyPagingItems] で消費し、共有 [ArticleCard] でカードを描画する。本 Issue (#34) では
 * pull-to-refresh と無限スクロールに伴う状態表示（初回ロード／空／追加読込／追加エラー／
 * 終端／refresh エラー通知）を確定する。
 *
 * ## pull-to-refresh の規約（Req 1.1〜1.5 / NFR 1.1, 1.2）
 *
 * - Material 3 [PullToRefreshBox] でラップし、`onRefresh` 時に
 *   [LazyPagingItems.refresh] を呼ぶ
 * - `LazyPagingItems.refresh()` は `CrossFeedRepository` の Pager で
 *   `PagingSource` を再生成する。再生成によりリポジトリ側の since_time が破棄され、
 *   新しい初回レスポンスで再固定される（Req 1.4 / `CrossFeedRepositoryImpl` の規約）
 * - フィード単位の手動フェッチ要求は一切発行しない（Req 1.5 / SPEC §4.2 注意）
 *
 * ## 状態表示の規約（Req 2.x / 3.x / 4.x / 5.x / 6.x / NFR 2.1, 3.1, 3.2）
 *
 * - 画面全体状態（InitialLoading / InitialError / Empty / Content）は
 *   [resolveTimelineScreenState] で排他的に決定（NFR 2.1）
 * - 一覧表示中（Content）はフッタを [resolveListFooterState] で決定し、
 *   追加読込中 / 追加エラー / 終端メッセージのいずれかを LazyColumn 末尾に挿入する
 *   （Req 2.2 / 2.4 / 4.1 / NFR 2.2）
 * - 画面全体エラーの再試行 / 追加エラーの再試行はいずれも [LazyPagingItems.retry] を呼ぶ
 *   （Req 3.3 / 4.3）
 * - refresh が失敗したとき、表示中の一覧と内部 since_time は保持し（Req 5.3、
 *   CrossFeedRepository は載っているデータを破棄しない）、snackbar で通知する
 *   （Req 5.1 / 5.2 / NFR 3.2）
 *
 * @param onOpenItemDetail 記事詳細を開くコールバック（実体は Issue #36 / Req 3.4）
 * @param onOpenExternalLink 外部リンクを開くコールバック（実体は Issue #37 / Req 4.3）
 */
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    onOpenItemDetail: (itemId: String) -> Unit = {},
    onOpenExternalLink: (itemId: String) -> Unit = {},
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.cardPagingData.collectAsLazyPagingItems()
    // 相対日時計算用 Clock。テストでは Compose UI Test 側で差し替え可能だが、
    // 本 Issue では Compose UI テストを担当しないため system clock を使う。
    val clock = remember { Clock.systemDefaultZone() }
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorMessage = stringResource(id = R.string.timeline_refresh_error)

    TimelineListContent(
        items = pagingItems,
        clock = clock,
        snackbarHostState = snackbarHostState,
        refreshErrorMessage = refreshErrorMessage,
        onOpenItemDetail = onOpenItemDetail,
        onOpenExternalLink = onOpenExternalLink,
        modifier = modifier,
    )
}

/**
 * ステートレスなタイムラインリスト本体。Compose UI テスト / プレビューが
 * [LazyPagingItems] を差し替えて再利用できるように分離する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineListContent(
    items: LazyPagingItems<ArticleCardModel>,
    clock: Clock,
    snackbarHostState: SnackbarHostState,
    refreshErrorMessage: String,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = items.loadState.refresh
    val append = items.loadState.append
    val screenState = resolveTimelineScreenState(refresh = refresh, itemCount = items.itemCount)

    // Req 5.1 / 5.2 / 5.3: 既存一覧表示中（itemCount > 0）に refresh が Error へ遷移した
    // タイミングで snackbar 通知を発火する。Loading → Error のトランジション検出のため
    // rememberSaveable で「前回の refresh が Loading だったか」を保持する。
    var wasRefreshing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(refresh, items.itemCount) {
        val isRefreshing = refresh is LoadState.Loading
        if (wasRefreshing && refresh is LoadState.Error && items.itemCount > 0) {
            // 既存一覧表示中の pull-to-refresh 失敗のみ通知（初回エラーは画面全体で表示する）
            FeedmanSnackbar.show(snackbarHostState, refreshErrorMessage)
        }
        wasRefreshing = isRefreshing
    }

    // Req 1.2 / 1.3 / NFR 2.2: pull-to-refresh は Material3 PullToRefreshBox を用いる。
    // isRefreshing は refresh=Loading のときに true（インジケータ表示）、それ以外で false。
    // onRefresh は LazyPagingItems.refresh() を呼ぶ → Pager invalidate → since_time リセット
    // → 先頭ページ再取得（Req 1.1 / 1.4）。
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
                    // Req 3.1
                    LoadingFullScreen()
                }

                TimelineScreenState.InitialError -> {
                    // Req 3.2 / 3.3
                    val errorMessage = (refresh as? LoadState.Error)?.error?.message
                    ErrorFullScreen(
                        onRetry = { items.retry() },
                        message = errorMessage,
                    )
                }

                TimelineScreenState.Empty -> {
                    // Req 6.1 / 6.2: 空状態でも PullToRefreshBox の onRefresh は受け付ける
                    EmptyState(
                        icon = DefaultEmptyStateIcon,
                        title = stringResource(id = R.string.timeline_empty),
                    )
                }

                TimelineScreenState.Content -> {
                    TimelineList(
                        items = items,
                        append = append,
                        clock = clock,
                        onOpenItemDetail = onOpenItemDetail,
                        onOpenExternalLink = onOpenExternalLink,
                    )
                }
            }
            // Req 5.2 通知の実描画。Snackbar は画面下部に配置する。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 一覧表示中（[TimelineScreenState.Content]）の LazyColumn を構築する。
 *
 * フッタ状態は [resolveListFooterState] で決定し、追加読込中 / 追加エラー / 終端のいずれかを
 * LazyColumn の末尾アイテムとして挿入する（Req 2.2 / 2.4 / 4.1 / NFR 2.2）。
 */
@Composable
private fun TimelineList(
    items: LazyPagingItems<ArticleCardModel>,
    append: LoadState,
    clock: Clock,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String) -> Unit,
) {
    val footerState = resolveListFooterState(
        isAppendLoading = append is LoadState.Loading,
        isAppendError = append is LoadState.Error,
        // Req 2.4: append が NotLoading かつ endOfPaginationReached=true で終端
        isEndOfPagination = append is LoadState.NotLoading && append.endOfPaginationReached,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Req 5.3 (Issue #33) — id を stable key として渡す。
        items(
            count = items.itemCount,
            key = { index ->
                items.peek(index)?.id ?: index
            },
        ) { index ->
            val card = items[index] ?: return@items
            ArticleCard(
                model = card,
                onOpen = { id -> onOpenItemDetail(id) },
                onStarToggle = { _, _ ->
                    // Issue #38 でサーバー反映を結線する。本 Issue では no-op。
                },
                clock = clock,
                onOpenLink = { id -> onOpenExternalLink(id) },
            )
        }
        // フッタ状態に応じて末尾アイテムを挿入する（排他 / NFR 2.2）。
        when (footerState) {
            ListFooterState.Loading -> item(key = "footer-loading") {
                // Req 2.2
                LoadingFooter()
            }
            ListFooterState.Error -> item(key = "footer-error") {
                // Req 4.1 / 4.3
                ErrorFooter(onRetry = { items.retry() })
            }
            ListFooterState.EndOfList -> item(key = "footer-end") {
                // Req 2.4: 終端メッセージ表示中は追加読込要求を出さない（Req 2.5）
                // → PagingSource が endOfPaginationReached=true を返している状態のため
                //   Paging 3 は自動的に append 要求を発行しない（規約により担保）。
                EndOfListFooter()
            }
            ListFooterState.None -> Unit
        }
    }
}
