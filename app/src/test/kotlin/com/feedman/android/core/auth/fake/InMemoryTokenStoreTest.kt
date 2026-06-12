package com.feedman.android.core.auth.fake

import com.feedman.android.core.auth.TokenSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [InMemoryTokenStore] および [com.feedman.android.core.auth.TokenStore] インターフェース契約の
 * 単体テスト（Issue #20）。
 *
 * 永続実装 [com.feedman.android.core.auth.EncryptedPrefsTokenStore] は Android runtime が無いと
 * 初期化できないため、本テストでは TokenStore の **インターフェース契約**（save / read / clear /
 * セット単位の上書き / 未保存時 null）を fake で検証する（NFR 2.1 / Req 3）。永続実装の特有な
 * 挙動（暗号化保管・プロセス再起動またぎ）の検証は instrumented test に委ねる（NFR 2.2）。
 */
class InMemoryTokenStoreTest {

    private fun sampleTokenSet(
        access: String = "access-jwt-token",
        refresh: String = "refresh-opaque-token",
        expiresAt: Long = 1_700_000_000_000L,
    ): TokenSet = TokenSet(
        accessToken = access,
        refreshToken = refresh,
        accessTokenExpiresAtEpochMillis = expiresAt,
    )

    /** Req 1.2 / 2.5 / 3.2: 未保存時の read は null（例外を投げない） */
    @Test
    fun `read returns null when no token set has been stored yet`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()

        // Act
        val actual = store.read()

        // Assert
        assertNull(
            "Fresh InMemoryTokenStore must return null on read before any save (Req 1.2 / 3.2).",
            actual,
        )
    }

    /** Req 1.1 / 1.2 / 3.3: save 後の read は保存したセットを返す */
    @Test
    fun `read returns the same token set that was most recently saved`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()
        val saved = sampleTokenSet()

        // Act
        store.save(saved)
        val actual = store.read()

        // Assert
        assertNotNull(actual)
        assertEquals(saved, actual)
    }

    /** Req 1.4: save はセット全体を上書きする（部分更新ではない） */
    @Test
    fun `save replaces the previously stored token set as a whole`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()
        val first = sampleTokenSet(
            access = "first-access",
            refresh = "first-refresh",
            expiresAt = 1_000L,
        )
        val second = sampleTokenSet(
            access = "second-access",
            refresh = "second-refresh",
            expiresAt = 2_000L,
        )

        // Act
        store.save(first)
        store.save(second)
        val actual = store.read()

        // Assert: 部分更新ではなく全体が second に置換されている
        assertEquals(second, actual)
        assertNotEquals(first.accessToken, actual?.accessToken)
        assertNotEquals(first.refreshToken, actual?.refreshToken)
        assertNotEquals(
            first.accessTokenExpiresAtEpochMillis,
            actual?.accessTokenExpiresAtEpochMillis,
        )
    }

    /** Req 1.3 / 2.4 / 3.4: clear 後の read は null を返す */
    @Test
    fun `read returns null after clear is invoked`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()
        store.save(sampleTokenSet())

        // Act
        store.clear()
        val actual = store.read()

        // Assert
        assertNull(actual)
    }

    /** Req 1.3 / 2.4: clear した後に再度 save し read すれば、新しいセットが返る */
    @Test
    fun `save after clear stores the new token set and read returns it`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()
        store.save(sampleTokenSet(access = "old"))
        store.clear()
        val resaved = sampleTokenSet(access = "new-access", refresh = "new-refresh", expiresAt = 9_999L)

        // Act
        store.save(resaved)
        val actual = store.read()

        // Assert
        assertEquals(resaved, actual)
    }

    /** Req 1.3: 何も保存していない状態で clear を呼んでも例外にならず、read は null のまま */
    @Test
    fun `clear is a no-op when nothing has been stored`() = runTest {
        // Arrange
        val store = InMemoryTokenStore()

        // Act
        store.clear()
        val actual = store.read()

        // Assert
        assertNull(actual)
    }

    /** Req 4.3 / 3.1: TokenStore インターフェース型として参照しても同じ挙動になる */
    @Test
    fun `InMemoryTokenStore is substitutable as a TokenStore interface reference`() = runTest {
        // Arrange: TokenStore 型で受ける（差し替え可能性の最小検証）
        val store: com.feedman.android.core.auth.TokenStore = InMemoryTokenStore()
        val expected = sampleTokenSet()

        // Act
        assertNull(store.read())
        store.save(expected)
        val readBack = store.read()
        store.clear()
        val afterClear = store.read()

        // Assert
        assertEquals(expected, readBack)
        assertNull(afterClear)
    }
}
