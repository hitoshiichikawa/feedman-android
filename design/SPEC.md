# Feedman Android アプリ 開発仕様書

> **対象読者**: Codex（コーディングエージェント）および実装担当者
> **バージョン**: v1.0 / 2026-06-08
> **入力成果物**: `Feedman Mobile.html`（Android プロトタイプ）, `Feedman iPhone.html`（iOS 版・参考）
> **既存システム**: `feedman/`（Go バックエンド + Next.js Web フロント、単一オリジン構成）

このドキュメント単体で実装に着手できることを目標とする。**API リファレンスは実コード（`feedman/internal/handler/router.go` ほか）から抽出した正確な契約**であり、推測を含まない。プロトタイプはモックデータで動作する**視覚・挙動のリファレンス**として併用すること（§9 参照）。

---

## 0. このドキュメントとプロトタイプを Codex にそのまま渡してよいか

**結論: プロトタイプ単体では不十分。本書と併せて渡すこと。** 理由と役割分担は以下。

| 成果物 | 信用してよい範囲 | 信用してはいけない範囲 |
|---|---|---|
| 本仕様書（.md） | API 契約・画面挙動・受け入れ基準・採用案 | — |
| プロトタイプ（.html） | 画面レイアウト・インタラクション・状態遷移・配色/タイポ | **データはすべてモック**。API 形・ページネーション・エラーは未反映。React Web であり Android ネイティブ実装ではない |

Codex への推奨インプット順:
1. 本書（`SPEC.md`）
2. プロトタイプ HTML（「この見た目・挙動を Android ネイティブで再現せよ」の視覚基準として）
3. 既存リポジトリ `feedman/`（API 実装の最終的な真実。型は §4 に転記済みだが、疑義があれば `internal/handler/` を参照）

> **重要**: プロトタイプの Tweaks（複数案の出し分け）は**探索用**。実装では §5 の「採用案」を唯一の正とすること。エージェントに「Tweaks を実装せよ」と誤解させないため、本書では各画面の決定を 1 つに固定している。

---

## 1. 概要

Feedman は RSS/Atom フィードリーダー。Google OAuth、フィード横断の新着タイムライン、はてなブックマーク連携を持つ。本プロジェクトは既存 Web の **API をほぼそのまま流用**して Android ネイティブアプリを新規開発する。

### 1.1 想定ユーザー / 主要ユースケース
- 通勤中のニュースチェック。複数フィードの新着を 1 タイムラインで消化。
- 気になる記事は外部ブラウザ（Custom Tabs）で本文を読む。
- **キーワードを登録しておき、一致する新着タイトルが来たらプッシュ通知**（→ サーバー新設。§7）。

### 1.2 v1 スコープ
すべての新着（横断タイムライン）／フィード別記事一覧／記事詳細閲覧／スター一覧／横断検索／フィード登録／購読設定（間隔・解除・再開）／Google ログイン（トークン認証）／アカウント（ログアウト・退会）。
（キーワードプッシュ通知は次フェーズ。§1.3 / §7 参照）

### 1.3 スコープ外（v1 では作らない）
- **キーワードプッシュ通知 → 次フェーズ**（サーバー側も次バージョンで対応。UI も次フェーズに送る。§7 参照）。
- フィード内検索 UI（API はあるが横断検索に集約）、フィード URL 変更 UI、OPML 入出力、オフライン全文キャッシュ。

---

## 2. 技術前提（要確定）

> **確定済み（2026-06-08）**。以下のスタックで実装する。

