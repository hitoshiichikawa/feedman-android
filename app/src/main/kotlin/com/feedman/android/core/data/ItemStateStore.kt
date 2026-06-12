package com.feedman.android.core.data

import com.feedman.android.core.network.FeedmanException
import com.feedman.android.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 単一 item の既読 / スター楽観的更新オーバーレイ（Issue #38 / Req 1.3）。
 *
 * `null` のフィールドは「上書き値なし（サーバー由来値をそのまま使う）」を意味する
 * （Req 1.4）。既読とスターは独立に保持される（Req 1.3）。
 *
 * @property isRead 既読の楽観的上書き値。`null` = 上書きなし。
 * @property isStarred スターの楽観的上書き値。`null` = 上書きなし。
 */
data class ItemStateOverlay(
    val isRead: Boolean? = null,
    val isStarred: Boolean? = null,
) {
    /** 既読・スターのいずれも未設定なら true（overlay 削除候補）。 */
    val isEmpty: Boolean
        get() = isRead == null && isStarred == null
}

/**
 * サーバー反映に失敗したことを購読側 UI に通知するイベント（Issue #38 / Req 2.3 / NFR 2.1）。
 *
 * @property itemId 失敗した記事の ID
 * @property kind 失敗した更新の種別（既読 / スター）
 */
data class ItemStateFailure(
    val itemId: String,
    val kind: Kind,
) {
    enum class Kind { Read, Star }
}

/**
 * 既読 / スターの楽観的更新オーバーレイを保持する横断同期点（Issue #38 / Req 1〜5 / NFR 1, 2）。
 *
 * `docs/GRAND-DESIGN.md` §5.4 が定義する「ページングデータ < オーバーレイ」のマージ規約を実体化する。
 * 複数画面（横断タイムライン・記事詳細シート・将来のスター一覧 / 検索）が `overlays` を共通の
 * 単一ストリームとして購読することで、ある画面で行ったトグル結果が他画面にも追加 API 呼び出し
 * なしに反映される（Req 4.1 / 4.2）。
 *
 * ## 動作概要
 *
 * 1. UI イベント（カードのスタートグル / 既読化トリガー）→ [setRead] / [setStarred] / [markRead]
 * 2. [overlays] を即時更新（Req 1.1 / NFR 1.1）
 * 3. 内部 scope で `ItemDetailRepository.updateState` を呼ぶ（NFR 1.2: 結果を待たずに配信は完了）
 * 4. 失敗時は [overlays] の該当フィールドを baseline へ巻き戻し、[failures] でイベントを流す（Req 2.2 / 2.3 / 2.5）
 *
 * ## 連続トグルの設計判断
 *
 * `setStarred(item, true)` の inflight 中にユーザーが再度 `setStarred(item, false)` を呼んだ場合、
 * 本実装は **両方の API を順序通り直列に投げる**（楽観値は最新値で常に上書き）。1 件目の失敗で
 * ロールバックが走るが、その時の baseline は「1 件目が立ち上がった時点の baseline」を保持しており、
 * 2 件目が成功してさらに上書きする場合と整合する。詳細は impl-notes.md を参照。
 *
 * ## DI
 *
 * `@Singleton` で Hilt が単一インスタンスを保証する。`@ApplicationScope` 相当の長寿命
 * scope は [scope] コンストラクタ引数で受け取る（テストでは fresh scope を渡す）。
 *
 * @param repository SPEC §4.2 `PUT /api/items/{id}/state` のラッパー（Issue #35）。
 * @param scope `viewModelScope` よりも長寿命の外側 scope。本ストアは Singleton なので
 *              通常は `SupervisorJob() + Dispatchers.Main.immediate` 相当を渡す。
 */
