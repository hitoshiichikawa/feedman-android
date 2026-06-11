// fm-sheets.jsx — bottom sheets, dialogs, login.
// Exports: FMSheet, FMDetailSheet, FMRegisterSheet, FMSettingsSheet,
// FMKeywordSheet, FMAccountSheet, FMLogin, FMToast.

// ── Bottom-sheet shell (drag handle, scrim, slide-up) ───────────────────────
function FMSheet({ open, onClose, children, heightPct = 62, full, label }) {
  const { T, safe } = useFM();
  const [drag, setDrag] = React.useState(0);
  const startY = React.useRef(null);
  const onDown = (y) => { startY.current = y; };
  const onMove = (y) => { if (startY.current != null) { const d = y - startY.current; if (d > 0) setDrag(d); } };
  const onUp = () => { if (startY.current != null) { if (drag > 90) onClose(); setDrag(0); startY.current = null; } };
  const h = full ? '94%' : heightPct + '%';
  return (
    <React.Fragment>
      <div onClick={onClose} style={{ position: 'absolute', inset: 0, background: T.scrim, zIndex: 60, opacity: open ? 1 : 0, pointerEvents: open ? 'auto' : 'none', transition: 'opacity .25s' }} />
      <div role="dialog" aria-label={label} style={{
        position: 'absolute', left: 0, right: 0, bottom: 0, zIndex: 61, height: h, maxHeight: '94%',
        background: T.bg, borderTopLeftRadius: 22, borderTopRightRadius: 22, display: 'flex', flexDirection: 'column',
        transform: open ? `translateY(${drag}px)` : 'translateY(101%)',
        transition: startY.current == null ? 'transform .3s cubic-bezier(.2,.85,.25,1)' : 'none',
        boxShadow: '0 -8px 40px rgba(0,0,0,0.22)', overflow: 'hidden', paddingBottom: (safe && safe.bottom) || 0,
      }}>
        <div onTouchStart={(e) => onDown(e.touches[0].clientY)} onTouchMove={(e) => onMove(e.touches[0].clientY)} onTouchEnd={onUp}
          onMouseDown={(e) => onDown(e.clientY)} onMouseMove={(e) => onMove(e.clientY)} onMouseUp={onUp} onMouseLeave={onUp}
          style={{ padding: '10px 0 6px', display: 'flex', justifyContent: 'center', flexShrink: 0, cursor: 'grab' }}>
          <div style={{ width: 40, height: 5, borderRadius: 999, background: T.borderStrong }} />
        </div>
        {children}
      </div>
    </React.Fragment>
  );
}

function sheetClose(T, onClose) {
  return (
    <button type="button" aria-label="閉じる" onClick={onClose} style={{ ...iconBtn(T), width: 34, height: 34, background: T.muted }}>
      <FMIcon name="x" size={18} color={T.fg} />
    </button>
  );
}

