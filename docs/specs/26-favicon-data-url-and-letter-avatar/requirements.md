# Requirements Document

## Introduction

フィード一覧・横断タイムライン・ドロワーなど多くの画面でフィードを識別するための favicon 表示が
必要になる。サーバーは favicon を `data:<mime>;base64,...` 形式の data URL または `null` で返す
（`design/SPEC.md` §4.4）。本機能では、data URL を描画でき、`null` や復号失敗時にはフィードタイトル
頭文字を用いた色付きレターアバターへフォールバックする共通 Composable を、デザインシステム
（`core/ui` / `core/designsystem` 周辺）に追加する。プロトタイプ `FMFavicon`（`design/mobile/fm-ui.jsx`）
の見た目・挙動を視覚基準とし、後続 Issue（タイムラインカード・ドロワー等）から再利用される共通部品
として提供する。

## Requirements

### Requirement 1: data URL favicon の描画

**Objective:** As a Feedman アプリ利用者, I want フィードから提供された data URL の favicon が
そのままアイコンとして表示されること, so that 各フィードを視覚的に素早く識別できる

#### Acceptance Criteria

1. When favicon 文字列が `data:` で始まる有効な data URL として渡される, the Favicon Composable shall
   その data URL を画像として復号し、指定サイズの矩形アイコンとして表示する
2. When 同一の data URL の favicon が複数箇所で繰り返し表示される, the Favicon Composable shall
   ネットワーク再取得を発生させずに表示する
3. The Favicon Composable shall 画像をアスペクト比を歪めずに指定された正方形領域に収めて描画する

### Requirement 2: フォールバック用レターアバター

**Objective:** As a Feedman アプリ利用者, I want favicon が無い／復号できないフィードでも一貫した
アイコンが表示されること, so that リスト上で空欄や崩れた表示にならずフィードを識別できる

#### Acceptance Criteria

1. If favicon 文字列が `null` または空文字である, the Favicon Composable shall フィードタイトル先頭
   1 文字を白色文字として配置した色付きレターアバターを表示する
2. If favicon 文字列が `data:` で始まらない、もしくは画像として復号できない, the Favicon Composable
   shall 同じレターアバターへフォールバックする
3. If フィードタイトルが空文字または `null` である, the Favicon Composable shall プレースホルダ文字
   `?` を用いたレターアバターを表示する
4. The Favicon Composable shall レターアバターの先頭文字を Unicode コードポイント 1 つ分単位で抽出
   する（サロゲートペア・絵文字 1 文字を分割しない）

### Requirement 3: アバター色の安定性

**Objective:** As a Feedman アプリ利用者, I want 同じフィードのレターアバターが常に同じ色で表示
されること, so that 色そのものをフィード識別の手がかりにできる

#### Acceptance Criteria

1. When 同一のフィードタイトルに対するレターアバターを複数回描画する, the Favicon Composable shall
   同一の背景色を選択する
2. When 異なるフィードタイトルに対するレターアバターを描画する, the Favicon Composable shall
   タイトル文字列から導出した決定論的なハッシュに基づいて背景色を選択する
3. The Favicon Composable shall アプリ再起動・プロセス再生成をまたいでも同一フィードタイトルに
   対して同一の背景色を返す
4. The Favicon Composable shall 白文字（前景色）との視認性を確保するため、あらかじめ定義された
   レターアバター用パレットからのみ背景色を選択する

### Requirement 4: サイズバリアント

**Objective:** As 後続機能を実装する開発者, I want リスト用・ドロワー用など複数のサイズで同じ
Favicon Composable を呼び出せること, so that 画面ごとに別実装を作らずに統一感を保てる

#### Acceptance Criteria

1. When 呼び出し側がリスト用サイズ（小）とドロワー用サイズ（大）のサイズバリアントを要求する, the
   Favicon Composable shall それぞれ指定されたサイズで歪みなく描画する
2. When サイズバリアントが指定される, the Favicon Composable shall data URL favicon／レターアバター
   いずれの場合も同一の外形（正方形・角丸）で描画する
3. The Favicon Composable shall レターアバター内の文字サイズを指定されたアイコンサイズに比例させ、
   サイズが変わっても文字が枠から食み出さないように配置する
4. The Favicon Composable shall サポートするサイズバリアントを `design/SPEC.md` および
   `design/mobile/fm-ui.jsx` の `FMFavicon` 利用箇所（リスト・ドロワー・コンパクト/最小カード等）が
   要求するサイズに揃える

### Requirement 5: 視覚仕様の準拠

**Objective:** As デザイン整合性を確認するレビュワー, I want Composable がプロトタイプ FMFavicon と
同じ視覚体験になっていること, so that 採用案（`SPEC.md` §5）との差分が発生しない

#### Acceptance Criteria

1. The Favicon Composable shall 正方形の外形に角丸を適用した形状で描画する
2. The Favicon Composable shall レターアバター文字をアイコン中央に配置し、白色で太字相当の
   ウェイトで表示する
3. The Favicon Composable shall アクセント色（Indigo）とは独立したレターアバター用パレットを使用し、
   テーマ切替（ライト／ダーク）の影響で背景色が変化しないようにする

## Non-Functional Requirements

### NFR 1: テスト容易性

1. The Favicon Composable shall ロジック部分（data URL 判定・頭文字抽出・タイトルハッシュからの
   色選択）を UI 描画から分離し、JVM 単体テスト（実機エミュレータを必要としない）から検証可能に
   する
2. The Favicon Composable shall 同一入力（タイトル文字列・favicon 文字列）に対して常に同一の出力
   （選択色・抽出文字・data URL 判定結果）を返す純粋関数として上記ロジックを提供する
3. The Favicon Composable shall data URL 描画の正常系・null フォールバック・復号失敗フォールバック・
   空タイトルフォールバックの各分岐を、ユーザー可視な分岐としてテストから観察できるように公開する

### NFR 2: 性能と再描画

1. The Favicon Composable shall 同一画面内で同じ favicon 値を持つアイテムが 50 件以上同時に表示
   される場面でも、スクロール中に追加のデコード処理がフレーム描画をブロックしないように非同期に
   画像を準備する
2. The Favicon Composable shall favicon 値・フィードタイトル・サイズが変わらない限り、UI 再描画時
   に画像の再デコードや色再計算を再実行しない

## Out of Scope

- favicon の取得・ネットワークキャッシュ戦略（data URL は API レスポンスに同梱されるため、本 Issue
  ではネットワーク取得・ディスクキャッシュは扱わない）
- favicon の永続キャッシュ・LRU 戦略
- フィード登録時の favicon 自動抽出ロジック（サーバー側責務）
- アクセシビリティラベルの文言詳細・スクリーンリーダー読み上げ仕様の最終確定（後続 Issue で
  デザインシステム全体の a11y 規約と合わせて整備する想定）
- アニメーション・スケルトン表示などのローディング UX
- UI 描画そのものを実機エミュレータで検証する instrumented テスト整備（JVM テスト可能な部分の
  み本 Issue でテスト対象）

## Open Questions

- なし（`design/SPEC.md` §4.4 と `design/mobile/fm-ui.jsx` の `FMFavicon` を視覚基準として確定可能）
