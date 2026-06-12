# Requirements Document

## Introduction

Feedman Android のアプリシェル（トップアプリバー + ナビゲーションドロワー）には、検索 /
テーマ切替 / アカウント / フィード登録という主要な機能導線の起点が集中する。本 Issue
ではプロトタイプ（`design/mobile/fm-screens.jsx` の `FMHeader` / `FMDrawer`）と `design/SPEC.md`
§5.0 で確定したヘッダ構成に従い、各導線を「タップ可能で次画面/シートを呼び出せる」状態に
結線することをスコープとする。後続 Issue（#47 検索画面 / #49 アカウントシート / #44 フィード
登録シート）が完成するまでの間、各導線が押下されたときに **placeholder シート/画面**を
表示することで、UI シェルとして閉じた状態を担保する。

テーマ切替は #25 で導入された `ThemeModeRepository` を介し、即時反映 + 永続化を行う。
タイトル/サブタイトルはプロトタイプ準拠で、現在表示中の画面に追従して切り替わる。

## Requirements

### Requirement 1: トップアプリバーのタイトルとサブタイトル

**Objective:** As a Feedman ユーザー, I want トップアプリバーで現在の画面が一目で分かること, so that 階層内での自分の位置を把握できる

#### Acceptance Criteria

1. While 「すべての新着」画面を表示中, the Top App Bar shall プロトタイプ準拠のタイトル（「すべての新着」相当）を表示する
2. While 「お気に入り」画面を表示中, the Top App Bar shall プロトタイプ準拠のタイトル（「お気に入り」相当）を表示する
3. While 個別フィード画面を表示中, the Top App Bar shall そのフィードの名称をタイトルとして表示する
4. Where 画面ごとにサブタイトルが定義されている場合, the Top App Bar shall タイトル直下にサブタイトルを表示する
5. When ユーザーが画面遷移を行ったとき, the Top App Bar shall 遷移先のタイトルとサブタイトルへ即時に切り替える

### Requirement 2: 検索アイコンによる検索画面への遷移

**Objective:** As a Feedman ユーザー, I want どの画面からでも検索を起動できること, so that 過去記事を探す動作を 1 タップで開始できる

#### Acceptance Criteria

1. While アプリシェルが表示されている任意の画面, the Top App Bar shall 右側に検索アイコンを表示する
2. When ユーザーが検索アイコンをタップしたとき, the App Shell shall 検索ルートへ遷移する
3. Where 検索画面の本実装（#47）が完了していない期間, the App Shell shall 検索ルートに対応する placeholder 画面を表示する
4. The 検索アイコン shall スクリーンリーダー向けに「検索」を読み上げ可能な accessibility label を持つ

### Requirement 3: テーマ切替アイコンによるライト/ダーク切替

**Objective:** As a Feedman ユーザー, I want トップバーから 1 タップでテーマを切替できること, so that 視認性を都度の環境に合わせて素早く調整できる

#### Acceptance Criteria

1. While 現在のテーマモードがライト, the Top App Bar shall 右側に「ダーク切替」を表すアイコン（プロト準拠で月相当）を表示する
2. While 現在のテーマモードがダーク, the Top App Bar shall 右側に「ライト切替」を表すアイコン（プロト準拠で太陽相当）を表示する
3. When ユーザーがテーマ切替アイコンをタップしたとき, the Theme Module shall 反対のテーマモードへ即時に切り替えてアプリ全体の配色を更新する
4. When ユーザーがテーマ切替アイコンをタップしたとき, the Theme Module shall 新しいテーマモードを次回起動以降も保持されるように永続化する
5. When アプリを再起動したとき, the Theme Module shall 最後に保存されたテーマモードで起動する
6. The テーマ切替アイコン shall スクリーンリーダー向けに「テーマ切替」を読み上げ可能な accessibility label を持つ

### Requirement 4: ドロワーヘッダのユーザー領域からアカウントシート起動

**Objective:** As a Feedman ユーザー, I want ドロワーヘッダのユーザー表示をタップしてアカウント情報にアクセスできること, so that ログイン情報や退会導線へ素早く到達できる

#### Acceptance Criteria

1. While ナビゲーションドロワーが開いている, the Drawer Content shall ヘッダ領域にユーザーアイコン + メールアドレス相当の表示を含むタップ可能な領域を表示する
2. When ユーザーがドロワーヘッダのユーザー領域をタップしたとき, the App Shell shall アカウントシートを起動する
3. When ユーザーがドロワーヘッダのユーザー領域をタップしたとき, the App Shell shall ナビゲーションドロワーを閉じる
4. Where アカウントシートの本実装（#49）が完了していない期間, the App Shell shall placeholder シートを表示する
5. The ユーザー領域 shall スクリーンリーダー向けに「アカウント」を読み上げ可能な accessibility label を持つ

