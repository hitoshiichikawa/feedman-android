# Requirements Document

## Introduction

Feedman Android アプリの全画面共通のビジュアル基盤として、プロトタイプ `design/mobile/fm-data.jsx`
の `FM_THEME` を正本としたデザイントークン（配色・角丸・タップ標的・アイコンサイズ）を確定し、
Material 3 ベースのテーマ実装へマッピングする。Parent Issue #4（デザイントークン基盤）の中核を担い、
後続の個別 UI コンポーネント Issue（#26-#28）が安心してトークンを参照できる状態を作ることを目的とする。

依存 Issue #1（プロジェクト骨組み）でテーマ骨格として配置済みの仮実装 `FeedmanColors` / `FeedmanTheme`
を、SPEC §8 / GRAND-DESIGN §5.5 に準拠した正式なトークン定義へ置き換える。テーマモード
（端末追従 / ライト / ダーク）の手動上書きとその永続化、および再起動後の復元もスコープに含める。

## Requirements

### Requirement 1: 配色トークン（FeedmanColors）

**Objective:** As a UI 実装者, I want FM_THEME と一致する配色定数を参照したい, so that 画面間で色のブレなく
プロトと同等のビジュアルを実装できる

#### Acceptance Criteria

1. The Feedman Theme Module shall expose color tokens whose values are derived from `design/mobile/fm-data.jsx`
   の `FM_THEME`（ライト / ダーク双方）に列挙された全エントリ（背景・サーフェス 2 段階・前景・muted /
   mutedFg・border / borderStrong・star・danger・scrim・accent / accentOn / accentSoft）に対応する
2. The Feedman Theme Module shall use Indigo `oklch(0.55 0.17 264)`（ダーク時 `oklch(0.68 0.15 264)`）のみを
   accent として採用し、Coral / Teal / Violet などの他アクセントを提供しない
3. While 各色定数の宣言箇所において, the Feedman Theme Module shall 換算元の oklch 値（および必要なら
   color-mix 元の式）をコメントとして保持する
4. If 仮実装として存在する Issue #1 由来の `FeedmanColors` / `FeedmanTheme` が残っている場合, the Feedman
   Theme Module shall 本要件が定める正式トークン定義へ置き換え、残骸を残さない
5. The Feedman Theme Module shall ライト / ダークそれぞれの色トークン集合を独立して参照可能な形で公開し、
   一方の値で他方の値を代用しない

### Requirement 2: Material 3 テーマ適用（FeedmanTheme）

**Objective:** As a Composable 実装者, I want テーマ Composable を 1 箇所適用するだけで配色・形状・タップ
標的が反映されてほしい, so that 各画面で個別にスタイルを組まずに済む

#### Acceptance Criteria

1. When アプリのルート Composable に Feedman Theme Composable を適用したとき, the Feedman Theme Module shall
   Material 3 ライト / ダーク両方の ColorScheme に FM_THEME 由来の色を一貫してマッピングして提供する
2. When 端末のダークモード設定が切り替わったとき, the Feedman Theme Module shall 配下の Composable に対し
   再コンポジションを経て対応する ColorScheme へ追従する
3. The Feedman Theme Module shall Material 3 標準の ColorScheme に収まらない独自トークン（少なくとも
   カード背景色、および既読項目用の前景 opacity `0.55`）を、テーマ経由でアクセス可能な拡張プロパティ
   として提供する
4. The Feedman Theme Module shall プロト準拠のスター色（star）および破壊的操作色（danger）を、Material 3
   標準ロールに丸め込まず独立したトークンとして公開する
5. If 仮実装の `FeedmanTheme` がプロト準拠ではない暫定マッピングを持っている場合, the Feedman Theme Module
   shall 本要件のマッピングで完全に置き換える

### Requirement 3: テーマモードの選択と永続化

**Objective:** As a ユーザー, I want アプリ内でライト / ダーク / 端末追従を選び、その選択が再起動後も保持
されてほしい, so that 自分の好みの見え方を毎回設定し直さずに済む

#### Acceptance Criteria

