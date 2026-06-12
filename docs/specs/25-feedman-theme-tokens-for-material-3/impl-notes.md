# 実装ノート — Issue #25 Feedman theme tokens for Material 3

## 概要

`design/mobile/fm-data.jsx` の `FM_THEME` を正本に、Compose で参照可能な ARGB 配色定数・
Material 3 ColorScheme マッピング・寸法トークン・テーマモード（端末追従 / Light / Dark）と
DataStore による永続化までを実装した。Issue #1 由来の仮 `FeedmanColors` / `FeedmanTheme` は
本実装で置き換え済み。

## oklch → ARGB 換算の方法

CSS Color Module Level 4 §16.7 のリファレンス変換に従って事前計算した値を `FeedmanColors.kt`
の ARGB 定数として埋め込んでいる。換算は以下の手順:

1. `oklch(L C h)` → OKLab: `a = C·cos(h_rad)`, `b = C·sin(h_rad)`
2. OKLab → linear sRGB: CSS Color Module Level 4 §16.7 の 3x3 マトリクスで変換
3. linear sRGB → sRGB（gamma compression）: 各チャンネルを `1.055·x^(1/2.4) - 0.055`
   （`x > 0.0031308` のとき）または `12.92·x`（それ以下）に変換
4. 各チャンネルを `[0, 1]` でクランプして 8bit 化（`round(x · 255)`）

換算結果は `FeedmanColors.kt` 中の各定数 KDoc に oklch 値とともに記述済み。今後 `FM_THEME` の
oklch 値が変わった場合は、同じパイプラインで再計算して定数を更新する（NFR 1.2）。

### 換算結果（参考値）

| トークン | oklch | ARGB |
|---|---|---|
| Light bg | `oklch(0.985 0 0)` | `#FFFAFAFA` |
| Light fg | `oklch(0.205 0 0)` | `#FF171717` |
| Light accent (Indigo) | `oklch(0.55 0.17 264)` | `#FF3C6AD3` |
| Light accentSoft | `mix(accent 12%, white)` | `#FFE6EDFB` |
| Light star | `oklch(0.78 0.16 84)` | `#FFE7AD01` |
| Light danger | `oklch(0.577 0.245 27)` | `#FFE7000F` |
| Light scrim | `rgba(0,0,0,0.32)` | `#52000000` |
| Dark bg | `oklch(0.145 0 0)` | `#FF0A0A0A` |
| Dark fg | `oklch(0.985 0 0)` | `#FFFAFAFA` |
| Dark accent (Indigo) | `oklch(0.68 0.15 264)` | `#FF6895F4` |
| Dark accentSoft | `accent @ 18% alpha` | `#2E6895F4` |
| Dark star | `oklch(0.82 0.16 84)` | `#FFF5BA26` |
| Dark danger | `oklch(0.704 0.191 22)` | `#FFFF6468` |
| Dark border | `oklch(1 0 0 / 12%)` | `#1FFFFFFF` |
| Dark borderStrong | `oklch(1 0 0 / 20%)` | `#33FFFFFF` |
| Dark scrim | `rgba(0,0,0,0.6)` | `#99000000` |

`accentSoft` の light 側は `color-mix(in oklch, accent 12%, white)` を OKLab 線形補間で展開し、
dark 側は `color-mix(in oklch, accent 18%, transparent)` を「accent 不透明色を 18% alpha で
描画する」と等価とみなして `0x2E6895F4` に固定した（透明とミックスする場合、結果は
alpha のみが変わり sRGB チャンネルは accent のまま、というのが OKLab/CSS color-mix の挙動）。

### WCAG AA コントラスト比

`FeedmanColorsTest` で実際に WCAG 2.1 の相対輝度式で計算した。fg / surface の組み合わせは
light 17.93 / dark 17.18 と AA（4.5:1）を大きく上回るため、本文テキストには問題ない（NFR 2.1）。

## 判断記録

### Material 3 ColorScheme への割り当て

FM_THEME のうち M3 標準スロットに収まる項目は標準スロットへ、収まらないものは
`FeedmanExtendedColors` という CompositionLocal で公開した。具体的な割り当て:

