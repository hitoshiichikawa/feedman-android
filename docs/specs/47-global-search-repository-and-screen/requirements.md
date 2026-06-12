# Requirements Document

## Introduction

Feedman Android の横断検索（`design/SPEC.md` §5.3、§4.2）は、購読中の全フィードを横断してキーワードで記事を探すための機能である。サーバーの `GET /api/items/search?q=<kw>&scope=global` は `ItemSearchHit` を返し、これは `ItemSummary` と差分があり、`hatebu_fetched_at` を含まず、`feed_title` と `favicon_url`（null 可）を含み、`published_at` も null 可となる。本 Issue ではトップアプリバーまたはドロワーから到達する検索画面と、`core/data` 配下の対応リポジトリ（cursor paging）を実装する。

検索画面の視覚・挙動の基準はプロトタイプ `design/mobile/fm-screens.jsx` の `FMSearchScreen` に準ずるが、データ取得はモックではなくサーバー API を用い、空クエリ時にはサジェストチップと空状態を表示してネットワーク呼び出しを行わない。クエリ送信後は cursor paging により結果カードを段階的に追加表示する。結果カードは `ItemSearchHit` の型差分に合わせて `published_at` および `favicon_url` の null を安全に扱う。

フィード内検索（`scope=feed`）の UI と検索結果からの記事詳細遷移（#48）は本 Issue のスコープ外である。

## Requirements

### Requirement 1: 検索画面への到達と入力 UI

**Objective:** As a Feedman Android アプリ利用者, I want トップアプリバーの検索エントリから検索画面に遷移してキーワード入力を即座に始められること, so that 思いついたキーワードで横断検索をすぐ開始できる

#### Acceptance Criteria

1. When ユーザーがトップアプリバーの検索エントリを選択したとき, the Global Search Screen shall 当該画面をメイン領域に表示する
2. When 検索画面が表示されたとき, the Global Search Screen shall キーワード入力欄にフォーカスを当ててソフトウェアキーボードが立ち上がる状態にする
3. When 検索画面が表示されたとき, the Global Search Screen shall 入力欄の左に検索アイコンを表示し、横断検索であることが利用者に伝わるプレースホルダ文を提示する
4. When 入力欄に 1 文字以上の値が入っているとき, the Global Search Screen shall 入力値を一括消去するクリア操作要素を入力欄内に提示する
5. When ユーザーがクリア操作要素を選択したとき, the Global Search Screen shall 入力欄の値を空に戻し、空クエリ時の表示（Requirement 2）に切り替える
6. When ユーザーが画面ヘッダーの戻る操作を行ったとき, the Global Search Screen shall 直前の画面へ遷移する

### Requirement 2: 空クエリ時のサジェストと空状態表示

**Objective:** As a Feedman Android アプリ利用者, I want キーワードを入力していない状態で検索候補が提示されること, so that 何を検索すればよいか迷ったときに着手しやすい

#### Acceptance Criteria

1. While 入力欄のキーワードが空（空白のみを含む）の状態, the Global Search Screen shall サーバーへの検索リクエストを発行しない
2. While 入力欄のキーワードが空の状態, the Global Search Screen shall プロトタイプ `FMSearchScreen` に準じたサジェストチップ群を表示する
3. When ユーザーがサジェストチップの 1 つを選択したとき, the Global Search Screen shall 当該チップの文字列を入力欄に投入する
4. When サジェストチップ選択により入力欄にキーワードが投入されたとき, the Global Search Screen shall 当該キーワードでの検索を Requirement 3 の送信フローと同じ規則で開始する

### Requirement 3: 検索の送信と結果取得

**Objective:** As a Feedman Android アプリ利用者, I want 入力欄から検索を送信すると `scope=global` で横断検索が実行されること, so that 購読中の全フィードからキーワードに合致する記事を取り出せる

#### Acceptance Criteria

