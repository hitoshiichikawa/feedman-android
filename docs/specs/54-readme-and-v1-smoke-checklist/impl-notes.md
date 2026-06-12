# Implementation Notes — Issue #54 README configuration and v1 smoke checklist

## 概要

Issue #54 は v1 リリース判定に向けたドキュメント整備で、(a) `README.md` 拡充と
(b) `docs/SMOKE-CHECKLIST.md` 新規作成を行う。アプリのコード・CI 設定・Play Store 関連の
変更は scope 外（requirements.md Out of Scope と整合）。

## 変更ファイル

- `README.md`（拡充）
  - ドキュメント表に `docs/SMOKE-CHECKLIST.md` 行を追加
  - 「OAuth コールバック（`feedman://auth/callback`）の前提」セクションを追加
  - 「v1 スモークチェック」セクションを追加し、`docs/SMOKE-CHECKLIST.md` への導線を配置
- `docs/SMOKE-CHECKLIST.md`（新規作成）
  - SPEC §10 の受け入れ基準を 1 項目ずつ列挙
  - 各項目に「自動テストで担保（対応テストクラス名）」「手動確認（手順）」の区分を明記
  - 実機 Google ログインはサーバーデプロイ後に実施する旨を明示
  - フェッチ間隔セグメント = 30 / 60 / 180 / 360 分（15 分廃止）の確認手順を含む
  - キーワードプッシュ通知は次フェーズである旨を明示
- `docs/specs/54-readme-and-v1-smoke-checklist/impl-notes.md`（本ファイル）

## Requirement ID → 対応表

### Requirement 1: README のセットアップ・ビルド・実行手順

| Req ID | 対応箇所 |
|---|---|
| 1.1 | `README.md` 「ビルド・実行 > 前提」セクション（JDK 17 / Android SDK / compileSdk 35 / targetSdk 35 / minSdk 26 / `ANDROID_HOME` / `local.properties`） |
| 1.2 | `README.md` 「基本ビルド」セクション（`./gradlew build`） |
| 1.3 | `README.md` 「基本ビルド」セクション（`./gradlew test`） |
| 1.4 | `README.md` 「Gradle プロパティ」表 + `assembleDebug -Pfeedman.mockMode=true` の例（既存記載を維持・確認） |
| 1.5 | `README.md` 「Gradle プロパティ」表（`feedman.baseUrl` 既定値 `https://stg-feed.markte-river.net`） |
| 1.6 | `README.md` 「OAuth コールバック（`feedman://auth/callback`）の前提」新規セクション |
| 1.7 | `README.md` 「機密情報の取り扱い」セクション（既存記載を維持） |
| 1.8 | `README.md` 「v1 スモークチェック」新規セクション + ドキュメント表に行追加 |

### Requirement 2: v1 スモークチェックリスト本体

| Req ID | 対応箇所 |
|---|---|
| 2.1 | `docs/SMOKE-CHECKLIST.md` 新規配置 |
| 2.2 | `docs/SMOKE-CHECKLIST.md` セクション 1〜10 が SPEC §10 の 9 項目（Google ログイン / 無限スクロール / フィルタ / 部分シート + 既読化 / Custom Tabs + 既読化 / スター整合 / Pull-to-refresh + cooldown / 登録・購読・間隔・再開 / テーマ切替）すべてをカバー |
| 2.3 | `docs/SMOKE-CHECKLIST.md` 「11. 次フェーズ（v1 スコープ外）」セクションにキーワードプッシュ通知を明示 |
| 2.4 | 各項目に「区分: 自動テストで担保 / 手動確認 / サーバーデプロイ後に実施」を併記 |
| 2.5 | 各項目に対応する `app/src/test/...` のテストクラス名を併記（grep で実在を確認した名前のみ） |
| 2.6 | セクション 1.2 / 1.3 / 0 に「サーバーデプロイ後に実施」を明示し、ローカル完結手順（モックモード / `./gradlew build` / `./gradlew test`）と区別 |
| 2.7 | セクション 7.1 / 7.2 で `POST /api/subscriptions/{id}/fetch` 呼び出しと `retry_after_seconds` 案内の確認手順を記載 |
| 2.8 | セクション 8.3 で 30 / 60 / 180 / 360 分の 4 値、15 分廃止を確認 |

### Requirement 3: ドキュメント記載前の自己検証