// ── Article detail. variant: partial | full | reader ────────────────────────
function FMDetailSheet({ item, onClose }) {
  const { T, actions, tweaks } = useFM();
  const variant = tweaks.detail;
  const [expanded, setExpanded] = React.useState(variant !== 'partial');
  React.useEffect(() => { setExpanded(variant !== 'partial'); }, [item && item.id, variant]);
  if (!item) return null;
  const reader = variant === 'reader';
  const height = variant === 'partial' ? 64 : 90;
  const pad = reader ? 24 : 18;
  return (
    <FMSheet open={!!item} onClose={onClose} heightPct={height} full={variant !== 'partial'} label="記事の詳細">
      {/* source row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: `0 14px 12px`, flexShrink: 0 }}>
        <FMFavicon item={item} size={26} radius={7} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.fg, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.feed_title}</div>
          <div style={{ fontSize: 11.5, color: T.mutedFg }}>{fmFormatDate(item.published_at)}{item.author && ' · ' + item.author}</div>
        </div>
        {sheetClose(T, onClose)}
      </div>

      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: `0 ${pad}px` }}>
        <h1 style={{ fontSize: reader ? 26 : 21, lineHeight: 1.3, fontWeight: 800, color: T.fg, margin: '4px 0 14px', letterSpacing: '-0.02em' }}>{item.title}</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, paddingBottom: 16, borderBottom: `1px solid ${T.border}`, marginBottom: 16 }}>
          <FMHatebu item={item} />
          {item.matched_keyword && <FMKeywordTag term={item.matched_keyword} />}
          <span style={{ marginLeft: 'auto' }}><FMStar on={item.is_starred} onClick={() => actions.toggleStar(item)} size={22} /></span>
        </div>
        <div className="fm-prose" style={{
          color: T.fg, fontSize: reader ? 16.5 : 15, lineHeight: reader ? 1.85 : 1.7,
          '--fm-accent': T.accent, '--fm-muted': T.mutedFg, '--fm-border': T.border, '--fm-fg': T.fg,
          maxHeight: expanded ? 'none' : 200, overflow: 'hidden', position: 'relative',
        }} dangerouslySetInnerHTML={{ __html: item.content }} />
        {!expanded && (
          <div style={{ position: 'relative', marginTop: -56, height: 56, background: `linear-gradient(to top, ${T.bg}, transparent)`, pointerEvents: 'none' }} />
        )}
        {variant === 'partial' && (
          <button type="button" onClick={() => setExpanded((e) => !e)} style={{
            margin: '8px 0 4px', padding: '9px 16px', borderRadius: 999, border: `1px solid ${T.border}`,
            background: T.surface, color: T.fg, fontSize: 13.5, fontWeight: 600, cursor: 'pointer',
            display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'Geist, system-ui',
          }}>{expanded ? '折りたたむ' : '続きを読む'}<FMIcon name={expanded ? 'chevronDown' : 'chevronRight'} size={15} color={T.fg} /></button>
        )}
        <div style={{ height: 24 }} />
      </div>

      {/* action bar */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderTop: `1px solid ${T.border}`, flexShrink: 0, background: T.bg }}>
        <button type="button" onClick={() => { actions.markRead(item); actions.toast('外部ブラウザで開きました'); }} style={{
          flex: 1, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, height: 46,
          borderRadius: 13, border: 'none', background: T.accent, color: T.accentOn, fontSize: 15, fontWeight: 700,
          cursor: 'pointer', fontFamily: 'Geist, system-ui',
        }}><FMIcon name="external" size={18} color={T.accentOn} />元記事を開く</button>
        <button type="button" aria-label="スター" onClick={() => actions.toggleStar(item)} style={actionSquare(T)}>
          <FMIcon name="star" size={20} fill={item.is_starred ? T.star : 'none'} color={item.is_starred ? T.star : T.fg} />
        </button>
      </div>
    </FMSheet>
  );
}
function actionSquare(T) {
  return { width: 46, height: 46, borderRadius: 13, border: `1px solid ${T.border}`, background: T.surface,
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0 };
}