1. The Feedman Theme Module shall ユーザーが選択可能なテーマモードとして「端末追従」「ライト固定」
   「ダーク固定」の 3 種類を提供する
2. While アプリ初回起動時、かつユーザーがテーマモードを一度も指定していないとき, the Feedman Theme Module
   shall 既定として「端末追従」を選択する
3. When ユーザーがテーマモードを切り替えたとき, the Feedman Theme Module shall 切り替え後のモードに対応
   する ColorScheme を現在表示中の画面に再コンポジションで即時適用する
4. When ユーザーがテーマモードを切り替えたとき, the Feedman Theme Module shall 当該選択を永続化し、
   プロセス終了後の次回起動時に同じ選択を復元する
5. While テーマモードが「端末追従」に設定されているとき, the Feedman Theme Module shall 端末のシステム
   ダークモード設定の変化に追従して ColorScheme を切り替える
6. If 永続化された値の読み出しに失敗した場合, the Feedman Theme Module shall フォールバックとして
   「端末追従」を適用し、ユーザー操作なしでアプリの起動を継続する

### Requirement 4: 寸法トークン（Dimens）

**Objective:** As a UI 実装者, I want 角丸・タップ標的・アイコンサイズの基準値をテーマから参照したい,
so that SPEC §8 と異なる寸法をうっかり混入させない

#### Acceptance Criteria

1. The Feedman Theme Module shall 角丸の寸法トークンとして SPEC §8 が定める `10dp`〜`16dp` の範囲を
   カバーする値を提供する
2. The Feedman Theme Module shall 操作可能要素の最小タップ標的として `44dp` を提供する
3. The Feedman Theme Module shall アイコンサイズの寸法トークンとして SPEC §8 が定める `18dp`〜`22dp` の
   範囲をカバーする値を提供する
4. Where 寸法トークンが定義されているとき, the Feedman Theme Module shall 各値の単位として `dp` を
   採用し、ピクセル直値での提供を行わない

## Non-Functional Requirements

### NFR 1: 正本整合性

1. The Feedman Theme Module shall `design/mobile/fm-data.jsx` の `FM_THEME` に存在しない色（独自に
   発明した色相・トーン）をトークンとして公開しない
2. If `FM_THEME` の値が更新された場合, the Feedman Theme Module shall 同じ値が反映されるまでの差分を
   コメント上の oklch 表記から目視で照合可能な状態に保つ

### NFR 2: アクセシビリティ

1. The Feedman Theme Module shall ライトおよびダークの両モードで、本文テキスト色と既定サーフェス色の
   コントラスト比が WCAG AA（通常テキスト 4.5:1 以上）を満たす組み合わせを既定として提供する
2. The Feedman Theme Module shall 操作可能要素のタップ標的最小値として `44dp` を保ち、これを下回る
   寸法トークンを操作可能要素向けに公開しない

### NFR 3: テスト容易性

1. The Feedman Theme Module shall Compose プレビュー（ライト / ダークそれぞれ）でテーマを単独適用して
   レンダリング確認できる形で公開される
2. The Feedman Theme Module shall テーマモード選択ロジックを、永続化層の実物を起動せずに単体検証
   できる粒度で公開する

## Out of Scope

- 個別 UI コンポーネント（カード・ボタン・スター・既読表現など）の実装。後続 Issue #26-#28 で扱う
- カスタムフォント（Geist 等）のバンドル。v1 はシステムフォントで開始し、必要なら別 Issue で扱う
- アクセント色の複数候補化やユーザーによるアクセント切替 UI（SPEC §8 で Indigo に確定済み）
- ハイコントラストモード・色覚多様性向けパレットの追加提供
- Dynamic Color（Android 12+ のシステム壁紙連動）の採用判断
- 永続化の具体的な保存先選定・スキーマ設計（design.md / 実装の領分）
- テーマモード選択 UI 画面の見た目・遷移仕様（設定シート側の Issue で扱う）

## Open Questions

- なし（Issue #25 本文・SPEC §8・GRAND-DESIGN §5.5・`FM_THEME` 定義によって本要件の論点は確定済み）

## 関連

- Parent: #4
- Depends on: #1
