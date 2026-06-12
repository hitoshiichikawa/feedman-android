@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.feedman.android.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.ui.FeedmanSheet

/** アカウントシートの testTag。 */
const val ACCOUNT_SHEET_TEST_TAG: String = "feature.account.AccountSheet"

/**
 * アカウントシート（Issue #49 / SPEC §5.7）。
 *
 * `design/mobile/fm-sheets.jsx` の `FMAccountSheet` を Compose で再現する。シート本体は
 * 共通 [FeedmanSheet] でラップし、内側に以下のセクションを並べる:
 *
 * 1. ユーザー領域（アバター + ラベル "You" + email or ローディング / エラー）+ 閉じるアイコン
 * 2. 区切り線（HorizontalDivider）
 *
 * ログアウトボタン（#50）と退会フロー（#51）は別 Issue の領分のため本実装には含めない。
 *
 * @param onUnauthorized 認証切れ時のコールバック。実際のログイン画面遷移は
 *   SessionStateProvider 観測経路（AppShell）で行われるため、本コールバックは
 *   呼び出し側で追加処理が必要ないなら no-op で渡す（Req 5.2 / 5.3）。
 */
@Composable
fun AccountSheet(
    onUnauthorized: () -> Unit,
    viewModel: AccountSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AccountSheetEvent.UnauthorizedRedirect -> onUnauthorized()
            }
        }
    }

    val state = uiState
    if (state !is AccountSheetUiState.Visible) return

    // Req 1.x: システム戻る操作で閉じる
    BackHandler(enabled = true) { viewModel.close() }

    FeedmanSheet(
        onDismissRequest = { viewModel.close() },
        label = stringResource(R.string.sheet_account_placeholder_title),
        modifier = Modifier.testTag(ACCOUNT_SHEET_TEST_TAG),
    ) {
        AccountSheetBody(
            state = state,
            onClose = viewModel::close,
            onRetry = viewModel::retry,
            onLogout = viewModel::logout,
            onStartDeletion = viewModel::startDeletion,
            onProceedToFinalConfirm = viewModel::proceedToFinalConfirm,
            onCancelDeletion = viewModel::cancelDeletion,
            onConfirmDeletion = viewModel::confirmDeletion,
        )
    }
}

/**
 * ステートレスなシート本体（テスト / プレビュー流用しやすい）。
 *
 * @param onLogout Issue #50 Req 1.2: ログアウトボタンタップのコールバック。
 *   進行中の二度押しは ViewModel 側でも no-op となるが、UI も `enabled = !logoutInProgress`
 *   でガードする。
 */
@Composable
internal fun AccountSheetBody(
    state: AccountSheetUiState.Visible,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onStartDeletion: () -> Unit,
    onProceedToFinalConfirm: () -> Unit,
    onCancelDeletion: () -> Unit,
    onConfirmDeletion: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountSheetHeader(
            loadState = state.loadState,
            onClose = onClose,
            onRetry = onRetry,
        )
        HorizontalDivider(color = MaterialTheme.feedmanColors.border)
        // Issue #50 Req 1.1 / 1.2 / 1.3 / 1.4: ログアウトボタン領域
        // Issue #51 Req 3.3: 退会フロー中はログアウトを disabled にする
        AccountSheetLogoutSection(
            logoutInProgress = state.logoutInProgress,
            deletionInFlight = state.deletion.isInFlight(),
            onLogout = onLogout,
        )
        // Issue #51 Req 1.1 / 1.2: 退会導線（破壊的操作）
        AccountSheetDeleteSection(
            deletion = state.deletion,
            logoutInProgress = state.logoutInProgress,
            onStartDeletion = onStartDeletion,
        )
    }

    // Issue #51 Req 1.3 / 2.1 / 2.2 / 2.3 / 2.4 / 2.5 / 3.1 / 5.4: 二段確認ダイアログ
    when (state.deletion) {
        AccountSheetUiState.DeletionState.Idle -> Unit
        AccountSheetUiState.DeletionState.ConfirmExplanation -> {
            AccountSheetDeleteExplanationDialog(
                onCancel = onCancelDeletion,
                onProceed = onProceedToFinalConfirm,
            )
        }
        AccountSheetUiState.DeletionState.ConfirmFinal -> {
            AccountSheetDeleteFinalConfirmDialog(
                inProgress = false,
                onCancel = onCancelDeletion,
                onConfirm = onConfirmDeletion,
            )
        }
        AccountSheetUiState.DeletionState.InProgress -> {
            // Req 3.1 / 3.2 / NFR 1.1: 最終確認ダイアログを進行中状態のまま保持する
            // （確定ボタン disabled + ローディング表示）。Req 3.2 によりキャンセル不可。
            AccountSheetDeleteFinalConfirmDialog(
                inProgress = true,
                onCancel = onCancelDeletion, // ViewModel 側で InProgress 中は no-op
                onConfirm = onConfirmDeletion, // ViewModel 側で多重起動を防ぐ
            )
        }
        is AccountSheetUiState.DeletionState.Error -> {
            AccountSheetDeleteErrorDialog(
                message = state.deletion.message,
                onDismiss = onCancelDeletion,
            )
        }
    }
}

