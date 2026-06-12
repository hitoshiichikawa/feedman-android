# Requirements Document

## Introduction

Feedman Android では Issue #21 で `AuthRepository.revoke()`（サーバー失効依頼 + ローカルトークン
消去を best-effort で行う実装）が、Issue #24 で `Restoring` / `LoggedIn` / `LoggedOut` を観測する
`SessionState` が、Issue #49 でアカウントシート（現在ユーザー表示）が、それぞれ既に merge 済みである。
本 Issue ではユーザーが明示的にログアウトする経路として、アカウントシートに「ログアウト」操作を
配置し、押下時に `AuthRepository.revoke()` 経由で `POST /api/auth/revoke`（SERVER.md §1.3）を試行
した上で保存済みトークンを確実に消去し、さらに前ユーザーのキャッシュ状態（既読・スター等の
`ItemStateStore` を含むユーザースコープのインメモリ／永続キャッシュ）をリセットしてからログイン画面
へ戻す責務を扱う。

revoke の HTTP 呼び出し自体は #21 で best-effort 実装済みであり、本 Issue は「UI 配置」「キャッシュ
リセット」「セッション状態遷移によるログイン画面復帰」が中心となる。退会（`DELETE /api/users/me`）
は別途 Issue #51 で扱うため本 Issue では実装しない。

## Requirements

### Requirement 1: アカウントシートからのログアウト起動

**Objective:** As an アプリ利用者, I want アカウントシート上にログアウト操作が用意されていて押下できる, so that 自分の意思で明示的にセッションを終了できる

#### Acceptance Criteria

1. While アカウントシートが表示されている間, the Account Sheet shall ログアウト操作のための UI 要素（ボタン）を表示する
2. When ユーザーがログアウトボタンを押下する, the Account Sheet shall ログアウト処理（revoke + ローカル消去 + キャッシュリセット + ログイン画面遷移）を 1 回開始する
3. While ログアウト処理が進行中である間, the Account Sheet shall ログアウトボタンを再押下不可（重複起動を防ぐ disabled 状態）にする
4. While ログアウト処理が進行中である間, the Account Sheet shall 進行中であることがユーザーに伝わる視覚表現（ローディングインジケータ等）を提供する

### Requirement 2: revoke 呼び出しとローカルクレデンシャル消去

**Objective:** As an アプリ利用者, I want ログアウト時にサーバーへの失効依頼が試みられつつ、ネットワーク状況に関わらず端末上のトークンが確実に消える, so that 端末を手放す前にログアウトしておけば認証情報が端末に残らないことを保証できる

#### Acceptance Criteria

1. When ログアウト処理が開始された, the Logout Coordinator shall `AuthRepository.revoke()` を 1 回呼び出す
2. When revoke 呼び出しがサーバー成功・サーバーエラー・ネットワーク失敗のいずれで完了した, the Logout Coordinator shall 端末上の access token と refresh token を消去した状態へ確定する
3. If revoke 呼び出しがネットワーク失敗で完了した, the Logout Coordinator shall ネットワーク失敗を理由にログアウト処理を中断せず、後続のキャッシュリセットおよびログイン画面遷移を継続する
4. When ログアウト処理が完了した, the Logout Coordinator shall 端末上に access token と refresh token のいずれも残らない状態にする

### Requirement 3: 前ユーザーキャッシュのリセット

**Objective:** As an アプリ利用者, I want ログアウト後に別アカウントでログインしても前ユーザーの既読・スター等の状態が混在しない, so that アカウント切り替え時に他人の閲覧履歴が見える事故を防げる

#### Acceptance Criteria

1. When ログアウト処理が完了した, the Logout Coordinator shall ユーザースコープのキャッシュ状態（既読・スター等を含む `ItemStateStore` 相当のユーザー単位インメモリ／プロセス内キャッシュ）を初期化する
2. When ログアウト処理完了後に新しいセッションが開始された, the App Shell shall ログアウト前のユーザーに紐づく既読・スター等の状態を画面に再現しない
3. When ログアウト処理が完了した, the Logout Coordinator shall アカウントシートで取得済みの現在ユーザー情報（email 等）を破棄する

