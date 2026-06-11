# Requirements Document

## Introduction

Feedman Android はゼロから開始するため、後続の idd-claude 実装が機能単位で継続できる最小の
Android プロジェクト基盤（Gradle 構成・パッケージ骨格・テスト構成・モック起動経路）を整える必要がある。
本要件は `docs/GRAND-DESIGN.md` §3 のパッケージ構成と §4 のレイヤー責務、および `design/SPEC.md` §2
で確定したスタック（Kotlin + Jetpack Compose / Material 3 / min SDK 26 / Hilt 等）に整合する
スケルトンの成立条件を定義する。実 OAuth・実 API 統合・デザインシステム確定・CI 整備は本 Issue の
スコープ外であり、後続 Issue（Epic #4・#14）で扱う。スケルトン段階では、ログイン placeholder 画面と
`feedman.mockMode=true` 時のドロワーシェル + モックタイムラインを提供することで、以後の機能 Issue が
独立に Repository / ViewModel / Composable を差し込めることをゴールとする。

## Requirements

### Requirement 1: ビルド可能な Gradle プロジェクト基盤

**Objective:** As a Android アプリ開発者, I want `./gradlew build` が成功する最小プロジェクトが配置されていること, so that 以後の機能 Issue がビルド通過を前提に実装を進められる

#### Acceptance Criteria

1. When 開発者がリポジトリルートで `./gradlew build` を実行したとき, the Feedman Android Build shall コンパイル・lint・JVM 単体テストを成功させ exit code 0 で終了する
2. The Feedman Android Build shall 最低 SDK レベル 26 (Android 8.0) を applicationId / namespace `com.feedman.android` のアプリモジュールに適用する
3. The Feedman Android Build shall JDK 17 を toolchain として宣言し、Kotlin と Jetpack Compose (Material 3) でのビルドを成立させる
4. The Feedman Android Build shall Gradle Kotlin DSL（`settings.gradle.kts` / `build.gradle.kts`）と Version Catalog（`gradle/libs.versions.toml`）でビルドスクリプトと依存定義を表現する
5. The Feedman Android Build shall Gradle wrapper（`gradlew` / `gradlew.bat` / `gradle/wrapper/*`）をリポジトリにコミットされた状態で提供する
6. The Feedman Android Build shall 少なくとも 1 件の JVM 単体テスト（`app/src/test/` 配下）を含み、`./gradlew build` 実行時に成功する
7. If Version Catalog に列挙する依存ライブラリのバージョンを宣言するとき, the Feedman Android Build shall 各依存の最新安定版を採用し alpha / beta / RC バージョンを既定として宣言しない

### Requirement 2: パッケージ骨格と依存方向

**Objective:** As a Android アプリ開発者, I want `docs/GRAND-DESIGN.md` §3 のパッケージ骨格が placeholder 込みで配置されていること, so that 後続 Issue がレイヤー責務・依存方向の規約に沿って差し込み実装できる

#### Acceptance Criteria

1. The Feedman Android Skeleton shall パッケージルート `com.feedman.android` を `app/src/main/kotlin/com/feedman/android/` 配下に持つ
2. The Feedman Android Skeleton shall `core/model` / `core/network` / `core/auth` / `core/data` / `core/designsystem` / `core/ui` の各サブパッケージを配置する
3. The Feedman Android Skeleton shall `feature/login` / `feature/timeline` / `feature/feed` / `feature/articledetail` / `feature/starred` / `feature/search` / `feature/registerfeed` / `feature/subscriptionsettings` / `feature/account` の各 feature パッケージを配置する
4. The Feedman Android Skeleton shall `shell` パッケージと `di` パッケージを配置する
5. The Feedman Android Skeleton shall `@HiltAndroidApp` を付与した `FeedmanApplication` クラスと、single-activity 構成の `MainActivity` クラスを配置する
6. The Feedman Android Skeleton shall `feature/*` から `core/*` への依存のみを許容し、`core/*` から `feature/*` への参照および `feature/*` 同士の直接参照を持たない
7. Where `core/*` または `feature/*` のサブパッケージが具体実装を持たないとき, the Feedman Android Skeleton shall そのパッケージを保持する placeholder（空ファイル・KDoc 付き型宣言等）を配置し、空ディレクトリのみの状態にしない

### Requirement 3: DI と Repository 抽象化

**Objective:** As a 後続 Issue の実装者, I want Repository がインターフェース + Fake 実装で Hilt にバインドされていること, so that 機能 Issue が実装を差し替えながら独立に進められる

#### Acceptance Criteria

1. The Feedman Android Skeleton shall Hilt を DI コンテナとして組み込み、`di` パッケージに Hilt module を集約する
2. The Feedman Android Skeleton shall 少なくとも 1 つの Repository（例: `ItemRepository`）をインターフェースとして公開し、対応する Fake 実装を `core/data/fake` 配下に提供する
3. When Hilt がアプリ起動時に Repository を解決するとき, the Feedman Android Skeleton shall Fake 実装をデフォルトのバインディングとして注入する
4. While スケルトン状態にあるとき, the Feedman Android Skeleton shall 手動でのシングルトン保持（`object` による直書きシングルトン等）に依存せず、Hilt module 経由でインスタンスを供給する