/**
 * Issue #51: 退会フロー中（確認段 + 進行中）かを返すヘルパー。Req 3.3 のログアウト disabled
 * 判定に使う。Error 状態は確認段に再入できる文脈であり「退会フロー中」と見なさない。
 */
private fun AccountSheetUiState.DeletionState.isInFlight(): Boolean = when (this) {
    AccountSheetUiState.DeletionState.ConfirmExplanation,
    AccountSheetUiState.DeletionState.ConfirmFinal,
    AccountSheetUiState.DeletionState.InProgress -> true
    AccountSheetUiState.DeletionState.Idle,
    is AccountSheetUiState.DeletionState.Error -> false
}

/**
 * Issue #50 Req 1.1〜1.4: ログアウト操作領域。
 *
 * - Req 1.1: ログアウトボタン（TextButton）を常時表示
 * - Req 1.2: タップで [onLogout] を 1 回呼び出す
 * - Req 1.3: `logoutInProgress = true` のときボタンを disabled にする（多重起動防止）
 * - Req 1.4 / NFR 1.1: 進行中の視覚表現として [CircularProgressIndicator] を併置する
 *
 * シートを閉じる導線（[onClose]）と独立した行に配置する。プロトタイプ FMAccountSheet 上では
 * フッタ位置にログアウト操作が置かれる想定だが、本実装ではシート上段（ユーザー情報の下、
 * 区切り線直下）に置き、Material 3 の TextButton で表現する（後続 Issue でデザインの
 * 細部調整が入る場合に最小差分で済むよう、現時点ではミニマルな配置とする）。
 */