### Requirement 4: セッション状態遷移とログイン画面復帰

**Objective:** As an アプリ利用者, I want ログアウトすると自動的にログイン画面に戻る, so that ログアウト後の操作対象がただちに明確になる

#### Acceptance Criteria

1. When ログアウト処理が完了した, the Session State Module shall セッション状態を LoggedOut へ遷移させる
2. When セッション状態が LoggedOut へ遷移した, the App Shell shall ログイン画面を描画する
3. When セッション状態が LoggedOut へ遷移した, the Account Sheet shall アカウントシートを閉じる
4. When ログアウト処理が完了した, the App Shell shall ログアウト前に表示されていたボトムシート（記事詳細・購読設定・フィード登録など）を破棄する

### Requirement 5: ログアウト中のエラー透過性

**Objective:** As an アプリ利用者, I want revoke がネットワーク失敗してもログアウト操作が完遂し、サーバーエラー由来の不可解な失敗画面を見せられない, so that ログアウトという操作の意図が常に達成される

#### Acceptance Criteria

1. If revoke 呼び出しがネットワーク失敗またはサーバーエラーで完了した, the Logout Coordinator shall 当該失敗を理由にログアウト処理全体を失敗扱いにせず、ローカル消去・キャッシュリセット・ログイン画面遷移を完了する
2. If revoke 呼び出しがネットワーク失敗で完了した, the Account Sheet shall ログアウト処理失敗を示すエラーメッセージを表示しない（ユーザーから観測される結果はログイン画面遷移のみ）

## Non-Functional Requirements

### NFR 1: 観測可能なパフォーマンス・体感

1. When ユーザーがログアウトボタンを押下した, the Account Sheet shall 1 秒以内に進行中であることがわかる視覚表現（ローディングインジケータ等）を画面に提示する
2. When revoke 呼び出しがネットワークタイムアウトに達した, the Logout Coordinator shall 最大 10 秒以内にログイン画面遷移までの全工程を完了する（revoke 応答待ちでログアウト操作を無期限にブロックしない）

### NFR 2: クレデンシャル消去の確実性

1. When ログアウト処理がいずれかの工程で例外を発生させた, the Logout Coordinator shall 端末上に access token と refresh token のいずれも残さない状態でログアウト処理を終了する
2. The Logout Coordinator shall 消去後の access token / refresh token をログ出力・クラッシュレポート本文に含めない

### NFR 3: プライバシー

1. The Logout Coordinator shall ログアウト処理の進行・結果に関するログに、対象ユーザーの email 等の識別情報を含めない

## Out of Scope

- 退会（アカウント削除）操作と `DELETE /api/users/me` 呼び出し（Issue #51 で扱う）
- 退会後のサーバー側 refresh_tokens / auth_codes 削除（SERVER.md §1.5 のサーバー責務）
- 起動時の自動セッション復元と 401 起因の自動ログアウト（Issue #24 で実装済み）
- ログイン画面そのものの実装（Issue #23 で実装済み）
- `AuthRepository.revoke()` の HTTP 呼び出し実装（Issue #21 で実装済み。本 Issue は呼び出し側）
- 複数アカウント切替・複数アカウント同時保持（v1 スコープ外）
- ログアウト直後にログイン画面で表示する補助メッセージ（「ログアウトしました」等のスナックバー）の有無

## Open Questions

- ユーザースコープでリセット対象とすべきキャッシュの具体的構成要素（`ItemStateStore` 以外に `core/data` 配下で導入済みのユーザー単位キャッシュが何件あるか）は設計時に `core/data` の実装を棚卸しして確定する。要件としては「ユーザースコープの既読・スター等の状態を初期化する」という観測可能挙動に限定する。
- ログアウト直後にログイン画面で「ログアウトしました」等のスナックバー／トーストを併出するかは UX 判断として未確定。Out of Scope として本 Issue では扱わず、必要であれば別 Issue で扱う。
