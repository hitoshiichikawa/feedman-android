# Impl Notes — Issue #27 Shared article metadata controls

## 概要

`core/ui` 配下に 4 系統カード（横断タイムライン / フィード別 / スター / 検索）共有のメタ
データ部品を実装した。プロト `design/mobile/fm-ui.jsx` の `FMStar` / `FMHatebu` /
`FMArticleCard` standard variant と `design/mobile/fm-data.jsx` の `fmFormatDate` を正本に、
Compose 上で同等の挙動・配色・余白・字幅を再現している。

スター状態 / 既読状態の API 呼び出しは本 Issue のスコープ外（Out of Scope 節）であり、
スタートグルは `onToggle` コールバックを公開するに留めた。

## ファイル構成

### 追加（実装）

- `app/src/main/kotlin/com/feedman/android/core/ui/RelativeTimeFormatter.kt`
  純粋ロジック。`Clock` 注入で System clock を参照せず、ISO-8601 文字列を相対 / 絶対表記に
  整形する。`is_date_estimated=true` のとき末尾に `" (推定)"` を付加。
- `app/src/main/kotlin/com/feedman/android/core/ui/HatebuLogic.kt`
  純粋ロジック。`hatebu_fetched_at == null` → `Display.Unavailable`、それ以外は
  `Display.Numeric(count, isHot = count >= 100)` を返す sealed class API。
- `app/src/main/kotlin/com/feedman/android/core/ui/StarToggle.kt`
  Composable。Material Icons Extended の `Icons.Filled.Star` / `Icons.Outlined.StarBorder`
  を `isStarred` で切替し、`IconButton` で 44dp タップ標的を確保。
- `app/src/main/kotlin/com/feedman/android/core/ui/HatebuBadge.kt`
  Composable。`HatebuLogic.compute` 結果を分岐して RSS アイコン + 数値 / 数値 + "users" /
  "−" を描画。
- `app/src/main/kotlin/com/feedman/android/core/ui/ArticleCard.kt`
  Composable + `ArticleCardModel` data class。共有カード骨格。`isRead=true` で
  `Modifier.alpha(feedmanColors.readForegroundAlpha)` をカード全体に一括適用する。

### 追加（テスト）

- `app/src/test/kotlin/com/feedman/android/core/ui/RelativeTimeFormatterTest.kt`
- `app/src/test/kotlin/com/feedman/android/core/ui/HatebuLogicTest.kt`

### 変更

- `app/src/main/res/values/strings.xml`
  - `article_meta_star_remove` / `article_meta_star_add`（スタートグル contentDescription）
  - `article_meta_hatebu_users_suffix`（hot 表示の "users" サフィックス）
  - `article_meta_hatebu_icon_description`（RSS アイコン contentDescription）

## requirement ID → テスト対応表

