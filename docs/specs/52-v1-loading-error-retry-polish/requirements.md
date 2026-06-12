# Requirements Document

## Introduction

v1 では Issue #28 で共通状態表示プリミティブ（`core/ui/StateViews.kt`）が整備され、その後の各画面実装 Issue（#34 横断タイムライン / #38 フィード別 / #42 スター / #43 検索 / #45 記事詳細シート / #46 購読設定 / #48 アカウント）で個別に状態表示が組み込まれた。本 Issue は v1 全体の通し確認として、SPEC §10 の受け入れチェックリストに照らして「全一覧画面とシート群で loading / empty / error / retry の挙動が統一されているか」を観測可能な統一基準として確定させ、不備があれば是正することを目的とする。

本要件は監査と是正の性格であり、現時点で具体的な不備リストを推測で確定させない。各要件は「全一覧画面（横断タイムライン / フィード別 / スター / 検索）および記事詳細・購読設定・フィード登録・アカウントの各ボトムシートで、観測可能な統一挙動が成立していること」を AC として記述する。実装フェーズで個別箇所を調査し、統一基準を満たさない箇所を是正する。

API 契約・採用案・新規機能の追加は本 Issue のスコープ外とし、挙動変更はポリッシュに留める。

## Requirements

### Requirement 1: 全一覧画面の初回ローディング統一

**Objective:** As a v1 アプリ利用者, I want すべての一覧画面で初回ローディング時に同一の視覚表現と挙動を体験すること, so that 画面ごとに挙動が違うことで戸惑わずに済む

#### Acceptance Criteria

1. While 横断タイムライン / フィード別記事一覧 / スター一覧 / 検索結果のいずれかが初回ロード未完了の状態, the Feedman Android App shall コンテンツ領域全体を占める単一のローディング表示（`core/ui` の `LoadingFullScreen` 相当）のみを描画する
2. While 上記いずれかの一覧で追加ページ取得が進行中, the Feedman Android App shall リスト末尾に追加ローディングフッター（`core/ui` の `LoadingFooter` 相当）を描画する
3. The Feedman Android App shall 初回ローディングと追加ローディングを同時に描画しない（初回ローディング中は追加フッターを表示しない）
4. The Feedman Android App shall 初回ローディング中、空状態・エラー・終端フッターを同時に描画しない

### Requirement 2: 全一覧画面のエラー表示と再試行統一

**Objective:** As a v1 アプリ利用者, I want エラー発生時に必ず再試行手段が用意され、復帰不能な dead-end に陥らないこと, so that ネットワーク不調などの一過性エラーから自力で回復できる

#### Acceptance Criteria

1. If 横断タイムライン / フィード別 / スター / 検索のいずれかで初回ロードが失敗した, the Feedman Android App shall エラーメッセージと再試行ボタンを含む全画面エラー表示（`core/ui` の `ErrorFullScreen` 相当）を描画する
2. When 全画面エラー表示の再試行ボタンがタップされた, the Feedman Android App shall 当該一覧の初回ロードを再実行する
3. If 上記いずれかの一覧で追加ページ取得が失敗した, the Feedman Android App shall リスト末尾に追加エラーフッター（`core/ui` の `ErrorFooter` 相当）を描画し、再試行ボタンを露出する
4. When 追加エラーフッターの再試行ボタンがタップされた, the Feedman Android App shall 当該一覧の追加ページ取得のみを再実行する（既存読込済みアイテムを破棄しない）
5. The Feedman Android App shall エラー表示時に再試行ボタンを必ず含め、再試行手段のない dead-end 状態を描画しない
6. Where API レスポンスがエラーフォーマット（SPEC §4.3）の `message` を含む, the Feedman Android App shall その `message` をエラー表示の本文として用いる
7. If エラーフォーマットに `message` が含まれない（タイムアウト等の通信失敗を含む）, the Feedman Android App shall 既定の汎用エラーメッセージを表示する

### Requirement 3: 全一覧画面の空状態統一

**Objective:** As a v1 アプリ利用者, I want データが 0 件のときに各画面で同一の空状態表現を体験すること, so that 「読み込み中なのか空なのか」を誤認しない

#### Acceptance Criteria

1. While 横断タイムライン / フィード別 / スター / 検索のいずれかで初回ロードが成功し、かつ表示対象アイテムが 0 件, the Feedman Android App shall 共通空状態（`core/ui` の `EmptyState` 相当 / `design/mobile/fm-ui.jsx` の `FMEmpty` 視覚仕様）を描画する
2. The Feedman Android App shall 各一覧画面の空状態に、その画面の文脈に合致する主題テキストを表示する
3. The Feedman Android App shall 空状態の描画と同時にローディング・エラー・終端フッターを描画しない
4. While 検索画面でクエリ未入力の初期状態, the Feedman Android App shall 入力前案内（既存のサジェスト UI またはクエリ入力促進）を描画し、エラー扱いの表示は描画しない

### Requirement 4: 全一覧画面の終端表示統一

**Objective:** As a v1 アプリ利用者, I want 無限スクロールが終端に達したことを明示的に知ること, so that スクロールが進まない理由を判別できる

#### Acceptance Criteria

1. When 横断タイムライン / フィード別 / スター / 検索のいずれかで `has_more === false` または `next_cursor` が null/空 で追加読込が確定終端に達した, the Feedman Android App shall 単一の終端フッター文言（`core/ui` の `EndOfListFooter` 相当）をリスト末尾に描画する
2. The Feedman Android App shall 終端フッターを追加ローディングフッター・追加エラーフッターと同時に描画しない
3. The Feedman Android App shall 終端文言を全一覧画面で同一の文字列リソースから供給する（`R.string.state_end_of_list` 単一定義に統一）

