# Requirements Document

## Introduction

ネイティブアプリの Google OAuth フロー（`design/SERVER.md` §1.2）は PKCE S256 を必須としており、認可完了後にサーバーから `feedman://auth/callback?auth_code=<one-time>` のディープリンクでアプリに戻る。本 Issue では、UI や Custom Tabs 起動・トークン交換 API 呼び出しに依存しない**純粋ロジック**として、(1) PKCE ペア（`code_verifier` / `code_challenge`）の生成と、(2) コールバック URI のパース・検証を `core/auth` パッケージ内に実装する。後続の Custom Tabs 起動（#23）とトークン交換（#21）が、本 Issue の成果物を組み合わせて完全な認証フローを構築する前提となる。

## Requirements

### Requirement 1: PKCE ペア生成

**Objective:** As a Feedman Android アプリの認証モジュール, I want PKCE の `code_verifier` と `code_challenge` を安全に生成する API を提供する, so that ネイティブ OAuth フローが RFC 7636 / SERVER.md §1.2 に準拠してサーバーと一時コードを交換できる

#### Acceptance Criteria

1. When PKCE ペア生成 API が呼び出されたとき, the PKCE Generator shall `code_verifier` と `code_challenge` の組を返す
2. When `code_verifier` が生成されたとき, the PKCE Generator shall 43 文字以上 128 文字以下の長さで、RFC 7636 で許可された unreserved 文字集合（`A-Z` / `a-z` / `0-9` / `-` / `.` / `_` / `~`）のみで構成された文字列を返す
3. When `code_challenge` が生成されたとき, the PKCE Generator shall `code_verifier` を ASCII バイト列として SHA-256 でハッシュし、その結果を **BASE64URL（パディングなし）** でエンコードした文字列を返す
4. The PKCE Generator shall `code_verifier` の生成に暗号論的に安全な乱数源を使用する
5. When PKCE ペア生成 API が連続して 2 回以上呼び出されたとき, the PKCE Generator shall 呼び出しごとに異なる `code_verifier` を返す
6. The PKCE Generator shall `code_challenge` の生成方式として SHA-256 ベース（PKCE の "S256" メソッド）のみをサポートする

### Requirement 2: コールバック URI パース

**Objective:** As a Feedman Android アプリの認証モジュール, I want ディープリンクで受領した URI から `auth_code` を抽出し不正な URI を型付きエラーで拒否する API を提供する, so that MainActivity が受け取った Intent URI を安全にトークン交換ステップへ渡せる

#### Acceptance Criteria

1. When 入力 URI が `feedman://auth/callback?auth_code=<value>` の形式であるとき, the Auth Callback Parser shall 成功結果として `<value>` を `auth_code` として返す
2. When 入力 URI に `auth_code` 以外のクエリパラメータが付与されているとき, the Auth Callback Parser shall `auth_code` の値のみを抽出して成功結果として返す
3. If 入力 URI の scheme が `feedman` 以外であるとき, the Auth Callback Parser shall クラッシュせず型付きエラー結果（scheme 不一致を識別できるカテゴリ）を返す
4. If 入力 URI の host / path が `auth/callback` 相当でないとき, the Auth Callback Parser shall クラッシュせず型付きエラー結果（host/path 不一致を識別できるカテゴリ）を返す
5. If 入力 URI に `auth_code` クエリパラメータが含まれないとき, the Auth Callback Parser shall クラッシュせず型付きエラー結果（`auth_code` 欠落を識別できるカテゴリ）を返す
6. If 入力 URI の `auth_code` クエリパラメータが空文字であるとき, the Auth Callback Parser shall クラッシュせず型付きエラー結果（`auth_code` 欠落と同等カテゴリ）を返す
7. If 入力 URI が構文的に不正で URI として解釈できないとき, the Auth Callback Parser shall クラッシュせず型付きエラー結果を返す
8. When `auth_code` がパーセントエンコードされた値を含むとき, the Auth Callback Parser shall デコード後の値を `auth_code` として返す

## Non-Functional Requirements

### NFR 1: 純粋ロジック・境界

1. The PKCE Generator and Auth Callback Parser shall Android UI フレームワーク（Activity / Context / Compose 等）に依存しない純粋ロジックとして実装され、JVM 単体テスト（`app/src/test/`）のみで全 AC を検証可能である
2. The PKCE Generator and Auth Callback Parser shall `core/auth` パッケージ配下のみに変更を閉じ、他レイヤー（`core/network` / `core/data` / `feature/*` / `shell` / `di`）の公開 API に副作用を持たない

### NFR 2: テスト可能性

1. The PKCE Generator shall 乱数源を注入可能な形で公開し、テストで決定論的な乱数を差し込んで `code_verifier` / `code_challenge` の組を検証可能である
2. The Auth Callback Parser shall 文字列または URI 相当の値を入力として受け取り、Android Intent オブジェクトを直接引数にしない形で公開され、文字列ベースの単体テストで全分岐を網羅可能である

## Out of Scope

- Chrome Custom Tabs の起動・OAuth ログイン URL の構築（#23 で扱う）
- `POST /api/auth/token { auth_code, code_verifier }` 呼び出しとレスポンス処理（#21 で扱う）
- `TokenStore` への access / refresh トークン保存・EncryptedSharedPreferences / Android Keystore 統合
- `AuthRepository` / `SessionState` の状態遷移管理
- MainActivity への deep link Intent filter 登録（後続 Issue）
- `code_challenge_method=plain` などの S256 以外の PKCE 方式
- サーバー側の `auth_code` 検証ロジック（feedman リポジトリの責務）

## Open Questions

なし
