import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { loans } from '../../api/services';
import { newKey } from '../../api/client';
import { AmountInput, Badge, Details, Dialog, EmptyState, Icon, Loading, Notice, PageHead, Panel, Sim, Stat, Table, Tabs, money, date, dateTime, words, useAction, useApi } from '../../components/ui';
import { Donut, Legend, StackedBars } from '../../components/charts';

const TYPES = ['PERSONAL', 'HOME', 'EDUCATION', 'VEHICLE'];
const P_COLOR = '#2457D6'; const I_COLOR = '#E9A23B';

export const emiColumns = [
  { key: 'emiNumber', label: '#', render: (r) => <b>{r.emiNumber}</b> },
  { key: 'dueDate', label: 'Due date', render: (r) => date(r.dueDate) },
  { key: 'principalComponent', label: 'Principal', align: 'right', render: (r) => <span className="num">{money(r.principalComponent)}</span> },
  { key: 'interestComponent', label: 'Interest', align: 'right', render: (r) => <span className="num">{money(r.interestComponent)}</span> },
  { key: 'emiAmount', label: 'EMI', align: 'right', render: (r) => <b className="num">{money(r.emiAmount)}</b> },
  { key: 'remainingPrincipal', label: 'Balance', align: 'right', render: (r) => <span className="num">{money(r.remainingPrincipal)}</span> },
  { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
  { key: 'paymentDate', label: 'Paid on', render: (r) => (r.paymentDate ? dateTime(r.paymentDate) : '—') },
];

const TABS = [['mine', 'My loans'], ['calc', 'EMI calculator'], ['apply', 'Apply for a loan']];

export default function Loans() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.some(([t]) => t === params.get('tab')) ? params.get('tab') : 'mine';
  const rates = useApi(() => loans.rates());
  const list = useApi(() => loans.list());
  return (<>
    <PageHead title="Loans & EMI" sub="Estimate EMIs, apply, and repay month by month."><Sim /></PageHead>
    <div className="disclaimer"><Icon n="alert" size={16} /><span>{rates.data?.disclaimer || 'DEMO / SIMULATION: not a real loan offer or credit decision.'}</span></div>
    <Tabs value={tab} onChange={(t) => setParams({ tab: t })} tabs={TABS} />
    {tab === 'calc' && <Calculator rates={rates.data} onApply={() => setParams({ tab: 'apply' })} />}
    {tab === 'apply' && <Apply rates={rates.data} onDone={() => { list.reload(); setParams({ tab: 'mine' }); }} />}
    {tab === 'mine' && <MyLoans list={list} onApply={() => setParams({ tab: 'apply' })} />}
  </>);
}

