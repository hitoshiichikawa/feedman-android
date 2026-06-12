# Requirements Document

## Introduction

購読設定シート（SPEC §5.6）は、ユーザーが個別フィードの購読に対して「フェッチ間隔の変更」「停止／エラー状態からの再開」「購読解除」を行うためのボトムシート UI である。ドロワー内のフィード行および各フィード画面から開く統一的な操作面として機能し、購読の状態を変える破壊的操作（解除）は確認ダイアログを介してユーザーの誤操作を防ぐ。フェッチ間隔セグメントの値はサーバー側バリデーション（30〜720 分・30 分刻み）に整合させるため `30/60/180/360 分` に固定する（2026-06-12 人間決定済み）。視覚・挙動の基準は `design/mobile/fm-sheets.jsx` の `FMSettingsSheet` を正とする。

## Requirements

### Requirement 1: 設定シートの起動と表示

**Objective:** As a Feedman Android アプリのユーザー, I want 購読設定シートをドロワーとフィード画面の双方から開けること, so that 設定したいフィードを迷わず選んで操作できる

#### Acceptance Criteria

1. When ユーザーがドロワー上のフィード行に付いた設定アイコンをタップしたとき, the Subscription Settings Sheet shall 当該フィードを対象として設定シートをボトムシートで開く
2. When ユーザーがフィード別記事一覧画面の設定導線をタップしたとき, the Subscription Settings Sheet shall 当該フィードを対象として設定シートをボトムシートで開く
3. While 設定シートが開いているとき, the Subscription Settings Sheet shall 対象フィードのタイトル・favicon・未読件数を表示する
4. When ユーザーがシートのクローズアイコン／シート外領域／システム戻る操作のいずれかでクローズを指示したとき, the Subscription Settings Sheet shall シートを閉じて元の画面に戻す

### Requirement 2: フェッチ間隔の表示と保存

**Objective:** As a ユーザー, I want フィードごとのフェッチ間隔を 30/60/180/360 分から選んで保存できること, so that 自分の閲覧頻度に合わせた更新間隔に調整できる

#### Acceptance Criteria

1. The Subscription Settings Sheet shall フェッチ間隔のセグメントとして `30 分` `60 分` `180 分` `360 分` の 4 値のみを表示する
2. When 設定シートが対象フィードの現在のフェッチ間隔を持って開かれたとき, the Subscription Settings Sheet shall その値に対応するセグメントを選択状態として表示する
3. If 対象フィードのフェッチ間隔が 30/60/180/360 分のいずれにも一致しないとき, the Subscription Settings Sheet shall 値を変更せずに「未選択」状態のセグメント表示にとどめ、保存ボタンを押すまでサーバーへの書き込みを行わない
4. When ユーザーが新しい間隔セグメントを選択して保存を指示したとき, the Subscription Settings Sheet shall 選択値を購読設定の更新としてサーバーに送信し、成功したら設定シートを閉じて完了をトースト等でユーザーに知らせる
5. While フェッチ間隔の保存リクエスト送信中, the Subscription Settings Sheet shall 保存ボタンを進行中の状態に切替え、追加の保存操作を受け付けない
6. If フェッチ間隔の保存リクエストが失敗したとき, the Subscription Settings Sheet shall シートを開いたまま、対象フィードのフェッチ間隔表示を保存前の旧値に戻し、エラーメッセージを表示する

### Requirement 3: 停止／エラー状態からの再開

**Objective:** As a ユーザー, I want 停止中またはエラーになったフィードを再開できること, so that 一時的に止まったフィードを設定シートから直接復旧できる

#### Acceptance Criteria

1. Where 対象フィードの状態が `stopped` または `error` であるとき, the Subscription Settings Sheet shall 状態バッジ・エラーメッセージ（ある場合）・再開アクションを表示する
2. When ユーザーが再開アクションをタップしたとき, the Subscription Settings Sheet shall 当該フィードを再開する要求をサーバーに送信する
3. When 再開要求が成功したとき, the Subscription Settings Sheet shall 完了をユーザーに通知し、対象フィードの状態を `active` として購読一覧（ドロワー）と整合させる
4. While 対象フィードの状態が `active` であるとき, the Subscription Settings Sheet shall 再開アクション・状態バッジ・エラーメッセージを表示しない
5. If 再開要求が失敗したとき, the Subscription Settings Sheet shall シートを開いたまま、対象フィードの状態表示を変更せず、エラーメッセージを表示する

