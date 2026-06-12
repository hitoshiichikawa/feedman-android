package com.feedman.android.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * 通知ヘルパ（Issue #28 / Req 5.1〜5.5 / NFR 2.1）。
 *
 * Material 3 [SnackbarHostState] をラップし、呼び出し側からの文字列メッセージのみで
 * スナックバー / トースト相当の通知を表示する。設計方針:
 *
 * - **Req 5.1**: [SnackbarHostState.showSnackbar] により短時間表示をスケジュール
 * - **Req 5.2**: [SnackbarHostState] が同時表示 1 件・順次キューを既定で保証する。
 *   後続が前を即時置換したい場合は [showAndReplaceCurrent] を使う
 * - **Req 5.3**: [SnackbarDuration.Short] により一定時間後に自動消去
 * - **Req 5.4**: ヘルパ内に固定文言を一切持たず、呼び出し側が渡した文字列のみで構成
 * - **Req 5.5**: アクション付き通知は [showWithAction] を使い、ラベル・コールバックを受け取る
 *
 * 視覚的には `design/mobile/fm-sheets.jsx` の `FMToast` が示す「画面下部・短時間・
 * 単一表示」と同じ意図で、実装は Material 3 Snackbar に委譲する。
 */
object FeedmanSnackbar {

    /**
     * 基本通知（Req 5.1, 5.2, 5.3, 5.4）。
     *
     * @param hostState 通知を表示する [SnackbarHostState]
     * @param message 表示文言（空文字列禁止 / Req 5.4: ヘルパ内に固定文言を持たないため
     *        呼び出し側の責務として非空を要求する）
     * @return [SnackbarResult]（ユーザー操作またはタイムアウト）。`Dismissed` が通常終了
     * @throws IllegalArgumentException [message] が空のとき（Req 5.4 の境界）
     */
    suspend fun show(
        hostState: SnackbarHostState,
        message: String,
    ): SnackbarResult {
        validateMessage(message)
        return hostState.showSnackbar(
            message = message,
            actionLabel = null,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
    }

    /**
     * アクション付き通知（Req 5.5）。
     *
     * 返り値の [SnackbarResult] が [SnackbarResult.ActionPerformed] のときに [onAction] を
     * 呼び出す。アクション付きは利用者の判断を待つため [SnackbarDuration.Long] を採用する
     * （Material 3 推奨の duration ルール）。
     *
     * @param hostState 通知を表示する [SnackbarHostState]
     * @param message 表示文言（空文字列禁止）
     * @param actionLabel アクションボタンの文言（空文字列禁止）
     * @param onAction アクションタップ時のコールバック
     * @throws IllegalArgumentException [message] または [actionLabel] が空のとき
     */
    suspend fun showWithAction(
        hostState: SnackbarHostState,
        message: String,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        validateMessage(message)
        validateActionLabel(actionLabel)
        val result = hostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onAction()
        }
    }

    /**
     * 現行通知を即時 dismiss して新しい通知を表示する（Req 5.2 の「後続が前を置き換える」
     * 側の選択肢）。
     *
     * 既定の [show] は順次キュー（前の通知が終わるまで待機）だが、UI 上の最新性を優先
     * したい場面（楽観的更新の連続など）では本 API を使う。
     */
    suspend fun showAndReplaceCurrent(
        hostState: SnackbarHostState,
        message: String,
    ): SnackbarResult {
        validateMessage(message)
        hostState.currentSnackbarData?.dismiss()
        return hostState.showSnackbar(
            message = message,
            actionLabel = null,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
    }

    /**
     * メッセージ検証（Req 5.4 の境界 / NFR 3.1 で JVM 単体テスト対象）。
     *
     * ヘルパ内に固定文言を持たない方針のため、空文字列は呼び出し側のロジックミスと
     * みなして例外で検出する。null は型システムで禁止。
     */
    internal fun validateMessage(message: String) {
        require(message.isNotBlank()) {
            "Snackbar message must not be blank (Req 5.4: helper holds no fallback text)"
        }
    }

    /** アクションラベル検証（Req 5.5 の境界）。 */
    internal fun validateActionLabel(actionLabel: String) {
        require(actionLabel.isNotBlank()) {
            "Snackbar action label must not be blank (Req 5.5)"
        }
    }
}