function Calculator({ rates, onApply }) {
  const [v, setV] = useState({ loanType: 'PERSONAL', amount: '', annualInterestRate: '', tenureMonths: 24 });
  const [res, setRes] = useState(null);
  const act = useAction();
  const submit = async (e) => {
    e.preventDefault();
    const r = await act.run(() => loans.calculate({ ...v, tenureMonths: Number(v.tenureMonths), annualInterestRate: v.annualInterestRate || undefined }), { quiet: true });
    if (r) setRes(r.data);
  };
  return (
    <div className="split">
      <form className="panel" onSubmit={submit}>
        <div className="panel-head"><div><h3><Icon n="chart" />EMI calculator</h3><p className="sub">EMI = P × r × (1 + r)ⁿ / ((1 + r)ⁿ − 1)</p></div></div>
        <div className="form-grid">
          <label>Loan type<select value={v.loanType} onChange={(e) => setV({ ...v, loanType: e.target.value })}>{TYPES.map((t) => <option key={t} value={t}>{words(t)} {rates ? `· ${rates.rates[t]}%` : ''}</option>)}</select></label>
          <label>Loan amount<AmountInput value={v.amount} onChange={(a) => setV({ ...v, amount: a })} min={rates?.minAmount} max={rates?.maxAmount} /></label>
          <label>Interest rate (% p.a.) <span className="hint">Optional</span><input type="number" step="0.01" min="0" max={rates?.maxRate} value={v.annualInterestRate}
            placeholder={rates ? `${rates.rates[v.loanType]}` : ''} onChange={(e) => setV({ ...v, annualInterestRate: e.target.value })} /></label>
          <label>Tenure (months)<input type="number" required min={rates?.minTenureMonths || 1} max={rates?.maxTenureMonths} value={v.tenureMonths} onChange={(e) => setV({ ...v, tenureMonths: e.target.value })} /></label>
        </div>
        <Notice error={act.error} />
        <button disabled={act.busy}>{act.busy ? 'Calculating…' : 'Calculate EMI'}</button>
      </form>
      <Panel title="Estimated repayment" icon="pie">
        {res ? (<>
          <div className="big-number">{money(res.monthlyEmi)}<span className="muted"> / month</span></div>
          <div className="donut-wrap">
            <Donut segments={[{ label: 'Principal', value: Number(res.loanAmount), color: P_COLOR }, { label: 'Interest', value: Number(res.totalInterest), color: I_COLOR }]} size={130}
              center={`${Math.round((Number(res.totalInterest) / Number(res.totalRepayment)) * 100)}% int.`} />
            <Legend items={[['Principal', P_COLOR, money(res.loanAmount)], ['Interest', I_COLOR, money(res.totalInterest)]]} />
          </div>
          <Details data={res} fields={[['annualInterestRate', 'Interest rate', (x) => `${x}% p.a.`], ['tenureMonths', 'Tenure', (x) => `${x} months`], ['totalRepayment', 'Total repayment', money]]} />
          <button type="button" className="ghost" onClick={onApply}>Apply for this loan<Icon n="arrow" size={16} /></button>
        </>) : <EmptyState icon="chart" title="Enter loan details" text="Your monthly EMI and the principal/interest split will appear here." />}
      </Panel>
    </div>);
}

