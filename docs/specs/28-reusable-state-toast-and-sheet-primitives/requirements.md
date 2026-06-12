# Requirements Document

## Introduction

Feedman Android の各一覧画面（横断タイムライン / フィード別 / スター / 検索）と各種ボトムシートでは、
「読み込み中」「空状態」「エラー＋再試行」「終端表示」「軽量な通知（トースト）」「ボトムシート枠」
といった同種の状態表示・UI 部品が繰り返し必要になる。これらを画面ごとに個別実装すると、
プロトタイプ（`design/mobile/fm-ui.jsx` の `FMEmpty` および `fm-sheets.jsx` の `FMSheet` / `FMToast`）が
意図する視覚・挙動からズレが生じ、SPEC §6「共通の挙動・状態」の要求（無限スクロール終端表示・
空 / エラー / ローディング表示の共通化）も満たせない。

本 Issue は、`core/ui` 配下に状態表示部品（StateViews）・通知ヘルパ（Snackbar/Toast）・
ボトムシート共通枠（FeedmanSheet）を **再利用可能なプリミティブとして提供する** ことを目的とする。
各画面への組み込みは本 Issue の対象外であり、後続の機能 Issue（#52 など）で行う。

## Requirements

### Requirement 1: ローディング表示プリミティブ

**Objective:** As a Feedman 利用者, I want 一覧画面で読み込み中であることを視覚的に把握できる, so that 操作中なのか応答が止まっているのかを判別できる

#### Acceptance Criteria

1. While 一覧の初回読み込みが未完了の状態, the StateViews shall コンテンツ領域全体を占有するローディング表示（中央寄せのインジケータ）を表示する
2. While 追加ページの読み込み（無限スクロール）が進行中, the StateViews shall リスト末尾にフッターサイズのローディング表示（インジケータ単独）を表示する
3. The StateViews shall 初回ローディングと追加ローディングを別個のコンポーネントとして公開する

### Requirement 2: 空状態表示プリミティブ

**Objective:** As a Feedman 利用者, I want データが空のときに何が起きていてどう操作すれば良いか分かる, so that 「壊れている」と誤解せずに次の行動が取れる

#### Acceptance Criteria

1. While 一覧の取得が完了し結果が 0 件の状態, the StateViews shall アイコン・主題テキスト・補助テキストを縦中央に配置した空状態を表示する
2. The StateViews shall 表示するアイコン・主題テキスト・補助テキストを呼び出し側から差し替え可能にする
3. Where 補助テキストが省略された呼び出し, the StateViews shall アイコンと主題テキストのみを表示する
4. The 空状態表示 shall `design/mobile/fm-ui.jsx` の `FMEmpty` と同等の構図（アイコン → 主題 → 補助）・余白・テキスト階層を踏襲する

### Requirement 3: エラー表示と再試行プリミティブ

**Objective:** As a Feedman 利用者, I want 通信エラー時にメッセージを確認しそのまま再試行できる, so that エラー後に画面を離れずに復帰できる

#### Acceptance Criteria

1. If 一覧の読み込みがエラーで終了した, the StateViews shall エラーメッセージと再試行ボタンを含むエラー表示をコンテンツ領域全体に表示する
2. When 利用者が再試行ボタンを押下したとき, the StateViews shall 呼び出し側から渡された再試行ハンドラを 1 回呼び出す
3. The StateViews shall エラーメッセージ文字列を呼び出し側から差し替え可能にする
4. If 追加ページの読み込みがエラーで終了した, the StateViews shall リスト末尾に再試行可能なフッター型エラー表示を表示する

### Requirement 4: 終端フッタープリミティブ

**Objective:** As a Feedman 利用者, I want 無限スクロールで最後まで到達したことを把握できる, so that 「まだ続きがあるのか」と無駄に下方スクロールしない

#### Acceptance Criteria

1. While ページネーションが終端に達した状態, the StateViews shall リスト末尾に「最後まで読みました」相当の終端フッターを表示する
2. The 終端フッター shall 追加ローディング・追加エラーと相互排他で表示される（同時には現れない）
3. The 終端フッターの表示文言 shall アプリ全体で 1 つに統一される

### Requirement 5: スナックバー / トースト通知ヘルパ

**Objective:** As a Feedman 利用者, I want 楽観的更新の結果や操作完了を短いメッセージで知ることができる, so that 自分の操作が反映されたかを確認できる

#### Acceptance Criteria

