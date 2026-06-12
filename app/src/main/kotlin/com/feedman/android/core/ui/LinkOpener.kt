package com.feedman.android.core.ui

import android.content.Context

/**
 * 元記事 URL を外部ブラウザ／Custom Tabs で開くためのデータ層境界
 * （Issue #37 / Req 1.1 / Req 2.1 / Req 3.x / Req 4.x / NFR 1.1 / NFR 2.1 / NFR 3.1）。
 *
 * 詳細シートのフッタ「元記事を開く」、およびタイムラインカードの外部リンクアイコンの
 * 双方から共通して呼び出される。Custom Tabs 非対応端末では標準 ACTION_VIEW にフォールバック
 * し、未対応スキーム（http/https 以外）は安全側に倒してユーザーへエラー通知できる結果を返す
 * （Req 3 / Req 4 / `design/GRAND-DESIGN.md` §5.7）。
 *
 * ## なぜ interface か
 *
 * - 単体テストでは [Context] / [android.content.Intent] / Custom Tabs SDK の Android 依存を
 *   排除して fake 実装で振る舞いを差し替えられるようにするため（NFR 3.1）
 * - 将来的に設定 UI で「完全外部ブラウザ切替」を追加する際に、本 interface の実装差し替えで
 *   対応できるようにするため（requirements.md "Out of Scope" / GRAND-DESIGN §5.7）
 *
 * 本 interface は **副作用の発火と発火結果の返却** のみを担い、既読化や snackbar 通知は
 * 呼び出し側（ViewModel / Composable）が結果 [OpenLinkResult] を見て決める。
 */
interface LinkOpener {

    /**
     * 指定 URL を Custom Tabs もしくはフォールバックで開く。
     *
     * - URL の検証は [UrlValidation.validate] と同じ規則（http / https のみ許可）に従う
     * - Custom Tabs 対応ブラウザが存在しない場合は ACTION_VIEW にフォールバックする
     * - いずれの経路でも起動できない場合は [OpenLinkResult.NoAppToHandle] を返す
     *
     * @param context Activity / Service など、Custom Tabs / ACTION_VIEW を起動できる Android [Context]
     * @param url 開く対象の URL 文字列
     * @return 起動結果（[OpenLinkResult]）。呼び出し側はこの値を使って既読化や snackbar を判断する
     */
    fun open(context: Context, url: String): OpenLinkResult
}

/**
 * [LinkOpener.open] の結果（Issue #37 / Req 3.x / Req 4.x）。
 *
 * Sealed class とし、呼び出し側で `when` の網羅性を保証する。
 */
sealed class OpenLinkResult {

    /**
     * Custom Tabs 経由で起動した（Req 1.1 / Req 2.1）。
     */
    object OpenedWithCustomTabs : OpenLinkResult()

    /**
     * Custom Tabs 非対応のため ACTION_VIEW フォールバックで起動した（Req 3.1 / Req 3.2）。
     */
    object OpenedWithFallback : OpenLinkResult()

    /**
     * URL のスキーマが http/https 以外、または空文字・不正な構文（Req 4.1 / Req 4.2 / Req 4.3）。
     *
     * 呼び出し側はこの結果を受け取った場合、既読化を行わずユーザーにエラー通知する。
     *
     * @property reason 拒否理由（snackbar 文言の選択 / ログのため）
     */
    data class InvalidUrl(val reason: InvalidUrlReason) : OpenLinkResult()

    /**
     * Custom Tabs もフォールバックの ACTION_VIEW も受け取るアプリが端末に存在しない
     * （Req 3.3）。呼び出し側は既読化を行わずユーザーにエラー通知する。
     */
    object NoAppToHandle : OpenLinkResult()
}

/**
 * [OpenLinkResult.InvalidUrl] の理由（Req 4.1 / Req 4.2 / NFR 3.1）。
 *
 * 区別したい主な理由は、ログ・スナックバー文言切替の余地を残すため。
 * v1 では UI 上の文言は同一だが、テスト・ログでの追跡性を高める。
 */
enum class InvalidUrlReason {
    /** 空文字または空白のみ。 */
    Blank,

    /** URI として構文的に解釈できない。 */
    Malformed,

    /** http または https 以外のスキーマ（mailto / javascript / file 等を含む）。 */
    UnsupportedScheme,
}