// ── Feed registration ───────────────────────────────────────────────────────
function FMRegisterSheet({ onClose }) {
  const { T, actions } = useFM();
  const [url, setUrl] = React.useState('');
  const [stage, setStage] = React.useState('input'); // input | loading | done
  const submit = () => { if (!url.trim()) return; setStage('loading'); setTimeout(() => setStage('done'), 900); };
  return (
    <FMSheet open onClose={onClose} heightPct={50} label="フィードを登録">
      <div style={{ padding: '0 18px 18px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <h2 style={{ flex: 1, fontSize: 18, fontWeight: 700, color: T.fg, margin: 0 }}>フィードを登録</h2>
          {sheetClose(T, onClose)}
        </div>
        <p style={{ fontSize: 13, color: T.mutedFg, lineHeight: 1.6, margin: 0 }}>サイトの URL か RSS/Atom の URL を入力してください。フィードは自動で検出されます。</p>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: T.muted, borderRadius: 12, padding: '0 12px', height: 48 }}>
          <FMIcon name="rss" size={18} color={T.mutedFg} />
          <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com" inputMode="url"
            style={{ flex: 1, border: 'none', outline: 'none', background: 'none', fontSize: 15, color: T.fg, fontFamily: 'Geist, system-ui' }} />
        </div>
        {stage === 'done' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 14, borderRadius: 13, background: T.accentSoft }}>
            <div style={{ width: 30, height: 30, borderRadius: 999, background: T.accent, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><FMIcon name="check" size={18} color={T.accentOn} /></div>
            <div style={{ flex: 1 }}><div style={{ fontSize: 14, fontWeight: 700, color: T.fg }}>Example Blog</div><div style={{ fontSize: 12, color: T.mutedFg }}>フィードを検出しました</div></div>
          </div>
        ) : (
          <button type="button" onClick={submit} disabled={stage === 'loading'} style={{
            height: 48, borderRadius: 13, border: 'none', background: T.accent, color: T.accentOn, fontSize: 15,
            fontWeight: 700, cursor: 'pointer', fontFamily: 'Geist, system-ui', opacity: stage === 'loading' ? 0.7 : 1,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          }}>{stage === 'loading' ? <React.Fragment><span style={{ animation: 'fmspin .7s linear infinite', display: 'flex' }}><FMIcon name="refresh" size={17} color={T.accentOn} /></span>検出中…</React.Fragment> : 'フィードを検出して登録'}</button>
        )}
        {stage === 'done' && <button type="button" onClick={() => { actions.toast('フィードを登録しました'); onClose(); }} style={{ height: 46, borderRadius: 13, border: `1px solid ${T.border}`, background: T.surface, color: T.fg, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>このフィードを登録</button>}
      </div>
    </FMSheet>
  );
}

// ── Subscription settings ───────────────────────────────────────────────────
function FMSettingsSheet({ feed, onClose }) {
  const { T, actions } = useFM();
  const intervals = [15, 30, 60, 180, 360];
  const [iv, setIv] = React.useState(feed ? feed.fetch_interval_minutes : 30);
  if (!feed) return null;
  const label = (m) => m < 60 ? m + '分' : (m / 60) + '時間';
  return (
    <FMSheet open onClose={onClose} heightPct={66} label="購読設定">
      <div style={{ padding: '0 18px 18px', overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingBottom: 16, borderBottom: `1px solid ${T.border}` }}>
          <FMFavicon item={feed} size={40} radius={11} />
          <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontSize: 16, fontWeight: 700, color: T.fg }}>{feed.feed_title}</div><div style={{ fontSize: 12, color: T.mutedFg }}>未読 {feed.unread_count} 件</div></div>
          {sheetClose(T, onClose)}
        </div>

        {feed.feed_status !== 'active' && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 12, marginTop: 14, borderRadius: 13, background: T.accentSoft }}>
            <FMIcon name={feed.feed_status === 'error' ? 'alert' : 'pause'} size={18} color={feed.feed_status === 'error' ? T.danger : T.mutedFg} />
            <span style={{ flex: 1, fontSize: 12.5, color: T.fg }}>{feed.error_message}</span>
            <button type="button" onClick={() => { actions.toast('フィードを再開しました'); onClose(); }} style={{ padding: '7px 14px', borderRadius: 999, border: 'none', background: T.accent, color: T.accentOn, fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>再開</button>
          </div>
        )}

        <div style={{ marginTop: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <span style={{ fontSize: 13.5, fontWeight: 700, color: T.fg }}>フェッチ間隔</span>
            <span style={{ fontSize: 13.5, fontWeight: 700, color: T.accent }}>{label(iv)}ごと</span>
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            {intervals.map((m) => (
              <button key={m} type="button" onClick={() => setIv(m)} style={{
                flex: 1, padding: '10px 0', borderRadius: 11, border: `1px solid ${iv === m ? 'transparent' : T.border}`,
                background: iv === m ? T.accent : T.surface, color: iv === m ? T.accentOn : T.mutedFg,
                fontSize: 12.5, fontWeight: 700, cursor: 'pointer', fontFamily: 'Geist, system-ui',
              }}>{label(m)}</button>
            ))}
          </div>
        </div>

        <button type="button" onClick={() => { actions.toast('設定を保存しました'); onClose(); }} style={{ marginTop: 20, width: '100%', height: 48, borderRadius: 13, border: 'none', background: T.accent, color: T.accentOn, fontSize: 15, fontWeight: 700, cursor: 'pointer', fontFamily: 'Geist, system-ui' }}>保存</button>
        <button type="button" onClick={() => { actions.toast('購読を解除しました'); onClose(); }} style={{ marginTop: 10, width: '100%', height: 46, borderRadius: 13, border: `1px solid ${T.border}`, background: 'none', color: T.danger, fontSize: 14, fontWeight: 700, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontFamily: 'Geist, system-ui' }}><FMIcon name="trash" size={17} color={T.danger} />購読を解除</button>
      </div>
    </FMSheet>
  );
}

