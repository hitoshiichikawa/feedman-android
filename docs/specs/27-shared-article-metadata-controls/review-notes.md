# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-27-impl-shared-article-metadata
- HEAD commit: 8f6aefd
- Compared to: origin/main..HEAD
- 変更ファイル: `app/src/main/kotlin/com/feedman/android/core/ui/{RelativeTimeFormatter,HatebuLogic,StarToggle,HatebuBadge,ArticleCard}.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/kotlin/com/feedman/android/core/ui/{RelativeTimeFormatterTest,HatebuLogicTest}.kt`, `docs/specs/27-.../{requirements.md,impl-notes.md}`

## Verified Requirements

- 1.1 — `StarToggle.kt:76` で `isStarred=true` のとき `Icons.Filled.Star`（filled） + `feedmanColors.star` tint を適用
- 1.2 — `StarToggle.kt:76` で `isStarred=false` のとき `Icons.Outlined.StarBorder`（outline） + `mutedFg` tint を適用
- 1.3 — `StarToggle.kt:66` `onClick = { onToggle(!isStarred) }`（false → true で 1 回呼ぶ）
- 1.4 — 同上（true → false で 1 回呼ぶ）
- 1.5 — `isStarred=true` のとき `R.string.article_meta_star_remove`「スターを解除」を contentDescription に設定（`StarToggle.kt:61-63` + `strings.xml:14`）
- 1.6 — `isStarred=false` のとき `R.string.article_meta_star_add`「スターを付ける」（`StarToggle.kt:61-63` + `strings.xml:16`）
- 1.7 — `IconButton` が独立した click 領域として消費するため親の `clickable` には伝播しない（Compose 標準挙動。`StarToggle.kt:65` / `ArticleCard.kt:106,152-155`）
- 1.8 — `Modifier.sizeIn(minWidth = minTapTarget, minHeight = minTapTarget)` で 44dp を確保（`StarToggle.kt:68`、`Dimens.kt:28` で `minTapTarget = 44.dp`）
- 2.1 — `HatebuLogic.compute` が non-null `fetched_at` で `Display.Numeric` を返す（`HatebuLogic.kt:56-59`） + `HatebuLogicTest` "Req 2_1 fetched_at non-null returns numeric display"
- 2.2 — null で `Display.Unavailable` → `HatebuBadge` が `UNAVAILABLE_LABEL = "−"`（U+2212）を描画（`HatebuLogic.kt:25`, `HatebuBadge.kt:63-69`） + テスト 2 件
- 2.3 — `count >= 100` のとき `isHot=true` → アクセント色 `MaterialTheme.colorScheme.primary` + `FontWeight.SemiBold` + "users" サフィックス（`HatebuBadge.kt:45-87`） + テスト「Req 2_3 count exactly 100 is hot」「count well above threshold」
- 2.4 — `count < 100` で `isHot=false` → mutedFg + 通常字幅、"users" は付加しない（`HatebuBadge.kt:71-87`） + テスト 2 件
- 2.5 — `Icons.Filled.RssFeed` を数値の左に必ず描画（`HatebuBadge.kt:55-61`）
- 3.1 — `h < 1` で "1時間以内"（`RelativeTimeFormatter.kt:76`、`fmFormatDate` と等価）+ テスト 0 分 / 59 分 59.999 秒で検証
- 3.2 — `h < 24` で "${h}時間前"（`RelativeTimeFormatter.kt:77`） + テスト 60 分（h=1） / 23 時間 59 分（h=23）で検証
- 3.3 — `d < 7` で "${d}日前"（`RelativeTimeFormatter.kt:78-79`） + テスト 24h ちょうど（d=1） / 6日23h（d=6）で検証
- 3.4 — `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.JAPAN)` で ja-JP 日付（`RelativeTimeFormatter.kt:90-94`） + テストで year/month/day 含有を確認
- 3.5 — `isDateEstimated=true` で `" (推定)"` を末尾に付加（`RelativeTimeFormatter.kt:62`） + テスト「within-hour」「day」両方に付加されることを検証
- 3.6 — `isDateEstimated=false` で付加しない（同 line + テスト「does not append suffix」）
- 3.7 — `Clock` 注入必須シグネチャで `Instant.now()`/`System.currentTimeMillis()` 未使用 + テスト「NFR 1_1 different fixed clocks yield different relative labels」で 2 clock 同一入力での結果差を検証
- 4.1 — `cardAlpha = if (model.isRead) feedman.readForegroundAlpha else 1.0f`（`ArticleCard.kt:93`）、`READ_FOREGROUND_ALPHA = 0.55f`（`FeedmanTheme.kt:48`）→ ルート `Column` に `Modifier.alpha(cardAlpha)` 適用（`ArticleCard.kt:103`）
- 4.2 — 同上 else 分岐で 1.0f を適用
- 4.3 — `Modifier.alpha` をルート `Column` に適用するため全子要素（タイトル / メタ Row / StarToggle / HatebuBadge / Favicon）に等しく伝播
- 4.4 — `Modifier.clickable { onOpen(model.id) }` は alpha 適用後も有効。`StarToggle` も独立 click 領域として動作（`ArticleCard.kt:106,152-155`）
- 5.1〜5.4 — `ArticleCardModel` を API モデル非依存の中立 data class として定義（`ArticleCard.kt:46-57`）。4 系統カードから同じ Composable で描画可能
- 5.5 — `HatebuLogic.compute(_, null)` → `Display.Unavailable`（`HatebuLogic.kt:57`） + テスト「Req 5_5 search hit without fetched_at falls back to unavailable」
- NFR 1.1 — Clock 引数を必須化し System clock を参照しない実装 + 「異なる Clock で異なる結果」テスト
- NFR 1.2 — 0 分 / 59 分 / 60 分 / 23時間59分 / 24時間 / 6日23時間 / 7日ちょうど の境界すべてに対するテスト存在
- NFR 2.1 — filled/outline で異なる文字列リソースを contentDescription に設定
- NFR 2.2 — `Modifier.sizeIn(44dp)` で確保
- NFR 3.1 — 配色（star / mutedFg / primary）・フォント階層（11sp / 12sp / 15sp）・余白（6dp/12dp）をプロトに合わせて再現

## Findings

なし

## Summary

`./gradlew :app:testDebugUnitTest` で `RelativeTimeFormatterTest` / `HatebuLogicTest` すべて green を確認。`RelativeTimeFormatter` は `fmFormatDate` と境界（`h < 1` / `h < 24` / `d < 7`）が一致し、固定 Clock 注入で NFR 1.1 / 1.2 を満たす。境界変更も core/ui / strings.xml / app/src/test に閉じており boundary 逸脱なし。Composable 描画検証（icon 実体・alpha 適用状態）は instrumented test 領分のため JVM 単体テスト不在を missing test 扱いしない方針に従い、純粋ロジック（RelativeTimeFormatter / HatebuLogic）のテスト網羅で十分とみなす。

RESULT: approve
