package com.feedman.android.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors

/**
 * 共有はてブ数バッジ（Issue #27 / Req 2.1, 2.2, 2.3, 2.4, 2.5, 5.5）。
 *
 * 表示判定は [HatebuLogic.compute] に委譲し、本 Composable は配色・字幅・"users" 付与
 * のみを担当する（UI とロジックの分離）。
 *
 * 視覚仕様（プロト `design/mobile/fm-ui.jsx` の `FMHatebu` 準拠）:
 * - 左端に RSS 風アイコン（[Icons.Filled.RssFeed]）（Req 2.5）
 * - `hatebu_fetched_at` null → "−"（mutedFg, 通常字幅）（Req 2.2 / 5.5）
 * - `hatebu_count >= 100` → アクセント色 + 太字 + "users" サフィックス（Req 2.3）
 * - `hatebu_count < 100` → mutedFg + 通常字幅、数値のみ（Req 2.4）
 *
 * @param hatebuCount API レスポンスの `hatebu_count`
 * @param hatebuFetchedAt API レスポンスの `hatebu_fetched_at`（null は取得未実施）
 * @param modifier 呼び出し側からの追加 [Modifier]
 */
@Composable
fun HatebuBadge(
    hatebuCount: Int,
    hatebuFetchedAt: String?,
    modifier: Modifier = Modifier,
) {
    val display = HatebuLogic.compute(hatebuCount = hatebuCount, hatebuFetchedAt = hatebuFetchedAt)
    val accentColor: Color = MaterialTheme.colorScheme.primary
    val mutedColor: Color = MaterialTheme.feedmanColors.mutedFg
    val isHot = display is HatebuLogic.Display.Numeric && display.isHot
    val contentColor: Color = if (isHot) accentColor else mutedColor

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.RssFeed,
            // Req 2.5 — 装飾的だが a11y のためラベルを与える
            contentDescription = stringResource(R.string.article_meta_hatebu_icon_description),
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        when (display) {
            HatebuLogic.Display.Unavailable -> {
                Text(
                    text = HatebuLogic.UNAVAILABLE_LABEL,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
            is HatebuLogic.Display.Numeric -> {
                Text(
                    text = display.count.toString(),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (display.isHot) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (display.isHot) {
                    Text(
                        text = stringResource(R.string.article_meta_hatebu_users_suffix),
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
