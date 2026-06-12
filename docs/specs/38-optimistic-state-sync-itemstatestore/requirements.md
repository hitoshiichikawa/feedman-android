# Requirements Document

## Introduction

Feedman Android では同じ記事（item）が複数の画面（横断タイムライン・フィード別記事一覧・スター一覧・横断検索・記事詳細シート）に別インスタンスとして表示される。既読／スターの状態をサーバー往復のたびにしか同期しないと、ある画面で行ったトグル結果が別画面に反映されず表示が割れる。本機能は `ItemStateStore` を中心に楽観的更新オーバーレイを導入し、一覧ページング結果より新しいユーザー操作が常に優先表示される一方向データフローを確立することを目的とする。グランドデザイン §5.4 が定める「ページングデータ < オーバーレイ」のマージ規約を実体化し、初期適用範囲としてタイムラインと記事詳細シートを overlay 購読側に切り替える。スター一覧・検索画面の購読側適用は別 Issue（#46 / #48）に分離する。

## Requirements

### Requirement 1: 楽観的状態オーバーレイ

**Objective:** As a Feedman Android ユーザー, I want 記事の既読／スター操作が即座に画面へ反映されること, so that サーバー往復の待ち時間にブロックされずに次の記事を読み進められる

#### Acceptance Criteria

1. When ユーザーが記事の既読状態またはスター状態をトグルしたとき, the ItemStateStore shall 当該 item の overlay を即座に新しい値で更新する
2. When ItemStateStore の overlay が更新されたとき, the ItemStateStore shall 当該変更を購読中のすべての画面へ単一の状態ストリームを通じて配信する
3. The ItemStateStore shall 各 item について「既読の上書き値」と「スターの上書き値」を独立に保持する
4. While ある item に対する overlay が未設定であるとき, the 購読側画面 shall サーバー由来の状態をそのまま表示する

### Requirement 2: サーバー反映とロールバック

**Objective:** As a Feedman Android ユーザー, I want サーバー側更新が失敗したときに表示が正しい状態に戻ること, so that 楽観的表示と実データの不一致に気付かないまま操作を続ける事態を避けられる

#### Acceptance Criteria

1. When 楽観的更新が overlay に適用されたとき, the ItemStateStore shall 続けてサーバー側状態更新リクエストを発行する
2. If サーバー側状態更新リクエストが失敗を返したとき, the ItemStateStore shall 当該 item の overlay を楽観的更新前の値に戻す
3. If サーバー側状態更新リクエストが失敗を返したとき, the 操作を発火した画面 shall エラーメッセージをユーザーに提示する
4. When サーバー側状態更新リクエストが成功を返したとき, the ItemStateStore shall overlay を維持し追加のロールバックを行わない
5. The ItemStateStore shall 「楽観値を適用 → サーバー失敗 → 旧値復元」の順序を、各 item ごとに観測可能なシーケンスとして発行する

### Requirement 3: ページングデータとの合成

**Objective:** As a Feedman Android ユーザー, I want ページを再読込しても直前に行った既読／スター操作が消えないこと, so that スクロール中に古い状態へ巻き戻される違和感を受けない

#### Acceptance Criteria

1. When 一覧画面がページングデータと overlay を組み合わせて表示状態を生成するとき, the 一覧画面 shall overlay に値がある item についてサーバー由来の値より overlay 値を優先する
2. When 新しいサーバーページが読み込まれたとき, the 一覧画面 shall 既存 overlay 値を維持したまま新ページの item を合成する
3. When overlay にない item をサーバーが返したとき, the 一覧画面 shall サーバー由来の状態をそのまま表示する
4. The 一覧画面 shall overlay 値とサーバー由来値が同一の item について、表示上の差異を生じさせない

### Requirement 4: 画面間リアクティブ同期

**Objective:** As a Feedman Android ユーザー, I want 複数画面に表示されている同じ記事の状態が連動すること, so that どの画面から操作しても全体の表示が一貫する

#### Acceptance Criteria

1. When ある画面で item のスターまたは既読状態がトグルされたとき, the 他の購読中画面 shall 追加のサーバー再取得を待たずに同じ item の表示状態を更新する
2. When 横断タイムラインと記事詳細シートが同時に表示されているとき, the 両画面 shall 同じ item に対する状態変更を共通の状態ストリームから受け取る
3. While 記事詳細シートが開かれているとき, the 記事詳細シート shall 表示中 item の既読／スター状態を ItemStateStore の overlay から購読する
4. While 横断タイムラインが表示されているとき, the 横断タイムライン shall 各 item の既読／スター状態を ItemStateStore の overlay から購読する

### Requirement 5: 既読化トリガー

**Objective:** As a Feedman Android ユーザー, I want 記事を読み始めた瞬間に既読が立つこと, so that 後で未読を見直す際にすでに開いた記事が再表示されない

#### Acceptance Criteria

1. When ユーザーが記事詳細シートを開いたとき, the ItemStateStore shall 当該 item の既読 overlay を「既読」に更新する
2. When ユーザーが記事の外部リンクを開いたとき, the ItemStateStore shall 当該 item の既読 overlay を「既読」に更新する
3. When 既に「既読」状態の item に対して既読化トリガーが発火したとき, the ItemStateStore shall 重複したサーバー側状態更新リクエストを発行しない

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーが既読またはスターをトグルしたとき, the 操作を発火した画面 shall 100 ミリ秒以内に新しい表示状態へ更新する
2. The ItemStateStore shall サーバー側状態更新リクエストの結果を待たずに overlay 値を購読側へ配信する

### NFR 2: 検証可能性

1. The ItemStateStore shall 「楽観値適用 → サーバー失敗 → 旧値復元」のシーケンスを、テストから観測できる粒度で公開する
2. The ItemStateStore shall 同一 item に対する複数の購読について、片方への更新が他方へも反映される経路を、テストから観測できる粒度で公開する

### NFR 3: スコープ整合

1. The 本機能 shall 変更影響範囲を ItemStateStore 本体と、横断タイムライン画面・記事詳細シート画面の購読接続変更に限定する
2. The 本機能 shall スター一覧画面および横断検索画面への overlay 購読接続を含まない

## Out of Scope

- スター一覧画面への overlay 購読接続（#46 で扱う）
- 横断検索画面への overlay 購読接続（#48 で扱う）
- フィード別記事一覧画面の overlay 購読接続（本 Issue の直接スコープ外。別 Issue で順次接続する想定）
- 一括既読・サーバー側起動同期 API（SPEC §7、次フェーズ）
- 既読／スター以外の item 属性（はてブ数・公開日時など）の楽観的更新
- ロールバック時のエラーメッセージ文言・UI 表現の最終確定（共通エラー表示規約に従う）
- オフライン時のキュー再送（リクエスト失敗時は即時ロールバック＋エラー表示のみ）

## Open Questions

- なし（既存ドキュメント `design/SPEC.md` §6 および `docs/GRAND-DESIGN.md` §5.4 で楽観的更新方針が確定済み。エラー文言の最終仕様は別の共通エラー UI Issue で決まる前提で、本要件では「エラーメッセージを提示する」までを規定する）
