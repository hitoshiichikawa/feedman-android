# Requirements Document

## Introduction

Feedman Android アプリの記事詳細体験を担う「部分ボトムシート（detail=partial）」の要件を定義する。
横断タイムラインやフィード別記事一覧、スター一覧、検索結果から記事をタップした際に、画面遷移ではなく
ボトムシートで本文プレビューを開く採用案（SPEC §5.4）に対応するユーザー可視挙動を確定させる。
シートを開いた時点で既読化を行い（楽観的更新）、本文は約 200dp 高さでフェードした上で「続きを読む」
で展開、フッタには「元記事を開く」主アクションとスタートグルを固定で配置する。外部リンク起動の実体
（Custom Tabs 連携）と、シート外のリストを含む画面横断の状態同期は別 Issue（#37 / #38）に切り出し、
本 Issue ではシート単体の表示・操作・シート内整合に閉じる。

## Requirements

### Requirement 1: シートの起動とソース情報表示

**Objective:** As a 記事閲覧者, I want 記事をタップしたら下から部分シートが開いてソース・タイトル・本文の概観をひと目で把握したい, so that 画面遷移なしで読むかどうかを判断できる

#### Acceptance Criteria

1. When ユーザーが記事リストの任意の項目をタップしたとき, the Article Detail Sheet shall 当該記事の詳細を部分ボトムシート形式で表示する
2. When 部分シートが表示されたとき, the Article Detail Sheet shall ソース行としてフィード favicon・フィード名・公開日時を上部に配置する
3. While 記事の `feed_favicon_url` が `null` であるとき, the Article Detail Sheet shall favicon の代わりに色付きレターアバター（フィード名 1 文字目）を表示する
4. When ユーザーがシート上部のドラッグハンドル下方向スワイプ・スクリム領域タップ・閉じるボタンタップのいずれかを行ったとき, the Article Detail Sheet shall シートを閉じて呼び出し元のリストに戻る
5. When ユーザーが Android のシステム戻る操作（戻るボタン/ジェスチャー）を行ったとき, the Article Detail Sheet shall 開いているシートを閉じる

### Requirement 2: 本文プレビューと「続きを読む」展開

**Objective:** As a 記事閲覧者, I want 本文の冒頭をシート内で読み、必要なら続きをその場で展開したい, so that 全画面に切り替えずに記事の要旨を確認できる

#### Acceptance Criteria

1. When 部分シートが初期表示されたとき, the Article Detail Sheet shall 本文を約 200dp 高さの範囲に収め、下端に向かって背景色へフェードアウトするグラデーションを重ねる
2. While 本文が折りたたまれた状態であるとき, the Article Detail Sheet shall プレビュー直下に「続きを読む」展開ボタンを表示する
3. When ユーザーが「続きを読む」ボタンをタップしたとき, the Article Detail Sheet shall 本文の高さ制限とフェードを解除して全文をシート内スクロールで閲覧可能にする
4. While 本文が展開された状態であるとき, the Article Detail Sheet shall 同位置のボタンを「折りたたむ」に切り替え、再タップで初期プレビュー状態に戻す
5. The Article Detail Sheet shall 本文中の見出し・段落・リンク・強調・引用・コード・リストが視認できる形で表示する
6. If 本文文字列が空であるとき, the Article Detail Sheet shall 「本文のプレビューはありません」の空状態メッセージを表示し、「続きを読む」ボタンを非表示にする

### Requirement 3: シートを開いた時点での既読化（楽観的更新）

**Objective:** As a 記事閲覧者, I want シートを開いた瞬間にその記事が既読になってリストの見た目にも反映されること, so that 既読／未読の管理を意識せずに記事消化を進められる

#### Acceptance Criteria

1. When 部分シートが表示されたとき, the Article Detail Sheet shall 当該記事の既読状態を即座に「既読」へ変更する
2. When シートを開いたことによる既読化が行われたとき, the Article Detail Sheet shall サーバーに既読状態の更新リクエストを送出する
3. If 既読化のサーバー更新が失敗したとき, the Article Detail Sheet shall 当該記事の既読状態を更新前の値へロールバックし、失敗を示すトースト等の通知を表示する
4. While 既読化の楽観的更新が反映されているとき, the Article Detail Sheet shall シートが参照する記事の表示（既読目印）を「既読」として描画する
5. If 当該記事が既にシート表示前から既読であるとき, the Article Detail Sheet shall 状態更新リクエストを再送せず冪等な挙動とする

### Requirement 4: フッタアクション（元記事を開く・スター）

**Objective:** As a 記事閲覧者, I want シート下部から「元記事を開く」と「スター切替」をすぐ実行したい, so that 読むか保存するかの判断をその場で完了できる

#### Acceptance Criteria

1. The Article Detail Sheet shall 「元記事を開く」主ボタンとスタートグルボタンを下端に固定表示し、本文スクロールの影響を受けない
2. When ユーザーが「元記事を開く」ボタンをタップしたとき, the Article Detail Sheet shall 当該記事の外部リンクを開く要求を発火する
3. When 「元記事を開く」要求が発火されたとき, the Article Detail Sheet shall 当該記事が未読であれば既読化を行う（既読の場合は再送しない）
4. When ユーザーがフッタのスターボタンをタップしたとき, the Article Detail Sheet shall 当該記事のスター状態をトグルし、シート内表示（本文上部のスター含む）に即時反映する
5. If スター更新のサーバー反映が失敗したとき, the Article Detail Sheet shall スター状態を更新前の値へロールバックし、失敗を示す通知を表示する
6. When スター状態が変化したとき, the Article Detail Sheet shall フッタのスターアイコンと本文上部のスターアイコンを同じ状態（ON/OFF）として描画する