1. When ユーザーが入力欄から検索を送信したとき, the Global Search Screen shall 前後の空白を取り除いたキーワードを検索対象として確定する
2. If 確定後のキーワードが空のとき, the Global Search Screen shall サーバーへの検索リクエストを発行せず、空クエリ時の表示（Requirement 2）を維持する
3. When 確定後のキーワードが 1 文字以上のとき, the Global Search Repository shall `GET /api/items/search` を `scope=global` および当該キーワードを指定してカーソル未指定で呼び出し、先頭ページを取得する
4. When 検索送信時にソフトウェアキーボードが表示されているとき, the Global Search Screen shall キーボードを閉じて結果領域がスクロール可能な状態にする
5. When 検索送信に対する先頭ページ取得が進行中のとき, the Global Search Screen shall 取得中であることが利用者に伝わる進捗表示を提示する

### Requirement 4: 検索結果の表示とカード仕様

**Objective:** As a Feedman Android アプリ利用者, I want 検索結果が記事カードとして表示され、ソース・公開日時・はてブ数・スター状態が一目で確認できること, so that 検索結果から目的の記事を素早く判別できる

#### Acceptance Criteria

1. When 検索結果が 1 件以上で読み込み完了したとき, the Global Search Screen shall 各ヒットを記事カードとして縦スクロールリストに提示する
2. When 各検索結果カードが描画されるとき, the Global Search Screen shall 当該ヒットの `feed_title` をソース表示として提示する
3. When 各検索結果カードが描画されるとき, the Global Search Screen shall 当該ヒットの `favicon_url` を data URL として取り込みアイコン表示する
4. If ヒットの `favicon_url` が null のとき, the Global Search Screen shall favicon の代替表示としてレターアバターを提示する
5. When 各検索結果カードが描画されるとき, the Global Search Screen shall 当該ヒットの `published_at` を相対日時表現として提示する
6. If ヒットの `published_at` が null のとき, the Global Search Screen shall 日時表示位置に日時が不明である旨を示す代替表現を提示し、カード自体は他の項目を含めて描画する
7. When 各検索結果カードが描画されるとき, the Global Search Screen shall 当該ヒットの `hatebu_count` を `ItemSearchHit` 取得時点での値として提示する
8. When 各検索結果カードが描画されるとき, the Global Search Screen shall 当該ヒットのスター状態をカード内に視認できる形で提示する
9. While 結果リスト全体が描画されているとき, the Global Search Screen shall 結果総件数または取得済み件数が利用者に伝わる件数表示を結果領域に提示する

### Requirement 5: cursor paging と結果の追加読み込み

**Objective:** As a Feedman Android アプリ利用者, I want 検索結果をスクロールしながら追加読み込みできること, so that ヒット件数が多くても末尾まで辿れる

#### Acceptance Criteria

1. When 直前ページのレスポンスに次ページを示すカーソルが含まれているとき, the Global Search Repository shall 当該カーソルを次ページ要求にそのまま搬送する
2. When サーバーレスポンスが次ページの存在しない旨を示したとき, the Global Search Repository shall 後続ページが存在しない旨をページング状態に反映する
3. While 終端に到達している状態, the Global Search Repository shall 追加の次ページ要求を発行しない
4. When ユーザーが入力欄のキーワードを変更して新規の検索を送信したとき, the Global Search Repository shall それまでのページ蓄積を破棄し、新しいキーワードで先頭ページから取得を開始する
5. While 1 つの検索クエリに対する cursor paging が継続している間, the Global Search Repository shall 当該検索クエリの開始時に確定したキーワードを後続ページ要求にも保持する

### Requirement 6: 空結果とエラー表示

**Objective:** As a Feedman Android アプリ利用者, I want ヒット 0 件のときと取得失敗時に状況が明確に伝わること, so that 入力を見直すべきか再試行すべきかを判断できる

#### Acceptance Criteria

