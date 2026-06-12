package com.feedman.android.feature.registerfeed

import com.feedman.android.core.network.FeedmanException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RegisterFeedErrorResolver] の単体テスト（Issue #44 / Req 5.1〜5.6）。
 *
 * httpStatus 主導 + サーバー `errorMessage` フォールバックの分岐ロジックを純粋関数として
 * 検証する。Android 依存なし。
 */
class RegisterFeedErrorResolverTest {

    private val texts = RegisterFeedErrorTexts(
        duplicate = "[DUP]",
        invalidUrl = "[INVALID]",
        rateLimitWithSeconds = { seconds -> "[RL=$seconds]" },
        rateLimitGeneric = "[RL_GENERIC]",
        genericFallback = "[GENERIC]",
        networkUnreachable = "[NETWORK]",
    )

    // ── Req 5.1: 409 重複登録 ────────────────────────────────

    @Test
    fun `Req 5_1 409 でサーバー message が存在すれば優先する`() {
        val e = FeedmanException(
            code = "FEED_ALREADY_REGISTERED",
            errorMessage = "このフィードはすでに登録されています",
            httpStatus = 409,
        )
        assertEquals("このフィードはすでに登録されています", RegisterFeedErrorResolver.resolve(e, texts))
    }

    @Test
    fun `Req 5_1 409 でサーバー message が空ならフォールバック文言を使う`() {
        val e = FeedmanException(code = "X", errorMessage = "", httpStatus = 409)
        assertEquals("[DUP]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    // ── Req 5.3: 429 + retryAfterSeconds ─────────────────

    @Test
    fun `Req 5_3 429 で retryAfterSeconds があれば残時間付き文言を使う`() {
        val e = FeedmanException(
            code = "REGISTRATION_RATE_LIMIT",
            errorMessage = "サーバー由来文言",
            httpStatus = 429,
            retryAfterSeconds = 60,
        )
        // 残時間付き UI 文言は texts 経由で構築 (サーバー message は採用しない)
        assertEquals("[RL=60]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    // ── Req 5.4: 429 で retryAfterSeconds なし ────────────

    @Test
    fun `Req 5_4 429 で retryAfterSeconds が null なら汎用文言を使う`() {
        val e = FeedmanException(
            code = "REGISTRATION_RATE_LIMIT",
            errorMessage = "サーバー由来文言",
            httpStatus = 429,
            retryAfterSeconds = null,
        )
        assertEquals("[RL_GENERIC]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    @Test
    fun `Req 5_4 429 で retryAfterSeconds が 0 でも汎用文言を使う`() {
        val e = FeedmanException(
            code = "X",
            errorMessage = "x",
            httpStatus = 429,
            retryAfterSeconds = 0,
        )
        assertEquals("[RL_GENERIC]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    // ── Req 5.2: 400 / 422 URL 不正 / フィード未検出 ─────

    @Test
    fun `Req 5_2 400 でサーバー message を優先する`() {
        val e = FeedmanException(
            code = "INVALID_FEED_URL",
            errorMessage = "このサイトでフィードを検出できませんでした",
            httpStatus = 400,
        )
        assertEquals(
            "このサイトでフィードを検出できませんでした",
            RegisterFeedErrorResolver.resolve(e, texts),
        )
    }

    @Test
    fun `Req 5_2 422 でサーバー message が空ならフォールバック文言を使う`() {
        val e = FeedmanException(code = "X", errorMessage = "", httpStatus = 422)
        assertEquals("[INVALID]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    // ── Req 5.5: その他 4xx / 5xx ─────────────────────────

    @Test
    fun `Req 5_5 500 でサーバー message を優先する`() {
        val e = FeedmanException(
            code = "INTERNAL_ERROR",
            errorMessage = "サーバー内部エラー",
            httpStatus = 500,
        )
        assertEquals("サーバー内部エラー", RegisterFeedErrorResolver.resolve(e, texts))
    }

    @Test
    fun `Req 5_5 500 でサーバー message が空なら汎用フォールバック`() {
        val e = FeedmanException(code = "X", errorMessage = "", httpStatus = 500)
        assertEquals("[GENERIC]", RegisterFeedErrorResolver.resolve(e, texts))
    }

    // ── Req 5.6: ネットワーク到達不可 ─────────────────────

    @Test
    fun `Req 5_6 NETWORK_ERROR ならネットワーク文言を使う httpStatus 無視`() {
        val e = FeedmanException(
            code = FeedmanException.CODE_NETWORK_ERROR,
            errorMessage = "irrelevant",
            httpStatus = null,
        )
        assertEquals("[NETWORK]", RegisterFeedErrorResolver.resolve(e, texts))
    }
}