@Composable
private fun AccountSheetLogoutSection(
    logoutInProgress: Boolean,
    deletionInFlight: Boolean,
    onLogout: () -> Unit,
) {
    // Issue #51 Req 3.3: 退会フロー中（確認段 / 進行中）はログアウトを disabled にする
    val disabled = logoutInProgress || deletionInFlight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(ACCOUNT_SHEET_LOGOUT_ROW_TEST_TAG)
            .semantics {
                if (logoutInProgress) {
                    // NFR 1.1: 進行中であることをスクリーンリーダーに伝える
                    liveRegion = LiveRegionMode.Polite
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onLogout,
            enabled = !disabled,
            modifier = Modifier
                .sizeIn(
                    minHeight = MaterialTheme.feedmanDimens.minTapTarget,
                )
                .testTag(ACCOUNT_SHEET_LOGOUT_BUTTON_TEST_TAG),
        ) {
            Text(
                text = stringResource(R.string.account_sheet_logout_button),
                color = if (disabled) {
                    MaterialTheme.feedmanColors.mutedFg
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (logoutInProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .testTag(ACCOUNT_SHEET_LOGOUT_PROGRESS_TEST_TAG)
                    .semantics {
                        contentDescription = ""
                    },
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.account_sheet_logout_in_progress),
                color = MaterialTheme.feedmanColors.mutedFg,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** ログアウト操作領域全体の testTag（テスト用 anchor）。 */
const val ACCOUNT_SHEET_LOGOUT_ROW_TEST_TAG: String = "feature.account.AccountSheet.logout.row"

/** ログアウトボタン自体の testTag。 */
const val ACCOUNT_SHEET_LOGOUT_BUTTON_TEST_TAG: String =
    "feature.account.AccountSheet.logout.button"

/** ログアウト進行中インジケータの testTag。 */
const val ACCOUNT_SHEET_LOGOUT_PROGRESS_TEST_TAG: String =
    "feature.account.AccountSheet.logout.progress"

/** Issue #51 退会導線ボタンの testTag。 */
const val ACCOUNT_SHEET_DELETE_BUTTON_TEST_TAG: String =
    "feature.account.AccountSheet.delete.button"

/** Issue #51 第 1 段 説明ダイアログの testTag。 */
const val ACCOUNT_SHEET_DELETE_EXPLAIN_DIALOG_TEST_TAG: String =
    "feature.account.AccountSheet.delete.dialog.explain"

/** Issue #51 第 2 段 最終確認ダイアログの testTag。 */
const val ACCOUNT_SHEET_DELETE_FINAL_DIALOG_TEST_TAG: String =
    "feature.account.AccountSheet.delete.dialog.final"

/** Issue #51 失敗エラーダイアログの testTag。 */
const val ACCOUNT_SHEET_DELETE_ERROR_DIALOG_TEST_TAG: String =
    "feature.account.AccountSheet.delete.dialog.error"

/** Issue #51 進行中インジケータの testTag（最終確認ダイアログ内）。 */
const val ACCOUNT_SHEET_DELETE_PROGRESS_TEST_TAG: String =
    "feature.account.AccountSheet.delete.progress"

/**
 * Issue #51 Req 1.1 / 1.2: 退会導線セクション。
 *
 * - Req 1.1: シート表示中は常に表示
 * - Req 1.2: 破壊的操作と識別できる視覚表現（warning/error カラー）
 * - Req 3.3 補強: ログアウト進行中 / 退会フロー進行中はボタンを disabled にする
 *   （UI 側のガード。ViewModel 側でも startDeletion が no-op で守られる）
 */
@Composable
private fun AccountSheetDeleteSection(
    deletion: AccountSheetUiState.DeletionState,
    logoutInProgress: Boolean,
    onStartDeletion: () -> Unit,
) {
    // 退会ボタンは Idle / Error のときのみ通常表示。確認段 / 進行中は disabled とする
    // （確認段ではダイアログ表示中で、進行中はサーバー応答待ち）。
    val enabledForStart = when (deletion) {
        AccountSheetUiState.DeletionState.Idle,
        is AccountSheetUiState.DeletionState.Error -> true
        AccountSheetUiState.DeletionState.ConfirmExplanation,
        AccountSheetUiState.DeletionState.ConfirmFinal,
        AccountSheetUiState.DeletionState.InProgress -> false
    } && !logoutInProgress

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onStartDeletion,
            enabled = enabledForStart,
            modifier = Modifier
                .sizeIn(minHeight = MaterialTheme.feedmanDimens.minTapTarget)
                .testTag(ACCOUNT_SHEET_DELETE_BUTTON_TEST_TAG),
        ) {
            Text(
                text = stringResource(R.string.account_sheet_delete_button),
                // Req 1.2: 破壊的操作として error カラーで表現
                color = if (enabledForStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.feedmanColors.mutedFg
                },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Issue #51 Req 1.3 / 2.1 / 2.2 / 2.5: 第 1 段 説明ダイアログ。
 *
 * - Req 2.1: 全購読・既読/スター状態が削除される / 取り消せない旨を提示
 * - Req 2.2: 「次へ進む」「キャンセル」両操作を提示
 * - Req 1.4: 本ダイアログでは DELETE を送らない
 * - Req 2.5: ダイアログ外タップ / システム戻る → cancel
 */
@Composable
private fun AccountSheetDeleteExplanationDialog(
    onCancel: () -> Unit,
    onProceed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(ACCOUNT_SHEET_DELETE_EXPLAIN_DIALOG_TEST_TAG),
        title = {
            Text(text = stringResource(R.string.account_sheet_delete_confirm_title))
        },
        text = {
            Text(text = stringResource(R.string.account_sheet_delete_confirm_message))
        },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text(
                    text = stringResource(R.string.account_sheet_delete_next),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.account_sheet_delete_cancel))
            }
        },
    )
}

/**
 * Issue #51 Req 2.3 / 2.4 / 2.5 / 3.1 / 3.2 / NFR 1.1: 第 2 段 最終確認ダイアログ。
 *
 * - `inProgress = false`: 通常表示（「退会を実行する」「キャンセル」両ボタン活性）
 * - `inProgress = true`: 進行中表示（確定ボタン disabled + ローディング、キャンセル disabled / Req 3.2）
 */
@Composable
private fun AccountSheetDeleteFinalConfirmDialog(
    inProgress: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        // Req 3.2: 進行中は外部タップ / システム戻るでもキャンセルしない
        onDismissRequest = { if (!inProgress) onCancel() },
        modifier = Modifier
            .testTag(ACCOUNT_SHEET_DELETE_FINAL_DIALOG_TEST_TAG)
            .semantics {
                if (inProgress) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        title = {
            Text(text = stringResource(R.string.account_sheet_delete_final_title))
        },
        text = {
            Text(text = stringResource(R.string.account_sheet_delete_final_message))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress, // Req 3.2: 二重送信抑止
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .testTag(ACCOUNT_SHEET_DELETE_PROGRESS_TEST_TAG)
                            .semantics {
                                contentDescription = ""
                            },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.account_sheet_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !inProgress) {
                Text(text = stringResource(R.string.account_sheet_delete_cancel))
            }
        },
    )
}

/**
 * Issue #51 Req 5.4 / 5.5: 退会失敗エラーダイアログ。
 *
 * - 失敗 message を表示し、OK で Idle に戻す
 * - ユーザーは再度退会ボタンから二段確認をやり直せる（Req 5.5）
 */
@Composable
private fun AccountSheetDeleteErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .testTag(ACCOUNT_SHEET_DELETE_ERROR_DIALOG_TEST_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        title = {
            Text(text = stringResource(R.string.account_sheet_delete_error))
        },
        text = {
            Text(
                text = message.ifBlank {
                    stringResource(R.string.account_sheet_delete_error)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK")
            }
        },
    )
}

/**
 * ユーザー領域 + 閉じるアイコン（プロトタイプ FMAccountSheet の上段に相当）。
 *
 * - Req 2.3: 見出しラベル "You" を常時表示
 * - Req 2.1: Loaded 状態で email を表示
 * - Req 2.2: email が空 / blank なら代替文言を表示
 * - Req 3.1 / 3.2: Loading のときはインジケータ + email を出さない
 * - Req 4.1: Error のときはメッセージ + 再試行ボタン
 */
@Composable
private fun AccountSheetHeader(
    loadState: AccountSheetUiState.LoadState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // アバター（プロトタイプの user アイコン）— muted は Material 3 では surfaceVariant にマップ
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.account_sheet_avatar_description),
                tint = feedman.mutedFg,
                modifier = Modifier.size(22.dp),
            )
        }

        // ユーザー情報領域
        Column(modifier = Modifier.weight(1f)) {
            // Req 2.3: 見出しラベル "You"（常時固定文言）
            Text(
                text = stringResource(R.string.account_sheet_user_label),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            AccountSheetUserStatusLine(
                loadState = loadState,
                onRetry = onRetry,
            )
        }

        // 閉じるアイコン
        IconButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(
                minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.account_sheet_close_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * ユーザー領域の 2 行目（email / loading / error）。
 *
 * - Loading: 小さい CircularProgressIndicator
 * - Loaded: email（空のときは代替文言 / Req 2.2）
 * - Error: エラーメッセージ + 再試行 TextButton（Req 4.1 / 4.2）
 */
@Composable
private fun AccountSheetUserStatusLine(
    loadState: AccountSheetUiState.LoadState,
    onRetry: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    when (loadState) {
        AccountSheetUiState.LoadState.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics {
                    // NFR 1.2: 読み上げ可能
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(14.dp)
                        .semantics {
                            contentDescription = ""
                        },
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.account_sheet_loading_description),
                    color = feedman.mutedFg,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        is AccountSheetUiState.LoadState.Loaded -> {
            // Req 2.1 / 2.2: email を表示。空 or blank なら代替文言。
            val emailText = if (loadState.user.email.isNotBlank()) {
                loadState.user.email
            } else {
                stringResource(R.string.account_sheet_email_missing)
            }
            Text(
                text = emailText,
                color = feedman.mutedFg,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
        is AccountSheetUiState.LoadState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(
                    text = loadState.message.ifBlank {
                        stringResource(R.string.account_sheet_load_error)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = onRetry,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.account_sheet_retry),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
