# Requirements Document

## Introduction

Feedman Android アプリは v1 のスコープ機能が概ね揃いつつあるが、TalkBack（Android 標準
スクリーンリーダー）対応とフォントスケール耐性が個別画面ごとにバラついている。本 Issue は
v1 リリース前のアクセシビリティ「監査 + 是正パス」として、主要操作（ドロワー開閉・フィード
選択・スター・外部リンク・各種シート操作）に意味のあるセマンティクスを付与し、フォントスケール
200% でもレイアウト破綻なくタップ標的最小 44dp を維持できる状態を担保する。見た目（プロトタイプ
準拠）は変えず、UI セマンティクスと寸法ポリシーを足すことを優先する。
TalkBack 実機での読み上げ品質はコード上で完全には検証できないため、本要件は「セマンティクスが
付与されている」「固定高さによる切り詰めが発生しない」など、コード／UI テスト上で観測
可能な基準として記述する。

## Requirements

### Requirement 1: 主要操作の意味あるセマンティクス付与

**Objective:** As a TalkBack 利用者, I want アプリの主要操作（ドロワー・フィード選択・スター・外部リンク・シート）に意味のある読み上げを得られること, so that 視覚に頼らず Feedman の中核機能を操作できる

#### Acceptance Criteria

1. The App Shell shall すべてのドロワー開閉トリガ（トップアプリバーのハンバーガーアイコン、ドロワー内の close 相当の操作）に対し、現在の開閉状態を示すアクセシビリティ ラベル（例: 「ナビゲーションを開く」「ナビゲーションを閉じる」）を付与する
2. The Drawer Content shall ドロワー内のフィード行に対し、フィード名・未読件数・フィード状態（active / stopped / error）が 1 まとまりとして読み上げ可能なアクセシビリティ ラベルを付与する
3. The Article Card shall 一覧カード内のスタートグル要素に対し、トグル可能な操作であることを示すアクセシビリティ ロール（toggle / switch 相当）と、現在の on / off 状態を公開する
4. The Article Card shall 一覧カード内の外部リンクアイコンに対し、ボタンであることを示すアクセシビリティ ロールと「元記事を外部ブラウザで開く」旨のラベルを付与する
5. The Bottom Sheet (Article Detail / Register Feed / Subscription Settings / Account) shall シート open 時にシートのタイトル（例: 「記事詳細」「フィード登録」）を見出しとしてアクセシビリティに公開する
6. The Bottom Sheet shall シート内の閉じる操作（背景タップ・close ボタン）に対し「シートを閉じる」旨のアクセシビリティ ラベルを付与する
7. If 装飾用途のアイコン（favicon・区切りアイコン等、操作対象でないもの）が存在するとき, the Article Card shall それらをアクセシビリティ ツリーから除外する（読み上げ対象としない）

### Requirement 2: トグル状態の状態変化アナウンス

**Objective:** As a TalkBack 利用者, I want スター等のトグル操作後に新しい状態が読み上げられること, so that 操作が成功したかを聴覚で確認できる

#### Acceptance Criteria

1. When ユーザーがスターをトグルしたとき, the Article Card shall スター要素のアクセシビリティ on / off 状態を新しい値に即時更新し、スクリーンリーダーが新状態をアナウンスできる形にする
2. While スターが付いている状態であるとき, the Article Card shall スター要素のアクセシビリティ状態を on として公開する
3. While スターが付いていない状態であるとき, the Article Card shall スター要素のアクセシビリティ状態を off として公開する
4. When 記事詳細シートでスターをトグルしたとき, the Article Detail Sheet shall 同シート内のスター要素のアクセシビリティ on / off 状態を新しい値に更新する

### Requirement 3: フォントスケール 200% でのレイアウト維持

**Objective:** As 大きいフォントスケールを設定したユーザー, I want 文字サイズを 200% に拡大してもレイアウトが破綻せずに主要操作が継続できること, so that 視認性を優先しても本アプリを継続利用できる

#### Acceptance Criteria

