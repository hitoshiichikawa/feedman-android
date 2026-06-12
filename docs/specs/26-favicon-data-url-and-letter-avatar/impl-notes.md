# 実装メモ — Issue #26 Favicon data URL and letter avatar composable

## 概要

`design/SPEC.md` §4.4 が定める「data URL favicon または `null`」を描画する共通
Composable `core/ui/Favicon.kt` を、レターアバター fallback と独立パレットつきで
追加した。プロト `design/mobile/fm-ui.jsx` の `FMFavicon` を視覚基準としつつ、
タイトル → 安定色選択のロジック・サロゲートペア対応・テーマ非依存パレットを
固定して、後続 Issue（タイムラインカード・ドロワー・記事カード）から再利用できる
形に整えた。

## ファイル構成

| パス | 役割 |
|---|---|
| `app/src/main/kotlin/com/feedman/android/core/designsystem/LetterAvatarPalette.kt` | レターアバター背景色の独立パレット（12 色、テーマ非依存） |
| `app/src/main/kotlin/com/feedman/android/core/designsystem/Dimens.kt`（追記） | Favicon サイズ 16/18/28/32dp と角丸 8dp トークンを追加 |
| `app/src/main/kotlin/com/feedman/android/core/ui/FaviconLogic.kt` | 純粋ロジック（data URL 判定 / 頭文字抽出 / 色選択） |
| `app/src/main/kotlin/com/feedman/android/core/ui/Favicon.kt` | Composable 本体（Coil AsyncImage + LetterAvatar fallback） |
| `app/src/test/kotlin/com/feedman/android/core/ui/FaviconLogicTest.kt` | JVM 単体テスト（AC 単位 18 ケース） |
| `app/src/test/kotlin/com/feedman/android/core/designsystem/FeedmanDimensTest.kt`（追記） | Favicon サイズ・角丸の境界値テスト |
| `app/build.gradle.kts`（追記） | `libs.coil.compose` を `implementation` に配線 |

## requirement ID → テスト対応表

