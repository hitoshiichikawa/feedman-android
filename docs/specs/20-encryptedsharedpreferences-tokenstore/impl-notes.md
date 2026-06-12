# Implementation Notes — Issue #20: EncryptedSharedPreferences TokenStore

## 概要

`docs/specs/20-encryptedsharedpreferences-tokenstore/requirements.md` の要件を満たす
`TokenStore` 抽象インターフェース・EncryptedSharedPreferences 裏付け永続実装・JVM 単体テスト用
in-memory fake・Hilt DI 配線を追加した。リフレッシュ実行ロジック（401 ハンドリング / mutex
単一飛行 / ローテーション）は `Out of Scope` のため触れていない（#21 / #22 のスコープ）。

## 作成 / 変更ファイル

| ファイル | 役割 |
|---|---|
| `app/src/main/kotlin/com/feedman/android/core/auth/TokenSet.kt` | トークン一式の data class（access / refresh / expiresAt） |
| `app/src/main/kotlin/com/feedman/android/core/auth/TokenStore.kt` | TokenStore インターフェース + `TokenStoreException`（Req 1, 2.6） |
| `app/src/main/kotlin/com/feedman/android/core/auth/EncryptedPrefsTokenStore.kt` | EncryptedSharedPreferences + Android Keystore 裏付けの永続実装（Req 2） |
| `app/src/main/kotlin/com/feedman/android/core/auth/fake/InMemoryTokenStore.kt` | JVM 単体テスト用 in-memory fake（Req 3） |
| `app/src/main/kotlin/com/feedman/android/di/AuthModule.kt` | `TokenStore` → `EncryptedPrefsTokenStore` の Hilt バインド（Req 4） |
| `app/src/test/kotlin/com/feedman/android/core/auth/fake/InMemoryTokenStoreTest.kt` | TokenStore インターフェース契約の単体テスト（Req 1/2.5/3） |
| `gradle/libs.versions.toml` | `androidx-security-crypto = "1.0.0"` 宣言 |
| `app/build.gradle.kts` | `implementation(libs.androidx.security.crypto)` 追加 |

## Requirement ID → テスト対応表

