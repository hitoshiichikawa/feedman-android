# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-46-impl-starred-list
- HEAD commit: 2ca35e0
- Compared to: origin/main..HEAD
- 変更ファイル: feature/starred (4 ファイル) / core/data (2 ファイル) / di/RepositoryModule.kt / shell/Navigation.kt / res/values/strings.xml / test (3 ファイル + fixtures 4 件) / docs/specs (requirements.md + impl-notes.md)

## Verified Requirements

- 1.1 — `shell/Navigation.kt` で `AppRoute.Starred.id` ルートを placeholder から `StarredScreen` に置換。ドロワー `DrawerMainItem.Starred` 経由で `drawer_starred` から遷移可能（既存 AppShell 結線を流用）
- 1.2 — `strings.xml` `appbar_title_starred = "お気に入り"` を維持。既存 AppBarTitleResolver が当該ルートに対して当該文字列を返す結線済み
- 1.3 — `StarredScreen.kt::StarredList` で `LazyColumn` + `contentPadding` + `verticalArrangement` によりスクロール可能な縦リストを構成
- 1.4 — `StarredCardModelMapper.kt::toCardModel` が `item.feedTitle` を `ArticleCardModel.feedTitle` に等価転写。`StarredCardModelMapperTest.feed_title はカードの feedTitle にそのまま転写される` / `StarredViewModelTest.Req 1-3 1-4 ...` / `StarredItemsRepositoryImplTest.NFR 2-3 ...` で三重に裏取り
- 2.1 — `StarredItemsRepositoryImpl::loadPage` が cursor=null で `api.getStarredItems(cursor=null, limit=50)`。`Req 2-1 initial load issues GET with no cursor query` で検証
- 2.2 — 同 loader が前ページ `nextCursor` を引き継ぐ。`Req 2-2 subsequent load forwards previous next_cursor as cursor query`
- 2.3 — `CursorPage(hasMore=false)` を返すと CursorPagingSource が `nextKey=null` を返す。`Req 2-3 has_more false on response terminates paging with null nextKey`
- 2.4 — `Req 2-4 no further request is issued after terminal reached via TestPager`（HTTP request 数 = 2 で固定）
- 2.5 — `Req 2-5 initial load failure surfaces as LoadResult Error with FeedmanException` および `Req 2-5 network failure surfaces FeedmanException with NETWORK_ERROR code` で 401 と socket 切断の 2 系統を検証
- 2.6 — `Req 2-6 subsequent load failure surfaces error without discarding previous page`（1 ページ目 2 件取得済み + 2 ページ目 500 で Error 露出）
- 3.1 — `StarredListContent` の `TimelineScreenState.Empty` 分岐で `EmptyState` + `starred_empty_title/subtitle` を表示。前提となる空 PagingData 伝播は `空のスター一覧も PagingData として伝播する_Req 3_1 の前提` で確認
- 3.2 — `Req 3-2 refresh issues new request from head with cursor unset`（新しい PagingSource を生成すること自体は `newPagingSource()` の internal 公開で担保）
- 3.3 — 同テストの後段で `refreshedResult.nextKey == null` を確認（終端判定が通常ロードと同規則）
- 3.4 — `Req 3-4 refresh failure surfaces as LoadResult Error` + `StarredListContent` の `wasRefreshing` トランジション検出による snackbar 発火（`starred_refresh_error`）
- 4.1 — `StarredScreen` から `onOpenItemDetail(id)` を `ArticleCard.onOpen` に渡し、`Navigation.kt` がシェル直下の `onOpenItemDetail` に伝達
- 4.2 — 既存 ArticleDetailViewModel / `ArticleDetailSheet` は AppShell 直下のため Out of Scope（impl-notes.md 4.2 行）
- 5.1 — `StarredViewModel.cardPagingData` が `combineWithOverlays` で overlay 優先合成。`Req 5-1 ItemStateStore overlay 値はサーバー値より優先される（即時反映）` で検証
- 5.2 — 同じ ItemStateStore singleton 経由で詳細シート由来の overlay も合成される。`Req 5-2 詳細シート由来の overlay 更新も同じストアを経由して即時反映される`
- 5.3 — VM がフィルタ除去ロジックを持たないため当該行は残置。`Req 5-3 スター解除（overlay isStarred=false）でも当該行はリストから除去されない` で 3 件中真ん中を解除しても全件残置 + outline 化を検証
- 5.4 — `Req 5-4 リフレッシュ後にサーバーが解除済みを除外したレスポンスを返せば当該行は表示されない`（サーバー側 filter で除去成立）
- 5.5 — `Req 5-5 楽観的更新のサーバー反映が失敗するとロールバックで isStarred が直前値に戻る`（FeedmanException で failures 通知 + 合成後 isStarred 復元 + `viewModel.itemStateFailures == store.failures` の同一性も確認）
- 6.1 — 差分対象が feature/starred + core/data + di/RepositoryModule.kt + shell/Navigation.kt + res/values/strings.xml + app/src/test に限定（17 ファイル全件確認）
- 6.2 — `StarredScreen` 内に検索 UI（TextField / SearchBar）の追加なし。#47 検索領域に踏み込んでいない
- 6.3 — `StarredScreen` 内にフィルタタブ（FilterTabs）/ ソート切替 UI の追加なし
- NFR 1.1 — `ItemStateStore.overlays` は `MutableStateFlow` 即時更新で、`StarredViewModel.combineWithOverlays` は同期的に合成（明示的な遅延なし）
- NFR 1.2 — `LazyPagingItems` の append は Paging 3 規約により別 coroutine 実行（フレームワーク既定挙動）
- NFR 2.1 — `StarredItemsRepositoryImplTest` で初回成功・終端到達・初回失敗（HTTP + network）・追加ロード失敗・リフレッシュ成功・リフレッシュ失敗の 6 観点を網羅
- NFR 2.2 — `StarredViewModelTest` で Req 5.3 / 5.4 / 5.5 を各 1 ケース以上検証
- NFR 2.3 — `StarredItemsRepositoryImplTest.NFR 2-3 response feed_title is propagated to caller verbatim`（"Android Developers" / "Kotlin Blog" を検証）+ `StarredCardModelMapperTest` で重ね確認

## Findings

なし

## Summary

requirements.md の全 numeric ID（Req 1.1〜6.3 / NFR 1.1〜2.3）に対する実装またはテスト対応を確認した。boundary 逸脱なし（feature/starred + core/data + di + shell + strings.xml + tests に限定、検索 UI / フィルタ UI 不在）。impl-notes.md の requirement → テスト対応表と実コードの対応も一致する。

RESULT: approve
