# Feedman サーバー実装仕様書（モバイル対応）

> **対象読者**: Codex（コーディングエージェント）/ バックエンド実装担当者
> **バージョン**: v1.0 / 2026-06-08
> **対象リポジトリ**: `feedman/`（Go / chi / PostgreSQL / 単一オリジン）
> **関連**: `SPEC.md`（Android アプリ仕様）。本書はそのサーバー側の追加実装を定義する。

本書は2つの機能を扱う。
- **§1 トークン認証 — v1（Android リリースに必須）**
- **§2 キーワードプッシュ通知 — 次フェーズ（v1 スコープ外）**

いずれも**既存の Cookie セッション認証・既存エンドポイントを壊さない**ことを絶対条件とする（後方互換。Web フロントは現状のまま動作し続ける）。

---

## 0. 既存実装の前提（読み込み済みの事実）

実装時に参照すべき既存コードと、その契約。

| 関心事 | 既存の場所 | 要点 |
|---|---|---|
| セッション | `internal/model`（`Session{ID, UserID, ExpiresAt}`）、`internal/repository`（`SessionRepository`: `Create/FindByID/DeleteByID/DeleteByUserID`） | `session_id` という **HTTP-only Cookie** で管理 |
| 認証ミドルウェア | `internal/middleware/session.go` | `NewSessionMiddleware(SessionFinder)` が Cookie→`session.UserID` を解決し `ContextWithUserID` で注入。`UserIDFromContext(ctx)` で取り出す |
| 認証サービス | `internal/auth/service.go` | `HandleCallback(ctx, code) -> *model.Session`（OAuth コード交換→ユーザー upsert→セッション発行）、`GetLoginURL(state)`、`Logout`、`GetCurrentUser` |
| OAuth | `internal/auth/google_oauth.go` | `GetLoginURL` / `ExchangeCode`。コールバックは現状フロントのオリジンへリダイレクト |
| ルーティング | `internal/handler/router.go` | 認証必須ルートは `r.Group` 内で `SessionMiddleware → RateLimiter → logging` の順 |
| フェッチワーカー | `internal/worker/fetch/`（`scheduler.go` / `fetcher.go` / `retry.go`） | 購読ごとの間隔で新着を取り込む。**キーワード判定の差し込み口**（§2） |
| 既存ワーカー雛形 | `internal/worker/cleanup/` | バックグラウンドジョブの実装パターン。FCM 送信ワーカーの参考に |
| エラー形式 | `internal/middleware`（`WriteErrorResponse`） | `{ error: { code, message, category, action, details? } }` |

> **設計原則**: 新規はすべて `/api/*` 配下に追加し、既存ハンドラ・ミドルウェアの登録順を変更しない。新ミドルウェアは「許可レイヤー」を**前段に重ねる**形で足す（既存の挙動を一切変えない）。

---

# §1. トークン認証（v1）

## 1.1 背景と方針

ネイティブアプリはブラウザオリジンを持たず、OAuth リダイレクト後の `SameSite=Lax` Cookie を引き継げない。そこで **OAuth 完了後にアプリ向けの自前トークン（アクセス + リフレッシュ）を発行**し、以降の API は `Authorization: Bearer` で認証する。

- **既存 Cookie セッションは温存**。Web は今まで通り。アプリはトークン。両者を**並存**させる。
- Google から得る `refresh_token` は Google 用。**本システムが発行するのは別物**（自前のリフレッシュトークン）。

## 1.2 ネイティブ OAuth フロー

```
[App] Custom Tabs で /auth/google/login?flow=native&code_challenge=... を開く
  → Google 認可
  → /auth/google/callback がコードを処理（既存 HandleCallback を流用）
  → flow=native のときは Cookie ではなく、アプリスキームへリダイレクト:
       feedman://auth/callback?auth_code=<one-time-code>
[App] ディープリンクで auth_code を受領
  → POST /api/auth/token { auth_code, code_verifier } で本トークンと交換
  → { access_token, refresh_token, expires_in } を取得し Keystore に保存
```

- **PKCE 必須**（`code_challenge`/`code_verifier`、S256）。`auth_code` は一回限り・短命（60秒）・単一交換。
- `flow=native` パラメータが無い既存呼び出しは**従来通り Cookie 発行 + フロントへリダイレクト**（後方互換）。

## 1.3 新規エンドポイント

すべて JSON。エラーは既存 `WriteErrorResponse` 形式。

### `POST /api/auth/token`
OAuth 後の一時コードを本トークンに交換する（未認証で叩ける。IP レート制限を適用）。
```jsonc
// Request
{ "auth_code": "<one-time>", "code_verifier": "<pkce verifier>" }
// 200 Response
{ "access_token": "<jwt>", "refresh_token": "<opaque>",
  "token_type": "Bearer", "expires_in": 900 }
// errors: 400 INVALID_GRANT（コード不正/期限切れ/再利用）
```

