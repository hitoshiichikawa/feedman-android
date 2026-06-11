// fm-data.jsx — Feedman mobile design tokens, icon set, and mock data.
// Carries over the web app's oklch grayscale tokens (web/src/app/globals.css)
// and adds a tweakable accent. Exports to window: FM_THEME, FMIcon, FM_FEEDS,
// FM_ITEMS, fmFormatDate, FM_KEYWORDS.

// ── Accent palettes (shared L/C, varied hue per design guidance) ────────────
const FM_ACCENTS = {
  indigo: { name: 'Indigo', light: 'oklch(0.55 0.17 264)', dark: 'oklch(0.68 0.15 264)', on: '#ffffff' },
  coral:  { name: 'Coral',  light: 'oklch(0.62 0.17 28)',  dark: 'oklch(0.70 0.15 28)',  on: '#ffffff' },
  teal:   { name: 'Teal',   light: 'oklch(0.58 0.12 185)', dark: 'oklch(0.70 0.11 185)', on: '#04201c' },
  violet: { name: 'Violet', light: 'oklch(0.55 0.19 300)', dark: 'oklch(0.69 0.16 300)', on: '#ffffff' },
};

/** Build the full token set for a theme + accent. Mirrors globals.css :root / .dark. */
function FM_THEME(dark, accentKey) {
  const a = FM_ACCENTS[accentKey] || FM_ACCENTS.indigo;
  const accent = dark ? a.dark : a.light;
  if (dark) {
    return {
      accent, accentOn: a.on, accentSoft: 'color-mix(in oklch, ' + accent + ' 18%, transparent)',
      bg: 'oklch(0.145 0 0)', surface: 'oklch(0.205 0 0)', surface2: 'oklch(0.235 0 0)',
      fg: 'oklch(0.985 0 0)', muted: 'oklch(0.269 0 0)', mutedFg: 'oklch(0.708 0 0)',
      border: 'oklch(1 0 0 / 12%)', borderStrong: 'oklch(1 0 0 / 20%)',
      star: 'oklch(0.82 0.16 84)', danger: 'oklch(0.704 0.191 22)', scrim: 'rgba(0,0,0,0.6)',
      statusDark: true,
    };
  }
  return {
    accent, accentOn: a.on, accentSoft: 'color-mix(in oklch, ' + accent + ' 12%, white)',
    bg: 'oklch(0.985 0 0)', surface: 'oklch(1 0 0)', surface2: 'oklch(0.975 0 0)',
    fg: 'oklch(0.205 0 0)', muted: 'oklch(0.97 0 0)', mutedFg: 'oklch(0.556 0 0)',
    border: 'oklch(0.922 0 0)', borderStrong: 'oklch(0.87 0 0)',
    star: 'oklch(0.78 0.16 84)', danger: 'oklch(0.577 0.245 27)', scrim: 'rgba(0,0,0,0.32)',
    statusDark: false,
  };
}
FM_THEME.accents = FM_ACCENTS;

// ── Icon set (lucide-style stroke paths, used by the web app via lucide-react) ─
const FM_ICON_PATHS = {
  menu: 'M3 6h18M3 12h18M3 18h18',
  search: 'M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M21 21l-4.3-4.3',
  plus: 'M12 5v14M5 12h14',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z',
  refresh: 'M21 2v6h-6 M3 12a9 9 0 0 1 15-6.7L21 8 M3 22v-6h6 M21 12a9 9 0 0 1-15 6.7L3 16',
  chevronRight: 'M9 18l6-6-6-6',
  chevronDown: 'M6 9l6 6 6-6',
  chevronLeft: 'M15 18l-6-6 6-6',
  arrowLeft: 'M19 12H5 M12 19l-7-7 7-7',
  external: 'M15 3h6v6 M10 14L21 3 M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.7 21a2 2 0 0 1-3.4 0',
  pause: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M10 15V9 M14 15V9',
  alert: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 8v4 M12 16h.01',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M12 1v2 M12 21v2 M4.2 4.2l1.4 1.4 M18.4 18.4l1.4 1.4 M1 12h2 M21 12h2 M4.2 19.8l1.4-1.4 M18.4 5.6l1.4-1.4',
  moon: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
  check: 'M20 6L9 17l-5-5',
  x: 'M18 6L6 18 M6 6l12 12',
  rss: 'M4 11a9 9 0 0 1 9 9 M4 4a16 16 0 0 1 16 16 M5 19a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  sparkles: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z M19 3v4 M21 5h-4',
  trash: 'M3 6h18 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6',
  filter: 'M22 3H2l8 9.5V19l4 2v-8.5L22 3z',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2 M5.5 5.6L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.4A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.6z',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 6v6l4 2',
  dot: 'M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0',
};

