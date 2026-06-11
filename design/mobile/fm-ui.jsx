// fm-ui.jsx — shared primitives, card variants, gestures.
// Reads tokens/data/icons from window (fm-data.jsx). Exports to window:
// FMCtx, useFM, FMFavicon, FMStar, FMHatebu, FMKeywordTag, FMThumb,
// FMPullToRefresh, FMInfinite, FMTimelineCard, FMArticleCard, FMTab.

const FMCtx = React.createContext(null);
const useFM = () => React.useContext(FMCtx);

// ── Feed favicon: colored letter avatar (mirrors web FeedFavicon fallback) ──
function FMFavicon({ item, size = 28, radius = 8 }) {
  const color = item.favicon_color || item.color || '#64748b';
  const letter = item.favicon_letter || item.letter || (item.feed_title || '?')[0];
  return (
    <div style={{
      width: size, height: size, borderRadius: radius, background: color, color: '#fff',
      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
      fontSize: size * 0.46, fontWeight: 700, letterSpacing: '-0.02em',
      fontFamily: 'Geist, system-ui, sans-serif',
    }}>{letter}</div>
  );
}

// ── OGP thumbnail placeholder (striped, honest about being a placeholder) ───
function FMThumb({ w = 96, h = 72, color }) {
  const { T } = useFM();
  const c = color || T.mutedFg;
  return (
    <div style={{
      width: w, height: h, borderRadius: 10, flexShrink: 0, overflow: 'hidden',
      position: 'relative', background: T.muted,
      backgroundImage: `repeating-linear-gradient(135deg, ${T.border} 0 8px, transparent 8px 16px)`,
    }}>
      <span style={{
        position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'Geist Mono, ui-monospace, monospace', fontSize: 10, letterSpacing: '0.08em',
        color: T.mutedFg, opacity: 0.85,
      }}>OGP</span>
    </div>
  );
}

// ── Star toggle ─────────────────────────────────────────────────────────────
function FMStar({ on, onClick, size = 20 }) {
  const { T } = useFM();
  return (
    <span role="button" tabIndex={0} aria-pressed={on} aria-label={on ? 'スターを解除' : 'スターを付ける'}
      onClick={(e) => { e.stopPropagation(); onClick && onClick(); }}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); onClick && onClick(); } }}
      style={{
        background: 'none', border: 'none', padding: 6, margin: -6, cursor: 'pointer',
        display: 'inline-flex', borderRadius: 999, WebkitTapHighlightColor: 'transparent',
      }}>
      <FMIcon name="star" size={size} fill={on ? T.star : 'none'} color={on ? T.star : T.mutedFg} />
    </span>
  );
}

// ── Open original article in external browser ───────────────────────────────
function FMOpenLink({ item, size = 20 }) {
  const { T, actions } = useFM();
  return (
    <span role="button" tabIndex={0} aria-label="元記事をブラウザで開く"
      onClick={(e) => { e.stopPropagation(); actions.openLink(item); }}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); actions.openLink(item); } }}
      style={{
        background: 'none', border: 'none', padding: 6, margin: -6, cursor: 'pointer',
        display: 'inline-flex', borderRadius: 999, WebkitTapHighlightColor: 'transparent',
      }}>
      <FMIcon name="external" size={size} color={T.mutedFg} />
    </span>
  );
}

// ── Hatebu count chip ───────────────────────────────────────────────────────
function FMHatebu({ item, compact }) {
  const { T } = useFM();
  const val = item.hatebu_fetched_at == null ? '−' : item.hatebu_count;
  const hot = item.hatebu_count >= 100;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: compact ? 11 : 12,
      color: hot ? T.accent : T.mutedFg, fontWeight: hot ? 600 : 500,
      fontFamily: 'Geist, system-ui, sans-serif',
    }}>
      <FMIcon name="rss" size={compact ? 12 : 13} color={hot ? T.accent : T.mutedFg} />
      {val}{hot && <span style={{ fontSize: 10 }}>users</span>}
    </span>
  );
}

// ── Keyword match tag ───────────────────────────────────────────────────────
function FMKeywordTag({ term }) {
  const { T } = useFM();
  if (!term) return null;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 11, fontWeight: 600,
      padding: '2px 7px', borderRadius: 999, color: T.accentOn, background: T.accent,
      fontFamily: 'Geist, system-ui, sans-serif', letterSpacing: '0.01em',
    }}>
      <FMIcon name="bell" size={11} color={T.accentOn} />{term}
    </span>
  );
}

