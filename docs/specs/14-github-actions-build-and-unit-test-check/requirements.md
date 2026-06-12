# Requirements Document

## Introduction

feedman-android では idd-claude による Issue 駆動の自動開発フローを採用しており、PR 必須
チェック・auto-merge・Reviewer 運用はすべて CI ステータスチェックの存在を前提としている。
スケルトン作成（#1）が main に取り込まれた現時点では、PR 時に走るビルド／単体テストの
GitHub Actions ワークフローが存在せず、以後の全 PR で品質ゲートが機能しない。本 Issue
では、PR 作成・更新および main への push に対して `./gradlew build` 相当（コンパイル・
Android Lint・JVM 単体テスト）を自動実行し、結果を GitHub のステータスチェックとして
報告する必須 CI を整備する。なお、branch protection / ruleset の設定そのものはリポジトリ
管理者による手動操作の運用範囲とし、本要件ではチェックを生成・公開する責務までを扱う。

## Requirements

### Requirement 1: PR 時のビルド・単体テスト自動実行

**Objective:** As a Reviewer / auto-merge 運用者, I want PR 作成・更新時に毎回ビルドと
JVM 単体テストが自動実行されること, so that 品質ゲートを通過していない変更が main に
取り込まれない

#### Acceptance Criteria

1. When 開発者が main を base とする PR を新規作成したとき, the Build Workflow shall そのコミットに対してビルドジョブをトリガする
2. When 既存 PR のヘッドブランチに新しいコミットが push されたとき, the Build Workflow shall 当該コミットに対してビルドジョブを再実行する
3. When ビルドジョブが起動したとき, the Build Workflow shall `./gradlew build` 相当（compile・Android Lint・JVM 単体テスト）を実行する
4. When ビルドジョブが完了したとき, the Build Workflow shall GitHub Checks API 上で対象コミットに対する成功または失敗のステータスチェックを報告する
5. If ビルドジョブ内で compile・Android Lint・JVM 単体テストのいずれかが非ゼロ exit code を返した場合, the Build Workflow shall ジョブを失敗ステータスで終了する

### Requirement 2: main への push 時のビルド・単体テスト実行

**Objective:** As a メンテナ, I want main への直接 push および PR マージ後の main commit
でも同一ビルドが走ること, so that main の HEAD が常に build green であることを継続的に
検証できる

#### Acceptance Criteria

1. When main ブランチに新しいコミットが push されたとき, the Build Workflow shall そのコミットに対してビルドジョブをトリガする
2. When main 用ビルドジョブが起動したとき, the Build Workflow shall PR 時と同一の `./gradlew build` 相当の手順を実行する
3. When main 用ビルドジョブが完了したとき, the Build Workflow shall 対象コミットに対する成功または失敗のステータスチェックを報告する

### Requirement 3: 実行環境とビルド整合性

**Objective:** As a 開発者, I want CI のビルド環境がローカルの想定構成と同一であること,
so that 「ローカルでは通るが CI では落ちる」「またはその逆」の乖離を最小化できる

#### Acceptance Criteria

1. The Build Workflow shall JDK 17 を実行環境にセットアップする
2. The Build Workflow shall リポジトリ直下の `./gradlew` を用いてビルドを実行する
3. The Build Workflow shall Android SDK（compileSdk / targetSdk = 35、minSdk = 26）を解決できる環境を準備する
4. If JDK セットアップまたは Android SDK 解決に失敗した場合, the Build Workflow shall ジョブを失敗ステータスで終了する

### Requirement 4: キャッシュによる実行時間短縮

**Objective:** As a 開発者 / 運用者, I want Gradle 依存と build cache が CI 実行間で再利用
されること, so that 反復実行のフィードバック時間を短縮し CI コストを抑制できる

#### Acceptance Criteria

1. The Build Workflow shall Gradle のユーザーホームキャッシュ（依存解決結果・wrapper・build cache）を CI 実行間で再利用するキャッシュ機構を有効にする
2. When 同一の Gradle 依存定義のまま 2 回目以降のビルドジョブが実行されたとき, the Build Workflow shall キャッシュからの復元を試行する
3. If キャッシュの保存または復元に失敗した場合, the Build Workflow shall ビルドジョブ自体を失敗させずに続行する

### Requirement 5: 既存ワークフローとの非干渉

**Objective:** As a idd-claude 運用者, I want 既存の `.github/workflows/issue-to-pr.yml`
（idd-claude 用・既定無効）が本ワークフロー追加によって挙動変更されないこと, so that 自動
開発フローの起動条件・実行内容が予期せず変わらない

#### Acceptance Criteria

1. The Build Workflow shall `.github/workflows/issue-to-pr.yml` の内容を変更せずに新規ワークフローとして追加される
2. When `.github/workflows/issue-to-pr.yml` がこれまでと同じ条件で起動したとき, the Build Workflow shall その起動・実行に介入しない
3. The Build Workflow shall ステータスチェック名として `issue-to-pr.yml` のチェック名と衝突しない一意な名称を使用する

## Non-Functional Requirements

### NFR 1: 実行時間とリソース効率

1. While Gradle キャッシュが有効に復元された状態で, the Build Workflow shall 通常の差分変更による PR ビルドを 10 分以内に完了することを目標とする
2. The Build Workflow shall instrumented テスト（Android エミュレータ）を実行しない

### NFR 2: 観測性

1. When ビルドジョブが失敗したとき, the Build Workflow shall GitHub Actions のジョブログに失敗した Gradle タスク名と stderr を残す
2. When 単体テストが失敗したとき, the Build Workflow shall ジョブログから失敗テストクラス名・メソッド名が特定できる出力を残す

### NFR 3: セキュリティ・機密情報

1. The Build Workflow shall 本番 API キー・OAuth クライアントシークレット・リリース署名鍵などの実値を CI ログ・成果物に出力しない
2. The Build Workflow shall ビルドに必要な公開設定（base URL の既定値等）のみを利用し、秘匿値を要求しない

## Out of Scope

- instrumented テスト（Android エミュレータ上の UI テスト・Compose UI Test）の CI 実行
- リリース署名・APK / AAB の配布・成果物の永続アップロード
- branch protection / ruleset での「必須チェック」指定そのもの（リポジトリ管理者が手動で設定する運用とする）
- コードカバレッジ計測・公開
- 静的解析の追加導入（ktlint 等）— 既存ビルドに組み込まれている範囲のみ実行対象
- 通知連携（Slack / メール等）
- `.github/workflows/issue-to-pr.yml` 側の機能変更・有効化方針の見直し

## Open Questions

- なし