| Req | 検証手段 | 場所 |
|---|---|---|
| 1.1 / 1.2（filled/outline 切替） | StarToggle 実装で `Icons.Filled.Star` / `Icons.Outlined.StarBorder` を `isStarred` で分岐 | `StarToggle.kt` |
| 1.3 / 1.4（onToggle 呼び出し） | StarToggle が `onToggle(!isStarred)` を 1 回だけ呼ぶ実装 | `StarToggle.kt` |
| 1.5 / 1.6（アクセシビリティラベル） | `contentDescription = stringResource(R.string.article_meta_star_remove/add)` を `isStarred` で分岐 | `StarToggle.kt` + `strings.xml` |
| 1.7（タップ伝播抑止） | `IconButton` が独自の click 領域を消費し親カードの clickable へ propagate しない | `StarToggle.kt` + `ArticleCard.kt` の親 clickable |
| 1.8（44dp タップ標的） | `Modifier.sizeIn(minWidth/Height = feedmanDimens.minTapTarget)` で 44dp 確保 | `StarToggle.kt` |
| 2.1（hatebu_fetched_at non-null → 数値） | `Req 2_1 fetched_at non-null returns numeric display` | `HatebuLogicTest.kt` |
| 2.2（null → "−"） | `Req 2_2 fetched_at null returns unavailable dash` / `Req 2_2 unavailable label is the unicode minus sign` | `HatebuLogicTest.kt` |
| 2.3（>=100 → hot, users 付加） | `Req 2_3 count exactly 100 is hot` / `Req 2_3 count well above threshold is hot` + HatebuBadge で `display.isHot` 時に "users" Text を追加 | `HatebuLogicTest.kt` / `HatebuBadge.kt` |
| 2.4（<100 → muted, users なし） | `Req 2_4 count just below threshold is not hot` / `Req 2_4 count zero is not hot` + HatebuBadge で `isHot=false` 時 "users" を出さない | `HatebuLogicTest.kt` / `HatebuBadge.kt` |
| 2.5（左に RSS 風アイコン） | `HatebuBadge` 内で `Icons.Filled.RssFeed` を必ず描画 | `HatebuBadge.kt` |
| 3.1（h < 1 → "1時間以内"） | `Req 3_1 zero minute diff returns within-hour label` / `Req 3_1 fifty-nine minutes diff returns within-hour label` | `RelativeTimeFormatterTest.kt` |
| 3.2（1h ≤ diff < 24h → "N時間前"） | `Req 3_2 exactly one hour returns 1 hour ago` / `Req 3_2 twenty-three hours fifty-nine minutes returns 23 hour ago` | `RelativeTimeFormatterTest.kt` |
| 3.3（24h ≤ diff < 7d → "N日前"） | `Req 3_3 exactly twenty-four hours returns 1 day ago` / `Req 3_3 six days twenty-three hours returns 6 days ago` | `RelativeTimeFormatterTest.kt` |
| 3.4（≥ 7d → ja-JP 日付） | `Req 3_4 exactly seven days returns ja date string` | `RelativeTimeFormatterTest.kt` |
| 3.5（推定 → "(推定)" 付加） | `Req 3_5 estimated date appends suffix to within-hour label` / `Req 3_5 estimated date appends suffix to day label` | `RelativeTimeFormatterTest.kt` |
| 3.6（非推定 → 付加なし） | `Req 3_6 non-estimated date does not append suffix` | `RelativeTimeFormatterTest.kt` |
| 3.7（固定 Clock 優先） | `NFR 1_1 different fixed clocks yield different relative labels` で同一入力 + 異なる Clock が異なる結果を返すことを確認 | `RelativeTimeFormatterTest.kt` |
| 4.1（is_read=true → opacity 0.55） | ArticleCard で `Modifier.alpha(feedmanColors.readForegroundAlpha)` を適用、`READ_FOREGROUND_ALPHA = 0.55f` は #25 で導入済み | `ArticleCard.kt` |
| 4.2（is_read=false → opacity 1.0） | `cardAlpha = if (isRead) 0.55f else 1.0f` の分岐 | `ArticleCard.kt` |
| 4.3（全要素一括適用） | `Modifier.alpha` をルートの `Column` に適用するため子要素が同一の alpha で描画される | `ArticleCard.kt` |
| 4.4（既読でもタップ可能） | `Modifier.clickable` は alpha 適用後も有効 | `ArticleCard.kt` |
| 5.1〜5.4（4 系統カード横断使用） | `ArticleCardModel` を中立的な data class として定義し、特定の API モデルに依存しない | `ArticleCard.kt` |
| 5.5（fetched_at 欠落 → "−"） | `Req 5_5 search hit without fetched_at falls back to unavailable` | `HatebuLogicTest.kt` |
| NFR 1.1（System clock 不参照） | `Clock` 引数を必須化し `Instant.now()` / `System.currentTimeMillis()` を使わない実装 + `NFR 1_1 different fixed clocks ...` テスト | `RelativeTimeFormatter.kt` / `RelativeTimeFormatterTest.kt` |
| NFR 1.2（境界値テスト） | 0分 / 59分 / 60分 / 23時間59分 / 24時間 / 6日23時間 / 7日 を全網羅 | `RelativeTimeFormatterTest.kt` |
| NFR 2.1（TalkBack ラベル区別） | filled / outline で異なる contentDescription（"スターを解除" / "スターを付ける"） | `StarToggle.kt` |
| NFR 2.2（44dp タップ標的） | `Modifier.sizeIn(minWidth/Height = feedmanDimens.minTapTarget)` | `StarToggle.kt` |
| NFR 3.1（プロトと視覚整合） | プロトの `FMStar` / `FMHatebu` / `FMArticleCard` standard の配色・余白・字幅を踏襲（star/mutedFg/primary 色、12sp/11sp/10sp の階層、12dp ギャップ） | `StarToggle.kt` / `HatebuBadge.kt` / `ArticleCard.kt` |

