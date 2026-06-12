package com.feedman.android.feature.starred

import com.feedman.android.core.model.StarredItemSummary
import com.feedman.android.core.ui.ArticleCardModel

/**
 * スター一覧で使う [ArticleCardModel] への変換ロジック（Issue #46 / Req 1.4 / NFR 2.3）。
 *
 * Composable から純粋ロジックを切り離し、JVM 単体テストで網羅できるようにする
 * （`TimelineCardModelMapper` / `feature/feed.toCardModel` と同じ流儀）。
 *
 * ## 設計上の補足
 *
 * - [StarredItemSummary] は `feed_title` を保持しており、これをカードのソース表示
 *   （`ArticleCardModel.feedTitle`）に流用する（Req 1.4）。横断タイムラインと違い
 *   favicon URL（`feed_favicon_url`）を API では返さないため、`faviconValue = null` を
 *   渡し、`Favicon` 部品側でレターアバターにフォールバックさせる。
 * - `hatebu_fetched_at` は API から返るためそのまま転写する（`HatebuBadge` が `null` 時に
 *   「−」表示するため、値があれば数値が表示される）。
 */
internal object StarredCardModelMapper {

    /**
     * [StarredItemSummary] を [ArticleCardModel] へ変換する。
     *
     * @param item starred API レスポンス 1 件分
     * @return カード描画用の中立モデル
     */
    fun toCardModel(item: StarredItemSummary): ArticleCardModel = ArticleCardModel(
        id = item.id,
        title = item.title,
        feedTitle = item.feedTitle, // Req 1.4: feed_title をソース表示に用いる
        faviconValue = null, // API レスポンスに favicon URL が含まれないためレターアバター fallback
        publishedAtIso = item.publishedAt,
        isDateEstimated = item.isDateEstimated,
        isRead = item.isRead,
        isStarred = item.isStarred,
        hatebuCount = item.hatebuCount,
        hatebuFetchedAt = item.hatebuFetchedAt,
        summary = item.summary,
        link = item.link,
    )
}
