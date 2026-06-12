package com.feedman.android.feature.subscriptionsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 購読設定シート（Issue #43 / SPEC §5.6）の ViewModel。
 *
 * シートはアプリ全体で 1 つ起動できる前提（AppShell 配下に配置）で、対象フィードを
 * 切り替えるたびに [open] でリセットする。
 *
 * 状態モデル:
 * - [uiState]: シートの表示状態（[SubscriptionSettingsUiState.Hidden] / [SubscriptionSettingsUiState.Visible]）
 * - [events]: 保存成功・再開成功・解除成功・認証切れの one-shot 通知
 *
 * Repository 観測:
 * - [open] で feedId を受け取った時点でその feedId の `observeFeed` を購読し、
 *   Subscription が更新されるたびに `uiState.subscription` を最新化する（Req 1.3 / 3.4）。
 *   購読は別 feedId を [open] したとき / [close] したときに自動でキャンセルする。
 *
 * 進行中フラグは「同時に 1 オペレーションのみ」運用（Req 2.5 / 4.6: 二重実行抑止）:
 * - save と unsubscribe は互いに排他（どちらかが進行中なら他方を受け付けない）
 * - resume は短時間で完了する想定で進行中フラグのみ持つ
 */
@HiltViewModel
class SubscriptionSettingsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<SubscriptionSettingsUiState> =
        MutableStateFlow(SubscriptionSettingsUiState.Hidden)
    val uiState: StateFlow<SubscriptionSettingsUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<SubscriptionSettingsEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: SharedFlow<SubscriptionSettingsEvent> = _events.asSharedFlow()

    /** [open] で起動する Subscription 観測ジョブ。別 feedId で再 open 時にキャンセルする。 */
    private var observeJob: Job? = null

    /**
     * Req 1.1 / 1.2: ドロワー or フィード別画面の設定導線から呼ばれて、対象フィードを
     * シートで開く。同じインスタンスが別フィードで再呼出されたら観測対象を切り替える。
     *
     * 対象 feedId が観測時点で repository 内のリストに無い場合（refresh 未完了 / 別の理由）
     * 観測が `null` を流し続けるため、UI 側で skeleton 表示にフォールバックする運用を想定する
     * （本実装では `Visible` 状態は最初に non-null Subscription を観測したタイミングで初めて
     * 構築される。それまでは Hidden のまま）。
     *
     * @param feedId 対象 [Subscription.feedId]
     */
    fun open(feedId: String) {
        // 既存の観測をキャンセル（別 feedId への切替に対応）
        observeJob?.cancel()
        // いったん Hidden に戻して再構築する（前回の操作中フラグ等を引き継がない）
        _uiState.value = SubscriptionSettingsUiState.Hidden
        observeJob = viewModelScope.launch {
            repository.observeFeed(feedId).collect { sub ->
                if (sub == null) {
                    // 対象が見つからない（解除完了などで消えた可能性）。Hidden に戻す。
                    _uiState.value = SubscriptionSettingsUiState.Hidden
                    return@collect
                }
                _uiState.update { current ->
                    when (current) {
                        is SubscriptionSettingsUiState.Visible -> {
                            // 既に Visible 状態 → subscription のみ最新化（ユーザー操作中の
                            // 一時状態は維持。例: selectedIntervalMinutes は引き続きユーザーの選択値）
                            current.copy(subscription = sub)
                        }
                        SubscriptionSettingsUiState.Hidden -> SubscriptionSettingsUiState.Visible(
                            subscription = sub,
                            selectedIntervalMinutes =
                                SubscriptionSettingsUiState.resolveInitialSelection(
                                    sub.fetchIntervalMinutes,
                                ),
                        )
                    }
                }
            }
        }
    }

    /** Req 1.4: クローズ操作（ドラッグ下げ / スクリム / バック）。 */
    fun close() {
        observeJob?.cancel()
        observeJob = null
        _uiState.value = SubscriptionSettingsUiState.Hidden
    }

    /**
     * Req 2.2 / 2.3: フェッチ間隔セグメントの選択。
     *
     * 30 / 60 / 180 / 360 以外の値は受け付けない（Req 2.1 の固定 4 値外）。
     */
    fun selectInterval(minutes: Int) {
        if (minutes !in SubscriptionSettingsUiState.ALLOWED_INTERVAL_MINUTES) return
        _uiState.update { current ->
            when (current) {
                is SubscriptionSettingsUiState.Visible -> current.copy(
                    selectedIntervalMinutes = minutes,
                    // 選択変更したらエラーメッセージはクリア（ユーザーが次のアクションを試行）
                    errorMessage = null,
                )
                SubscriptionSettingsUiState.Hidden -> current
            }
        }
    }

    /**
     * Req 2.4 / 2.5 / 2.6 / 5.1 / 5.2: 設定保存（PUT）。
     *
     * - 進行中フラグを true にして二重実行を抑止
     * - 成功: [SubscriptionSettingsEvent.SettingsSaved] を流し、シートを閉じる（Req 2.4）
     * - 失敗: エラーメッセージを設定し、選択値を旧値（subscription.fetchIntervalMinutes）に
     *   戻す（Req 2.6 / 5.2: 楽観的変更をロールバック）
     * - 401（UNAUTHORIZED）: [SubscriptionSettingsEvent.UnauthorizedRedirect] を流し、
     *   シートを閉じる（Req 5.3）
     */
    fun save() {
        val current = _uiState.value as? SubscriptionSettingsUiState.Visible ?: return
        if (!current.canSave) return
        val targetInterval = current.selectedIntervalMinutes ?: return
        val previousInterval = current.subscription.fetchIntervalMinutes

        _uiState.value = current.copy(saveInProgress = true, errorMessage = null)
        viewModelScope.launch {
            try {
                repository.updateSettings(
                    subscriptionId = current.subscription.id,
                    fetchIntervalMinutes = targetInterval,
                )
                // Req 2.4: 成功で snackbar 用イベント + シートを閉じる
                _events.emit(SubscriptionSettingsEvent.SettingsSaved)
                close()
            } catch (e: FeedmanException) {
                handleFailureOrUnauthorized(
                    exception = e,
                    rollback = { v ->
                        v.copy(
                            saveInProgress = false,
                            // Req 5.2: 楽観的変更をロールバック（旧値に戻す）
                            selectedIntervalMinutes =
                                SubscriptionSettingsUiState.resolveInitialSelection(previousInterval),
                            errorMessage = resolveMessage(e),
                        )
                    },
                )
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is SubscriptionSettingsUiState.Visible) {
                        state.copy(
                            saveInProgress = false,
                            selectedIntervalMinutes =
                                SubscriptionSettingsUiState.resolveInitialSelection(previousInterval),
                            errorMessage = e.message?.takeIf { it.isNotBlank() }
                                ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /**
     * Req 3.2 / 3.3 / 3.5 / 5.1 / 5.3: 再開アクション。
     *
     * - 進行中フラグを true
     * - 成功: [SubscriptionSettingsEvent.ResumeSucceeded] を流す（Req 3.3）。Subscription は
     *   Repository 内部で active 化されているので observeFeed 経由で UI に反映される
     * - 失敗: エラーメッセージのみ設定。状態表示は変更しない（Req 3.5）
     * - 401: [SubscriptionSettingsEvent.UnauthorizedRedirect]
     */
    fun resume() {
        val current = _uiState.value as? SubscriptionSettingsUiState.Visible ?: return
        if (current.resumeInProgress || current.saveInProgress || current.unsubscribeInProgress) return
        if (!current.showResumeAction) return

        _uiState.value = current.copy(resumeInProgress = true, errorMessage = null)
        viewModelScope.launch {
            try {
                repository.resume(current.subscription.id)
                _events.emit(SubscriptionSettingsEvent.ResumeSucceeded)
                _uiState.update { state ->
                    if (state is SubscriptionSettingsUiState.Visible) {
                        state.copy(resumeInProgress = false)
                    } else state
                }
            } catch (e: FeedmanException) {
                handleFailureOrUnauthorized(
                    exception = e,
                    rollback = { v ->
                        v.copy(resumeInProgress = false, errorMessage = resolveMessage(e))
                    },
                )
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is SubscriptionSettingsUiState.Visible) {
                        state.copy(
                            resumeInProgress = false,
                            errorMessage = e.message?.takeIf { it.isNotBlank() }
                                ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                        )
                    } else state
                }
            }
        }
    }

    /** Req 4.1: 解除確認ダイアログを開く（明示的なユーザー操作）。 */
    fun requestUnsubscribe() {
        _uiState.update { current ->
            if (current is SubscriptionSettingsUiState.Visible) {
                current.copy(confirmUnsubscribeOpen = true, errorMessage = null)
            } else current
        }
    }

    /** Req 4.2 / NFR 3.2: 解除確認ダイアログをキャンセルする（外部タップ / システム戻る）。 */
    fun cancelUnsubscribe() {
        _uiState.update { current ->
            if (current is SubscriptionSettingsUiState.Visible) {
                current.copy(confirmUnsubscribeOpen = false)
            } else current
        }
    }

    /**
     * Req 4.3 / 4.4 / 4.5 / 4.6 / 4.7 / 5.3: 解除を確定する（DELETE）。
     *
     * - 進行中フラグを true（Req 4.6 二重実行抑止）
     * - 成功: [SubscriptionSettingsEvent.Unsubscribed] を流す（Req 4.4 ドロワー除去は
     *   Repository 内部で完了済み / Req 4.5 画面遷移は UI 側で feedId を照合して判断）
     *   → close() でシートを閉じる
     * - 失敗: エラーメッセージを設定（リスト・画面遷移は変更しない / Req 4.7）
     * - 401: UnauthorizedRedirect
     */
    fun confirmUnsubscribe() {
        val current = _uiState.value as? SubscriptionSettingsUiState.Visible ?: return
        if (current.unsubscribeInProgress || current.saveInProgress) return
        if (!current.confirmUnsubscribeOpen) return

        val targetFeedId = current.subscription.feedId
        val targetSubscriptionId = current.subscription.id
        _uiState.value = current.copy(
            unsubscribeInProgress = true,
            confirmUnsubscribeOpen = false,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                repository.unsubscribe(targetSubscriptionId)
                _events.emit(SubscriptionSettingsEvent.Unsubscribed(feedId = targetFeedId))
                close()
            } catch (e: FeedmanException) {
                handleFailureOrUnauthorized(
                    exception = e,
                    rollback = { v ->
                        v.copy(unsubscribeInProgress = false, errorMessage = resolveMessage(e))
                    },
                )
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is SubscriptionSettingsUiState.Visible) {
                        state.copy(
                            unsubscribeInProgress = false,
                            errorMessage = e.message?.takeIf { it.isNotBlank() }
                                ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                        )
                    } else state
                }
            }
        }
    }

    /**
     * FeedmanException が UNAUTHORIZED のときは認証切れフローへ。それ以外は [rollback] で
     * UI 状態を巻き戻す（Req 5.1 / 5.2 / 5.3）。
     */
    private suspend fun handleFailureOrUnauthorized(
        exception: FeedmanException,
        rollback: (SubscriptionSettingsUiState.Visible) -> SubscriptionSettingsUiState.Visible,
    ) {
        if (exception.code == CODE_UNAUTHORIZED) {
            _events.emit(SubscriptionSettingsEvent.UnauthorizedRedirect)
            close()
            return
        }
        _uiState.update { state ->
            if (state is SubscriptionSettingsUiState.Visible) rollback(state) else state
        }
    }

    /**
     * Req 5.1: サーバー応答のエラーメッセージ（ない場合は汎用メッセージ）。
     */
    private fun resolveMessage(e: FeedmanException): String =
        e.errorMessage.ifBlank {
            when (e.code) {
                FeedmanException.CODE_NETWORK_ERROR -> FeedmanException.FALLBACK_NETWORK_MESSAGE
                else -> FeedmanException.FALLBACK_UNKNOWN_MESSAGE
            }
        }

    private companion object {
        /** SPEC §4.3 認証切れ応答の code。 */
        const val CODE_UNAUTHORIZED: String = "UNAUTHORIZED"
    }
}
