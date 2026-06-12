@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.feedman.android.feature.articledetail

import android.text.Html
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.ui.ErrorFullScreen
import com.feedman.android.core.ui.Favicon
import com.feedman.android.core.ui.FeedmanSheet
import com.feedman.android.core.ui.FeedmanSnackbar
import com.feedman.android.core.ui.HatebuBadge
import com.feedman.android.core.ui.OpenLinkResult
import com.feedman.android.core.ui.RelativeTimeFormatter
import com.feedman.android.core.ui.StarToggle
import com.feedman.android.feature.articledetail.ArticleDetailEvent
import com.feedman.android.feature.articledetail.ArticleDetailUiState
import com.feedman.android.feature.articledetail.ArticleDetailViewModel
import java.time.Clock
import kotlinx.coroutines.launch

/**
 * 記事詳細ボトムシートの testTag（Compose UI テスト想定）。
 */
const val ARTICLE_DETAIL_SHEET_TEST_TAG: String = "feature.articledetail.ArticleDetailSheet"

/**
 * 記事詳細ボトムシート（Issue #36 / Req 1, 2, 3, 4, 5, 6 / NFR 1, 2, 3）。
 *
 * `design/mobile/fm-sheets.jsx` の `FMDetailSheet`（`variant: partial`）を Compose で再現する。
 * 本実装はシート単体の表示・操作・シート内整合に閉じ、外部リンク起動の実体と画面横断同期は
 * 別 Issue（#37 / #38）に切り出される（requirements.md "Out of Scope"）。
 *
 * ## ステートレス分離
 *
 * 本 Composable は `viewModel.uiState` を購読し、内側の [ArticleDetailSheetContent] へ
 * 状態を渡す。`ArticleDetailUiState.Hidden` のときはシートを描画しない（Req 1.1）。
 *
 * ## イベント
 *
 * 楽観的更新の失敗（[ArticleDetailEvent]）は `ViewModel` の `events` SharedFlow から流れ、
 * 本 Composable が [SnackbarHostState] に流して表示する（Req 3.3 / 4.5 / SPEC §6 準拠）。
 *
 * @param onOpenExternal 「元記事を開く」タップ時に呼ばれるコールバック（Issue #37 実体）。
 *        URL を引数に取り Custom Tabs 起動を担当し、[OpenLinkResult] を返す。
 *        本 Composable は返り値が失敗系（[OpenLinkResult.InvalidUrl] /
 *        [OpenLinkResult.NoAppToHandle]）のとき snackbar でエラーを通知し、既読化を発火しない
 *        （Req 4.3 — 未対応 URL では既読状態を変更しない）。
 * @param viewModel 親（呼び出し元 NavHost のルート）で `hiltViewModel()` 経由のインスタンス。
 */
@Composable
fun ArticleDetailSheet(
    onOpenExternal: (url: String) -> OpenLinkResult,
    viewModel: ArticleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 楽観的更新の失敗通知（Req 3.3 / 4.5）。stringResource を Composition 内で resolve する。
    val markReadFailedMsg = stringResource(R.string.article_detail_mark_read_failed)
    val starUpdateFailedMsg = stringResource(R.string.article_detail_star_update_failed)
    // Issue #37: 外部リンク起動失敗時の通知文言
    val openLinkFailedMsg = stringResource(R.string.article_detail_open_link_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                ArticleDetailEvent.MarkReadFailed -> markReadFailedMsg
                ArticleDetailEvent.StarUpdateFailed -> starUpdateFailedMsg
            }
            FeedmanSnackbar.show(snackbarHostState, message)
        }
    }

    if (uiState !is ArticleDetailUiState.Hidden) {
        ArticleDetailSheetContent(
            state = uiState,
            snackbarHostState = snackbarHostState,
            onDismiss = viewModel::dismiss,
            onRetry = viewModel::retry,
            onToggleStar = viewModel::toggleStar,
            onOpenExternalRequested = { detail ->
                // Issue #37 Req 1.1 / 1.2 / 4.3: LinkOpener 経由で起動し、結果に応じて分岐する。
                // 成功（OpenedWithCustomTabs / OpenedWithFallback）→ 既読化（冪等）
                // 失敗（InvalidUrl / NoAppToHandle）→ 既読化を行わず snackbar で通知
                when (onOpenExternal(detail.link)) {
                    OpenLinkResult.OpenedWithCustomTabs,
                    OpenLinkResult.OpenedWithFallback -> {
                        viewModel.markReadOnOpenExternal()
                    }
                    is OpenLinkResult.InvalidUrl,
                    OpenLinkResult.NoAppToHandle -> {
                        coroutineScope.launch {
                            FeedmanSnackbar.show(snackbarHostState, openLinkFailedMsg)
                        }
                    }
                }
            },
        )
    }
}

