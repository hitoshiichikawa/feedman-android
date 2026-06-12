package com.feedman.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens
import java.time.Clock

/**
 * 共有記事カードに渡す描画モデル（Issue #27 / Issue #33）。
 *
 * `MockTimelineItem` や API モデル（`ItemSummary` / `ItemSearchHit`）に依存せず、
 * 4 系統カード（横断タイムライン・フィード別・スター・検索）から同じ部品で
 * 描画できるよう中立的なフィールドだけを保持する（Req 5.1〜5.4）。
 *
 * Issue #33 で [summary] フィールドを追加した（横断タイムラインカードの概要 2 行表示。
 * 既定値 `""` のため既存呼び出し側は概要を表示しない挙動を維持する）。
 *
 * @property id 記事 ID（クリックハンドラ経由で呼び出し側に渡す）
 * @property title 記事タイトル
 * @property feedTitle ソースフィードタイトル
 * @property faviconValue Favicon の data URL または `null`（[Favicon] へそのまま渡す）
 * @property publishedAtIso RFC3339 形式の published_at
 * @property isDateEstimated `true` のとき相対日時に "(推定)" を付加
 * @property isRead 既読フラグ（true なら不透明度 0.55）
 * @property isStarred スター状態
 * @property hatebuCount はてブ数
 * @property hatebuFetchedAt はてブ取得時刻。`null` は取得未実施（"−" 表示）
 * @property summary 記事概要文字列。Issue #33 Req 1.4 / 1.5 に従い、空文字列のとき
 *   概要行を描画せずレイアウト領域も確保しない。既定値は `""`（後方互換）。
 */
data class ArticleCardModel(
    val id: String,
    val title: String,
    val feedTitle: String,
    val faviconValue: String?,
    val publishedAtIso: String,
    val isDateEstimated: Boolean,
    val isRead: Boolean,
    val isStarred: Boolean,
    val hatebuCount: Int,
    val hatebuFetchedAt: String?,
    val summary: String = "",
)

/**
 * 外部リンク（タイムラインカード）テスト用 [testTag]。
 */
const val ARTICLE_CARD_OPEN_LINK_TEST_TAG: String = "core.ui.ArticleCard.OpenLink"

/**
 * 共有記事カード（Issue #27 / Req 4.1, 4.2, 4.3, 4.4 / Req 5.1〜5.4 / NFR 3.1）。
 *
 * プロト `design/mobile/fm-ui.jsx` の `FMArticleCard`（standard variant）を Compose 上に
 * 再現する。横断タイムライン・フィード別・スター一覧・検索結果の 4 系統で共通して使う。
 *
 * 既読時の見た目（Req 4.1, 4.2, 4.3）:
 * - `isRead=true` → カード全体に [Modifier.alpha] 0.55 を一括適用
 * - `isRead=false` → 不透明度 1.0
 * - alpha は子要素全てに伝播するため、タイトル・サマリ・メタ部品（スター / はてブ数 /
 *   相対日時）が同一の opacity で減衰する
 *
 * インタラクション（Req 4.4）:
 * - カード本体タップで [onOpen] を呼び出す（既読状態でも引き続きクリック可能）
 * - スタートグルは独自の click 領域を持ち、本タップを消費して [onStarToggle] のみ呼ぶ
 *
 * @param model 表示用モデル
 * @param onOpen カード本体タップ時のコールバック。引数は `model.id`
 * @param onStarToggle スタートグルタップ時のコールバック。第 1 引数は `model.id`、
 *        第 2 引数は **新しい** スター状態（!model.isStarred）
 * @param clock 相対日時計算用 [Clock]。テストでは [Clock.fixed] を渡す
 * @param modifier 追加 [Modifier]
 * @param onOpenLink 外部リンクアイコンタップ時のコールバック（Issue #33 Req 4.1）。
 *        `null` のときアイコンを描画しない（既存呼び出し側の後方互換）。
 *        非 null のとき [Icons.AutoMirrored.Outlined.OpenInNew] アイコンを描画し、
 *        タップ時にカード本体タップへ伝播せず本コールバックのみを呼ぶ（Req 3.3 / 4.2）。
 */
