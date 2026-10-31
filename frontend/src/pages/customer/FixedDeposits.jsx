import { useMemo, useState } from 'react';
import { fd } from '../../api/services';
import { newKey } from '../../api/client';
import { AmountInput, Badge, Details, Dialog, Icon, Loading, Notice, PageHead, Panel, Sim, Stat, Table, money, date, words, useAction, useApi } from '../../components/ui';
import { AreaChart, Donut, Legend } from '../../components/charts';

/**
 * Value at the end of each year for the growth chart. The end point is the maturity amount
 * returned by the backend; intermediate points use the same formula and the rate it returned.
 */
function growth(q) {
  const n = q.compoundingPerYear; const r = Number(q.interestRate) / (100 * n); const P = Number(q.principal);
  const pts = [{ label: 'Start', value: P }];
  const step = q.tenureMonths <= 24 ? 3 : 12;
  for (let m = step; m < q.tenureMonths; m += step) {
    const periods = (n * m) / 12; const whole = Math.floor(periods);
    pts.push({ label: m % 12 === 0 ? `${m / 12}y` : `${m}m`, value: P * (1 + r) ** whole * (1 + r * (periods - whole)) });
  }
  pts.push({ label: 'Maturity', value: Number(q.maturityAmount) });
  return pts;
}

