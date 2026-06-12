# Requirements Document

## Introduction

Feedman Android アプリでは、横断タイムライン・フィード別記事一覧・スター一覧・検索結果の
4 系統のリストカードで、相対日時表示・はてブ数バッジ・スタートグル・既読時の見た目減衰
といった同一のメタ情報部品を繰り返し表示する。各画面で個別に組むと表現の揺れ（境界値
の差・アクセシビリティラベルの欠落・既読減衰の不一致）が発生しやすく、SPEC §5.1 / §6 で
要求されるカード仕様の一貫性を満たせない。

本機能では `core/ui` に共有メタデータ部品（スタートグル / はてブ数バッジ / 相対日時
フォーマッタ / 既読 dim 付きカード枠）を一式そろえ、SPEC §6 の「相対日時の境界仕様」と
プロトタイプ `design/mobile/fm-ui.jsx` のカード構成を Android Compose 上で再現する。
状態変更の API 呼び出し（既読化・スタートグルの永続化）は本 Issue では扱わず、UI 部品が
コールバックを公開するところまでをスコープとする。

## Requirements

### Requirement 1: 共有スタートグル部品

**Objective:** As a Feedman Android ユーザー, I want どの記事カードからもスター状態を 1 タップで
切り替えたい, so that 後で読み返したい記事を画面横断で同じ操作感で確保できる

#### Acceptance Criteria

1. When スタートグル部品が is_starred=true の記事を描画する, the Article Meta UI shall アクセント色の
   塗りつぶし（filled）アイコンを表示する
2. When スタートグル部品が is_starred=false の記事を描画する, the Article Meta UI shall ミュート色の
   輪郭のみ（outline）アイコンを表示する
3. When ユーザーが is_starred=false の状態でスタートグルをタップする, the Article Meta UI shall
   onToggle コールバックを現在の記事と新しい状態 true を引数に 1 回だけ呼び出す
4. When ユーザーが is_starred=true の状態でスタートグルをタップする, the Article Meta UI shall
   onToggle コールバックを現在の記事と新しい状態 false を引数に 1 回だけ呼び出す
5. While スタートグルが is_starred=true の状態にある, the Article Meta UI shall アクセシビリティ
   ラベルとして「スターを解除」を読み上げる
6. While スタートグルが is_starred=false の状態にある, the Article Meta UI shall アクセシビリティ
   ラベルとして「スターを付ける」を読み上げる
7. When ユーザーがカード上のスタートグルをタップする, the Article Meta UI shall タップイベントを
   親カードの「記事詳細を開く」アクションに伝播させない
8. The スタートグルのタップ可能領域 shall 端から端まで 44dp 以上を確保する

### Requirement 2: 共有はてブ数バッジ

**Objective:** As a Feedman Android ユーザー, I want 各記事のはてブ数を一目で把握したい,
so that 注目度の高い記事を優先して読むかを判断できる

#### Acceptance Criteria

1. When はてブ数バッジが hatebu_fetched_at が null でない記事を描画する, the Article Meta UI shall
   hatebu_count の数値を表示する
2. If hatebu_fetched_at が null である, the Article Meta UI shall 数値の代わりに「−」（U+2212）を
   表示する
3. When はてブ数バッジが hatebu_count が 100 以上の記事を描画する, the Article Meta UI shall アクセント
   色 + 太字で数値を表示し、末尾に「users」を付加する
4. When はてブ数バッジが hatebu_count が 100 未満の記事を描画する, the Article Meta UI shall ミュート
   色 + 通常字幅で数値のみを表示する（「users」は付加しない）
5. The はてブ数バッジ shall 数値の左に RSS 風のアイコンを 1 つ表示する

### Requirement 3: 相対日時フォーマッタ

**Objective:** As a Feedman Android ユーザー, I want 記事の公開時刻を「いつ頃の記事か」が
すぐ分かる相対表現で見たい, so that カードを流し読みしながら新しい記事を判別できる

#### Acceptance Criteria

1. When 相対日時フォーマッタが「現在時刻 − published_at が 1 時間未満」の記事を整形する,
   the Relative Time Formatter shall 「1時間以内」という文字列を返す
2. When 相対日時フォーマッタが「現在時刻 − published_at がちょうど 1 時間（3600000ms）以上 24
   時間未満」の記事を整形する, the Relative Time Formatter shall 「N時間前」（N は経過時間の整数
   時間部分）という文字列を返す
3. When 相対日時フォーマッタが「現在時刻 − published_at がちょうど 24 時間（86400000ms）以上 7
   日未満」の記事を整形する, the Relative Time Formatter shall 「N日前」（N は経過時間の整数
   日数部分）という文字列を返す
