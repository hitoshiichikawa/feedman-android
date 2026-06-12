package com.feedman.android.core.auth.fake

import com.feedman.android.core.auth.TokenSet
import com.feedman.android.core.auth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android ランタイム非依存の in-memory fake [TokenStore] 実装（Issue #20 / Req 3）。
 *
 * - 構築直後は empty 状態（[read] が `null` を返す / Req 3.2）。
 * - [save] / [clear] は次回以降の [read] に反映される（Req 3.3 / Req 3.4）。
 * - [save] / [clear] / [read] は単一の [Mutex] で直列化し、並行アクセス時にも一貫した
 *   セットを観測させる（NFR 1.2 を fake 側でも担保することで、上位レイヤのテストが
 *   並行性を仮定してよい）。
 *
 * 永続実装が必要としない Android 依存（EncryptedSharedPreferences / MasterKey）を持たない
 * ため、JVM 単体テストの runtime からそのまま `InMemoryTokenStore()` で利用できる
 * （Req 3.1 / NFR 2.1）。
 *
 * Hilt の `@Inject constructor()` を付与しているため、テストや mockMode で永続実装の
 * 代わりに DI で差し込めば、本実装が DI コンテナから供給される（Req 4.3）。
 */
@Singleton
class InMemoryTokenStore @Inject constructor() : TokenStore {

    private val mutex = Mutex()
    private var current: TokenSet? = null

    override suspend fun save(tokenSet: TokenSet) {
        mutex.withLock {
            current = tokenSet
        }
    }

    override suspend fun read(): TokenSet? = mutex.withLock { current }

    override suspend fun clear() {
        mutex.withLock {
            current = null
        }
    }
}