### Requirement 4: 購読解除とその後処理

**Objective:** As a ユーザー, I want 購読の解除を明示的な確認を経て実行できること, so that 誤操作なくフィードを削除し、削除後の表示状態の不整合を避けられる

#### Acceptance Criteria

1. When ユーザーが購読解除アクションをタップしたとき, the Subscription Settings Sheet shall 解除確認ダイアログを表示し、ユーザーが明示的に「解除」を選ぶまで解除リクエストを送信しない
2. When ユーザーが解除確認ダイアログをキャンセルしたとき, the Subscription Settings Sheet shall 解除リクエストを送信せず、設定シートを開いたままにする
3. When ユーザーが解除確認ダイアログで解除を確定したとき, the Subscription Settings Sheet shall 対象フィードの購読解除要求をサーバーに送信する
4. When 購読解除が成功したとき, the Subscription Settings Sheet shall 設定シートを閉じ、ドロワーの購読一覧から当該フィードを除去する
5. When 購読解除が成功し、かつ当該フィードのフィード別記事一覧画面が表示中であったとき, the Feedman Android App shall 当該画面から退避し、ユーザーを横断新着タイムラインに戻す
6. While 購読解除リクエスト送信中, the Subscription Settings Sheet shall 解除操作の二重実行と他の保存操作を受け付けない
7. If 購読解除リクエストが失敗したとき, the Subscription Settings Sheet shall 設定シートを開いたまま、購読一覧と画面遷移に変更を加えず、エラーメッセージを表示する

### Requirement 5: 失敗時のフィードバックと旧値保持

**Objective:** As a ユーザー, I want 通信失敗時に直前の状態が保持されてエラー原因が分かること, so that 想定外の状態変化に巻き込まれず再操作できる

#### Acceptance Criteria

1. If 購読設定の更新・再開・解除のいずれかのサーバー要求が HTTP エラーまたはネットワーク失敗で完了しなかったとき, the Subscription Settings Sheet shall サーバー応答のエラーメッセージ（ない場合は汎用メッセージ）をユーザーに表示する
2. If 操作の途中で失敗したとき, the Subscription Settings Sheet shall 対象フィードの楽観的に変更した表示（間隔セグメント選択／状態バッジ／一覧からの除去）をすべて操作前の状態にロールバックする
3. If 失敗が認証切れ（未ログイン相当）に起因するとき, the Subscription Settings Sheet shall 設定シートを閉じ、ユーザーをアプリのログイン導線に誘導する

## Non-Functional Requirements

### NFR 1: 応答性とフィードバック

1. While いずれかのサーバー要求送信中, the Subscription Settings Sheet shall 該当アクションのボタンを 100ms 以内に進行中状態に切り替える
2. The Subscription Settings Sheet shall ユーザーが操作した結果（保存成功・再開成功・解除成功・失敗メッセージ）を、サーバー応答受信から 500ms 以内に画面に反映する

### NFR 2: 視覚整合

1. The Subscription Settings Sheet shall 視覚レイアウト・余白・配色・タイポを `design/mobile/fm-sheets.jsx` の `FMSettingsSheet` に整合させる（タップ標的は最小 44px、シート高さは画面の概ね 2/3）

### NFR 3: アクセシビリティ

1. The Subscription Settings Sheet shall フェッチ間隔セグメントの各選択肢に選択状態を読み上げ可能なラベルを付与する
2. The Subscription Settings Sheet shall 解除確認ダイアログを、システム戻る操作および外部タップでキャンセル扱いとして閉じることを許容する

## Out of Scope

- フィード URL の変更 UI（v1 では対象外、SPEC §1.3）
- フィード削除 UI（購読解除のみ。SPEC §4.2 表注記）
- フェッチ間隔の自由入力（15 分などサーバー仕様外の値を含む任意入力）
- 手動フェッチ（Pull-to-refresh / `POST .../fetch`）の起動：本シートでは扱わずフィード別画面側で扱う（Issue #28 系で対応済み）
- キーワード通知設定（v1 スコープ外、SPEC §5.8）
- 退会・ログアウト（アカウント画面側で扱う、SPEC §5.7）
- ドロワーや記事画面側で表示するエラーバナー等の上位 UI 仕様（本シートは状態整合のみ責任を持つ）

## Open Questions

- なし（フェッチ間隔セグメントの値は 30/60/180/360 分で確定済み、2026-06-12）
