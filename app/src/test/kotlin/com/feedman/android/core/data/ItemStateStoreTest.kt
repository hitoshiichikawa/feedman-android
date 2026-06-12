package com.feedman.android.core.data

import app.cash.turbine.test
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ItemStateStore] (Issue #38 / Req 1, 2, 3, 4, 5 / NFR 1, 2).
 *
 * `ItemDetailRepository` を fake で差し替え、楽観的更新オーバーレイの即時反映 /
 * サーバー失敗時ロールバック / 失敗イベント発行 / 複数購読者への配信 / overlay 優先合成 を
 * Turbine で検証する。
 *
 * カバーする AC:
 * - Req 1.1 / 1.2 / 1.3 / 1.4: setRead / setStarred で overlay が即時反映され単一ストリームで配信される。
 * - Req 2.1 / 2.4 / 2.5: 楽観反映後に updateState が呼ばれる。成功時は overlay 維持。
 * - Req 2.2 / 2.5: 失敗で overlay を旧値に戻す（楽観値→失敗→旧値復元の観測可能シーケンス）。
 * - Req 2.3: 失敗時に failures イベントが流れる。
 * - Req 3.1 / 3.2: ページング想定の合成 helper が overlay 値を優先しサーバー値より優先する。
 * - Req 3.3: overlay にない item は overlay 値が反映されない。
 * - Req 4.1 / 4.2 / NFR 2.2: 2 つの購読者が同じ更新を観測する。
 * - Req 5.1 / 5.2 / 5.3: markRead は冪等（同じ既読値で再度呼んでも API を再送しない）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemStateStoreTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 楽観的反映 / 配信 ───────────────────────────────────────────────

    @Test
    fun `setRead で overlay の isRead が即時 true になる_Req 1_1`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act
        store.setRead(itemId = "a1", isRead = true, baselineRead = false)

        // Assert: overlay が即時反映されている
        val overlay = store.overlays.first()
        assertEquals(true, overlay["a1"]?.isRead)
        // API も呼ばれる
        assertEquals(1, repo.updateStateCalls.size)
        assertEquals("a1", repo.updateStateCalls[0].itemId)
        assertEquals(true, repo.updateStateCalls[0].isRead)
    }

    @Test
    fun `setStarred で overlay の isStarred が即時トグルされ updateState を呼ぶ_Req 1_1_4_4`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act
        store.setStarred(itemId = "s1", isStarred = true, baselineStarred = false)

        // Assert
        val overlay = store.overlays.first()
        assertEquals(true, overlay["s1"]?.isStarred)
        assertNull(overlay["s1"]?.isRead) // Req 1.3: 既読は独立に保持される（未設定）
        val starCalls = repo.updateStateCalls.filter { it.isStarred != null }
        assertEquals(1, starCalls.size)
        assertEquals(true, starCalls[0].isStarred)
    }

    @Test
    fun `既読とスターの overlay は同一 item で独立に保持される_Req 1_3`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act: 同じ item に既読のみ設定
        store.setRead(itemId = "x", isRead = true, baselineRead = false)
        // 次にスターのみ設定
        store.setStarred(itemId = "x", isStarred = true, baselineStarred = false)

        // Assert
        val overlay = store.overlays.first()
        assertEquals(true, overlay["x"]?.isRead)
        assertEquals(true, overlay["x"]?.isStarred)
    }

    @Test
    fun `overlay 未設定 item は resolve helper でサーバー値をそのまま返す_Req 1_4_3_3`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())
        val overlay: Map<String, ItemStateOverlay> = store.overlays.first()

        // Act + Assert: helper で合成する想定
        val resolved = ItemStateStore.resolve(
            itemId = "y",
            serverRead = false,
            serverStarred = true,
            overlays = overlay,
        )
        assertEquals(false, resolved.isRead)
        assertEquals(true, resolved.isStarred)
    }

    // ── サーバー失敗 / ロールバック ───────────────────────────────────

    @Test
    fun `スター更新失敗で overlay を旧値に戻し failure イベントを流す_Req 2_2_2_3_2_5`() = runTest {
        // Arrange
        val repo = FakeRepo(updateStateError = FeedmanException(code = "X", errorMessage = "fail"))
        val scope = backgroundScope()
        val store = ItemStateStore(repository = repo, scope = scope)

        // overlay の変化と failures をそれぞれ Turbine で観測
        val collectedOverlays = mutableListOf<Map<String, ItemStateOverlay>>()
        val overlayJob = scope.launch {
            store.overlays.collect { collectedOverlays += it }
        }

        // Act + Assert: failure イベントが流れる
        store.failures.test {
            store.setStarred(itemId = "s1", isStarred = true, baselineStarred = false)
            val event = awaitItem()
            assertEquals("s1", event.itemId)
            assertEquals(ItemStateFailure.Kind.Star, event.kind)
            cancelAndIgnoreRemainingEvents()
        }

        // Req 2.5: 楽観値適用 → 旧値復元の順序を観測できる
        // collectedOverlays には少なくとも「楽観値が立っている時点」と「ロールバック後」の状態が含まれる
        val sawOptimistic = collectedOverlays.any { it["s1"]?.isStarred == true }
        val sawRollback = collectedOverlays.last()["s1"]?.isStarred != true
        assertTrue("楽観値が一度は overlay に反映されたはず（Req 2.5）", sawOptimistic)
        assertTrue("失敗後に旧値（未設定 or false）に復元されているはず（Req 2.2 / 2.5）", sawRollback)

        overlayJob.cancel()
    }

    @Test
    fun `既読更新失敗で overlay を旧値に戻し failure イベントを流す_Req 2_2_2_3_2_5`() = runTest {
        // Arrange
        val repo = FakeRepo(updateStateError = FeedmanException(code = "X", errorMessage = "fail"))
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act + Assert
        store.failures.test {
            store.setRead(itemId = "r1", isRead = true, baselineRead = false)
            val event = awaitItem()
            assertEquals("r1", event.itemId)
            assertEquals(ItemStateFailure.Kind.Read, event.kind)
            cancelAndIgnoreRemainingEvents()
        }
        // 失敗後の overlay 状態は元 baseline（false）に戻っている
        val finalOverlay = store.overlays.first()
        val overlay = finalOverlay["r1"]
        // 旧 baseline が false（=サーバー値と同じ）なので overlay は除去 or false 保持のいずれでもよい
        // 「楽観値 true がそのまま残っていない」ことが重要
        assertFalse("失敗後に楽観値 true がそのまま残ってはいけない（Req 2.2）", overlay?.isRead == true)
    }

    @Test
    fun `成功時は overlay を維持し追加のロールバックを行わない_Req 2_4`() = runTest {
        // Arrange
        val repo = FakeRepo() // 成功
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act
        store.setStarred(itemId = "z", isStarred = true, baselineStarred = false)

        // Assert: API が呼ばれ、overlay は維持されている
        assertEquals(1, repo.updateStateCalls.size)
        val overlay = store.overlays.first()
        assertEquals(true, overlay["z"]?.isStarred)
    }

    // ── 複数購読者リアクティブ ─────────────────────────────────────

    @Test
    fun `複数購読者が同じ overlay 更新を観測する_Req 4_1_4_2_NFR 2_2`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())
        val sub1 = mutableListOf<Map<String, ItemStateOverlay>>()
        val sub2 = mutableListOf<Map<String, ItemStateOverlay>>()
        val j1 = backgroundScope().launch { store.overlays.collect { sub1 += it } }
        val j2 = backgroundScope().launch { store.overlays.collect { sub2 += it } }

        // Act
        store.setStarred(itemId = "shared", isStarred = true, baselineStarred = false)

        // Assert: 両方の購読者が overlay の更新を観測する
        assertTrue(sub1.any { it["shared"]?.isStarred == true })
        assertTrue(sub2.any { it["shared"]?.isStarred == true })

        j1.cancel(); j2.cancel()
    }

    // ── ページング合成 (resolve helper) ─────────────────────────────

    @Test
    fun `resolve は overlay 値をサーバー値より優先する_Req 3_1`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())
        store.setStarred(itemId = "p1", isStarred = true, baselineStarred = false)
        val overlay = store.overlays.first()

        // Act: サーバーが（古い）false を返しても overlay の true が勝つ
        val resolved = ItemStateStore.resolve(
            itemId = "p1",
            serverRead = false,
            serverStarred = false, // サーバー由来値
            overlays = overlay,
        )

        // Assert
        assertEquals(true, resolved.isStarred) // Req 3.1
        assertEquals(false, resolved.isRead)   // 既読は overlay 未設定なのでサーバー値
    }

    @Test
    fun `resolve は overlay 値とサーバー値が一致しても差分を生じさせない_Req 3_4`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())
        store.setStarred(itemId = "p2", isStarred = true, baselineStarred = false)
        val overlay = store.overlays.first()

        // Act: 既にサーバー値も true（一致）
        val resolved = ItemStateStore.resolve(
            itemId = "p2",
            serverRead = false,
            serverStarred = true,
            overlays = overlay,
        )

        // Assert: 一致しているので overlay 経由でも同じ値
        assertEquals(true, resolved.isStarred)
    }

    @Test
    fun `新しいサーバーページが来ても overlay は維持される_Req 3_2`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())
        store.setRead(itemId = "p3", isRead = true, baselineRead = false)

        // Act: 新ページが届く想定で resolve を呼ぶ（overlay は変わらない）
        val overlay = store.overlays.first()
        val resolved = ItemStateStore.resolve(
            itemId = "p3",
            serverRead = false, // 新ページが「未読」を返してきた
            serverStarred = false,
            overlays = overlay,
        )

        // Assert: overlay の既読が維持される
        assertEquals(true, resolved.isRead)
    }

    // ── 既読化トリガー / 冪等 ───────────────────────────────────────

    @Test
    fun `markRead は既に既読の item に対して API を再送しない_Req 5_3`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act: 既読の item に既読化トリガー
        store.markRead(itemId = "r0", currentIsRead = true)

        // Assert: API は呼ばれない（冪等 / Req 5.3）
        assertTrue(repo.updateStateCalls.isEmpty())
        // overlay も新たな上書きは入らない（未設定のまま）
        assertNull(store.overlays.first()["r0"])
    }

    @Test
    fun `markRead は未読の item に対して overlay 反映と API 発火を行う_Req 5_1_5_2`() = runTest {
        // Arrange
        val repo = FakeRepo()
        val store = ItemStateStore(repository = repo, scope = backgroundScope())

        // Act: 未読の item に既読化トリガー
        store.markRead(itemId = "r2", currentIsRead = false)

        // Assert: overlay が isRead=true、API が呼ばれる
        val overlay = store.overlays.first()
        assertEquals(true, overlay["r2"]?.isRead)
        assertEquals(1, repo.updateStateCalls.size)
        assertEquals("r2", repo.updateStateCalls[0].itemId)
        assertEquals(true, repo.updateStateCalls[0].isRead)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun backgroundScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Unconfined)

    private data class UpdateCall(val itemId: String, val isRead: Boolean?, val isStarred: Boolean?)

    /** updateState の挙動を制御する fake。エラー注入 / 完了待ち合わせができる。 */
    private class FakeRepo(
        var updateStateError: FeedmanException? = null,
    ) : ItemDetailRepository {
        val updateStateCalls: MutableList<UpdateCall> = mutableListOf()
        var pendingGate: CompletableDeferred<Unit>? = null

        override suspend fun getItem(itemId: String): com.feedman.android.core.model.ItemDetail =
            error("getItem must not be called in this test")

        override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
            updateStateCalls += UpdateCall(itemId, isRead, isStarred)
            pendingGate?.await()
            updateStateError?.let { throw it }
        }
    }
}
