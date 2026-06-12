# Requirements Document

## Introduction

#44 で導入したフィード登録ボトムシートは、登録成功時にシートを閉じて成功トーストを表示するところまでを担うが、ドロワーに表示される購読フィード一覧は登録前のスナップショットのままで、新しく登録したフィードが現れない。そのためユーザーは登録直後に「ドロワーを開き直す」「アプリを再起動する」などの追加操作なしには新フィードを選択して記事を読み始められない。
本 Issue では、#39 で導入された `SubscriptionRepository.refresh()`（`GET /api/subscriptions` の再取得とドロワー観測者への通知）を、登録成功イベントを契機として実行するワイヤリングを追加する。これにより登録成功 → ドロワーに新フィード行が出現 → 直後にその行を選択すると記事一覧が読み込まれる、という動線をシート操作だけで完結させる。失敗時はユーザー操作をブロックせず、次にドロワーを開いた際の通常の再取得経路で復帰させる。本要件は `design/SPEC.md` §5.5（フィード登録）および §4.2（購読一覧）の仕様、ならびに #39 / #44 の既存要件を前提とする。

## Requirements

### Requirement 1: 登録成功時の購読一覧再取得トリガ

**Objective:** As a Feedman 利用者, I want 登録に成功したら新しいフィードが追加操作なしにドロワーへ現れる, so that そのままドロワーから新フィードを開いて読み始められる

#### Acceptance Criteria

1. When サーバーがフィード登録成功応答を返したとき, the Feed Registration Flow shall Subscription Repository の `refresh()` を 1 回呼び出す
2. When `refresh()` 呼び出し後に Subscription Repository が成功状態の購読一覧を発行したとき, the Drawer Feed List shall 当該リストに含まれる新規登録フィードを追加の手動更新操作なしで次の再描画時に反映する
3. The Feed Registration Flow shall 登録成功トースト（#44 Requirement 4.2）の表示と購読一覧再取得を、いずれかの失敗・遅延が他方を抑制しないかたちで両方実行する
4. The Feed Registration Flow shall 登録成功応答受信から購読一覧再取得呼び出しまでを、ユーザーの追加操作を要さず実行する

### Requirement 2: 新フィード即時選択時の記事一覧読み込み

**Objective:** As a Feedman 利用者, I want 登録直後にドロワーで新フィードをタップしたら記事一覧が読み込まれる, so that 登録から閲覧開始までを 1 動線で完了できる

#### Acceptance Criteria

1. When 登録成功後にドロワーへ反映された新規購読行をユーザーがタップしたとき, the App Shell shall 当該フィードの記事一覧画面へ遷移する
2. When 新規購読行のタップによりフィード別記事一覧画面が表示されたとき, the Feed Item List shall 当該 `feed_id` に対応する記事一覧の読み込みを開始する
3. While 登録成功直後に購読一覧の再取得が進行中で、新規購読行がドロワーへ未反映の状態, the Drawer Feed List shall 既存の購読行と現在表示中の画面の操作性を維持する

### Requirement 3: 登録成功後の再取得失敗時の挙動

**Objective:** As a Feedman 利用者, I want 登録自体は成功しているのに購読一覧の再取得だけ失敗した場合でも操作が止まらない, so that 登録は完了しているという事実が損なわれず、後から復帰できる

#### Acceptance Criteria

1. If 登録成功後の購読一覧再取得が非 2xx 応答またはネットワーク失敗で完了したとき, the Feed Registration Flow shall 登録成功トースト（#44 Requirement 4.2）の表示を抑止せず、登録自体は成功した旨をユーザーに伝え続ける
2. If 登録成功後の購読一覧再取得が失敗したとき, the Feed Registration Flow shall ユーザーの他の操作（ドロワー開閉・既存購読行のタップ・トップバー操作）をブロックするモーダルダイアログや全画面エラーを表示しない
3. If 登録成功後の購読一覧再取得が失敗したとき, the App Shell shall ユーザーが視認できる形（ドロワー内のフィードセクションのエラー表示等、#39 Requirement 2 の経路）で再取得失敗を提示する
4. When ユーザーが再取得失敗後にドロワーを開き直しフィードセクションの再試行操作を行ったとき, the Subscription Repository shall #39 Requirement 2.4 の経路で `GET /api/subscriptions` の再取得を実行し、成功時に新規登録フィードを観測者へ流す
5. When ユーザーが再取得失敗後にドロワーを閉じて再度開いたとき, the Drawer Feed List shall 通常のドロワー表示経路を通じて Subscription Repository の最新の購読一覧状態を反映する

### Requirement 4: 登録失敗・キャンセル時の再取得抑止

**Objective:** As a Feedman 利用者, I want 登録が成功していないときには余計な再取得が走らない, so that 不要な通信や状態の揺らぎが発生しない

#### Acceptance Criteria

1. If サーバーが登録に対して 4xx / 5xx エラー応答を返したとき, the Feed Registration Flow shall Subscription Repository の `refresh()` を呼び出さない
2. If 登録応答の待機中にネットワーク失敗（ネットワーク到達不可・タイムアウト）が発生したとき, the Feed Registration Flow shall Subscription Repository の `refresh()` を呼び出さない
3. If ユーザーが登録応答の待機中にシートを閉じる操作を行ったとき, the Feed Registration Flow shall #44 Requirement 3.4 の挙動を維持し、Subscription Repository の `refresh()` を呼び出さない

## Non-Functional Requirements

### NFR 1: 応答性

1. The Feed Registration Flow shall 登録成功応答の受信から Subscription Repository の `refresh()` 呼び出し開始までを 200 ミリ秒以内に行う
2. The Feed Registration Flow shall 購読一覧再取得の完了を待たずに登録成功トースト（#44 Requirement 4.2）の表示とシートのクローズ（#44 Requirement 4.1）を進める

### NFR 2: 変更範囲

1. The 本 Issue の変更 shall `feature/registerfeed`（登録成功イベントから再取得呼び出しへの配線）および `core/data`（既存 `SubscriptionRepository.refresh()` の利用）に閉じ、`SubscriptionRepository` の公開インターフェース・`Subscription` 型・`feature/*` の他画面のソースを変更しない
2. The 本 Issue の変更 shall 登録シート（#44）の UI 構成・入力バリデーション・エラーメッセージング・トースト文言を変更しない

### NFR 3: テスト容易性

1. The Feed Registration Flow shall 登録成功イベント発生時に Subscription Repository の `refresh()` が 1 回呼び出されることを、Subscription Repository をテストダブルで差し替えた単体テストで検証できる
2. The Feed Registration Flow shall 登録エラー応答・通信失敗・シートクローズの各ケースで `refresh()` が呼び出されないことを、Subscription Repository をテストダブルで差し替えた単体テストで検証できる

## Out of Scope

- 登録シート自体の UI / バリデーション / メッセージング変更（#44 のスコープ）
- `SubscriptionRepository` の取得経路・エラー状態モデル・モックモード分岐の変更（#39 のスコープ）
- 新規登録フィードを「自動で開く」「ハイライトする」など、登録直後のフォーカス移動演出
- 登録成功時の購読一覧の差分マージ最適化（楽観的に新規フィード行を即時挿入する等）。本 Issue はサーバーからの再取得結果の反映のみを対象とする
- 一括登録 / OPML インポート時の再取得まとめ
- フィード登録専用レート制限（#44 Requirement 5.3 / 5.4）の長期記憶
- 横断タイムライン・フィード別記事一覧の自動再取得（登録した新フィードの最新記事のフェッチ）

## Open Questions

- なし

## 関連

- Parent: #9
- Depends on: #39 #44
