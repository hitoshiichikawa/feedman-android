package com.feedman.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.feedman.android.core.designsystem.FeedmanTheme
import com.feedman.android.core.designsystem.feedmanDimens

/**
 * フィードの favicon を描画する共通 Composable（Issue #26）。
 *
 * 描画分岐（Req 1.1 / Req 2.1, 2.2, 2.3 / NFR 1.3）:
 * 1. `faviconValue` が data URL（`data:<mime>;base64,...`）→ Coil の [AsyncImage] で復号して表示
 * 2. data URL の復号に失敗 → onError コールバックで `Error` を検知し、レターアバターへフォールバック
 * 3. `faviconValue` が `null` / 空 / data URL でない → 即座にレターアバターへフォールバック
 * 4. `feedTitle` が `null` / 空 → プレースホルダ `?` のレターアバター
 *
 * 視覚仕様（Req 5.1, 5.2, 5.3）:
 * - 正方形 + 角丸（[com.feedman.android.core.designsystem.FeedmanDimens.faviconCornerRadius]）
 * - レターアバター: 背景 [FaviconLogic.pickLetterColor]、前景 `#FFFFFF`、太字、中央配置
 * - レターアバター用パレットはアクセント Indigo と独立し、テーマ切替で背景色が変化しない
 *
 * @param faviconValue API レスポンスの `favicon_url` / `feed_favicon_url` 相当。
 *        data URL（`data:<mime>;base64,...`）か `null` を想定（`design/SPEC.md` §4.4）
 * @param feedTitle レターアバター fallback 用のフィードタイトル
 * @param size アイコン辺長。`FeedmanDimens.faviconSmall` 等のサイズトークンを渡す（Req 4.1, 4.2, 4.4）
 * @param modifier 呼び出し側からの追加 [Modifier]（クリック・余白などを後段で付与可能）
 * @param contentDescription a11y 説明。`null` のときは装飾扱い（a11y ラベル文言の最終確定は後続 Issue）
 */
@Composable
fun Favicon(
    faviconValue: String?,
    feedTitle: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val cornerRadius = MaterialTheme.feedmanDimens.faviconCornerRadius
    val shape = RoundedCornerShape(cornerRadius)
    // NFR 2.2: faviconValue が変わらない限り isDataUrl は再計算しない。
    val isDataUrl = remember(faviconValue) { FaviconLogic.isDataUrl(faviconValue) }

    if (isDataUrl) {
        // Coil の復号失敗を検知してレターアバターへ切替（Req 2.2）。
        var hasError by remember(faviconValue) { mutableStateOf(false) }
        if (hasError) {
            LetterAvatar(
                feedTitle = feedTitle,
                size = size,
                shape = shape,
                modifier = modifier,
                contentDescription = contentDescription,
            )
        } else {
            // NFR 2.1: Coil の AsyncImage は内部で非同期にデコードしフレーム描画をブロックしない。
            // NFR 2.2 / Req 1.2: 同一 model（faviconValue 文字列）に対して Coil は memory cache を効かせ、
            //                    再コンポジション時の再デコード・ネットワーク再取得を抑制する。
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconValue)
                    .crossfade(false)
                    .build(),
                contentDescription = contentDescription,
                modifier = modifier
                    .size(size)
                    .clip(shape),
                contentScale = ContentScale.Crop, // Req 1.3: アスペクト比を歪めず正方形に収める
                onError = { hasError = true },
            )
        }
    } else {
        LetterAvatar(
            feedTitle = feedTitle,
            size = size,
            shape = shape,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    }
}

/**
 * レターアバター fallback の描画（Req 2.1, 2.3, 2.4 / Req 3.1, 3.2, 3.3, 3.4 / Req 5.1, 5.2）。
 *
 * 文字サイズはアイコン辺長 × [LETTER_SIZE_RATIO] を `sp` 換算する。プロト `FMFavicon` の
 * `fontSize: size * 0.46` を踏襲し、サイズが変わっても文字が枠から食み出さないようにする（Req 4.3）。
 */
@Composable
private fun LetterAvatar(
    feedTitle: String?,
    size: Dp,
    shape: RoundedCornerShape,
    modifier: Modifier,
    contentDescription: String?,
) {
    // NFR 2.2: feedTitle が変わらない限り色・文字を再計算しない。
    val backgroundColor: Color = remember(feedTitle) { FaviconLogic.pickLetterColor(feedTitle) }
    val letter: String = remember(feedTitle) { FaviconLogic.extractLetter(feedTitle) }
    val fontSize: TextUnit = (size.value * LETTER_SIZE_RATIO).sp

    val baseModifier = modifier
        .size(size)
        .clip(shape)
        .background(backgroundColor)
    val finalModifier = if (contentDescription != null) {
        baseModifier.semantics { this.contentDescription = contentDescription }
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = LETTER_FOREGROUND,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * レターアバター内文字サイズ比率（プロト `FMFavicon` の `fontSize: size * 0.46` 準拠）。
 * Req 4.3 — 文字サイズはアイコン辺長に比例し、サイズが変わっても枠内に収まる。
 */
internal const val LETTER_SIZE_RATIO: Float = 0.46f

/**
 * レターアバターの前景色（Req 5.2 — 白文字固定）。
 * パレットの全色が白文字で AA コントラストを満たすため、ライト／ダーク共通で同値。
 */
private val LETTER_FOREGROUND: Color = Color.White

// ─────────────────────────────────────────────────────────────────────────────
//  Previews — Issue #26 視覚確認用（Req 5.1, 5.2 / Req 4.1 サイズバリアント）
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Favicon - LetterAvatar fallback", showBackground = true)
@Composable
private fun FaviconLetterPreview() {
    FeedmanTheme {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Favicon(
                faviconValue = null,
                feedTitle = "Hacker News",
                size = 32.dp,
            )
        }
    }
}

@Preview(name = "Favicon - LetterAvatar placeholder", showBackground = true)
@Composable
private fun FaviconPlaceholderPreview() {
    FeedmanTheme {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Favicon(
                faviconValue = null,
                feedTitle = null,
                size = 32.dp,
            )
        }
    }
}
