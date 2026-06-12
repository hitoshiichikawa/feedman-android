package com.feedman.android.core.auth

/**
 * トークンセット（access token / refresh token / access token 有効期限）の永続化境界
 * （Issue #20 / Req 1）。
 *
 * 上位レイヤ（AuthInterceptor / TokenAuthenticator / AuthRepository。いずれも後続 Issue で
 * 実装）は本インターフェースのみを参照し、永続実装（[EncryptedPrefsTokenStore]）と
 * テスト用 fake（[com.feedman.android.core.auth.fake.InMemoryTokenStore]）を DI で差し替える
 * 構成とする（Req 1.5 / Req 4.3）。
 *
 * 設計上の留意点:
 * - [save] は part-update ではなくセット全体の上書きを意味する（Req 1.4）。
 * - [read] は保存されたセットが無いとき null を返し、例外を投げない（Req 2.5）。
 * - 読み取り不能（鍵破損・ストレージ破損）は [TokenStoreException] で呼び出し側に通知する
 *   （Req 2.6）。silent fail（empty 値で隠す）はしない。
 *
 * 並行性: 並行 save / read のもとでも、読み手は保存前後どちらかの一貫したセットを観測し、
 * 部分更新の合成セットは返さない（NFR 1.2）。実装側は単一の write 操作で全フィールドを
 * 同時にコミットすることで保証する。
 *
 * 非同期: 永続実装は内部で I/O を行うため `suspend` で宣言する。in-memory fake も同じ
 * シグネチャを満たせるよう、本インターフェースをそのまま実装する（Req 3.1）。
 */
interface TokenStore {

    /**
     * トークンセットを保存する。既存のセットが存在する場合は全フィールドを丸ごと上書きする
     * （部分更新ではない / Req 1.1 / Req 1.4）。
     *
     * @throws TokenStoreException 永続実装で書き込み不能（鍵破損・ストレージ破損等）の場合
     */
    suspend fun save(tokenSet: TokenSet)

    /**
     * 現在保存されているトークンセットを返す。保存されていない場合は `null` を返す
     * （Req 1.2 / Req 2.5）。
     *
     * @throws TokenStoreException 永続実装で読み取り不能（鍵破損・ストレージ破損等）の場合（Req 2.6）
     */
    suspend fun read(): TokenSet?

    /**
     * 保存されているトークンフィールドを全て削除する。以降の [read] は `null` を返す
     * （Req 1.3 / Req 2.4）。
     *
     * @throws TokenStoreException 永続実装で削除不能の場合
     */
    suspend fun clear()
}

/**
 * [TokenStore] の永続実装が暗号化ストアを開けない・読み書きできない等の致命エラーを
 * 呼び出し側に通知するための型付き例外（Req 2.6）。
 *
 * silent fail を避けるため、実装は捕捉した低レベル例外を本例外で wrap して再 throw する。
 */
class TokenStoreException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
