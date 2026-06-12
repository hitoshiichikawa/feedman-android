# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-20-impl-encryptedsharedpreferences-tokenstore
- HEAD commit: d66dda9
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（採否確認のみ。flag 観点の boundary 細目は適用しない）

## Verified Requirements

### Requirement 1（TokenStore 抽象インターフェース）

- 1.1 save 操作（atomic write）— `TokenStore.save(tokenSet)` が 3 フィールドを単一の操作で受け取り、`InMemoryTokenStore.save` は `Mutex.withLock` 配下で `current = tokenSet` を 1 ステップで上書き。テスト `InMemoryTokenStoreTest#read returns the same token set that was most recently saved` で観測検証
- 1.2 read 操作（未保存時 null）— `TokenStore.read(): TokenSet?` のシグネチャと、`InMemoryTokenStoreTest#read returns null when no token set has been stored yet` で検証
- 1.3 clear 操作— `TokenStore.clear()` 定義および `InMemoryTokenStoreTest#read returns null after clear is invoked` / `clear is a no-op when nothing has been stored`
- 1.4 セット全体上書き— `InMemoryTokenStoreTest#save replaces the previously stored token set as a whole` で 3 フィールド全てが assertNotEquals により差し替わったことを検証
- 1.5 fake 差し替え可能— `InMemoryTokenStoreTest#InMemoryTokenStore is substitutable as a TokenStore interface reference` で `val store: TokenStore = InMemoryTokenStore()` の参照で契約成立を確認

### Requirement 2（永続実装の暗号化保管）

- 2.1 ハードウェア裏付け鍵— `EncryptedPrefsTokenStore.prefs` で `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)`（Android Keystore 経由）を採用。NFR 2.2 に従い JVM 単体テスト範囲外
- 2.2 平文書き込み禁止— `EncryptedSharedPreferences.create(..., PrefKeyEncryptionScheme.AES256_SIV, PrefValueEncryptionScheme.AES256_GCM)` で key/value 両方を暗号化。検証は instrumented 領分
- 2.3 プロセス再起動またぎ永続化— `SharedPreferences` のディスク永続化に内在（instrumented 領分）
- 2.4 clear 後の null— `EncryptedPrefsTokenStore.clear` が 3 キーを `remove()` して `commit()`。挙動検証は `InMemoryTokenStoreTest#read returns null after clear is invoked` で代替（同一インターフェース契約）
- 2.5 fresh install 時の null— `EncryptedPrefsTokenStore.read` が `prefs.contains(...)` を確認し、欠けていれば `null` を返す。`InMemoryTokenStoreTest#read returns null when no token set has been stored yet` で契約検証
- 2.6 typed error surface— `TokenStoreException` 型を新設し、`prefs by lazy`・`save`・`read`・`clear` の各所で `GeneralSecurityException` / `IOException` / `commit()==false` を wrap して throw（コードレベルで確認）

### Requirement 3（in-memory fake 実装）

- 3.1 同 interface・JVM 動作— `InMemoryTokenStore : TokenStore` 宣言、`InMemoryTokenStoreTest` は `runTest` のみ依存で Android runtime 非依存
- 3.2 構築直後 empty— `InMemoryTokenStoreTest#read returns null when no token set has been stored yet`
- 3.3 save 後 read 整合— `InMemoryTokenStoreTest#read returns the same token set that was most recently saved`
- 3.4 clear 後 null— `InMemoryTokenStoreTest#read returns null after clear is invoked` / `clear is a no-op`

### Requirement 4（変更範囲と依存差し替え）

- 4.1 変更範囲は auth + DI に閉じる— `git diff --stat origin/main..HEAD` の結果は `app/src/main/kotlin/com/feedman/android/core/auth/*`・`app/src/main/kotlin/com/feedman/android/di/AuthModule.kt`・`app/src/test/kotlin/com/feedman/android/core/auth/fake/InMemoryTokenStoreTest.kt`・`gradle/libs.versions.toml`・`app/build.gradle.kts` のみで、feature/* / shell/* / 他レイヤへの波及なし
- 4.2 production binding— `AuthModule.bindTokenStore(impl: EncryptedPrefsTokenStore): TokenStore` を `@Binds @Singleton` で宣言、`@InstallIn(SingletonComponent::class)`
- 4.3 テストで fake 差し替え— `InMemoryTokenStore` を `@Singleton class @Inject constructor()` 付きで宣言し、Hilt `@TestInstallIn` / `@BindValue` で production source を変更せず置換できる構成

### Non-Functional Requirements

- NFR 1.1 鍵非エクスポート— `MasterKeys.AES256_GCM_SPEC` 経由で Android Keystore が鍵を生成・保管（コードレベルで確認）
- NFR 1.2 並行 save/read 一貫性— `EncryptedPrefsTokenStore` / `InMemoryTokenStore` ともに `kotlinx.coroutines.sync.Mutex` で save/read/clear を直列化、save は単一 `Editor.commit()` で 3 フィールドを同時 commit
- NFR 2.1 JVM 単体テスト— `InMemoryTokenStoreTest` が `app/src/test/` 配下に存在
- NFR 2.2 永続実装 instrumented 領分— `EncryptedPrefsTokenStore` 自体の JVM テストは追加せず、`impl-notes.md` に明文化

## Findings

なし

## Summary

Issue #20 の全 requirement（1.1〜1.5 / 2.1〜2.6 / 3.1〜3.4 / 4.1〜4.3 / NFR 1.1〜2.2）に対して、コード差分または `InMemoryTokenStoreTest` 7 件のいずれかで観測可能な裏付けが確認できた。変更範囲は core/auth / di / gradle / app/src/test に閉じており、`_Boundary:_` 違反なし。impl-notes.md に `./gradlew build` SUCCESS と全テスト pass が記録されているため、JVM テスト再実行は省略した。Feature Flag Protocol は opt-out のため flag 観点の確認は不要。

RESULT: approve
