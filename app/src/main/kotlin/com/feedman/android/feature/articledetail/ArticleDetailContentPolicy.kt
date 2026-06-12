package com.feedman.android.feature.articledetail

/**
 * 記事詳細シートの本文プレビュー領域に流し込む文字列の選択ロジック（Issue #36 / Req 2.5, 2.6）。
 *
 * UI 層（Composable）から切り離した純粋関数として提供し、JVM 単体テストで境界を検証する
 * （`Html.fromHtml` 等の Android 依存を持ち込まない）。
 *
 * ## 選択順
 *
 * 1. `content`（sanitized HTML）が空でなければそれを返す（Req 2.5）。
 * 2. `content` が空で `summary` が空でなければ `summary` を返す（fallback / 設計判断）。
 * 3. 両方とも空であれば `null` を返す（Req 2.6 — 「本文のプレビューはありません」の空状態を
 *    UI 側で描画する）。
 *
 * ## 空判定
 *
 * `String.isBlank()` を採用し、半角・全角スペースのみの文字列も空とみなす。
 * `content` は SPEC §4.2 で `string` 必須のため null は来ない前提だが、保険的に
 * `String?` を受け、`null` も空として扱う。
 *
 * ## 設計判断の根拠
 *
 * 要件 Open Questions の 1 つに「`content` が空のケースの扱い」が挙げられており、本 Issue では
 * Req 2.6 の保険的記述に沿いつつ、空時は `summary` を fallback として表示する設計判断とした
 * （詳細は impl-notes.md に記録）。
 */
internal object ArticleDetailContentPolicy {

    /**
     * 表示するプレビュー本文を選択する。
     *
     * @param content `ItemDetail.content`（sanitized HTML）。null / blank は空扱い
     * @param summary `ItemDetail.summary`。null / blank は空扱い
     * @return 表示するプレビュー本文。両方空のときは `null`（プレースホルダ表示を UI 側で行う）
     */
    fun resolvePreview(content: String?, summary: String?): String? {
        if (!content.isNullOrBlank()) return content
        if (!summary.isNullOrBlank()) return summary
        return null
    }

    /**
     * `resolvePreview` の結果が `null` のとき、UI 側で「本文プレビューなし」のプレースホルダを
     * 描画し、「続きを読む」ボタンを非表示にするかを判定する（Req 2.6）。
     *
     * @return 「続きを読む」ボタンを表示する場合 true
     */
    fun showExpandToggle(preview: String?): Boolean = preview != null && preview.isNotBlank()
}
