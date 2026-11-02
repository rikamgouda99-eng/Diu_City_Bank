import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Outlet, Navigate, useLocation, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { profile as profileApi } from '../api/services';

// ------------------------------------------------------------- icons (24px stroke set)
const PATHS = {
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  swap: 'M7 7h13l-4-4M17 17H4l4 4',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
  card: 'M3 6h18v12H3zM3 10h18M7 15h4',
  vault: 'M4 4h16v16H4zM12 15a3 3 0 100-6 3 3 0 000 6zM8 20v1M16 20v1',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6',
  x: 'M12 21a9 9 0 100-18 9 9 0 000 18zM9 9l6 6M15 9l-6 6',
  close: 'M6 6l12 12M18 6L6 18',
  out: 'M10 4H5v16h5M15 8l4 4-4 4M19 12H9',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 100-6 3 3 0 000 6z',
  eyeoff: 'M3 3l18 18M10 6.2A9 9 0 0112 5c6 0 10 7 10 7a17 17 0 01-3 3.7M6.6 6.7A17 17 0 002 12s4 7 10 7c1.7 0 3.2-.5 4.5-1.2',
  check: 'M5 12l5 5 9-10',
  checkc: 'M12 21a9 9 0 100-18 9 9 0 000 18zM8 12l3 3 5-6',
  loan: 'M3 10l9-6 9 6M5 10v8M9 10v8M15 10v8M19 10v8M3 21h18',
  bank: 'M3 21h18M4 10h16M12 3l8 5H4zM6 10v8M18 10v8M10 14h4',
  id: 'M3 5h18v14H3zM8 11a2 2 0 100-4 2 2 0 000 4zM5 16a3 3 0 016 0M14 9h4M14 13h4',
  inbox: 'M3 13l3-8h12l3 8v6H3zM3 13h5l1 3h6l1-3h5',
  down: 'M12 4v12M6 10l6 6 6-6M5 20h14',
  plus: 'M12 5v14M5 12h14',
  deposit: 'M12 3v12M7 10l5 5 5-5M4 21h16',
  withdraw: 'M12 21V9M7 14l5-5 5 5M4 3h16',
  send: 'M22 2L11 13M22 2l-7 20-4-9-9-4z',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  bell: 'M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0',
  search: 'M11 19a8 8 0 100-16 8 8 0 000 16zM21 21l-4.3-4.3',
  settings: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z',
  menu: 'M3 6h18M3 12h18M3 18h18',
  left: 'M15 18l-6-6 6-6', right: 'M9 18l6-6-6-6', chevdown: 'M6 9l6 6 6-6',
  chart: 'M3 3v18h18M7 15l4-4 3 3 5-6',
  wallet: 'M3 7h15a3 3 0 013 3v8a2 2 0 01-2 2H5a2 2 0 01-2-2zM3 7l12-4v4M16 14h.01',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  arrowup: 'M7 17L17 7M9 7h8v8', arrowdown: 'M17 7L7 17M15 17H7V9',
  alert: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  file: 'M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9zM14 3v6h6M9 13h6M9 17h6',
  trend: 'M3 17l6-6 4 4 8-8M15 7h6v6',
  users: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8',
  lock: 'M5 11h14v10H5zM8 11V7a4 4 0 018 0v4',
  mail: 'M3 5h18v14H3zM3 6l9 7 9-7',
  camera: 'M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2zM12 17a4 4 0 100-8 4 4 0 000 8z',
  pie: 'M21.2 15.9A10 10 0 118 2.8M22 12A10 10 0 0012 2v10z',
};
export const Icon = ({ n, size }) => (
  <svg className="i" viewBox="0 0 24 24" aria-hidden="true" style={size ? { width: size, height: size } : undefined}><path d={PATHS[n] || PATHS.grid} /></svg>);