/** Generic lucide-style icon. `fill` only used by star/bookmark filled states. */
function FMIcon({ name, size = 20, color = 'currentColor', fill = 'none', strokeWidth = 2, style }) {
  const d = FM_ICON_PATHS[name];
  if (!d) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={color}
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      style={{ flexShrink: 0, display: 'block', ...style }} aria-hidden="true">
      {d.split(' M').map((seg, i) => <path key={i} d={(i === 0 ? seg : 'M' + seg)} />)}
    </svg>
  );
}

// ── Mock feeds (mirrors GET /api/subscriptions: Subscription[]) ─────────────
const FM_FEEDS = [
  { id: 's1', feed_id: 'f1', feed_title: 'Publickey',          letter: 'P', color: '#2563eb', fetch_interval_minutes: 30,  feed_status: 'active',  unread_count: 12 },
  { id: 's2', feed_id: 'f2', feed_title: 'Zenn トレンド',        letter: 'Z', color: '#0ea5e9', fetch_interval_minutes: 15,  feed_status: 'active',  unread_count: 5 },
  { id: 's3', feed_id: 'f3', feed_title: 'はてブ テクノロジー',    letter: 'B', color: '#ea580c', fetch_interval_minutes: 30,  feed_status: 'active',  unread_count: 28 },
  { id: 's4', feed_id: 'f4', feed_title: 'ITmedia NEWS',        letter: 'I', color: '#dc2626', fetch_interval_minutes: 60,  feed_status: 'active',  unread_count: 8 },
  { id: 's5', feed_id: 'f5', feed_title: 'The Go Blog',         letter: 'G', color: '#0891b2', fetch_interval_minutes: 360, feed_status: 'active',  unread_count: 2 },
  { id: 's6', feed_id: 'f6', feed_title: 'Qiita 人気の記事',      letter: 'Q', color: '#16a34a', fetch_interval_minutes: 30,  feed_status: 'stopped', error_message: '手動で停止しました', unread_count: 14 },
  { id: 's7', feed_id: 'f7', feed_title: 'GIGAZINE',            letter: 'G', color: '#7c3aed', fetch_interval_minutes: 60,  feed_status: 'error',   error_message: '404 Not Found（フィードが見つかりません）', unread_count: 0 },
];
const FM_FEED_BY_ID = Object.fromEntries(FM_FEEDS.map((f) => [f.feed_id, f]));

