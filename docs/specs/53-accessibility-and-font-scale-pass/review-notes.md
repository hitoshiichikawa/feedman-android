# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-53-impl-accessibility-pass
- HEAD commit: 38f2e73
- Compared to: origin/main..HEAD
- 変更ファイル: `app/src/main/kotlin/com/feedman/android/core/ui/StarToggle.kt` / `feature/login/LoginScreen.kt` / `feature/search/SearchScreen.kt` / `shell/DrawerContent.kt` / `shell/DrawerFeedRow.kt` / `res/values/strings.xml` / `app/src/test/.../DrawerFeedRowA11yTest.kt` + spec docs
- `./gradlew test`: BUILD SUCCESSFUL（UP-TO-DATE 確認）

## Verified Requirements

- 1.1 — 既存 `appbar_open_drawer` を維持。Material3 標準のドロワー開閉トリガラベル付与済み（impl-notes 監査マトリクス OK 行で根拠提示）
- 1.2 — `DrawerContent.kt` 行情報セクション Row に `clearAndSetSemantics { contentDescription = rowDescription }` を適用。`DrawerFeedRowA11y.resolve(statusIcon, unreadCount)` + 6 種の `drawer_feed_row_description_*` 文字列で「タイトル、状態、未読 N 件」の 1 文化を実現
- 1.3 — `StarToggle.kt` に `semantics { role = Role.Switch; toggleableState = ...; stateDescription = ... }` を追加。IconButton 既定の Role.Button を Switch で上書きしトグルとして読み上げ可能
- 1.4 — 既存 `OpenLinkIconButton` + `article_card_open_link_description` で対応済み（impl-notes 監査マトリクス OK 行で確認）
- 1.5 — 既存 `FeedmanSheet.paneTitle` で全シートに pane タイトル付与済み（既存実装の差分なし）
- 1.6 — 既存各シートの `*_close_description` で対応済み（既存実装の差分なし）
- 1.7 — ArticleCard 内 Favicon は `contentDescription = null` 装飾扱い（既存実装の差分なし）
- 2.1 — `StarToggle` の `toggleableState` が `isStarred` 値で再計算され、`stateDescription` も `付与済み` / `未付与` で切り替わるため、トグル直後にスクリーンリーダーが新状態をアナウンス可能
- 2.2 — `isStarred=true` → `ToggleableState.On` + `stateDescription="付与済み"`
- 2.3 — `isStarred=false` → `ToggleableState.Off` + `stateDescription="未付与"`
- 2.4 — ArticleDetailSheet 内のスターも同じ `StarToggle` を再利用しており本変更の効果が自動波及
- 3.1 — ArticleCard は既存 wrap 高さ + `Arrangement.spacedBy` で固定高さなし（impl-notes 監査マトリクス OK 行で確認）
- 3.2 — DrawerFeedRow も固定高さなし。`Row` 内要素は `weight(1f)` + wrap で、状態アイコン / 未読バッジは縮まずに wrap（既存実装維持）
- 3.3 — TopAppBar は Material3 標準のスケーラブル実装（既存実装維持）
- 3.4 — `SearchScreen.kt` 検索バー Row を `height(48.dp)` → `heightIn(min = 48.dp)`、`LoginScreen.kt` Google ログインボタンを `height(52.dp)` → `heightIn(min = 52.dp)` に変更。フォントスケール 200% で内側 TextField / ラベルが縦方向に切り詰められない
- 3.5 — 既存 `ArticleDetailSheet` の `verticalScroll` + FooterActionBar の `heightIn` で対応済み（既存実装維持）
- 4.1 — DrawerFeedsSection の + IconButton: `size(40.dp)` → `sizeIn(minWidth/minHeight = minTapTarget)`。DrawerFeedRowItem の設定 IconButton: `size(28.dp)` → `sizeIn(minTapTarget)`。SearchScreen クリア IconButton: `sizeIn(minWidth=32.dp, minHeight=32.dp)` → `sizeIn(minTapTarget)`
- 4.2 — `sizeIn(minWidth/minHeight)` は wrap 方向に min を保証する形で、文字スケール拡大時もタップ標的が縮まない
- 4.3 — 視覚アイコンサイズ（`iconSmall = 18dp` 等）は `Icon` 側 modifier で維持しつつ、IconButton 側で 44dp ヒット領域を確保
- 4.4 — DrawerFeedRowItem の行情報 Row（`clearAndSetSemantics`）と設定 IconButton を別 Row 兄弟として配置。`clearAndSetSemantics` のスコープは内側 Row に限定され、設定 IconButton は独立した a11y / click ノードとして残る
- NFR 1.1 — 監査対象が App Shell / Drawer / Article Card / 4 Bottom Sheet に限定されている（impl-notes 監査マトリクスで明示）
- NFR 1.2 — 付与した contentDescription / Role / toggleableState は `composeTestRule.onNode(...)` で観測可能な形（CI 必須外として UI テスト追加は次期 Issue 候補と明記）
- NFR 1.3 — 弾性高さ化（`heightIn(min)`）でフォントスケール 200% 時のレイアウト破綻を防止可能な構成。プレビュー追加は今後の運用判断と明記
- NFR 2.1 — 視覚サイズ・配色・カード形状・余白は変更なし。`height(...)` → `heightIn(min = ...)` は通常スケール時に同一サイズで描画される
- NFR 2.2 — 楽観的更新・既読化トリガ・Pull-to-refresh などの挙動は変更なし。`onToggle` / `onSelectFeed` / `onSelectFeedSettings` のシグネチャ・意味は維持

## Findings

なし

## Summary

監査マトリクスが網羅的で、各 Requirement ID と是正内容 / 既存対応の根拠が impl-notes に明示されている。StarToggle の `Role.Switch` + `ToggleableState` + `stateDescription` 付与、ドロワー行の `clearAndSetSemantics` による 1 文化、44dp タップ標的の `sizeIn` 統一、固定高さの弾性化（`heightIn(min)`）はいずれも要件を直接的に満たす。boundary は core/ui / feature/* / shell / strings.xml / app/src/test 内で完結し、見た目（プロト準拠）への影響なし。JVM 単体テスト `DrawerFeedRowA11yTest`（6 通り + 境界 1 件）が新規追加された純粋ロジックを網羅。UI テスト不在は CLAUDE.md テスト規約上 instrumented 領分のため missing test 扱いとしない。

RESULT: approve
