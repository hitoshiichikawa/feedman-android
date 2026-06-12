/**
 * Package marker for `feature/login` (Issue #29 Req 2.7 / Issue #23 Req 1.1, 1.2).
 *
 * The login UI Composable lives in [com.feedman.android.feature.login.LoginScreen]
 * and is driven by [com.feedman.android.feature.login.LoginViewModel].
 *
 * - PKCE / Custom Tabs 起動の責務は [com.feedman.android.feature.login.LoginViewModel]
 * - URL 組み立てロジックは [com.feedman.android.feature.login.AuthorizationUrlBuilder]
 * - ディープリンク受領は [com.feedman.android.MainActivity] が onNewIntent で
 *   [com.feedman.android.feature.login.LoginViewModel.onDeepLink] に転送する
 */
package com.feedman.android.feature.login