// ── Keyword push notifications (NEW server API) ─────────────────────────────
function FMKeywordSheet({ onClose }) {
  const { T, actions, data } = useFM();
  const [list, setList] = React.useState(data.keywords);
  const [term, setTerm] = React.useState('');
  const [push, setPush] = React.useState(true);
  const add = () => { if (!term.trim()) return; setList((l) => [{ id: 'k' + Date.now(), term: term.trim(), enabled: true, scope: 'title', hits: 0 }, ...l]); setTerm(''); actions.toast('キーワードを追加しました'); };
  const toggle = (id) => setList((l) => l.map((k) => k.id === id ? { ...k, enabled: !k.enabled } : k));
  const del = (id) => setList((l) => l.filter((k) => k.id !== id));
  return (
    <FMSheet open onClose={onClose} heightPct={84} full label="キーワード通知">
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '0 16px 12px', flexShrink: 0 }}>
        <div style={{ width: 34, height: 34, borderRadius: 10, background: T.accent, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><FMIcon name="bell" size={18} color={T.accentOn} /></div>
        <div style={{ flex: 1 }}><div style={{ fontSize: 17, fontWeight: 700, color: T.fg }}>キーワード通知</div></div>
        {sheetClose(T, onClose)}
      </div>
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '0 16px 18px' }}>
        <div style={{ display: 'flex', gap: 8, padding: 12, borderRadius: 13, background: T.accentSoft, marginBottom: 16 }}>
          <FMIcon name="sparkles" size={16} color={T.accent} style={{ marginTop: 2 }} />
          <span style={{ fontSize: 12, color: T.fg, lineHeight: 1.6 }}>登録したキーワードを記事タイトルに含む新着があると、通勤前でもプッシュでお知らせします。<b style={{ color: T.accent }}>※サーバー側の新規機能が必要です（後述の提案参照）。</b></span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 4px', borderBottom: `1px solid ${T.border}` }}>
          <div><div style={{ fontSize: 14, fontWeight: 600, color: T.fg }}>プッシュ通知</div><div style={{ fontSize: 12, color: T.mutedFg }}>一致した記事を端末に通知</div></div>
          <Toggle on={push} onClick={() => setPush((p) => !p)} />
        </div>

        <div style={{ display: 'flex', gap: 8, margin: '16px 0' }}>
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8, background: T.muted, borderRadius: 12, padding: '0 12px', height: 46 }}>
            <FMIcon name="plus" size={17} color={T.mutedFg} />
            <input value={term} onChange={(e) => setTerm(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && add()} placeholder="キーワードを追加（例: Rust）"
              style={{ flex: 1, border: 'none', outline: 'none', background: 'none', fontSize: 14, color: T.fg, fontFamily: 'Geist, system-ui' }} />
          </div>
          <button type="button" onClick={add} style={{ padding: '0 18px', borderRadius: 12, border: 'none', background: T.accent, color: T.accentOn, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>追加</button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {list.map((k) => (
            <div key={k.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', borderRadius: 13, border: `1px solid ${T.border}`, background: T.surface, opacity: k.enabled ? 1 : 0.55 }}>
              <FMIcon name="bell" size={17} color={k.enabled ? T.accent : T.mutedFg} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14.5, fontWeight: 700, color: T.fg }}>{k.term}</div>
                <div style={{ fontSize: 11.5, color: T.mutedFg }}>タイトル一致 · 過去7日で {k.hits} 件</div>
              </div>
              <Toggle on={k.enabled} onClick={() => toggle(k.id)} small />
              <button type="button" aria-label="削除" onClick={() => del(k.id)} style={{ ...iconBtn(T), width: 32, height: 32 }}><FMIcon name="trash" size={16} color={T.mutedFg} /></button>
            </div>
          ))}
        </div>
      </div>
    </FMSheet>
  );
}

