import { admin } from '../../api/services';
import { Loading, Notice, PageHead, Panel, Stat, money, words, useApi } from '../../components/ui';
import { BarList, Donut, Legend } from '../../components/charts';
import { TXN_STATUSES, TXN_TYPES } from '../../components/txn';

const COLORS = ['#2457D6', '#16A36A', '#E9A23B', '#DC4545', '#7C3AED', '#0891B2', '#64748B'];

/** Counts per value of a filter, each from the API's totalElements. */
const countBy = (values, fetch) => Promise.all(values.map((v) => fetch(v).then((r) => ({ label: words(v), value: r.data.totalElements }))))
  .then((rows) => ({ data: rows }));

function Breakdown({ title, icon, load, donut }) {
  const q = useApi(load);
  const rows = (q.data || []).filter((r) => r.value > 0);
  return (
    <Panel title={title} icon={icon}>
      {q.loading ? <Loading rows={4} /> : q.error ? <Notice error={q.error} /> : !rows.length ? <p className="muted">No records yet.</p> : donut ? (
        <div className="donut-wrap">
          <Donut segments={rows.map((r, i) => ({ ...r, color: COLORS[i % COLORS.length] }))} size={140} center={String(rows.reduce((s, r) => s + r.value, 0))} />
          <Legend items={rows.map((r, i) => [r.label, COLORS[i % COLORS.length], r.value])} />
        </div>) : <BarList rows={rows.sort((a, b) => b.value - a.value)} />}
    </Panel>);
}

export default function Reports() {
  const d = useApi(() => admin.dashboard());
  const s = d.data;
  return (<>
    <PageHead title="Reports" sub="Distribution of transactions and products, computed from live data." />
    {d.loading ? <Panel><Loading rows={2} /></Panel> : d.error ? <Notice error={d.error} /> : (
      <div className="stats">
        <Stat icon="wallet" label="Total deposits held" value={money(s.totalDeposits)} />
        <Stat icon="loan" tone="bad" label="Loan book outstanding" value={money(s.loanOutstanding)} />
        <Stat icon="swap" tone="violet" label="Transactions today" value={s.transactionsToday} />
        <Stat icon="alert" tone="warn" label="Failed transactions" value={s.failedTransactions} />
      </div>)}
    <div className="two">
      <Breakdown title="Transactions by type" icon="swap" load={() => countBy(TXN_TYPES, (t) => admin.transactions({ type: t, size: 1 }))} />
      <Breakdown title="Transactions by status" icon="checkc" donut load={() => countBy(TXN_STATUSES, (st) => admin.transactions({ status: st, size: 1 }))} />
    </div>
    <div className="two">
      <Breakdown title="Fixed deposits by status" icon="vault" donut load={() => countBy(['PENDING', 'ACTIVE', 'MATURED', 'CLOSED', 'REJECTED'], (st) => admin.fds(st, 0))} />
      <Breakdown title="Loans by status" icon="loan" donut load={() => countBy(['PENDING', 'ACTIVE', 'CLOSED', 'REJECTED'], (st) => admin.loans(st, 0))} />
    </div>
    <div className="two">
      <Breakdown title="Cards by status" icon="card" load={() => countBy(['APPLIED', 'ACTIVE', 'BLOCKED', 'REJECTED', 'REPLACED'], (st) => admin.cards(st, '', 0))} />
      <Breakdown title="Bill payments by type" icon="receipt" load={() => countBy(['ELECTRICITY', 'WATER', 'MOBILE_RECHARGE', 'INTERNET', 'GAS', 'DTH', 'OTHER'], (b) => admin.bills({ billType: b, size: 1 }))} />
    </div>
  </>);
}