1. While 端末のフォントスケールが 200% に設定されているとき, the Article Card shall タイトル・概要・メタ行のテキストが重なり合わずに表示される
2. While 端末のフォントスケールが 200% に設定されているとき, the Drawer Content shall フィード行内のフィード名・未読バッジ・状態アイコンが重なり合わずに表示される
3. While 端末のフォントスケールが 200% に設定されているとき, the App Shell shall トップアプリバーのタイトル・サブタイトルが切り詰められた場合でも、ハンバーガー / 検索 / テーマ切替の各タップ標的が画面内に保持される
4. If テキストを含む UI 要素が固定高さの領域に配置されているとき, the App Shell, Drawer Content, Article Card, Bottom Sheet shall 該当要素の高さをフォントサイズに応じて伸長する形にし、フォントスケール拡大時にテキストが切り詰められないようにする
5. While 端末のフォントスケールが 200% に設定されているとき, the Bottom Sheet shall シート内の主要ボタン（「元記事を開く」など）がシート可視領域内で完全に表示される、または縦スクロールでアクセス可能な状態にする

### Requirement 4: タップ標的サイズの最小確保

**Objective:** As 細かい操作が難しいユーザー, I want すべての操作要素が最小 44dp のタップ標的を持つこと, so that 誤タップなく主要操作を実行できる

#### Acceptance Criteria

1. The App Shell, Drawer Content, Article Card, Bottom Sheet shall 操作対象のアイコン・ボタン・トグルすべてに対し、最小タップ標的サイズを 44dp × 44dp 以上に設定する
2. While 端末のフォントスケールが 200% に設定されているとき, the App Shell, Drawer Content, Article Card, Bottom Sheet shall 操作対象要素のタップ標的サイズを 44dp × 44dp 未満に縮小しない
3. Where 視覚要素自体が 44dp 未満であるとき（例: 18–22dp のアイコン）, the App Shell, Drawer Content, Article Card, Bottom Sheet shall 周囲のヒットスロット（余白またはダミー領域）で 44dp × 44dp 以上のタップ標的を確保する
4. If 隣接する操作要素間の距離が 44dp 未満になるとき, the Article Card, Drawer Content shall それぞれの操作要素を独立にタップ可能な状態で維持する（タップ判定の重複・誤動作を起こさない）

## Non-Functional Requirements

### NFR 1: 監査範囲とコード上の検証可能性

1. The Accessibility Pass shall 監査対象の画面群を「ドロワー（App Shell / Drawer Content）」「タイムライン・フィード別・スター・検索 各一覧の Article Card」「記事詳細 / フィード登録 / 購読設定 / アカウント の各 Bottom Sheet」に限定する
2. The Accessibility Pass shall 上記対象に付与したアクセシビリティ ラベル・ロール・on/off 状態を、UI テストまたはプレビュー上のセマンティクス ノード探索で観測可能な形として実装する
3. The Accessibility Pass shall フォントスケール 200% の挙動について、プレビューまたは UI テストでフォントスケールを上書きできる構成を用意し、対象画面のレイアウト破綻が無いことを検証可能にする

### NFR 2: 既存挙動・見た目への影響

1. The Accessibility Pass shall プロトタイプ準拠の見た目（配色・アクセントカラー Indigo・カード形状・余白）を維持し、レイアウトの視覚的変更（色・形状・既定フォントサイズ・余白規約）を伴わない
2. The Accessibility Pass shall 既存の楽観的更新・既読化トリガ・Pull-to-refresh 等の挙動を変更しない

## Out of Scope

- フルのアクセシビリティ監査（WCAG 2.1 AA / AAA の網羅的準拠評価）
- TalkBack 以外の支援技術（Switch Access、Voice Access、外部スイッチデバイス等）への個別対応
- フォントスケール 200% を超えるスケール値（例: 端末設定の「最大」相当）の保証
- 高コントラストモード / カラーフィルタ / カラーブラインドネス向け配色変更
- アクセシビリティ専用設定画面の追加
- TalkBack 実機での読み上げ品質検証（人手・instrumented test の領分。本要件はコード上観測可能な基準に留める）
- v1 スコープ外機能（キーワードプッシュ通知設定画面・OPML・オフライン全文キャッシュ・フィード内検索 UI）への対応

## Open Questions

- なし（Issue 本文・SPEC §8・GRAND-DESIGN §3 の範囲で要件は確定可能）
