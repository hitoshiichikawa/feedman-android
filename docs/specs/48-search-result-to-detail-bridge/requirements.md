# Requirements Document

## Introduction

Feedman Android の横断検索画面（#47 で導入済み）は、現時点では `ItemSearchHit` を結果カードとして列挙するのみで、検索結果カードから記事詳細シート（#36）や元記事の外部閲覧（#37）への導線が存在しない。本 Issue では検索結果カードをタップ可能なエントリポイントに昇格させ、共有の記事詳細シートおよび共通の外部リンク導線へ接続する。あわせて、ItemStateStore（#38）の既読／スターオーバーレイ購読を検索画面側にも組み込み、詳細シートやカード内アクションで発生した既読化・スター変更を検索結果一覧の見た目にもリアクティブに反映させる。

本 Issue のスコープは検索画面の結線・購読接続および記事詳細シート側のスター状態整合に限定し、検索画面本体の入力・空クエリ表示・サジェスト・cursor paging のロジック（#47）や、詳細シート・元記事閲覧の中身そのもの（#36 / #37）には踏み込まない。変更影響範囲は `feature/search` 配下の結線箇所と、`feature/articledetail` 配下の購読接続に閉じる。

## Requirements

### Requirement 1: 検索結果カードから記事詳細シートを開く

**Objective:** As a Feedman Android アプリ利用者, I want 検索結果カードをタップしたら共有の記事詳細シートが開くこと, so that 検索からそのままプレビューと既読化を含む通常の閲覧フローへ進める

#### Acceptance Criteria

1. When ユーザーが検索結果カードの本体をタップしたとき, the Search Results Screen shall 当該ヒットの item を対象として記事詳細シートを表示する
2. When 記事詳細シートが検索結果から起動されたとき, the Article Detail Sheet shall 横断タイムラインやフィード別一覧からの起動時と同一の表示・操作仕様で開く
3. When 記事詳細シートが検索結果から起動されたとき, the Search Results Screen shall 同じカードのタップで詳細シートを多重起動しないように、シートが閉じるまで同一カードからの再起動操作を抑止する
4. When 記事詳細シートが閉じられたとき, the Search Results Screen shall 検索結果一覧のスクロール位置と入力中のキーワードを保持したまま結果表示に戻る
5. If 当該ヒットの item 詳細取得に失敗したとき, the Article Detail Sheet shall ユーザーが認識できるエラー表示を行い、検索結果一覧に対する破壊的変更を行わない

### Requirement 2: 検索結果カードの外部リンクアイコンからの元記事閲覧

**Objective:** As a Feedman Android アプリ利用者, I want 検索結果カードの外部リンクアイコンから記事詳細シートを経由せずに元記事を開きたい, so that 興味が明確なヒットを 1 タップで本文へ直行できる

#### Acceptance Criteria

1. When ユーザーが検索結果カードの外部リンクアイコンを押下したとき, the Search Results Screen shall 共通の外部リンク導線を通じて当該ヒットの元記事 URL を開く要求を発火する
2. When ユーザーが検索結果カードの外部リンクアイコンを押下したとき, the Search Results Screen shall 同じカードの本体タップで開く記事詳細シートを起動しない
3. When 検索結果カードの外部リンクアイコン押下によって元記事の起動要求が発火されたとき, the Search Results Screen shall 当該 item を既読として扱うよう既読化トリガーを発行する
4. If 元記事 URL の起動が拒否または失敗したとき, the Search Results Screen shall ユーザーが認識できるエラー表示を行い、当該ヒットに対する既読化を取り消す

### Requirement 3: 検索結果一覧への既読／スターオーバーレイ購読

**Objective:** As a Feedman Android アプリ利用者, I want 検索結果カードの既読／スター表示が他画面での操作と連動すること, so that 検索結果と詳細シート・タイムラインの間で既読・スター状態が割れない

#### Acceptance Criteria

