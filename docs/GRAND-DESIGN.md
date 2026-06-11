# Feedman Android グランドデザイン

> **バージョン**: v1.0 / 2026-06-12
> **位置づけ**: `design/SPEC.md`（仕様の正本）を実装に落とすための**アーキテクチャの正本**。
> idd-claude の Architect が各 Issue で生成する `docs/specs/<N>-<slug>/design.md` は、本書の構成・命名・責務分離に従うこと。本書と SPEC が矛盾した場合は SPEC（API 契約・画面挙動）が優先。

## 0. ドキュメントの関係

| ドキュメント | 役割 |
|---|---|
| `design/SPEC.md` | **何を作るか**: API 契約（§4）・画面採用案（§5）・共通挙動（§6）・受け入れ基準（§10） |
| `design/SERVER.md` | サーバー側（`hitoshiichikawa/feedman`）に追加されるトークン認証 / プッシュ通知の契約 |
| 本書 `docs/GRAND-DESIGN.md` | **どう作るか**: モジュール / パッケージ構成・レイヤー責務・横断設計・テスト戦略 |
| `design/IDD-CLAUDE-ISSUES.md` | **どの順で作るか**: Epic / 子 Issue の対応表・依存関係・投入手順 |
| `design/Feedman Mobile.html` / `design/mobile/*.jsx` | 視覚基準（モックデータ。API 形は SPEC §4 が正） |

---

## 1. 全体方針

- **単一 Gradle モジュール（`:app`）+ パッケージ分割**。v1 の規模（画面 8 前後・API 約 20）ではマルチモジュールの管理コストが利益を上回るため採用しない。パッケージ境界を §3 のとおり厳密に保ち、将来のモジュール分割に耐える形にする。
- **MVVM + Repository、単方向データフロー**（SPEC §2 確定）。UI → ViewModel（イベント）→ Repository（suspend / Flow）→ API、状態は `StateFlow<UiState>` で UI へ一方向に流す。
- **Repository はインターフェース + 実装の分離**。実装前の画面開発・テスト・モックモードは Fake 実装で行う。
- **サーバー契約は流用が前提**。アプリ都合でサーバー変更を要求しない（v1 の例外はトークン認証のみ。`feedman` リポジトリ側 Issue #163 系で並行実装中）。

### 確定済み技術スタック（SPEC §2・付録 A）

Kotlin / Jetpack Compose (Material 3) / min SDK 26 / Coroutines + Flow / Paging 3 /
Retrofit + OkHttp + kotlinx.serialization / Coil / Hilt / Chrome Custom Tabs / FCM（次フェーズ）。
ビルドは Gradle Kotlin DSL + Version Catalog（`gradle/libs.versions.toml`）、JDK 17、依存バージョンはスケルトン作成時点の最新安定版を採用する。

---

## 2. アプリケーション識別子・環境

| 項目 | 値 |
|---|---|
| applicationId / namespace | `com.feedman.android` |
| ディープリンク（v1） | `feedman://auth/callback`（OAuth コールバック） |
| ディープリンク（次フェーズ） | `feedman://items/{id}`（プッシュ通知タップ） |
| API base URL | `BuildConfig.BASE_URL`。Gradle プロパティ `feedman.baseUrl` で上書き。debug 既定は開発サーバー |
| モックモード | Gradle プロパティ `feedman.mockMode=true` → `BuildConfig.MOCK_MODE`。Hilt が Fake リポジトリ群を bind し、ログイン不要で全画面をモックデータ閲覧できる（サーバー認証実装前の UI 開発・スクリーンショット確認用） |

---

## 3. パッケージ構成（正本）

