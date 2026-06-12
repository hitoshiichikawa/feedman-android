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
     * 描画側で [ArticleCardModel.relativeTimeOverride] が非 null なら本値は読まれないが、
     * 念のため空文字列を入れて [RelativeTimeFormatter] を直接呼ばれた場合の早期検出に使う
     * （空文字は parse 失敗 = `IllegalArgumentException` で気付ける）。
     */
    internal const val UNKNOWN_PUBLISHED_AT: String = ""

    /**
     * [ItemSearchHit] を [ArticleCardModel] へ変換する。
     *
     * `published_at` が null のとき [ArticleCardModel.relativeTimeOverride] に
     * [unknownLabel] を詰めて返す（Req 4.6）。描画側 ArticleCard はこの override が非 null
     * のとき [RelativeTimeFormatter] を呼ばずに本文字列をそのままメタ行に表示するため、
     * カード自体は他項目（タイトル / ソース / はてブ / スター）を含めて描画される。
     *
     * @param hit 検索 API レスポンス 1 件分
     * @param unknownLabel `published_at` が null のときメタ行に表示する代替文字列
     *   （`stringResource(R.string.search_published_at_unknown)` 相当 / Req 4.6）
     * @return カード描画用の中立モデル
     */
    fun toCardModel(hit: ItemSearchHit, unknownLabel: String): ArticleCardModel = ArticleCardModel(
        id = hit.id,
        title = hit.title,
        feedTitle = hit.feedTitle, // Req 4.2: feed_title をソース表示に用いる
        // Req 4.3 / 4.4: favicon_url が data URL なら Favicon が復号、null ならレターアバター
        faviconValue = hit.faviconUrl,
        // Req 4.5 / 4.6: published_at が非 null のときはそのまま、null のとき空文字に正規化
        publishedAtIso = hit.publishedAt ?: UNKNOWN_PUBLISHED_AT,
        isDateEstimated = hit.isDateEstimated,
        isRead = hit.isRead,
        isStarred = hit.isStarred,
        hatebuCount = hit.hatebuCount,
        // Req 4.7: ItemSearchHit は hatebu_fetched_at を持たないため null
        hatebuFetchedAt = null,
        summary = hit.summary,
        link = hit.link,
        // Req 4.6: published_at が null のときのみ override 文字列をメタ行に流す
        relativeTimeOverride = if (hit.publishedAt == null) unknownLabel else null,
    )
}
