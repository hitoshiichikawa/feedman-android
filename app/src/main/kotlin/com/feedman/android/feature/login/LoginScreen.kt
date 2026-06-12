package com.feedman.android.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import com.feedman.android.R
import com.feedman.android.core.ui.LinkOpener

/**
 * Google ログイン画面（Issue #23 / Req 1.1 / 1.3 / 2.1 / 2.5 / 3.5 / 4.1 / 4.2）。
 *
 * fm-sheets.jsx `FMLogin` の構造に倣う:
 * - 中央寄せの Feedman ブランドカード + キャッチコピー
 * - 下部に「Google でログイン」ボタン（Req 2.1）
 *   - 押下で [LoginViewModel.startGoogleLogin]（Req 2.1）
 *   - 起動中 / 交換中は disabled + ローディング表示（Req 2.5 / 3.5）
 * - 失敗時に [LoginUiState.Error] のメッセージを表示し、再押下でやり直し（Req 4.1 / 4.2 / 4.3）
 * - 端末のライト / ダークテーマ（MaterialTheme.colorScheme）に従う（Req 1.3）
 *
 * Custom Tabs 起動は ViewModel の [LoginViewModel.openCustomTabs] が emit する URL を
 * `LaunchedEffect` 内で collect し、[LinkOpener.open] で開く。Composable から Activity の
 * Context を渡し、Activity 経由で起動する（Custom Tabs は `FLAG_ACTIVITY_NEW_TASK` を持た
 * なくても Activity Context なら起動可能）。
 *
 * @param linkOpener Custom Tabs / ACTION_VIEW でリンクを開くオープナー。
 * @param viewModel [LoginViewModel]。`hiltViewModel()` で取得して呼び出し側から差し替えない。
 */
@Composable
fun LoginScreen(
    linkOpener: LinkOpener,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Req 2.1: openCustomTabs が emit したら LinkOpener で URL を開く。
    LaunchedEffect(lifecycleOwner) {
        viewModel.openCustomTabs
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { url ->
                linkOpener.open(context, url)
            }
    }

    LoginScreenContent(
        uiState = uiState,
        onGoogleLoginClick = viewModel::startGoogleLogin,
        modifier = modifier,
    )
}

/**
 * [LoginScreen] の純粋 Composable 部（ViewModel から切り離した描画ロジック）。
 *
 * 直接 ViewModel を持たないため、プレビューや UI テストで状態を渡せる。
 */
@Composable
internal fun LoginScreenContent(
    uiState: LoginUiState,
    onGoogleLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        LoginScreenBody(
            uiState = uiState,
            onGoogleLoginClick = onGoogleLoginClick,
            contentPadding = padding,
        )
    }
}

@Composable
private fun LoginScreenBody(
    uiState: LoginUiState,
    onGoogleLoginClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // ブランドマーク（fm-sheets.jsx の角丸アクセントカード相当）
        Box(
            modifier = Modifier
                .height(72.dp)
                .widthIn(min = 72.dp, max = 72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Material 3 の primaryContainer 相当のアクセント面に Feedman 名前略表示
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "F",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.login_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        // エラーメッセージ表示（Req 4.1 / 4.2）
        val errorMessage = errorMessageFor(uiState)
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }

        // 進行中表示（Req 2.5 / 3.5）
        val busy = uiState is LoginUiState.LaunchingCustomTabs || uiState is LoginUiState.Exchanging
        Button(
            onClick = onGoogleLoginClick,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.login_google_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.login_legal_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(0.4f))
    }
}

/**
 * UI 描画用にエラーメッセージを解決する（Req 4.1 / 4.2 / 5.1）。
 *
 * - [LoginUiState.Error.Network] → ネットワーク失敗の汎用文言
 * - [LoginUiState.Error.Server] で `INVALID_GRANT` → 一時コード期限切れ用文言
 * - [LoginUiState.Error.Server] それ以外 → サーバー応答 `message` をそのまま表示（fallback 文言ではなく）
 * - その他の状態 → null（メッセージ非表示）
 *
 * 戻り値が null のとき、UI 上にエラーメッセージは表示されない（Req 5.1: Custom Tabs を閉じた
 * だけの場合はエラーを表示しない）。
 */
@Composable
private fun errorMessageFor(uiState: LoginUiState): String? {
    if (uiState !is LoginUiState.Error) return null
    return when (val error = uiState.error) {
        LoginError.Network -> stringResource(R.string.login_error_network)
        is LoginError.Server -> if (error.isInvalidGrant()) {
            stringResource(R.string.login_error_invalid_grant)
        } else {
            // サーバーが UI 表示用の message を返してくれているなら優先する。
            // 空のときは汎用文言にフォールバック。
            error.message.ifBlank { stringResource(R.string.login_error_generic) }
        }
    }
}
