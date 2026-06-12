package com.feedman.android.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `feedman://auth/callback?...` ディープリンク URI を MainActivity から LoginViewModel に
 * 配信する singleton dispatcher（Issue #23 Req 3.1 / NFR 2.1）。
 *
 * ## なぜ singleton dispatcher か
 *
 * - [com.feedman.android.MainActivity] が `onNewIntent` / 初期 `getIntent()` でディープリンクを
 *   受領する位置は ViewModel スコープの外側のため、Activity から ViewModel に直接通知できない。
 * - 一方で [com.feedman.android.feature.login.LoginViewModel] は `hiltViewModel()` 経由で
 *   Composable スコープに作られ、Activity が直接参照を持てない。
 * - そこで両者の中間に Hilt singleton の本 dispatcher を置き、Activity が `dispatch` で URI を
 *   流し、ViewModel が [intents] を collect することで疎結合に配信する。
 *
 * ## buffer / 再配信ポリシー
 *
 * - `MutableSharedFlow(replay = 1, extraBufferCapacity = 1)` を採用し、collector が未接続の
 *   タイミング（ViewModel が `hiltViewModel()` で作られる前）にディープリンクが届いても、
 *   その後の最初の collect で 1 件だけ再配信される。
 * - これによりプロセス kill から復帰した瞬間にディープリンクで起動された場合にも、最初の
 *   composition がディープリンクを取り逃さない。
 * - LoginViewModel は同じ URI を 2 回処理しないよう、内部で「処理済み」を管理する必要は
 *   ない（auth_code 自体が 1 回限り消費される + AuthRepository が冪等でないため、2 回処理
 *   されると 2 回目は INVALID_GRANT になる）。万一連続で同じ URI が流れた場合に備え、
 *   ViewModel 側で `code_verifier` を破棄した後の onDeepLink は no-op（NFR 2.2）になる。
 */
@Singleton
class AuthCallbackDispatcher @Inject constructor() {

    private val _intents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    /** ViewModel が collect する SharedFlow。最新 1 件は replay される。 */
    val intents: SharedFlow<String> = _intents.asSharedFlow()

    /**
     * MainActivity からディープリンク URI を流す。
     *
     * @param uri 受領した Intent の `data` を `toString()` した値（`feedman://auth/callback?...`）
     */
    fun dispatch(uri: String) {
        _intents.tryEmit(uri)
    }
}
