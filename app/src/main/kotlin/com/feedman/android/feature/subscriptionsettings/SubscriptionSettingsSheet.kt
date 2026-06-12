@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.feedman.android.feature.subscriptionsettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanColors
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.ui.Favicon
import com.feedman.android.core.ui.FeedmanSheet
import com.feedman.android.core.ui.FeedmanSnackbar

/** 購読設定シートの testTag。 */
const val SUBSCRIPTION_SETTINGS_SHEET_TEST_TAG: String =
    "feature.subscriptionsettings.SubscriptionSettingsSheet"

/**
 * 購読設定シート（Issue #43 / SPEC §5.6）。
 *
 * `design/mobile/fm-sheets.jsx` の `FMSettingsSheet` を Compose で再現する。シート本体は
 * 共通 [FeedmanSheet] でラップし、内側に以下のセクションを並べる:
 *
 * 1. ヘッダ（favicon + フィードタイトル + 未読件数 + 閉じるアイコン）
 * 2. 状態バッジ + エラーメッセージ + 再開アクション（stopped / error のみ / Req 3.1）
 * 3. フェッチ間隔セグメント（30 / 60 / 180 / 360 / Req 2.1）
 * 4. エラーメッセージ表示（直近の操作失敗時 / Req 5.1）
 * 5. 保存ボタン + 購読解除ボタン
 * 6. 解除確認ダイアログ（Req 4.1）
 *
 * @param onUnsubscribed 購読解除成功時のコールバック。`feedId` 引数を受け取り、UI 側で
 *   「現在の画面が当該フィードなら timeline へ退避」（Req 4.5）を判断する。
 * @param onUnauthorized 認証切れ時のコールバック。UI 側でログイン導線へ誘導する（Req 5.3）。
 */
@Composable
fun SubscriptionSettingsSheet(
    onUnsubscribed: (feedId: String) -> Unit,
    onUnauthorized: () -> Unit,
    viewModel: SubscriptionSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.subscription_settings_saved)
    val resumeSucceededMessage = stringResource(R.string.subscription_settings_resume_succeeded)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SubscriptionSettingsEvent.SettingsSaved ->
                    FeedmanSnackbar.show(snackbarHostState, savedMessage)
                SubscriptionSettingsEvent.ResumeSucceeded ->
                    FeedmanSnackbar.show(snackbarHostState, resumeSucceededMessage)
                is SubscriptionSettingsEvent.Unsubscribed -> onUnsubscribed(event.feedId)
                SubscriptionSettingsEvent.UnauthorizedRedirect -> onUnauthorized()
            }
        }
    }

    val state = uiState
    if (state !is SubscriptionSettingsUiState.Visible) return

    // Req 1.4: システム戻る操作で閉じる
    BackHandler(enabled = true) { viewModel.close() }

    FeedmanSheet(
        onDismissRequest = { viewModel.close() },
        label = stringResource(R.string.subscription_settings_sheet_pane_title),
        modifier = Modifier.testTag(SUBSCRIPTION_SETTINGS_SHEET_TEST_TAG),
    ) {
        SubscriptionSettingsSheetBody(
            state = state,
            snackbarHostState = snackbarHostState,
            onClose = viewModel::close,
            onSelectInterval = viewModel::selectInterval,
            onSave = viewModel::save,
            onResume = viewModel::resume,
            onRequestUnsubscribe = viewModel::requestUnsubscribe,
            onCancelUnsubscribe = viewModel::cancelUnsubscribe,
            onConfirmUnsubscribe = viewModel::confirmUnsubscribe,
        )
    }
}

/**
 * ステートレスなシート本体（テスト / プレビュー流用しやすい）。
 */
@Composable
internal fun SubscriptionSettingsSheetBody(
    state: SubscriptionSettingsUiState.Visible,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onSelectInterval: (Int) -> Unit,
    onSave: () -> Unit,
    onResume: () -> Unit,
    onRequestUnsubscribe: () -> Unit,
    onCancelUnsubscribe: () -> Unit,
    onConfirmUnsubscribe: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. ヘッダ
        SubscriptionSettingsHeader(state = state, onClose = onClose)
        HorizontalDivider(color = MaterialTheme.feedmanColors.border)

        // 2. 状態バッジ + 再開
        if (state.showResumeAction) {
            ResumeStatusSection(state = state, onResume = onResume)
            HorizontalDivider(color = MaterialTheme.feedmanColors.border)
        }

        // 3. フェッチ間隔セグメント
        FetchIntervalSection(
            state = state,
            onSelectInterval = onSelectInterval,
        )

        // 4. エラーメッセージ
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // 5. 保存 + 解除アクション
        ActionButtons(
            state = state,
            onSave = onSave,
            onRequestUnsubscribe = onRequestUnsubscribe,
        )

        // Snackbar（保存 / 再開成功通知）
        SnackbarHost(hostState = snackbarHostState)
    }

    // 6. 解除確認ダイアログ
    if (state.confirmUnsubscribeOpen) {
        UnsubscribeConfirmDialog(
            feedTitle = state.subscription.feedTitle,
            inProgress = state.unsubscribeInProgress,
            onCancel = onCancelUnsubscribe,
            onConfirm = onConfirmUnsubscribe,
        )
    }
}

