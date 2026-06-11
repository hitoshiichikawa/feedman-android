// fm-screens.jsx — chrome (header, drawer, bottom tabs) + main screens.
// Exports: FMHeader, FMDrawer, FMFeedListBody, FMBottomTabs, FMTimelineScreen,
// FMFeedScreen, FMStarredScreen, FMSearchScreen, FMEmpty, FMScrim.

function FMScrim({ show, onClick }) {
  const { T } = useFM();
  return (
    <div onClick={onClick} style={{
      position: 'absolute', inset: 0, background: T.scrim, zIndex: 40,
      opacity: show ? 1 : 0, pointerEvents: show ? 'auto' : 'none', transition: 'opacity .25s',
    }} />
  );
}

function FMEmpty({ icon, title, sub }) {
  const { T } = useFM();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, height: '100%', padding: 40, textAlign: 'center' }}>
      <div style={{ width: 56, height: 56, borderRadius: 16, background: T.muted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <FMIcon name={icon} size={26} color={T.mutedFg} />
      </div>
      <div style={{ fontSize: 15, fontWeight: 600, color: T.fg }}>{title}</div>
      {sub && <div style={{ fontSize: 13, color: T.mutedFg, lineHeight: 1.6, maxWidth: 240 }}>{sub}</div>}
    </div>
  );
}

