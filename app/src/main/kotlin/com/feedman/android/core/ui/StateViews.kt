package com.feedman.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens

/**
 * 共通状態表示プリミティブ（Issue #28 / Req 1, 2, 3, 4 / NFR 1, 2, 3）。
 *
 * 一覧画面（横断タイムライン / フィード別 / スター / 検索）と各種ボトムシートで
 * 共通利用する初回ローディング・追加ローディング・空状態・エラー・終端表示を
 * 単一のモジュール（[com.feedman.android.core.ui]）に集約する（Req 7.1）。
 *
 * 視覚仕様（NFR 1.1, 1.3）:
 * - SPEC §8 のデザイントークン（`feedmanColors` / `feedmanDimens`）を参照
 * - 空状態は `design/mobile/fm-ui.jsx` の `FMEmpty`（アイコン → 主題 → 補助、56dp 角丸 16dp
 *   ベースのアイコン背景、補助テキスト最大幅 240dp）を踏襲（Req 2.4）
 *
 * 状態判定（Req 4.2 / NFR 3.1）:
 * - 一覧フッターの 4 状態（追加読込中 / 追加エラー / 終端 / なし）は
 *   [ListFooterState] と [resolveListFooterState]（JVM 単体テスト対象）で決定する。
 */

// ────────────────────────────────────────────────────────────────────────────
// Req 1: ローディング
// ────────────────────────────────────────────────────────────────────────────

/**
 * 初回ローディング（Req 1.1, 1.3）。
 *
 * コンテンツ領域全体を占有し、中央に [CircularProgressIndicator] を表示する。
 * 一覧の初回読み込みが未完了の状態で用いる。
 *
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun LoadingFullScreen(
    modifier: Modifier = Modifier,
) {
    val description = stringResource(id = R.string.state_loading_description)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 追加ローディング（Req 1.2, 1.3）。
 *
 * Paging 3 の `append` 進行中に LazyColumn の末尾アイテムとして配置するフッター。
 * 縦幅は 56dp、インジケータ単独で水平中央配置する。
 *
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun LoadingFooter(
    modifier: Modifier = Modifier,
) {
    val description = stringResource(id = R.string.state_loading_description)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Req 2: 空状態
// ────────────────────────────────────────────────────────────────────────────

/**
 * 空状態表示（Req 2.1, 2.2, 2.3, 2.4）。
 *
 * `design/mobile/fm-ui.jsx` の `FMEmpty` を踏襲: 56dp 角丸 16dp の `muted` 背景に
 * アイコンを配置 → 主題テキスト（15sp / SemiBold） → 補助テキスト（13sp / mutedFg /
 * 最大幅 240dp） の縦並び（gap 12dp）、コンテナ周囲 padding 40dp、中央寄せ。
 *
 * @param icon 表示するアイコン
 * @param title 主題テキスト
 * @param subtitle 補助テキスト（null のとき非表示 / Req 2.3）
 * @param iconContentDescription アイコンの contentDescription（null 可）
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconContentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val feedman = MaterialTheme.feedmanColors
    val dimens = MaterialTheme.feedmanDimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // アイコン背景（56dp 角丸 16dp、muted ≒ surfaceVariant）
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = feedman.mutedFg,
                modifier = Modifier.size(26.dp),
            )
        }
        // gap 12dp
        Box(modifier = Modifier.size(12.dp))
        // 主題
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            // gap 4dp
            Box(modifier = Modifier.size(4.dp))
            // 補助
            Text(
                text = subtitle,
                color = feedman.mutedFg,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
    }
}

/**
 * `EmptyState` の既定アイコン（`design/mobile/fm-screens.jsx` の `FMEmpty` 呼び出し例で
 * 横断タイムライン・フィード別では `inbox` が用いられている）。
 *
 * 呼び出し側で別のアイコンを渡したい場合は [EmptyState] の `icon` 引数で差し替える
 * （Req 2.2）。
 */
val DefaultEmptyStateIcon: ImageVector = Icons.Outlined.Inbox

// ────────────────────────────────────────────────────────────────────────────
// Req 3: エラー
// ────────────────────────────────────────────────────────────────────────────

/**
 * 初回エラー表示（Req 3.1, 3.2, 3.3）。
 *
 * コンテンツ領域全体を占有し、エラーメッセージと再試行ボタンを縦並びで中央配置する。
 *
 * @param message 表示するエラーメッセージ。null のとき [R.string.state_error_default_message]
 *        を使う（Req 3.3 — 呼び出し側からの差し替え可能）
 * @param onRetry 再試行ボタンタップ時のコールバック（Req 3.2）
 * @param retryLabel 再試行ボタンのラベル。既定は [R.string.state_error_retry]
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun ErrorFullScreen(
    onRetry: () -> Unit,
    message: String? = null,
    retryLabel: String = stringResource(id = R.string.state_error_retry),
    modifier: Modifier = Modifier,
) {
    val feedman = MaterialTheme.feedmanColors
    val resolvedMessage = message ?: stringResource(id = R.string.state_error_default_message)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = resolvedMessage,
            color = feedman.mutedFg,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Box(modifier = Modifier.size(12.dp))
        TextButton(
            onClick = onRetry,
            // NFR 1.2: 最小タップ標的 44dp を満たす
            modifier = Modifier.sizeIn(minWidth = 88.dp, minHeight = 44.dp),
        ) {
            Text(
                text = retryLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 追加エラーフッター（Req 3.4）。
 *
 * Paging 3 の `append` がエラー終了したときに LazyColumn 末尾アイテムとして配置する。
 * メッセージと再試行ボタンを横並びで中央配置する。
 *
 * @param onRetry 再試行ボタンタップ時のコールバック
 * @param message エラーメッセージ。null のとき既定文言
 * @param retryLabel 再試行ボタンラベル
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun ErrorFooter(
    onRetry: () -> Unit,
    message: String? = null,
    retryLabel: String = stringResource(id = R.string.state_error_retry),
    modifier: Modifier = Modifier,
) {
    val feedman = MaterialTheme.feedmanColors
    val resolvedMessage = message ?: stringResource(id = R.string.state_error_default_message)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = resolvedMessage,
            color = feedman.mutedFg,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 44.dp),
        ) {
            Text(
                text = retryLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Req 4: 終端フッター
// ────────────────────────────────────────────────────────────────────────────

/**
 * 終端フッター（Req 4.1, 4.2, 4.3 / NFR 2.1）。
 *
 * 無限スクロールで終端に到達したことを示す。文言「最後まで読みました」は
 * [R.string.state_end_of_list] に集約され、アプリ全体で 1 つに統一される（Req 4.3）。
 *
 * 追加ローディング・追加エラーとの排他性は呼び出し側で [resolveListFooterState] により
 * 担保される（Req 4.2）。
 *
 * @param modifier 外側に適用する [Modifier]
 */
@Composable
fun EndOfListFooter(
    modifier: Modifier = Modifier,
) {
    val feedman = MaterialTheme.feedmanColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.state_end_of_list),
            color = feedman.mutedFg,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