@Composable
fun ArticleCard(
    model: ArticleCardModel,
    onOpen: (id: String) -> Unit,
    onStarToggle: (id: String, newState: Boolean) -> Unit,
    clock: Clock,
    modifier: Modifier = Modifier,
    onOpenLink: ((id: String) -> Unit)? = null,
) {
    val dimens = MaterialTheme.feedmanDimens
    val feedman = MaterialTheme.feedmanColors

    val cardAlpha: Float = if (model.isRead) feedman.readForegroundAlpha else 1.0f
    val relativeTime: String = RelativeTimeFormatter.format(
        publishedAtIso = model.publishedAtIso,
        isDateEstimated = model.isDateEstimated,
        clock = clock,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(dimens.cornerSmall))
            .background(feedman.cardBackground)
            .clickable { onOpen(model.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ソース行: favicon + フィード名
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Favicon(
                faviconValue = model.faviconValue,
                feedTitle = model.feedTitle,
                size = dimens.faviconExtraSmall,
            )
            Text(
                text = model.feedTitle,
                color = feedman.mutedFg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // タイトル行（Issue #33 Req 1.3 — 最大 3 行に制限）
        Text(
            text = model.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = if (model.isRead) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // 概要行（Issue #33 Req 1.4 / 1.5）
        // 空文字列のときは Composable を生成しない（レイアウト領域を確保しない / Req 1.5）。
        if (model.summary.isNotEmpty()) {
            Text(
                text = model.summary,
                color = feedman.mutedFg,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // メタ行: 相対日時 / はてブ / スター / 外部リンクアイコン
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = relativeTime,
                color = feedman.mutedFg,
                fontSize = 11.sp,
            )
            HatebuBadge(
                hatebuCount = model.hatebuCount,
                hatebuFetchedAt = model.hatebuFetchedAt,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Issue #33 Req 1.8 / 4.1 — 外部リンクアイコン（コールバック指定時のみ描画）。
            if (onOpenLink != null) {
                OpenLinkIconButton(
                    id = model.id,
                    onOpenLink = onOpenLink,
                )
            }
            StarToggle(
                isStarred = model.isStarred,
                onToggle = { newState -> onStarToggle(model.id, newState) },
            )
        }
    }
}

/**
 * 外部リンクアイコンボタン（Issue #33 Req 1.8 / 3.3 / 4.1 / 4.2 / NFR 3.1 / NFR 3.2）。
 *
 * - 独自の click 領域として `onClick` をカード本体タップへ伝播させない（[IconButton] が
 *   click event を消費する → カード本体の `clickable { onOpen(id) }` には届かない）。
 * - 最小タップ標的 44dp を [Modifier.sizeIn] で確保する（NFR 3.2）。
 * - `contentDescription` は「元記事をブラウザで開く」相当の文言を [stringResource] から取得する
 *   （NFR 3.1）。
 */
@Composable
private fun OpenLinkIconButton(
    id: String,
    onOpenLink: (id: String) -> Unit,
) {
    val minTapTarget = MaterialTheme.feedmanDimens.minTapTarget
    val iconSize = MaterialTheme.feedmanDimens.iconMedium
    val mutedColor = MaterialTheme.feedmanColors.mutedFg
    val description = stringResource(id = R.string.article_card_open_link_description)
    IconButton(
        onClick = { onOpenLink(id) },
        modifier = Modifier
            .sizeIn(minWidth = minTapTarget, minHeight = minTapTarget)
            .testTag(ARTICLE_CARD_OPEN_LINK_TEST_TAG),
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = description,
                tint = mutedColor,
            )
        }
    }
}