| 項目 | 選定 | 備考 |
|---|---|---|
| 言語 / UI | **Kotlin + Jetpack Compose** | プロトタイプの宣言的構造と相性が良い |
| 最低 SDK | API 26 (Android 8.0) | プッシュ・Custom Tabs の前提を満たす |
| アーキテクチャ | MVVM + Repository、単方向データフロー | プロトの `actions`/`state` 分離に対応 |
| 非同期 / ページング | Coroutines + Flow、Paging 3 | カーソルページネーション（§4.1）に Paging 3 を割り当て |
| ネットワーク | Retrofit + OkHttp + kotlinx.serialization | — |
| 画像 | Coil | favicon は data URL（§4.2 注意） |
| 外部リンク | **Chrome Custom Tabs** | 完全外部 `ACTION_VIEW` への切替も設定で可能に |
| プッシュ | **FCM**（Firebase Cloud Messaging） | §7 のサーバー新設が前提 |
| DI | Hilt | — |

---

## 3. 認証（最重要の設計判断）

既存 Web は **Cookie セッション（`SameSite=Lax`）+ CORS 制限 + 同一オリジン相対パス**で動作する。ネイティブアプリはオリジンを持たないため、この方式をそのまま使うと OAuth リダイレクトと Cookie 共有で破綻する。**ここが「流用が苦しい」唯一の中核。**

### 3.1 既存の認証フロー（Web）
- `GET /auth/google/login` → Google 認可画面へリダイレクト
- `GET /auth/google/callback` → セッション Cookie を発行し、フロントのオリジン（例 `http://localhost:3000`）へリダイレクト
- `POST /auth/logout` / `GET /auth/me`（現在ユーザー）
- 以降の API は Cookie を自動送信（`credentials: "include"`）

> **確定（2026-06-08）: 方針 A（トークン認証）を v1 から採用。** サーバー側のトークン発行・検証エンドポイントを v1 のうちに新設する。下記 3.2 を実装方針とする（3.3 の暫定案は不採用だが、`AuthRepository` 抽象化の指針としては有効）。

### 3.2 方針 A（採用・サーバー新設）— トークン認証
OAuth 完了後に**短命アクセストークン + リフレッシュトークン**を発行する。

```
POST /api/auth/token        # OAuth 完了コード/一時鍵 → { access_token, refresh_token, expires_in }
POST /api/auth/refresh      # { refresh_token } → 新しい access_token
```
- アプリは `Authorization: Bearer <access_token>` を全 API に付与。
- トークンは Android Keystore + EncryptedSharedPreferences に保管。
- ネイティブの OAuth は Custom Tabs + アプリリンク（`feedman://auth/callback`）でコールバックを受ける。
- **サーバー作業**: トークン発行・検証ミドルウェアの追加。既存 Cookie セッションと並存可能（`internal/middleware` にベアラ検証を追加し、`SessionMiddleware` の前段で許可）。

### 3.3 方針 B（不採用・参考）— WebView ログイン + Cookie 流用
新設なしで先行着手する代替案だったが、§3.2 の方針 A を採用したため**実装しない**。`AuthRepository` 抽象化の設計参考としてのみ残す。
- ログインのみ WebView（または Custom Tabs）で既存 `/auth/google/login` を開く。
- 発行された Cookie を `CookieManager` から OkHttp の `CookieJar` へ引き継ぎ、API 呼び出しに利用。
- 制約: Cookie の `SameSite`/`Secure`/ドメイン設定に依存。サーバーの CORS 許可オリジンにアプリ用スキームの追加が必要な場合あり。**中長期は方針 A へ移行前提。**

> **Codex への指示**: 認証層は `AuthRepository` インターフェースで抽象化する。**v1 は方針 A で実装**（サーバーのトークン発行・検証が前提）。抽象化により将来の方式変更にも耐えられるようにする。

---

## 4. API リファレンス（実装に対する正本）

- すべて JSON。認証必須エンドポイントは §3 のトークン or Cookie を付与。
- ベースパス: 既存と同じ（同一オリジン）。アプリでは `BASE_URL` を環境で切替。
- エラーは統一フォーマット（§4.3）。

