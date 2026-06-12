package com.feedman.android.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.feedman.android.core.ui.ErrorFullScreen
import com.feedman.android.core.ui.LoadingFullScreen
import java.time.Clock

/**
 * 横断タイムライン画面（Issue #33 / SPEC §5.1）。
 *
 * `CrossFeedRepository` から流れる `Flow<PagingData<ArticleCardModel>>` を
 * [LazyPagingItems] で消費し、共有 [ArticleCard] でカードを描画する。
 *
 * ## カード結線（Req 1.1〜1.7 / 2.1 / 2.2 / 3.1〜3.4 / 4.1〜4.3）
 *
 * - フィード名 + favicon / 相対日時 / タイトル最大 3 行 / 概要最大 2 行 / はてブ数 / スター
 *   / 外部リンクアイコン
 * - 既読カードは [ArticleCard] が `isRead=true` に応じて opacity 0.55 を適用（Req 2.1）
 * - カードタップ → [onOpenItemDetail] を呼ぶ（Req 3.1）
 * - 外部リンクアイコンタップ → [onOpenExternalLink] を呼ぶ（Req 4.1）
 *   IconButton が click event を消費するためカード本体タップは発火しない（Req 4.2）
 * - スタートグルタップ → 本 Issue では no-op（実際のサーバー反映は Issue #38）
 *
 * ## 状態表示（Req 6.1〜6.3）
 *
 * - 初回ロード中（`refresh = Loading` かつ 0 件）→ [LoadingFullScreen]
 * - 初回ロード完了 + 0 件 → [EmptyState]
 * - 初回ロードエラー → [ErrorFullScreen]（再試行で `retry()`）
 * - 追加ロード中 / 追加エラー / 終端フッターは Issue #34 のスコープ（本 Issue では扱わない）
 *
 * ## stable key（Req 5.3）
 *
 * `LazyColumn.items(key = { it.id })` で各カードを記事 ID に紐付け、再コンポジション時に
 * 同一記事を同一インスタンスとして扱う。
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

    TimelineListContent(
        items = pagingItems,
        clock = clock,
        onOpenItemDetail = onOpenItemDetail,
        onOpenExternalLink = onOpenExternalLink,
        modifier = modifier,
    )
}

/**
 * ステートレスなタイムラインリスト本体。Compose UI テスト / プレビューが
 * [LazyPagingItems] を差し替えて再利用できるように分離する。
 */
@Composable
internal fun TimelineListContent(
    items: LazyPagingItems<ArticleCardModel>,
    clock: Clock,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = items.loadState.refresh
    val isInitialLoading = refresh is LoadState.Loading && items.itemCount == 0
    val isInitialError = refresh is LoadState.Error && items.itemCount == 0
    val isInitialEmpty = refresh is LoadState.NotLoading && items.itemCount == 0

    when {
        isInitialLoading -> {
            // Req 6.1
            LoadingFullScreen(modifier = modifier)
        }

        isInitialError -> {
            // Req 6.3
            val errorMessage = (refresh as LoadState.Error).error.message
            ErrorFullScreen(
                onRetry = { items.retry() },
                message = errorMessage,
                modifier = modifier,
            )
        }

        isInitialEmpty -> {
            // Req 6.2
            EmptyState(
                icon = DefaultEmptyStateIcon,
                title = stringResource(id = R.string.timeline_empty),
                modifier = modifier,
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Req 5.3 — id を stable key として渡す。
                items(
                    count = items.itemCount,
                    key = { index ->
                        // LazyPagingItems の placeholder が無効（CrossFeedRepositoryImpl で
                        // enablePlaceholders=false 設定済み）でも、index が itemCount 内に
                        // 収まっていれば peek() は null を返さない。
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
            }
        }
    }
}
