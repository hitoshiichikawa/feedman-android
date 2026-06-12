package com.feedman.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import com.feedman.android.R
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.model.ItemSearchHit
import com.feedman.android.core.ui.ArticleCard
import com.feedman.android.core.ui.ArticleCardModel
import com.feedman.android.core.ui.EmptyState
import com.feedman.android.core.ui.EndOfListFooter
import com.feedman.android.core.ui.ErrorFooter
import com.feedman.android.core.ui.ErrorFullScreen
import com.feedman.android.core.ui.FeedmanSnackbar
import com.feedman.android.core.ui.ListFooterState
import com.feedman.android.core.ui.LoadingFullScreen
import com.feedman.android.core.ui.OpenLinkResult
import com.feedman.android.core.ui.resolveListFooterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * 横断検索画面（Issue #47 / Issue #48 / SPEC §5.3 / `design/mobile/fm-screens.jsx` `FMSearchScreen`）。
 *
 * - 上部はカスタム検索バー（戻る + 入力欄 + クリア）で構成し、トップアプリバーは非表示にせず
 *   そのまま下に重ねる（AppShell 側の Scaffold TopAppBar は維持。本 Issue では検索バーを
 *   ルート画面内に持たせる構成とした / 確認事項参照）。
 * - 入力欄は起動時に自動フォーカス + ソフトキーボード起動（Req 1.2）
 * - 空クエリ時はサジェストチップ群を表示（Req 2.2）。チップタップで検索を即開始（Req 2.3 / 2.4）
 * - 入力欄から検索送信（IME action）→ ViewModel.submit() → 結果リスト切替（Req 3.1 / 3.3 / 3.4）
 * - 結果はカーソル paging（共通 ArticleCard / Req 4.x）
 * - 0 件 / 失敗 / 追加ロード失敗時の表示を [com.feedman.android.core.ui.EmptyState] /
 *   [ErrorFullScreen] / [ErrorFooter] に委譲（Req 6.1 / 6.3 / 6.5）
 *
 * ## Issue #48 — 検索→詳細ブリッジ
 *
 * - 結果カード本体タップ → 詳細シート起動（Req 1.1）。AppShell 直下の ArticleDetailViewModel に
 *   `open(itemId)` を委譲することで、横断タイムライン・スター一覧と同一の表示・操作仕様で
 *   開く（Req 1.2 / 1.4）。シートを閉じても本画面はスクロール位置・キーワード保持で残る
 *   （NavHost の各ルートが backstack を保持しているため自動的に成立）
 * - 結果カードの外部リンクアイコン → 共通 LinkOpener 経由で元記事を開く（Req 2.1）+
 *   `markReadOnExternalOpen` で既読化トリガーを発行（Req 2.3）。失敗時は snackbar 通知 + 既読化
 *   発行を抑止する（Req 2.4）
 * - ItemSearchHit を ArticleCardModel に変換した上で ItemStateStore.overlays と combine
 *   する経路は ViewModel が担保しているため、Composable 側はカードを描画するだけで
 *   既読 / スター値が他画面の操作と即時同期する（Req 3.1〜3.6）
 *
 * @param onOpenItemDetail 結果カードタップ時のコールバック（Issue #48 Req 1.1 / 1.2）。
 *        AppShell 直下の ArticleDetailViewModel.open(itemId) と接続される。Navigation 側で
 *        no-op を渡す経路はもう存在しない。
 * @param onOpenExternalLink 外部リンクアイコン押下時のコールバック（Issue #48 Req 2.1 / 2.4）。
 *        AppShell 直下の共通 LinkOpener.open(...) と接続される。
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onOpenItemDetail: (itemId: String) -> Unit = {},
    onOpenExternalLink: (url: String) -> OpenLinkResult = { OpenLinkResult.NoAppToHandle },
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val queryInput by viewModel.queryInput.collectAsStateWithLifecycle()
    val submittedQuery by viewModel.submittedQuery.collectAsStateWithLifecycle()
    val unknownLabel = stringResource(id = R.string.search_published_at_unknown)
    // PagingData の map は producer 側で動くため、stringResource を一度キャプチャして mapper に
    // 渡す。再コンポジションのたびに新しい Flow を作らないよう remember で固定する。
    // Issue #48: cardPagingData（overlay 合成済み）を購読する。本 Flow は ArticleCardModel
    // を流すが relativeTimeOverride が null で固定される（VM が Android リソースを読まない
    // ため）。ここで stringResource を読み、UI 側で override を再付与する。
    val cardPagingFlow: Flow<PagingData<ArticleCardModel>> = remember(viewModel, unknownLabel) {
        viewModel.cardPagingData.map { paging ->
            paging.map { card ->
                // ViewModel 側の mapper では UNKNOWN_PUBLISHED_AT="" を入れているため、
                // 描画時に「日時不明」を表示したい場合は publishedAtIso が空のときに
                // override を付ける。サーバー由来 published_at がある通常ケースは
                // relativeTimeOverride = null のままで RelativeTimeFormatter が動く。
                if (card.publishedAtIso.isEmpty() && card.relativeTimeOverride == null) {
                    card.copy(relativeTimeOverride = unknownLabel)
                } else {
                    card
                }
            }
        }
    }
    val pagingItems = cardPagingFlow.collectAsLazyPagingItems()
    val clock = remember { Clock.systemDefaultZone() }
    val snackbarHostState = remember { SnackbarHostState() }
    val markReadFailedMessage = stringResource(id = R.string.article_detail_mark_read_failed)
    val starUpdateFailedMessage = stringResource(id = R.string.article_detail_star_update_failed)
    val openLinkFailedMessage = stringResource(id = R.string.timeline_open_link_failed)

    // Issue #48 Req 2.4: 外部リンク起動自体の失敗（InvalidUrl / NoAppToHandle）通知。
    // 既読化失敗は ItemStateStore.failures（itemStateFailures）に統一されている。
    LaunchedEffect(viewModel) {
        viewModel.externalLinkEvents.collect { event ->
            val message = when (event) {
                SearchExternalLinkEvent.OpenLinkFailed -> openLinkFailedMessage
            }
            FeedmanSnackbar.show(snackbarHostState, message)
        }
    }

    // Issue #48 Req 2.4 / 3.x: ItemStateStore の楽観的更新失敗を購読して snackbar 通知に変換する。
    // 検索画面で生じたトグルだけでなく、シート側で生じたトグルもここで観測される（store は
    // singleton で全画面共通のため）。タイムライン / スター画面と同じ流儀。
    LaunchedEffect(viewModel) {
        viewModel.itemStateFailures.collect { failure ->
            val message = when (failure.kind) {
                ItemStateFailure.Kind.Read -> markReadFailedMessage
                ItemStateFailure.Kind.Star -> starUpdateFailedMessage
            }
            FeedmanSnackbar.show(snackbarHostState, message)
        }
    }

    // Issue #48 Req 2.1 / 2.3 / 2.4: ArticleCard の onOpenLink 結線。LinkOpener の結果を
    // ViewModel に伝え、成功時のみ既読化（store.markRead 経由）、失敗時は ViewModel 経由で
    // 通知する。currentIsRead はカード描画時の値（overlay 合成済み）を渡し、冪等性を保証する。
    val onExternalLinkClicked: (itemId: String, link: String, currentIsRead: Boolean) -> Unit =
        { itemId, link, currentIsRead ->
            when (onOpenExternalLink(link)) {
                OpenLinkResult.OpenedWithCustomTabs,
                OpenLinkResult.OpenedWithFallback -> {
                    viewModel.markReadOnExternalOpen(itemId = itemId, currentIsRead = currentIsRead)
                }
                is OpenLinkResult.InvalidUrl,
                OpenLinkResult.NoAppToHandle -> {
                    viewModel.notifyExternalLinkFailed()
                }
            }
        }

    SearchScreenContent(
        queryInput = queryInput,
        submittedQuery = submittedQuery,
        items = pagingItems,
        suggestions = SearchViewModel.SUGGESTIONS,
        clock = clock,
        snackbarHostState = snackbarHostState,
        onQueryChanged = viewModel::onQueryChanged,
        onClear = viewModel::clear,
        onSubmit = viewModel::submit,
        onSelectSuggestion = viewModel::selectSuggestion,
        onOpenItemDetail = onOpenItemDetail,
        onOpenExternalLink = onExternalLinkClicked,
        onStarToggle = { itemId, newState, baseline ->
            viewModel.toggleStar(itemId = itemId, newState = newState, baselineStarred = baseline)
        },
        modifier = modifier,
    )
}

