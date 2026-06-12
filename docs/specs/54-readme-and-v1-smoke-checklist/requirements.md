# Requirements Document

## Introduction

v1 リリースに向けて子 Issue 群（#24, #34, #38, #42, #43, #45, #46, #48, #51）が出揃った段階で、
新規参加者や次フェーズ担当者がリポジトリを clone してから「ビルドが通り、テストが通り、v1 の主要
機能が動くこと」を自走で確認できる状態を整える。具体的には、(a) README に必要ツール / ビルド・
実行手順 / Gradle プロパティ（`feedman.baseUrl` / `feedman.mockMode`）/ OAuth コールバック
（`feedman://auth/callback`）の前提 / テスト実行方法を網羅し、(b) `design/SPEC.md` §10 の受け入れ
基準を手動で追跡できるスモークチェックリストを別ドキュメントとして配置する。本 Issue 自体は
ドキュメント整備が対象であり、アプリのコード追加・CI 変更・Play Store 提出手順は扱わない。

なお、本 Issue 時点ではサーバー側（Bearer トークン認証エンドポイント）が未デプロイのため、実機で
Google ログイン以降を end-to-end 検証する手順は「サーバーデプロイ後に実施する前提手順」として記載
する（チェックリスト本文に明示）。ローカルで完結する `./gradlew build` / `./gradlew test` /
モックモード起動はドキュメント確定前に実行検証する。

## Requirements

### Requirement 1: README のセットアップ・ビルド・実行手順

**Objective:** As a 新規開発者 (or 次フェーズ担当者), I want README から clone・ビルド・実行・テスト
までを迷わず辿りたい, so that リポジトリを開いて最短で開発環境を立ち上げられる

#### Acceptance Criteria

1. The README shall リポジトリ clone 前に必要なツール（JDK 17 / Android SDK / 環境変数
   `ANDROID_HOME` または `local.properties` の `sdk.dir`）と各々のバージョン要件（compileSdk /
   targetSdk = 35, minSdk = 26）を明示する
2. The README shall リポジトリルートで `./gradlew build` を実行すれば compile / Android Lint /
   JVM 単体テストが exit code 0 で完了することを記述する
3. The README shall JVM 単体テストのみを単独で実行するコマンド（`./gradlew test`）を記述する
4. When 開発者がモックモードで起動したいとき, the README shall `feedman.mockMode=true` を
   `-P` フラグまたは `gradle.properties` で指定する手順と、それによりログイン placeholder が
   スキップされてドロワー + モックタイムラインが表示される挙動を説明する
5. When 開発者が API base URL を切り替えたいとき, the README shall `feedman.baseUrl` を
   `-P` フラグまたは `gradle.properties` で指定する手順と、未指定時の既定値が
   `https://stg-feed.markte-river.net` であることを明示する
6. The README shall Bearer トークン認証で OAuth コールバックを受け取るために
   `feedman://auth/callback` カスタムスキームがアプリに登録されていることを「前提情報」として
   記述し、開発者がサーバー設定側で同 redirect URI を許可する必要がある旨を案内する
7. The README shall 実トークン・本番 API キー・OAuth クライアントシークレットをソースコード /
   Version Catalog / `gradle.properties` に埋め込まず、`local.properties` または CI secrets 経由で
   渡す運用ルールを明示する
8. The README shall v1 スモーク確認手順への導線として `docs/SMOKE-CHECKLIST.md` へのリンクを
   配置する

### Requirement 2: v1 スモークチェックリスト本体

**Objective:** As a リリース判定担当者, I want SPEC §10 の各受け入れ基準を順に追える手動チェック
リストを参照したい, so that v1 リリース時に主要機能の動作確認漏れを防げる

#### Acceptance Criteria

1. The Smoke Checklist shall `docs/SMOKE-CHECKLIST.md` 配下に配置される
2. The Smoke Checklist shall `design/SPEC.md` §10 に列挙された受け入れ基準（Google ログイン →
   横断タイムライン / `since_time` 固定の無限スクロール / フィード別フィルタ / 記事タップで部分
   シート + 既読化 / Custom Tabs での元記事閲覧 + 既読化 / スターのトグル整合 / フィード別
   Pull-to-refresh と `FEED_COOLDOWN` 案内 / フィード登録・購読解除・間隔変更・再開 / ライト/
   ダーク切替）すべてに対し、手順・期待結果・チェックボックスを含む項目を 1 つ以上用意する
