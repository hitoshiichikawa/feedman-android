# Requirements Document

## Introduction

feedman-android は OAuth 完了後にサーバーから発行されるアクセストークン（JWT、有効期限 15 分）とリフレッシュトークン（不透明乱数、有効期限 30 日）を端末に保持し、以降の API 呼び出しで Bearer 認証を行う。これらのトークンは端末紛失・root 化・他アプリからの読み取りといった脅威に対し、平文で保存してはならない（`docs/GRAND-DESIGN.md` §5.3）。

本 Issue では、トークンと有効期限のセット（以下「トークンセット」）を安全に永続化する `TokenStore` を独立した横断関心事として整備する。具体的には (a) 上位レイヤ（AuthInterceptor / TokenAuthenticator / AuthRepository）が依存する抽象インターフェース、(b) 端末上でハードウェア裏付け鍵により暗号化して永続化する本実装、(c) JVM 単体テストで他コンポーネントが利用するための in-memory fake 実装、の 3 点を提供する。

リフレッシュ実行ロジック（401 ハンドリング / mutex 単一飛行 / ローテーション戦略）は #21 / #22 のスコープであり、本 Issue では取り扱わない。本 Issue の責務は「トークンセットを安全に保存・読み出し・削除する」点に限定する。

## Requirements

### Requirement 1: TokenStore 抽象インターフェース

**Objective:** As a 認証関連機能の実装者, I want トークン保存・読み出し・削除を抽象化したインターフェースを参照したい, so that 上位層（API クライアント / リフレッシュロジック / ログアウト処理 / ViewModel テスト）が永続実装に直接結合せず、テストでは fake 実装に差し替えられる

#### Acceptance Criteria

1. The TokenStore shall expose a save operation that accepts an access token, a refresh token, and an access token expiration timestamp as a single atomic write.
2. The TokenStore shall expose a read operation that returns the currently stored token set, or a null/absent value when no token set has been stored.
3. The TokenStore shall expose a clear operation that removes all stored token fields.
4. When the save operation is invoked, the TokenStore shall replace any previously stored token set in full（部分更新ではなくセット全体を上書きする）.
5. The TokenStore shall be defined in a way that allows substitution by an in-memory fake implementation for JVM unit tests without requiring an Android runtime.

### Requirement 2: 永続実装の暗号化保管

**Objective:** As a エンドユーザー, I want アプリに保存される認証トークンが端末紛失・他アプリ・物理的取り出し攻撃に対して読み取られないことを期待する, so that アカウントが第三者に乗っ取られない

#### Acceptance Criteria

1. When the persistent TokenStore implementation writes a token set, it shall persist all token fields encrypted at rest using a hardware-backed key managed by the platform key store.
2. The persistent TokenStore implementation shall not write any token field（access token / refresh token / 有効期限）to disk in plaintext form.
3. When the application process restarts, the persistent TokenStore shall return the same token set that was most recently saved before the restart.
4. When the clear operation completes, the persistent TokenStore shall ensure that subsequent reads return the null/absent value and that no residual plaintext or ciphertext of the cleared token fields remains in the store.
5. While no token set has ever been stored on a fresh install, the persistent TokenStore shall return the null/absent value without throwing an exception.
6. If the underlying encrypted store cannot be opened or read（鍵破損・ストレージ破損等）, the persistent TokenStore shall surface the failure as a typed error to callers rather than silently returning a stale or empty value.

### Requirement 3: in-memory fake 実装

**Objective:** As a 他機能の実装者・テスト作成者, I want Android ランタイムなしで TokenStore 相当の挙動を再現できる fake を使いたい, so that JVM 単体テストでログイン後フローや 401 リトライ等のシナリオを安価に書ける

#### Acceptance Criteria

1. The in-memory fake TokenStore shall implement the same interface as the persistent implementation and shall be runnable on the JVM unit test runtime without Android framework dependencies.
2. When the fake TokenStore is constructed, it shall start in the empty state where read operations return the null/absent value.
3. When save is invoked on the fake TokenStore, subsequent reads in the same instance shall return the token set that was most recently saved.
4. When clear is invoked on the fake TokenStore, subsequent reads in the same instance shall return the null/absent value.

### Requirement 4: 変更範囲と依存差し替え

**Objective:** As a リポジトリ全体のメンテナ, I want TokenStore 関連の変更が認証モジュールと DI 配線に閉じている状態を保ちたい, so that 他機能のレビュー影響範囲が広がらず、テスト構成も単純に保てる

#### Acceptance Criteria

1. The TokenStore interface and its implementations shall reside within the auth module and the dependency-injection wiring module, and shall not require modifications to feature-level modules outside of these areas.
2. When the application is built, the dependency-injection wiring shall provide the persistent TokenStore implementation as the default binding for the TokenStore interface in production builds.
3. Where unit tests need to substitute the TokenStore, the dependency-injection wiring shall allow the in-memory fake to be used in place of the persistent implementation without modifying production source files.

## Non-Functional Requirements

### NFR 1: セキュリティ

1. The persistent TokenStore shall use a key whose private material is non-extractable from the device's secure hardware boundary（鍵がアプリのファイルとしてエクスポート不可能であること）.
2. If multiple processes or threads invoke save and read concurrently, the persistent TokenStore shall return either the prior fully-consistent token set or the newly saved fully-consistent token set, and shall not return a partially-updated combination of old and new fields.

### NFR 2: テスト容易性 / 運用制約

1. The TokenStore interface and the in-memory fake shall be verifiable by JVM unit tests that run in CI without requiring an Android emulator or instrumented test environment.
2. Where the persistent implementation requires an Android runtime to verify behavior, those checks shall be confined to instrumented tests that are not required to pass in the standard CI lane（標準 CI lane では emulator を必須にしない）.

## Out of Scope

- リフレッシュトークンを用いた access token 再発行の実行ロジック（mutex 単一飛行・ローテーション・再利用検知の扱い）→ #21 / #22 が担当
- 401 応答時のリトライ・SessionState 遷移・ログイン画面への自動遷移
- OAuth 開始フロー・PKCE 生成・Custom Tabs 起動・ディープリンク受領（`feedman://auth/callback`）
- ログイン UI / ログアウト UI / アカウント画面
- サーバー側エンドポイント（`/api/auth/token` `/api/auth/refresh` `/api/auth/revoke`）の仕様変更
- 既存平文ストレージからの移行（v1 初回導入のため移行対象が存在しない）
- 複数アカウント同時ログイン対応（v1 単一アカウント前提）

## Open Questions

- なし（Issue #20 本文・`design/SERVER.md` §1.4・`docs/GRAND-DESIGN.md` §5.3 で判断材料は揃っている）
