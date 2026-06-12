package com.feedman.android.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens

/**
 * テスト用 [testTag]。Composable UI テストで対象を取得する際に使う。
 */
const val STAR_TOGGLE_TEST_TAG: String = "core.ui.StarToggle"

/**
 * 共有スタートグル部品（Issue #27 / Req 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8 / NFR 2.1, 2.2）。
 *
 * 視覚仕様（プロト `design/mobile/fm-ui.jsx` の `FMStar` 準拠）:
 * - `isStarred=true` → [Icons.Filled.Star]（filled）+ [feedmanColors] の star 色
 * - `isStarred=false` → [Icons.Outlined.StarBorder]（outline）+ mutedFg 色
 *
 * インタラクション:
 * - タップで [onToggle] を呼び出す。引数は **次の状態**（現在 false → true、現在 true → false）。
 *   親カードの「記事詳細を開く」アクションへのタップ伝播は [IconButton] が独自の click 領域として
 *   消費するため発生しない（Req 1.7）。
 * - タップ可能領域は [feedmanDimens] の `minTapTarget`（44dp）以上を確保（Req 1.8 / NFR 2.2）。
 *
 * アクセシビリティ（NFR 2.1 / Issue #53 Req 1.3 / 2.1〜2.3）:
 * - `contentDescription` は次に発生するアクションを示す:
 *   - `isStarred=true` → "スターを解除"（R.string.article_meta_star_remove）
 *   - `isStarred=false` → "スターを付ける"（R.string.article_meta_star_add）
 * - 加えて [Role.Switch] と [ToggleableState] を semantics で公開し、TalkBack が
 *   「on / off」として現在状態をアナウンスできるようにする（Issue #53 Req 1.3 / 2.1〜2.3）。
 * - [stateDescription] に "付与済み" / "未付与" を明示することで、トグル操作後の
 *   状態変化を音声で確認可能にする（Issue #53 Req 2.1）。
 * - 状態文字列は `strings.xml` に集約しハードコードしない。
 *
 * @param isStarred 現在のスター状態
 * @param onToggle タップ時に呼び出されるコールバック。引数は新しい状態（!isStarred）
 * @param modifier 呼び出し側からの追加 [Modifier]
 */
@Composable
fun StarToggle(
    isStarred: Boolean,
    onToggle: (newState: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starColor = MaterialTheme.feedmanColors.star
    val mutedColor = MaterialTheme.feedmanColors.mutedFg
    val minTapTarget = MaterialTheme.feedmanDimens.minTapTarget
    val iconSize = MaterialTheme.feedmanDimens.iconMedium

    val contentDescription = stringResource(
        if (isStarred) R.string.article_meta_star_remove else R.string.article_meta_star_add,
    )
    val stateDescriptionText = stringResource(
        if (isStarred) R.string.article_meta_star_state_on else R.string.article_meta_star_state_off,
    )
    val toggleable = if (isStarred) ToggleableState.On else ToggleableState.Off

    IconButton(
        onClick = { onToggle(!isStarred) },
        modifier = modifier
            .sizeIn(minWidth = minTapTarget, minHeight = minTapTarget)
            .testTag(STAR_TOGGLE_TEST_TAG)
            // Issue #53 Req 1.3 / 2.1〜2.3: toggle role と on/off 状態をスクリーンリーダーへ公開する。
            // IconButton 自体が Role.Button を付与するため、role を Switch で上書きする。
            .semantics {
                role = Role.Switch
                toggleableState = toggleable
                stateDescription = stateDescriptionText
            },
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = contentDescription,
                tint = if (isStarred) starColor else mutedColor,
            )
        }
    }
}