### Requirement 5: ドロワー内 + ボタンからフィード登録シート起動

**Objective:** As a Feedman ユーザー, I want ドロワーの「フィード」セクション横の + ボタンからフィード登録を開始できること, so that 新規フィードを追加する導線を 1 タップで開ける

#### Acceptance Criteria

1. While ナビゲーションドロワーが開いている, the Drawer Content shall 「フィード」セクション見出しの横に + ボタンを表示する
2. When ユーザーがドロワー内の + ボタンをタップしたとき, the App Shell shall フィード登録シートを起動する
3. When ユーザーがドロワー内の + ボタンをタップしたとき, the App Shell shall ナビゲーションドロワーを閉じる
4. Where フィード登録シートの本実装（#44）が完了していない期間, the App Shell shall placeholder シートを表示する
5. The + ボタン shall スクリーンリーダー向けに「フィードを登録」を読み上げ可能な accessibility label を持つ

### Requirement 6: ドロワーの開閉

**Objective:** As a Feedman ユーザー, I want トップバー左のメニューアイコンからドロワーを開閉できること, so that 主要な導線（フィード一覧 / 設定 / アカウント）に素早くアクセスできる

#### Acceptance Criteria

1. While アプリシェルが表示されている任意の画面, the Top App Bar shall 左側にメニュー（ハンバーガー）アイコンを表示する
2. When ユーザーがメニューアイコンをタップしたとき, the App Shell shall ナビゲーションドロワーを開く
3. When ナビゲーションドロワーが開いている状態でユーザーがスクリム（ドロワー外の暗転領域）をタップしたとき, the App Shell shall ナビゲーションドロワーを閉じる
4. When ナビゲーションドロワーが開いている状態でユーザーが端末の戻る操作を行ったとき, the App Shell shall ナビゲーションドロワーを閉じる（戻る操作はその時点では画面遷移に使われない）
5. The メニューアイコン shall スクリーンリーダー向けに「メニュー」を読み上げ可能な accessibility label を持つ

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーが検索 / テーマ切替 / メニュー / + / ユーザー領域のいずれかをタップしたとき, the App Shell shall 100ms 以内に視覚的フィードバック（リップル相当）を返す
2. When ユーザーがテーマ切替アイコンをタップしたとき, the Theme Module shall 200ms 以内にアプリ全体の配色を新テーマへ更新する

### NFR 2: 永続化の堅牢性

1. While テーマモードの永続化処理が完了していない過渡的な状態, the Theme Module shall 画面表示には新しいテーマモードを反映する（永続化失敗時も UI 上は新モードのままとし、次回起動時にフォールバックする）
2. If テーマモードの永続化に失敗したとき, the Theme Module shall サイレントに失敗せず、デバッグ可能なログを残す

### NFR 3: アクセシビリティ

1. The トップアプリバーおよびドロワー内のすべてのタップ可能アイコン shall 最小 48dp 四方のタッチターゲットを確保する
2. The トップアプリバーおよびドロワー内のすべてのタップ可能アイコン shall accessibility label を持ち、スクリーンリーダーで内容を識別可能にする

## Out of Scope

- 検索画面の本実装（Issue #47 で別途扱う。本 Issue では placeholder のみ）
- アカウントシートの本実装（Issue #49 で別途扱う。本 Issue では placeholder のみ）
- フィード登録シートの本実装（Issue #44 で別途扱う。本 Issue では placeholder のみ）
- ドロワーフッタの「キーワード通知」導線（v1 スコープ外。`design/SPEC.md` §5.8）
- ドロワー本体の「フィード一覧」「すべての新着」「お気に入り」ナビゲーション挙動（既存実装 / 別 Issue で扱う）
- テーマモードの「端末設定追従」モード切替（SPEC §6 に記載があるが、本 Issue ではプロト準拠の 2 値トグル（ライト/ダーク）のみを扱う）
- ドロワー内のユーザー領域に表示するユーザー情報の取得（本 Issue ではプロト準拠の表示文字列で代替してよい。`/auth/me` 連携は #49 で扱う）

## Open Questions

- なし（本 Issue 範囲ではプロトタイプと SPEC §5.0 で挙動が確定している）

## 関連

- Parent: #5
- Depends on: #29
- Related: #25 #44 #47 #49
