import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { account, cards, fd, loans, txn } from '../../api/services';
import { Badge, EmptyState, Icon, Loading, Notice, PageHead, Pager, Panel, Sim, Table, money, maskAcc, words, useApi } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';
import { AreaChart, Donut } from '../../components/charts';
import { TXN_STATUSES, TXN_TYPES, txnColumns } from '../../components/txn';

// local calendar date (toISOString would shift to UTC and drop today's entries in IST after midnight)
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function useHidden() {
  const [hidden, setHidden] = useState(() => { try { return localStorage.getItem('dcb.hideBalance') === '1'; } catch { return false; } });
  const toggle = () => setHidden((h) => { try { localStorage.setItem('dcb.hideBalance', h ? '0' : '1'); } catch { /* ignore */ } return !h; });
  return [hidden, toggle];
}

const CAT_COLORS = ['#2457D6', '#E9A23B', '#16A36A', '#DC4545', '#7C3AED', '#0891B2', '#64748B'];

function monthRange() {
  const now = new Date();
  return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now) };
}

export default function Dashboard() {
  const { user } = useAuth();
  const acc = useApi(() => account.me());
  const month = useApi(() => txn.history({ ...monthRange(), status: 'SUCCESS', size: 100 }));
  const [hidden, toggle] = useHidden();
  const show = (v) => (hidden ? '₹ ••••••' : money(v));
  const a = acc.data;

  return (<>
    <PageHead title={`Welcome back, ${user.name?.split(' ')[0]}`} sub="Here's what's happening with your money today.">
      <Link className="btn ghost" to="/app/transactions?tab=history"><Icon n="list" />Statements</Link>
      <Link className="btn" to="/app/transfer"><Icon n="send" />Send money</Link>
    </PageHead>
    {acc.loading ? <div className="hero-card"><Loading rows={3} /></div> : acc.error ? <Notice error={acc.error} /> : (
      <section className="hero-card" aria-label="Account balance">
        <div>
          <div className="row gap"><span className="label">Total balance</span><Sim /></div>
          <div className="amount">
            <span>{show(a.balance)}</span>
            <button type="button" onClick={toggle} aria-label={hidden ? 'Show balance' : 'Hide balance'} title={hidden ? 'Show balance' : 'Hide balance'}>
              <Icon n={hidden ? 'eye' : 'eyeoff'} /></button>
          </div>
          <span style={{ color: '#9FB2D3', fontSize: '.88rem' }}>Available {show(a.availableBalance)}</span>
          <div className="meta">
            <span>Account <b>{maskAcc(a.accountNo)}</b></span>
            <span>Type <b>{words(a.accountType)}</b></span>
            <span>Holder <b>{a.holderName}</b></span>
          </div>
        </div>
        <div className="side">
          <Badge v={a.status} />
          <div className="row gap">
            <Link className="btn light" to="/app/accounts"><Icon n="wallet" />Details</Link>
            <Link className="btn" to="/app/transfer"><Icon n="send" />Transfer</Link>
          </div>
        </div>
      </section>)}

    <nav className="quick" aria-label="Quick actions">
      <Link to="/app/transfer"><span className="ic"><Icon n="send" /></span>Transfer</Link>
      <Link to="/app/bills"><span className="ic"><Icon n="receipt" /></span>Pay Bills</Link>
      <Link to="/app/cards"><span className="ic"><Icon n="card" /></span>Cards</Link>
      <Link to="/app/transactions?tab=mini"><span className="ic"><Icon n="list" /></span>Mini Statement</Link>
    </nav>

    <div className="split">
      <div className="stack" style={{ gap: 20 }}>
        <FinancialOverview hidden={hidden} month={month} />
        <Products />
      </div>
      <Spending month={month} hidden={hidden} />
    </div>

    <RecentTransactions />
  </>);
}

function FinancialOverview({ hidden, month }) {
  const now = new Date();
  const today = iso(now);
  const since = iso(new Date(Date.now() - 30 * 86400000));
  const recent = useApi(() => txn.history({ from: since, to: today, size: 100 }));

  const sums = useMemo(() => {
    const rows = month.data?.content || [];
    return {
      income: rows.filter((t) => t.direction === 'CREDIT').reduce((s, t) => s + Number(t.amount), 0),
      expense: rows.filter((t) => t.direction === 'DEBIT').reduce((s, t) => s + Number(t.amount), 0),
      count: month.data?.totalElements ?? 0,
    };
  }, [month.data]);

  // balance after every successful ledger entry in the last 30 days, oldest first
  const points = useMemo(() => (recent.data?.content || [])
    .filter((t) => t.status !== 'FAILED' && t.balanceAfter !== null)
    .reverse()
    .map((t) => ({ label: new Date(t.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }), value: Number(t.balanceAfter) })), [recent.data]);

  const fmt = (v) => (hidden ? '₹ ••••' : money(v));
  return (
    <Panel title="Financial overview" sub={now.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })} icon="chart">
      {month.loading ? <Loading /> : month.error ? <Notice error={month.error} /> : (
        <div className="flow">
          <div><span>Money in this month</span><b className="cr">{fmt(sums.income)}</b></div>
          <div><span>Money out this month</span><b>{fmt(sums.expense)}</b></div>
        </div>)}
      <div className="row"><span className="muted small">Balance over the last 30 days</span><span className="muted small">{sums.count} transactions this month</span></div>
      {recent.loading ? <Loading rows={3} /> : points.length >= 2 && !hidden ? <AreaChart points={points} height={190} />
        : <EmptyState icon="chart" title={hidden ? 'Balance hidden' : 'Not enough activity yet'} text={hidden ? 'Show your balance to see the chart.' : 'The chart appears after a couple of transactions.'} />}
    </Panel>);
}