function FMUnread({ n }) {
  const { T } = useFM();
  if (!n) return null;
  return (
    <span style={{
      minWidth: 20, height: 20, padding: '0 6px', borderRadius: 999, background: T.accent,
      color: T.accentOn, fontSize: 11, fontWeight: 700, display: 'inline-flex',
      alignItems: 'center', justifyContent: 'center', fontFamily: 'Geist, system-ui',
    }}>{n}</span>
  );
}

// ── Pull-to-refresh wrapper (touch + mouse drag from top) ───────────────────
function FMPullToRefresh({ children, onRefresh, enabled = true, scrollRef }) {
  const { T } = useFM();
  const [pull, setPull] = React.useState(0);
  const [refreshing, setRefreshing] = React.useState(false);
  const start = React.useRef(null);
  const TH = 72;
  const atTop = () => { const el = scrollRef && scrollRef.current; return !el || el.scrollTop <= 0; };
  const begin = (y) => { if (enabled && !refreshing && atTop()) start.current = y; };
  const move = (y, e) => {
    if (start.current == null) return;
    const d = y - start.current;
    if (d > 0 && atTop()) { if (e && e.cancelable) e.preventDefault(); setPull(Math.min(d * 0.5, TH + 24)); }
    else if (d < 0) { start.current = null; setPull(0); }
  };
  const end = () => {
    if (start.current == null) return;
    start.current = null;
    if (pull >= TH) { setRefreshing(true); setPull(TH); Promise.resolve(onRefresh && onRefresh()).then(() => { setTimeout(() => { setRefreshing(false); setPull(0); }, 650); }); }
    else setPull(0);
  };
  const prog = Math.min(pull / TH, 1);
  return (
    <div style={{ position: 'relative', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}
      onTouchStart={(e) => begin(e.touches[0].clientY)}
      onTouchMove={(e) => move(e.touches[0].clientY, e)}
      onTouchEnd={end}
      onMouseDown={(e) => begin(e.clientY)}
      onMouseMove={(e) => { if (start.current != null) move(e.clientY, e); }}
      onMouseUp={end} onMouseLeave={end}>
      <div style={{
        position: 'absolute', top: 8, left: 0, right: 0, display: 'flex', justifyContent: 'center',
        zIndex: 5, pointerEvents: 'none', opacity: pull > 4 || refreshing ? 1 : 0,
        transform: `translateY(${Math.max(pull - 36, 0)}px)`, transition: start.current == null ? 'transform .25s, opacity .2s' : 'none',
      }}>
        <div style={{
          width: 34, height: 34, borderRadius: 999, background: T.surface, border: `1px solid ${T.border}`,
          boxShadow: '0 4px 14px rgba(0,0,0,0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{ transform: `rotate(${refreshing ? 0 : prog * 270}deg)`, animation: refreshing ? 'fmspin 0.7s linear infinite' : 'none' }}>
            <FMIcon name="refresh" size={17} color={prog >= 1 || refreshing ? T.accent : T.mutedFg} />
          </div>
        </div>
      </div>
      <div ref={scrollRef} style={{
        flex: 1, minHeight: 0, overflowY: 'auto', WebkitOverflowScrolling: 'touch',
        transform: `translateY(${pull}px)`, transition: start.current == null ? 'transform .3s cubic-bezier(.2,.8,.2,1)' : 'none',
      }}>{children}</div>
    </div>
  );
}

// ── Infinite scroll sentinel ────────────────────────────────────────────────
function FMInfinite({ onLoad, hasMore, loading }) {
  const { T } = useFM();
  const ref = React.useRef(null);
  React.useEffect(() => {
    const el = ref.current; if (!el) return;
    const ob = new IntersectionObserver((es) => { if (es[0].isIntersecting && hasMore && !loading) onLoad(); }, { rootMargin: '200px' });
    ob.observe(el); return () => ob.disconnect();
  }, [onLoad, hasMore, loading]);
  return (
    <div ref={ref} style={{ padding: '18px 0 28px', display: 'flex', justifyContent: 'center' }}>
      {hasMore ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: T.mutedFg, fontSize: 13 }}>
          <div style={{ animation: 'fmspin 0.7s linear infinite', display: 'flex' }}><FMIcon name="refresh" size={15} color={T.mutedFg} /></div>
          読み込み中…
        </div>
      ) : <span style={{ color: T.mutedFg, fontSize: 12, fontFamily: 'Geist Mono, monospace' }}>— 最後まで読みました —</span>}
    </div>
  );
}