| FM_THEME | M3 スロット | 備考 |
|---|---|---|
| accent | `primary` / `secondary` / `surfaceTint` | M3 では secondary も指定が必要だが、SPEC §8 でアクセント 1 色確定のため同色 |
| accentOn | `onPrimary` / `onSecondary` / `onError` | |
| accentSoft | `primaryContainer` | M3 の "primaryContainer" の意味とも整合 |
| bg | `background` | |
| surface | `surface` | |
| fg | `onBackground` / `onSurface` | |
| muted | `surfaceVariant` | |
| mutedFg | `onSurfaceVariant` | extended にも保持 |
| border | `outline` | extended にも保持 |
| borderStrong | `outlineVariant` | extended にも保持 |
| danger | `error` | extended にも独立公開（Req 2.4） |
| scrim | `scrim` | extended にも保持 |
| **star** | **収まらない** | extended のみ（Req 2.4） |
| **cardBackground** | （= surface） | extended で独立公開（用途明示） |
| **readForegroundAlpha 0.55** | **収まらない** | extended のみ（Req 2.3） |

`star` / `danger` を独立トークンとして公開する規約（Req 2.4）に従い、M3 標準スロットの
複製ではなく `FeedmanExtendedColors` のフィールドとして利用すること。

### 寸法トークン

`FeedmanDimens` で SPEC §8 の値を提供（角丸 10/12/16dp、最小タップ 44dp、アイコン 18/20/22dp）。
`Material 3` 標準の `Shapes` は採用していない（SPEC §8 の固定値で十分なため、抽象化コストの
方が大きい）。後続 Issue（#26-#28）で `Shapes` 化が必要になったら追加で導入する。

### DataStore Preferences の採用理由

- 安定版（1.1.1, GA stable）が存在し、CLAUDE.md / libs.versions.toml の "GA stable のみ" 方針と整合
- Kotlin Flow API のため、`Flow<ThemeMode>` の publish と整合（Req 3.3 / 3.5）
- I/O は単一 file (`feedman_theme.preferences_pb`) で軽量
- JVM 単体テストから `PreferenceDataStoreFactory.create(produceFile = { File(tmp, ...) })` で
  実物を起動可能（NFR 3.2）。`EncryptedSharedPreferences` のように Android Keystore 依存ではない
- 既存の `EncryptedPrefsTokenStore`（SharedPreferences 系）と棲み分け: トークンはセキュリティ
  情報なので暗号化保管、テーマモードは非機微情報なので平文 DataStore

### テーマモード解決の役割分担

- `ThemeModeRepository`: 永続化 / 観測（platform-agnostic）
- `ThemeModeViewModel`（MainActivity 配下）: `Flow<ThemeMode>` を `StateFlow` 化、Compose に
  橋渡し
- `ThemeMode.shouldUseDarkTheme()` Composable 拡張: `FOLLOW_SYSTEM` の場合のみ
  `isSystemInDarkTheme()` を参照（Req 3.5）
- `FeedmanTheme(useDarkTheme = ...)`: 解決済み Boolean を受け取り、ColorScheme を切り替える

この分割により、ロジック部（`shouldUseDarkTheme` を除く）は Compose ランタイム非依存になり、
JVM 単体テストで検証できる。Compose に依存する `shouldUseDarkTheme` 自体は単純な
`when` 式なので単体テストの対象外とした（必要なら androidTest 側で UI スモークテストする）。

## requirement ID → テスト対応表