### 4.1 ページネーション（カーソル方式）
一覧系は共通で以下を返す:
```jsonc
{ "items": [...], "next_cursor": "<string|null>", "has_more": true }
```
- 次ページ取得は `?cursor=<next_cursor>&limit=<n>`。
- `has_more === false` または `next_cursor` が null/空 のとき終端。
- 横断新着のみ `since_time`（RFC3339）を追加で返す。**セッション初回の `since_time` を固定**して以降のページ取得に使う（新着判定の基準時刻ブレ防止）。
- カーソル形式（参考・パースしないこと）: 横断 `<RFC3339Nano>:<itemID>` / 検索 `<RFC3339Nano>|<uuid>`。

### 4.2 エンドポイント一覧

#### 認証 / ユーザー
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/auth/google/login` | OAuth 開始（リダイレクト） |
| GET | `/auth/google/callback` | OAuth コールバック |
| POST | `/auth/logout` | ログアウト |
| GET | `/auth/me` | 現在ユーザー `{ id, email, ... }` |
| DELETE | `/api/users/me` | 退会（全購読・状態を削除） |
| PUT | `/api/users/me/cross-feed-last-seen` | 横断一覧の最終閲覧時刻を更新（新着バッジ計算用） |

#### 横断新着タイムライン
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/items/cross-feed` | 全フィード横断の新着。50件/回・上限200。`since_time` 付き |

`CrossFeedItem`:
```ts
{ id, feed_id, feed_title, feed_favicon_url: string|null,
  title, link, summary, published_at, is_date_estimated,
  is_read, is_starred, hatebu_count }
```

#### 購読（フィード一覧 / 設定）
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/subscriptions` | 購読一覧（サイドバー） |
| DELETE | `/api/subscriptions/{id}` | 購読解除 |
| PUT | `/api/subscriptions/{id}/settings` | フェッチ間隔等の更新 |
| POST | `/api/subscriptions/{id}/resume` | 停止/エラーフィードの再開 |
| POST | `/api/subscriptions/{id}/fetch` | **手動フェッチ（= Pull-to-refresh の実体）**。同期。クールダウンあり |

`Subscription`:
```ts
{ id, user_id, feed_id, feed_title, feed_url, favicon_url?: string|null,
  fetch_interval_minutes, feed_status: "active"|"stopped"|"error",
  error_message?: string|null, unread_count, created_at }
```

> **Pull-to-refresh の注意**: `POST .../fetch` は**フィード単位**。クールダウン中は `429 / FEED_COOLDOWN` を返し、`details.retry_after_seconds` を含む。横断タイムラインを引っ張って更新する場合、全フィードを叩くのは非現実的。**v1 では「フィード別画面でのみ手動フェッチ（`POST .../fetch`）、横断タイムラインは GET 再取得」と確定**（一括同期 API は次フェーズ候補・§7）。

#### フィード（登録 / 記事一覧 / スター）
| メソッド | パス | 用途 |
|---|---|---|
| POST | `/api/feeds` | フィード登録（URL 自動検出。専用レート制限あり） |
| GET | `/api/feeds/{id}/items` | フィード別記事一覧（`?filter=all\|unread\|starred`） |
| GET | `/api/feeds/starred/items` | 全フィード横断スター一覧 |
| GET | `/api/feeds/{id}` | フィード詳細 |
| PATCH | `/api/feeds/{id}` | フィード URL 変更（v1 UI 対象外） |
| DELETE | `/api/feeds/{id}` | フィード削除（v1 UI 対象外。解除は購読側を使用） |

`ItemSummary` / `ItemDetail`:
```ts
ItemSummary { id, feed_id, title, link, summary, published_at,
  is_date_estimated, is_read, is_starred, hatebu_count,
  hatebu_fetched_at: string|null }
ItemDetail extends ItemSummary { content /* sanitized HTML */, author }
ItemListResponse { items: ItemSummary[], next_cursor, has_more }
StarredItemSummary extends ItemSummary { feed_title }  // スター一覧
```

#### 記事
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/items/{id}` | 記事詳細（`content` 含む） |
| PUT | `/api/items/{id}/state` | 既読/スター更新。body `{ is_read?: bool\|null, is_starred?: bool\|null }` |