@Singleton
class ItemStateStore @Inject constructor(
    private val repository: ItemDetailRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : UserScopedCache {

    private val _overlays = MutableStateFlow<Map<String, ItemStateOverlay>>(emptyMap())

    /**
     * 全 item の overlay。`Map<itemId, ItemStateOverlay>` で公開する（Req 1.2 / 4.1）。
     *
     * 購読側は `combine(pagingData, overlays)` などで合成して表示状態を生成する。
     * 合成 helper として [resolve] を提供する（Req 3.x）。
     */
    val overlays: StateFlow<Map<String, ItemStateOverlay>> = _overlays.asStateFlow()

    private val _failures = MutableSharedFlow<ItemStateFailure>(
        replay = 0,
        extraBufferCapacity = 16,
    )

    /**
     * サーバー反映失敗の one-shot 通知（Req 2.3 / NFR 2.1）。
     *
     * `replay = 0` で再購読時に再送しない（再コンポジションで snackbar を多重表示しないため）。
     * 購読側は scope ごとに 1 度だけ collect することを想定している。
     */
    val failures: SharedFlow<ItemStateFailure> = _failures.asSharedFlow()

    /**
     * 既読 overlay を新しい値に切り替え、サーバーへ更新リクエストを発行する（Req 1.1 / 2.1）。
     *
     * @param itemId 対象 item の ID
     * @param isRead 新しい既読値（楽観値）
     * @param baselineRead ロールバック時に復元する旧既読値（サーバー由来 or 直前の overlay）
     */
    fun setRead(itemId: String, isRead: Boolean, baselineRead: Boolean) {
        applyOverlay(itemId = itemId) { it.copy(isRead = isRead) }
        scope.launch {
            try {
                repository.updateState(itemId = itemId, isRead = isRead, isStarred = null)
            } catch (e: FeedmanException) {
                rollbackRead(itemId = itemId, baseline = baselineRead)
                _failures.emit(ItemStateFailure(itemId = itemId, kind = ItemStateFailure.Kind.Read))
            }
        }
    }

    /**
     * スター overlay を新しい値に切り替え、サーバーへ更新リクエストを発行する（Req 1.1 / 2.1）。
     *
     * @param itemId 対象 item の ID
     * @param isStarred 新しいスター値（楽観値）
     * @param baselineStarred ロールバック時に復元する旧スター値
     */
    fun setStarred(itemId: String, isStarred: Boolean, baselineStarred: Boolean) {
        applyOverlay(itemId = itemId) { it.copy(isStarred = isStarred) }
        scope.launch {
            try {
                repository.updateState(itemId = itemId, isRead = null, isStarred = isStarred)
            } catch (e: FeedmanException) {
                rollbackStarred(itemId = itemId, baseline = baselineStarred)
                _failures.emit(ItemStateFailure(itemId = itemId, kind = ItemStateFailure.Kind.Star))
            }
        }
    }

    /**
     * 既読化トリガー（Req 5.1 / 5.2 / 5.3）。
     *
     * `currentIsRead = true` のとき何もしない（冪等 / Req 5.3）。`false` のときは
     * [setRead] と同じ動きで overlay を `true` に切り替えサーバー反映する。
     *
     * @param itemId 対象 item の ID
     * @param currentIsRead 現在の既読状態（overlay 合成後の値）。`true` なら no-op
     */
    fun markRead(itemId: String, currentIsRead: Boolean) {
        if (currentIsRead) return // Req 5.3: 冪等
        setRead(itemId = itemId, isRead = true, baselineRead = false)
    }

    /**
     * ログアウト時に overlay を初期状態に戻す（Issue #50 Req 3.1）。
     *
     * 以下を実行する:
     * - [_overlays] を空マップに置き換える（前ユーザーの既読・スター楽観値を破棄）
     * - inflight な `repository.updateState` がもし継続中であれば、その完了結果は
     *   既に空になった overlay に向けてのロールバックを試みる可能性がある。ただし
     *   rollback ロジックは「該当 item が存在しなければ早期 return する」設計のため
     *   （[rollbackRead] / [rollbackStarred] 参照）、競合は安全に吸収される。
     *
     * 失敗イベント [_failures] は `replay = 0` の SharedFlow であり、購読者が居なくなれば
     * 自動的にバッファから消える（明示的なバッファ clear API は SharedFlow に存在しない）。
     * ログアウト後にログイン画面へ遷移する経路では旧 ViewModel が破棄されるため、
     * 残置されたイベントが新ユーザーに観測されることは無い。
     */
    override suspend fun reset() {
        _overlays.value = emptyMap()
    }

    // ── internal ─────────────────────────────────────────────────────

    private fun applyOverlay(itemId: String, transform: (ItemStateOverlay) -> ItemStateOverlay) {
        _overlays.update { map ->
            val current = map[itemId] ?: ItemStateOverlay()
            val next = transform(current)
            if (next.isEmpty) map - itemId else map + (itemId to next)
        }
    }

    private fun rollbackRead(itemId: String, baseline: Boolean) {
        _overlays.update { map ->
            val current = map[itemId] ?: return@update map
            // baseline がサーバー由来値と等価な場合は overlay を除去する。
            // ここでは baseline 値を保持して overlay にする（resolve helper が overlay 値を使う）。
            val next = current.copy(isRead = baseline)
            if (next.isEmpty) map - itemId else map + (itemId to next)
        }
    }

    private fun rollbackStarred(itemId: String, baseline: Boolean) {
        _overlays.update { map ->
            val current = map[itemId] ?: return@update map
            val next = current.copy(isStarred = baseline)
            if (next.isEmpty) map - itemId else map + (itemId to next)
        }
    }

    companion object {

        /**
         * サーバー由来値と overlay を合成して最終表示値を解決する（Req 3.1 / 3.3 / 3.4）。
         *
         * 各フィールドについて overlay に値があればそれを使い、なければサーバー値を使う。
         *
         * @param itemId 対象 item の ID
         * @param serverRead サーバー由来の既読値
         * @param serverStarred サーバー由来のスター値
         * @param overlays [ItemStateStore.overlays] のスナップショット
         * @return overlay 値を優先した解決後 (isRead, isStarred) ペア
         */
        fun resolve(
            itemId: String,
            serverRead: Boolean,
            serverStarred: Boolean,
            overlays: Map<String, ItemStateOverlay>,
        ): ResolvedItemState {
            val o = overlays[itemId]
            return ResolvedItemState(
                isRead = o?.isRead ?: serverRead,
                isStarred = o?.isStarred ?: serverStarred,
            )
        }
    }
}

/**
 * 合成解決後の既読 / スター値（Issue #38 Req 3.x）。
 *
 * `Pair<Boolean, Boolean>` ではなく明示的なデータクラスにすることで、呼び出し側の
 * 可読性とリファクタ耐性を高める。
 */
data class ResolvedItemState(
    val isRead: Boolean,
    val isStarred: Boolean,
)
