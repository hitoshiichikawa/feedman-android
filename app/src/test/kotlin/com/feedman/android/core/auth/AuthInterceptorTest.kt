package com.feedman.android.core.auth

import com.feedman.android.core.auth.fake.InMemoryTokenStore
import com.feedman.android.core.network.ApiClientFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [AuthInterceptor] の単体テスト（Issue #21 Req 4）。
 *
 * 実 OkHttp + MockWebServer 経由でリクエストヘッダを観察し、AC ごとに Bearer 付与の有無を検証する。
 *
 * 対応 AC:
 * - Req 4.1: アクセストークン保存時、認証対象リクエストに `Authorization: Bearer <token>` を付与する
 * - Req 4.2: アクセストークン未保存時、`Authorization` ヘッダを付与せずに送信する
 * - Req 4.3: 認証不要エンドポイント（token 交換 / refresh）には `Authorization` ヘッダを付与しない
 */
class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var tokenStore: InMemoryTokenStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
        tokenStore = InMemoryTokenStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newApi() = ApiClientFactory.create(
        baseUrl = baseUrl,
        additionalInterceptors = listOf(AuthInterceptor(tokenStore)),
    )

    @Test
    fun `Req 4-1 Bearer is attached when access token is stored`() = runTest {
        // Arrange: TokenStore にアクセストークンを保存
        tokenStore.save(TokenSet("access-abc", "refresh-xyz", 9_000L))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"u1","email":"a@b"}"""))
        val api = newApi()

        // Act
        api.getCurrentUser()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("Bearer access-abc", recorded.getHeader("Authorization"))
    }

    @Test
    fun `Req 4-2 Authorization header is omitted when no access token is stored`() = runTest {
        // Arrange: TokenStore は empty
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"u1","email":"a@b"}"""))
        val api = newApi()

        // Act
        api.getCurrentUser()

        // Assert
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `Req 4-3 Authorization header is not attached to token exchange endpoint`() = runTest {
        // Arrange: アクセストークンが保存されていても token 交換 / refresh には Bearer を付与しない
        tokenStore.save(TokenSet("access-abc", "refresh-xyz", 9_000L))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900}"""
                ),
        )
        val api = newApi()

        // Act
        api.exchangeAuthToken(
            com.feedman.android.core.network.TokenExchangeRequest(
                authCode = "code",
                codeVerifier = "verifier",
            ),
        )

        // Assert: Bearer は付与されない（Req 4.3）
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("/api/auth/token", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 4-3 Authorization header is not attached to refresh endpoint`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("access-abc", "refresh-xyz", 9_000L))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900}"""
                ),
        )
        val api = newApi()

        // Act
        api.refreshAuthToken(com.feedman.android.core.network.RefreshTokenRequest(refreshToken = "refresh-xyz"))

        // Assert: refresh エンドポイントにも Bearer は付与されない（401 ループ防止 / Req 4.3）
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("/api/auth/refresh", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Bearer is attached to revoke endpoint because revoke requires authentication`() = runTest {
        // Arrange: SERVER.md §1.3 の revoke は Bearer 認証下なので、Authorization が付くべき
        tokenStore.save(TokenSet("access-abc", "refresh-xyz", 9_000L))
        server.enqueue(MockResponse().setResponseCode(204))
        val api = newApi()

        // Act
        api.revokeAuthToken(com.feedman.android.core.network.RevokeTokenRequest(refreshToken = "refresh-xyz"))

        // Assert
        val recorded = server.takeRequest()
        assertEquals("Bearer access-abc", recorded.getHeader("Authorization"))
        assertEquals("/api/auth/revoke", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Authorization header is omitted when stored access token is blank`() = runBlocking {
        // Arrange: 境界ケース — 空文字の access token が保存されている場合は付与しない
        tokenStore.save(TokenSet(accessToken = "", refreshToken = "r", accessTokenExpiresAtEpochMillis = 9_000L))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"u1","email":"a@b"}"""))
        val api = newApi()

        // Act
        api.getCurrentUser()

        // Assert
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
