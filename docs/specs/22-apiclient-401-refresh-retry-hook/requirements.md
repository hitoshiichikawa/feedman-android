# Requirements Document

## Introduction

アクセストークンは 15 分で失効する短命設計のため、何もしなければ通常操作中に頻繁に
401 応答が発生し、利用者は突然の再ログインを強いられる。本機能は 401 を受けた API 呼び出しを
透過的に復旧させる「401 → リフレッシュ → 元リクエスト 1 回再試行」の共通フックをネットワーク層に
組み込み、リフレッシュトークンが有効な間はユーザーに認証エラーを露出させないことを目的とする。
リフレッシュ自体が失敗した場合は、利用者がログインしていない状態であることを画面群に通知し、
無限再試行や不整合状態を防止する。本要件は サーバー仕様 `design/SERVER.md` §1.3 の
`/api/auth/refresh` 契約および グランドデザイン §5.3 の認証フローを前提とする。

## Requirements

### Requirement 1: 401 応答に対する透過的なトークンリフレッシュと再試行

**Objective:** As an アプリ利用者, I want アクセストークン失効を意識せず API 呼び出しが継続できる, so that 操作中に突然エラーやログイン画面に飛ばされず作業を続けられる

#### Acceptance Criteria

1. When 認証必須 API が HTTP 401 を返し、かつ有効なリフレッシュトークンが端末に保存されている, the Network Auth Layer shall リフレッシュトークンを用いて新しいアクセストークンを取得し、元のリクエストを新しいアクセストークンで 1 回だけ再試行する
2. When 再試行したリクエストが 2xx を返した, the Network Auth Layer shall リフレッシュと再試行を呼び出し元に露出させず、当該応答をそのまま呼び出し元に返却する
3. When 再試行したリクエストが再度 401 以外のエラー（4xx / 5xx）を返した, the Network Auth Layer shall そのエラー応答をリフレッシュ前と同じ形式で呼び出し元に返却する
4. The Network Auth Layer shall 1 回の元リクエストに対する自動リフレッシュ + 再試行を 1 回に制限し、それ以降は再試行せずに応答を呼び出し元に返却する

### Requirement 2: リフレッシュ不可時のセッション失効通知

**Objective:** As an アプリ利用者, I want リフレッシュが失敗したときに迷子のエラー状態に置かれない, so that 何をすれば良いか（再ログイン）が画面側で一貫して案内される

#### Acceptance Criteria

1. If リフレッシュトークンが端末に保存されていない状態で 401 を受信した, the Network Auth Layer shall リフレッシュも再試行も行わず、当該 401 応答を呼び出し元に返却する
2. If `/api/auth/refresh` が 401 など失敗応答を返した, the Auth Session Module shall 保存されているアクセストークンとリフレッシュトークンを破棄する
3. When トークン破棄が発生した, the Auth Session Module shall セッション状態を「未ログイン」へ遷移させ、当該状態を購読している画面群が観測できる形で公開する
4. If `/api/auth/refresh` が失敗した結果として元リクエストの再試行ができない, the Network Auth Layer shall 元リクエストの呼び出し元に対して認証が必要であることが識別できるエラー応答を返却し、再試行ループに入らない

### Requirement 3: 並行 401 発生時のリフレッシュ単一飛行

**Objective:** As an アプリ運用者, I want 同時に複数 API が 401 を受け取っても サーバーへの refresh 呼び出しが 1 回に収束する, so that リフレッシュトークンのローテーションが多重化されて family 全失効を引き起こすリスクを避けたい

#### Acceptance Criteria

1. When 複数の API リクエストが同時に 401 を受信した, the Network Auth Layer shall 同時に進行中のリフレッシュ呼び出しを 1 件のみに保ち、他の 401 リクエストは同一のリフレッシュ結果を待って再試行する
2. When 単一飛行中のリフレッシュが成功した, the Network Auth Layer shall 待機していた他の 401 リクエストすべてを、取得した新しいアクセストークンで各 1 回ずつ再試行する
3. If 単一飛行中のリフレッシュが失敗した, the Network Auth Layer shall 待機していた他の 401 リクエストを再試行せず、すべてに対して Requirement 2 と同じ未認証エラー応答を返却する
4. While リフレッシュが進行中, the Network Auth Layer shall 新規に発生した 401 を当該リフレッシュの結果に合流させ、新たなリフレッシュを開始しない

### Requirement 4: トークンローテーションの即時保存

**Objective:** As an アプリ運用者, I want リフレッシュ応答で返ってきた新しいリフレッシュトークンが端末側で常に最新化される, so that 次回のリフレッシュで旧トークンを誤って使用し family 全失効に巻き込まれることを防ぎたい

#### Acceptance Criteria

1. When リフレッシュ応答に新しいアクセストークンおよびリフレッシュトークンが含まれている, the Auth Session Module shall 保存済みの両トークンを応答内容で上書きしてから、待機中の再試行へ新しいアクセストークンを供給する
2. If 新しいトークンの保存に失敗した, the Auth Session Module shall リフレッシュは失敗扱いとし、Requirement 2 と同じ未認証フローに合流する

## Non-Functional Requirements

### NFR 1: 再試行の有界性と無限ループ防止

1. The Network Auth Layer shall 1 つの元リクエストに対して自動的に発生するネットワーク往復を「元リクエスト 1 回 + リフレッシュ 1 回 + 再試行 1 回」の合計 3 回以内に制限する
2. If 再試行したリクエストが再び 401 を返した, the Network Auth Layer shall 自動リフレッシュ・再試行を行わず、Requirement 2 と同じ未認証フローに合流する

### NFR 2: 検証可能性

1. The Network Auth Layer shall モックサーバーを用いた「401 → refresh 成功 → 200」「401 → refresh 失敗 → 未認証通知」「並行 401 → refresh 1 回 → 全件再試行」の 3 シナリオを自動テストで再現できる粒度のフックポイントを持つ

## Out of Scope

- リフレッシュ失敗後にログイン画面へ実際に遷移させる画面実装（Issue #24 で `SessionState` を購読する別 Issue が担当）
- 認証エラー時のユーザー向け文言・トースト表示などの UI 文言設計
- リフレッシュトークン取得・初回ログイン（`POST /api/auth/token`）の実装（別 Issue）
- アクセストークン期限の事前判定による proactive refresh（本 Issue は 401 を受けてからの reactive refresh のみ）
- リフレッシュ以外のエラー（ネットワーク断・5xx）に対する自動リトライ戦略
- アクセストークン・リフレッシュトークンの暗号化保存方式の選定（別 Issue。本要件は「保存・破棄・上書き」の挙動のみ規定）

## Open Questions

- なし（Issue 本文・`design/SERVER.md` §1.3・`docs/GRAND-DESIGN.md` §5.3 で挙動が確定しており、本要件の範囲では追加判断は不要）