### Requirement 5: メタ情報の表示（はてブ・スター）

**Objective:** As a 記事閲覧者, I want 本文の上にはてブ数とスターを並べて確認したい, so that 注目度や自分の保存状態を読み始める前に把握できる

#### Acceptance Criteria

1. When 部分シートが表示されたとき, the Article Detail Sheet shall タイトル直下に「はてなブックマーク数」と「スター状態」を横並びで表示する
2. While `hatebu_count` が 0 もしくは未取得（`hatebu_fetched_at` が null）であるとき, the Article Detail Sheet shall はてブ数の数値表示を省略するか「0」と判別可能な見た目で描画する
3. The Article Detail Sheet shall 本文上部のスター表示とフッタのスター表示が常に同じ ON/OFF 状態となるように整合させる

### Requirement 6: シート内ローディング・エラー表示

**Objective:** As a 記事閲覧者, I want 詳細情報の取得中や失敗時に状態を理解できること, so that シートが固まったように見えても安心して操作できる

#### Acceptance Criteria

1. While 記事詳細の本文取得中であるとき, the Article Detail Sheet shall 本文領域にローディング状態（プログレス表示など）を表示する
2. If 記事詳細取得が失敗したとき, the Article Detail Sheet shall エラーメッセージと再試行手段を本文領域に表示する
3. When ユーザーがエラー状態から再試行操作を行ったとき, the Article Detail Sheet shall 詳細取得を再実行する
4. While 本文取得が完了していないとき, the Article Detail Sheet shall フッタの「元記事を開く」ボタンを当該記事の外部リンク情報が利用可能になり次第有効化する

## Non-Functional Requirements

### NFR 1: 応答性・タップ操作性

1. When ユーザーが記事をタップしてから 100ms 以内, the Article Detail Sheet shall シートのスライドアップアニメーションを開始する
2. The Article Detail Sheet shall 「元記事を開く」「スター」「閉じる」「続きを読む/折りたたむ」のタップ標的サイズを最小 44dp × 44dp 以上で提供する
3. While 本文展開／折りたたみのアニメーション中であるとき, the Article Detail Sheet shall フッタの主ボタンとスターを操作可能なまま維持する

### NFR 2: 視覚・テーマ整合

1. The Article Detail Sheet shall ライト／ダークいずれのテーマでもタイトル・本文・メタ情報・フッタの文字／背景コントラストが判読できる配色で描画する
2. The Article Detail Sheet shall プレビュー下端のフェードグラデーションをシート背景色に整合させる（テーマ切替時もグラデーションが破綻しない）

### NFR 3: アクセシビリティ

1. The Article Detail Sheet shall シート全体に「記事の詳細」を示すアクセシビリティラベルを付与する
2. The Article Detail Sheet shall フッタの主ボタン・スターボタン・閉じるボタン・「続きを読む」ボタンにそれぞれ役割が判別できるアクセシビリティ説明を付与する

## Out of Scope

- 「元記事を開く」タップ時に外部ブラウザ（Chrome Custom Tabs / `ACTION_VIEW`）を実際に起動する経路の実装（#37 で扱う）
- シートで発生した既読化・スター変更を、シートを呼び出した一覧画面や他画面のリストへリアルタイム反映するための画面横断同期機構（#38 で扱う）
  - 本 Issue ではシート内表示の整合（本文上部のスターとフッタのスター、シート内既読目印）までを対象とする
- 全画面シート（detail=full）・リーダー表示（detail=reader）への切り替え（SPEC §5.4 で不採用）
- 記事詳細からの共有・コピー・後で読む等の追加アクション
- WebView による本文表示を含む高度な HTML レンダリング（プレビュー用途を超える要件）
- 既読／スター API のリクエスト再試行戦略・オフラインキューイング
- 横断タイムライン／フィード別一覧／検索／スター一覧側の「シートから戻ったときに該当行の既読・スターが反映されるか」の保証（#38 で扱う）

## Open Questions

- 既読化・スター更新の失敗通知は SPEC §6 で「楽観的更新 → 失敗時ロールバック」とのみ定義されている。失敗時のユーザー通知手段（トースト／インライン表示／無通知のいずれか）について明文化された前例がないため、本要件では「失敗を示す通知を表示する」と記述している。表示手段の最終決定（特にトースト文言の標準化）は design フェーズまたは Issue コメントで確認する。
- 記事詳細取得（`GET /api/items/{id}`）について、横断タイムライン等の一覧で既に保持している `summary` 等の情報をプレビューに流用するか、シート起動時に必ず詳細を再取得するかは観測可能挙動として既存ドキュメントに明記がない。design 領分とみなすが、ローディング表示の有無に影響するため設計時に確認する。
- 本文が空（`content` が空文字列）になるケースの発生条件は SPEC に明記がなく、保険的に Requirement 2.6 を置いている。実データで該当ケースが存在しなければ design 時に削除を検討する。