// ------------------------------------------------------------- formatting
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const inrShort = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 });
export const money = (v) => (v === null || v === undefined || v === '' ? '—' : inr.format(Number(v)));
export const moneyShort = (v) => (v === null || v === undefined ? '—' : inrShort.format(Number(v)));
export const date = (v) => (v ? new Date(v.length === 10 ? `${v}T00:00:00` : v).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—');
export const dateTime = (v) => (v ? new Date(v).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');
const ACRONYMS = /\b(fd|emi|kyc|upi|dth|pin|otp|pan|id)\b/g;
export const words = (v) => (v ? String(v).replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()).replace(ACRONYMS, (m) => m.toUpperCase()) : '—');
export const maskAcc = (v) => (v ? `•••• ${String(v).slice(-4)}` : '—');

const GOOD = ['SUCCESS', 'ACTIVE', 'APPROVED', 'PAID', 'MATURED', 'CREDIT'];
const BAD = ['FAILED', 'REJECTED', 'BLOCKED', 'FROZEN', 'CLOSED', 'REVERSED'];
const WARN = ['PENDING', 'APPLIED', 'OPEN', 'INITIATED', 'NOT_SUBMITTED'];
export const Badge = ({ v }) => (
  <span className={`badge ${GOOD.includes(v) ? 'good' : BAD.includes(v) ? 'bad' : WARN.includes(v) ? 'warn' : 'plain'}`}>{words(v)}</span>);

export const Sim = () => <span className="sim" title="Simulated for an academic project - no real money moves">Demo / Simulation</span>;

// ------------------------------------------------------------- toasts
const ToastCtx = createContext(() => {});
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  const push = useCallback((text, kind = 'ok') => {
    const id = Math.random().toString(36).slice(2);
    setItems((t) => [...t.slice(-3), { id, text, kind }]);
    setTimeout(() => setItems((t) => t.filter((x) => x.id !== id)), kind === 'err' ? 6000 : 3500);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`} role="status">
            <span className="ic"><Icon n={t.kind === 'err' ? 'close' : 'check'} size={13} /></span>
            <p>{t.text}</p>
            <button type="button" aria-label="Dismiss" onClick={() => setItems((x) => x.filter((y) => y.id !== t.id))}><Icon n="close" size={14} /></button>
          </div>))}
      </div>
    </ToastCtx.Provider>);
}

// ------------------------------------------------------------- data hooks

/** Loads data on mount and when deps change. load() must return the API envelope. */
export function useApi(load, deps = []) {
  const [state, setState] = useState({ data: null, error: '', loading: true });
  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true, error: '' }));
    return load()
      .then((res) => setState({ data: res?.data ?? null, error: '', loading: false }))
      .catch((e) => setState({ data: null, error: e.message, loading: false }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { reload(); }, [reload]);
  return { ...state, reload };
}

/** Busy/error state for a submit; success messages are shown as toasts. */
export function useAction() {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const run = async (fn, { quiet } = {}) => {
    setBusy(true); setError(''); setOk('');
    try {
      const res = await fn();
      if (res?.message && !quiet) { setOk(res.message); toast(res.message); }
      return res;
    } catch (e) {
      setError(e.message || 'Something went wrong.');
      return undefined;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, ok, run, setError, setOk, clear: () => { setError(''); setOk(''); } };
}

// ------------------------------------------------------------- building blocks
export const Notice = ({ error }) => (error ? <p className="msg" role="alert"><Icon n="alert" size={16} />{error}</p> : null);

export function Loading({ rows = 4 }) {
  return (
    <div className="skeleton" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => <span key={i} className="sk" style={{ height: i === 0 ? 18 : 14, width: `${[60, 100, 92, 80, 96, 70][i % 6]}%` }} />)}
    </div>);
}

export function EmptyState({ icon = 'inbox', title = 'Nothing here yet', text, children }) {
  return (
    <div className="empty-state">
      <span className="ic"><Icon n={icon} size={20} /></span>
      <b>{title}</b>{text && <span className="small">{text}</span>}{children}
    </div>);
}

export function Panel({ title, sub, icon, actions, children, className = '' }) {
  return (
    <section className={`panel ${className}`}>
      {(title || actions) && (
        <div className="panel-head">
          <div><h3>{icon && <Icon n={icon} />}{title}</h3>{sub && <p className="sub">{sub}</p>}</div>
          {actions}
        </div>)}
      {children}
    </section>);
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="seg" role="tablist">
      {tabs.map(([id, label]) => (
        <button key={id} type="button" role="tab" aria-selected={value === id} className={value === id ? 'on' : ''} onClick={() => onChange(id)}>{label}</button>))}
    </div>);
}

export function Stat({ label, value, tone, icon = 'chart', sub }) {
  return (
    <div className={`stat ${tone || ''}`}>
      <span className="ic"><Icon n={icon} /></span>
      <div><span>{label}</span><b>{value}</b>{sub && <small>{sub}</small>}</div>
    </div>);
}

/** columns: [{ key, label, render?(row), align? }] */
export function Table({ columns, rows, empty = 'Nothing to show yet.', emptyIcon, onRow, selected }) {
  if (!rows?.length) return <EmptyState icon={emptyIcon} title={empty} />;
  return (
    <div className="scroll">
      <table>
        <thead><tr>{columns.map((c) => <th key={c.key} className={c.align === 'right' ? 'r' : ''}>{c.label}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => (
          <tr key={r.id ?? r.transactionId ?? r.reference ?? i} onClick={onRow ? () => onRow(r) : undefined}
            className={`${onRow ? 'clickable' : ''} ${selected && selected === (r.id ?? r.transactionId ?? r.loanId ?? r.fdId ?? r.cardId) ? 'sel' : ''}`}>
            {columns.map((c) => <td key={c.key} className={c.align === 'right' ? 'r' : ''}>{c.render ? c.render(r) : r[c.key] ?? '—'}</td>)}
          </tr>))}
        </tbody>
      </table>
    </div>);
}

export function Pager({ page, onPage }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pager">
      <span className="muted small">Page {page.page + 1} of {page.totalPages} · {page.totalElements} records</span>
      <div className="row gap">
        <button type="button" className="ghost sm" disabled={page.page <= 0} onClick={() => onPage(page.page - 1)}><Icon n="left" size={14} />Previous</button>
        <button type="button" className="ghost sm" disabled={page.page >= page.totalPages - 1} onClick={() => onPage(page.page + 1)}>Next<Icon n="right" size={14} /></button>
      </div>
    </div>);
}

/** Key-value list of a record. fields: [[key, label, format?]] */
export function Details({ data, fields }) {
  return (
    <dl>{fields.filter(([k]) => data[k] !== undefined && data[k] !== null && data[k] !== '').map(([k, l, f]) => (
      <div key={k}><dt>{l}</dt><dd>{f ? f(data[k], data) : String(data[k])}</dd></div>))}
    </dl>);
}

/**
 * Modal form used for confirmations and admin decisions.
 * fields: [{ name, label, type?, required?, placeholder?, options? }]
 */
export function Dialog({ title, text, fields = [], confirm = 'Confirm', danger, onSubmit, onClose }) {
  const [v, setV] = useState(() => Object.fromEntries(fields.map((f) => [f.name, f.value ?? ''])));
  const act = useAction();
  useEffect(() => {
    const esc = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onClose]);
  const submit = async (e) => {
    e.preventDefault();
    const res = await act.run(() => onSubmit(v));
    if (res !== undefined) onClose(res);
  };
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="panel dialog" onSubmit={submit}>
        <h3>{title}</h3>
        {text && <div className="muted">{text}</div>}
        {fields.map((f, i) => (
          <label key={f.name}>{f.label}
            {f.options ? (
              <select required={f.required} value={v[f.name]} onChange={(e) => setV({ ...v, [f.name]: e.target.value })}>
                {f.options.map((o) => <option key={o} value={o}>{words(o)}</option>)}
              </select>
            ) : (
              <input autoFocus={i === 0} type={f.type || 'text'} required={f.required} placeholder={f.placeholder} step={f.step} min={f.min} maxLength={f.maxLength}
                pattern={f.pattern} inputMode={f.inputMode} value={v[f.name]} onChange={(e) => setV({ ...v, [f.name]: e.target.value })} />
            )}
          </label>))}
        <Notice error={act.error} />
        <div className="row gap">
          <button type="button" className="ghost" onClick={() => onClose()}>Cancel</button>
          <button className={danger ? 'btn-danger' : ''} disabled={act.busy}>{act.busy ? 'Working…' : confirm}</button>
        </div>
      </form>
    </div>);
}

/** Amount input with a rupee prefix; only allows up to 2 decimals. */
export const AmountInput = ({ value, onChange, max, ...rest }) => (
  <span className="amount-input"><span>₹</span>
    <input type="number" inputMode="decimal" min="0.01" step="0.01" max={max} required value={value} placeholder="0.00"
      onChange={(e) => onChange(e.target.value)} {...rest} /></span>);

// ------------------------------------------------------------- avatar (protected photo)

/** Loads the logged-in user's photo once; refreshes when the profile page fires 'dcb:photo'. */
export function useMyPhoto(enabled = true) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    if (!enabled) return undefined;
    let current = null; let alive = true;
    // ask the profile first so users without a photo don't trigger a 404
    const load = () => profileApi.get()
      .then((p) => (p.data?.hasPhoto ? profileApi.photoBlob() : null))
      .then((b) => {
        if (!alive) return;
        if (current) URL.revokeObjectURL(current);
        current = b ? URL.createObjectURL(b) : null;
        setUrl(current);
      })
      .catch(() => { if (alive) setUrl(null); });
    load();
    window.addEventListener('dcb:photo', load);
    return () => { alive = false; window.removeEventListener('dcb:photo', load); if (current) URL.revokeObjectURL(current); };
  }, [enabled]);
  return url;
}

export const Avatar = ({ url, name, className = 'av' }) => (
  <span className={className}>{url ? <img src={url} alt="" /> : (name?.[0] || '?').toUpperCase()}</span>);

function useOutside(ref, onOut) {
  useEffect(() => {
    const h = (e) => ref.current && !ref.current.contains(e.target) && onOut();
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [ref, onOut]);
}

// ------------------------------------------------------------- layout

/**
 * App frame shared by customers and admins.
 * nav: [{ section, items: [[path, label, icon, danger?]] }]
 * notifications: () => Promise<[{ icon, tone, title, text, to }]>
 */
export function Shell({ role, nav, notifications, searchHint, onSearch }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('dcb.collapsed') === '1'; } catch { return false; } });
  const [open, setOpen] = useState(false);
  const [menu, setMenu] = useState(null); // 'notif' | 'user' | null
  const [notes, setNotes] = useState([]);
  const [q, setQ] = useState('');
  const [hl, setHl] = useState(0);
  const photo = useMyPhoto(!!user);
  const menusRef = useRef(null);
  const searchRef = useRef(null);
  useOutside(menusRef, useCallback(() => setMenu(null), []));
  useOutside(searchRef, useCallback(() => setQ(''), []));

  useEffect(() => { setOpen(false); setMenu(null); }, [location.pathname, location.search]);
  useEffect(() => { try { localStorage.setItem('dcb.collapsed', collapsed ? '1' : '0'); } catch { /* storage unavailable */ } }, [collapsed]);
  useEffect(() => {
    if (!user || !notifications) return;
    notifications().then(setNotes).catch(() => setNotes([]));
  }, [user, notifications, location.pathname]);

  const items = useMemo(() => nav.flatMap((s) => s.items), [nav]);
  const matches = q.trim() ? items.filter(([, l]) => l.toLowerCase().includes(q.trim().toLowerCase())).slice(0, 6) : [];

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/app'} replace />;

  const now = new Date();
  const hour = now.getHours();
  const hi = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const home = role === 'ADMIN' ? '/admin' : '/app';
  const doLogout = () => { logout(); navigate('/'); };
  const go = (to) => { setQ(''); navigate(to); };
  const searchKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setHl((h) => Math.min(h + 1, matches.length - (onSearch ? 0 : 1))); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setHl((h) => Math.max(h - 1, 0)); }
    if (e.key === 'Escape') setQ('');
    if (e.key === 'Enter') {
      e.preventDefault();
      if (matches[hl]) go(matches[hl][0]);
      else if (onSearch && q.trim()) { onSearch(q.trim(), navigate); setQ(''); }
    }
  };

  return (
    <div className={`shell ${collapsed ? 'collapsed' : ''} ${open ? 'open' : ''}`}>
      <aside className="sidebar" aria-label="Main navigation">
        <Link to={home} className="brand"><span className="logo">D</span><b>DIU City Bank<small>{role === 'ADMIN' ? 'Admin console' : 'Digital banking'}</small></b></Link>
        <nav>
          {nav.map((s) => (
            <div key={s.section} className="stack" style={{ gap: 2 }}>
              <span className="nav-label">{s.section}</span>
              {s.items.map(([to, l, ic, danger]) => (
                <NavLink key={to} to={to} title={l} className={({ isActive }) => {
                  const [p, qs] = to.split('?');
                  const active = qs ? location.pathname === p && location.search === `?${qs}`
                    : isActive;
                  return `${active ? 'active' : ''} ${danger ? 'danger' : ''}`;
                }} end={to.split('/').length === 2} onClick={() => setOpen(false)}><Icon n={ic} /><span className="txt">{l}</span></NavLink>))}
            </div>))}
        </nav>
        <button type="button" className="side-btn collapse-btn" onClick={() => setCollapsed((c) => !c)} title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          <Icon n={collapsed ? 'right' : 'left'} /><span className="txt">Collapse</span></button>
        <button type="button" className="side-btn" onClick={doLogout} title="Log out"><Icon n="out" /><span className="txt">Log out</span></button>
      </aside>
      <div className="scrim" onClick={() => setOpen(false)} />

      <div className="main-col">
        <header className="topbar">
          <button type="button" className="icon-btn hamburger" aria-label="Open menu" onClick={() => setOpen(true)}><Icon n="menu" /></button>
          <div className="greet">
            <b>{hi}, {user.name?.split(' ')[0]}</b>
            <span>{now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</span>
          </div>
          <div className="spacer" />
          <div className="search" ref={searchRef}>
            <Icon n="search" size={16} />
            <input value={q} onChange={(e) => { setQ(e.target.value); setHl(0); }} onKeyDown={searchKey} placeholder={searchHint || 'Search pages…'} aria-label="Search" />
            {q.trim() && (
              <div className="menu search-results" role="listbox">
                {matches.map(([to, l, ic], i) => (
                  <button key={to} type="button" className={i === hl ? 'hl' : ''} onMouseDown={(e) => { e.preventDefault(); go(to); }}><Icon n={ic} />{l}</button>))}
                {onSearch && (
                  <button type="button" className={hl === matches.length ? 'hl' : ''} onMouseDown={(e) => { e.preventDefault(); onSearch(q.trim(), navigate); setQ(''); }}>
                    <Icon n="search" />Search customers for “{q.trim()}”</button>)}
                {!matches.length && !onSearch && <div className="empty-row">No matching pages</div>}
              </div>)}
          </div>
          <div className="row" ref={menusRef} style={{ gap: 4 }}>
            <div className="dropdown">
              <button type="button" className="icon-btn" aria-label={`Notifications (${notes.length})`} onClick={() => setMenu(menu === 'notif' ? null : 'notif')}>
                <Icon n="bell" />{notes.length > 0 && <span className="notif-dot">{notes.length}</span>}
              </button>
              {menu === 'notif' && (
                <div className="menu" style={{ minWidth: 320 }}>
                  <div className="head"><b>Notifications</b><span>{notes.length ? `${notes.length} item${notes.length > 1 ? 's' : ''} need attention` : 'You are all caught up'}</span></div>
                  {notes.length ? notes.map((n, i) => (
                    <Link key={i} to={n.to} className="notif">
                      <span className="ic" style={{ background: `var(--${n.tone || 'blue'}-50)`, color: `var(--${n.tone === 'blue' || !n.tone ? 'blue-600' : n.tone})` }}><Icon n={n.icon} size={16} /></span>
                      <div><b>{n.title}</b><span>{n.text}</span></div>
                    </Link>)) : <div className="empty-row">No new notifications.</div>}
                </div>)}
            </div>
            <div className="dropdown">
              <button type="button" className="avatar-btn" onClick={() => setMenu(menu === 'user' ? null : 'user')} aria-label="Account menu">
                <Avatar url={photo} name={user.name} /><span className="txt small" style={{ fontWeight: 600 }}>{user.name?.split(' ')[0]}</span><Icon n="chevdown" size={14} />
              </button>
              {menu === 'user' && (
                <div className="menu">
                  <div className="head"><b>{user.name}</b><span>{role === 'ADMIN' ? 'Administrator' : 'Customer'} · {user.bankId}</span></div>
                  <Link to={`${home}/profile`}><Icon n="user" />My profile</Link>
                  <Link to={`${home}/settings`}><Icon n="settings" />Settings</Link>
                  <button type="button" onClick={doLogout}><Icon n="out" />Log out</button>
                </div>)}
            </div>
          </div>
        </header>
        <main key={location.pathname}><Outlet /></main>
        <footer className="app-foot">
          <span>© {now.getFullYear()} DIU City Bank · Demo / simulation, no real money moves.</span>
          <nav><Link to="/">Home</Link><Link to={`${home}/profile`}>Profile</Link><Link to={`${home}/settings`}>Settings</Link></nav>
        </footer>
      </div>
    </div>);
}

export function PageHead({ title, sub, children }) {
  return (
    <div className="page-head">
      <div><h2>{title}</h2>{sub && <p className="muted">{sub}</p>}</div>
      {children && <div className="row gap">{children}</div>}
    </div>);
}

/** Name with an initials avatar, for people in admin tables. */
export const Person = ({ name, sub }) => (
  <div className="person"><Avatar name={name} /><div><b>{name || '—'}</b>{sub && <span>{sub}</span>}</div></div>);

/**
 * Status filter chips with live counts. count(status) must resolve to the API envelope of a
 * paged list; its totalElements is the number shown. refreshKey re-fetches counts after a change.
 */
export function StatusChips({ statuses, value, onChange, count, refreshKey, all = true }) {
  const [counts, setCounts] = useState({});
  useEffect(() => {
    let alive = true;
    Promise.all(statuses.map((s) => count(s).then((r) => [s, r.data.totalElements]).catch(() => [s, null])))
      .then((pairs) => alive && setCounts(Object.fromEntries(pairs)));
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey]);
  const total = Object.values(counts).reduce((a, b) => a + (b || 0), 0);
  return (
    <div className="chips" role="tablist" aria-label="Filter by status">
      {all && <button type="button" role="tab" aria-selected={!value} className={`chip-btn ${!value ? 'on' : ''}`} onClick={() => onChange('')}>All <b>{total}</b></button>}
      {statuses.map((s) => (
        <button key={s} type="button" role="tab" aria-selected={value === s} className={`chip-btn ${value === s ? 'on' : ''}`} onClick={() => onChange(s)}>
          {words(s)} <b>{counts[s] ?? '…'}</b></button>))}
    </div>);
}
