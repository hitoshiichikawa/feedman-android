@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.feedman.android.feature.registerfeed

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.designsystem.feedmanDimens
import com.feedman.android.core.ui.FeedmanSheet

/** フィード登録シートの testTag。 */
const val REGISTER_FEED_SHEET_TEST_TAG: String =
    "feature.registerfeed.RegisterFeedSheet"

/**
 * フィード登録シート（Issue #44 / SPEC §5.5）。
 *
 * `design/mobile/fm-sheets.jsx` の `FMRegisterSheet` を Compose で再現する。
 * 共通 [FeedmanSheet] でラップし、内側に以下を並べる:
 *
 * 1. ヘッダ（タイトル + 閉じるアイコン）
 * 2. URL 入力欄（フォーカス可能・プレースホルダ・補助テキスト・エラー表示）
 * 3. 登録ボタン（ローディング状態あり）
 *
 * @param onRegistrationSucceeded 登録成功時のコールバック（UI 側でトースト表示）
 */
@Composable
fun RegisterFeedSheet(
    onRegistrationSucceeded: () -> Unit,
    viewModel: RegisterFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState
    if (state !is RegisterFeedUiState.Visible) return

    // strings.xml 解決済み文言を ViewModel に注入する（純粋関数 resolver を Android
    // 依存なしで保つための DI 戦略）。
    val duplicateText = stringResource(R.string.register_feed_error_duplicate)
    val invalidUrlText = stringResource(R.string.register_feed_error_invalid_feed)
    val rateLimitTemplate = stringResource(R.string.register_feed_error_rate_limit_with_seconds)
    val rateLimitGenericText = stringResource(R.string.register_feed_error_rate_limit_generic)
    val genericFallbackText = stringResource(R.string.register_feed_error_generic)
    val networkUnreachableText = stringResource(R.string.register_feed_error_network)
    val texts = remember(
        duplicateText,
        invalidUrlText,
        rateLimitTemplate,
        rateLimitGenericText,
        genericFallbackText,
        networkUnreachableText,
    ) {
        RegisterFeedErrorTexts(
            duplicate = duplicateText,
            invalidUrl = invalidUrlText,
            rateLimitWithSeconds = { seconds -> String.format(rateLimitTemplate, seconds) },
            rateLimitGeneric = rateLimitGenericText,
            genericFallback = genericFallbackText,
            networkUnreachable = networkUnreachableText,
        )
    }
    val clientInvalidUrl = stringResource(R.string.register_feed_invalid_url_format)
    LaunchedEffect(viewModel, texts, clientInvalidUrl) {
        viewModel.setErrorTexts(texts, clientInvalidUrl)
    }

    // events: 登録成功 → 親コンポーネント（AppShell）にトースト表示を委譲
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RegisterFeedEvent.RegistrationSucceeded -> onRegistrationSucceeded()
            }
        }
    }

    // Req 1.5: システム戻る操作で閉じる
    BackHandler(enabled = true) { viewModel.close() }

    FeedmanSheet(
        onDismissRequest = { viewModel.close() },
        label = stringResource(R.string.register_feed_sheet_pane_title),
        modifier = Modifier.testTag(REGISTER_FEED_SHEET_TEST_TAG),
    ) {
        RegisterFeedSheetBody(
            state = state,
            onClose = viewModel::close,
            onUrlChange = viewModel::updateUrl,
            onSubmit = viewModel::submit,
        )
    }
}

@Composable
internal fun RegisterFeedSheetBody(
    state: RegisterFeedUiState.Visible,
    onClose: () -> Unit,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Header(onClose = onClose)
        UrlInputSection(state = state, onUrlChange = onUrlChange)
        // 補助説明（Req 1.2）
        Text(
            text = stringResource(R.string.register_feed_url_helper),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
        SubmitSection(state = state, onSubmit = onSubmit)
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.register_feed_sheet_pane_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(
                minWidth = MaterialTheme.feedmanDimens.minTapTarget,
                minHeight = MaterialTheme.feedmanDimens.minTapTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.register_feed_close_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun UrlInputSection(
    state: RegisterFeedUiState.Visible,
    onUrlChange: (String) -> Unit,
) {
    val labelText = stringResource(R.string.register_feed_url_label)
    val placeholderText = stringResource(R.string.register_feed_url_placeholder)
    // Req 5.7: ローディング中は入力欄を編集不可にし、それ以外は編集可能
    val isError = state.clientErrorMessage != null || state.serverErrorMessage != null
    val errorMessage = state.clientErrorMessage ?: state.serverErrorMessage
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            enabled = !state.submitInProgress, // Req 3.3
            label = { Text(labelText) },
            placeholder = { Text(placeholderText) },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = labelText },
        )
        // エラー文言（NFR 2.2: 入力欄と視覚的に関連付け）
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SubmitSection(
    state: RegisterFeedUiState.Visible,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.heightIn(min = MaterialTheme.feedmanDimens.minTapTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(13.dp),
        ) {
            if (state.submitInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.register_feed_submit_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
