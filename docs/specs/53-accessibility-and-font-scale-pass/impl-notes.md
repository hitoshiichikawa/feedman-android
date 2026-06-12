# Implementation Notes — Issue #53 アクセシビリティ・フォントスケール対応

本ノートは「監査 + 是正パス」の Issue #53 実装サマリです。プロトタイプ準拠の見た目を
維持したまま、TalkBack 読み上げの明瞭化、トグル状態のアナウンス、フォントスケール 200%
時のレイアウト維持、最小 44dp タップ標的の確保に焦点を絞って差分を入れています。

## 監査結果マトリクス

| 監査対象 | 観点 | 現状 | 結論 |
|---|---|---|---|
| AppShell トップバー Menu / Search / ThemeToggle | contentDescription / Role | 全て strings.xml 経由で付与済み。Material3 IconButton が 48dp 標準 | OK（変更なし） |
| DrawerHeader アカウント領域 | contentDescription / 48dp 最小高さ | `drawer_action_account` 付与済み、`defaultMinSize(minHeight=48.dp)` 適用済み | OK |
| DrawerFeedsSection + ボタン | タップ標的 | `size(40.dp)` で固定 → 44dp 未満 | **是正**: `sizeIn(minTapTarget)` に変更 |
| DrawerFeedRowItem 全体 | 行のまとまった a11y 読み上げ | 個別アイコンに個別 contentDescription → TalkBack が冗長に列挙 | **是正**: 行情報セクションを `clearAndSetSemantics` で 1 文化（DrawerFeedRowA11y） |
| DrawerFeedRowItem 設定 IconButton | タップ標的 | `size(28.dp)` 固定 → 44dp 未満 | **是正**: `sizeIn(minTapTarget)` に変更 |
| StarToggle | toggle role / on-off 状態 | contentDescription のみ。state なし | **是正**: `Role.Switch` + `ToggleableState` + `stateDescription` を付与 |
| ArticleCard 外部リンク IconButton | Role / contentDescription / タップ標的 | `article_card_open_link_description` + `sizeIn(minTapTarget)` 付与済み | OK |
| ArticleCard Favicon | 装飾扱い | ArticleCard 呼び出し側で `contentDescription` 未指定 = a11y ノードに ContentDescription を追加しない | OK（仕様通り） |
| FeedmanSheet | paneTitle / 閉じる label | `paneTitle` 既に semantics で設定。ドラッグハンドル description あり | OK |
| ArticleDetailSheet 閉じる / 元記事 / 戻る等 | contentDescription / タップ標的 | 各 IconButton で `sizeIn(minTapTarget)` 付与済み、`heightIn(min=46.dp)` | OK |
| SubscriptionSettingsSheet 閉じる / 再開 / 保存 / 解除 / セグメント | contentDescription / Role / タップ標的 | セグメントは `Role.RadioButton` + `selected`、いずれも `heightIn(min = minTapTarget)` | OK |
| RegisterFeedSheet 閉じる / 登録 | contentDescription / タップ標的 | `sizeIn(minTapTarget)`、Submit Button は `heightIn(min = minTapTarget)` | OK |
| AccountSheet 閉じる / ログアウト / 退会 / 再試行 | contentDescription / liveRegion | progress に LiveRegionMode.Polite、`heightIn(min = minTapTarget)` | OK |
| SearchScreen 検索バー外側 Row | フォントスケール時の縦切り詰め | `height(48.dp)` 固定 → 200% で TextField 縦切り詰めリスク | **是正**: `heightIn(min = 48.dp)` に変更 |
| SearchScreen クリア IconButton | タップ標的 | `sizeIn(minWidth/minHeight = 32.dp)` 固定 → 44dp 未満 | **是正**: `sizeIn(minTapTarget)` に変更 |
| FeedScreen 設定 IconButton | タップ標的 | `size(44.dp)` 適用済み | OK |
| LoginScreen Google ログインボタン | フォントスケール時の縦切り詰め | `height(52.dp)` 固定 → 200% で文字縦切り詰めリスク | **是正**: `heightIn(min = 52.dp)` に変更 |
| ArticleCard タイトル / 概要 maxLines | 仕様 | `maxLines = 3` / `maxLines = 2` ellipsis | OK（SPEC §5.1 維持） |
| DrawerFeedRow タイトル `maxLines = 1` | 仕様 | 1 行省略 | OK（NFR 1.2 維持） |