### `POST /api/auth/refresh`
リフレッシュトークンでアクセストークンを再発行（ローテーションあり）。
```jsonc
// Request
{ "refresh_token": "<opaque>" }
// 200 Response
{ "access_token": "<jwt>", "refresh_token": "<new opaque>",
  "token_type": "Bearer", "expires_in": 900 }
// errors: 401 INVALID_REFRESH_TOKEN（失効/不正/再利用検知）
```

### `POST /api/auth/revoke`
ログアウト時にリフレッシュトークンを失効（Bearer 認証下）。
```jsonc
{ "refresh_token": "<opaque>" }   // 204 No Content
```

> 既存 `POST /auth/logout`（Cookie セッション破棄）はそのまま。アプリは `revoke` を呼ぶ。

## 1.4 トークン設計

| 種別 | 形式 | 寿命 | 保存 |
|---|---|---|---|
| アクセストークン | **JWT**（HS256 or RS256）。claims: `sub`(userID), `exp`, `iat`, `jti`, `token_use:"access"` | **15分**（`expires_in:900`） | アプリ側のみ（サーバーは保持しない） |
| リフレッシュトークン | **不透明乱数**（256bit）。サーバーに**ハッシュ保存**（`sha256`、既存 `hashSessionIDForLog` と同方針） | **30日**（スライディング） | DB（§1.5） |

- **リフレッシュトークンローテーション**: `refresh` のたびに新トークンを発行し旧を失効。**再利用検知**（失効済みトークンの提示）時は当該ファミリ全失効（盗難対策）。
- アクセストークンはステートレス検証（DB 不要）。失効が必要な高リスク操作は短寿命で吸収。
- 署名鍵は環境変数（`config`）。鍵ローテーションのため `kid` を JWT ヘッダに含める。

## 1.5 DB スキーマ（新規マイグレーション）

```sql
CREATE TABLE refresh_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash    TEXT NOT NULL UNIQUE,          -- sha256(refresh_token)
  family_id     UUID NOT NULL,                 -- ローテーション系列。再利用検知で family 単位失効
  device_label  TEXT,                          -- 任意（"Pixel 8" 等）
  expires_at    TIMESTAMPTZ NOT NULL,
  revoked_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

-- auth_code（一時コード）は短命のため Redis 等でも可。RDB なら:
CREATE TABLE auth_codes (
  code_hash      TEXT PRIMARY KEY,             -- sha256(auth_code)
  user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_challenge TEXT NOT NULL,                -- PKCE S256
  expires_at     TIMESTAMPTZ NOT NULL,         -- now()+60s
  consumed_at    TIMESTAMPTZ
);
```
- 退会（既存 `user.Service` の削除トランザクション）に `refresh_tokens` / `auth_codes` の `DELETE BY user_id` を追加すること（`ON DELETE CASCADE` でも担保されるが明示推奨）。

## 1.6 ミドルウェア統合（最重要・後方互換）

既存 `SessionMiddleware`（Cookie）を**置き換えない**。Bearer を先に試し、無ければ既存 Cookie 解決に委譲する「複合認証」を新設する。

```go
// internal/middleware/auth.go（新規・概念コード）
func NewBearerOrSessionMiddleware(jwtVerifier JWTVerifier, sessionFinder SessionFinder) Middleware {
  bearer  := NewBearerMiddleware(jwtVerifier)       // Authorization: Bearer <jwt> → ContextWithUserID
  session := NewSessionMiddleware(sessionFinder)    // 既存そのまま
  return func(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
      if hasBearer(r) { bearer(next).ServeHTTP(w, r); return }
      session(next).ServeHTTP(w, r)                 // 既存パス完全維持
    })
  }
}
```
- 注入先は既存と同じ `ContextWithUserID` → 下流ハンドラは**一切変更不要**（`UserIDFromContext` で透過的に動く）。
- `router.go` の認証必須 `r.Group` のミドルウェアを `NewSessionMiddleware` → `NewBearerOrSessionMiddleware` に差し替えるのみ。レート制限・logging の順序は不変。

## 1.7 セキュリティ要件
- すべて HTTPS 必須。アクセストークンは短命（15分）。
- リフレッシュトークンは平文保存禁止（ハッシュのみ）。再利用検知で family 全失効。
- `auth_code` は 60 秒・単回・PKCE 紐付け。
- レート制限: `/api/auth/token`・`/refresh` は IP 単位（既存 `unauthIPMW` パターンを流用）。
- JWT 署名鍵は環境変数管理。`kid` でローテーション可能に。

## 1.8 v1 受け入れ基準（サーバー）
- [ ] 既存 Web（Cookie）が一切の変更なく従来通り動作する（回帰なし）。
- [ ] `flow=native` の OAuth がアプリスキームへ `auth_code` を返す。
- [ ] `POST /api/auth/token` が PKCE 検証の上でトークンを発行する。
- [ ] `POST /api/auth/refresh` がローテーションし、旧トークン再利用を family 失効で検知する。
- [ ] Bearer 付き既存 API（subscriptions/items/...）が Cookie と同じ結果を返す。
- [ ] 退会で当該ユーザーの refresh_tokens / auth_codes が削除される。