// ── Timeline card (cross-feed). variant: list | cards | magazine ────────────
function FMTimelineCard({ item, variant, onOpen, onStar }) {
  const { T } = useFM();
  const date = fmFormatDate(item.published_at);
  const dim = item.is_read ? 0.55 : 1;
  const source = (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
      <FMFavicon item={item} size={18} radius={5} />
      <span style={{ fontSize: 12, fontWeight: 600, color: T.mutedFg, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 150 }}>{item.feed_title}</span>
    </span>
  );

  if (variant === 'list') {
    return (
      <button type="button" onClick={() => onOpen(item)} style={{ ...rowBtn(T), gap: 12, opacity: dim }}>
        <FMFavicon item={item} size={32} radius={8} />
        <div style={{ flex: 1, minWidth: 0, textAlign: 'left' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span style={{ fontSize: 11, fontWeight: 600, color: T.mutedFg, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.feed_title}</span>
            <span style={{ fontSize: 11, color: T.mutedFg, marginLeft: 'auto', whiteSpace: 'nowrap' }}>{date}</span>
          </div>
          <div style={{ fontSize: 14.5, lineHeight: 1.4, fontWeight: item.is_read ? 400 : 600, color: T.fg, marginTop: 2, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.title}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6 }}>
            <FMHatebu item={item} compact />
            {item.matched_keyword && <FMKeywordTag term={item.matched_keyword} />}
          </div>
        </div>
        <div style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
          <FMStar on={item.is_starred} onClick={() => onStar(item)} size={18} />
          <FMOpenLink item={item} size={17} />
        </div>
      </button>
    );
  }

  if (variant === 'magazine') {
    return (
      <button type="button" onClick={() => onOpen(item)} style={{ ...cardBtn(T), opacity: dim, gap: 12, alignItems: 'stretch' }}>
        <div style={{ flex: 1, minWidth: 0, textAlign: 'left', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>{source}{item.matched_keyword && <span style={{ marginLeft: 'auto' }}><FMKeywordTag term={item.matched_keyword} /></span>}</div>
          <div style={{ fontSize: 17, lineHeight: 1.32, fontWeight: 700, color: T.fg, letterSpacing: '-0.01em', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.title}</div>
          <div style={{ fontSize: 13, lineHeight: 1.5, color: T.mutedFg, marginTop: 6, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.summary}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 'auto', paddingTop: 10 }}>
            <span style={{ fontSize: 11, color: T.mutedFg }}>{date}</span>
            <FMHatebu item={item} />
            <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6 }}><FMOpenLink item={item} /><FMStar on={item.is_starred} onClick={() => onStar(item)} /></span>
          </div>
        </div>
        <FMThumb w={104} h="auto" />
      </button>
    );
  }

  // cards (default)
  return (
    <button type="button" onClick={() => onOpen(item)} style={{ ...cardBtn(T), opacity: dim, flexDirection: 'column', alignItems: 'stretch', gap: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{source}<span style={{ fontSize: 11, color: T.mutedFg, marginLeft: 'auto', whiteSpace: 'nowrap' }}>{date}</span></div>
      <div style={{ fontSize: 15.5, lineHeight: 1.38, fontWeight: item.is_read ? 500 : 700, color: T.fg, textAlign: 'left', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.title}</div>
      {item.summary && <div style={{ fontSize: 13, lineHeight: 1.5, color: T.mutedFg, textAlign: 'left', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.summary}</div>}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <FMHatebu item={item} />
        {item.matched_keyword && <FMKeywordTag term={item.matched_keyword} />}
        <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6 }}><FMOpenLink item={item} /><FMStar on={item.is_starred} onClick={() => onStar(item)} /></span>
      </div>
    </button>
  );
}

// ── Article card (feed list / starred / search). variant: standard|compact|minimal ──
function FMArticleCard({ item, variant, onOpen, onStar, showSource }) {
  const { T } = useFM();
  const date = fmFormatDate(item.published_at);
  const dim = item.is_read ? 0.55 : 1;

  if (variant === 'compact') {
    return (
      <button type="button" onClick={() => onOpen(item)} style={{ ...rowBtn(T), gap: 10, padding: '10px 16px', opacity: dim }}>
        {!item.is_read && <span style={{ width: 7, height: 7, borderRadius: 999, background: T.accent, flexShrink: 0 }} />}
        {showSource && <FMFavicon item={item} size={18} radius={5} />}
        <span style={{ flex: 1, minWidth: 0, fontSize: 14, lineHeight: 1.35, fontWeight: item.is_read ? 400 : 600, color: T.fg, textAlign: 'left', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.title}</span>
        <span style={{ fontSize: 11, color: T.mutedFg, whiteSpace: 'nowrap' }}>{date}</span>
        {item.is_starred && <FMIcon name="star" size={15} fill={T.star} color={T.star} />}
        <FMOpenLink item={item} size={17} />
      </button>
    );
  }

  if (variant === 'minimal') {
    return (
      <button type="button" onClick={() => onOpen(item)} style={{ ...rowBtn(T), flexDirection: 'column', alignItems: 'stretch', gap: 7, padding: '16px 18px', opacity: dim }}>
        {showSource && <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><FMFavicon item={item} size={16} radius={4} /><span style={{ fontSize: 11, fontWeight: 600, color: T.mutedFg }}>{item.feed_title}</span></div>}
        <div style={{ fontSize: 16, lineHeight: 1.4, fontWeight: item.is_read ? 400 : 600, color: T.fg, textAlign: 'left', letterSpacing: '-0.01em' }}>{item.title}</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 11.5, color: T.mutedFg }}>{date}</span>
          <FMHatebu item={item} compact />
          {item.matched_keyword && <FMKeywordTag term={item.matched_keyword} />}
          <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6 }}><FMOpenLink item={item} size={18} /><FMStar on={item.is_starred} onClick={() => onStar(item)} size={18} /></span>
        </div>
      </button>
    );
  }

  // standard (default) — closest to web ItemRow
  return (
    <button type="button" onClick={() => onOpen(item)} style={{ ...rowBtn(T), flexDirection: 'column', alignItems: 'stretch', gap: 6, padding: '14px 16px', opacity: dim }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          {showSource && <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}><FMFavicon item={item} size={16} radius={4} /><span style={{ fontSize: 11, fontWeight: 600, color: T.mutedFg }}>{item.feed_title}</span></div>}
          <div style={{ fontSize: 15, lineHeight: 1.4, fontWeight: item.is_read ? 400 : 600, color: T.fg, textAlign: 'left', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.title}</div>
        </div>
        {item.is_starred && <FMIcon name="star" size={16} fill={T.star} color={T.star} style={{ marginTop: 2 }} />}
      </div>
      {item.summary && <div style={{ fontSize: 12.5, lineHeight: 1.5, color: T.mutedFg, textAlign: 'left', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.summary}</div>}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 1 }}>
        <span style={{ fontSize: 11.5, color: T.mutedFg, display: 'inline-flex', gap: 4, alignItems: 'center' }}>{date}{item.is_date_estimated && <span style={{ color: T.star }}>(推定)</span>}</span>
        <FMHatebu item={item} compact />
        {item.matched_keyword && <FMKeywordTag term={item.matched_keyword} />}
        <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6 }}><FMOpenLink item={item} size={18} /><FMStar on={item.is_starred} onClick={() => onStar(item)} size={18} /></span>
      </div>
    </button>
  );
}

function rowBtn(T) {
  return { display: 'flex', alignItems: 'center', width: '100%', background: 'none', border: 'none',
    borderBottom: `1px solid ${T.border}`, padding: '13px 16px', cursor: 'pointer', textAlign: 'left',
    fontFamily: 'Geist, system-ui, sans-serif', WebkitTapHighlightColor: 'transparent', color: T.fg };
}
function cardBtn(T) {
  return { display: 'flex', width: '100%', background: T.surface, border: `1px solid ${T.border}`,
    borderRadius: 16, padding: 14, cursor: 'pointer', textAlign: 'left',
    fontFamily: 'Geist, system-ui, sans-serif', WebkitTapHighlightColor: 'transparent', color: T.fg,
    boxShadow: '0 1px 2px rgba(0,0,0,0.04)' };
}

Object.assign(window, {
  FMCtx, useFM, FMFavicon, FMThumb, FMStar, FMOpenLink, FMHatebu, FMKeywordTag, FMUnread,
  FMPullToRefresh, FMInfinite, FMTimelineCard, FMArticleCard, rowBtn, cardBtn,
});