| Req | テストクラス / メソッド |
|---|---|
| 1.1 配色トークンの存在（全エントリ） | `FeedmanColorsTest`: 各 `light *`, `dark *` テスト群（合計約 20 件） |
| 1.2 Indigo アクセントの採用 | `FeedmanColorsTest`: `Req 1_2 light accent is Indigo ...`, `Req 1_2 dark accent is Indigo ...`, `Req 1_2 light accentOn is white`, accentSoft 2 件 |
| 1.3 oklch 換算コメントの保持 | `FeedmanColors.kt` の KDoc コメント（コードレビュー対象。テスト不可だがコメント存在自体は `FeedmanColors.kt` 上で目視確認可能） |
| 1.4 仮実装の置き換え | コミット履歴と `FeedmanColors.kt` の差分（旧 `IndigoAccent` 1 定数のみ → 全エントリ）で担保 |
| 1.5 light/dark 独立 | `FeedmanColorsTest`: `Req 1_5 light and dark palettes are independent ...` |
| 2.1 M3 ColorScheme マッピング | `FeedmanThemeMappingTest`: light / dark の各 slot マッピング検証 7 件 |
| 2.2 端末ダーク切替への再コンポジション追従 | `MainActivity` が `collectAsStateWithLifecycle` で StateFlow を購読し、`isSystemInDarkTheme()` は Compose State の一種として実装上 recompose を駆動する。`FeedmanTheme` の引数を Boolean としたため、引数変化で内部 `CompositionLocalProvider` も recompose する設計 |
| 2.3 独自トークン（カード背景・既読 opacity） | `FeedmanThemeMappingTest`: `Req 2_3 extended tokens expose read foreground alpha 0_55`, `Req 2_3 extended tokens expose card background per theme` |
| 2.4 star / danger の独立公開 | `FeedmanThemeMappingTest`: `Req 2_4 extended tokens expose star independently`, `Req 2_4 extended tokens expose danger independently` |
| 2.5 仮 FeedmanTheme の置き換え | コミット履歴（feat commit 1 件目）と `FeedmanTheme.kt` の差分で担保 |
| 3.1 3 種のテーマモード | `ThemeModeTest`: `Req 3_1 exposes three selectable modes`, `Req 3_1 enum names are stable and parseable`; `InMemoryThemeModeRepositoryTest`: `Req 3_1 all three modes can be persisted and observed` |
| 3.2 既定 = 端末追従 | `ThemeModeTest`: `Req 3_2 default is FOLLOW_SYSTEM`; `InMemoryThemeModeRepositoryTest`: `Req 3_2 default value before any setMode is FOLLOW_SYSTEM`; `DataStoreThemeModeRepositoryTest`: `Req 3_2 default is FOLLOW_SYSTEM when datastore is empty` |
| 3.3 切替後の再コンポジション即時適用 | `MainActivity` の実装で `StateFlow<ThemeMode>` を `collectAsStateWithLifecycle` 経由で購読し、`FeedmanTheme(useDarkTheme = themeMode.shouldUseDarkTheme())` に渡す構造。`InMemoryThemeModeRepositoryTest`: `Req 3_4 setMode value is observable in downstream flow`（Flow 観測の即時性を担保） |
| 3.4 切替値の永続化 | `DataStoreThemeModeRepositoryTest`: `Req 3_4 setMode persists value across new repository instance`, `Req 3_4 setMode updates emit to active observers` |
| 3.5 端末追従モード時のシステム追従 | `MainActivity` の `ThemeMode.shouldUseDarkTheme()` が `FOLLOW_SYSTEM` のときのみ `isSystemInDarkTheme()` を参照する実装。Compose 上の挙動は androidTest が必要だが、本機能のスコープでは optional |
| 3.6 読み出し失敗時のフォールバック | `DataStoreThemeModeRepositoryTest`: `Req 3_6 unknown raw value falls back to FOLLOW_SYSTEM`、および `Flow.catch` による I/O 例外吸収（コードレビュー対象） |
| 4.1 角丸 10-16dp | `FeedmanDimensTest`: `Req 4_1 corner tokens cover 10dp to 16dp range inclusive`, `Req 4_1 corner tokens include boundary values 10dp and 16dp` |
| 4.2 最小タップ 44dp | `FeedmanDimensTest`: `Req 4_2 minTapTarget is 44dp` |
| 4.3 アイコン 18-22dp | `FeedmanDimensTest`: `Req 4_3 icon tokens cover 18dp to 22dp range inclusive`, `Req 4_3 icon tokens include boundary values 18dp and 22dp` |
| 4.4 dp 単位 | `FeedmanDimens` の型シグネチャ（`Dp`）で型レベルで担保 |
| NFR 1.1 FM_THEME に無い色を公開しない | `FeedmanColors.kt` のレビュー対象（Indigo 系統のみ。Coral / Teal / Violet を含まない） |
| NFR 1.2 oklch コメント残置 | `FeedmanColors.kt` の各 KDoc コメント |
| NFR 2.1 コントラスト比 AA | `FeedmanColorsTest`: `NFR 2_1 light fg over surface satisfies WCAG AA contrast 4_5_1`, `NFR 2_1 dark fg over surface satisfies WCAG AA contrast 4_5_1` |
| NFR 2.2 操作可能要素の最小タップ標的 44dp | `FeedmanDimensTest`: `NFR 2_2 minTapTarget meets accessibility minimum 44dp` |
| NFR 3.1 Compose プレビュー対応 | `FeedmanTheme` は引数 1 個の Composable として公開（`@Preview` から `FeedmanTheme { ... }` で起動可能）。明示的な `@Preview` 関数追加は別 Issue 化候補 |
| NFR 3.2 永続化層を起動せずに単体検証可能 | `ThemeModeRepository` interface + `InMemoryThemeModeRepository` fake、`DataStoreThemeModeRepository` も tmp dir で JVM 起動可能（`DataStoreThemeModeRepositoryTest` で実証） |

