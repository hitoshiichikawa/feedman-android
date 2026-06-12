# Feedman Android

[Feedman](https://github.com/hitoshiichikawa/feedman)（RSS/Atom フィードリーダー）の Android ネイティブクライアント。
既存の Go バックエンド API をほぼそのまま流用し、Kotlin + Jetpack Compose で実装する。

- iOS 版: [feedman-ios](https://github.com/hitoshiichikawa/feedman-ios)（先行開発中。Issue 分割・進め方の参考元）
- 開発スタイル: [idd-claude](https://github.com/hitoshiichikawa/idd-claude) による Issue 駆動自動開発

## ドキュメント

| ファイル | 内容 |
|---|---|
| [design/SPEC.md](design/SPEC.md) | アプリ仕様の正本（API 契約 / 画面採用案 / 受け入れ基準） |
| [design/SERVER.md](design/SERVER.md) | サーバー側追加実装（トークン認証 / キーワードプッシュ）の仕様 |
| [docs/GRAND-DESIGN.md](docs/GRAND-DESIGN.md) | アーキテクチャ全体像（パッケージ構成 / レイヤー責務 / 状態同期 / テスト戦略） |
| [design/IDD-CLAUDE-ISSUES.md](design/IDD-CLAUDE-ISSUES.md) | Issue バックログ・Epic/子Issue 対応表・投入手順 |
| [CLAUDE.md](CLAUDE.md) | idd-claude 全エージェント共通のプロジェクト憲章 |
| `design/Feedman Mobile.html` / `design/mobile/*.jsx` | 視覚・挙動のリファレンス（モックデータ。API 形は SPEC §4 が正） |

## 技術スタック（確定）

Kotlin + Jetpack Compose / MVVM + Repository / Coroutines + Flow + Paging 3 /
Retrofit + OkHttp + kotlinx.serialization / Coil / Hilt / Chrome Custom Tabs / min SDK 26。
詳細と決定の経緯は `design/SPEC.md` §2・付録 A を参照。

## v1 スコープ

横断新着タイムライン / フィード別記事一覧 / 記事詳細シート / スター / 横断検索 /
フィード登録・購読設定 / Google ログイン（Bearer トークン認証）/ アカウント管理。
キーワードプッシュ通知（FCM）は次フェーズ（`design/SERVER.md` §2）。

## ビルド・実行

### 前提

- JDK 17（Temurin 推奨。Gradle の Kotlin JVM toolchain で 17 に固定済み）
- Android SDK（compileSdk / targetSdk = **35**、minSdk = **26**）
- 環境変数 `ANDROID_HOME` を Android SDK のインストール先に設定するか、`local.properties` に
  `sdk.dir=` を記載（`local.properties` はコミットしない / `.gitignore` 済み）

### 基本ビルド

リポジトリルートで以下を実行すれば、compile / Android Lint / JVM 単体テストがまとめて
走り、exit code 0 で完了する:

```bash
./gradlew build
```

JVM 単体テストのみ実行する場合:

```bash
./gradlew test
```

### Gradle プロパティ（`feedman.baseUrl` / `feedman.mockMode`）

スケルトンは以下の Gradle プロパティを `BuildConfig` フィールドに反映する。指定方法は
**コマンドラインの `-P` フラグ**または `gradle.properties` への記述のいずれか:

| Gradle プロパティ | BuildConfig フィールド | 未指定時の既定値 | 用途 |
|---|---|---|---|
| `feedman.baseUrl` | `BuildConfig.BASE_URL` | `https://stg-feed.markte-river.net` | 開発サーバー / 本番サーバーの切替（実 API 統合は後続 Issue） |
| `feedman.mockMode` | `BuildConfig.MOCK_MODE` | `false` | `true` でログイン placeholder をスキップしてドロワー + モックタイムラインを起動 |

例: モックモードでビルドする

```bash
./gradlew assembleDebug -Pfeedman.mockMode=true
```

例: baseUrl を上書きしてビルドする

```bash
./gradlew assembleDebug -Pfeedman.baseUrl=https://stg-feed.markte-river.net
```

複数プロパティを永続的に設定するには `gradle.properties` に追記する（リポジトリ直下に
コメントアウト済みの例あり）:

```properties
feedman.mockMode=true
feedman.baseUrl=https://stg-feed.markte-river.net
```

### 機密情報の取り扱い

- 実トークン・本番 API キー・OAuth クライアントシークレットを **ソースコード / Version
  Catalog / `gradle.properties` に埋め込まない**（`CLAUDE.md` 「機密情報の扱い」参照）
- 本番接続情報は `local.properties` や CI secrets を経由して `-P` フラグで渡す運用とする
- `local.properties` / `keystore.properties` / `google-services.json` は `.gitignore` 済み

## CI（GitHub Actions）

`.github/workflows/android-ci.yml` が以下のタイミングで起動し、
`./gradlew build`（コンパイル / Android Lint / JVM 単体テスト）を実行する:

- `main` を base とする Pull Request の作成・更新（`pull_request`）
- `main` へのコミット push（`push`）

実行環境は Ubuntu Latest + JDK 17（Temurin）。Gradle 依存・wrapper・build cache は
`gradle/actions/setup-gradle` によって CI 実行間で再利用される。instrumented テスト
（Android エミュレータ）は CI 必須にしない。

ステータスチェック名は `Build and Unit Test`（job 名）として GitHub Checks API に
報告される。**必須チェック化（branch protection / ruleset の required status check 指定）
はリポジトリ管理者が GitHub Web UI で手動設定する運用**とし、本ワークフローは
チェックの生成・公開までを担当する。

ローカルで同等の検証を行うには:

```bash
./gradlew build
```

## idd-claude 運用

- Epic（`epic` ラベル）には `auto-dev` を付けない。実装は `task` ラベルの子 Issue 単位で投入する
- 子 Issue は依存（本文の `Depends on:`）が満たされたものから人間が `auto-dev` を付けて投入する
- 未充足の依存がある Issue には `blocked` ラベルが付いている
- watcher 起動例:

```bash
REPO=hitoshiichikawa/feedman-android \
REPO_DIR=$HOME/Documents/GitHub/feedman-android \
$HOME/bin/issue-watcher.sh
```