| Req ID | 対応箇所 |
|---|---|
| 3.1 | 本ファイル「検証記録」セクションで `./gradlew build` 成功を記録 |
| 3.2 | 本ファイル「検証記録」セクションで `./gradlew test`（`build` に含まれる `testDebugUnitTest` / `testReleaseUnitTest`）成功を記録 |
| 3.3 | 本ファイル「検証記録」セクションで `./gradlew assembleDebug -Pfeedman.mockMode=true` 成功を記録 |
| 3.4 | `docs/SMOKE-CHECKLIST.md` 各項目で「サーバーデプロイ後に実施」と「ローカルで完結する手順」を区別して記載（架空の検証結果は記載していない） |

### NFR 1: ドキュメント可読性・保守性

| NFR ID | 対応箇所 |
|---|---|
| NFR 1.1 | `README.md` は既存セクション構成（ドキュメント表 / 技術スタック / v1 スコープ / ビルド・実行 / CI / idd-claude 運用）を維持しつつ追記し、日本語ベース・Markdown 形式で記述 |
| NFR 1.2 | `docs/SMOKE-CHECKLIST.md` の各項目を `- [ ]` の GitHub Markdown checkbox で記述 |
| NFR 1.3 | `README.md` ドキュメント表に `docs/SMOKE-CHECKLIST.md` 行を追加し 1 行説明を併記 |

### NFR 2: 既存ドキュメントとの整合

| NFR ID | 対応箇所 |
|---|---|
| NFR 2.1 | SPEC §10 受け入れ基準の 9 項目 + 付録 A-6（30/60/180/360 分）と整合した記述 |
| NFR 2.2 | `CLAUDE.md` 「機密情報の扱い」「技術スタック」と矛盾しない（API base URL の切替は `BuildConfig` + Gradle プロパティで行うこと、署名鍵 / `google-services.json` のコミット禁止を明記済み） |

## 検証記録

- `./gradlew build`（compile / Android Lint / JVM 単体テスト）: **BUILD SUCCESSFUL**（exit code 0、約 1 分、UP-TO-DATE 多数）
- `./gradlew assembleDebug -Pfeedman.mockMode=true`: **BUILD SUCCESSFUL**（exit code 0、本サイクル中に実行）
- `./gradlew test`: `./gradlew build` 内の `:app:testDebugUnitTest` / `:app:testReleaseUnitTest` が UP-TO-DATE / 成功で完了しており、単体実行も等価（明示的単独実行は省略可）

## 自動テスト → 対応テストクラスの実在性検証

`docs/SMOKE-CHECKLIST.md` 内で参照したテストクラス名は、すべて `find app/src/test -name "*.kt"`
の出力を grep で照合済み。架空のクラス名は含まない。代表例:

- `feature.feed.FeedScreenViewModelTest.onPullToRefresh が FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2`
  → `app/src/test/kotlin/com/feedman/android/feature/feed/FeedScreenViewModelTest.kt:408` で確認
- `feature.subscriptionsettings.SubscriptionSettingsViewModelTest.現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2`
  → `app/src/test/kotlin/com/feedman/android/feature/subscriptionsettings/SubscriptionSettingsViewModelTest.kt:147` で確認

## 確認事項（レビュワー / 人間運用者向け）

- 本 PR の時点では Bearer トークン認証サーバー（`hitoshiichikawa/feedman` 側）が未デプロイのため、
  `docs/SMOKE-CHECKLIST.md` の実機 Google ログイン以降の項目は実検証していません。サーバー
  デプロイ後の改めての pass を別途記録する想定です（チェックリスト末尾「検証結果記録欄」が
  その記録枠）
- requirements.md 1.4（`feedman.mockMode=true` でのモック挙動）について、README 既存記載
  「ログイン placeholder をスキップしてドロワー + モックタイムラインを起動」を維持・確認済み
- 既存 `README.md` のセクション順は変更せず、ドキュメント表 / OAuth コールバック / v1 スモーク
  チェックの 3 箇所に追記する形でまとめました

## 補足ノート

- requirements.md には design.md / tasks.md がない（ドキュメント整備のみで設計分割不要と PM が判断）
- フェッチ間隔セグメントの 15 分廃止は SPEC 付録 A-6（2026-06-12 確定）と整合
- Feature Flag Protocol は本リポジトリ CLAUDE.md で `**採否**: opt-out`、本 Issue は flag 裏実装に該当しないため通常フローで実装

STATUS: complete