#### 検索
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/items/search?q=<kw>` | 横断検索（`scope=global\|feed`、デフォルト global） |

`ItemSearchHit`（ItemSummary とは差分あり・注意）:
```ts
{ id, feed_id, title, link, summary,
  published_at: string|null, is_date_estimated, hatebu_count,
  feed_title, favicon_url: string|null,  // 追加
  is_read, is_starred }
// ※ hatebu_fetched_at は含まれない
```

### 4.3 エラーフォーマット
```jsonc
{ "error": {
    "code": "FEED_COOLDOWN", "message": "...", "category": "...",
    "action": "...",
    "details": { "retry_after_seconds": 30 }  // 429 / FEED_COOLDOWN のときのみ
} }
```
- `4xx`/`5xx` で上記ボディ。アプリは `code` で分岐し、`message` をユーザー表示の基本にする。
- レート制限（429）は `Retry-After` ヘッダも参照。

### 4.4 favicon の扱い（注意）
`feed_favicon_url` / `favicon_url` は **`data:<mime>;base64,...` 形式の data URL** か `null`。
- Coil は data URL を読める。`null` のときは色付きレターアバター（プロトの `FMFavicon` 参照）にフォールバック。

---

## 5. 画面仕様（採用案を固定）

各画面の「採用案」がプロトタイプ Tweaks のどの値かを明記する。**実装は採用案のみ**。代替案は将来検討メモとして残すだけ。

### 5.0 ナビゲーション構造 — 採用: **左ドロワー + 記事ビュー**（Tweak `nav=drawer`）
- トップアプリバー: 左にハンバーガー（ドロワー開）、タイトル/サブタイトル、右に検索・テーマ切替。
- ドロワー内: ヘッダ（ロゴ + ユーザー）／「すべての新着」「お気に入り」／フィード一覧（未読バッジ・状態アイコン・設定）／フッタ（キーワード通知・アカウント・テーマ）。
- 代替（不採用）: 下タブ。比較したい場合のみプロト `nav=bottomtabs`。

### 5.1 新着横断タイムライン — 採用: **カード**（Tweak `timeline=cards`）
- データ: `GET /api/items/cross-feed`（Paging 3、`since_time` 固定、無限スクロール）。
- カード: フィード名 + favicon、相対日時、タイトル（最大3行）、概要（最大2行）、はてブ数、キーワード一致タグ、スター、**外部リンクアイコン**。
- 既読記事は不透明度を下げる（プロト同様 opacity 0.55）。
- タップ = 記事詳細シート（§5.4）。外部リンクアイコンタップ = Custom Tabs で `link` を開く + 既読化。
- Pull-to-refresh: 横断は GET 再取得（§4.2 注意）。

### 5.2 フィード別記事一覧 — 採用カード: **標準**（Tweak `card=standard`）
- データ: `GET /api/feeds/{id}/items?filter=...`。
- 上部にフィルタタブ（すべて / 未読 / スター）→ `filter` クエリに対応。
- フィード状態が `stopped`/`error` のとき上部に警告バナー + 「再開」（`POST .../resume`）。
- Pull-to-refresh = `POST /api/subscriptions/{id}/fetch`（クールダウン 429 をトーストで案内）。

### 5.3 スター一覧 / 検索
- スター: `GET /api/feeds/starred/items`（`feed_title` でソース表示）。
- 検索: `GET /api/items/search?q=`。空状態でサジェストチップ。結果は `ItemSearchHit`（`hatebu_fetched_at` 無し・`favicon_url` 有りに注意）。

### 5.4 記事詳細 — 採用: **部分シート（プレビュー）**（Tweak `detail=partial`）
- ボトムシートで開く。ソース行・タイトル・はてブ/キーワード/スター・本文プレビュー（`content` を約200pxでフェード）+「続きを読む」。
- フッタ固定アクション: **「元記事を開く」（主ボタン, Custom Tabs）** + スター。
- 開いた時点で既読化（`PUT /api/items/{id}/state {is_read:true}`）。
- 代替（不採用）: 全画面シート / リーダー。

### 5.5 フィード登録
- `POST /api/feeds`。URL 入力 → 検出 → 確認 → 登録。専用レート制限と重複登録エラーをハンドリング。

### 5.6 購読設定（ボトムシート）
- フェッチ間隔セグメント（15/30/60/180/360分）→ `PUT /api/subscriptions/{id}/settings`。
- 再開（`POST .../resume`）、購読解除（`DELETE /api/subscriptions/{id}`、確認付き）。

### 5.7 ログイン / アカウント
- ログイン: §3 のフロー。Google ボタン1つ。
- アカウント: `GET /auth/me` 表示、ログアウト（`POST /auth/logout`）、退会（`DELETE /api/users/me`、二段確認）。

### 5.8 キーワードプッシュ通知設定 — **次フェーズ（v1 スコープ外）**
- サーバー新設とセットで次バージョン対応。プロトタイプに UI 案はあるが、**v1 では実装しない**（ドロワーフッタの「キーワード通知」導線も v1 では非表示 or 無効）。詳細は §7。

---

## 6. 共通の挙動・状態

- **既読化のタイミング**: 詳細シートを開いた時、外部リンクを開いた時に `is_read:true`。一覧の見た目に即時反映（楽観的更新 → 失敗時ロールバック）。
- **スター**: 一覧/詳細どこからでもトグル。楽観的更新。
- **テーマ**: ライト/ダーク切替（端末設定追従 + 手動上書き）。トークンはプロトの oklch グレースケール + アクセント1色（§8）。
- **無限スクロール**: 全一覧で Paging 3。終端表示「最後まで読みました」。
- **空状態 / エラー状態 / ローディング**: 各一覧で用意（プロトの `FMEmpty` 参照）。
- **相対日時**: 「1時間以内 / N時間前 / N日前 / 日付」。`is_date_estimated` のとき「(推定)」表示。

---

## 7. 新規 API（サーバー追加が必要）

| 優先 | 機能 | 提案エンドポイント | フェーズ | 目的 |
|---|---|---|---|---|
| ★★★ | トークン認証 | `POST /api/auth/token`, `POST /api/auth/refresh` | **v1** | ネイティブの OAuth/Cookie 問題を解消（§3・採用済） |
| ★★★ | キーワードプッシュ | 下記 7.1 | **次フェーズ** | 通勤前にタイトル一致をプッシュ（主要要望） |
| ★★ | 一括既読 | `PUT /api/feeds/{id}/read-all`, `PUT /api/items/cross-feed/read-all` | 次フェーズ候補 | まとめ既読。現状は1件ずつ |
| ★ | 起動同期 | `GET /api/sync?since=` | 次フェーズ候補 | 購読・未読数・状態を一括取得し起動時リクエスト削減 |

> **v1 で新設するのはトークン認証のみ。** キーワードプッシュ以下は次フェーズ。下記 7.1 は**次フェーズ設計の参考**として残す。

### 7.1 キーワードプッシュ通知（次フェーズ設計メモ）
```
POST   /api/devices            # 端末登録 { platform:"android", fcm_token }
DELETE /api/devices/{id}       # 端末登録解除
GET    /api/keywords           # キーワード一覧
POST   /api/keywords           # 追加 { term, scope:"title", enabled }
PATCH  /api/keywords/{id}      # 有効/無効・term 変更
DELETE /api/keywords/{id}      # 削除
```
- **worker 追加**: 既存のフェッチワーカー（`internal/worker/fetch`）が新着を取り込むたびに、ユーザーの有効キーワードと**タイトル**を突き合わせ、一致したら FCM 送信ジョブをエンキュー。
- 通知ペイロードに `item_id` / `feed_id` を載せ、タップで該当記事詳細へディープリンク（`feedman://items/{id}`）。
- マッチ単位の重複通知抑止（同一 item×keyword は1回）。
- レスポンス型・マッチ履歴（`hits`）は UI（プロトの `FMKeywordSheet`）に合わせて設計。

