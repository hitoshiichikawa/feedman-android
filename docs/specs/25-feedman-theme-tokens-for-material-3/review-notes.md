# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-25-impl-feedman-theme-tokens-for-material-3
- HEAD commit: 43d5abc
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `FeedmanColors.kt` の Light/Dark 各 16 定数（bg / surface / surface2 / fg / muted / mutedFg /
  border / borderStrong / star / danger / scrim / accent / accentOn / accentSoft）が
  `FM_THEME`（fm-data.jsx L18-L34）の全エントリと対応。`FeedmanColorsTest` で全エントリの
  ARGB を一致確認
- 1.2 — `FeedmanColors.kt` 上で Indigo（light: `#3C6AD3` / dark: `#6895F4`）のみを公開し、
  Coral / Teal / Violet 定数は不存在。`FeedmanColorsTest`「Req 1_2 …」3 件で検証
- 1.3 — 各定数 KDoc に oklch 値が記載（例: `LightAccent` は `oklch(0.55 0.17 264) → #3C6AD3`、
  `LightAccentSoft` は `color-mix(in oklch, accent 12%, white)` の式も保持）
- 1.4 — commit c5668ed で旧 `FeedmanColors`（Indigo 単一定数のみの仮実装）が全エントリへ
  置き換えられている。残骸 `IndigoAccent` 等は存在しない
- 1.5 — `FeedmanColorsTest`「Req 1_5 light and dark palettes are independent for primary surfaces」
- 2.1 — `FeedmanLightColorScheme` / `FeedmanDarkColorScheme` が primary / background / surface /
  onSurface / outline / error / scrim 等を FM_THEME 由来でマッピング。`FeedmanThemeMappingTest`
  9 件で検証
- 2.2 — `MainActivity` が `FeedmanTheme(useDarkTheme = themeMode.shouldUseDarkTheme())` を呼び、
  `shouldUseDarkTheme()` 内で `isSystemInDarkTheme()`（Compose state）を参照することで端末
  ダーク切替に追従。`FeedmanTheme` 引数変化により内部 `CompositionLocalProvider` も recompose
- 2.3 — `FeedmanExtendedColors.cardBackground` / `readForegroundAlpha = 0.55f` を独自トークンとして
  公開。`FeedmanThemeMappingTest`「Req 2_3 …」2 件で検証
- 2.4 — `FeedmanExtendedColors.star` / `danger` を独立公開。`FeedmanThemeMappingTest`「Req 2_4 …」2 件
- 2.5 — commit c5668ed で旧 `FeedmanTheme`（仮実装）が本テーマで置き換え済み
- 3.1 — `ThemeMode` enum（FOLLOW_SYSTEM / LIGHT / DARK）。`ThemeModeTest`「Req 3_1 …」2 件
- 3.2 — `ThemeMode.DEFAULT = FOLLOW_SYSTEM`、`DataStoreThemeModeRepository.observe()` が空 prefs で
  DEFAULT を emit。`ThemeModeTest` / `InMemoryThemeModeRepositoryTest` / `DataStoreThemeModeRepositoryTest`
  各 1 件
- 3.3 — `MainActivity` が `collectAsStateWithLifecycle` で `StateFlow<ThemeMode>` を購読し、
  値変化で `FeedmanTheme(useDarkTheme = …)` の引数が変わって recompose。
  `InMemoryThemeModeRepositoryTest`「Req 3_4 setMode value is observable …」で Flow 観測性を担保
- 3.4 — `DataStoreThemeModeRepositoryTest`「Req 3_4 setMode persists value across new repository
  instance」+「Req 3_4 setMode updates emit to active observers」
- 3.5 — `MainActivity.shouldUseDarkTheme()` が `FOLLOW_SYSTEM` のときのみ `isSystemInDarkTheme()`
  を参照（LIGHT/DARK は固定）。実装上、Compose の再コンポジション機構で追従
- 3.6 — `DataStoreThemeModeRepository` が `Flow.catch` で I/O 例外を吸収し empty prefs として継続、
  未知文字列は `IllegalArgumentException` catch で DEFAULT へ。
  `DataStoreThemeModeRepositoryTest`「Req 3_6 unknown raw value falls back to FOLLOW_SYSTEM」
- 4.1 — `FeedmanDimens.cornerSmall=10.dp` / `cornerMedium=12.dp` / `cornerLarge=16.dp`。
  `FeedmanDimensTest`「Req 4_1 …」2 件
- 4.2 — `FeedmanDimens.minTapTarget=44.dp`。`FeedmanDimensTest`「Req 4_2 minTapTarget is 44dp」
- 4.3 — `iconSmall=18.dp` / `iconMedium=20.dp` / `iconLarge=22.dp`。`FeedmanDimensTest`「Req 4_3 …」2 件
- 4.4 — `FeedmanDimens` 全フィールドが `Dp` 型で型レベル担保（ピクセル直値を提供しない）
- NFR 1.1 — `FeedmanColors` は Indigo 系のみ。Coral / Teal / Violet 等の独自色は不存在
- NFR 1.2 — `FeedmanColors.kt` 全定数の KDoc に oklch 表記が保持されている
- NFR 2.1 — `FeedmanColorsTest`「NFR 2_1 light fg over surface …」「NFR 2_1 dark fg over surface …」で
  WCAG 2.1 相対輝度式から計算したコントラスト比 4.5 以上を検証
- NFR 2.2 — `FeedmanDimensTest`「NFR 2_2 minTapTarget meets accessibility minimum 44dp」
- NFR 3.1 — `FeedmanTheme` は `useDarkTheme: Boolean` 引数 1 個の Composable として公開され、
  `@Preview` から起動可能な API 形状
- NFR 3.2 — `ThemeModeRepository` interface + `InMemoryThemeModeRepository`（fake）+
  `DataStoreThemeModeRepository` を tmp dir でテスト起動（`DataStoreThemeModeRepositoryTest`）

## Findings

なし。

## Summary

Issue #25 のすべての numeric ID（Req 1.1-1.5 / 2.1-2.5 / 3.1-3.6 / 4.1-4.4 / NFR 1.1-1.2 / 2.1-2.2 / 3.1-3.2）
が `FeedmanColors` / `FeedmanTheme` / `FeedmanDimens` / `ThemeMode` / `ThemeModeRepository` の
実装と対応テスト群でカバーされている。境界は `core/designsystem` / `di` / `MainActivity`（最小追従）
/ `gradle` 配線 / `app/src/test` に閉じており、テーマ切替 UI などスコープ外の実装は含まれていない。
`./gradlew :app:testDebugUnitTest` は UP-TO-DATE で green。

RESULT: approve