4. When 相対日時フォーマッタが「現在時刻 − published_at が 7 日以上」の記事を整形する,
   the Relative Time Formatter shall ja-JP ロケールの year / month / day を含む日付文字列を返す
5. When 相対日時フォーマッタが is_date_estimated=true の記事を整形する, the Relative Time
   Formatter shall 相対日時文字列の直後に視覚的に区別される「(推定)」サフィックスを付加する
6. When 相対日時フォーマッタが is_date_estimated=false の記事を整形する, the Relative Time
   Formatter shall 「(推定)」サフィックスを付加しない
7. While 単体テストから固定された現在時刻が注入されている, the Relative Time Formatter shall
   実時計を参照せず注入された時刻のみを基準に整形結果を計算する

### Requirement 4: 共有記事カードの既読時減衰

**Objective:** As a Feedman Android ユーザー, I want 既読の記事カードが未読より控えめに見えてほしい,
so that 未読の記事を効率的に拾い読みできる

#### Acceptance Criteria

1. When 共有記事カードが is_read=true の記事を描画する, the Article Card UI shall カード全体の
   不透明度を 0.55 に設定する
2. When 共有記事カードが is_read=false の記事を描画する, the Article Card UI shall カード全体の
   不透明度を 1.0 に設定する
3. While is_read=true の状態でカードが表示されている, the Article Card UI shall タイトル・サマリ・
   メタ部品（スター / はてブ数 / 相対日時）を含むすべての要素に同一の不透明度を一括適用する
4. While is_read=true の状態でカードが表示されている, the Article Card UI shall タップ・スター
   トグルなどのインタラクションを引き続き受け付ける

### Requirement 5: 4 系統カード横断の一貫性

**Objective:** As a Feedman Android ユーザー, I want 横断タイムライン・フィード別・スター一覧・
検索結果のどの画面でも同じ部品で記事メタを見たい, so that 画面遷移しても操作感と読み取り感に
段差を感じない

#### Acceptance Criteria

1. The 横断タイムラインカード shall 本ドキュメント Requirement 1〜4 で定義した共有メタ部品を
   そのまま使用する
2. The フィード別記事一覧カード shall 本ドキュメント Requirement 1〜4 で定義した共有メタ部品を
   そのまま使用する
3. The スター一覧カード shall 本ドキュメント Requirement 1〜4 で定義した共有メタ部品を
   そのまま使用する
4. The 検索結果カード shall 本ドキュメント Requirement 1〜4 で定義した共有メタ部品を
   そのまま使用する
5. Where 検索結果カードのように hatebu_fetched_at がレスポンスに含まれない場合, the Article Meta
   UI shall hatebu_fetched_at を null とみなしてはてブ数バッジを「−」表示で描画する

## Non-Functional Requirements

### NFR 1: テスト容易性

1. The Relative Time Formatter shall System clock を直接参照せず、外部から注入された時刻
   ソースのみを使用する
2. The Relative Time Formatter shall 境界値（経過 0 分 / 59 分 / 60 分 / 23 時間 59 分 / 24
   時間ちょうど / 6 日 23 時間 / 7 日ちょうど）すべてについて単体テストで結果を一意に検証
   できる出力を返す

### NFR 2: アクセシビリティ

1. The スタートグル shall TalkBack 等のスクリーンリーダーで現在の状態（filled / outline）が
   ラベル文字列によって区別される
2. The 共有記事カードの全タップ要素 shall 44dp 以上のタップ標的サイズを確保する

### NFR 3: 視覚一貫性

1. The 共有メタ部品 shall プロトタイプ `design/mobile/fm-ui.jsx` の `FMStar` / `FMHatebu` /
   `fmFormatDate` および `FMArticleCard` の standard バリアントが示す配色・余白・フォント
   階層（タイトル / サマリ / メタ）と整合する見た目を保つ

## Out of Scope

- スター状態の永続化・サーバー反映（`PUT /api/items/{id}/state` 呼び出し）— Issue #38 で扱う
- 既読化トリガ（記事タップ時 / 外部リンク起動時の `is_read:true` 反映）— 別 Issue で扱う
- カード本体のタップ→記事詳細シート遷移ロジック（onOpen ハンドラの中身）
- OGP サムネイル（プロトタイプの `FMThumb`）の実装
- キーワード一致タグ `FMKeywordTag` の実装（キーワードプッシュ通知は v1 スコープ外）
- Pull-to-refresh / 無限スクロール部品
- フィード favicon の data URL 描画とフォールバック（別部品として扱う）

## Open Questions

- なし（SPEC §6 と `design/mobile/fm-data.jsx` の `fmFormatDate` 実装で相対日時の境界仕様は
  確定しており、本 Requirement 3 はその仕様を Android 側に移植する形で記述している）

## 関連

- Parent: #4
- Depends on: #25