| Requirement ID | 検証手段 |
|---|---|
| Req 1.1 save 操作（atomic write） | `InMemoryTokenStoreTest.read returns the same token set that was most recently saved` |
| Req 1.2 read 操作（未保存時 null） | `read returns null when no token set has been stored yet` / `InMemoryTokenStore is substitutable as a TokenStore interface reference` |
| Req 1.3 clear 操作 | `read returns null after clear is invoked` / `clear is a no-op when nothing has been stored` |
| Req 1.4 セット全体上書き | `save replaces the previously stored token set as a whole`（access / refresh / expiresAt すべての差し替えを assertNotEquals で確認） |
| Req 1.5 fake 差し替え可能 | `InMemoryTokenStore is substitutable as a TokenStore interface reference`（`TokenStore` 型で参照しても全契約が成立） |
| Req 2.1 ハードウェア裏付け鍵 | `EncryptedPrefsTokenStore` で `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` を採用（Android Keystore 内で生成・非エクスポート）。コード上の方針＋ NFR 2.2 により instrumented test 領分（JVM では検証不可） |
| Req 2.2 平文書き込み禁止 | `EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV` + `PrefValueEncryptionScheme.AES256_GCM` を使用し、key/value 両方を暗号化。検証は instrumented 領分 |
| Req 2.3 プロセス再起動またぎ永続化 | 永続実装＝ SharedPreferences のディスク永続化に内在。検証は instrumented 領分 |
| Req 2.4 clear 後の null 返却 | `read returns null after clear is invoked` / `save after clear stores the new token set and read returns it` |
| Req 2.5 fresh install 時の null 返却 | `read returns null when no token set has been stored yet` |
| Req 2.6 エラーの typed surface | `TokenStoreException` を導入し、`EncryptedPrefsTokenStore` の `prefs by lazy {}` / save / read / clear の各所で `GeneralSecurityException` / `IOException` / commit() == false を wrap して throw（コードレベルで担保） |
| Req 3.1 fake は同じ interface・JVM 動作 | `InMemoryTokenStoreTest` 自体（`runTest` でテスト実行・Android runtime 不要） |
| Req 3.2 構築直後は empty | `read returns null when no token set has been stored yet` |
| Req 3.3 save 後の read 整合 | `read returns the same token set that was most recently saved` |
| Req 3.4 clear 後の null | `read returns null after clear is invoked` / `clear is a no-op when nothing has been stored` |
| Req 4.1 変更範囲は auth + DI に閉じる | 追加・変更ファイルが `core/auth/` / `di/` / `gradle/libs.versions.toml` / `app/build.gradle.kts` / `app/src/test/.../auth/` に閉じている（feature/*・shell/* に手を付けていない） |
| Req 4.2 production は EncryptedPrefs を bind | `AuthModule.bindTokenStore` で `EncryptedPrefsTokenStore` を `@Binds` |
| Req 4.3 テストで fake に差し替え可能 | `InMemoryTokenStore` を `@Singleton class @Inject constructor()` で宣言。Hilt の `@TestInstallIn` または `@BindValue` で `AuthModule` を置換できる構成 |
| NFR 1.1 鍵が非エクスポート | `MasterKeys.AES256_GCM_SPEC` は内部で `KeyGenParameterSpec.Builder(..., KEY_ALIAS, PURPOSE_ENCRYPT or DECRYPT).setBlockModes(GCM).setEncryptionPaddings(NONE).setKeySize(256).build()` を生成し、Android Keystore に格納する。アプリのファイルにはエクスポートされない |
| NFR 1.2 並行 save/read の一貫性 | `EncryptedPrefsTokenStore` 内 `Mutex` で save/read/clear を直列化、save は単一 `Editor.commit()` で 3 フィールドを同時 commit。`InMemoryTokenStore` も同様に `Mutex` で直列化 |
| NFR 2.1 JVM 単体テスト可能 | `InMemoryTokenStoreTest`（Android runtime 不要） |
| NFR 2.2 永続実装は instrumented 領分 | 永続実装の検証用テストは追加していない（`./gradlew build` の CI lane では emulator 不要） |

## 判断記録

1. **`androidx-security-crypto` のバージョン選定**:
   - GA stable は **1.0.0**（2021-04 リリース）。`MasterKey` クラス＋ `KeyScheme` 列挙の新 API は
     1.1.0 系統で導入されたが、2026-06 時点でも 1.1.x は alpha のみ（最新 alpha06）。
   - libs.versions.toml のポリシー（`GA stable only` / `When stable version is unavailable, justify in a comment`）
     および Issue spec の「stable が無い場合のみ alpha」指示に従い、**1.0.0** を採用。
   - 結果として `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` + 5 引数 `EncryptedSharedPreferences.create(fileName, keyAlias, context, ...)` の旧 API を使用する形になった。
     新 API（`MasterKey.Builder(context, alias)` パターン）は将来 1.1.0 GA 化時点で乗り換える。
2. **`Mutex` での直列化**: NFR 1.2「並行 save/read で部分更新を観測させない」を保証するため、
   永続実装と fake の双方で `kotlinx.coroutines.sync.Mutex` を用いて save/read/clear を直列化。
   SharedPreferences 自体はスレッドセーフだが、3 フィールドの「セット単位」読み取りで
   write 中の合成セットが返らないことを保証するために必要。save では単一 `Editor.commit()` で
   3 フィールドを同時 commit している。
3. **`commit()` を `apply()` の代わりに採用**: NFR 1.2 のアトミック性を担保するため、
   非同期 apply ではなく同期 `commit()` を使用。失敗時は `commit() == false` を検出して
   `TokenStoreException` で throw する（silent fail を避ける / Req 2.6）。
4. **`TokenStoreException`**: Req 2.6 「読み取り不能を typed error で surface」を満たすため、
   `RuntimeException` 派生の独自例外を導入。`GeneralSecurityException` / `IOException` を
   wrap して再 throw する形を `EncryptedPrefsTokenStore` 内で統一。
5. **`accessTokenExpiresAtEpochMillis` を `Long` で保持**: `expires_in:900` を受信時刻と
   合算してエポックミリ秒で保存する設計。次の Issue #21 / #22 の refresh 判定が時刻計算
   なしで失効判定できるようにするためのインターフェース上の選択。
6. **永続実装の JVM テストは追加していない**: NFR 2.2 「emulator 必須にしない」方針に従い、
   `EncryptedPrefsTokenStore` 自体の単体テストは instrumented 領分とした。
   インターフェース契約は `InMemoryTokenStore` で同じ TokenStore 契約として検証している
   ため、契約逸脱は実装間で防げる。
7. **fake と persistence の DI 切替**: 既定では production binding を `EncryptedPrefsTokenStore`
   とし、テスト・mockMode での切替は Hilt の `@TestInstallIn` / `@BindValue` で `AuthModule` を
   置換する構成。本 Issue では mockMode 連動の自動切替（`if (MOCK_MODE) bind fake else bind real`）
   は実装していない（Issue #20 のスコープは「fake 差し替え可能性」までで、mockMode 連動の
   要件はない）。

## 追加依存の理由

- `androidx.security:security-crypto:1.0.0` (`implementation` スコープ):
  EncryptedSharedPreferences + MasterKeys API を提供するため必須。GA stable のみを採用
  するプロジェクト方針に従い 1.0.0 を選択（上記「判断記録」参照）。

## 確認事項（PR 本文への記載候補）

- **androidx.security:security-crypto 1.0.0 採用の妥当性**: 1.1.x 系統は alpha のみで、
  GA stable は 1.0.0（旧 API）。CLAUDE.md / libs.versions.toml ポリシーに従い 1.0.0 を採用
  しているが、1.0.0 は 5 年以上 GA stable 更新なし。プロジェクトとして許容するか確認。
- **永続実装の JVM カバレッジ**: `EncryptedPrefsTokenStore` 自体（暗号化保管 / 再起動またぎ）
  は instrumented test 領分のため本 PR の JVM テストでは検証していない。requirements.md NFR 2.2
  に明文記載があり要件範囲内だが、後続 Issue で androidTest を整備するか確認したい。
- **`AuthRepository.kt` の既存スケルトン**: 既存ファイルは「skeleton で Fake/Hilt binding を
  作らない」コメントを持つが、`TokenStore` は本 Issue で `EncryptedPrefsTokenStore` を default
  binding として導入した。これは AuthRepository 自体ではなく **トークンストアの境界**に対する
  binding であり、`feature/login` の placeholder 画面が自動でログイン挙動を始める可能性は無い
  （`TokenStore` 単体ではログインフロー誘発しない）。スコープ的整合は問題ないと考えるが念のため。
- **rebase 元との関係**: 作業ツリーに `docs/specs/25-feedman-theme-tokens-for-material-3/` の
  未追跡ファイルが存在するが、本 PR では `git add` していない（spec の指示に従う）。

## ビルド・テスト結果

- `./gradlew build`: SUCCESS（lintDebug / lintReportDebug / testDebugUnitTest / testReleaseUnitTest を含む）
- `InMemoryTokenStoreTest`: 7 件すべて pass（testDebugUnitTest / testReleaseUnitTest 双方）

## 派生タスク候補

- `EncryptedPrefsTokenStore` の instrumented test 整備（永続化・暗号化・プロセス再起動またぎ）
- mockMode 時に DI で `InMemoryTokenStore` を自動 bind する仕組みの導入
- Issue #21 / #22 で `AuthInterceptor` / `TokenAuthenticator` / `AuthRepository` 実装が入る段階で
  `TokenSet.accessTokenExpiresAtEpochMillis` の `Clock` 注入・失効判定ロジックを追加

STATUS: complete
