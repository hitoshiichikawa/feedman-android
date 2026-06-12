# Requirements Document

## Introduction

フィード別記事一覧画面（SPEC §5.2）における Pull-to-refresh の正本挙動を定義する。
横断タイムラインの refresh（GET 再取得）は #34 で実装済みのため対象外で、本要件は
フィード単位の手動フェッチ（SPEC §4.2 の `POST /api/subscriptions/{id}/fetch`）と、
クールダウン（SPEC §4.3 の `FEED_COOLDOWN` / `details.retry_after_seconds`）を含むエラー
ハンドリング、成功後の一覧再読込・ドロワー未読バッジ反映までを扱う。ユーザーが
最新記事を取得しに行く操作を期待通り完了させ、サーバー保護のクールダウン規約を
利用者が理解できる形で UI に表出することが目的である。

## Requirements

### Requirement 1: フィード別画面の手動フェッチ起動

**Objective:** As a フィード購読者, I want フィード別画面でリストを下に引いて最新記事を取得したい, so that 自動フェッチ間隔を待たずに新着を確認できる

#### Acceptance Criteria

1. When ユーザーがフィード別記事一覧画面で Pull-to-refresh ジェスチャを完了したとき, the Feed Detail Screen shall 当該フィードに対する手動フェッチ要求を発行する
2. While 手動フェッチ要求が応答待ちであるとき, the Feed Detail Screen shall リフレッシュ進行中インジケータを表示する
3. While 手動フェッチ要求が応答待ちであるとき, the Feed Detail Screen shall 既存の記事一覧の閲覧操作（スクロール・フィルタタブ切替・記事タップ）を継続して受け付ける
4. While 手動フェッチ要求が応答待ちであるとき, the Feed Detail Screen shall 同一フィードに対する追加の Pull-to-refresh 起動を抑止する

### Requirement 2: 成功時の一覧再読込と未読反映

**Objective:** As a フィード購読者, I want 手動フェッチ成功後に最新記事が一覧へ反映されてほしい, so that 取り込まれた新着をその場で読める

#### Acceptance Criteria

1. When 手動フェッチが成功応答を返したとき, the Feed Detail Screen shall 現在選択中のフィルタ条件（すべて / 未読 / スター）で記事一覧を再読込する
2. When 記事一覧の再読込が完了したとき, the Feed Detail Screen shall リフレッシュ進行中インジケータを終了状態に遷移させる
3. When 手動フェッチが成功応答を返したとき, the Navigation Drawer shall 当該フィードの未読件数バッジを最新値に更新する
4. If 再読込結果に新規記事が存在しなかったとき, the Feed Detail Screen shall 既存の一覧表示・スクロール位置を保持する

### Requirement 3: クールダウン応答のユーザー通知

**Objective:** As a フィード購読者, I want クールダウン中であることと再試行可能までの残り秒数を知りたい, so that いつ再操作すればよいか判断できる

#### Acceptance Criteria

1. If 手動フェッチがクールダウン理由で拒否されたとき, the Feed Detail Screen shall 再試行可能までの残り秒数を含むスナックバーを表示する
2. When クールダウン理由のスナックバーが表示されたとき, the Feed Detail Screen shall 残り秒数の表記をサーバー応答で受領した値に基づいて生成する
3. If クールダウン応答に残り秒数の値が含まれない場合, the Feed Detail Screen shall 残り秒数を明示しないクールダウン中である旨のメッセージを表示する
4. If 手動フェッチがクールダウン理由で拒否されたとき, the Feed Detail Screen shall 既存の記事一覧表示を保持し閲覧操作を継続して受け付ける

### Requirement 4: クールダウン以外のエラーハンドリング

**Objective:** As a フィード購読者, I want クールダウン以外の失敗時にも理由を把握しつつ読書を続けたい, so that エラーで画面全体が使えなくなることを避けられる

#### Acceptance Criteria

1. If 手動フェッチがクールダウン以外の理由で失敗したとき, the Feed Detail Screen shall サーバー応答のエラーメッセージをユーザーに表示する
2. If 手動フェッチがクールダウン以外の理由で失敗したとき, the Feed Detail Screen shall 既存の記事一覧表示を保持し閲覧操作を継続して受け付ける
3. If 手動フェッチがネットワーク到達不可で失敗したとき, the Feed Detail Screen shall ネットワーク不通である旨のメッセージを表示する
4. When 手動フェッチが失敗終了したとき, the Feed Detail Screen shall リフレッシュ進行中インジケータを終了状態に遷移させる

## Non-Functional Requirements

### NFR 1: 応答可観測性

1. When 手動フェッチ要求が発行されたとき, the Feed Detail Screen shall リフレッシュ進行中インジケータをジェスチャ完了から 200ms 以内に表示する
2. When サーバー応答（成功・失敗・クールダウン）を受領したとき, the Feed Detail Screen shall リフレッシュ進行中インジケータを応答受領から 500ms 以内に終了状態へ遷移させる

### NFR 2: ユーザー体験の継続性

1. While 手動フェッチ要求が応答待ち・成功処理中・失敗処理中のいずれであっても, the Feed Detail Screen shall 画面全体をブロックするモーダル UI を表示しない

## Out of Scope

- 横断新着タイムライン画面の Pull-to-refresh（#34 で GET 再取得として実装済み）
- 全フィード一括の手動フェッチ API（SPEC §7 の次フェーズ候補）
- フェッチ間隔の変更 UI（購読設定ボトムシートの責務、本 Issue 対象外）
- 停止／エラー状態フィードの「再開」操作（SPEC §5.2 上部警告バナーの責務、本 Issue 対象外）
- クールダウン残り秒数のリアルタイムカウントダウン表示（スナックバー初期表示のみが本要件の対象）
- 手動フェッチ結果の通知（プッシュ通知）連携

## Open Questions

- なし

## 関連

- Parent: #8
- Depends on: #41