/** Compose UI / プレビューが LazyPagingItems を差し替えて再利用できるよう分離する。 */
@Composable
internal fun SearchScreenContent(
    queryInput: String,
    submittedQuery: String?,
    items: LazyPagingItems<ArticleCardModel>,
    suggestions: List<String>,
    clock: Clock,
    snackbarHostState: SnackbarHostState,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String, link: String, currentIsRead: Boolean) -> Unit,
    onStarToggle: (itemId: String, newState: Boolean, baselineStarred: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchInputBar(
                value = queryInput,
                onValueChange = onQueryChanged,
                onClear = onClear,
                onSubmit = onSubmit,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (submittedQuery == null) {
                    // Req 2.2: 空クエリ時はサジェストチップを表示
                    SuggestionChips(
                        suggestions = suggestions,
                        onSelect = onSelectSuggestion,
                    )
                } else {
                    SearchResultsArea(
                        submittedQuery = submittedQuery,
                        items = items,
                        clock = clock,
                        onOpenItemDetail = onOpenItemDetail,
                        onOpenExternalLink = onOpenExternalLink,
                        onStarToggle = onStarToggle,
                    )
                }
            }
        }
        // Issue #48 Req 2.4 / 3.x: 外部リンク起動失敗・楽観的更新失敗の snackbar を
        // 画面下部に重ねて配置する。タイムライン / スター画面と同等の流儀。
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 検索入力バー（Req 1.2 / 1.3 / 1.4 / 1.5 / 1.6 / 3.1 / 3.4）。
 *
 * - 左に戻るアイコン（本 Issue では navigation handler を AppShell が持つため、ここでは
 *   placeholder の onClick を持たせない選択肢もあるが、TopAppBar の戻る相当は Shell 側で
 *   既に持つため本入力バーは入力に専念し、戻るボタンは省略している。確認事項参照）
 * - 検索アイコン → TextField → クリアボタン（値非空のとき）の構成
 * - IME action は Search、送信時にキーボードを閉じる（Req 3.4）
 */
