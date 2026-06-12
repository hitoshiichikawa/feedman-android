package com.feedman.android.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore に裏付けられた鍵で暗号化する [TokenStore] の永続実装
 * （Issue #20 / Req 2 / NFR 1.1）。
 *
 * - androidx.security の [EncryptedSharedPreferences] を採用し、key/value 両方を AES-GCM で
 *   暗号化する（Req 2.1 / Req 2.2）。
 * - 鍵は [MasterKey] 経由で Android Keystore から取得（AES-256 GCM スキーム）。鍵マテリアルは
 *   端末のセキュアハードウェア境界の外には出ない（NFR 1.1）。
 * - 本実装は **薄い**永続化アダプタであり、リフレッシュ判定・有効期限判定・401 リトライ等の
 *   ロジックは含まない（#21 / #22 のスコープ）。
 *
 * 並行性 / アトミック性:
 * - [save] は 3 フィールドを 1 つの [SharedPreferences.Editor] で commit し、内部 [Mutex] で
 *   直列化することで、並行 read が部分更新を観測しない（NFR 1.2）。
 * - [read] も同じ Mutex 配下で 3 フィールドをまとめて取り出し、いずれかが欠けていれば
 *   `null` を返す（書き込み途中の合成セットを返さない）。
 *
 * エラー伝播:
 * - 初期化（[EncryptedSharedPreferences.create] / [MasterKey] 構築）で [GeneralSecurityException]
 *   や [java.io.IOException] が出た場合、`TokenStoreException` で wrap して呼び出し側へ surface
 *   する（Req 2.6）。silent fail は行わない。
 *
 * JVM テスト性:
 * - 本実装は Android runtime（Keystore / SharedPreferences 実体）が必要なため、JVM 単体テスト
 *   からは初期化できない（NFR 2.2）。検証は instrumented test または in-memory fake で行う
 *   構成を取る。
 */
@Singleton
class EncryptedPrefsTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {

    private val mutex = Mutex()

    /**
     * EncryptedSharedPreferences の遅延初期化。初回参照時に Keystore へアクセスする。
     * 構築失敗は [TokenStoreException] として呼び出し側へ伝播する（Req 2.6）。
     */
    private val prefs: SharedPreferences by lazy {
        try {
            // androidx.security:security-crypto 1.0.0（GA stable）の旧 API。
            // MasterKey クラス + KeyScheme 列挙は 1.1.x 系（alpha のみ / 2026-06 時点）で
            // 導入された API のため、GA stable を維持する本プロジェクトでは
            // `MasterKeys.getOrCreate(AES256_GCM_SPEC)` + 5 引数 `EncryptedSharedPreferences.create` を使用する。
            // 鍵は Android Keystore 内で生成・保管され、アプリのファイルにはエクスポートされない（NFR 1.1）。
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: GeneralSecurityException) {
            throw TokenStoreException("Failed to open encrypted token store", e)
        } catch (e: java.io.IOException) {
            throw TokenStoreException("Failed to open encrypted token store", e)
        }
    }

    override suspend fun save(tokenSet: TokenSet) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    prefs.edit()
                        .putString(KEY_ACCESS_TOKEN, tokenSet.accessToken)
                        .putString(KEY_REFRESH_TOKEN, tokenSet.refreshToken)
                        .putLong(KEY_ACCESS_TOKEN_EXPIRES_AT, tokenSet.accessTokenExpiresAtEpochMillis)
                        .commit()
                        .also { committed ->
                            if (!committed) {
                                throw TokenStoreException("Encrypted token store commit returned false")
                            }
                        }
                } catch (e: TokenStoreException) {
                    throw e
                } catch (e: GeneralSecurityException) {
                    throw TokenStoreException("Failed to save token set", e)
                }
            }
        }
    }

    override suspend fun read(): TokenSet? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val access = prefs.getString(KEY_ACCESS_TOKEN, null)
                val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
                val hasExpiry = prefs.contains(KEY_ACCESS_TOKEN_EXPIRES_AT)
                if (access == null || refresh == null || !hasExpiry) {
                    null
                } else {
                    TokenSet(
                        accessToken = access,
                        refreshToken = refresh,
                        accessTokenExpiresAtEpochMillis = prefs.getLong(KEY_ACCESS_TOKEN_EXPIRES_AT, 0L),
                    )
                }
            } catch (e: GeneralSecurityException) {
                throw TokenStoreException("Failed to read token set", e)
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    prefs.edit()
                        .remove(KEY_ACCESS_TOKEN)
                        .remove(KEY_REFRESH_TOKEN)
                        .remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
                        .commit()
                        .also { committed ->
                            if (!committed) {
                                throw TokenStoreException("Encrypted token store clear returned false")
                            }
                        }
                } catch (e: TokenStoreException) {
                    throw e
                } catch (e: GeneralSecurityException) {
                    throw TokenStoreException("Failed to clear token set", e)
                }
            }
        }
    }

    private companion object {
        /**
         * EncryptedSharedPreferences のファイル名。`xml` 拡張子は androidx 側で自動付与される。
         * 既存ファイル名の変更はマイグレーション影響があるため、命名を不用意に変えないこと。
         */
        const val PREFS_FILE_NAME = "feedman_auth_tokens"

        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_TOKEN_EXPIRES_AT = "access_token_expires_at"
    }
}