| Requirement ID | 担保しているテスト | 担保箇所（実装） |
|---|---|---|
| Req 1.1（data URL 描画） | `FaviconLogicTest.Req 1_1 isDataUrl returns true for valid data url png` ほか | `FaviconLogic.isDataUrl`, `Favicon` の `AsyncImage` 分岐 |
| Req 1.2（再取得しない） | （Coil memory cache の挙動。設計判断として impl-notes に明記） | `Favicon` で `ImageRequest.Builder(...).data(faviconValue)` を一意キーに |
| Req 1.3（アスペクト比歪めず） | （視覚要件のため instrumented 領分。実装で担保） | `Favicon` の `ContentScale.Crop` + `Modifier.size(size).clip(shape)` |
| Req 2.1（タイトル頭文字） | `FaviconLogicTest.Req 2_1 extractLetter returns first ascii/japanese character` | `FaviconLogic.extractLetter` |
| Req 2.2（非 data URL / decode 失敗時 fallback） | `FaviconLogicTest.Req 2_2 isDataUrl returns false for null/empty/https/plain/file/...` | `Favicon` の `else` 分岐 + Coil `onError` callback |
| Req 2.3（プレースホルダ `?`） | `FaviconLogicTest.Req 2_3 extractLetter returns question mark for null/empty/whitespace` | `FaviconLogic.extractLetter`、`FaviconLogic.PLACEHOLDER_LETTER` |
| Req 2.4（サロゲートペア・絵文字 1 文字を分割しない） | `FaviconLogicTest.Req 2_4 extractLetter keeps emoji as single grapheme` / `keeps surrogate pair after leading whitespace` | `Character.toChars(codePoint)` で UTF-16 surrogate pair をまとめて返す |
| Req 3.1（同一タイトル → 同一色） | `FaviconLogicTest.Req 3_1 pickLetterColor returns same color for same title` | `FaviconLogic.pickLetterColor` |
| Req 3.2（決定論的ハッシュ） | `FaviconLogicTest.Req 3_2 pickLetterColor distributes different titles to different colors` | `String.hashCode() and Int.MAX_VALUE` |
| Req 3.3（プロセス再生成跨ぎ安定） | `FaviconLogicTest.Req 3_3 pickLetterColor uses stable hash across invocations` / `returns same color for null and empty and blank` | JVM `String.hashCode` の仕様安定性 |
| Req 3.4（パレット内のみ） | `FaviconLogicTest.Req 3_4 pickLetterColor always returns palette color` | `LetterAvatarPalette.Colors[index]`、剰余で index 範囲を強制 |
| Req 4.1（サイズバリアント） | `FeedmanDimensTest.Issue 26 Req 4_1 favicon size tokens are ordered ascending` | `FeedmanDimens.faviconExtraSmall/Small/Medium/Large` |
| Req 4.2（同一外形） | （実装で担保 — data URL 経路と LetterAvatar 経路で同一 `RoundedCornerShape(faviconCornerRadius)`） | `Favicon` 両分岐で共通の `shape` を渡す |
| Req 4.3（文字サイズ比例） | （JVM 単体テストでは Compose の sp 換算が完結できないため、実装で担保） | `(size.value * LETTER_SIZE_RATIO).sp`、定数 `LETTER_SIZE_RATIO=0.46f` |
| Req 4.4（FMFavicon 利用箇所のサイズに整合） | `FeedmanDimensTest.Issue 26 Req 4_4 favicon size tokens match FMFavicon usage 16-18-28-32dp` | `FeedmanDimens` 4 サイズトークン |
| Req 5.1（正方形 + 角丸） | `FeedmanDimensTest.Issue 26 Req 5_1 favicon corner radius is positive dp` | `Favicon` 両分岐で `Modifier.size(size).clip(shape)` |
| Req 5.2（中央・白・太字） | （視覚要件。実装で担保） | `Box(contentAlignment=Center)` + `Text(color=White, fontWeight=Bold)` |
| Req 5.3（テーマ非依存パレット） | `FaviconLogicTest.Req 3_4 ... always returns palette color`（LetterAvatarPalette はテーマに依存しない `object`） | `LetterAvatarPalette` を `object`（テーマモードを取らない）で実装 |
| NFR 1.1（JVM テスト可能なロジック分離） | テスト 18 ケース全件が `app/src/test`（JVM）配下 | `FaviconLogic` を Composable から分離 |
| NFR 1.2（純粋関数） | `Req 3_3 pickLetterColor uses stable hash across invocations` ほか同一入力テスト | `FaviconLogic` は副作用なし |
| NFR 1.3（分岐を観察可能） | `FaviconLogicTest` の data URL / null / empty / decode-error 入力で分岐を網羅 | `isDataUrl` の bool 返却 + `extractLetter` の `?` 返却 |
| NFR 2.1（非同期デコード） | （Coil の AsyncImage 仕様。実装判断） | `AsyncImage` を採用 |
| NFR 2.2（再デコード抑制） | （Coil memory cache + Compose `remember`） | `remember(faviconValue) { isDataUrl(...) }` / `remember(feedTitle) { pickLetterColor / extractLetter }` |

## 判断記録

### 1. レターアバター用パレットを Indigo アクセントと独立にした（Req 5.3）

プロト `FMFavicon` は `item.favicon_color || item.color || '#64748b'` をそのまま使うが、
SPEC §8 で確定したアクセントは Indigo のみ。Indigo 系統だけでレターアバターを彩色
すると識別性が失われるため、Tailwind 系の 12 色（slate / red / orange / amber / lime /
green / teal / sky / blue / violet / fuchsia / pink）を `LetterAvatarPalette` として
独立公開した。すべて白文字（`#FFFFFF`）で AA 相当のコントラストを満たす中明度色のみを
採用し、ライト／ダーク共通で同色を返す（Req 5.3）。

### 2. ハッシュは `String.hashCode` を採用（Req 3.3）

JVM の `String.hashCode` は `s[0]*31^(n-1) + ... + s[n-1]` で仕様確定しており、
プロセス再生成・OS バージョン跨ぎでも同一値を返す。MD5/SHA1 は `java.security`
への依存が増えるため不採用。`hashCode and Int.MAX_VALUE` で正の整数に正規化し、
`Math.abs(Int.MIN_VALUE) == Int.MIN_VALUE` の罠を回避（コメントで明示）。

### 3. data URL 判定は「`data:` プレフィックス」のみ（Req 1.1, 2.2）

base64 ペイロードまでの厳密検証は Coil の `AsyncImage` `onError` コールバックに委ね、
ロジック側では「Coil に渡すべきか LetterAvatar に落とすか」だけを決める。これにより
壊れた base64 / 不正な MIME も最終的にレターアバターへ fallback する（Req 2.2）。
ロバスト性のため先頭空白は許容する。

### 4. 文字抽出は Unicode コードポイント単位（Req 2.4）

