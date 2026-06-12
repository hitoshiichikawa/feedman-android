# 実装ノート: Issue #14 GitHub Actions build and unit test check

## 概要

`pull_request`（main 向け）と `push`（main）で `./gradlew build` を実行する GitHub Actions
ワークフロー `.github/workflows/android-ci.yml` を新規追加した。既存の
`.github/workflows/issue-to-pr.yml`（idd-claude 用・既定無効）には触れていない。

## 受入基準と担保箇所の対応表

### Requirement 1: PR 時のビルド・単体テスト自動実行

| AC | 担保箇所 |
|---|---|
| 1.1 PR 作成時に発火 | `on.pull_request.branches: [main]` により main を base とする PR の `opened` イベントで発火（GitHub Actions の `pull_request` イベントは既定で `opened` / `synchronize` / `reopened` を含む） |
| 1.2 既存 PR への push 時に再実行 | 同上の `pull_request` イベントが既定で `synchronize` を含むため、head への新規 push で再実行される |
| 1.3 `./gradlew build` 相当を実行 | `Build (compile + Android Lint + JVM unit tests)` ステップで `./gradlew --no-daemon build` を実行（Android Gradle Plugin の `build` タスクは compile + lint + JVM unit test を含む） |
| 1.4 GitHub Checks API にステータス報告 | GitHub Actions は jobs を自動的に Checks API のチェック実行（`Build and Unit Test`）として PR / commit に紐付ける |
| 1.5 いずれか失敗時にジョブ失敗 | `./gradlew build` が compile / lint / unit test のいずれかの非ゼロ exit を返した場合に shell step の exit code が伝搬し、Actions が job を fail にする |

### Requirement 2: main への push 時のビルド・単体テスト実行

| AC | 担保箇所 |
|---|---|
| 2.1 main への push で発火 | `on.push.branches: [main]` |
| 2.2 PR 時と同一手順 | 同一 job (`android-build`) が `pull_request` / `push` 両イベントで使用される |
| 2.3 main 用ジョブの結果が Checks API に報告 | GitHub Actions のジョブ実行は commit に対する Check Run として自動報告される |

### Requirement 3: 実行環境とビルド整合性

| AC | 担保箇所 |
|---|---|
| 3.1 JDK 17 セットアップ | `actions/setup-java@v4` with `distribution: temurin` / `java-version: '17'` |
| 3.2 リポジトリ直下の `./gradlew` を使用 | `./gradlew --no-daemon build` を直接呼び出し |
| 3.3 Android SDK 解決 | `ubuntu-latest` ランナーには Android SDK（compileSdk 35 含む）が pre-installed されており `ANDROID_HOME` / `ANDROID_SDK_ROOT` が設定済み。Android Gradle Plugin はこれを自動解決する |
| 3.4 JDK / SDK 解決失敗時にジョブ失敗 | `actions/setup-java` 失敗、もしくは Gradle 実行時の SDK 解決失敗時はそれぞれ非ゼロ exit でジョブが fail する |

### Requirement 4: キャッシュによる実行時間短縮

| AC | 担保箇所 |
|---|---|
| 4.1 Gradle ユーザーホームキャッシュ有効化 | `gradle/actions/setup-gradle@v4` がデフォルトで Gradle user home（dependencies / wrapper / build cache）を保存・復元する |
| 4.2 2 回目以降はキャッシュから復元 | 同 action のキャッシュキーは Gradle 関連ファイル（`gradle/wrapper/**` / `**/*.gradle*` / `**/gradle.properties` / `**/libs.versions.toml`）のハッシュで管理され、同一定義なら復元される |
| 4.3 キャッシュ失敗時もジョブ自体は失敗させない | `gradle/actions/setup-gradle` はキャッシュ保存・復元失敗を warning に留め、ジョブを fail させない設計 |

### Requirement 5: 既存ワークフローとの非干渉

