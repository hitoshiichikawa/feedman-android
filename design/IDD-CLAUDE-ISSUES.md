# Feedman Android idd-claude Issue Backlog

作成日: 2026-06-12

`feedman-android` の GitHub Issue バックログの正本。iOS 版（`feedman-ios/design/IDD-CODEX-ISSUES.md`）の分割実績と教訓（`ZERO-TO-IDD-CODEX-NOTES.md`）を踏襲し、**登録時点から idd-claude が 1 PR で完了できる task 粒度**まで分割して登録済み。

## 前提

- Android クライアントは Kotlin + Jetpack Compose / min SDK 26 / MVVM + Repository で作る（`design/SPEC.md` §2）。
- v1 の認証は Cookie ではなく Bearer トークン方式（SPEC §3 方針 A）。
- v1 ではキーワードプッシュ通知 UI は実装しない。ドロワー導線も非表示にする。
- 視覚基準は `design/Feedman Mobile.html` と `design/mobile/*.jsx`、API 契約は `design/SPEC.md` と `design/SERVER.md`、アーキテクチャは `docs/GRAND-DESIGN.md` を正とする。
- **サーバー側のトークン認証は `hitoshiichikawa/feedman` の Umbrella #163（子: #164〜#172）として登録済み・実装中**。Android の認証実結合（#21, #23 の E2E）はこれが前提。
- キーワードプッシュのサーバー API（SERVER.md §2）は **Issue 未作成**。次フェーズ着手時に feedman 側へ起票する。

## Epic / 子Issue 対応表（2026-06-12 登録）

Epic（`epic` ラベル）には `auto-dev` を付けない。実装投入は `task` ラベルの子Issue単位。

| Epic | 内容 | 子Issue |
|---|---|---|
| — | アプリスケルトン | #1 |
| — | CI（build + unit test チェック） | #14 |
| #2 | API models / APIClient / pagination / error | #15 #16 #17 #18 #22 |
| #3 | OAuth トークンログイン / トークン保存 | #19 #20 #21 #23 #24 |
| #4 | デザインシステム / 共通 Compose UI | #25 #26 #27 #28 |
| #5 | ドロワーナビゲーション / アプリシェル | #29 #30 #31 |
| #6 | 横断タイムライン | #32 #33 #34 |
| #7 | 記事詳細 / 既読 / スター / Custom Tabs | #35 #36 #37 #38 |
| #8 | フィード別一覧 / 購読設定 | #39 #40 #41 #42 #43 |
| #9 | フィード登録 | #44 #45 |
| #10 | スター一覧 / 横断検索 | #46 #47 #48 |
| #11 | アカウント / ログアウト / 退会 | #49 #50 #51 |
| #12 | v1 結合仕上げ | #52 #53 #54 |
| #13 | キーワードプッシュ通知（次フェーズ） | #55 #56 #57 |

Epic 番号 #2〜#13 は feedman-ios の Epic（同番号）と意図的に揃えてある。

## 依存グラフ（子Issue の Depends on）

```
#1 ─┬─ #14 (CI)
    ├─ #15 → #16 → #17 ─┬─ #18 ─┬─ #32 → #33 → #34        （タイムライン）
    │                   │       ├─ #40
    │                   │       ├─ #46 / #47               （スター/検索系の基盤）
    │                   ├─ #35 → #36 → #37 / #38           （詳細・状態同期）
    │                   ├─ #21（+#19 +#20 +feedman#163）→ #22 / #23 → #24
    │                   ├─ #39（+#30）→ #41 → #42 / #43
    │                   ├─ #44（+#28 +#31）→ #45
    │                   └─ #49（+#31 +#28）→ #50（+#21 +#24）→ #51
    ├─ #19 / #20        （PKCE・TokenStore は #1 のみ依存）
    └─ #25 ─┬─ #26 / #27 / #28
            └─ #29（+#28）→ #30（+#26）/ #31
v1 完了（#12 系）→ #55 → #56 / #57（次フェーズ・サーバー §2 API 必須）
```

正確な依存は各 Issue 本文の `Depends on:` を正とする。

## 粒度の基準（iOS 版の教訓を踏襲）

- 1 Issue = 1 PR = 受入基準 3〜6 個程度。
- 変更ファイルの目安は 3〜8 個。
- API 型、APIClient、Repository、ViewModel、UI、polish を同一 Issue に詰め込まない。
- cross-feature state sync（#38）/ auth refresh（#22）/ TokenStore（#20）/ Custom Tabs（#37）/ push deeplink（#57）など横断関心事は単独 Issue。
- Architect の tasks が 8 個を超えそうなら分割に戻す（11 個以上見込みは事前分割）。
- 初期は mock mode（Fake repository）で UI を先行し、実 API 統合は別 Issue（例: #30 mock → #39 実データ）。

## 投入手順（人間のオペレーション）

1. 依存（`Depends on:`）の PR がすべて merge された Issue を選ぶ。
2. `blocked` ラベルを外し、`auto-dev` ラベルを付ける。

   ```bash
   gh issue edit <N> -R hitoshiichikawa/feedman-android --remove-label blocked --add-label auto-dev
   ```

3. watcher が Triage → （必要なら設計 PR ゲート）→ 実装 PR を作成する。
4. 同時投入は「触るパッケージが重ならない範囲」に留める（hot file 競合の予防）。

- **#1 merge 後に最初に投入可能になるもの**: #14（CI）, #15（モデル）, #19（PKCE）, #20（TokenStore）, #25（テーマ）。
- 認証系（#21 #23）の実機 E2E は feedman#163 系の merge・デプロイ後。単体テスト（MockWebServer）はサーバー実装前でも成立する。
- #55〜#57 は v1 リリースとサーバー §2 API 起票・実装まで投入しない。

## 既知の論点（needs-decisions 候補）

- **フェッチ間隔セグメント値の不整合**（#43 に記載済み）: SPEC §5.6 は 15/30/60/180/360 分だが、サーバーは 30〜720 分・30 分刻みで検証する（feedman#46）。15 分は拒否されるため、投入前にセグメント値を確定すること（例: 30/60/180/360/720）。
- 記事詳細の HTML レンダリング方式（#36）: Html.fromHtml ベースか WebView か。プレビュー用途なら前者を想定。
- スター解除時のスター一覧の挙動（#46）: 即時除去かグレーアウトか。

## ラベル運用

| ラベル | 用途 |
|---|---|
| `epic` | 管理用 Epic。auto-dev を付けない |
| `task` | 実装単位の子Issue（1 Issue = 1 PR） |
| `blocked` | 依存 Issue 未 merge により投入不可。解消時に人間が外す |
| `auto-dev` | idd-claude の処理対象。投入時に人間が付ける |
| その他 | idd-claude 標準（`needs-decisions` / `claude-claimed` / `ready-for-review` 等）。watcher が遷移させる |
