package com.feedman.android.feature.timeline

import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.model.MockTimelineItem
import com.feedman.android.core.ui.ArticleCardModel

/**
 * 横断タイムラインで使う [ArticleCardModel] への変換ロジック（Issue #33 / Req 1.1〜1.7, 2.1, 2.2）。
 *
 * Composable から純粋ロジックを切り離し、JVM 単体テストで網羅できるようにする。
 *
 * ## 設計上の補足
 *
 * - [CrossFeedItem] には `hatebu_fetched_at` フィールドが含まれない（SPEC §4.2:
 *   `CrossFeedItem` は `hatebu_count` のみ持つ）。一方、[com.feedman.android.core.ui.HatebuBadge]
 *   は `hatebu_fetched_at == null` のときに「−」を表示する仕様（Issue #27 Req 2.2 /
 *   `HatebuLogic`）であるため、横断タイムラインで数値表示を維持するには非 null 値を
 *   渡す必要がある。
 *
 *   本マッパでは `publishedAt`（必ず非 null）を [ArticleCardModel.hatebuFetchedAt] に
 *   流用することで「取得済み（数値表示）」相当の挙動を取らせる。これは表示根拠としては
 *   暫定であり、サーバー側で `cross-feed` のレスポンスに `hatebu_fetched_at` を追加した
 *   段階で純粋な転写に切り替わる（要件には影響しない）。
 *
 * - [toMockCardModel] は mockMode 用に [MockTimelineItem] を [ArticleCardModel] に
 *   変換する。`MockTimelineItem.publishedAt` は事前整形された相対表現文字列（"10 分前"）で
 *   ISO-8601 ではないため、別途 [fallbackPublishedAtIso] を渡して
 *   [com.feedman.android.core.ui.RelativeTimeFormatter] が解釈できる値を与える。
 */
internal object TimelineCardModelMapper {

    /**
     * [CrossFeedItem] を [ArticleCardModel] へ変換する。
     *
     * @param item cross-feed API レスポンス 1 件分
     * @return カード描画用の中立モデル
     */
    fun toCardModel(item: CrossFeedItem): ArticleCardModel = ArticleCardModel(
        id = item.id,
        title = item.title,
        feedTitle = item.feedTitle,
        faviconValue = item.feedFaviconUrl,
        publishedAtIso = item.publishedAt,
        isDateEstimated = item.isDateEstimated,
        isRead = item.isRead,
        isStarred = item.isStarred,
        hatebuCount = item.hatebuCount,
        // 解説は object 級 KDoc 参照。CrossFeedItem には hatebu_fetched_at が無いため
        // publishedAt を代用して HatebuBadge を「取得済み（数値表示）」モードで描画させる。
        hatebuFetchedAt = item.publishedAt,
        summary = item.summary,
        // Issue #37: タイムラインカードの外部リンクアイコンが Custom Tabs で開く対象 URL。
        link = item.link,
    )

    /**
     * [MockTimelineItem] を [ArticleCardModel] へ変換する（mockMode 用）。
     *
     * mockMode は API 接続無しでスクリーンを起動する開発支援機能であり、
     * `MockTimelineItem` の `publishedAt` フィールドは事前整形された相対表現文字列
     * （例: "10 分前"）なので RFC3339 ではない。本変換では [fallbackPublishedAtIso] を
     * `ArticleCardModel.publishedAtIso` として用い、相対日時表示は
     * [com.feedman.android.core.ui.RelativeTimeFormatter] に委ねる。
     *
     * @param item モック記事 1 件
     * @param fallbackPublishedAtIso 相対日時計算用に流し込む RFC3339 文字列
     */
    fun toMockCardModel(
        item: MockTimelineItem,
        fallbackPublishedAtIso: String,
    ): ArticleCardModel = ArticleCardModel(
        id = item.id,
        title = item.title,
        feedTitle = item.feedName,
        faviconValue = null, // mockMode はレターアバター fallback で十分
        publishedAtIso = fallbackPublishedAtIso,
        isDateEstimated = false,
        isRead = false,
        isStarred = false,
        hatebuCount = 0,
        hatebuFetchedAt = fallbackPublishedAtIso,
        summary = "",
    )
}