| AC | 担保箇所 |
|---|---|
| 5.1 `issue-to-pr.yml` を変更しない | 本 PR では同ファイルを一切編集していない（`git diff` で確認） |
| 5.2 既存ワークフローの起動に介入しない | 新規ファイルは別 workflow であり、`issues` イベントで起動する `issue-to-pr.yml` と発火イベント（`pull_request` / `push`）が重ならない |
| 5.3 チェック名の一意性 | 本 workflow 名 `Android CI` / job 名 `Build and Unit Test`。`issue-to-pr.yml` の workflow 名 `Issue-driven Team Development` / job 名 `claude-team-dev` と重複しない |

### Non-Functional Requirements

| NFR | 担保箇所 |
|---|---|
| NFR 1.1 PR ビルド 10 分以内目標 | `timeout-minutes: 30` を上限とし、キャッシュ復元により目標 10 分は到達可能（保証ではなく目標） |
| NFR 1.2 instrumented テスト不実行 | `./gradlew build` のみを呼び出し、`connectedAndroidTest` 等は呼ばない |
| NFR 2.1 失敗時の Gradle タスク名・stderr ログ | `./gradlew build` のデフォルト出力（タスク名 + stack trace）が job ログに残る |
| NFR 2.2 失敗テストのクラス名・メソッド名特定 | Android Gradle Plugin の test report がデフォルトで失敗テストを stdout に出力する |
| NFR 3.1 秘匿値の非出力 | secrets を一切参照しない |
| NFR 3.2 公開設定のみ利用 | `feedman.baseUrl` / `feedman.mockMode` は build に必須ではない（既定値あり）。署名鍵 / API キーは要求しない |

## 判断記録

- **action バージョン**: 安定版メジャータグ `@v4` を採用（`actions/checkout` / `actions/setup-java` /
  `gradle/actions/setup-gradle`）。SHA pin は本 Issue のスコープ外（運用負荷とのトレードオフ。
  必要であれば別 Issue で renovate / dependabot 化を検討）。
- **キャッシュ機構**: `actions/setup-java@v4` の `cache: gradle` ではなく、より高機能で
  Gradle 公式の `gradle/actions/setup-gradle@v4` を選択。build cache・configuration cache・
  dependency cache を一括管理し、失敗時も job を fail させない（Req 4.3）ため。
- **`cache-read-only`**: PR ビルドではキャッシュを読み取り専用にして、main / default branch の
  ジョブのみキャッシュ保存に責任を持たせる。これは公式推奨パターン（feature branch からの
  保存が乱立すると key が氾濫するため）。
- **concurrency**: 同一 ref の旧 run は cancel する設定。push して直後に修正 push した
  場合の無駄な実行を抑制（NFR 1 のリソース効率に寄与）。
- **`--no-daemon`**: CI のような短命環境では daemon の起動コストの方が大きく、また各 run
  が独立した方が再現性が高いため明示。
- **既存 workflow 不変**: `.github/workflows/issue-to-pr.yml` は完全に未編集。同ファイルは
  `issues` イベントで発火するため、`pull_request` / `push` で発火する本 workflow とは
  発火条件が重ならない（Req 5.2）。

## ローカル検証結果

- YAML 構文チェック: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/android-ci.yml'))"` → OK
- `./gradlew build` のローカル実行は Gradle / Android のロジック変更を伴わないため省略
  （本 PR は CI 設定の追加と README 追記のみで、Gradle script / Kotlin source / リソースに
  変更がない）。

## 確認事項（レビュワー向け）

- 必須チェック化（branch protection / ruleset）は本 PR の責務外。merge 後にリポジトリ管理者が
  `Settings → Branches → Branch protection rules` または `Rulesets` でステータスチェック名
  `Build and Unit Test` を required に指定する運用となる。
- 初回 CI 実行ではキャッシュ未生成のため build 時間が長め（10 分超の可能性）になる場合がある。
  2 回目以降はキャッシュ復元により短縮される想定。
- Android SDK の自動解決は `ubuntu-latest` の pre-installed SDK に依存している。AGP の
  compileSdk が将来上がってランナーの SDK と乖離した場合は、明示的に `android-actions/setup-android`
  等で SDK パッケージを install するステップを追加する余地がある（現時点では compileSdk 35
  がランナーに含まれているため不要）。

## 追加した依存

なし（GitHub Actions の official action / Gradle 公式 action のみ）。

STATUS: complete