```
app/src/main/kotlin/com/feedman/android/
├── FeedmanApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                # single-activity。ディープリンク受領
│
├── core/
│   ├── model/                    # APIドメインモデル（kotlinx.serialization @Serializable）
│   │   ├── Item.kt               #   CrossFeedItem / ItemSummary / ItemDetail / StarredItemSummary / ItemSearchHit
│   │   ├── Subscription.kt       #   Subscription / Feed
│   │   ├── User.kt
│   │   └── Page.kt               #   ページレスポンス共通（items / next_cursor / has_more / since_time）
│   ├── network/
│   │   ├── FeedmanApi.kt         #   Retrofit インターフェース（SPEC §4.2 の全エンドポイント）
│   │   ├── ApiClientFactory.kt   #   OkHttp + Retrofit + kotlinx.serialization 構成
│   │   ├── FeedmanException.kt   #   エラーボディ {error:{code,...}} の型付き表現（§5.1）
│   │   ├── AuthInterceptor.kt    #   Authorization: Bearer 付与
│   │   ├── TokenAuthenticator.kt #   401 → refresh → 1回だけ再試行（§5.3）
│   │   └── paging/               #   カーソルページネーション共通 PagingSource 基盤（§5.2）
│   ├── auth/
│   │   ├── AuthRepository.kt     #   interface + DefaultAuthRepository（token交換/refresh/revoke/me）
│   │   ├── TokenStore.kt         #   EncryptedSharedPreferences + Keystore 保管
│   │   ├── Pkce.kt               #   code_verifier / code_challenge(S256) 生成
│   │   ├── AuthCallback.kt       #   feedman://auth/callback?auth_code=... のパース
│   │   └── SessionState.kt       #   認証状態 (LoggedOut / LoggedIn / Restoring) の StateFlow
│   ├── data/
│   │   ├── ItemRepository.kt     #   cross-feed / feed items / starred / search / detail / state更新
│   │   ├── SubscriptionRepository.kt # 購読一覧 / settings / resume / fetch / 解除
│   │   ├── FeedRepository.kt     #   フィード登録（POST /api/feeds）
│   │   ├── UserRepository.kt     #   /auth/me 表示用 / 退会
│   │   ├── ItemStateStore.kt     #   既読/スターの楽観的更新オーバーレイ（§5.4・横断同期の要）
│   │   └── fake/                 #   Fake* 実装（モックモード・テスト・Compose Preview 共用）
│   ├── designsystem/
│   │   ├── FeedmanTheme.kt       #   Material3 ColorScheme へのトークンマッピング（ライト/ダーク）
│   │   ├── FeedmanColors.kt      #   oklch グレースケール + Indigo accent を ARGB 定数化（§5.5）
│   │   └── Dimens.kt             #   角丸 10–16px / 最小タップ 44dp / アイコン 18–22dp
│   └── ui/                       # 機能横断の共通 Compose 部品（プロト fm-ui.jsx 対応）
│       ├── Favicon.kt            #   data URL デコード + レターアバターfallback
│       ├── ArticleCard.kt        #   タイムライン/一覧共通カード
│       ├── ArticleMeta.kt        #   スター・はてブ数・相対日時・既読opacity
│       ├── StateViews.kt         #   Loading / Empty / Error(+retry) / 終端表示
│       ├── FeedmanSheet.kt       #   ボトムシート共通枠
│       └── RelativeTime.kt       #   相対日時フォーマッタ（Clock 注入・(推定) 表示）
│
├── feature/
│   ├── login/                    # Google ログイン（Custom Tabs + PKCE）
│   ├── timeline/                 # 横断新着タイムライン（主画面）
│   ├── feed/                     # フィード別記事一覧（フィルタ・状態バナー・手動フェッチ）
│   ├── articledetail/            # 記事詳細 部分ボトムシート
│   ├── starred/                  # スター一覧
│   ├── search/                   # 横断検索
│   ├── registerfeed/             # フィード登録シート
│   ├── subscriptionsettings/     # 購読設定シート（間隔/再開/解除）
│   └── account/                  # アカウントシート（me/ログアウト/退会）
│
├── shell/                        # アプリシェル
│   ├── AppShell.kt               #   Scaffold + TopAppBar + ModalNavigationDrawer
│   ├── DrawerContent.kt          #   ドロワー（すべての新着/お気に入り/フィード一覧/フッタ）
│   └── Navigation.kt             #   NavHost とルート定義（§5.6）
│
└── di/                           # Hilt modules（Network / Auth / Repository / Fake切替）
```

- 各 `feature/<name>/` は `<Name>Screen.kt`（Composable）+ `<Name>ViewModel.kt`（+必要なら `<Name>UiState.kt`）で構成する。
- **依存方向**: `feature/* → core/*`、`shell → feature/*, core/*`。`core/*` から `feature/*` への依存は禁止。`feature` 間の直接依存も禁止（画面間連携はナビゲーション引数と `core/data` 経由で行う）。
- テストは `app/src/test/kotlin/`（JVM 単体・最優先）と `app/src/androidTest/`（Compose UI・最小限）。fixture JSON は `app/src/test/resources/fixtures/`。

---

## 4. レイヤー責務

