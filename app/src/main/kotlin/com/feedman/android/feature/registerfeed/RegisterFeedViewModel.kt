package com.feedman.android.feature.registerfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.FeedRegistrationRepository
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.ui.UrlValidation
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * フィード登録シート（Issue #44 / SPEC §5.5）の ViewModel。
 *
 * シートはアプリ全体で 1 つ起動できる前提（AppShell 配下に配置）。
 *
 * 状態モデル:
 * - [uiState]: シートの表示状態（[RegisterFeedUiState.Hidden] / [RegisterFeedUiState.Visible]）
 * - [events]: 登録成功の one-shot 通知（UI 側でトースト + シートクローズ）
 *
 * エラー文言の分岐は [RegisterFeedErrorResolver] が担い、本クラスは
 * `strings.xml` 解決済みの [RegisterFeedErrorTexts] を渡す責務のみを持つ。
 * Android 依存（`Context.getString`）は Composable 側で解決して
 * [setErrorTexts] で注入する設計（純粋関数の resolver を JVM 単体テスト可能にする）。
 */
@HiltViewModel
class RegisterFeedViewModel @Inject constructor(
    private val repository: FeedRegistrationRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<RegisterFeedUiState> =
        MutableStateFlow(RegisterFeedUiState.Hidden)
    val uiState: StateFlow<RegisterFeedUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<RegisterFeedEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: SharedFlow<RegisterFeedEvent> = _events.asSharedFlow()

    /**
     * UI 側（Composable）から `strings.xml` 解決済み文言を注入する。
     * Hidden → Visible 遷移時に保持して、エラー文言構築に使う。
     */
    private var errorTexts: RegisterFeedErrorTexts? = null

    /**
     * クライアント側 URL バリデーション失敗時の文言。Composable 側で stringResource() を
     * 解決して注入する。
     */
    private var clientInvalidUrlText: String = "URL の形式が正しくありません"

    /**
     * Composable 側から文言テーブルを注入する（Req 5 系のサーバー文言と Req 2.1 の
     * クライアント文言）。
     */
    fun setErrorTexts(texts: RegisterFeedErrorTexts, clientInvalidUrl: String) {
        this.errorTexts = texts
        this.clientInvalidUrlText = clientInvalidUrl
    }

    /**
     * Req 1.1: シートを開く（入力空で起動）。
     *
     * 既に Visible 状態でも入力をリセットして再オープン扱いとする。
     */
    fun open() {
        _uiState.value = RegisterFeedUiState.Visible()
    }

    /**
     * Req 1.5: シートを閉じる（ドラッグ下げ / スクリム / 閉じるボタン）。
     *
     * Req 3.4: 登録要求の待機中であってもシートを閉じることは可能とし、結果の通知は行わない。
     * 進行中のリクエストは viewModelScope の自然なキャンセル（ViewModel 自身が破棄されない
     * 限り継続）に任せるが、UI 側は閉じた以上結果イベントを受け取らない。
     */
    fun close() {
        _uiState.value = RegisterFeedUiState.Hidden
    }

    /**
     * Req 1.3 / 2.3 / 5.8: 入力欄の更新。
     *
     * 入力変更時にクライアント / サーバー由来エラー文言を解除する（Req 2.3 / 5.8）。
     */
    fun updateUrl(newValue: String) {
        _uiState.update { current ->
            if (current is RegisterFeedUiState.Visible) {
                current.copy(
                    url = newValue,
                    clientErrorMessage = null,
                    serverErrorMessage = null,
                )
            } else current
        }
    }

    /**
     * Req 3.1 / 2.x / 5.x: 送信操作。
     *
     * フロー:
     * 1. Req 2.4: 入力前後の空白を除去
     * 2. Req 2.1: クライアント側バリデーション（http/https 構文）。失敗時は送信せずエラー表示
     * 3. Req 3.2: 送信進行中に切替
     * 4. Repository を呼ぶ
     * 5. 成功 → [RegisterFeedEvent.RegistrationSucceeded] を流して close
     * 6. 失敗 → [RegisterFeedErrorResolver] でユーザー文言を構築し serverErrorMessage に設定
     */
    fun submit() {
        val current = _uiState.value as? RegisterFeedUiState.Visible ?: return
        if (!current.canSubmit) return

        // Req 2.4: 半角空白の trim（kotlin の trim は Unicode 空白も対象）
        val trimmed = current.url.trim()
        // Req 2.1: クライアント側 URL 形式チェック
        val validation = UrlValidation.validate(trimmed)
        if (validation is UrlValidation.ValidationResult.Invalid) {
            _uiState.value = current.copy(
                clientErrorMessage = clientInvalidUrlText,
                serverErrorMessage = null,
            )
            return
        }

        _uiState.value = current.copy(
            submitInProgress = true,
            clientErrorMessage = null,
            serverErrorMessage = null,
        )

        viewModelScope.launch {
            try {
                repository.register(trimmed)
                // Req 4.1: 成功でシートを閉じる
                // Req 4.2: トースト発火（UI 側で表示）
                _events.emit(RegisterFeedEvent.RegistrationSucceeded)
                close()
            } catch (e: FeedmanException) {
                handleServerError(e)
            } catch (e: Exception) {
                // 予期しない例外は汎用フォールバック（Req 5.5）
                handleServerError(
                    FeedmanException(
                        code = FeedmanException.CODE_UNKNOWN_ERROR,
                        errorMessage = e.message?.takeIf { it.isNotBlank() }
                            ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                    ),
                )
            }
        }
    }

    /**
     * Req 5.x / 5.7: エラー文言を設定し、入力欄と送信ボタンを再操作可能な状態に戻す。
     */
    private fun handleServerError(e: FeedmanException) {
        val texts = errorTexts
        val message = if (texts != null) {
            RegisterFeedErrorResolver.resolve(e, texts)
        } else {
            // テキストが未注入の場合（テスト等）はサーバー message を直接使う fallback
            e.errorMessage.ifBlank { FeedmanException.FALLBACK_UNKNOWN_MESSAGE }
        }
        _uiState.update { state ->
            if (state is RegisterFeedUiState.Visible) {
                state.copy(
                    submitInProgress = false,
                    serverErrorMessage = message,
                )
            } else state
        }
    }
}