---

# §2. キーワードプッシュ通知（次フェーズ）

> v1 スコープ外。アプリ UI（`mobile/fm-sheets.jsx` の `FMKeywordSheet`）は実装済みの参考。本節は次フェーズ着手時の設計の正本。

## 2.1 概要
ユーザーが登録したキーワードを、**新着記事のタイトル**に対して照合し、一致したら端末へプッシュ（Android=FCM）。通勤前の事前チェックを支援する。

## 2.2 DB スキーマ

```sql
CREATE TABLE push_devices (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform    TEXT NOT NULL,                  -- 'android' | 'ios'
  push_token  TEXT NOT NULL,                  -- FCM registration token（iOS は APNs token を FCM 経由）
  enabled     BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, push_token)
);

CREATE TABLE keywords (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  term        TEXT NOT NULL,
  scope       TEXT NOT NULL DEFAULT 'title',  -- v1 は 'title' のみ
  enabled     BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, term, scope)
);
CREATE INDEX idx_keywords_user_enabled ON keywords(user_id, enabled);

-- 重複通知抑止（同一 item × keyword は1回）
CREATE TABLE keyword_notifications (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  keyword_id  UUID NOT NULL REFERENCES keywords(id) ON DELETE CASCADE,
  item_id     UUID NOT NULL,
  sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (keyword_id, item_id)
);
```

## 2.3 エンドポイント

| メソッド | パス | 用途 |
|---|---|---|
| POST | `/api/devices` | 端末登録 `{ platform, push_token }`（既存があれば upsert・`enabled=true`） |
| DELETE | `/api/devices/{id}` | 端末登録解除 |
| GET | `/api/keywords` | キーワード一覧（`{ id, term, scope, enabled, hits }`） |
| POST | `/api/keywords` | 追加 `{ term, scope:"title", enabled }` |
| PATCH | `/api/keywords/{id}` | `enabled` / `term` 更新 |
| DELETE | `/api/keywords/{id}` | 削除 |

- `hits`（過去N日の一致数）は `keyword_notifications` の集計で返す（UI 表示用）。
- すべて認証必須グループに追加（§1.6 の複合認証で Bearer/Cookie 両対応）。

## 2.4 マッチング & 配信（ワーカー）

既存フェッチパイプライン（`internal/worker/fetch/fetcher.go`）が**新規 item を永続化した直後**にフックを差し込む。

```
fetcher が新着 item を保存
  → 当該フィードを購読するユーザーの enabled な keywords を取得
  → item.title に term を含むか判定（正規化: NFKC + lowercase。v1 は部分一致）
  → 一致 かつ keyword_notifications に未送信
       → keyword_notifications に INSERT（UNIQUE で二重送信を防止）
       → push ジョブをエンキュー
[Push Worker]（internal/worker/push/ を cleanup ワーカーに倣って新設）
  → user の enabled な push_devices へ FCM 送信
  → 無効トークン（FCM 410/NotRegistered）は push_devices を無効化
```

- マッチングはフェッチのトランザクション内で重い処理をしない（判定→エンキューのみ）。送信は別ワーカーで非同期・リトライ付き。
- 大量一致時のスロットリング（ユーザー単位の送信レート上限）を設ける。

## 2.5 FCM ペイロード

```jsonc
{
  "message": {
    "token": "<fcm token>",
    "notification": { "title": "「Go」に一致する新着", "body": "<記事タイトル>" },
    "data": { "item_id": "<uuid>", "feed_id": "<uuid>", "keyword": "Go",
              "deep_link": "feedman://items/<uuid>" },
    "android": { "priority": "high", "notification": { "channel_id": "keyword_alerts" } }
  }
}
```
- タップで `data.deep_link` により該当記事詳細へ遷移（アプリ側で `GET /api/items/{id}`）。
- **iOS 互換**: 同じ FCM 経由で APNs に配信可能。`platform='ios'` の端末は `apns` ブロックを付与。スキーマ・ロジックは共通で、配信層のみ分岐。

## 2.6 次フェーズ受け入れ基準
- [ ] 端末登録・キーワード CRUD が動作する。
- [ ] 新着フェッチ時にタイトル一致を検出し、未送信のもののみ通知する（重複なし）。
- [ ] 通知タップで該当記事詳細へディープリンク遷移する。
- [ ] 無効 FCM トークンが自動的に無効化される。
- [ ] 退会で devices / keywords / keyword_notifications が削除される。

---

## 付録. 実装順の推奨
1. **§1 トークン認証**（v1 必須）— 複合ミドルウェア → token/refresh/revoke → ネイティブ OAuth フロー → 退会連動。
2. （次フェーズ）**§2 キーワードプッシュ** — スキーマ → CRUD → フェッチフック → Push ワーカー → FCM。

> いずれも「既存 Web・既存エンドポイントの回帰ゼロ」を最優先の受け入れ条件とする。