---

## 8. デザイントークン

プロトタイプ `mobile/fm-data.jsx` の `FM_THEME` が正本。要点:
- **配色**: oklch グレースケール（Web の `globals.css` を移植）。ライト/ダーク両対応。
- **アクセント**: 1色（**Indigo `oklch(0.55 0.17 264)` に確定**）。Coral/Teal/Violet は不採用。
- **角丸**: 10–16px。**タイポ**: Geist（ネイティブは近似フォント or 同梱）。
- **タップ標的**: 最小 44px。アイコン 18–22px。
- 詳細な数値・本文 prose スタイルはプロト HTML の `<style>` と各コンポーネントを参照。

---

## 9. プロトタイプの読み方（Codex 向け）

- `Feedman Mobile.html` … Android 版。**画面・挙動の視覚基準**。
- `Feedman iPhone.html` … iOS 版（参考。Android 実装には不要）。
- `mobile/fm-data.jsx` … トークン・アイコン・**モックデータ形**（実 API 形は §4 が正）。
- `mobile/fm-ui.jsx` … カード/スター/はてブ/Pull-to-refresh 等の共通部品。
- `mobile/fm-screens.jsx` … ヘッダ・ドロワー・各画面。
- `mobile/fm-sheets.jsx` … 詳細・登録・設定・キーワード・ログインの各シート。