1. While 検索結果一覧が表示されているとき, the Search Results Screen shall 各ヒットの既読／スター表示を共通の状態ストリームから購読する
2. When 共通の状態ストリームで item の既読またはスター状態が更新されたとき, the Search Results Screen shall 検索結果一覧で当該 item の表示を追加のサーバー再取得なしに更新する
3. When 詳細シートを開いたことや外部リンク起動に伴って item の既読状態が更新されたとき, the Search Results Screen shall 当該カードの既読表示（不透明度低下を含むカード見た目）を即座に反映する
4. When 詳細シートのスタートグルにより item のスター状態が更新されたとき, the Search Results Screen shall 当該カードのスター表示を即座に反映する
5. While 共通の状態ストリームに上書き値が無い item について, the Search Results Screen shall サーバー由来の `ItemSearchHit` の既読／スター値をそのまま表示する
6. When 検索結果のページが追加読み込みされたとき, the Search Results Screen shall 既存の既読／スター上書き値を維持したまま新規ヒットを合成表示する

### Requirement 4: スコープ境界

**Objective:** As a Feedman Android プロジェクト関係者, I want 本 Issue の変更影響範囲と扱わない事項が明示されていること, so that 検索画面本体や詳細シート本体への二重実装やスコープ膨張を避けられる

#### Acceptance Criteria

1. The Search-to-Detail Bridge feature shall ソース変更を `feature/search` 配下の結線追加と `feature/articledetail` 配下の購読接続に限定する
2. The Search-to-Detail Bridge feature shall 検索入力欄・空クエリ表示・サジェストチップ・cursor paging のロジックに変更を加えない
3. The Search-to-Detail Bridge feature shall 記事詳細シート本体および外部リンク導線本体の挙動を変更しない
4. The Search-to-Detail Bridge feature shall フィード内検索（`scope=feed`）の UI を扱わない
5. The Search-to-Detail Bridge feature shall キーワード通知（プッシュ通知のキーワード登録）に関する機能を扱わない

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーが検索結果カードをタップしたとき, the Search Results Screen shall 300 ミリ秒以内に詳細シートの起動アニメーションを開始する
2. When 共通の状態ストリームで item の既読またはスター状態が更新されたとき, the Search Results Screen shall 100 ミリ秒以内に該当カードの表示状態を更新する
3. While 検索結果一覧が表示されているとき, the Search Results Screen shall 既読／スター上書き値の購読を理由としたサーバー往復を発生させない

### NFR 2: テスト網羅性

1. The Search Results Screen test suite shall Requirement 1 のカードタップから詳細シート起動までの導線、および詳細シート起動後の検索結果スクロール位置・キーワード保持を最低 1 ケースずつ検証する
2. The Search Results Screen test suite shall Requirement 2 の外部リンクアイコン押下時にカード本体タップによる詳細シート起動が抑止されることを最低 1 ケース検証する
3. The Search Results Screen test suite shall Requirement 3 の既読／スターの上書き反映、追加ページ読み込み後の上書き保持、上書きが無い item でのサーバー由来値表示を最低 1 ケースずつ検証する

## Out of Scope

- 検索入力欄・空クエリ表示・サジェストチップ・cursor paging のロジック（#47 で実装済み）
- 記事詳細シート本体の表示・操作仕様（#36 で実装済み）
- 元記事閲覧の共通導線本体（#37 で実装済み）
- 検索結果カードからの直接スタートグル UI（本 Issue では既存カード仕様の範囲内でスター表示のみ同期する。専用のスタートグル操作要素は対象外）
- フィード内検索（`scope=feed`）の UI
- 検索履歴の永続化およびその UI 表示
- キーワード通知（プッシュ通知のキーワード登録）連携
- 検索結果カードからのディープリンクや共有メニュー連携

## Open Questions

- なし（カードタップ＝詳細シート、外部リンクアイコン＝元記事直行、既読／スター同期＝共通状態ストリーム購読、という挙動方針は `design/SPEC.md` §5.3 / §5.4 / §6 およびタイムラインカード（#33 / #37）・ItemStateStore（#38）の既存仕様と整合する形で確定した）

## 関連

- Parent: #10
- Depends on: #47 #36 #37 #38