3. The Smoke Checklist shall キーワードプッシュ通知（SPEC §10 末尾 / §7）は次フェーズである旨を
   明示し、v1 チェック対象から除外する
4. The Smoke Checklist shall 各項目に対し「自動テストで担保される」か「手動確認のみ」かの区分
   を明示する
5. While 各項目に自動テストが対応している場合, the Smoke Checklist shall 対応するテストクラス名
   またはテスト関数名（または該当する `app/src/test/...` パス）を併記する
6. Where v1 時点でサーバー側 Bearer トークン発行・検証エンドポイントが未デプロイの場合,
   the Smoke Checklist shall 実機 Google ログイン以降の手順を「サーバーデプロイ後に実施する
   前提手順」として明示し、ローカルで完結可能な手順（モックモード起動 / `./gradlew build` /
   `./gradlew test`）と区別して記載する
7. The Smoke Checklist shall フィード別 Pull-to-refresh 項目で `POST /api/subscriptions/{id}/fetch`
   呼び出しと、`FEED_COOLDOWN`（429）応答時に `retry_after_seconds` がユーザーに案内されることの
   確認手順を含む
8. The Smoke Checklist shall フェッチ間隔設定項目でセグメント値が **30 / 60 / 180 / 360 分**
   であること（15 分は廃止）の確認手順を含む

### Requirement 3: ドキュメント記載前の自己検証

**Objective:** As a Product Manager / ドキュメント記述者, I want README とチェックリストに書く手順
を実機で検証してから記載したい, so that 読者が手順どおりに実行して再現失敗する事態を防げる

#### Acceptance Criteria

1. Before the README / Smoke Checklist is finalized, the ドキュメント記述者 shall リポジトリ
   ローカル環境で `./gradlew build` を実行し exit code 0 で完了することを確認する
2. Before the README / Smoke Checklist is finalized, the ドキュメント記述者 shall リポジトリ
   ローカル環境で `./gradlew test` を実行し JVM 単体テストが pass することを確認する
3. Before the README / Smoke Checklist is finalized, the ドキュメント記述者 shall
   `./gradlew assembleDebug -Pfeedman.mockMode=true` がモックモードビルドを成功させることを
   確認する
4. If 実機 Google ログイン手順がサーバー未デプロイ等の理由でローカル検証不能な場合,
   the ドキュメント記述者 shall 当該手順を Smoke Checklist 上で「未検証 / サーバーデプロイ後に
   実施」と明示する（架空の検証結果を記載しない）

## Non-Functional Requirements

### NFR 1: ドキュメント可読性・保守性

1. The README shall 既存セクション構成（ドキュメント表 / 技術スタック / v1 スコープ /
   ビルド・実行 / CI / idd-claude 運用）と一貫した日本語ベース・Markdown 形式で記述される
2. The Smoke Checklist shall 各項目に GitHub Markdown の checkbox 記法（`- [ ]`）を用いて、
   実施者がリリース判定時にチェックを付けて記録可能な形式で記述される
3. The README shall ドキュメント表（既存の表）に `docs/SMOKE-CHECKLIST.md` への行を追加し、
   その内容を 1 行説明する

### NFR 2: 既存ドキュメントとの整合

1. The README and the Smoke Checklist shall `design/SPEC.md` §10（受け入れ基準）および付録 A
   （確定事項）の記述と矛盾しない内容で記述される
2. The README shall `CLAUDE.md` の「機密情報の扱い」「技術スタック」と矛盾しない範囲で記述される

## Out of Scope

- 新規アプリケーションコードの追加・既存コードの挙動変更
- 自動化されたスモークテストスイート（Compose UI Test / instrumented test）の追加
- CI ワークフロー（`.github/workflows/android-ci.yml`）の変更・拡張
- Play Store / Google Play Console への提出手順、署名鍵 / `keystore.properties` 関連の
  ドキュメント整備
- Bearer トークン認証サーバーのデプロイ手順そのもの（サーバー側リポジトリの責務）
- キーワードプッシュ通知（FCM）に関する手順記載（次フェーズ）

## Open Questions

- なし（チェックリスト配置場所は本要件で `docs/SMOKE-CHECKLIST.md` に確定。サーバー未デプロイ
  に伴う実機ログイン検証の扱いも本要件で「前提手順として明示する」方針に確定）

## 関連

- Parent: #12
- Depends on: #24 #34 #38 #42 #43 #45 #46 #48 #51