export default function FixedDeposits() {
  const rates = useApi(() => fd.rates());
  const summary = useApi(() => fd.summary());
  const list = useApi(() => fd.list());
  const [v, setV] = useState({ amount: '', tenureMonths: 12 });
  const [quote, setQuote] = useState(null);
  const [key, setKey] = useState(newKey);
  const [selected, setSelected] = useState(null);
  const [dialog, setDialog] = useState(null);
  const act = useAction();
  const r = rates.data;
  const s = summary.data;
  const refresh = () => { summary.reload(); list.reload(); };
  const slabRate = r && v.tenureMonths ? r.slabs.find((x) => Number(v.tenureMonths) <= x.upToMonths)?.rate ?? r.slabs.at(-1).rate : null;
  const points = useMemo(() => (quote ? growth(quote) : []), [quote]);

  const calc = async (e) => {
    e.preventDefault();
    const res = await act.run(() => fd.quote({ amount: v.amount, tenureMonths: Number(v.tenureMonths) }), { quiet: true });
    if (res) setQuote(res.data);
  };
  const book = async () => {
    const res = await act.run(() => fd.create({ amount: quote.principal, tenureMonths: quote.tenureMonths }, key));
    if (res) { setQuote(null); setV({ amount: '', tenureMonths: 12 }); setKey(newKey()); refresh(); }
  };
  const askClose = async (row) => {
    const res = await act.run(() => fd.closePreview(row.fdId), { quiet: true });
    if (!res) return;
    const p = res.data; const closeKey = newKey();
    setDialog({
      title: p.closureType === 'MATURITY' ? 'Close matured FD' : 'Close FD before maturity',
      danger: p.closureType !== 'MATURITY',
      confirm: `Close and credit ${money(p.payoutAmount)}`,
      text: (<>
        <Details data={p} fields={[['principal', 'Principal', money], ['contractedRate', 'Contracted rate', (x) => `${x}%`], ['penaltyRate', 'Penalty', (x) => `${x}%`],
          ['applicableRate', 'Rate applied', (x) => `${x}%`], ['daysHeld', 'Days held'], ['interestEarned', 'Interest', money], ['payoutAmount', 'You receive', money],
          ['maturityAmountIfHeld', 'If held to maturity', money]]} />
        <p className="hint" style={{ marginTop: 8 }}>{p.note}</p></>),
      onSubmit: () => fd.close(row.fdId, closeKey),
    });
  };

  return (<>
    <PageHead title="Fixed deposits" sub="Grow your savings with guaranteed returns."><Sim /></PageHead>
    {s ? (
      <div className="stats">
        <Stat icon="wallet" label="Total investment" value={money(s.totalInvested)} sub={`${s.totalFds} deposits in total`} />
        <Stat icon="trend" tone="good" label="Expected interest" value={money(s.totalExpectedInterest)} />
        <Stat icon="vault" tone="violet" label="Maturity amount" value={money(s.totalMaturityAmount)} />
        <Stat icon="checkc" tone="warn" label="Active deposits" value={s.activeFds} sub={`${s.pendingFds} pending · ${s.maturedFds + s.closedFds} closed`} />
      </div>) : summary.loading ? <Panel><Loading rows={2} /></Panel> : <Notice error={summary.error} />}

    <div className="split">
      <Panel title="FD calculator" icon="chart" sub={r ? `Compounded ${r.compoundingPerYear}× a year · min ${money(r.minAmount)} · ${r.minTenureMonths}–${r.maxTenureMonths} months` : undefined}>
        <form onSubmit={calc} className="stack">
          <div className="form-grid">
            <label>Principal amount<AmountInput value={v.amount} onChange={(a) => { setV({ ...v, amount: a }); setQuote(null); }} min={r?.minAmount} /></label>
            <label>Tenure (months)<input type="number" required min={r?.minTenureMonths || 1} max={r?.maxTenureMonths} value={v.tenureMonths}
              onChange={(e) => { setV({ ...v, tenureMonths: e.target.value }); setQuote(null); }} /></label>
            <label>Interest rate<input readOnly value={slabRate ? `${slabRate}% p.a.` : '—'} /><span className="hint">Set by tenure</span></label>
          </div>
          <Notice error={act.error} />
          <button disabled={act.busy}>{act.busy && !quote ? 'Calculating…' : 'Calculate maturity'}</button>
        </form>
        {quote && (<>
          <div className="flow">
            <div><span>Estimated interest</span><b className="cr">{money(quote.totalInterest)}</b></div>
            <div><span>Maturity amount</span><b>{money(quote.maturityAmount)}</b></div>
          </div>
          <AreaChart points={points} height={200} color="#7C3AED" />
          <div className="row" style={{ flexWrap: 'wrap' }}>
            <span className="hint">Matures on {date(quote.maturityDate)} · {quote.formula}</span>
            <button type="button" onClick={book} disabled={act.busy}><Icon n="vault" />{act.busy ? 'Booking…' : `Book FD of ${money(quote.principal)}`}</button>
          </div>
          <p className="hint">The principal is moved from your balance now. The FD starts once the bank approves it, and the money is refunded if it's rejected.</p>
        </>)}
      </Panel>
      <Panel title="Interest rates" icon="trend">
        {r ? (<>
          <div className="activity">
            {r.slabs.map((x, i) => (
              <div key={x.upToMonths}><span className="txn-ic"><Icon n="clock" size={16} /></span>
                <div className="txt"><b>{i === 0 ? `Up to ${x.upToMonths} months` : `${r.slabs[i - 1].upToMonths + 1}–${x.upToMonths} months`}</b><span>Quarterly compounding</span></div>
                <b className="cr">{x.rate}%</b></div>))}
          </div>
          {s && Number(s.totalInvested) > 0 && (
            <div className="donut-wrap">
              <Donut segments={[{ label: 'Principal', value: Number(s.totalInvested), color: '#2457D6' }, { label: 'Interest', value: Number(s.totalExpectedInterest), color: '#7C3AED' }]} size={120} />
              <Legend items={[['Invested', '#2457D6', money(s.totalInvested)], ['Interest', '#7C3AED', money(s.totalExpectedInterest)]]} />
            </div>)}
          <p className="hint">Closing before maturity reduces the rate by {r.prematurePenaltyRate}%; closing within {r.prematureMinDays} days pays no interest.</p>
        </>) : <Loading />}
      </Panel>
    </div>

    <Panel className="flush" title="My fixed deposits" icon="vault">
      {list.loading ? <div style={{ padding: 24 }}><Loading /></div> : list.error ? <div style={{ padding: 24 }}><Notice error={list.error} /></div> : (
        <Table rows={list.data} empty="You have no fixed deposits yet" emptyIcon="vault" onRow={setSelected} selected={selected?.fdId} columns={[
          { key: 'fdId', label: 'Deposit', render: (x) => <div className="txn-cell"><span className="txn-ic"><Icon n="vault" size={16} /></span><div><b>{x.fdId}</b><span className="sub">{x.tenureMonths} months · {x.interestRate}%</span></div></div> },
          { key: 'principal', label: 'Principal', align: 'right', render: (x) => <span className="num">{money(x.principal)}</span> },
          { key: 'maturityAmount', label: 'Maturity amount', align: 'right', render: (x) => <b className="num">{money(x.maturityAmount)}</b> },
          { key: 'maturityDate', label: 'Matures', render: (x) => <>{date(x.maturityDate)}{x.daysToMaturity !== undefined && <span className="sub">{x.daysToMaturity} days left</span>}</> },
          { key: 'status', label: 'Status', render: (x) => <Badge v={x.status} /> },
          { key: 'act', label: '', render: (x) => x.status === 'ACTIVE' && <button type="button" className="ghost sm" onClick={(e) => { e.stopPropagation(); askClose(x); }}>Close</button> },
        ]} />)}
    </Panel>

    {selected && (
      <div style={{ marginTop: 20 }}>
        <Panel title={`Deposit ${selected.fdId}`} icon="file" actions={<button type="button" className="icon-btn" aria-label="Close details" onClick={() => setSelected(null)}><Icon n="close" /></button>}>
          <Details data={selected} fields={[['status', 'Status', (x) => <Badge v={x} />], ['principal', 'Principal', money], ['interestRate', 'Rate', (x) => `${x}% p.a.`],
            ['tenureMonths', 'Tenure', (x) => `${x} months`], ['depositDate', 'Deposit date', date], ['maturityDate', 'Maturity date', date],
            ['expectedInterest', 'Expected interest', money], ['maturityAmount', 'Maturity amount', money], ['closureType', 'Closure', words], ['payoutAmount', 'Paid out', money],
            ['penaltyRate', 'Penalty', (x) => `${x}%`], ['adminComment', 'Bank comment'], ['fundingTransactionId', 'Booking transaction'], ['payoutTransactionId', 'Payout transaction'], ['closedAt', 'Closed on', date]]} />
        </Panel>
      </div>)}
    {dialog && <Dialog {...dialog} onClose={(res) => { setDialog(null); if (res) refresh(); }} />}
  </>);
}