1. When 任意の画面から通知ヘルパに文字列メッセージが渡されたとき, the Snackbar Helper shall 画面下部に短時間表示される通知をスケジュールする
2. The Snackbar Helper shall 同時に表示される通知が常に 1 件であることを保証する（連続発行時は後続が前を置き換えるか順次表示される）
3. The Snackbar Helper shall 一定時間経過後に自動で通知を消去する
4. The Snackbar Helper shall 表示文言を呼び出し側から渡された文字列のみで構成し、ヘルパ内部に固定文言を持たない
5. Where アクション付き通知が指定された, the Snackbar Helper shall ラベルとアクションコールバックを伴う通知を表示する

### Requirement 6: ボトムシート共通枠 FeedmanSheet

**Objective:** As a Feedman 利用者, I want どのボトムシートでも同じ枠（ドラッグハンドル・角丸・余白）で開かれる, so that 一貫した操作感で内容に集中できる

#### Acceptance Criteria

1. While FeedmanSheet が表示中の状態, the FeedmanSheet shall 上端中央にドラッグハンドル、上端両角の丸み、下端のセーフエリア確保を伴う枠を提示する
2. The FeedmanSheet shall 枠内に任意の内容（slot）を差し込めるようにする
3. The FeedmanSheet shall 閉じる操作（ドラッグ下げ・スクリム外側タップ）に対する閉鎖ハンドラを呼び出し側から受け取る
4. The FeedmanSheet shall アクセシビリティ用のラベル文字列を呼び出し側から受け取り、スクリーンリーダーから参照可能にする
5. The FeedmanSheet の視覚仕様（角丸半径・ハンドル寸法・スクリム濃度・余白） shall `design/mobile/fm-sheets.jsx` の `FMSheet` を基準とし、SPEC §8 のデザイントークンに整合する

### Requirement 7: プリミティブの再利用境界

**Objective:** As Feedman の開発者, I want 状態表示・通知・シート枠が単一のモジュールから提供される, so that 後続の各画面 Issue で重複実装を生まず、視覚・挙動を一箇所で調整できる

#### Acceptance Criteria

1. The 本 Issue で追加するプリミティブ群 shall 共通 UI モジュール（`core/ui`）配下に配置され、機能別モジュールには配置しない
2. The 本 Issue shall プリミティブ群を `core/ui` に追加するのみで、既存の画面・ボトムシートに対する組み込み・置換は行わない
3. The 本 Issue で追加するプリミティブ群 shall 公開 API として呼び出し方とパラメータを提示し、機能別 Issue から再利用可能な状態で完了とみなされる

## Non-Functional Requirements

### NFR 1: ビジュアル整合性

1. The StateViews / Snackbar Helper / FeedmanSheet shall SPEC §8 のデザイントークン（配色・角丸・タイポ・タップ標的）を参照して描画される
2. The FeedmanSheet および StateViews のタップ可能要素 shall 最小 44px のタップ標的サイズを満たす
3. The プリミティブ群 shall ライト / ダーク両テーマで読み取り可能な配色を維持する（ダーク時に低コントラストにならない）

### NFR 2: ロケール

1. The プリミティブ群が直接表示する固定文言（終端フッターの「最後まで読みました」など） shall リソース文字列として定義され、ハードコードされない

### NFR 3: テスト容易性

1. The プリミティブ群の状態判定ロジック（どの状態を表示するかの分岐、通知のキュー制御など） shall UI 描画と分離され、JVM 単体テストで検証可能な単位を持つ
2. The Compose UI コンポーネント自体の描画検証 shall instrumented テストの領分とし、本 Issue では JVM テストの必須対象としない

## Out of Scope

- 各画面（横断タイムライン / フィード別 / スター / 検索 / 各種シート）への本プリミティブ群の組み込み・置換（後続 Issue #52 ほか機能別 Issue で実施）
- Pull-to-refresh コンポーネントの提供（別 Issue の領分）
- 既存ボトムシート（`FMDetailSheet` / `FMSettingsSheet` 等）の中身の再実装
- 通知のキュー深さ制御・優先度・カスタムアニメーション（本 Issue では基本動作のみ）
- アクセシビリティの読み上げ文言精緻化（ラベル受け取り口の提供までを本 Issue とし、各画面の文言は組み込み側で確定）
- instrumented テストの整備（Compose UI 検証は領分外）

## Open Questions

- なし（視覚仕様はプロトタイプ `FMEmpty` / `FMSheet` / `FMToast`、デザイントークンは SPEC §8、共通挙動は SPEC §6 を参照する前提で要件を確定）
