# Requirements Document

## Introduction

ユーザーが新しい RSS / Atom フィードをアプリから登録できるようにする機能。ドロワーフッタ等から呼び出されるボトムシートで URL を入力し、サーバーへ登録要求を送信する。サーバー側は URL からフィードを自動検出するため、アプリ側は URL 文字列を渡すだけでよいが、サーバーは重複登録・URL 不正・登録専用レート制限などのエラーを返すため、登録シートはこれらをユーザーに分かる文言で表示し、再操作可能な状態に戻す責務を持つ。本要件は `design/SPEC.md` §5.5 の登録仕様、§4.3 のエラーフォーマット、`design/mobile/fm-sheets.jsx` の `FMRegisterSheet` 視覚基準を正本として扱う。

## Requirements

### Requirement 1: 登録シートの起動と入力

**Objective:** As a Feedman 利用者, I want フィード登録ボトムシートで URL を入力できる, so that 新規購読を追加できる

#### Acceptance Criteria

1. When ユーザーがフィード登録導線（ドロワーまたは購読画面上のフィード追加ボタン）を選択したとき, the FeedRegisterSheet shall ボトムシート形式で表示され URL 入力欄を空状態でフォーカス可能にする
2. When 登録シートが開いた直後の状態で表示されるとき, the FeedRegisterSheet shall 入力欄のプレースホルダおよび説明文として「サイトの URL か RSS/Atom の URL を入力」する旨を提示する
3. When ユーザーが入力欄に文字列を入力したとき, the FeedRegisterSheet shall 入力値を保持し送信ボタンの活性状態を入力値の有無に応じて更新する
4. While 入力欄が空または空白文字のみの状態, the FeedRegisterSheet shall 送信ボタンを非活性として送信を抑止する
5. When ユーザーがシートのスクリム・閉じるボタン・下方向ドラッグのいずれかで閉じる操作を行ったとき, the FeedRegisterSheet shall シートを閉じ入力状態を破棄する

### Requirement 2: クライアント側 URL バリデーション

**Objective:** As a Feedman 利用者, I want 明らかに不正な URL の送信を事前に止めてもらう, so that 無駄な通信や待ち時間が発生しない

#### Acceptance Criteria

1. When ユーザーが送信操作を行い、入力値が http または https スキームを持つ絶対 URL として解釈できないとき, the FeedRegisterSheet shall 登録要求を送信せず入力欄付近にエラー文言「URL の形式が正しくありません」を表示する
2. If クライアント側バリデーションが失敗状態にあるとき, the FeedRegisterSheet shall 入力欄を編集可能なまま維持し、ユーザーが修正できる状態を保つ
3. When ユーザーがエラー表示後に入力内容を変更したとき, the FeedRegisterSheet shall 直前のクライアント側バリデーションエラー表示を解除する
4. The FeedRegisterSheet shall 入力前後の半角/全角空白を送信前に除去した結果を検証および送信対象とする

### Requirement 3: 登録要求の送信と進行状態

**Objective:** As a Feedman 利用者, I want 送信中であることが分かるフィードバックがほしい, so that 二重送信や待たされ不安なくシートを操作できる

#### Acceptance Criteria

1. When ユーザーが有効な URL で送信操作を行ったとき, the FeedRegisterSheet shall サーバーへフィード登録要求を送出する
2. While サーバー応答を待機中, the FeedRegisterSheet shall 送信ボタンをローディング表示に切り替え、再送信操作を抑止する
3. While サーバー応答を待機中, the FeedRegisterSheet shall 入力欄の編集を抑止しシートを閉じる操作以外を制限する
4. If 待機中にユーザーがシートを閉じたとき, the FeedRegisterSheet shall 登録結果に関わらずトーストや結果表示を行わずシート状態のみ破棄する

### Requirement 4: 登録成功時の挙動

**Objective:** As a Feedman 利用者, I want 登録が成功したことが明確に分かる, so that 次のフィードを追加するかタイムラインに戻るか判断できる

#### Acceptance Criteria

1. When サーバーが登録成功応答を返したとき, the FeedRegisterSheet shall シートを閉じる
2. When 登録成功応答を受け取ったとき, the FeedRegisterSheet shall 「フィードを登録しました」相当の成功トーストを表示する
3. The FeedRegisterSheet shall 登録成功時に同一セッション内で再度同じ URL を送信できる状態（入力初期化）でシートを終了する

### Requirement 5: 登録エラー時のユーザー向けメッセージング

**Objective:** As a Feedman 利用者, I want 登録が失敗した理由が分かる, so that 入力修正や時間をおいた再試行など適切な行動が取れる

#### Acceptance Criteria