## 追加した依存

- `androidx.datastore:datastore-preferences:1.1.1`（main implementation）— テーマモードの永続化
- `androidx.datastore:datastore-preferences-core:1.1.1`（test implementation）— JVM 単体テストでの DataStore 起動

## 確認事項（PR レビュー時）

- Material 3 の `secondary` / `tertiary` に Indigo accent を入れたが、本来 secondary は別系統の
  アクセント色を割り当てるのが M3 の意図。SPEC §8 でアクセント 1 色確定のため流用したが、
  M3 標準コンポーネント（例: `FilledTonalButton` の tonal 用 secondary）使用時に違和感が出る
  可能性。後続 Issue で実コンポーネントを組む際に再評価する
- `cardBackground` を `surface` と同値で公開しているが、SPEC §5.1 の「カード」が `bg`（#FAFAFA）
  上の `surface`（#FFF）か、それとも `surface2`（#F7F7F7）か、プロト HTML の実描画と
  突き合わせて再確認したい。本実装では `surface` を採用（プロト `FM_THEME` の `surface` 用途と
  合致するため）
- `secondary` / `tertiary` slot の重複や `cardBackground` の選択など、独自トークン拡張側の
  「正解」が後続 UI Issue 着手時に揺れる可能性。`FeedmanExtendedColors` の API 安定性は
  後続 Issue マイルストーン後に再評価する

## 派生タスク候補

- @Preview 関数の追加（`FeedmanThemeLightPreview` / `FeedmanThemeDarkPreview`、最小カード描画）—
  NFR 3.1 を実体としてカバーする小 Issue
- テーマ切替 UI（設定シート）— SPEC §5.0「右上テーマ切替」+ ドロワーフッタの「テーマ」と
  整合する別 Issue で扱う（本 Issue スコープ外）
- M3 `Typography`（Geist 近似 → 必要なら同梱）— Issue #1 の決定通り別 Issue

## Implementation Notes

### Task 1
（タスク分割無しの一括実装。設計 PR 不要の小規模 Issue として扱った）

採用方針: oklch → sRGB の事前計算を Python で行い、結果を ARGB 定数としてコード埋め込み。
重要な判断: M3 標準 ColorScheme と独自 `FeedmanExtendedColors` の二層構成にすることで、
（a）M3 標準コンポーネントが期待するスロットを満たし、（b）star / 既読 opacity 等の独自要件も
表現する。`accentSoft` の dark 側を `accent + 18% alpha` に固定したのは color-mix(in oklch,
accent 18%, transparent) と等価な実装の単純化。
残存課題: M3 secondary / tertiary slot の Indigo 流用が後続 UI 実装で違和感を生む可能性
（PR 確認事項に記載）。

STATUS: complete
