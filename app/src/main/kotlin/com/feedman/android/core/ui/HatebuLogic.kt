package com.feedman.android.core.ui

/**
 * はてブ数バッジの表示判定ロジック（Issue #27 / Req 2.1, 2.2, 2.3, 2.4, 5.5）。
 *
 * UI 描画から切り離した純粋関数として提供し、JVM 単体テストで検証する。
 * プロト `design/mobile/fm-ui.jsx` の `FMHatebu`（L75-L89）を正本とし、以下を踏襲する:
 *
 * ```js
 * const val = item.hatebu_fetched_at == null ? '−' : item.hatebu_count
 * const hot = item.hatebu_count >= 100
 * ```
 *
 * - `hatebu_fetched_at == null` → "−" 表示（取得未実施。検索結果 `ItemSearchHit` のように
 *   レスポンスにフィールドが含まれない場合を含む。Req 2.2 / Req 5.5）
 * - `hatebu_count >= 100` → hot バリアント（アクセント色 + 太字 + "users" サフィックス。Req 2.3）
 * - それ以外 → 通常バリアント（ミュート色 + 通常字幅 + 数値のみ。Req 2.4）
 */
object HatebuLogic {

    /** `hatebu_count >= 100` で hot 表示となる閾値（プロト準拠）。 */
    const val HOT_THRESHOLD: Int = 100

    /** 取得未実施時のラベル（U+2212 MINUS SIGN）。Req 2.2。 */
    const val UNAVAILABLE_LABEL: String = "−"

    /**
     * 表示判定結果。
     *
     * UI 層（Composable）は本 sealed class を `when` で分岐し、`Numeric` の `isHot` に応じて
     * 色・字幅・"users" サフィックスを切り替える。
     */
    sealed class Display {
        /**
         * 数値表示（Req 2.1, 2.3, 2.4）。
         *
         * @property count 表示する `hatebu_count`
         * @property isHot `count >= HOT_THRESHOLD` のときに `true`。アクセント色 + 太字 +
         *           "users" サフィックスを付加する UI 切り替えに使う
         */
        data class Numeric(val count: Int, val isHot: Boolean) : Display()

        /** 取得未実施（"−" 表示）。Req 2.2, 5.5。 */
        data object Unavailable : Display()
    }

    /**
     * 表示判定を行う（Req 2.1〜2.4 / Req 5.5）。
     *
     * @param hatebuCount API レスポンスの `hatebu_count`
     * @param hatebuFetchedAt API レスポンスの `hatebu_fetched_at`（取得時刻）。`null` は
     *        取得未実施として扱う（`ItemSearchHit` のようにフィールドが含まれない場合も
     *        呼び出し側で `null` に正規化して渡す）
     * @return [Display.Numeric] または [Display.Unavailable]
     */
    fun compute(hatebuCount: Int, hatebuFetchedAt: String?): Display {
        if (hatebuFetchedAt == null) return Display.Unavailable
        return Display.Numeric(count = hatebuCount, isHot = hatebuCount >= HOT_THRESHOLD)
    }
}