/**
 * ステートレスなシート本体。状態と callback を受け取り描画する（テスト・プレビュー流用しやすい）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleDetailSheetContent(
    state: ArticleDetailUiState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenExternalRequested: (ItemDetail) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
) {
    // Req 1.5: Android のシステム戻る操作で閉じる。FeedmanSheet 本体は ModalBottomSheet を
    // 内包しており、Material3 の標準動作も既存だが BackHandler で明示的に dismiss を呼ぶ。
    BackHandler(enabled = true) { onDismiss() }

    FeedmanSheet(
        onDismissRequest = onDismiss,
        label = stringResource(R.string.article_detail_sheet_pane_title),
        modifier = Modifier.testTag(ARTICLE_DETAIL_SHEET_TEST_TAG),
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (state) {
                is ArticleDetailUiState.Hidden -> Unit
                is ArticleDetailUiState.Loading -> SheetLoadingBody(onDismiss = onDismiss)
                is ArticleDetailUiState.Error -> SheetErrorBody(
                    message = state.message,
                    onRetry = onRetry,
                    onDismiss = onDismiss,
                )
                is ArticleDetailUiState.Content -> SheetContentBody(
                    state = state,
                    onDismiss = onDismiss,
                    onToggleStar = onToggleStar,
                    onOpenExternalRequested = { onOpenExternalRequested(state.detail) },
                )
            }
            // Snackbar はシート内で表示する（Req 3.3 / 4.5）
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

// ── Loading ───────────────────────────────────────────────────────────────

@Composable
private fun SheetLoadingBody(onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeaderBar(title = null, onClose = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Req 6.1
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Error ────────────────────────────────────────────────────────────────

@Composable
private fun SheetErrorBody(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val fallback = stringResource(R.string.article_detail_load_error)
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeaderBar(title = null, onClose = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
        ) {
            // Req 6.2 / 6.3: 既存 ErrorFullScreen を再利用
            ErrorFullScreen(
                onRetry = onRetry,
                message = message.ifBlank { fallback },
            )
        }
    }
}

// ── Content ──────────────────────────────────────────────────────────────

@Composable
private fun SheetContentBody(
    state: ArticleDetailUiState.Content,
    onDismiss: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenExternalRequested: () -> Unit,
) {
    val detail = state.detail
    // Req 2.1〜2.4: 本文展開・折りたたみ状態。saveable で再コンポジション・横回転を超えて保持。
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    val preview = remember(detail.content, detail.summary) {
        ArticleDetailContentPolicy.resolvePreview(content = detail.content, summary = detail.summary)
    }
    val showExpandToggle = remember(preview) { ArticleDetailContentPolicy.showExpandToggle(preview) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Source row ──────────────────────────────────────────────────
        SourceRow(detail = detail, onClose = onDismiss)

        // ── Scrollable body（title / meta / content / expand） ───────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            // Title
            Text(
                text = detail.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 28.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            // Meta row: HatebuBadge + 余白 + StarToggle + 相対日時
            MetaRow(state = state, onToggleStar = onToggleStar)

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                color = MaterialTheme.feedmanColors.border,
            )

            // Content preview（HTML or プレースホルダ）
            ContentPreview(
                htmlOrText = preview,
                expanded = expanded,
            )

            // 「続きを読む」/「折りたたむ」ボタン
            if (showExpandToggle) {
                val label = if (expanded) {
                    stringResource(R.string.article_detail_collapse)
                } else {
                    stringResource(R.string.article_detail_expand)
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .sizeIn(minWidth = 88.dp, minHeight = 44.dp), // NFR 1.2
                ) {
                    Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // 底部スペース（fixed footer 用）
            Box(modifier = Modifier.size(24.dp))
        }

        // ── Footer action bar（固定） ───────────────────────────────────
        FooterActionBar(
            isStarred = state.isStarred,
            onOpenExternal = onOpenExternalRequested,
            onToggleStar = onToggleStar,
        )
    }
}

@Composable
private fun SourceRow(detail: ItemDetail, onClose: () -> Unit) {
    // Note: ItemDetail には feed_title / feed_favicon_url が含まれない（SPEC §4.2）。
    // 一覧から渡されない構造のため、本実装ではフィード ID を fallback 表示し、favicon は
    // null を渡してレターアバターにする（Req 1.2 / 1.3）。今後一覧と詳細の橋渡しを Issue #38
    // で行う際に CrossFeedItem 由来の feed_title を伝搬する設計を検討する（impl-notes 参照）。
    val faviconDescription = stringResource(
        R.string.article_detail_source_favicon_description,
        detail.feedId,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Favicon(
            faviconValue = null,
            feedTitle = detail.feedId,
            size = 26.dp,
            contentDescription = faviconDescription,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.feedId,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val clock = remember { Clock.systemDefaultZone() }
            val relTime = remember(detail.publishedAt, detail.isDateEstimated) {
                runCatching {
                    RelativeTimeFormatter.format(
                        publishedAtIso = detail.publishedAt,
                        isDateEstimated = detail.isDateEstimated,
                        clock = clock,
                    )
                }.getOrDefault(detail.publishedAt)
            }
            Text(
                text = if (detail.author.isNotBlank()) "$relTime · ${detail.author}" else relTime,
                color = MaterialTheme.feedmanColors.mutedFg,
                fontSize = 11.5f.sp,
                maxLines = 1,
            )
        }
        // Req 1.4: 閉じるボタン
        IconButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(
                minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.article_detail_close_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MetaRow(state: ArticleDetailUiState.Content, onToggleStar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Req 5.1: はてブ数
        HatebuBadge(
            hatebuCount = state.detail.hatebuCount,
            hatebuFetchedAt = state.detail.hatebuFetchedAt,
        )
        // Spacer
        Box(modifier = Modifier.weight(1f))
        // Req 5.1 / 5.3: スター
        StarToggle(isStarred = state.isStarred, onToggle = { onToggleStar() })
    }
}

@Composable
private fun ContentPreview(htmlOrText: String?, expanded: Boolean) {
    // Req 2.6: 本文プレビューが空のときはプレースホルダ
    if (htmlOrText == null) {
        Text(
            text = stringResource(R.string.article_detail_content_empty),
            color = MaterialTheme.feedmanColors.mutedFg,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        )
        return
    }

    // Req 2.1: 本文を約 200dp 高さに収め、下端フェード（折りたたみ時のみ）。
    val previewBg = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val collapsedHeight = 200.dp

    Box(
        modifier = if (expanded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(max = collapsedHeight)
        },
    ) {
        // 本文表示: sanitized HTML を Html.fromHtml で TextView に流し込む
        // （Req 2.5 — 見出し・段落・リンク・強調・引用・コード・リストが視認できる程度の表示。
        //   フル仕様の HTML レンダリングは Out of Scope）
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                TextView(context).apply {
                    textSize = 15f
                    setTextColor(onSurface.toArgb())
                    setLineSpacing(0f, 1.5f)
                }
            },
            update = { textView ->
                @Suppress("DEPRECATION") // FROM_HTML_MODE_COMPACT は API 24+ で利用可能
                textView.text = Html.fromHtml(htmlOrText, Html.FROM_HTML_MODE_COMPACT)
            },
        )

        // Req 2.1: 下端フェード（折りたたみ時のみ）
        if (!expanded) {
            val fadeHeightDp = 56.dp
            val fadeHeightPx = with(density) { fadeHeightDp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = fadeHeightDp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, previewBg),
                            startY = 0f,
                            endY = fadeHeightPx,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun FooterActionBar(
    isStarred: Boolean,
    onOpenExternal: () -> Unit,
    onToggleStar: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.feedmanColors.border)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Req 4.1, 4.2: 「元記事を開く」主ボタン（フッタ固定）
        Button(
            onClick = onOpenExternal,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(13.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.article_detail_open_external),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // Req 4.4 / 4.6: フッタのスター（本文上部と同じ状態を共有）
        StarToggle(isStarred = isStarred, onToggle = { onToggleStar() })
    }
}

@Composable
private fun SheetHeaderBar(title: String?, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(
                minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.article_detail_close_description),
            )
        }
    }
}

// AndroidView 用に Compose の Color を Android ARGB 整数へ変換する小さなヘルパ
private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt(),
    (red * 255f).toInt(),
    (green * 255f).toInt(),
    (blue * 255f).toInt(),
)
