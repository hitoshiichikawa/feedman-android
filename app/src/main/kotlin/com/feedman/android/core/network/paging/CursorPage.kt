package com.feedman.android.core.network.paging

/**
 * カーソルページネーション基盤（[CursorPagingSource]）が扱う統一ページ envelope。
 *
 * SPEC §4.1 で定義される `{ items, next_cursor, has_more }` の不変表現で、`core/model` の
 * `Page<T>` および `CrossFeedPage` から呼び出し側 Repository が変換して渡す想定（NFR 1.1）。
 * `Page<T>` を直接扱わずに独立した型を切る理由は、横断新着の `CrossFeedPage`（`since_time`
 * を持つ）と通常の `Page<T>` を同じ基盤で扱えるようにし、サーバー envelope の項目追加が
 * 本基盤の公開 API に波及しないようにするため（Req 5.1 / Req 5.2）。
 *
 * @property items 当該ページのアイテム配列。0 件もありうる（終端の直前ページなど）。
 * @property nextCursor 次ページ取得用の不透明トークン。`null` または空文字列で終端を示す
 *   （Req 2.2）。本基盤はパース・解釈しない（Req 1.3）。
 * @property hasMore `false` のとき後続ページなしを示す（Req 2.1）。`true` でも `nextCursor`
 *   が `null` / 空なら終端として扱う（Req 2.2）。
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