/** Where this month's money went: successful debits grouped by transaction type. */
function Spending({ month, hidden }) {
  const rows = useMemo(() => {
    const byType = {};
    (month.data?.content || []).filter((t) => t.direction === 'DEBIT').forEach((t) => { byType[t.type] = (byType[t.type] || 0) + Number(t.amount); });
    return Object.entries(byType).sort((a, b) => b[1] - a[1]).map(([type, value], i) => ({ label: words(type), value, color: CAT_COLORS[i % CAT_COLORS.length] }));
  }, [month.data]);
  const total = rows.reduce((s, r) => s + r.value, 0);
  return (
    <Panel title="Spending by category" icon="pie" sub="Money out this month">
      {month.loading ? <Loading /> : month.error ? <Notice error={month.error} /> : !rows.length ? <EmptyState icon="pie" title="No spending yet this month" />
        : hidden ? <EmptyState icon="eyeoff" title="Amounts hidden" text="Show your balance to see the breakdown." /> : (<>
          <div className="donut-wrap" style={{ justifyContent: 'center' }}>
            <Donut segments={rows} size={150} center={moneyCompact(total)} />
          </div>
          <div className="activity">
            {rows.map((r) => (
              <div key={r.label}>
                <i style={{ width: 10, height: 10, borderRadius: 3, background: r.color, flex: 'none' }} />
                <div className="txt"><b>{r.label}</b><span>{Math.round((r.value / total) * 100)}% of spending</span></div>
                <b className="num" style={{ fontSize: '.87rem' }}>{money(r.value)}</b>
              </div>))}
          </div>
        </>)}
    </Panel>);
}

const moneyCompact = (v) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 }).format(v);

function Products() {
  const f = useApi(() => fd.summary());
  const l = useApi(() => loans.list());
  const c = useApi(() => cards.list());
  const active = (l.data || []).filter((x) => x.status === 'ACTIVE');
  const next = active.filter((x) => x.nextEmiDueDate).sort((x, y) => x.nextEmiDueDate.localeCompare(y.nextEmiDueDate))[0];
  const rows = [
    ['/app/fd', 'vault', 'Fixed deposits', f.data ? `${f.data.activeFds} active · ${money(f.data.totalMaturityAmount)} at maturity` : '…'],
    ['/app/loans', 'loan', 'Loans', l.data ? (active.length ? `${money(active.reduce((s, x) => s + Number(x.outstandingPrincipal || 0), 0))} outstanding` : 'No active loans') : '…'],
    ['/app/loans', 'clock', 'Next EMI', l.data ? (next ? `${money(next.nextEmiAmount)} on ${new Date(next.nextEmiDueDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}` : 'Nothing due') : '…'],
    ['/app/cards', 'card', 'Cards', c.data ? `${c.data.filter((x) => x.status === 'ACTIVE').length} active · ${c.data.filter((x) => x.status === 'APPLIED').length} in review` : '…'],
  ];
  return (
    <Panel title="Your products" icon="wallet">
      <div className="activity">
        {rows.map(([to, ic, title, text]) => (
          <div key={title}>
            <span className="txn-ic"><Icon n={ic} size={16} /></span>
            <div className="txt"><b>{title}</b><span>{text}</span></div>
            <Link to={to} className="link small">Open</Link>
          </div>))}
      </div>
    </Panel>);
}

function RecentTransactions() {
  const [f, setF] = useState({ type: '', status: '', page: 0, size: 6 });
  const list = useApi(() => txn.history(f), [f]);
  return (
    <Panel className="flush" title="Recent transactions" icon="swap" actions={
      <div className="row gap">
        <select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value, page: 0 })} aria-label="Filter by type">
          <option value="">All types</option>{TXN_TYPES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select>
        <select value={f.status} onChange={(e) => setF({ ...f, status: e.target.value, page: 0 })} aria-label="Filter by status">
          <option value="">All statuses</option>{TXN_STATUSES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select>
        <Link className="btn ghost sm" to="/app/transactions?tab=history">View all</Link>
      </div>}>
      {list.loading ? <div style={{ padding: 24 }}><Loading /></div> : list.error ? <div style={{ padding: 24 }}><Notice error={list.error} /></div> : (<>
        <Table rows={list.data.content} empty="No transactions match" emptyIcon="swap" columns={txnColumns({ balances: false })} />
        <Pager page={list.data} onPage={(p) => setF({ ...f, page: p })} />
      </>)}
    </Panel>);
}