## 実装上の判断

### 相対日時計算の浮動小数 vs 整数

fmFormatDate（JavaScript）の `Math.floor(diffMs / 3600000)` を Kotlin の `Long` 除算
（0 方向への truncation）に置き換えた。負値（未来時刻）では truncation と floor で挙動差
が出る可能性があるが、判定境界が `h < 1` `h < 24` `d < 7` でいずれも負値はすべて
`h < 1` 側に該当するため、`fmFormatDate` と差分等価な結果になる。テストでも未来時刻ケース
（`future published_at is treated as within-hour`）を追加して確認した。

### ja-JP 絶対日付フォーマット

要件 3.4 は「ja-JP ロケールの year / month / day を含む日付文字列」とだけ規定しており、
具体的なフォーマット（`2026年6月5日` / `2026/6/5` 等）は固定していない。プロトの
`toLocaleDateString('ja-JP', { year:'numeric', month:'short', day:'numeric' })` を Android で
直接再現できないため、`DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
.withLocale(Locale.JAPAN)` を採用した。JVM の ICU 実装に依存するため、テストでは厳密文字列
ではなく `year/month/day を含む`「相対表現に戻っていない」の 2 条件で検証している。

### HatebuLogic を sealed class で公開

`Display.Unavailable` を `Display.Numeric(count=0, isHot=false)` で代替する案もあったが、
"−" 表示と "0" 表示で UI 側で取り違える事故を防ぐため sealed class で物理的に分離した。
これにより HatebuBadge 側の `when` が exhaustive になり、将来の表示パターン追加時にも
コンパイラが網羅性を検証する。

### Composable レベルの描画テストは instrumented 領分

`StarToggle` / `HatebuBadge` / `ArticleCard` の描画テスト（Icons の実体や Modifier.alpha の
適用状態の検証）は Compose UI Test で行うのが本来だが、CLAUDE.md テスト規約「CI 前提:
通常の Issue 実装で要求されるのは JVM 単体テスト」に従い JVM 単体テストの範疇では
RelativeTimeFormatter / HatebuLogic の純粋ロジックの境界網羅にとどめた。プレビュー
（`@Preview`）コンポーザブルは既存規約に合わせ、視覚確認専用とし本 PR では追加していない
（必要なら別 Issue で追加）。

### feedmanColors.readForegroundAlpha の再利用

Issue #25 で `FeedmanExtendedColors.readForegroundAlpha = 0.55f` がすでにテーマトークン
として定義されているため、本 Issue では新規定数を追加せずそのまま参照した。SPEC §5.1 と
プロトの `dim = item.is_read ? 0.55 : 1` に整合する。

## ビルド・テスト結果

- `./gradlew build` 成功（lint + unit test + release build 含む）
- 追加テスト: `RelativeTimeFormatterTest`（13 ケース）/ `HatebuLogicTest`（9 ケース）
  すべて green

## 確認事項（PR 本文用）

- ja-JP の絶対日付フォーマットを `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`
  にした。プロトの `toLocaleDateString` と Android ICU のスタイル差は許容範囲か
  （年月日が含まれていれば OK か）。
- RSS 風アイコンとして Material Icons Extended の `Icons.Filled.RssFeed` を採用した
  （プロトの `FMIcon name="rss"` の代替）。アイコン差し替えが必要なら別 Issue で扱う。
- スタートグルの「タップ伝播抑止」は `IconButton` の click 領域消費に依存している。
  Compose の標準挙動に従っており明示的な `pointerInput` 介入は行っていない。
- ArticleCard には summary / matched_keyword / 外部リンクアイコンを含めていない。
  Out of Scope の Pull-to-refresh / OGP サムネイル / FMKeywordTag と同様、SPEC §5.1 の
  最低要素（フィード名 + favicon / タイトル / メタ）に絞った。後続 Issue（横断
  タイムライン画面実装など）で必要に応じて拡張する想定。

## 関連

- Parent: #4
- Depends on: #25（テーマトークン）#26（Favicon）

STATUS: complete
