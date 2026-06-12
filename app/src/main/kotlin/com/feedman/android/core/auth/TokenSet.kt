package com.feedman.android.core.auth

/**
 * サーバーから受領した認証トークン一式（access / refresh）と access token の有効期限。
 *
 * - [accessToken]: サーバー発行 JWT（`design/SERVER.md` §1.4 で寿命 15 分）。
 * - [refreshToken]: 不透明乱数（同 §1.4 で寿命 30 日・ローテーション運用）。
 * - [accessTokenExpiresAtEpochMillis]: access token の有効期限を端末ローカル時刻
 *   （`System.currentTimeMillis()` 互換のエポックミリ秒）で表現したもの。`expires_in`
 *   レスポンスを受信時刻と合算して保存することで、後段の refresh ロジック（#21 / #22）が
 *   時刻計算なしで失効判定できる。
 *
 * Issue #20 のスコープでは本データクラスは「保存・読み出し・削除される値」の入れ物に過ぎず、
 * 失効判定や refresh 実行ロジックは持たない（[TokenStore] の責務に同じ）。
 */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
)
