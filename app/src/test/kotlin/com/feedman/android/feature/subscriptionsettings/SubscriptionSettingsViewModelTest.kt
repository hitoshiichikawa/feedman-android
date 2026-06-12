package com.feedman.android.feature.subscriptionsettings

import app.cash.turbine.test
import com.feedman.android.core.data.SubscriptionLoadState
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SubscriptionSettingsViewModel] の単体テスト（Issue #43 / requirements.md AC 1〜5）。
 *
 * 主な観点:
 * - open / close の表示状態切替（Req 1.1 / 1.2 / 1.4）
 * - フェッチ間隔の初期選択 / 未選択判定（Req 2.2 / 2.3）
 * - 保存 成功 / 失敗 / 旧値ロールバック（Req 2.4 / 2.5 / 2.6 / 5.2）
 * - 再開 成功 / 失敗 / 表示判定（Req 3.1 / 3.3 / 3.5）
 * - 解除 確認ダイアログ → DELETE → イベント発火 / 失敗ロールバック（Req 4.1〜4.7）
 * - 認証切れ時のリダイレクトイベント（Req 5.3）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionSettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── open / close ─────────────────────────────────────────────

    @Test
    fun `初期状態は Hidden_Req 1_4`() = runTest {
        val vm = SubscriptionSettingsViewModel(repository = StubRepository(listOf()))
        assertEquals(SubscriptionSettingsUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `open で対象 feedId の Subscription を Visible 状態にする_Req 1_1`() = runTest {
        // Arrange
        val target = newSub(feedId = "feed-a", interval = 60)
        val vm = SubscriptionSettingsViewModel(repository = StubRepository(listOf(target)))

        // Act
        vm.open("feed-a")

        // Assert
        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals(target, state.subscription)
    }

    @Test
    fun `open 後に close で Hidden に戻る_Req 1_4`() = runTest {
        val target = newSub(feedId = "feed-a", interval = 60)
        val vm = SubscriptionSettingsViewModel(repository = StubRepository(listOf(target)))
        vm.open("feed-a")

        vm.close()

        assertEquals(SubscriptionSettingsUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `Issue 52 Req 5_2 Repository が未存在 feedId を返したら NotFound に遷移する`() = runTest {
        val vm = SubscriptionSettingsViewModel(repository = StubRepository(emptyList()))
        vm.open("missing")
        // Issue #52 Req 5.2 / 5.3: Hidden ではなく NotFound でエラー + 再試行を露出する
        // （dead-end 回避）。NotFound を表示することで「閉じる」以外の選択肢を残す。
        val state = vm.uiState.value
        assertTrue(
            "expected NotFound but was $state",
            state is SubscriptionSettingsUiState.NotFound,
        )
        assertEquals(
            "missing",
            (state as SubscriptionSettingsUiState.NotFound).feedId,
        )
    }

    @Test
    fun `Issue 52 Req 5_1 open 直後で観測前は Loading を経由する`() = runTest {
        // Arrange: observeFeed が即時 emit せず suspend するスタブ
        val sub = newSub(feedId = "feed-a", interval = 60)
        val gateRepo = GateRepository(sub)
        val vm = SubscriptionSettingsViewModel(repository = gateRepo)
        // Act: open するが Flow は未 emit
        vm.open("feed-a")
        // Assert: Loading 状態でシートを描画できることを担保（dead-end 回避）
        val state = vm.uiState.value
        assertTrue(
            "expected Loading but was $state",
            state is SubscriptionSettingsUiState.Loading,
        )
        assertEquals(
            "feed-a",
            (state as SubscriptionSettingsUiState.Loading).feedId,
        )
        // 後段: Flow を流すと Visible に遷移する
        gateRepo.emit(sub)
        assertTrue(vm.uiState.value is SubscriptionSettingsUiState.Visible)
    }

    @Test
    fun `Issue 52 Req 5_3 NotFound から retry で再観測する`() = runTest {
        // Arrange: 最初は空、retry 後に対象が現れる
        val target = newSub(feedId = "feed-a", interval = 60)
        val repo = MutableRepository(initial = emptyList())
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("feed-a")
        assertTrue(vm.uiState.value is SubscriptionSettingsUiState.NotFound)

        // Act: 別経路で subscription が追加された後 retry
        repo.update(listOf(target))
        vm.retry()

        // Assert: Visible に遷移している
        val state = vm.uiState.value
        assertTrue(
            "expected Visible after retry but was $state",
            state is SubscriptionSettingsUiState.Visible,
        )
    }

    // ── フェッチ間隔の初期選択 / 未選択 ───────────────────────────

    @Test
    fun `現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2`() = runTest {
        listOf(30, 60, 180, 360).forEach { interval ->
            val vm = SubscriptionSettingsViewModel(
                repository = StubRepository(listOf(newSub(feedId = "f", interval = interval))),
            )
            vm.open("f")
            val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
            assertEquals(interval, state.selectedIntervalMinutes)
        }
    }

    @Test
    fun `現在の interval が 4 値以外なら未選択 null になる_Req 2_3`() = runTest {
        // 90 分（30 分刻みだが 4 値外）の場合
        val vm = SubscriptionSettingsViewModel(
            repository = StubRepository(listOf(newSub(feedId = "f", interval = 90))),
        )
        vm.open("f")
        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertNull(state.selectedIntervalMinutes)
        assertEquals(false, state.canSave) // 未選択時は保存不可
    }

    @Test
    fun `selectInterval で選択値が更新される 4 値以外は無視`() = runTest {
        val vm = SubscriptionSettingsViewModel(
            repository = StubRepository(listOf(newSub(feedId = "f", interval = 60))),
        )
        vm.open("f")
        vm.selectInterval(180)
        assertEquals(
            180,
            (vm.uiState.value as SubscriptionSettingsUiState.Visible).selectedIntervalMinutes,
        )
        // 4 値外は no-op（180 のまま）
        vm.selectInterval(90)
        assertEquals(
            180,
            (vm.uiState.value as SubscriptionSettingsUiState.Visible).selectedIntervalMinutes,
        )
    }

    @Test
    fun `現在値と同じ選択値なら canSave は false 不要な PUT を避ける`() = runTest {
        val vm = SubscriptionSettingsViewModel(
            repository = StubRepository(listOf(newSub(feedId = "f", interval = 60))),
        )
        vm.open("f")
        // 初期選択は 60、保存ボタンは無効
        assertEquals(
            false,
            (vm.uiState.value as SubscriptionSettingsUiState.Visible).canSave,
        )
    }

    // ── 保存（save） ───────────────────────────────────────────

    @Test
    fun `save 成功で SettingsSaved イベントが流れシートが閉じる_Req 2_4`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60)))
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.selectInterval(180)

        vm.events.test {
            vm.save()
            assertEquals(SubscriptionSettingsEvent.SettingsSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // close 済み
        assertEquals(SubscriptionSettingsUiState.Hidden, vm.uiState.value)
        // Repository が新値で呼ばれた
        assertEquals(180, repo.lastUpdateSettingsInterval)
    }

    @Test
    fun `save 失敗で旧値ロールバック + エラーメッセージ表示 シートは閉じない_Req 2_6 Req 5_2`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60))).apply {
            updateSettingsException = FeedmanException(
                code = "INTERNAL_ERROR",
                errorMessage = "サーバーエラー",
            )
        }
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.selectInterval(180)

        vm.save()

        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        // Req 5.2: 旧値（60）にロールバック
        assertEquals(60, state.selectedIntervalMinutes)
        // Req 2.6 / 5.1: エラーメッセージが設定される
        assertEquals("サーバーエラー", state.errorMessage)
        // Req 2.5: 進行中フラグは false に戻っている
        assertEquals(false, state.saveInProgress)
    }

    @Test
    fun `save 進行中は追加 save を受け付けない_Req 2_5`() = runTest {
        // Arrange: 1 回成功させた後、saveInProgress=true の状態を再現
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60)))
        repo.suspendUpdateSettings = true
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.selectInterval(180)

        vm.save() // 1 回目: suspend されたまま停止
        // saveInProgress = true
        assertEquals(
            true,
            (vm.uiState.value as SubscriptionSettingsUiState.Visible).saveInProgress,
        )
        val callsBefore = repo.updateSettingsCalls

        // Act: 2 回目の save 試行
        vm.save()

        // Assert: 呼び出し回数は増えていない
        assertEquals(callsBefore, repo.updateSettingsCalls)
    }

    // ── 再開（resume） ───────────────────────────────────────

    @Test
    fun `resume showResumeAction は stopped と error 状態でのみ true_Req 3_1 Req 3_4`() = runTest {
        listOf(
            "active" to false,
            "stopped" to true,
            "error" to true,
        ).forEach { (status, expected) ->
            val vm = SubscriptionSettingsViewModel(
                repository = StubRepository(
                    listOf(newSub(feedId = "f", interval = 60, status = status)),
                ),
            )
            vm.open("f")
            val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
            assertEquals("status=$status", expected, state.showResumeAction)
        }
    }

    @Test
    fun `resume 成功で ResumeSucceeded イベントが流れシートは開いたまま_Req 3_3`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60, status = "stopped")))
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")

        vm.events.test {
            vm.resume()
            assertEquals(SubscriptionSettingsEvent.ResumeSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Visible のままで resumeInProgress は false
        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals(false, state.resumeInProgress)
    }

    @Test
    fun `resume 失敗でエラーメッセージのみ表示 状態は変更されない_Req 3_5`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60, status = "stopped"))).apply {
            resumeException = FeedmanException(code = "INTERNAL_ERROR", errorMessage = "再開失敗")
        }
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")

        vm.resume()

        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals("再開失敗", state.errorMessage)
        // Subscription 状態は変わらない（stopped のまま）
        assertEquals("stopped", state.subscription.feedStatus)
    }

    // ── 解除（unsubscribe） ─────────────────────────────────

    @Test
    fun `requestUnsubscribe で確認ダイアログが開く_Req 4_1`() = runTest {
        val vm = SubscriptionSettingsViewModel(
            repository = StubRepository(listOf(newSub(feedId = "f", interval = 60))),
        )
        vm.open("f")

        vm.requestUnsubscribe()

        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals(true, state.confirmUnsubscribeOpen)
    }

    @Test
    fun `cancelUnsubscribe で確認ダイアログを閉じる 解除リクエストは送らない_Req 4_2`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60)))
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.requestUnsubscribe()

        vm.cancelUnsubscribe()

        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals(false, state.confirmUnsubscribeOpen)
        assertEquals(0, repo.unsubscribeCalls)
    }

    @Test
    fun `confirmUnsubscribe 成功で Unsubscribed イベント feedId 付きが流れシートが閉じる_Req 4_3 Req 4_4 Req 4_5`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "feed-a", interval = 60)))
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("feed-a")
        vm.requestUnsubscribe()

        vm.events.test {
            vm.confirmUnsubscribe()
            val ev = awaitItem()
            assertTrue(ev is SubscriptionSettingsEvent.Unsubscribed)
            assertEquals("feed-a", (ev as SubscriptionSettingsEvent.Unsubscribed).feedId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SubscriptionSettingsUiState.Hidden, vm.uiState.value)
        assertEquals(1, repo.unsubscribeCalls)
    }

    @Test
    fun `confirmUnsubscribe 失敗で エラーメッセージ表示 シートは開いたまま_Req 4_7`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60))).apply {
            unsubscribeException = FeedmanException(code = "INTERNAL_ERROR", errorMessage = "解除失敗")
        }
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.requestUnsubscribe()

        vm.confirmUnsubscribe()

        val state = vm.uiState.value as SubscriptionSettingsUiState.Visible
        assertEquals("解除失敗", state.errorMessage)
        assertEquals(false, state.unsubscribeInProgress)
    }

    @Test
    fun `confirmUnsubscribe 確認ダイアログ未表示のとき no-op になり DELETE を送らない_Req 4_1`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60)))
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")

        // ダイアログを開かずに confirm を呼ぶ → no-op
        vm.confirmUnsubscribe()

        assertEquals(0, repo.unsubscribeCalls)
    }

    // ── 認証切れ（UNAUTHORIZED）───────────────────────────

    @Test
    fun `save 中に 401 が出ると UnauthorizedRedirect が流れシートが閉じる_Req 5_3`() = runTest {
        val repo = StubRepository(listOf(newSub(feedId = "f", interval = 60))).apply {
            updateSettingsException = FeedmanException(
                code = "UNAUTHORIZED",
                errorMessage = "ログインが必要です",
            )
        }
        val vm = SubscriptionSettingsViewModel(repository = repo)
        vm.open("f")
        vm.selectInterval(180)

        vm.events.test {
            vm.save()
            assertEquals(SubscriptionSettingsEvent.UnauthorizedRedirect, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SubscriptionSettingsUiState.Hidden, vm.uiState.value)
    }

    // ── ヘルパー ─────────────────────────────────────

    private fun newSub(
        feedId: String,
        interval: Int,
        status: String = "active",
        id: String = "sub-$feedId",
    ): Subscription = Subscription(
        id = id,
        userId = "u",
        feedId = feedId,
        feedTitle = "title-$feedId",
        feedUrl = "https://example.com/$feedId",
        faviconUrl = null,
        fetchIntervalMinutes = interval,
        feedStatus = status,
        errorMessage = if (status == "error") "サンプルエラー" else null,
        unreadCount = 3,
        createdAt = "2025-05-01T09:00:00Z",
    )

    /**
     * テスト用の最小 [SubscriptionRepository]。`observeFeed` は内部リストから feedId 一致の
     * Subscription を流す。更新系（updateSettings / unsubscribe / resume）は呼び出し回数の
     * カウンタと、任意の例外を投げるフックを持つ。
     */
    private class StubRepository(
        initial: List<Subscription>,
    ) : SubscriptionRepository {

        private val list: MutableStateFlow<List<Subscription>> = MutableStateFlow(initial)

        var updateSettingsCalls: Int = 0
        var lastUpdateSettingsInterval: Int? = null
        var updateSettingsException: Throwable? = null
        var suspendUpdateSettings: Boolean = false

        var resumeCalls: Int = 0
        var resumeException: Throwable? = null

        var unsubscribeCalls: Int = 0
        var unsubscribeException: Throwable? = null

        override fun observeSubscriptions(): Flow<List<Subscription>> = list.asStateFlow()

        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)

        override suspend fun refresh() {}

        override suspend fun updateSettings(
            subscriptionId: String,
            fetchIntervalMinutes: Int,
        ): Subscription {
            updateSettingsCalls += 1
            lastUpdateSettingsInterval = fetchIntervalMinutes
            if (suspendUpdateSettings) {
                // 完了しない suspend をシミュレートするため無限待機
                kotlinx.coroutines.awaitCancellation()
            }
            updateSettingsException?.let { throw it }
            val updated = list.value.map {
                if (it.id == subscriptionId) it.copy(fetchIntervalMinutes = fetchIntervalMinutes) else it
            }
            list.value = updated
            return updated.first { it.id == subscriptionId }
        }

        override suspend fun resume(subscriptionId: String): Subscription {
            resumeCalls += 1
            resumeException?.let { throw it }
            val updated = list.value.map {
                if (it.id == subscriptionId) it.copy(feedStatus = "active", errorMessage = null) else it
            }
            list.value = updated
            return updated.first { it.id == subscriptionId }
        }

        override suspend fun unsubscribe(subscriptionId: String) {
            unsubscribeCalls += 1
            unsubscribeException?.let { throw it }
            list.value = list.value.filterNot { it.id == subscriptionId }
        }
    }

    /**
     * Issue #52 Req 5.1 専用テスト用 [SubscriptionRepository]。`observeFeed` は手動で
     * [emit] が呼ばれるまで何も流さない（Flow は collect 中だが suspending state）。
     */
    @Suppress("UNUSED_PARAMETER")
    private class GateRepository(
        target: Subscription,
    ) : SubscriptionRepository {

        private val list: MutableStateFlow<List<Subscription>> = MutableStateFlow(emptyList())

        fun emit(sub: Subscription) {
            list.value = listOf(sub)
        }

        override fun observeSubscriptions(): Flow<List<Subscription>> = list.asStateFlow()

        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)

        override suspend fun refresh() {}

        // `observeFeed` 既定実装は observeSubscriptions().mapToSingleByFeedId(feedId) を返すため、
        // 上記 list が emit を待っている間は null すら流さない動作になることを意図する。
        // しかし StateFlow は initial value（emptyList）を即座に流すため、collect の最初の値で
        // mapToSingleByFeedId が null を返してしまう。テスト用にここでは observeFeed を override
        // して、emit が呼ばれるまで何も流さない振る舞いをエミュレートする。
        override fun observeFeed(feedId: String): Flow<Subscription?> =
            list.asStateFlow().let { state ->
                kotlinx.coroutines.flow.flow {
                    state.collect { subs ->
                        if (subs.isEmpty()) {
                            // 空のときは何も流さず Loading を維持させる
                            return@collect
                        }
                        emit(subs.firstOrNull { it.feedId == feedId })
                    }
                }
            }
    }

    /**
     * Issue #52 Req 5.3 用テスト helper。`update` で外部から購読リストを書き換え可能。
     */
    private class MutableRepository(
        initial: List<Subscription>,
    ) : SubscriptionRepository {

        private val list: MutableStateFlow<List<Subscription>> = MutableStateFlow(initial)

        fun update(subs: List<Subscription>) {
            list.value = subs
        }

        override fun observeSubscriptions(): Flow<List<Subscription>> = list.asStateFlow()

        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)

        override suspend fun refresh() {}
    }
}