1. When サーバーが重複登録を示すエラー応答を返したとき, the FeedRegisterSheet shall 入力欄付近に「このフィードはすでに登録されています」相当の文言を表示し、シートを閉じない
2. When サーバーが URL 不正・フィード未検出を示すエラー応答を返したとき, the FeedRegisterSheet shall 入力欄付近に「このサイトでフィードを検出できませんでした」相当の文言を表示し、シートを閉じない
3. When サーバーが登録専用のレート制限を示すエラー応答を返し、応答に再試行可能までの秒数が含まれるとき, the FeedRegisterSheet shall ユーザーに再試行可能までの目安時間を含むエラー文言を表示する
4. When サーバーが登録専用のレート制限を示すエラー応答を返し、応答に再試行可能までの秒数が含まれないとき, the FeedRegisterSheet shall 「しばらくしてから再度お試しください」相当の文言を表示する
5. If 上記以外の 4xx / 5xx エラー応答を受け取ったとき, the FeedRegisterSheet shall サーバー応答の `message` フィールドを優先しつつ、欠落時は汎用エラー文言「フィードの登録に失敗しました」にフォールバックして表示する
6. If 通信失敗（ネットワーク到達不可・タイムアウト）が発生したとき, the FeedRegisterSheet shall 「ネットワークに接続できません」相当の文言を表示しシートを閉じない
7. While エラー文言が表示されている状態, the FeedRegisterSheet shall 入力欄および送信ボタンを再操作可能な状態に戻す
8. When ユーザーがエラー文言表示後に入力内容を変更したとき, the FeedRegisterSheet shall 表示中のサーバー由来エラー文言を解除する

### Requirement 6: 登録結果の取り扱い範囲

**Objective:** As Feedman 利用者, I want 登録したフィードがアプリの他画面と矛盾しない, so that 操作後の混乱を避けられる

#### Acceptance Criteria

1. The FeedRegisterSheet shall 登録成功時にローカル状態として最新の登録 URL を破棄し、シート自体に登録済みフィード一覧を保持しない
2. Where 購読一覧画面が同時に表示されている場合でも, the FeedRegisterSheet shall 購読一覧の即時反映を自身の責務として行わない（再取得は別 Issue #45 のスコープ）

## Non-Functional Requirements

### NFR 1: 応答性と再操作性

1. While サーバー応答待機中, the FeedRegisterSheet shall 送信ボタン押下から 200 ミリ秒以内にローディング表示へ遷移する
2. If サーバー応答が 30 秒以内に返らないとき, the FeedRegisterSheet shall 通信失敗として扱い Requirement 5.6 のメッセージを表示する
3. The FeedRegisterSheet shall ローディング表示中もシートを閉じる操作（スクリム・閉じるボタン・下方向ドラッグ）を 1 秒以内に受け付ける

### NFR 2: アクセシビリティと表示一貫性

1. The FeedRegisterSheet shall 入力欄・送信ボタン・閉じるボタンそれぞれにユーザーが読み上げ可能な日本語ラベルを付与する
2. The FeedRegisterSheet shall エラー文言を入力欄と視覚的に関連付けて表示し、画面リーダーで入力エラーであることが識別可能な形にする
3. The FeedRegisterSheet shall `design/mobile/fm-sheets.jsx` の `FMRegisterSheet` を視覚基準として、ヘッダ・入力欄・主ボタン・閉じる操作の構成を維持する

### NFR 3: メッセージ文言の一貫性

1. The FeedRegisterSheet shall すべてのユーザー向け文言を日本語で表示する
2. The FeedRegisterSheet shall サーバー応答の `message` を表示する場合でも、改行・HTML タグ・制御文字を素の状態でレンダリングせず、プレーンテキストとして表示する

## Out of Scope

- 登録成功後の購読一覧画面の自動再取得・差分反映（Issue #45 のスコープ）
- OPML 形式での一括インポート
- フィード URL の事前検出プレビュー（プロトタイプの "done" ステージで表示される検出済みフィード名・確認 UI は v1 では再現せず、サーバーの自動検出結果はトーストのみで通知）
- フィード URL 変更 UI / 削除 UI（SPEC §4.2 で v1 UI 対象外）
- 登録専用レート制限の長期記憶（次回シート起動時に前回のレート制限残時間を引き継ぐ動作は本スコープ外）
- 通知設定・購読間隔設定など、登録後のフィード固有設定（#46 等の別 Issue で扱う）

## Open Questions

- SPEC §4.3 のエラー `code` 一覧のうち、フィード登録（POST /api/feeds）が返す具体的な code 文字列（重複登録 / URL 不正 / フィード未検出 / 登録レート制限）が SPEC / SERVER の双方に明示列挙されていない。Architect / Developer フェーズでサーバー側ハンドラ（`feedman/internal/handler` 配下）の実装から正確な code を確認し、design.md で固定すること。本要件は SPEC §4.3 の枠組み（`code` で分岐し `message` を基本表示）に従う前提で「重複」「不正 / 未検出」「レート制限」の 3 カテゴリに分けてユーザー文言を規定している
- 登録専用レート制限応答に SPEC §4.3 の `details.retry_after_seconds` が含まれるか否かはサーバー実装次第。Requirement 5.3 / 5.4 で両ケースに対応する文言切替を規定済みだが、実値（秒単位・分単位の表示丸め）の細かい表現は design.md / 実装で確定する

## 関連

- Parent: #9
- Depends on: #17 #28 #31
