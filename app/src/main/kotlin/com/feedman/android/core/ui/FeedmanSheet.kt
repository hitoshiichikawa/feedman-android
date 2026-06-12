package com.feedman.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors

/**
 * Feedman 共通ボトムシート枠（Issue #28 / Req 6.1〜6.5 / NFR 1.1, 1.2, 1.3）。
 *
 * `design/mobile/fm-sheets.jsx` の `FMSheet` を Compose 上で再現する共通枠:
 *
 * - **Req 6.1**: 上端中央にドラッグハンドル、上端両角の丸み、下端のセーフエリア確保
 *   - 角丸: SPEC §8 の上限（16dp）に合わせる。プロトは 22dp 相当だが、SPEC §8 の
 *     「10–16px」設計トークンを優先（NFR 1.1）
 *   - 下端: [WindowInsets.Companion.navigationBars] を contentWindowInsets として渡し
 *     セーフエリアを確保
 * - **Req 6.2**: `content` を slot として受け取り、内側に任意 Composable を差し込める
 * - **Req 6.3**: ドラッグ下げ / スクリム外側タップで [onDismissRequest] を呼ぶ
 *   （Material 3 [ModalBottomSheet] が標準で提供する挙動）
 * - **Req 6.4**: アクセシビリティラベル [label] を [Modifier.semantics] の `paneTitle`
 *   に設定し、スクリーンリーダーから参照可能にする
 * - **Req 6.5**: 視覚仕様（角丸・ハンドル寸法・スクリム濃度・余白）はプロト `FMSheet` を
 *   基準としつつ SPEC §8 のデザイントークンに整合
 *
 * @param onDismissRequest シートを閉じるリクエスト（ドラッグ下げ・スクリムタップ・
 *        ハードウェアバック）が発生したときのコールバック（Req 6.3）
 * @param label アクセシビリティラベル。スクリーンリーダーで読み上げられる
 *        ペインのタイトル（Req 6.4）
 * @param modifier 外側に適用する [Modifier]
 * @param sheetState [ModalBottomSheet] の状態。既定は [rememberModalBottomSheetState]
 *        （`skipPartiallyExpanded = false`）
 * @param content シート内に描画する Composable slot（Req 6.2）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedmanSheet(
    onDismissRequest: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    FeedmanSheetLabel.validate(label)
    val feedman = MaterialTheme.feedmanColors
    val handleDescription = stringResource(id = R.string.sheet_drag_handle_description)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.semantics { paneTitle = label },
        sheetState = sheetState,
        // SPEC §8 の角丸上限 16dp に整合（NFR 1.1）。
        // 上端のみ角を丸める（下端は画面下にクリップされる）。
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = feedman.scrim,
        // Material 3 既定のドラッグハンドルを差し替え、`FMSheet` の
        // 40x5 角丸 999 を Compose で再現する。
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(feedman.borderStrong)
                    .size(width = 40.dp, height = 5.dp)
                    .semantics { contentDescription = handleDescription },
            )
        },
        // Req 6.1 (下端セーフエリア): navigation bars インセットを反映
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        // slot をフル幅 Column にラップ（呼び出し側は内側の padding を自由に設計可能 / Req 6.2）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 0.dp)),
            horizontalAlignment = Alignment.Start,
        ) {
            content()
        }
    }
}

/**
 * [FeedmanSheet] のアクセシビリティラベル検証（Issue #28 / Req 6.4 の境界 / NFR 3.1）。
 *
 * Req 6.4 は「アクセシビリティ用のラベル文字列を呼び出し側から受け取り、スクリーン
 * リーダーから参照可能にする」ことを要求するため、空白文字列だけのラベルでは
 * スクリーンリーダーが認識できず要求を満たさない。`require` で早期検出する。
 *
 * UI 描画と分離した純粋関数として公開し、JVM 単体テスト対象とする（NFR 3.1）。
 */
internal object FeedmanSheetLabel {
    /**
     * @throws IllegalArgumentException [label] が空白のみ（Req 6.4 の境界）
     */
    fun validate(label: String) {
        require(label.isNotBlank()) {
            "FeedmanSheet label must not be blank (Req 6.4: screen reader pane title)"
        }
    }
}