## Requirement ID → 対応マッピング

| Requirement ID | 是正内容 / 既存対応 | 検証手段 |
|---|---|---|
| Req 1.1（ドロワー開閉トリガラベル） | 既存 `appbar_open_drawer` で対応済み。背景タップ/スワイプは Material3 標準 | コード走査（既存 strings.xml） |
| Req 1.2（ドロワーフィード行のまとまった読み上げ） | `DrawerFeedRowA11y.resolve` で string resource ID を解決し、`clearAndSetSemantics` で `<feedTitle>、<status>、未読 N 件` の 1 文化 | `DrawerFeedRowA11yTest`（6 通り + 境界値） |
| Req 1.3（スタートグルの Role / on/off 状態） | `StarToggle` に `Role.Switch` + `ToggleableState` + `stateDescription` を付与 | コード差分（Compose UI テスト相当は CI 必須外） |
| Req 1.4（外部リンクアイコンの Role / ラベル） | 既存 `OpenLinkIconButton`（IconButton で Role.Button 自動付与） + `article_card_open_link_description` で対応済み | 既存 ArticleCardTest 等 |
| Req 1.5（シート pane title） | 既存 `FeedmanSheet.paneTitle` で全シート対応済み | `FeedmanSheetLabel` テスト |
| Req 1.6（シート閉じる label） | 既存各シートの `*_close_description` で対応済み | コード走査 |
| Req 1.7（装飾アイコンの a11y 除外） | ArticleCard 内 Favicon は `contentDescription = null` で a11y ノードを追加せず | コード差分 |
| Req 2.1（スター状態変化のアナウンス） | `stateDescription` を `付与済み` / `未付与` で切り替え | `DrawerFeedRowA11yTest` の周辺方針として StarToggle に反映 |
| Req 2.2 / 2.3（on/off 状態の公開） | `ToggleableState.On` / `ToggleableState.Off` + `stateDescription` | コード差分（StarToggle.kt） |
| Req 2.4（ArticleDetailSheet 内スター） | 同じ `StarToggle` を再利用しており本是正の効果が自動的に適用される | コード差分 |
| Req 3.1（カードのテキスト重なり防止） | 既存 `Column` + `Arrangement.spacedBy` + `wrapContent` 高さ。固定高さなし | コード差分 |
| Req 3.2（ドロワー行の重なり防止） | 行はオリジナルから `Modifier.size` 固定なし。IconButton のみ修正で対応 | コード差分 |
| Req 3.3（トップバー）| Material3 標準のスケーラブル TopAppBar | OK（変更なし） |
| Req 3.4（固定高さ → 弾性高さ） | `SearchScreen.kt` の検索バー Row / `LoginScreen.kt` の Google ログインボタンを `height` → `heightIn(min)` に変更 | コード差分 |
| Req 3.5（シート可視領域内 / 縦スクロール） | 既存 `ArticleDetailSheet` の `verticalScroll` 採用 / FooterActionBar の `heightIn` 採用 | コード走査 |
| Req 4.1（最小タップ標的 44dp） | DrawerFeedRowItem 設定 IconButton / DrawerFeedsSection + ボタン / SearchScreen クリア IconButton を `sizeIn(minTapTarget)` 化 | コード差分 |
| Req 4.2（フォントスケール下でも 44dp 維持） | `sizeIn` は wrap 方向に min を保証するため文字スケールでも崩れない | コード差分 |
| Req 4.3（小視覚アイコンを 44dp で囲う） | 設定アイコン視覚 18dp / クリアアイコン 18dp の周囲を 44dp ヒット領域で確保 | コード差分 |
| Req 4.4（隣接要素のタップ判定独立性） | 設定 IconButton は行情報セクション（clearAndSetSemantics）の兄弟 Row として配置することで a11y / click とも独立 | コード差分 |
| NFR 1.1〜1.3（監査対象限定 / 観測可能 / 200% 検証） | 監査マトリクス（上記表）+ pure ロジックの JVM テストで観測可能。プレビューでフォントスケール上書きする運用は Compose UI テスト領分（次期 Issue で検討） | impl-notes に記録 |
| NFR 2.1 / 2.2（見た目・既存挙動の保持） | 視覚サイズ（iconSmall = 18dp 等）と既存 onClick の意味は不変 | コード差分 |

