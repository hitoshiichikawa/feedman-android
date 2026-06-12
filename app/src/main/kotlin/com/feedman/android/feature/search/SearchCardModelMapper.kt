package com.feedman.android.feature.search

import com.feedman.android.core.model.ItemSearchHit
import com.feedman.android.core.ui.ArticleCardModel

/**
 * 横断検索結果カード用の [ArticleCardModel] への変換ロジック（Issue #47 / Req 4 / NFR 2.2）。
 *
 * Composable から純粋ロジックを切り離し、JVM 単体テストで [ItemSearchHit] の
 * nullable フィールド（`published_at` / `favicon_url`）の取り扱いを直接検証できるようにする
 * （[StarredCardModelMapper] / `feature/feed.toCardModel` と同じ流儀）。
 *
 * ## 設計上の判断
 *
 * - **`published_at` が null**: [ArticleCardModel.publishedAtIso] は **非 null String** のため、
 *   null を `""`（空文字列）に正規化して [ArticleCardModel] に詰める。Req 4.6 で求められる
 *   「日時が不明である旨を示す代替表現」は描画側（カード / `RelativeTimeFormatter`）の責務だが、
 *   `RelativeTimeFormatter` は空文字列に対して既定の fallback 文言を返す挙動になっており、
 *   mapper はそのインターフェース契約に乗せる
 * - **`favicon_url` が null**: そのまま `faviconValue = null` を渡し、
 *   [com.feedman.android.core.ui.Favicon] がレターアバターに自動フォールバックする（Req 4.4）
 * - **`hatebu_fetched_at` を持たない**: [ItemSearchHit] は当該フィールドを持たないため、
 *   `hatebuFetchedAt = null` を渡す（Req 4.7。`HatebuBadge` が null 時に「−」表示を出すが、
 *   検索結果カードでは `hatebu_count` だけを表示する仕様で十分）
 * - **`feed_title` をソース表示に使う**（Req 4.2）
 */
internal object SearchCardModelMapper {

    /**
     * 検索ヒットが [publishedAtIso] が null の場合に [ArticleCardModel.publishedAtIso] に
     * 詰める正規化値（Req 4.6 / NFR 2.2）。
     *
     * 描画側（`RelativeTimeFormatter`）が空文字列を「日時不明」の fallback 文言で表示する
     * 契約に乗せるため、null を空文字列に正規化する。
     */
    internal const val UNKNOWN_PUBLISHED_AT: String = ""

    /**
     * [ItemSearchHit] を [ArticleCardModel] へ変換する。
     *
     * @param hit 検索 API レスポンス 1 件分
     * @return カード描画用の中立モデル
     */
    fun toCardModel(hit: ItemSearchHit): ArticleCardModel = ArticleCardModel(
        id = hit.id,
        title = hit.title,
        feedTitle = hit.feedTitle, // Req 4.2: feed_title をソース表示に用いる
        // Req 4.3 / 4.4: favicon_url が data URL なら Favicon が復号、null ならレターアバター
        faviconValue = hit.faviconUrl,
        // Req 4.5 / 4.6: published_at が null のとき空文字列に正規化し、描画側 fallback に委ねる
        publishedAtIso = hit.publishedAt ?: UNKNOWN_PUBLISHED_AT,
        isDateEstimated = hit.isDateEstimated,
        isRead = hit.isRead,
        isStarred = hit.isStarred,
        hatebuCount = hit.hatebuCount,
        // Req 4.7: ItemSearchHit は hatebu_fetched_at を持たないため null
        hatebuFetchedAt = null,
        summary = hit.summary,
        link = hit.link,
    )
}
