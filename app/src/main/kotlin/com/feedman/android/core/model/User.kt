package com.feedman.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 現在ログイン中のユーザー（SPEC §4.2 `User`）。
 *
 * `GET /auth/me` のレスポンス本体。SPEC §4.2 では `{ id, email, ... }` と簡略化されているが、
 * 確実に契約に存在するのは `id` と `email` のみであり、それ以外の任意フィールドは将来追加
 * された場合に decode を壊さないように [kotlinx.serialization.json.Json] の
 * `ignoreUnknownKeys = true` 設定（テスト側で適用）で吸収する。
 */
@Serializable
data class User(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
)