| レイヤー | 担当 | してはいけないこと |
|---|---|---|
| Composable（Screen / 部品） | `UiState` の描画とイベント発火。stateless 優先 | Repository / API 直接呼び出し、ビジネスロジック |
| ViewModel | UiState の組み立て、イベント処理、Paging Flow の保持（`cachedIn(viewModelScope)`）、楽観的更新の指揮 | HTTP / JSON / 永続化の詳細を知ること |
| Repository（`core/data`, `core/auth`） | API 呼び出し、`FeedmanException` への変換、ページネーション、`ItemStateStore` 反映 | UI 文言の決定、Android UI 依存 |
| `core/network` | HTTP / 認証ヘッダ / 401 リフレッシュ / エラーボディのデコード | 画面・機能固有の知識 |

**UiState 規約**: 画面ごとに immutable data class（または sealed interface）を 1 つ定義し、`StateFlow` で公開する。`LiveData` は使わない。エラーは `FeedmanException` の `message` をユーザー表示の基本とする（SPEC §4.3）。

---

## 5. 横断設計（全 Issue 共通の決めごと）

### 5.1 API エラー

- 非 2xx は統一フォーマット `{ "error": { code, message, category, action, details? } }`（SPEC §4.3）。
- `core/network` でデコードし `FeedmanException(code, message, category, action, retryAfterSeconds?, httpStatus)` として throw。デコード不能・ネットワーク断は `code=NETWORK_ERROR` 等の合成コードで同型に寄せる。
- 画面は `code` で分岐（例: `FEED_COOLDOWN` → `details.retry_after_seconds` を案内）、それ以外は `message` 表示 + 必要に応じ再試行ボタン。

### 5.2 カーソルページネーション

- 一覧 4 種（cross-feed / feed items / starred / search）はすべて `{ items, next_cursor, has_more }`。Paging 3 の共通 `PagingSource` 基盤を `core/network/paging` に置き、「次キー = `next_cursor`、終端 = `has_more == false || next_cursor.isNullOrEmpty()`」を一元実装する。
- **cross-feed のみ `since_time` を返す**: 初回ページの `since_time` をセッション中固定し、後続ページ取得に使う（SPEC §4.1）。保持は timeline の Pager を生成する Repository 側で行い、pull-to-refresh（`PagingSource` invalidate ではなく Pager 再生成）でリセットする。
- カーソル文字列はパースせず不透明トークンとして扱う。

### 5.3 認証（SERVER.md §1 と対）

```
[App] Custom Tabs: /auth/google/login?flow=native&code_challenge=<S256>
   → Google 認可 → サーバーが feedman://auth/callback?auth_code=... へリダイレクト
[App] MainActivity がディープリンク受領
   → POST /api/auth/token { auth_code, code_verifier }
   → access(15分, JWT) + refresh(30日, opaque) を TokenStore に保存
以降: AuthInterceptor が Bearer 付与
401: TokenAuthenticator が /api/auth/refresh → 成功なら元リクエストを 1 回だけ再試行
     refresh も失敗 → トークン破棄 → SessionState を LoggedOut へ（全画面はログインへ戻る）
ログアウト: POST /api/auth/revoke + TokenStore クリア
```

- トークン保管は EncryptedSharedPreferences（Android Keystore 鍵）。平文 SharedPreferences 禁止。
- リフレッシュは Mutex で単一飛行（並行 401 で refresh を多重実行しない）。ローテーションされた refresh token は即座に上書き保存。
- `AuthRepository` インターフェースで抽象化し、認証方式の将来変更に備える（SPEC §3）。
- **サーバー依存**: `feedman` リポジトリの Umbrella Issue #163（token/refresh/revoke/flow=native）が前提。完了までは Fake 認証 + モックモードで他機能を先行開発する。

### 5.4 既読・スターの楽観的更新と画面間同期

複数画面（タイムライン / フィード別 / スター / 検索 / 詳細）が同じ記事を別インスタンスで表示するため、**`ItemStateStore`（シングルトン）をオーバーレイ**として使う:

- `StateFlow<Map<itemId, ItemStateOverride(isRead?, isStarred?)>>` を保持。
- 各一覧は Paging データと overlay を `combine` して表示状態を作る（サーバー由来の値 < overlay）。
- 更新フロー: UI イベント → 即 overlay 反映（楽観的）→ `PUT /api/items/{id}/state` → 失敗時は overlay をロールバックしエラー表示（SPEC §6）。
- 既読化トリガは「詳細シートを開いた時」「外部リンクを開いた時」の 2 つ。

