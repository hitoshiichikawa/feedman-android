# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-26-impl-favicon-letter-avatar
- HEAD commit: 85ffd15
- Compared to: origin/main..HEAD
- 変更ファイル: `app/build.gradle.kts` / `core/designsystem/Dimens.kt` /
  `core/designsystem/LetterAvatarPalette.kt` / `core/ui/Favicon.kt` /
  `core/ui/FaviconLogic.kt` / `core/designsystem/FeedmanDimensTest.kt` /
  `core/ui/FaviconLogicTest.kt` / `docs/specs/26-.../{requirements,impl-notes}.md`
- Feature Flag Protocol: 採否 `opt-out`（CLAUDE.md 宣言通り）→ flag 細目は適用しない

## Verified Requirements

- 1.1 — `FaviconLogic.isDataUrl` + `Favicon.kt` 68-95（AsyncImage 分岐）。
  テスト: `Req 1_1 isDataUrl returns true for valid data url png` / `... svg` /
  `... tolerates leading whitespace`
- 1.2 — `Favicon.kt` 83-94: `ImageRequest.Builder(...).data(faviconValue)` を
  単一 model として渡し、Coil の memory cache に委譲。`crossfade(false)`。
  （視覚要件であり Coil 挙動依存。impl-notes に判断記録あり）
- 1.3 — `Favicon.kt` 89-92: `ContentScale.Crop` + `Modifier.size(size).clip(shape)`。
  正方形領域への収まりは実装で担保（視覚要件 / requirements.md Out of Scope）
- 2.1 — `FaviconLogic.extractLetter`。テスト: `Req 2_1 ... ascii character` /
  `... japanese character`
- 2.2 — `FaviconLogic.isDataUrl` の false 分岐 + `Favicon.kt` 70 / 96 の
  fallback 経路、および AsyncImage `onError` callback (Favicon.kt 93)。
  テスト: `Req 2_2 isDataUrl returns false for null / empty / https / plain / file`
- 2.3 — `FaviconLogic.PLACEHOLDER_LETTER = "?"`、`extractLetter` の null/blank 分岐。
  テスト: `Req 2_3 extractLetter returns question mark for null / empty /
  whitespace only string`
- 2.4 — `FaviconLogic.extractLetter` の `codePointAt(0)` + `Character.toChars(...)`。
  テスト: `Req 2_4 extractLetter keeps emoji as single grapheme` /
  `keeps surrogate pair after leading whitespace`
- 3.1 — `FaviconLogic.pickLetterColor` の決定論性。テスト:
  `Req 3_1 pickLetterColor returns same color for same title` / `differs between
  distinct hashes`
- 3.2 — `String.hashCode and Int.MAX_VALUE` + `% LetterAvatarPalette.Size`。
  テスト: `Req 3_2 pickLetterColor distributes different titles to different colors`
- 3.3 — JVM `String.hashCode` 仕様（プロセス再生成跨ぎ安定）。テスト:
  `Req 3_3 pickLetterColor uses stable hash across invocations` /
  `returns same color for null and empty and blank`
- 3.4 — 剰余で index 範囲を強制し `LetterAvatarPalette.Colors[index]` から選択。
  テスト: `Req 3_4 pickLetterColor always returns palette color`
- 4.1 — `FeedmanDimens.faviconExtraSmall/Small/Medium/Large` 4 バリアント。
  テスト: `Issue 26 Req 4_1 favicon size tokens are ordered ascending`
- 4.2 — `Favicon.kt` 両分岐（AsyncImage / LetterAvatar）に同一 `shape =
  RoundedCornerShape(cornerRadius)` を渡している（Favicon.kt 63-104）
- 4.3 — `LETTER_SIZE_RATIO = 0.46f` を `(size.value * ratio).sp` として
  Text に渡す（Favicon.kt 124）。プロト準拠の比例式（視覚要件）
- 4.4 — `FeedmanDimens` 4 トークンが FMFavicon 利用箇所の 16/18/28/32dp に整合。
  テスト: `Issue 26 Req 4_4 favicon size tokens match FMFavicon usage 16-18-28-32dp`
- 5.1 — `Modifier.clip(RoundedCornerShape(faviconCornerRadius))` を両分岐に適用、
  `faviconCornerRadius = 8.dp`。テスト: `Issue 26 Req 5_1 favicon corner radius
  is positive dp`
- 5.2 — `Box(contentAlignment=Center)` + `Text(color=Color.White,
  fontWeight=FontWeight.Bold, textAlign=Center)`（Favicon.kt 136-147）。視覚要件
- 5.3 — `LetterAvatarPalette` を `object`（テーマモード非依存）で実装し、
  Composable 内で `MaterialTheme.colorScheme` を参照しない（パレットを直接利用）。
  テスト: `Req 3_4 ... always returns palette color`
- NFR 1.1 — `FaviconLogic` を `Favicon` Composable から分離し、JVM `app/src/test`
  に 18 ケース配置（FaviconLogicTest.kt）
- NFR 1.2 — `FaviconLogic` の各関数は副作用無しの object 関数として実装
- NFR 1.3 — data URL / null / 非 data URL / 空タイトルの各分岐を `isDataUrl` /
  `extractLetter` のテストから観察可能
- NFR 2.1 — Coil `AsyncImage`（非同期デコード）を採用
- NFR 2.2 — `remember(faviconValue)` で isDataUrl / hasError を、
  `remember(feedTitle)` で背景色・文字を再計算抑制（Favicon.kt 66, 70, 122, 123）

## Boundary

許可境界（prompt 指定: `core/ui` / `core/designsystem` / gradle 配線 /
`app/src/test`）内に全変更が収まっている。`docs/specs/` 配下は仕様 / 実装メモのみ
で既存 spec ディレクトリ。境界逸脱なし。

## Test 実行確認

`./gradlew :app:testDebugUnitTest --tests
com.feedman.android.core.ui.FaviconLogicTest --tests
com.feedman.android.core.designsystem.FeedmanDimensTest` を実行し
BUILD SUCCESSFUL を確認（既存テスト分も維持）。

## Findings

なし

## Summary

全 numeric ID（Req 1.1〜5.3 / NFR 1.1〜2.2）について、テスト観測可能なものは
JVM 単体テスト 18 + 3 ケースで担保し、視覚要件（1.3 / 4.3 / 5.1 一部 / 5.2）は
requirements.md Out of Scope 通り実装で担保している。境界・テスト共に問題なし。

RESULT: approve
