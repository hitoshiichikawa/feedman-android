package com.feedman.android.di

import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.fake.FakeSubscriptionRepository
import com.feedman.android.core.network.ApiClientFactory
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * [selectSubscriptionRepository] 純粋関数の単体テスト（Issue #39 / Req 3.1, 3.3 / NFR 2.3）。
 *
 * DI 切替ロジックを Hilt から独立して検証することで、`AppConfig.mockMode = true` のときに
 * Fake が選択され、`false` のときに実 API 実装が選択されることを機械的に保証する。
 */
class SubscriptionRepositoryProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Req 3_1 mockMode true のとき Fake 実装を返す`() {
        // Arrange
        val fake = FakeSubscriptionRepository()
        val real = SubscriptionRepositoryImpl(
            ApiClientFactory.create(baseUrl = server.url("/").toString()),
        )

        // Act
        val resolved = selectSubscriptionRepository(mockMode = true, fake = fake, real = real)

        // Assert: Fake インスタンスがそのまま返る
        assertSame(fake, resolved)
    }

    @Test
    fun `Req 3_3 mockMode false のとき実 API 実装を返す`() {
        // Arrange
        val fake = FakeSubscriptionRepository()
        val real = SubscriptionRepositoryImpl(
            ApiClientFactory.create(baseUrl = server.url("/").toString()),
        )

        // Act
        val resolved = selectSubscriptionRepository(mockMode = false, fake = fake, real = real)

        // Assert: 実 API 実装インスタンスがそのまま返る
        assertSame(real, resolved)
    }
}