function Apply({ rates, onDone }) {
  const [v, setV] = useState({ loanType: 'PERSONAL', amount: '', tenureMonths: 24, employmentType: 'SALARIED', employerName: '', monthlyIncome: '', purpose: '' });
  const act = useAction();
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });
  const submit = async (e) => {
    e.preventDefault();
    const r = await act.run(() => loans.apply({ ...v, tenureMonths: Number(v.tenureMonths), employerName: v.employerName || undefined, purpose: v.purpose || undefined }));
    if (r) onDone();
  };
  return (
    <form className="panel narrow" onSubmit={submit}>
      <div className="panel-head"><div><h3><Icon n="file" />Loan application</h3><p className="sub">The bank reviews your application and sets the final amount and rate.</p></div></div>
      <h4>Loan</h4>
      <div className="form-grid">
        <label>Loan type<select value={v.loanType} onChange={set('loanType')}>{TYPES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        <label>Amount<AmountInput value={v.amount} onChange={(a) => setV({ ...v, amount: a })} min={rates?.minAmount} max={rates?.maxAmount} /></label>
        <label>Tenure (months)<input type="number" required min={rates?.minTenureMonths || 1} max={rates?.maxTenureMonths} value={v.tenureMonths} onChange={set('tenureMonths')} /></label>
      </div>
      <h4>Employment & income</h4>
      <div className="form-grid">
        <label>Employment<select value={v.employmentType} onChange={set('employmentType')}>{['SALARIED', 'SELF_EMPLOYED', 'STUDENT', 'RETIRED', 'OTHER'].map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        <label>Employer <span className="hint">Optional</span><input maxLength={100} value={v.employerName} onChange={set('employerName')} /></label>
        <label>Monthly income<AmountInput value={v.monthlyIncome} onChange={(a) => setV({ ...v, monthlyIncome: a })} min="0" /></label>
      </div>
      <label>Purpose <span className="hint">Optional</span><input maxLength={200} value={v.purpose} onChange={set('purpose')} placeholder="e.g. Home renovation" /></label>
      <Notice error={act.error} />
      <button disabled={act.busy}>{act.busy ? 'Submitting…' : 'Submit application'}</button>
    </form>);
}

function MyLoans({ list, onApply }) {
  const [selectedId, setSelectedId] = useState(null);
  const rows = list.data || [];
  const active = rows.filter((l) => l.status === 'ACTIVE');
  const next = active.filter((l) => l.nextEmiDueDate).sort((a, b) => a.nextEmiDueDate.localeCompare(b.nextEmiDueDate))[0];
  useEffect(() => { if (!selectedId && rows.length) setSelectedId((active[0] || rows[0]).loanId); }, [rows, active, selectedId]);
  const selected = rows.find((l) => l.loanId === selectedId);
  if (list.loading) return <Panel><Loading /></Panel>;
  if (list.error) return <Notice error={list.error} />;
  if (!rows.length) return <EmptyState icon="loan" title="You have no loans yet" text="Estimate your EMI first, then apply in a few steps."><button type="button" style={{ marginTop: 8 }} onClick={onApply}>Apply for a loan</button></EmptyState>;
  return (<>
    <div className="stats">
      <Stat icon="loan" label="Active loans" value={active.length} sub={`${rows.filter((l) => l.status === 'PENDING').length} in review`} />
      <Stat icon="wallet" tone="bad" label="Outstanding balance" value={money(active.reduce((s, l) => s + Number(l.outstandingPrincipal || 0), 0))} />
      <Stat icon="clock" tone="warn" label="Upcoming EMI" value={next ? money(next.nextEmiAmount) : '—'} sub={next ? `Due ${date(next.nextEmiDueDate)}` : 'Nothing due'} />
      <Stat icon="trend" tone="violet" label="Monthly EMIs" value={money(active.reduce((s, l) => s + Number(l.monthlyEmi || 0), 0))} />
    </div>
    <Panel className="flush" title="Loans" icon="list">
      <Table rows={rows} onRow={(r) => setSelectedId(r.loanId)} selected={selectedId} columns={[
        { key: 'loanId', label: 'Loan', render: (r) => <div className="txn-cell"><span className="txn-ic"><Icon n="loan" size={16} /></span><div><b>{words(r.loanType)} loan</b><span className="sub">{r.loanId}</span></div></div> },
        { key: 'amount', label: 'Amount', align: 'right', render: (r) => <span className="num">{money(r.approvedAmount ?? r.requestedAmount)}</span> },
        { key: 'interestRate', label: 'Rate', render: (r) => (r.interestRate ? `${r.interestRate}%` : '—') },
        { key: 'monthlyEmi', label: 'EMI', align: 'right', render: (r) => <span className="num">{money(r.monthlyEmi)}</span> },
        { key: 'outstandingPrincipal', label: 'Outstanding', align: 'right', render: (r) => <b className="num">{money(r.outstandingPrincipal)}</b> },
        { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
      ]} />
    </Panel>
    {selected && <div style={{ marginTop: 24 }}><LoanDetail key={selected.loanId} loan={selected} onChange={list.reload} /></div>}
  </>);
}

function LoanDetail({ loan, onChange }) {
  const schedule = useApi(() => loans.schedule(loan.loanId), [loan.loanId, loan.emisPaid]);
  const history = useApi(() => loans.emiHistory(loan.loanId), [loan.loanId, loan.emisPaid]);
  const [dialog, setDialog] = useState(null);
  const [view, setView] = useState('schedule');
  const rows = schedule.data || [];
  const next = rows.find((e) => e.status === 'PENDING');
  const bars = useMemo(() => rows.map((e) => ({ label: `#${e.emiNumber}`, a: Number(e.principalComponent), b: Number(e.interestComponent) })), [rows]);
  const paidPct = rows.length ? Math.round((rows.filter((e) => e.status === 'PAID').length / rows.length) * 100) : 0;
  const pay = () => {
    const key = newKey();
    setDialog({
      title: `Pay EMI #${next.emiNumber}`, text: `${money(next.emiAmount)} will be debited from your savings account (due ${date(next.dueDate)}).`,
      confirm: `Pay ${money(next.emiAmount)}`, onSubmit: () => loans.payEmi(loan.loanId, next.emiNumber, key),
    });
  };
  return (<>
    <div className="split">
      <Panel title={`${words(loan.loanType)} loan`} sub={loan.loanId} icon="loan" actions={<Badge v={loan.status} />}>
        {rows.length > 0 && (<>
          <div className="row"><span className="muted small">Repaid</span><span className="small"><b>{paidPct}%</b> · {loan.emisPaid} of {rows.length} EMIs</span></div>
          <div className="progress"><i style={{ width: `${paidPct}%` }} /></div>
        </>)}
        <div className="info-grid">
          <div><span>Approved amount</span><b className="num">{money(loan.approvedAmount ?? loan.requestedAmount)}</b></div>
          <div><span>Interest rate</span><b>{loan.interestRate}% p.a.</b></div>
          <div><span>Tenure</span><b>{loan.tenureMonths} months</b></div>
          <div><span>Monthly EMI</span><b className="num">{money(loan.monthlyEmi)}</b></div>
          <div><span>Total interest</span><b className="num">{money(loan.totalInterest)}</b></div>
          <div><span>Total repayment</span><b className="num">{money(loan.totalRepayment)}</b></div>
          <div><span>Outstanding</span><b className="num">{money(loan.outstandingPrincipal)}</b></div>
          <div><span>Applied on</span><b>{date(loan.appliedAt)}</b></div>
        </div>
        {loan.adminComment && <p className="msg ok">Bank comment: {loan.adminComment}</p>}
      </Panel>
      <Panel title="Upcoming EMI" icon="clock">
        {loan.status === 'PENDING' && <EmptyState icon="clock" title="Application in review" text="Your schedule is generated once the bank approves the loan." />}
        {loan.status === 'REJECTED' && <EmptyState icon="x" title="Application not approved" text={loan.adminComment} />}
        {loan.status === 'CLOSED' && <EmptyState icon="checkc" title="Loan fully repaid" text={`Closed on ${date(loan.closedAt)}`} />}
        {loan.status === 'ACTIVE' && next && (<>
          <div className="big-number">{money(next.emiAmount)}</div>
          <p className="muted">EMI #{next.emiNumber} · due {date(next.dueDate)}</p>
          <div className="flow"><div><span>Principal</span><b>{money(next.principalComponent)}</b></div><div><span>Interest</span><b>{money(next.interestComponent)}</b></div></div>
          <button type="button" onClick={pay}><Icon n="wallet" />Pay EMI now</button>
        </>)}
      </Panel>
    </div>

    {rows.length > 0 && (
      <Panel title="Principal vs interest" icon="chart" sub="How each EMI splits between principal and interest" actions={<Legend items={[['Principal', P_COLOR], ['Interest', I_COLOR]]} />}>
        <StackedBars data={bars} colors={[P_COLOR, I_COLOR]} />
      </Panel>)}

    {rows.length > 0 && (
      <div style={{ marginTop: 24 }}>
        <Tabs value={view} onChange={setView} tabs={[['schedule', `Repayment schedule (${rows.length})`], ['history', `Payment history (${history.data?.length ?? 0})`]]} />
        <Panel className="flush">
          {view === 'schedule' ? <Table rows={rows} columns={emiColumns} />
            : <Table rows={history.data} empty="No EMIs paid yet" emptyIcon="clock" columns={[...emiColumns.filter((c) => c.key !== 'remainingPrincipal'), { key: 'transactionId', label: 'Transaction' }]} />}
        </Panel>
      </div>)}
    {schedule.error && <Notice error={schedule.error} />}
    {dialog && <Dialog {...dialog} onClose={(res) => { setDialog(null); if (res) { onChange(); schedule.reload(); history.reload(); } }} />}
  </>);
}
