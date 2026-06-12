package com.feedman.android.core.data.fake

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FakeSubscriptionRepository] の単体テスト（Issue #30 / Req 5.1, 5.2, 5.3, 5.4 / Req 1.5）。
 *
 * Fake が満たすべき仕様:
 * - Req 5.3: 購読開始時点で即座に現在のフィード一覧を 1 件以上 emit する
 * - Req 5.2: active / stopped / error の各状態を最低 1 件ずつ含む
 * - Req 1.1.1 / 1.1.2: 未読件数が 0 のフィードと 1 以上のフィードの両方を含む
 *   （UI 側の未読バッジ表示・非表示分岐を検証可能にするため）
 * - favicon は data URL と null の両方を含む（#26 Favicon の分岐を確認）
 * - Req 1.5: 同一 Fake インスタンスを複数回購読しても順序が安定している
 */
class FakeSubscriptionRepositoryTest {

    @Test
    fun `observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3`() = runTest {
        // Arrange
        val repository = FakeSubscriptionRepository()

        // Act + Assert
        repository.observeSubscriptions().test {
            val first = awaitItem()
            assertTrue(
                "Fake は表示確認用に空でないリストを emit する必要がある（Req 5.2）",
                first.isNotEmpty(),
            )
            awaitComplete()
        }
    }

    @Test
    fun `モックデータに active stopped error の状態がそれぞれ 1 件以上含まれる_Req 5_2`() = runTest {
        // Arrange
        val repository = FakeSubscriptionRepository()

        // Act
        var items: List<com.feedman.android.core.model.Subscription> = emptyList()
        repository.observeSubscriptions().test {
            items = awaitItem()
            awaitComplete()
        }

        // Assert
        assertTrue("active が含まれる", items.any { it.feedStatus == "active" })
        assertTrue("stopped が含まれる", items.any { it.feedStatus == "stopped" })
        assertTrue("error が含まれる", items.any { it.feedStatus == "error" })
    }

    @Test
    fun `モックデータに未読 0 件のフィードと 1 以上のフィードが両方含まれる_Req 1_1_1_1_1_2`() = runTest {
        // Arrange
        val repository = FakeSubscriptionRepository()

        // Act
        var items: List<com.feedman.android.core.model.Subscription> = emptyList()
        repository.observeSubscriptions().test {
            items = awaitItem()
            awaitComplete()
        }

        // Assert
        assertTrue("未読 0 件のフィードが含まれる", items.any { it.unreadCount == 0 })
        assertTrue("未読 1 件以上のフィードが含まれる", items.any { it.unreadCount > 0 })
    }

    @Test
    fun `モックデータに favicon data URL と null の両方が含まれる_Req 5_4`() = runTest {
        // Arrange
        val repository = FakeSubscriptionRepository()

        // Act
        var items: List<com.feedman.android.core.model.Subscription> = emptyList()
        repository.observeSubscriptions().test {
            items = awaitItem()
            awaitComplete()
        }

        // Assert
        assertNotNull(
            "data URL の favicon が少なくとも 1 件存在する",
            items.firstOrNull { it.faviconUrl?.startsWith("data:") == true },
        )
        assertTrue(
            "favicon_url が null のフィードも存在する（レターアバター fallback 経路）",
            items.any { it.faviconUrl == null },
        )
    }

    @Test
    fun `複数回購読しても同じ順序のスナップショットが返る_Req 1_5`() = runTest {
        // Arrange
        val repository = FakeSubscriptionRepository()
        var first: List<com.feedman.android.core.model.Subscription> = emptyList()
        var second: List<com.feedman.android.core.model.Subscription> = emptyList()
        repository.observeSubscriptions().test {
            first = awaitItem()
            awaitComplete()
        }

        // Act
        repository.observeSubscriptions().test {
            second = awaitItem()
            awaitComplete()
        }

        // Assert: feed_id 列で順序の安定性を比較
        assertEquals(first.map { it.feedId }, second.map { it.feedId })
    }
}