### Requirement 5: ボトムシートのローディング・エラー統一

**Objective:** As a v1 アプリ利用者, I want 記事詳細・購読設定・フィード登録・アカウントの各シートでもローディング・エラー・再試行の挙動が一貫していること, so that シートを開いて何も起きない・閉じる以外の選択肢がない、といった dead-end に陥らない

#### Acceptance Criteria

1. While 記事詳細シート / 購読設定シート / フィード登録シート / アカウントシートが初回データ取得中, the Feedman Android App shall シート内に進行中であることを示すローディング表示を描画する
2. If 上記いずれかのシートで初回データ取得が失敗した, the Feedman Android App shall シート内にエラーメッセージと再試行手段を描画し、シートを閉じる以外の選択肢を必ず残す
3. When ボトムシートのエラー表示で再試行手段がタップされた, the Feedman Android App shall 当該シートの初回データ取得を再実行する
4. If ボトムシート上で送信系操作（PUT / POST / DELETE）が失敗した, the Feedman Android App shall ユーザーに失敗を可視的に通知し、シートを自動で閉じない（再操作の余地を残す）

### Requirement 6: フィード別 Pull-to-refresh のクールダウン挙動統一

**Objective:** As a v1 アプリ利用者, I want フィード別画面の手動フェッチがクールダウン中であることが分かり、いつ再試行できるかを把握できること, so that 闇雲に引っ張り続けずに済む

#### Acceptance Criteria

1. When フィード別記事一覧で Pull-to-refresh が `POST /api/subscriptions/{id}/fetch` を呼び出し、レスポンスが `FEED_COOLDOWN` を返した, the Feedman Android App shall `details.retry_after_seconds` の値をユーザーに案内する文言を表示する
2. If `FEED_COOLDOWN` 以外のエラーで Pull-to-refresh が失敗した, the Feedman Android App shall 失敗をユーザーに通知し、既読アイテムなど既存表示を破棄しない
3. The Feedman Android App shall Pull-to-refresh の失敗通知後、ユーザーが再度 Pull-to-refresh を試行できる状態に戻す

### Requirement 7: 統一の維持を妨げる API 契約・採用案の不変

**Objective:** As a v1 開発運用者, I want 本 Issue の是正が SPEC で確定済みの API 契約・採用案・スコープを変更しないこと, so that 後続フェーズの想定が崩れない

#### Acceptance Criteria

1. The Feedman Android App shall SPEC §4 で定義された API 契約（エンドポイント・型・ページネーション・エラーフォーマット）を本 Issue 内で変更しない
2. The Feedman Android App shall SPEC §5 で固定された各画面の採用案（ナビ構造・カード形式・シート形式）を本 Issue 内で変更しない
3. The Feedman Android App shall v1 スコープ外の機能（キーワードプッシュ通知 / 一括既読 / オフライン全文キャッシュ / OPML / フィード内検索 UI / アクセシビリティ強化）を本 Issue 内で導入しない

## Non-Functional Requirements

### NFR 1: 視覚一貫性

1. The Feedman Android App shall ローディング・空状態・エラー・終端の各表示を `core/ui` の共通プリミティブ（`LoadingFullScreen` / `LoadingFooter` / `EmptyState` / `ErrorFullScreen` / `ErrorFooter` / `EndOfListFooter`）に集約し、画面ごとの独自実装を残さない
2. The Feedman Android App shall 再試行ボタンの最小タップ標的を 44dp 以上（SPEC §8 準拠）で描画する
3. The Feedman Android App shall ライト／ダーク両テーマで上記状態表示の視認性を維持する（既存デザイントークンの `mutedFg` 等を用いる）

### NFR 2: 状態排他性の検証可能性

1. The Feedman Android App shall 一覧フッターの状態（追加ローディング / 追加エラー / 終端 / なし）の排他判定ロジックを JVM 単体テストで検証できる純粋関数として保持する（既存 `resolveListFooterState` 相当の継続）
2. The Feedman Android App shall 各画面の初回ローディング・エラー・空状態・終端の同時描画禁止を、ViewModel が公開する `UiState` のみから一意に判定できるよう保つ

### NFR 3: 互換性

1. The Feedman Android App shall 既存のテスト（依存 Issue #34 / #38 / #42 / #43 / #45 / #46 / #48 の単体テスト）を本 Issue の変更で壊さない
2. The Feedman Android App shall 本 Issue の変更で SPEC §4 で規定された API リクエスト形・レスポンス解釈を変更しない

## Out of Scope

- 新機能の追加（キーワードプッシュ通知、一括既読、起動同期、オフライン全文キャッシュ、OPML、フィード内検索 UI、フィード URL 変更 UI）
- アクセシビリティ強化（Issue #53 で別途扱う。本 Issue ではタップ標的サイズなど既存規約の維持に留める）
- API 契約の変更（SPEC §4）
- 各画面の採用案（ナビ構造・カード形式・シート形式）の変更（SPEC §5）
- デザイントークンの変更（SPEC §8）
- 認証フロー・トークン管理の挙動変更（SPEC §3）
- 計測・テレメトリ・クラッシュレポートの追加

## Open Questions

なし（不備の具体は実装フェーズで監査時に確定する性格の Issue であるため、要件段階での Open Questions は発生しない）

## 関連

- Parent: #12
- Depends on: #34 #38 #42 #43 #45 #46 #48
- Related: #28 #53