### Requirement 4: 起動経路（ログイン placeholder / モックモード）

**Objective:** As a 動作確認者, I want 認証なしでも起動でき、モックモード下では実画面骨格を確認できること, so that 認証実装・API 実装が揃う前から UI 開発と動作確認が継続できる

#### Acceptance Criteria

1. When アプリが資格情報を保持しない状態で起動したとき, the Feedman Android App shall ログイン placeholder 画面を最初の画面として表示する
2. While ログイン placeholder 画面が表示されているとき, the Feedman Android App shall 後続の認証 Issue が差し込めるよう、実 OAuth フローを呼び出さない
3. Where Gradle プロパティ `feedman.mockMode=true` が指定されてビルドされたとき, the Feedman Android App shall ログイン placeholder を経由せず、ドロワーベースのアプリシェルとモックタイムラインを起動直後に表示する
4. While モックモードで起動しているとき, the Feedman Android App shall タイムラインにモックデータ（Fake Repository が提供する記事一覧）を表示する
5. Where Gradle プロパティ `feedman.mockMode` が未指定または `false` のとき, the Feedman Android App shall ログイン placeholder 画面を起動経路として採用する

### Requirement 5: ビルド構成プロパティ（baseUrl / mockMode）

**Objective:** As a 動作確認者, I want 外部 API URL とモードを Gradle プロパティで切替えられること, so that 開発サーバー・本番サーバー・モック起動を再ビルドだけで切り替えられる

#### Acceptance Criteria

1. When Gradle プロパティ `feedman.baseUrl` の値を変更してビルドしたとき, the Feedman Android Build shall `BuildConfig.BASE_URL` を指定された値で生成する
2. When Gradle プロパティ `feedman.mockMode` の値を変更してビルドしたとき, the Feedman Android Build shall `BuildConfig.MOCK_MODE` を指定された真偽値で生成する
3. If Gradle プロパティ `feedman.baseUrl` が未指定でデバッグビルドを行ったとき, the Feedman Android Build shall 開発サーバー向けの既定 URL を `BuildConfig.BASE_URL` に設定する
4. If Gradle プロパティ `feedman.mockMode` が未指定のとき, the Feedman Android Build shall `BuildConfig.MOCK_MODE` を `false` として生成する
5. The Feedman Android Build shall 実トークン・実 API キー等の機密値をソースコードや Version Catalog に埋め込まない

### Requirement 6: ビルド手順ドキュメント

**Objective:** As a 新規参加者, I want スケルトンのビルド手順が `README.md` に書かれていること, so that 初回 clone から `./gradlew build` 成功まで迷わず到達できる

#### Acceptance Criteria

1. The Feedman Android Repository shall `README.md` にビルド手順節を設け、必要な JDK バージョン・`./gradlew build` 実行コマンド・モックモード起動方法（`feedman.mockMode=true`）を記述する
2. The Feedman Android Repository shall `README.md` に `feedman.baseUrl` および `feedman.mockMode` の指定方法を記述する

## Non-Functional Requirements

### NFR 1: 依存バージョン方針

1. The Feedman Android Build shall すべての主要依存（Kotlin / AGP / Compose BOM / Hilt / Retrofit / OkHttp / kotlinx.serialization / Coil / Paging 3 / Coroutines / Compose Material 3）について安定版（GA）を Version Catalog で宣言する
2. If 安定版が存在しない依存（例: 一部 androidx ライブラリ）を採用する必要があるとき, the Feedman Android Build shall `README.md` または Version Catalog コメントに理由を明記する

### NFR 2: テスト基盤の到達性

1. The Feedman Android Repository shall `app/src/test/` ディレクトリを JVM 単体テストの配置先として有効化し、`./gradlew test` または `./gradlew build` 経由でテストを実行可能にする
2. The Feedman Android Repository shall `app/src/androidTest/` ディレクトリを Compose UI / instrumented テストの将来配置先として有効化するが、本 Issue では CI 必須化しない

### NFR 3: ビルドの再現性

1. The Feedman Android Repository shall Gradle wrapper のバージョンを `gradle/wrapper/gradle-wrapper.properties` に固定し、リポジトリを clone した直後の `./gradlew build` がローカルの Gradle インストールに依存せず成功する

## Out of Scope

- 実 Google OAuth ログインフロー（`POST /api/auth/token` 等）の実装（後続 Issue / `design/SPEC.md` §3.2）
- `feedman` サーバーの実 API（タイムライン・購読・記事状態など）との結合
- プロトタイプ（`design/Feedman Mobile.html` / `design/mobile/*.jsx`）との視覚的な完全一致およびデザインシステムの確定（Epic #4 のスコープ）
- CI（GitHub Actions）の整備および PR 必須チェック化（#14 のスコープ）
- キーワードプッシュ通知（FCM）・OPML 入出力・オフライン全文キャッシュなど v1 スコープ外機能
- リリース署名鍵（keystore）・`google-services.json` 等の実クレデンシャル配置
- `core/network` の Retrofit インターフェース・実 `FeedmanApi` 実装（後続 Issue でモデル単位に分割）

## Open Questions

- なし（本 Issue のスコープは `docs/GRAND-DESIGN.md` および `design/SPEC.md` で確定済みの範囲に閉じており、追加判断は不要）
