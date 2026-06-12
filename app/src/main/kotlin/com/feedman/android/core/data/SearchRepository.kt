package com.feedman.android.core.data

import androidx.paging.PagingData
import com.feedman.android.core.model.ItemSearchHit
import kotlinx.coroutines.flow.Flow

/**
 * 横断検索（SPEC §5.3 / §4.2 `GET /api/items/search?q=&scope=global`）のデータ層境界
 * （Issue #47）。
 *
 * 指定キーワードでサーバーへ横断検索を要求し、Paging 3 の
 * `Flow<PagingData<ItemSearchHit>>` を返す。本リポジトリは
 * `scope=global`（購読中の全フィードを横断）に **固定**し、`scope=feed`
 * （フィード内検索）は Issue スコープ外として扱わない（Req 7.2）。
 *
 * ## ページング規約（Req 3 / Req 5）
 *
 * - 初回ロード（cursor=null）はキーワードと `scope=global` のみで先頭ページを取得する
 *   （Req 3.3）
 * - 後続ロードは前ページの `next_cursor` を `cursor` クエリに搬送する（Req 5.1）
 * - キーワードはセッション開始時に確定し、後続ページ要求にも保持する（Req 5.5）
 * - `has_more=false` / `next_cursor=null` で終端に到達したとみなす（Req 5.2 / 5.3）
 * - 初回失敗・追加ロード失敗はいずれも
 *   [com.feedman.android.core.network.paging.CursorPagingSource] 経由で
 *   `LoadResult.Error` として上位レイヤーへ透過する（Req 6.2 / 6.4）
 *
 * ## キーワード変更時の挙動（Req 5.4）
 *
 * 同一インスタンスでも、`pagingData(query)` を異なるキーワードで呼び直すたびに新しい
 * [androidx.paging.Pager] を返す。UI 側は新しいキーワード送信のたびに Flow を購読し直す
 * ことで、それまでのページ蓄積を破棄し、新しいキーワードで先頭ページから取得を開始する
 * （Req 5.4）。
 *
 * ## スコープ外
 *
 * - フィード内検索（`scope=feed`）UI（Req 7.2）
 * - 検索結果カードから記事詳細シートを開く導線（Req 7.3 / #48 で扱う）
 * - 検索履歴の永続化（Req 7.4）
 * - キーワード通知（Req 7.5）
 */
interface SearchRepository {

    /**
     * 指定キーワードの横断検索 Paging データストリーム。
     *
     * 呼び出し 1 回ごとに新しい [androidx.paging.Pager] を生成する。UI 側は新しい
     * 検索送信のたびに本メソッドを呼び直し、戻り値の Flow を購読し直すことで、
     * それまでのページ蓄積を破棄し、新しいキーワードで先頭ページから取得を開始する
     * （Req 5.4）。
     *
     * @param query 検索キーワード（前後の空白は呼び出し側で除去済みであることを前提とする）。
     *   呼び出し側は空文字列を渡してはならない（Req 2.1 / 3.2 で空送信は呼び出し前に弾く）。
     */
    fun pagingData(query: String): Flow<PagingData<ItemSearchHit>>
}