function Toggle({ on, onClick, small }) {
  const { T } = useFM();
  const w = small ? 40 : 46, h = small ? 24 : 28, k = h - 6;
  return (
    <button type="button" role="switch" aria-checked={on} onClick={onClick} style={{
      width: w, height: h, borderRadius: 999, border: 'none', cursor: 'pointer', padding: 3,
      background: on ? T.accent : T.borderStrong, transition: 'background .2s', flexShrink: 0,
    }}>
      <div style={{ width: k, height: k, borderRadius: 999, background: '#fff', transform: on ? `translateX(${w - k - 6}px)` : 'translateX(0)', transition: 'transform .2s', boxShadow: '0 1px 3px rgba(0,0,0,0.3)' }} />
    </button>
  );
}

// ── Account ─────────────────────────────────────────────────────────────────
function FMAccountSheet({ onClose }) {
  const { T, actions } = useFM();
  const [confirm, setConfirm] = React.useState(false);
  return (
    <FMSheet open onClose={onClose} heightPct={44} label="アカウント">
      <div style={{ padding: '0 18px 18px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingBottom: 16, borderBottom: `1px solid ${T.border}` }}>
          <div style={{ width: 44, height: 44, borderRadius: 999, background: T.muted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><FMIcon name="user" size={22} color={T.mutedFg} /></div>
          <div style={{ flex: 1 }}><div style={{ fontSize: 15, fontWeight: 700, color: T.fg }}>You</div><div style={{ fontSize: 12.5, color: T.mutedFg }}>you@example.com</div></div>
          {sheetClose(T, onClose)}
        </div>
        <button type="button" onClick={() => actions.logout()} style={{ marginTop: 16, width: '100%', height: 46, borderRadius: 13, border: `1px solid ${T.border}`, background: T.surface, color: T.fg, fontSize: 14, fontWeight: 700, cursor: 'pointer', fontFamily: 'Geist, system-ui' }}>ログアウト</button>
        {!confirm ? (
          <button type="button" onClick={() => setConfirm(true)} style={{ marginTop: 10, width: '100%', height: 46, borderRadius: 13, border: 'none', background: 'none', color: T.danger, fontSize: 13.5, fontWeight: 700, cursor: 'pointer' }}>退会（アカウント削除）</button>
        ) : (
          <div style={{ marginTop: 14, padding: 14, borderRadius: 13, border: `1px solid ${T.danger}` }}>
            <div style={{ fontSize: 13, color: T.fg, lineHeight: 1.6, marginBottom: 10 }}>すべての購読と記事状態が削除されます。この操作は取り消せません。</div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button type="button" onClick={() => setConfirm(false)} style={{ flex: 1, height: 42, borderRadius: 11, border: `1px solid ${T.border}`, background: T.surface, color: T.fg, fontSize: 13.5, fontWeight: 700, cursor: 'pointer' }}>キャンセル</button>
              <button type="button" onClick={() => actions.logout()} style={{ flex: 1, height: 42, borderRadius: 11, border: 'none', background: T.danger, color: '#fff', fontSize: 13.5, fontWeight: 700, cursor: 'pointer' }}>退会する</button>
            </div>
          </div>
        )}
      </div>
    </FMSheet>
  );
}

// ── Login (Google OAuth) ────────────────────────────────────────────────────
function FMLogin({ onLogin }) {
  const { T } = useFM();
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 32, background: T.bg }}>
      <div style={{ flex: 1 }} />
      <div style={{ width: 72, height: 72, borderRadius: 20, background: T.accent, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(0,0,0,0.18)' }}>
        <FMIcon name="rss" size={36} color={T.accentOn} />
      </div>
      <div style={{ fontSize: 30, fontWeight: 800, color: T.fg, marginTop: 22, letterSpacing: '-0.03em' }}>Feedman</div>
      <div style={{ fontSize: 14.5, color: T.mutedFg, marginTop: 8, textAlign: 'center', lineHeight: 1.6 }}>通勤中のニュースチェックを、<br />ひとつのタイムラインに。</div>
      <div style={{ flex: 1 }} />
      <button type="button" onClick={onLogin} style={{
        width: '100%', maxWidth: 320, height: 52, borderRadius: 14, border: `1px solid ${T.border}`, background: T.surface,
        color: T.fg, fontSize: 15, fontWeight: 700, cursor: 'pointer', display: 'inline-flex', alignItems: 'center',
        justifyContent: 'center', gap: 10, fontFamily: 'Geist, system-ui',
      }}>
        <svg width="20" height="20" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9 3.6l6.8-6.8C35.6 2.4 30.2 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.2C12.4 13.5 17.7 9.5 24 9.5z"/><path fill="#4285F4" d="M46.1 24.6c0-1.6-.1-3.2-.4-4.6H24v9.1h12.4c-.5 2.9-2.1 5.4-4.6 7l7.1 5.5c4.2-3.9 6.6-9.6 6.6-16.4z"/><path fill="#FBBC05" d="M10.5 28.4c-.5-1.5-.8-3-.8-4.4s.3-3 .8-4.4l-7.9-6.2C1 16.5 0 20.1 0 24s1 7.5 2.6 10.6l7.9-6.2z"/><path fill="#34A853" d="M24 48c6.2 0 11.5-2 15.3-5.5l-7.1-5.5c-2 1.4-4.6 2.2-8.2 2.2-6.3 0-11.6-4-13.5-9.4l-7.9 6.2C6.5 42.6 14.6 48 24 48z"/></svg>
        Google でログイン
      </button>
      <div style={{ fontSize: 11.5, color: T.mutedFg, marginTop: 14, textAlign: 'center', lineHeight: 1.6 }}>続行することで利用規約とプライバシーポリシーに同意したものとみなされます</div>
      <div style={{ flex: 0.4 }} />
    </div>
  );
}

// ── Toast ───────────────────────────────────────────────────────────────────
function FMToast({ msg }) {
  const { T } = useFM();
  return (
    <div style={{
      position: 'absolute', left: '50%', bottom: 84, transform: `translateX(-50%) translateY(${msg ? 0 : 16}px)`,
      zIndex: 80, background: T.statusDark ? 'oklch(0.32 0 0)' : 'oklch(0.22 0 0)', color: '#fff', padding: '11px 18px',
      borderRadius: 999, fontSize: 13.5, fontWeight: 600, fontFamily: 'Geist, system-ui', whiteSpace: 'nowrap',
      boxShadow: '0 8px 24px rgba(0,0,0,0.3)', opacity: msg ? 1 : 0, pointerEvents: 'none', transition: 'opacity .25s, transform .25s',
      display: 'flex', alignItems: 'center', gap: 8,
    }}><FMIcon name="check" size={16} color="#fff" />{msg}</div>
  );
}

Object.assign(window, {
  FMSheet, FMDetailSheet, FMRegisterSheet, FMSettingsSheet, FMKeywordSheet, FMAccountSheet, FMLogin, FMToast, Toggle,
});