## 是正で発生した実装上の判断

- **a11y のまとまり化方針**: 行全体に `mergeDescendants` を付ける選択肢もあるが、子の状態
  アイコン description「停止中」「エラー」と未読バッジ description「未読 N 件」が文として
  同居すると読み上げが冗長になりやすい。状態と件数の組み合わせから 1 文を構成する純粋
  ロジック（`DrawerFeedRowA11y`）を用意し、`clearAndSetSemantics` で行情報セクションを 1
  ノード化する方針を採用した。設定 IconButton は当該セクションの兄弟として配置し、
  独立した a11y / click 領域を維持する。
- **タップ標的拡大の方式**: 既存実装は `Modifier.size(28.dp)` 等の固定値で IconButton 全体を
  小さくしていたが、`sizeIn(minWidth = minTapTarget, minHeight = minTapTarget)` に変更する
  ことで「視覚サイズは小さいまま、ヒット領域だけ最小 44dp」を実現できる。表示アイコン
  サイズは `Icon` 側の `Modifier.size(...)` で別途指定されているため挙動に変化はない。
- **固定高さ → 弾性高さ**: `height(...)` を `heightIn(min = ...)` に変えるのみ。フォント
  スケール 100% の通常時は同じ高さで描画され、200% 等の拡大時のみコンテンツが伸長する。
  見た目の劣化（プロトタイプとの差）は通常スケールでは発生しない。
- **StarToggle の Role 選択**: Material3 IconButton はデフォルトで Role.Button を付与する
  が、トグル動作のため `Role.Switch` で上書きした。`Modifier.semantics` で role を上書き
  しても IconButton の onClick 自体は機能する。

## テスト戦略

- **JVM 単体テスト追加**: `DrawerFeedRowA11yTest`（6 通り + 境界値 1 件 = 7 ケース）。
  状態 × 未読件数の全分岐を網羅。
- **既存テストへの影響**: なし。`DrawerFeedRow` のデータクラス変更はなく、`Favicon` /
  `StarToggle` / `ArticleCard` の API も変更していないため既存テストはそのまま通過する。
- **Compose UI テスト**: 本リポジトリの CI は JVM 単体テストを必須とし、UI テストは別途
  運用方針（CLAUDE.md「テスト規約」）。本 Issue で追加した semantics（toggleableState /
  Role.Switch / contentDescription）は `composeTestRule.onNode(...)` で観測可能だが、
  CI 必須外のため本 Issue では追加していない。次期 Issue で UI テスト基盤を整える際に
  AC ベースのテストを追加する候補。

## ビルド結果

- `./gradlew build`: SUCCESS（unit tests + lint 通過）

## 確認事項

レビューワーへの留意点として以下を残します（spec 書き換えはしていません）。

1. **DrawerHeader のアカウント領域**: 既に `clickable` 自体に `contentDescription = "アカウント"`
   を付与しているが、行の `Text` が「`you@example.com` の placeholder」表示で
   ArticleCard と同様に email を併読することが望ましい可能性。本 Issue では既存挙動
   （ラベル単独）を維持。
2. **ArticleCard 内 ソース行（favicon + フィード名）**: 当面 a11y ノードはまとめていない
   （Favicon は contentDescription なし装飾扱い、フィード名 Text は単独で読み上げ可能）。
   タイトル・概要・メタ行と合わせて TalkBack が順次読み上げる形で機能上の課題は
   なさそうだが、UI テストでより読み上げ感を確認するのは次期 Issue の領分。
3. **Compose UI テスト（Req NFR 1.2 / 1.3）**: 監査対象画面の UI テストでセマンティクス
   ノードを実機相当に observed する手段は `composeTestRule.onAllNodes(...)` で可能だが、
   CI 必須外として本 Issue では追加していない。次期 Issue で `app/src/androidTest/` 配下に
   per-画面の Compose テスト（特にスタートグルの状態 / クリックでの状態変化アナウンス /
   フィード行のまとまった読み上げ）を追加する候補。
4. **フォントスケール 200% プレビュー / テスト構成**: Compose Preview は `fontScale`
   パラメータで上書き可能（`@Preview(fontScale = 2.0f)`）だが、本 Issue では追加プレビュー
   は導入していない。今後の運用方針（プレビュー追加 vs UI テスト導入）を決める対象。

STATUS: complete