1. When 検索送信に対して先頭ページが 0 件で完了したとき, the Global Search Screen shall 当該キーワードに一致する記事が無い旨と再入力を促す文言を含む空状態表示を提示する
2. If 先頭ページ取得が失敗したとき, the Global Search Repository shall 当該エラーをページング状態のエラーとして露出し UI 層から再試行できるようにする
3. If 先頭ページ取得が失敗したとき, the Global Search Screen shall エラーが発生した旨をユーザーに提示する
4. If 追加ページ取得が失敗したとき, the Global Search Repository shall それまで読み込み済みの結果を破棄せず、追加ロード分のエラーをページング状態のエラーとして露出する
5. If 追加ページ取得が失敗したとき, the Global Search Screen shall 既に表示済みの結果カードを保持したまま追加ロード失敗を提示する

### Requirement 7: スコープ境界

**Objective:** As a Feedman Android プロジェクト関係者, I want 本 Issue の変更影響範囲と扱わない事項が明示されていること, so that 他 Issue との二重実装やスコープ膨張を避けられる

#### Acceptance Criteria

1. The Global Search feature shall ソース変更を `feature/search` 配下の検索画面追加および `core/data` 配下の検索リポジトリ追加に限定する
2. The Global Search feature shall フィード内検索（`scope=feed`）に向けた UI を含まない
3. The Global Search feature shall 検索結果カードから記事詳細シートを開く導線を含まない
4. The Global Search feature shall 検索履歴の永続化機能を含まない
5. The Global Search feature shall キーワード通知（プッシュ通知のキーワード登録）に関する機能を含まない

## Non-Functional Requirements

### NFR 1: 応答性

1. When ユーザーが入力欄のキーワードを変更したとき, the Global Search Screen shall 100 ミリ秒以内に入力欄の表示値を更新する
2. While 入力欄のキーワードが空の状態, the Global Search Screen shall サーバー往復を発生させずにサジェストチップ表示を維持する
3. While 追加ページの読み込み中, the Global Search Screen shall 既に表示済みの結果カードのスクロール操作を阻害しない

### NFR 2: テスト網羅性

1. The Global Search Repository test suite shall Requirement 5 の正常系・終端到達・キーワード変更による再起動・初回失敗・追加ロード失敗を最低 1 ケースずつ網羅する
2. The Global Search Repository test suite shall `ItemSearchHit` の `published_at` が null のヒット、`favicon_url` が null のヒット、双方が非 null のヒットそれぞれを呼び出し元へ正しく伝達することを最低 1 ケースずつ検証する
3. The Global Search Screen test suite shall Requirement 2 の空クエリ時にネットワーク呼び出しが発生しないことと、サジェストチップ選択から検索開始までの導線を最低 1 ケースずつ検証する
4. The Global Search Screen test suite shall Requirement 6 の空結果表示・先頭ページ失敗時のエラー表示・追加ページ失敗時の既存結果保持を最低 1 ケースずつ検証する

## Out of Scope

- フィード内検索 UI（`scope=feed`）。v1 対象外であり、当面導入しない
- 検索結果カードから記事詳細シートを開く導線（#48 で扱う）
- 検索履歴の永続化およびその UI 表示
- キーワード通知（プッシュ通知のキーワード登録）連携
- サジェストチップの内容を利用者ごとに動的生成する仕組み（本 Issue ではプロトタイプ準拠の静的候補を用いる）
- 検索結果のオフラインキャッシュ・全文ローカル保存
- 検索結果に対するソート・フィルタ切替 UI

## Open Questions

- なし（`ItemSearchHit` の型差分・`scope=global` の既定挙動・空クエリ時にネットワーク呼び出しを行わない方針はいずれも `design/SPEC.md` §4.2 / §5.3 とプロトタイプ `FMSearchScreen` の挙動に基づいて確定した）

## 関連

- Parent: #10
- Depends on: #18 #31 #27
