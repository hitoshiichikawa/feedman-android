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

Android アプリスケルトンは Issue 駆動で作成する（`design/IDD-CLAUDE-ISSUES.md` の T0）。
スケルトン完成後、本節にビルド手順（base URL 設定 / debug 実行 / テスト実行）を記載する。

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
