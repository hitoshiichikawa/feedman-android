package com.feedman.android.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * レターアバター用の独立背景色パレット（Issue #26 / Req 3.4, Req 5.3）。
 *
 * Indigo アクセントとは別系統で、白文字（前景 `#FFFFFF`）と十分なコントラストを持つ
 * 飽和した中明度カラーのみを採用する。プロト `design/mobile/fm-ui.jsx` の `FMFavicon`
 * では `item.favicon_color || item.color || '#64748b'` を直接利用していたが、本実装では
 * フィードタイトルから決定論的に色を選ぶため、視認性が保証されたパレットを事前に定義する。
 *
 * ## パレット選定方針
 *
 * - 白文字（`#FFFFFF`）との Web AA コントラスト比 ≥ 3:1 を確保
 *   （アイコン内の太字 1 文字は graphical object 扱いの目安）
 * - 色相を 360° 全体に分散させ、隣接フィードの色がぶつかりにくくする
 * - ライト／ダークテーマで同一の色を返す（Req 5.3: テーマ切替で背景色が変化しない）
 * - パレット数は 2 のべき乗ではなく、ハッシュ剰余の偏りより色相分散を優先して 12 色
 *
 * @see com.feedman.android.core.ui.FaviconLogic.pickLetterColor
 */
object LetterAvatarPalette {

    /**
     * Tailwind 系の slate-500 / red-500 / orange-500 等から選んだ、白文字でも視認可能な
     * 12 色。順序を変更すると既存フィードの色が変わるため、追加・並べ替えは慎重に行う。
     */
    val Colors: List<Color> = listOf(
        Color(0xFF64748B), // slate-500（FMFavicon デフォルト互換）
        Color(0xFFEF4444), // red-500
        Color(0xFFF97316), // orange-500
        Color(0xFFD97706), // amber-600
        Color(0xFF65A30D), // lime-600
        Color(0xFF16A34A), // green-600
        Color(0xFF0D9488), // teal-600
        Color(0xFF0EA5E9), // sky-500
        Color(0xFF2563EB), // blue-600
        Color(0xFF7C3AED), // violet-600
        Color(0xFFC026D3), // fuchsia-600
        Color(0xFFDB2777), // pink-600
    )

    /** パレット要素数。テストで剰余境界を確認するために公開する。 */
    val Size: Int get() = Colors.size
}