// ── Mock articles ───────────────────────────────────────────────────────────
const now = Date.now();
const mins = (m) => new Date(now - m * 60000).toISOString();
function buildContent(lead) {
  return (
    '<p>' + lead + '</p>' +
    '<p>本稿では実際の導入プロジェクトで得られた知見を、設計判断の背景とあわせて整理する。' +
    'パフォーマンスと運用コストのトレードオフは、チームの規模やリリース頻度によって最適解が変わる。</p>' +
    '<h3>移行のステップ</h3>' +
    '<p>まずは計測から始め、ボトルネックを特定したうえで段階的に置き換えていく。一度にすべてを変更すると' +
    '切り戻しが難しくなるため、フラグで新旧を切り替えられる構成にしておくと安全だ。</p>' +
    '<ul><li>計測とベースラインの取得</li><li>影響範囲の小さい箇所から置換</li><li>本番でのカナリア検証</li></ul>' +
    '<p>結果として、平均レイテンシは約 40% 改善し、運用上のアラートも大きく減少した。詳細な数値と' +
    '再現手順は元記事を参照してほしい。</p>'
  );
}
const RAW = [
  ['f1', 'Goの新しいイテレータが安定版に、range-over-funcの実用例まとめ', 'range-over-func が GA となり、独自コレクションのイテレートが書きやすくなった。', 18, false, false, 142, 'Go'],
  ['f3', 'OpenAIが新しい推論APIを公開、レイテンシを大幅短縮', '新エンドポイントはストリーミング前提の設計で、初回トークンまでの時間が縮んだ。', 42, false, true, 318, 'OpenAI'],
  ['f5', 'Kubernetes 1.31でGateway APIがGAに到達、Ingressからの移行ガイド', 'Ingress からの移行を見据えた実践的なマッピング表を用意した。', 95, false, false, 87, 'Kubernetes'],
  ['f2', '個人開発のSaaSを1年運用して分かったコスト最適化の勘所', '小さく始めて計測しながら削る、という当たり前を徹底した結果を共有する。', 130, true, false, 64, null],
  ['f4', 'TypeScript 5.7のパフォーマンス改善とエディタ体験の刷新', '型チェックの高速化に加え、補完の精度とエラーメッセージが改善された。', 165, false, false, 51, 'TypeScript'],
  ['f3', 'Rustで書かれた高速JSバンドラの実力をベンチマーク検証', '既存ツールとの比較で、コールドスタートとHMRの両面を測定した。', 210, true, false, 203, 'Rust'],
  ['f1', '認証基盤をCookieセッションからトークンへ移行する判断軸', 'モバイル対応を見据えたとき、どこで線を引くべきかを整理する。', 260, false, false, 96, null],
  ['f2', 'WebAssemblyでフロントの重い処理をオフロードする設計パターン', '画像処理や全文検索をブラウザ内で完結させる構成を検討した。', 320, true, false, 38, null],
  ['f4', 'PostgreSQL 17のインクリメンタルバックアップを本番投入した話', 'バックアップ時間とリストア検証の運用フローを刷新した。', 410, false, true, 77, null],
  ['f3', 'RSSリーダーを自作してニュース収集を自動化してみた', 'フィード検出からはてブ数の取得まで、小さな自作リーダーの設計を紹介。', 540, true, false, 121, null],
  ['f1', 'マイクロサービスをやめてモジュラモノリスに戻した理由', 'チーム規模に対して分割しすぎた結果、運用コストが膨らんでいた。', 720, true, false, 256, null],
  ['f5', 'Goの構造化ログ（slog）を実運用に乗せるときの設計', 'ハンドラの選定とフィールド設計の指針をまとめた。', 900, false, false, 44, 'Go'],
  ['f4', '生成AIをコードレビューに組み込んで分かった得意・不得意', '機械的な指摘は任せ、設計判断は人が担うという役割分担に落ち着いた。', 1100, true, false, 188, null],
  ['f2', 'フロントエンドの状態管理、2025年の現実的な選び方', 'ライブラリ選定よりもデータの所在を先に決めるべきだという話。', 1500, true, false, 72, null],
  ['f3', 'Dockerイメージを最小化してコールドスタートを半分にした', 'distrolessとマルチステージビルドの合わせ技で大幅に削減できた。', 1900, true, false, 134, null],
  ['f1', 'オブザーバビリティ入門、まず何から計測すべきか', 'メトリクス・ログ・トレースのうち、最初に手を付ける順番を解説する。', 2600, true, false, 59, null],
];
const FM_ITEMS = RAW.map((r, i) => {
  const [feed_id, title, summary, m, is_read, is_starred, hatebu, kw] = r;
  const f = FM_FEED_BY_ID[feed_id];
  return {
    id: 'i' + (i + 1), feed_id, feed_title: f.feed_title, favicon_letter: f.letter, favicon_color: f.color,
    title, summary, link: 'https://example.com/articles/' + (i + 1),
    published_at: mins(m), is_date_estimated: i === 11, is_read, is_starred,
    hatebu_count: hatebu, hatebu_fetched_at: mins(m), author: i % 3 === 0 ? '編集部' : '',
    matched_keyword: kw, content: buildContent(summary),
  };
});

const FM_KEYWORDS = [
  { id: 'k1', term: 'Go', enabled: true, scope: 'title', hits: 3 },
  { id: 'k2', term: 'Kubernetes', enabled: true, scope: 'title', hits: 1 },
  { id: 'k3', term: 'OpenAI', enabled: true, scope: 'title', hits: 1 },
  { id: 'k4', term: 'TypeScript', enabled: false, scope: 'title', hits: 1 },
];

/** Relative date, mirrors item-list.tsx formatDate. */
function fmFormatDate(iso) {
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  const h = Math.floor(diffMs / 3600000);
  const d = Math.floor(diffMs / 86400000);
  if (h < 1) return '1時間以内';
  if (h < 24) return h + '時間前';
  if (d < 7) return d + '日前';
  return date.toLocaleDateString('ja-JP', { year: 'numeric', month: 'short', day: 'numeric' });
}

Object.assign(window, { FM_THEME, FMIcon, FM_FEEDS, FM_ITEMS, FM_KEYWORDS, fmFormatDate });