### 5.5 テーマ・デザイントークン

- 正本はプロト `design/mobile/fm-data.jsx` の `FM_THEME`（oklch グレースケール + アクセント Indigo `oklch(0.55 0.17 264)`）。
- Compose は oklch を直接扱えないため、**スケルトン時点で ARGB へ変換した定数を `FeedmanColors.kt` に固定**し、換算元の oklch 値をコメントで残す。Material3 `ColorScheme`（light/dark）へマッピングし、独自トークンが必要な箇所（カード背景・既読 opacity 0.55 等）は `FeedmanTheme` の拡張プロパティで提供する。
- テーマモード: 端末追従 + 手動上書き（DataStore に保存）。
- タイポは Geist 近似（システムフォント開始、必要なら後続 Issue でフォント同梱）。

### 5.6 ナビゲーション

- **採用: 左ドロワー + 単一 NavHost**（SPEC §5.0、下タブ不採用）。
- ルート: `timeline`（起動後の初期画面）/ `feed/{feedId}` / `starred` / `search`。記事詳細・フィード登録・購読設定・アカウントは**ルートではなくボトムシート**（ModalBottomSheet）として呼び出し元画面上に表示する。
- 認証分岐: `SessionState` が `LoggedOut` のときはシェルごと Login 画面へ差し替える（NavHost 外側で分岐）。
- v1 ではドロワーフッタの「キーワード通知」導線を**表示しない**（SPEC §5.8）。

### 5.7 外部リンク

- 記事リンクは Chrome Custom Tabs で開く（開いた時点で既読化）。設定での完全外部 `ACTION_VIEW` 切替は v1 では実装せず、`LinkOpener` インターフェースだけ用意して将来に備える。

---

## 6. テスト戦略

| 対象 | 手段 | 必須度 |
|---|---|---|
| モデルのデコード | fixture JSON + kotlinx.serialization（nullable / 型差分を重点的に） | 必須 |
| APIClient / エラー / 401 リフレッシュ | MockWebServer（Retrofit をモックしない） | 必須 |
| Repository / ItemStateStore | runTest + Turbine（楽観的更新→ロールバックを含む） | 必須 |
| ViewModel | Fake リポジトリ + runTest | 必須 |
| Compose UI | 共通部品（Favicon / ArticleCard / StateViews）の最小スモーク | 任意（androidTest。CI 必須にしない） |

- CI（GitHub Actions）は `./gradlew build`（compile + lint + JVM unit tests）を PR 必須チェックとする。エミュレータ依存のテストは CI 必須にしない。
- 各 Issue の受入基準（EARS）と 1 対 1 のテストを置く（`CLAUDE.md` テスト規約）。

---

## 7. フェーズ計画

| フェーズ | 内容 |
|---|---|
| v1 | SPEC §1.2 の全機能（タイムライン / フィード別 / 詳細 / スター / 検索 / 登録 / 購読設定 / 認証 / アカウント）+ ライト/ダーク + CI |
| 次フェーズ | キーワードプッシュ通知（FCM・`design/SERVER.md` §2）、一括既読、起動同期、外部ブラウザ切替設定 |

v1 のリリース判定は SPEC §10 の受け入れ基準を正とする。

---

## 8. idd-claude 運用ルール（このリポジトリでの取り決め）

- **Epic**（`epic` ラベル）は管理用。`auto-dev` を付けない。実装は `task` ラベルの子 Issue 単位。
- 子 Issue の粒度目安: **変更ファイル 3〜8 個 / 受入基準 3〜6 個 / 1 Issue = 1 PR**。Architect の tasks が 8 個を超えそうなら分割に戻す（iOS 版での教訓。`design/IDD-CLAUDE-ISSUES.md` 参照）。
- 「API 型」「Repository」「ViewModel + UI」「結合 polish」を同一 Issue に詰め込まない。横断関心事（認証 refresh / Keystore / Custom Tabs / 状態同期 / push deeplink）は単独 Issue。
- 依存は Issue 本文の `Depends on: #N` に記載。未充足の Issue には `blocked` ラベルを付け、**依存 PR が merge されてから人間が `blocked` を外し `auto-dev` を付けて投入**する。
- サーバー依存（`feedman` #163 系）が未完了の間、認証実エンドポイントに触る Issue は投入しない。
- base branch は `main`（単一ブランチ運用で開始。多段運用が必要になったら `IDD_CLAUDE_BASE_BRANCH` で切替）。