**やってよいこと**: レイアウト・余白・状態遷移・空/エラー表示・配色をピクセル単位で参照。
**やってはいけないこと**: モックの JSON 形をそのまま API 型と見なす（§4 を優先）。Tweaks の複数案を実装する（§5 の採用案のみ）。

---

## 10. 受け入れ基準（v1 / 抜粋）

- [ ] Google ログイン → 横断タイムライン表示まで到達できる。
- [ ] 横断タイムラインが無限スクロールし、`since_time` がセッション中固定される。
- [ ] フィード別一覧でフィルタ（すべて/未読/スター）が `filter` クエリで切り替わる。
- [ ] 記事タップで部分シートが開き、開いた時点で既読になる（楽観的更新 + サーバー反映）。
- [ ] 「元記事を開く」で Custom Tabs が起動し、当該記事が既読化される。
- [ ] スターのトグルが一覧/詳細/スター一覧で整合する。
- [ ] フィード別 Pull-to-refresh が `POST .../fetch` を呼び、`FEED_COOLDOWN` 時に `retry_after_seconds` を案内する。
- [ ] フィード登録・購読解除・間隔変更・再開が各エンドポイントで成功する。
- [ ] ライト/ダーク切替が全画面に反映される。
- [ ] （次フェーズ / §7 実装後）キーワード登録 → 一致記事のプッシュ受信 → タップで詳細へ遷移。

---

## 付録 A. 決定事項（2026-06-08 確定済み）
1. ✅ 技術スタック: **Kotlin + Jetpack Compose**（§2 の確定構成）。
2. ✅ 認証方針: **A（トークン認証）を v1 から**。サーバーにトークン発行/検証を新設。
3. ✅ アクセントカラー: **Indigo**。
4. ✅ キーワードプッシュ: **次フェーズ**（サーバー・アプリとも v1 スコープ外）。
5. ✅ 横断 Pull-to-refresh: **GET 再取得のみ**（一括同期 API は設けない）。

→ 本付録の確定値は本文各セクションに反映済み。Codex へは本書（SPEC.md）+ プロトタイプ HTML をそのまま渡してよい。