// ── ヘッダ ───────────────────────────────────────────────────────────

@Composable
private fun SubscriptionSettingsHeader(
    state: SubscriptionSettingsUiState.Visible,
    onClose: () -> Unit,
) {
    val sub = state.subscription
    val faviconDescription = stringResource(
        R.string.subscription_settings_favicon_description,
        sub.feedTitle,
    )
    val unreadDescription = stringResource(
        R.string.subscription_settings_unread_description,
        sub.unreadCount,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Favicon(
            faviconValue = sub.faviconUrl,
            feedTitle = sub.feedTitle,
            size = MaterialTheme.feedmanDimens.faviconMedium,
            contentDescription = faviconDescription,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sub.feedTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = unreadDescription,
                color = MaterialTheme.feedmanColors.mutedFg,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        // 閉じるアイコン（Req 1.4）
        IconButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(
                minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.subscription_settings_close_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── 状態バッジ + 再開 ─────────────────────────────────────────

@Composable
private fun ResumeStatusSection(
    state: SubscriptionSettingsUiState.Visible,
    onResume: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    val sub = state.subscription
    val statusLabel = when (sub.feedStatus) {
        SubscriptionSettingsUiState.STATUS_STOPPED ->
            stringResource(R.string.subscription_settings_status_stopped)
        SubscriptionSettingsUiState.STATUS_ERROR ->
            stringResource(R.string.subscription_settings_status_error)
        else -> sub.feedStatus
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(feedman.accentSoft)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = statusLabel,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        sub.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
            Text(
                text = msg,
                color = feedman.mutedFg,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // 再開ボタン（NFR 1.1 進行中状態 100ms 以内切替: Compose 再構成のオーバーヘッドは無視可能）
        TextButton(
            onClick = onResume,
            enabled = !state.resumeInProgress &&
                !state.saveInProgress &&
                !state.unsubscribeInProgress,
            modifier = Modifier.sizeIn(
                minWidth = 88.dp,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            if (state.resumeInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.subscription_settings_resume_button),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── フェッチ間隔セグメント ─────────────────────────────────────

@Composable
private fun FetchIntervalSection(
    state: SubscriptionSettingsUiState.Visible,
    onSelectInterval: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.subscription_settings_interval_label),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubscriptionSettingsUiState.ALLOWED_INTERVAL_MINUTES.forEach { interval ->
                IntervalSegment(
                    label = stringResource(
                        R.string.subscription_settings_interval_minutes,
                        interval,
                    ),
                    selected = state.selectedIntervalMinutes == interval,
                    enabled = !state.saveInProgress && !state.unsubscribeInProgress,
                    onClick = { onSelectInterval(interval) },
                )
            }
        }
    }
}

@Composable
private fun IntervalSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val feedman = MaterialTheme.feedmanColors
    Box(
        modifier = Modifier
            .sizeIn(minHeight = MaterialTheme.feedmanDimens.minTapTarget) // NFR 2.1 タップ標的
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.surface else feedman.mutedFg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── 保存 / 解除 ───────────────────────────────────────────────

@Composable
private fun ActionButtons(
    state: SubscriptionSettingsUiState.Visible,
    onSave: () -> Unit,
    onRequestUnsubscribe: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 解除ボタン（左 / 二次アクション）
        TextButton(
            onClick = onRequestUnsubscribe,
            enabled = !state.saveInProgress && !state.unsubscribeInProgress,
            modifier = Modifier.heightIn(min = MaterialTheme.feedmanDimens.minTapTarget),
        ) {
            Text(
                text = stringResource(R.string.subscription_settings_unsubscribe_button),
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(modifier = Modifier.weight(1f))
        // 保存ボタン（右 / 主アクション）
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.heightIn(min = MaterialTheme.feedmanDimens.minTapTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(13.dp),
        ) {
            if (state.saveInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.subscription_settings_save_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── 解除確認ダイアログ ─────────────────────────────────────

@Composable
private fun UnsubscribeConfirmDialog(
    feedTitle: String,
    inProgress: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel, // NFR 3.2: 外部タップ / システム戻るで cancel
        title = {
            Text(text = stringResource(R.string.subscription_settings_unsubscribe_confirm_title))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.subscription_settings_unsubscribe_confirm_message,
                    feedTitle,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subscription_settings_unsubscribe_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !inProgress) {
                Text(text = stringResource(R.string.subscription_settings_unsubscribe_cancel))
            }
        },
    )
}
