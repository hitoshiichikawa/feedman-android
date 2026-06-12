package com.feedman.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.SearchRepository
import com.feedman.android.core.ui.ArticleCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 横断検索画面の ViewModel（Issue #47 / Req 1 / 2 / 3 / 5 / 6）。
 *
 * 状態の正本は 2 つの `StateFlow` に分割する:
 *
 * - [queryInput]: 入力欄の現在値（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。テキスト変更は常に
 *   こちらにのみ反映され、サーバー呼び出しは発生しない（NFR 1.2）
 * - [submittedQuery]: 検索を確定したキーワード（前後空白除去後 / Req 3.1）。`null` は
 *   「未送信 = 空クエリ状態」を示し、Pager Flow は emptyFlow となる（Req 2.1 / 3.2）
 *
 * [cardPagingData] は [submittedQuery] の変化に追随し、null → emptyFlow、非 null →
 * 新しい Pager を生成する（Req 5.4: キーワード変更で先頭ページから取り直し）。
 *
 * ## 設計上の補足
 *
 * - `flatMapLatest` で submittedQuery の変化に合わせて Flow を切替える。前回の Pager 購読は
 *   破棄され、追従中の append/refresh も中断する（Req 5.4）。
 * - `cachedIn(viewModelScope)` で再コンポジション時のキャッシュを保持する（再回転・他画面
 *   経由復帰）。
 * - サジェストチップは Req 2.2 でプロトタイプ準拠の静的リストとし、ViewModel は
 *   [SUGGESTIONS] 定数を公開する（UI が `stringResource` 経由ではなく直接表示する）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _queryInput = MutableStateFlow("")

    /** 入力欄の現在値（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。 */
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _submittedQuery = MutableStateFlow<String?>(null)

    /**
     * 検索を確定したキーワード（前後空白除去後 / Req 3.1）。`null` は未送信＝空クエリ状態
     * （Req 2.1 / 3.2）。
     */
    val submittedQuery: StateFlow<String?> = _submittedQuery.asStateFlow()

    /**
     * カード描画用 PagingData の Flow。
     *
     * [submittedQuery] が `null` のときは [emptyFlow]（Req 2.1 / 3.2: サーバー呼び出しなし）、
     * 非 null のときは [SearchRepository.pagingData] で新しい Pager を生成しつつ
     * [SearchCardModelMapper] で [ArticleCardModel] に射影する。Pager 出力は
     * `cachedIn(viewModelScope)` で再コンポジション間のキャッシュを保持する。
     *
     * `flatMapLatest` を使うことで、新しい [submittedQuery] が来たタイミングで前回の購読を
     * 破棄し、新しいキーワードで先頭ページから取得を開始する（Req 5.4）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> =
        submittedQuery.flatMapLatest { query ->
            if (query == null) {
                emptyFlow()
            } else {
                searchRepository.pagingData(query)
                    .map { paging -> paging.map(SearchCardModelMapper::toCardModel) }
            }
        }.cachedIn(viewModelScope)

    /**
     * 入力欄の値を変更する（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。
     *
     * サーバー呼び出しは発生しない。クリア操作（Req 1.5）から呼ばれる場合も同じ経路で、
     * 直前に確定済みの [submittedQuery] は **意図的に維持しない**: クリアは「空クエリ状態に
     * 戻す」挙動なので [submittedQuery] も null に戻すために、UI 側がクリア操作で
     * [clear] を呼ぶ。
     *
     * @param value 入力欄の新しい値（未トリム）
     */
    fun onQueryChanged(value: String) {
        _queryInput.value = value
    }

    /**
     * 入力欄を空に戻し、空クエリ表示（Req 1.5 / 2.1）に切り替える。
     *
     * UI 側のクリアボタン（Req 1.4 / 1.5）から呼ぶ。`submittedQuery` を null に戻すことで
     * [cardPagingData] が `emptyFlow` に切り替わり、結果リストが消える。
     */
    fun clear() {
        _queryInput.value = ""
        _submittedQuery.value = null
    }

    /**
     * 入力欄の現在値を送信して検索を確定する（Req 3.1 / 3.2 / 3.3 / 5.4）。
     *
     * - 前後の空白を除去（Req 3.1）
     * - 除去後に空文字なら何もしない（Req 3.2: サーバー呼び出し発生せず、空クエリ表示維持）
     * - 非空のときは [submittedQuery] を更新 → [cardPagingData] が新 Pager に切り替わる
     *   （Req 3.3 / 5.4）
     */
    fun submit() {
        val trimmed = _queryInput.value.trim()
        if (trimmed.isEmpty()) return
        _submittedQuery.value = trimmed
    }

    /**
     * サジェストチップ選択（Req 2.3 / 2.4）。
     *
     * - 当該チップの文字列を入力欄に投入する（Req 2.3）
     * - そのまま [submit] と同じフローで検索を開始する（Req 2.4）
     */
    fun selectSuggestion(suggestion: String) {
        _queryInput.value = suggestion
        submit()
    }

    companion object {
        /**
         * Req 2.2: プロトタイプ `FMSearchScreen` の suggestions と同じ静的候補。
         *
         * 利用者ごとに動的生成する仕組みは v1 の Out of Scope（requirements.md Out of Scope）。
         */
        val SUGGESTIONS: List<String> = listOf("Go", "Kubernetes", "OpenAI", "TypeScript", "Rust")
    }
}