`String.first()` は `Char` を返すためサロゲートペア（絵文字・拡張漢字など BMP 外
コードポイント）を割ってしまう。`codePointAt(0)` + `Character.toChars(...)` で
1 コードポイント分の `String` を返す実装にした。grapheme cluster（例: 国旗絵文字、
ZWJ で連結された絵文字シーケンス）までは対応しないが、フィードタイトルの 1 文字目
には実用上問題ない（テストでは 📰 NEWSPAPER を境界値として検証）。

### 5. サイズトークンを `FeedmanDimens` に集約した（Req 4.4）

`fm-ui.jsx` の FMFavicon 利用箇所は `size = 16 / 18 / 28 / 32` の 4 通り。サイズを
呼び出し側で直接 `dp` リテラル指定するとデザイントークンの一元管理が崩れるため、
`FeedmanDimens.faviconExtraSmall / faviconSmall / faviconMedium / faviconLarge` の
4 トークンを追加した。角丸は `radius = 8` をデフォルトとして集約（プロトでは
小サイズで `radius = 4` を使う箇所もあるが、Compose 実装では Material 3 整合
のため単一値に統一）。

### 6. Composable 描画そのもののテストは scope 外（Out of Scope）

Req 1.3（アスペクト比歪まず）/ Req 4.3（文字サイズ比例）/ Req 5.1, 5.2（角丸・中央
配置・白文字太字）は視覚要件であり、JVM 単体テストでは検証不可能。requirements.md
の Out of Scope に「UI 描画そのものを実機エミュレータで検証する instrumented テスト
整備」が明記されているため、本 Issue では実装で担保するに留め、テストは追加して
いない。後続 Issue で a11y / Compose UI Test を整備する想定。

## 追加依存の理由

- `io.coil-kt:coil-compose:2.7.0`
  - `gradle/libs.versions.toml` に既存宣言済み（`coil = "2.7.0"`）。本 Issue で
    `app/build.gradle.kts` から `implementation(libs.coil.compose)` として配線追加した。
  - 採用理由: `design/SPEC.md` §2「画像 = Coil」「favicon は data URL（§4.2 注意）」
    で確定済み。data URL を `AsyncImage` の `data(...)` に直接渡すだけで復号でき、
    内部 memory cache により Req 1.2 / NFR 2.1 を自然に満たす。
  - バージョン 2.7.0 は GA stable（CLAUDE.md `libs.versions.toml` の GA stable のみ
    方針に準拠）。

## 確認事項（PR レビュワー向け）

1. **パレット選定 12 色の妥当性**: `LetterAvatarPalette.Colors` は Tailwind 系の中明度
   12 色を選定した。色相が偏っていない／白文字でコントラストが十分か、デザイナー
   レビューで確認を希望する。配色仕様が後から確定する場合、`LetterAvatarPalette.Colors`
   のみ差し替えれば AC を維持できる構造。
2. **角丸 8dp 単一値**: プロトの `radius=4..8` のサイズ別出し分けを 8dp に統一した。
   16dp サイズで角丸が大きく見える場合は呼び出し側で `Modifier.clip(...)` 上書きの
   余地を残してあるが、デフォルト 8dp で良いかレビュー希望。
3. **a11y `contentDescription`**: 呼び出し側で明示しない限り `null`（装飾扱い）。
   requirements.md Out of Scope に「a11y ラベル文言の最終確定は後続 Issue」と明記
   されているため、本 Issue では引数として受け付けるだけ。
4. **Coil の data URL decode 失敗時の挙動**: `onError` callback で `hasError=true`
   にして LetterAvatar に切り替える設計。Coil 内部が一度 error を返した後でも
   `faviconValue` が変わると `remember` キーがリセットされて再試行される
   （`var hasError by remember(faviconValue) { mutableStateOf(false) }`）。

## 検証結果

- `./gradlew build`: BUILD SUCCESSFUL（unit test / lint / debug+release assemble まで通過）
- 単体テスト件数:
  - `FaviconLogicTest`: 18 ケース全て pass
  - `FeedmanDimensTest`: 既存 6 + 追加 3 = 9 ケース全て pass

## ブランチ・コミット

ブランチ: `claude/issue-26-impl-favicon-letter-avatar`

コミット（origin/main..HEAD）:

1. `feat(core/ui): Favicon ロジック層と LetterAvatarPalette を追加`
2. `feat(core/designsystem): Favicon サイズトークンと角丸を FeedmanDimens に追加`
3. `feat(core/ui): Favicon Composable を追加（data URL 描画 + レターアバター fallback）`

STATUS: complete