@Composable
private fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Req 1.2: 画面表示時に入力欄へフォーカスを当ててソフトウェアキーボードを立ち上げる
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Issue #53 Req 3.4: 固定 48dp ではフォントスケール 200% で TextField の縦寸法が
        // 検索バー枠を超えて切り詰めが発生しうる。heightIn(min) でテキストに追従させる。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(feedman.mutedFg.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Req 1.3: 左端の検索アイコン
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.search_input_icon_description),
                tint = feedman.mutedFg,
                modifier = Modifier.size(18.dp),
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_input_placeholder),
                        fontSize = 15.sp,
                    )
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    // Req 3.1 / 3.4: 検索送信 + キーボードを閉じる
                    onSubmit()
                    keyboardController?.hide()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_INPUT_TEST_TAG),
            )
            // Req 1.4 / 1.5: 値が 1 文字以上のときクリアボタンを表示
            // Issue #53 Req 4.1: 固定 32dp のタップ標的では 44dp 未満となり誤タップ
            // が起きやすいため、sizeIn で 44dp 最小タップ標的を確保する。表示アイコン
            // サイズ（18dp）は維持。
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .sizeIn(
                            minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                            minHeight = MaterialTheme.feedmanDimens.minTapTarget,
                        )
                        .testTag(SEARCH_CLEAR_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear_description),
                        tint = feedman.mutedFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * サジェストチップ群（Req 2.2 / 2.3 / 2.4）。
 *
 * プロトタイプ `FMSearchScreen` 相当の角丸 999dp チップを縦並びでセクションラベル付きで表示。
 * ラップ flex は Compose 標準には無いため、本 Issue では縦に並べる（Row の wrap は将来差替）。
 */
@Composable
private fun SuggestionChips(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.search_suggestions_label),
            color = feedman.mutedFg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag(searchSuggestionTestTag(suggestion)),
                ) {
                    Text(
                        text = suggestion,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * 検索結果領域（Req 3.5 / 4.x / 5.x / 6.x / Issue #48 Req 1.x / 2.x / 3.x）。
 *
 * 進行中 → 初回エラー → 0 件 → 一覧 + フッタ（追加ロード中 / 追加エラー / 終端） の
 * 4 状態を排他的に描画する。横断タイムライン / スター画面と同じ流儀。
 */
@Composable
private fun SearchResultsArea(
    submittedQuery: String,
    items: LazyPagingItems<ArticleCardModel>,
    clock: Clock,
    onOpenItemDetail: (itemId: String) -> Unit,
    onOpenExternalLink: (itemId: String, link: String, currentIsRead: Boolean) -> Unit,
    onStarToggle: (itemId: String, newState: Boolean, baselineStarred: Boolean) -> Unit,
) {
    val refresh = items.loadState.refresh
    val append = items.loadState.append

    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> {
            // Req 3.5: 進行中表示
            LoadingFullScreen()
        }
        refresh is LoadState.Error && items.itemCount == 0 -> {
            // Req 6.3: 先頭ページ取得失敗
            val message = (refresh as? LoadState.Error)?.error?.message
            ErrorFullScreen(onRetry = { items.retry() }, message = message)
        }
        refresh is LoadState.NotLoading && items.itemCount == 0 -> {
            // Req 6.1: 0 件
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.search_empty_title, submittedQuery),
                subtitle = stringResource(R.string.search_empty_subtitle),
            )
        }
        else -> {
            ResultsList(
                items = items,
                append = append,
                clock = clock,
                onOpenItemDetail = onOpenItemDetail,
                onOpenExternalLink = onOpenExternalLink,
                onStarToggle = onStarToggle,
            )
        }
    }
}