// ── Top app bar ─────────────────────────────────────────────────────────────
function FMHeader({ title, sub }) {
  const { T, nav, actions, tweaks } = useFM();
  const drawer = tweaks.nav === 'drawer';
  return (
    <header style={{
      display: 'flex', alignItems: 'center', gap: 6, padding: '6px 8px 6px 6px',
      background: T.bg, borderBottom: `1px solid ${T.border}`, flexShrink: 0, minHeight: 52,
    }}>
      {drawer ? (
        <button type="button" aria-label="メニュー" onClick={actions.openDrawer} style={iconBtn(T)}>
          <FMIcon name="menu" size={22} color={T.fg} />
        </button>
      ) : <div style={{ width: 8 }} />}
      <div style={{ flex: 1, minWidth: 0, paddingLeft: drawer ? 0 : 6 }}>
        <div style={{ fontSize: 18, fontWeight: 700, color: T.fg, letterSpacing: '-0.02em', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>
        {sub && <div style={{ fontSize: 11.5, color: T.mutedFg, marginTop: -1 }}>{sub}</div>}
      </div>
      <button type="button" aria-label="検索" onClick={() => actions.setView('search')} style={iconBtn(T)}>
        <FMIcon name="search" size={21} color={T.fg} />
      </button>
      <button type="button" aria-label="テーマ切替" onClick={actions.toggleTheme} style={iconBtn(T)}>
        <FMIcon name={T.statusDark ? 'sun' : 'moon'} size={20} color={T.fg} />
      </button>
    </header>
  );
}

// ── Feed list body (shared by drawer + bottom-tabs feed screen) ─────────────
function FMFeedListBody({ onPick }) {
  const { T, nav, actions, data } = useFM();
  const total = data.feeds.reduce((s, f) => s + f.unread_count, 0);
  const navItem = (active, color, icon, label, count, onClick, letter) => (
    <button type="button" onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 12, width: '100%', padding: '11px 14px',
      background: active ? T.accentSoft : 'none', border: 'none', borderRadius: 12, cursor: 'pointer',
      color: T.fg, fontFamily: 'Geist, system-ui', WebkitTapHighlightColor: 'transparent',
    }}>
      <div style={{ width: 30, height: 30, borderRadius: 9, background: color, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {letter ? <span style={{ color: '#fff', fontWeight: 700, fontSize: 14 }}>{letter}</span> : <FMIcon name={icon} size={17} color="#fff" />}
      </div>
      <span style={{ flex: 1, minWidth: 0, fontSize: 14.5, fontWeight: active ? 700 : 500, textAlign: 'left', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{label}</span>
      {count > 0 && <FMUnread n={count} />}
    </button>
  );
  return (
    <div style={{ padding: '8px 8px 16px' }}>
      {navItem(nav.view === 'timeline', T.accent, 'sparkles', 'すべての新着', total, () => onPick(() => actions.setView('timeline')))}
      {navItem(nav.view === 'starred', T.star, 'star', 'お気に入り', 0, () => onPick(() => actions.setView('starred')))}

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 14px 6px' }}>
        <span style={{ fontSize: 11.5, fontWeight: 700, color: T.mutedFg, letterSpacing: '0.06em', textTransform: 'uppercase' }}>フィード</span>
        <button type="button" aria-label="フィードを登録" onClick={() => onPick(() => actions.openSheet('register'))} style={{ ...iconBtn(T), width: 30, height: 30 }}>
          <FMIcon name="plus" size={18} color={T.accent} />
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {data.feeds.map((f) => {
          const active = nav.view === 'feed' && nav.feedId === f.feed_id;
          return (
            <div key={f.id} style={{
              display: 'flex', alignItems: 'center', gap: 11, padding: '9px 14px', borderRadius: 12,
              background: active ? T.accentSoft : 'none', cursor: 'pointer',
            }} onClick={() => onPick(() => actions.setView('feed', { feedId: f.feed_id }))}>
              <FMFavicon item={f} size={28} radius={8} />
              <span style={{ flex: 1, minWidth: 0, fontSize: 14, fontWeight: active ? 700 : 500, color: T.fg, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.feed_title}</span>
              {f.feed_status === 'stopped' && <FMIcon name="pause" size={16} color={T.mutedFg} />}
              {f.feed_status === 'error' && <FMIcon name="alert" size={16} color={T.danger} />}
              <FMUnread n={f.unread_count} />
              <button type="button" aria-label="設定" onClick={(e) => { e.stopPropagation(); onPick(() => actions.openSheet('settings', f)); }} style={{ ...iconBtn(T), width: 28, height: 28, opacity: 0.65 }}>
                <FMIcon name="settings" size={15} color={T.mutedFg} />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── Navigation drawer ───────────────────────────────────────────────────────
function FMDrawer() {
  const { T, actions, tweaks, data, safe } = useFM();
  const open = actions.drawerOpen && tweaks.nav === 'drawer';
  const pick = (fn) => { fn(); actions.closeDrawer(); };
  return (
    <React.Fragment>
      <FMScrim show={open} onClick={actions.closeDrawer} />
      <aside style={{
        position: 'absolute', top: 0, bottom: 0, left: 0, width: 312, zIndex: 50, background: T.bg,
        borderRight: `1px solid ${T.border}`, transform: open ? 'translateX(0)' : 'translateX(-104%)',
        transition: 'transform .28s cubic-bezier(.2,.8,.2,1)', display: 'flex', flexDirection: 'column',
        boxShadow: open ? '4px 0 30px rgba(0,0,0,0.18)' : 'none',
      }}>
        <div style={{ padding: '18px 18px 14px', paddingTop: 18 + ((safe && safe.top) || 0), borderBottom: `1px solid ${T.border}` }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 34, height: 34, borderRadius: 10, background: T.accent, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <FMIcon name="rss" size={18} color={T.accentOn} />
            </div>
            <div style={{ fontSize: 19, fontWeight: 800, color: T.fg, letterSpacing: '-0.03em' }}>Feedman</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12 }}>
            <div style={{ width: 26, height: 26, borderRadius: 999, background: T.muted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><FMIcon name="user" size={15} color={T.mutedFg} /></div>
            <span style={{ fontSize: 12.5, color: T.mutedFg }}>you@example.com</span>
          </div>
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          <FMFeedListBody onPick={pick} />
        </div>

        <div style={{ borderTop: `1px solid ${T.border}`, padding: 8, display: 'flex', flexDirection: 'column', gap: 1 }}>
          {[['bell', 'キーワード通知', () => pick(() => actions.openSheet('keyword'))],
            ['user', 'アカウント', () => pick(() => actions.openSheet('account'))],
            [T.statusDark ? 'sun' : 'moon', T.statusDark ? 'ライトモード' : 'ダークモード', () => actions.toggleTheme()]].map(([ic, lb, fn]) => (
            <button key={lb} type="button" onClick={fn} style={drawerFootBtn(T)}>
              <FMIcon name={ic} size={18} color={T.mutedFg} /><span>{lb}</span>
              {ic === 'bell' && <span style={{ marginLeft: 'auto' }}><FMUnread n={data.keywords.filter((k) => k.enabled).length} /></span>}
            </button>
          ))}
        </div>
      </aside>
    </React.Fragment>
  );
}

// ── Bottom tab bar (alternative nav) ────────────────────────────────────────
function FMBottomTabs() {
  const { T, nav, actions, tweaks, safe } = useFM();
  if (tweaks.nav !== 'bottomtabs') return null;
  const tabs = [
    ['新着', 'sparkles', 'timeline'],
    ['スター', 'star', 'starred'],
    ['検索', 'search', 'search'],
    ['フィード', 'inbox', 'feeds'],
  ];
  return (
    <nav style={{ display: 'flex', borderTop: `1px solid ${T.border}`, background: T.bg, flexShrink: 0, paddingBottom: (safe && safe.bottom) || 0 }}>
      {tabs.map(([label, icon, view]) => {
        const active = nav.view === view || (view === 'feeds' && nav.view === 'feed');
        return (
          <button key={view} type="button" onClick={() => actions.setView(view)} style={{
            flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3, padding: '8px 0 7px',
            background: 'none', border: 'none', cursor: 'pointer', color: active ? T.accent : T.mutedFg,
            fontFamily: 'Geist, system-ui', WebkitTapHighlightColor: 'transparent',
          }}>
            <FMIcon name={icon} size={22} color={active ? T.accent : T.mutedFg} fill={active && icon === 'star' ? T.accent : 'none'} />
            <span style={{ fontSize: 11, fontWeight: active ? 700 : 500 }}>{label}</span>
          </button>
        );
      })}
    </nav>
  );
}

// ── Timeline (cross-feed) ───────────────────────────────────────────────────
function FMTimelineScreen() {
  const { T, actions, data, tweaks } = useFM();
  const scrollRef = React.useRef(null);
  const [count, setCount] = React.useState(8);
  const items = data.items;
  const shown = items.slice(0, count);
  const layout = tweaks.timeline; // list | cards | magazine
  const padded = layout !== 'list';
  return (
    <FMPullToRefresh onRefresh={actions.refresh} enabled={tweaks.pull} scrollRef={scrollRef}>
      <div style={{ padding: padded ? '12px 14px 0' : 0, display: 'flex', flexDirection: 'column', gap: padded ? 12 : 0 }}>
        {shown.map((it) => (
          <FMTimelineCard key={it.id} item={it} variant={layout} onOpen={actions.openDetail} onStar={actions.toggleStar} />
        ))}
      </div>
      <FMInfinite hasMore={count < items.length} loading={false} onLoad={() => setCount((c) => Math.min(c + 6, items.length))} />
    </FMPullToRefresh>
  );
}

// ── Filter tabs ─────────────────────────────────────────────────────────────
function FMFilterTabs() {
  const { T, nav, actions } = useFM();
  const tabs = [['all', 'すべて'], ['unread', '未読'], ['starred', 'スター']];
  return (
    <div style={{ display: 'flex', gap: 6, padding: '8px 14px', borderBottom: `1px solid ${T.border}`, background: T.bg }}>
      {tabs.map(([k, label]) => {
        const active = nav.filter === k;
        return (
          <button key={k} type="button" onClick={() => actions.setFilter(k)} style={{
            padding: '6px 14px', borderRadius: 999, border: `1px solid ${active ? 'transparent' : T.border}`,
            background: active ? T.fg : 'transparent', color: active ? T.bg : T.mutedFg,
            fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'Geist, system-ui',
            WebkitTapHighlightColor: 'transparent',
          }}>{label}</button>
        );
      })}
    </div>
  );
}

// ── Feed-specific item list ─────────────────────────────────────────────────
function FMFeedScreen() {
  const { T, nav, actions, data, tweaks } = useFM();
  const scrollRef = React.useRef(null);
  const feed = data.feeds.find((f) => f.feed_id === nav.feedId);
  let items = data.items.filter((i) => i.feed_id === nav.feedId);
  if (nav.filter === 'unread') items = items.filter((i) => !i.is_read);
  if (nav.filter === 'starred') items = items.filter((i) => i.is_starred);
  const [count, setCount] = React.useState(10);
  const shown = items.slice(0, count);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
      {feed && feed.feed_status !== 'active' && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', background: T.accentSoft, fontSize: 12.5, color: T.fg, borderBottom: `1px solid ${T.border}` }}>
          <FMIcon name={feed.feed_status === 'error' ? 'alert' : 'pause'} size={16} color={feed.feed_status === 'error' ? T.danger : T.mutedFg} />
          <span style={{ flex: 1 }}>{feed.error_message}</span>
          <button type="button" onClick={() => actions.openSheet('settings', feed)} style={{ fontSize: 12.5, fontWeight: 700, color: T.accent, background: 'none', border: 'none', cursor: 'pointer' }}>再開</button>
        </div>
      )}
      <FMFilterTabs />
      <FMPullToRefresh onRefresh={actions.refresh} enabled={tweaks.pull} scrollRef={scrollRef}>
        {shown.length === 0 ? <FMEmpty icon="inbox" title="記事がありません" sub="フィルタを変えるか、引っ張って更新してください" /> : (
          <React.Fragment>
            {shown.map((it) => <FMArticleCard key={it.id} item={it} variant={tweaks.card} onOpen={actions.openDetail} onStar={actions.toggleStar} />)}
            <FMInfinite hasMore={count < items.length} loading={false} onLoad={() => setCount((c) => Math.min(c + 8, items.length))} />
          </React.Fragment>
        )}
      </FMPullToRefresh>
    </div>
  );
}

// ── Starred (cross-feed) ────────────────────────────────────────────────────
function FMStarredScreen() {
  const { actions, data, tweaks } = useFM();
  const scrollRef = React.useRef(null);
  const items = data.items.filter((i) => i.is_starred);
  return (
    <FMPullToRefresh onRefresh={actions.refresh} enabled={tweaks.pull} scrollRef={scrollRef}>
      {items.length === 0 ? <FMEmpty icon="star" title="スターした記事はありません" sub="記事のスターを付けると、ここに集まります" /> :
        items.map((it) => <FMArticleCard key={it.id} item={it} variant={tweaks.card} onOpen={actions.openDetail} onStar={actions.toggleStar} showSource />)}
    </FMPullToRefresh>
  );
}

// ── Search ──────────────────────────────────────────────────────────────────
function FMSearchScreen() {
  const { T, nav, actions, data, tweaks } = useFM();
  const [q, setQ] = React.useState(nav.query || '');
  const inputRef = React.useRef(null);
  React.useEffect(() => { if (inputRef.current) inputRef.current.focus(); }, []);
  const query = q.trim();
  const results = query ? data.items.filter((i) => i.title.includes(query) || i.summary.includes(query)) : [];
  const suggestions = ['Go', 'Kubernetes', 'OpenAI', 'TypeScript', 'Rust'];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
      <div style={{ padding: '8px 8px', borderBottom: `1px solid ${T.border}`, background: T.bg, display: 'flex', alignItems: 'center', gap: 4 }}>
        <button type="button" aria-label="戻る" onClick={() => actions.setView('timeline')} style={iconBtn(T)}>
          <FMIcon name="arrowLeft" size={21} color={T.fg} />
        </button>
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8, background: T.muted, borderRadius: 12, padding: '0 12px', height: 44 }}>
          <FMIcon name="search" size={18} color={T.mutedFg} />
          <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="購読中のフィードを横断検索"
            style={{ flex: 1, border: 'none', outline: 'none', background: 'none', fontSize: 15, color: T.fg, fontFamily: 'Geist, system-ui' }} />
          {q && <button type="button" onClick={() => setQ('')} aria-label="クリア" style={iconBtn(T)}><FMIcon name="x" size={16} color={T.mutedFg} /></button>}
        </div>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        {!query ? (
          <div style={{ padding: 18 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: T.mutedFg, letterSpacing: '0.05em', marginBottom: 10 }}>よく検索されるキーワード</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {suggestions.map((s) => (
                <button key={s} type="button" onClick={() => setQ(s)} style={{
                  padding: '7px 14px', borderRadius: 999, border: `1px solid ${T.border}`, background: T.surface,
                  fontSize: 13, color: T.fg, cursor: 'pointer', fontFamily: 'Geist, system-ui',
                }}>{s}</button>
              ))}
            </div>
          </div>
        ) : results.length === 0 ? (
          <FMEmpty icon="search" title={`「${query}」に一致する記事はありません`} sub="別のキーワードでお試しください" />
        ) : (
          <React.Fragment>
            <div style={{ padding: '10px 16px 4px', fontSize: 12, color: T.mutedFg }}>{results.length} 件の記事</div>
            {results.map((it) => <FMArticleCard key={it.id} item={it} variant={tweaks.card} onOpen={actions.openDetail} onStar={actions.toggleStar} showSource />)}
          </React.Fragment>
        )}
      </div>
    </div>
  );
}

function iconBtn(T) {
  return { width: 40, height: 40, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    background: 'none', border: 'none', borderRadius: 999, cursor: 'pointer', flexShrink: 0,
    WebkitTapHighlightColor: 'transparent' };
}
function drawerFootBtn(T) {
  return { display: 'flex', alignItems: 'center', gap: 12, width: '100%', padding: '11px 14px',
    background: 'none', border: 'none', borderRadius: 12, cursor: 'pointer', color: T.fg, fontSize: 14,
    fontWeight: 500, fontFamily: 'Geist, system-ui', WebkitTapHighlightColor: 'transparent' };
}

Object.assign(window, {
  FMScrim, FMEmpty, FMHeader, FMFeedListBody, FMDrawer, FMBottomTabs,
  FMTimelineScreen, FMFilterTabs, FMFeedScreen, FMStarredScreen, FMSearchScreen, iconBtn, drawerFootBtn,
});