/**
 * 結果リスト本体（Req 4.x / 5.x / 6.5 / Issue #48 Req 1.x / 2.x / 3.x）。
 *
 * - 先頭に取得済み件数表示（Req 4.9）
 * - 各カードを共通 [ArticleCard] で描画
 * - フッタ状態は [resolveListFooterState] で排他決定（Loading / Error / EndOfList / None）
 * - カードタップ → onOpenItemDetail（Issue #48 Req 1.1）
 * - 外部リンクアイコン → onOpenExternalLink（Issue #48 Req 2.1 / 2.2: カード本体タップによる
 *   詳細シート起動は ArticleCard 内部で外部リンクの click を消費するため自動抑止）
 * - スタートグルは ItemStateStore 経由で同期する（Issue #48 Req 3.4 / requirements.md
 *   Out of Scope のため UI 上の直接トグルは露出しないが、callback 自体は配線して将来拡張に備える）
 */
@Composable
private fun ResultsList(
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
        // Req 4.9: 取得済み件数を結果領域に提示する
        item(key = "search-results-header") {
            Text(
                text = stringResource(R.string.search_results_count, items.itemCount),
                color = MaterialTheme.feedmanColors.mutedFg,
                fontSize = 12.sp,
            )
        }
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.id ?: index },
        ) { index ->
            val card = items[index] ?: return@items
            ArticleCard(
                model = card,
                onOpen = { id -> onOpenItemDetail(id) },
                // Issue #48 Req 3.4: スタートグルは ItemStateStore 経由で他画面と同期する。
                // 検索結果カード自体に専用トグル UI を露出させないのは requirements.md
                // Out of Scope の方針だが、callback は配線しておく（ArticleCard 仕様上
                // 必須引数のため）。baseline には現在のカード値（overlay 合成済み）を渡し
                // ロールバック先を一意化する。
                onStarToggle = { id, newState ->
                    onStarToggle(id, newState, card.isStarred)
                },
                clock = clock,
                // Issue #48 Req 2.1 / 2.2 / 2.3: 外部リンクアイコン → 共通 LinkOpener + 既読化。
                // ArticleCard 内部で外部リンク click が onOpen を消費しないため、本体タップで
                // 詳細シートが起動する流れと両立する（Req 2.2 の抑止）。currentIsRead を
                // 渡すことで markRead の冪等性を保証する（Req 2.3）。
                onOpenLink = { id -> onOpenExternalLink(id, card.link, card.isRead) },
            )
        }
        when (footerState) {
            ListFooterState.Loading -> item(key = "footer-loading") {
                com.feedman.android.core.ui.LoadingFooter()
            }
            ListFooterState.Error -> item(key = "footer-error") {
                // Req 6.5: 既存カードを保持しつつ追加ロード失敗を提示
                ErrorFooter(onRetry = { items.retry() })
            }
            ListFooterState.EndOfList -> item(key = "footer-end") {
                EndOfListFooter()
            }
            ListFooterState.None -> Unit
        }
    }
}

/** SearchScreen 内の入力欄 testTag（Compose UI テスト用）。 */
const val SEARCH_INPUT_TEST_TAG: String = "feature.search.SearchScreen.Input"

/** SearchScreen 内のクリアボタン testTag（Compose UI テスト用）。 */
const val SEARCH_CLEAR_TEST_TAG: String = "feature.search.SearchScreen.Clear"

/** サジェストチップ testTag のヘルパー（チップ別に識別したいテスト用途）。 */
fun searchSuggestionTestTag(suggestion: String): String =
    "feature.search.SearchScreen.Suggestion.$suggestion"

/**
 * プレビュー / テスト用に空の paging Flow を返す（resultsPaging の初期状態相当）。
 */
internal fun emptySearchResultsPaging(): Flow<PagingData<ItemSearchHit>> =
    flowOf(PagingData.empty())
