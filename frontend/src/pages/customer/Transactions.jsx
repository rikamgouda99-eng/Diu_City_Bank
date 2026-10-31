import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { account, txn } from '../../api/services';
import { newKey } from '../../api/client';
import {
  AmountInput, Avatar, Badge, Details, Icon, Loading, Notice, PageHead, Pager, Panel, Sim, Stat, Table, Tabs, money, dateTime, maskAcc, words, useAction, useApi,
} from '../../components/ui';
import { TXN_STATUSES, TXN_TYPES, txnColumns } from '../../components/txn';
import { useAuth } from '../../context/AuthContext';

const TABS = [['transfer', 'Transfer'], ['deposit', 'Deposit'], ['withdraw', 'Withdraw'], ['history', 'History'], ['mini', 'Mini statement']];

export default function Transactions() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.some(([id]) => id === params.get('tab')) ? params.get('tab') : 'transfer';
  const acc = useApi(() => account.me());
  return (<>
    <PageHead title="Transactions" sub="Move money and review your account activity."><Sim /></PageHead>
    {acc.data && (
      <div className="stats">
        <Stat icon="wallet" label="Available balance" value={money(acc.data.availableBalance)} />
        <Stat icon="bank" tone="violet" label="Account" value={maskAcc(acc.data.accountNo)} sub={words(acc.data.accountType)} />
        <Stat icon="shield" tone={acc.data.status === 'ACTIVE' ? 'good' : 'bad'} label="Status" value={words(acc.data.status)} />
      </div>)}
    <Notice error={acc.error} />
    <Tabs value={tab} onChange={(t) => setParams({ tab: t })} tabs={TABS} />
    {tab === 'deposit' && <AmountForm key="d" kind="deposit" account={acc.data} onDone={acc.reload} />}
    {tab === 'withdraw' && <AmountForm key="w" kind="withdraw" account={acc.data} onDone={acc.reload} />}
    {tab === 'transfer' && <Transfer account={acc.data} onDone={acc.reload} />}
    {tab === 'history' && <History />}
    {tab === 'mini' && <MiniStatement />}
  </>);
}

/** Success receipt shared by deposit, withdrawal and transfer. */
function Receipt({ title, amount, status, replayed, from, to, rows, transactionId, onNew }) {
  const dl = useAction();
  const ok = status === 'SUCCESS';
  return (
    <div className="receipt" role="status">
      <div className={`receipt-top ${ok ? '' : 'fail'}`}>
        <div className="tick"><Icon n={ok ? 'check' : 'close'} /></div>
        <p className="muted">{title}</p>
        <div className="amt">{money(amount)}</div>
        <Badge v={status} />
        {replayed && <p className="muted small" style={{ marginTop: 8 }}>Duplicate request: the original result is shown, no money moved twice.</p>}
      </div>
      <div className="receipt-body">
        {from && to && (
          <div className="receipt-parties">
            <div><span>From</span><b>{from.name}</b><small>{from.account}</small></div>
            <Icon n="arrow" />
            <div style={{ textAlign: 'right' }}><span>To</span><b>{to.name}</b><small>{to.account}</small></div>
          </div>)}
        <Details data={rows} fields={Object.keys(rows).map((k) => [k, k])} />
      </div>
      <div className="receipt-foot">
        {transactionId && <button type="button" className="ghost" disabled={dl.busy} onClick={() => dl.run(() => txn.receipt(transactionId), { quiet: true })}><Icon n="down" />Download PDF</button>}
        {onNew && <button type="button" onClick={onNew}>New transaction</button>}
      </div>
      {dl.error && <div style={{ padding: '0 24px 16px' }}><Notice error={dl.error} /></div>}
    </div>);
}

function AmountForm({ kind, account: acc, onDone }) {
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [key, setKey] = useState(newKey);
  const [result, setResult] = useState(null);
  const act = useAction();
  const deposit = kind === 'deposit';
  const balance = acc?.availableBalance;

  const submit = async (e) => {
    e.preventDefault();
    if (!deposit && balance !== undefined && Number(amount) > Number(balance)) {
      act.setError(`Amount is more than your available balance of ${money(balance)}.`);
      return;
    }
    const res = await act.run(() => (deposit ? txn.deposit : txn.withdraw)({ amount, description: description || undefined }, key));
    if (res) { setResult(res.data); setAmount(''); setDescription(''); setKey(newKey()); onDone(); }
  };

  return (
    <div className="two">
      <form className="panel" onSubmit={submit}>
        <div className="panel-head"><div><h3><Icon n={deposit ? 'deposit' : 'withdraw'} />{deposit ? 'Deposit money' : 'Withdraw money'}</h3>
          <p className="sub">{deposit ? 'Simulated cash deposit into your savings account.' : 'Simulated cash withdrawal from your available balance.'}</p></div></div>
        <label>Amount<AmountInput value={amount} onChange={setAmount} max={deposit ? undefined : balance} />
          {!deposit && balance !== undefined && <span className="hint">Available: {money(balance)}</span>}</label>
        <label>Description <span className="hint">Optional</span><input maxLength={200} value={description} onChange={(e) => setDescription(e.target.value)} placeholder={deposit ? 'e.g. Salary cash' : 'e.g. Rent'} /></label>
        <Notice error={act.error} />
        <button disabled={act.busy}>{act.busy ? 'Processing…' : deposit ? 'Deposit' : 'Withdraw'}</button>
      </form>
      {result ? (
        <Receipt title={deposit ? 'Deposit' : 'Withdrawal'} amount={result.amount} status={result.status} replayed={result.replayed} transactionId={result.transactionId}
          rows={{ 'Transaction ID': result.transactionId, Reference: result.reference, 'Updated balance': money(result.balanceAfter), RRN: result.rrn, STAN: result.stan, 'Date & time': dateTime(result.createdAt) }}
          onNew={() => setResult(null)} />
      ) : (
        <Panel title="How it works" icon="shield">
          <p className="muted">Each request carries a unique key, so pressing the button twice or retrying after a network error never {deposit ? 'deposits' : 'withdraws'} twice.
            Every entry gets a reference, an RRN and a STAN, and appears in your history immediately.</p>
        </Panel>)}
    </div>);
}

const STEPS = ['Beneficiary', 'Amount', 'Review', 'Confirm', 'Receipt'];

function Transfer({ account: acc, onDone }) {
  const { user } = useAuth();
  const [step, setStep] = useState(0);
  const [accountNo, setAccountNo] = useState('');
  const [beneficiary, setBeneficiary] = useState(null);
  const [amount, setAmount] = useState('');
  const [remarks, setRemarks] = useState('');
  const [agree, setAgree] = useState(false);
  const [key, setKey] = useState(newKey);
  const [result, setResult] = useState(null);
  const act = useAction();
  const balance = acc?.availableBalance;

  const reset = () => { setStep(0); setAccountNo(''); setBeneficiary(null); setAmount(''); setRemarks(''); setAgree(false); setKey(newKey()); setResult(null); act.clear(); };

  const lookup = async (e) => {
    e.preventDefault();
    const res = await act.run(() => txn.beneficiary(accountNo.trim()), { quiet: true });
    if (!res) return;
    if (res.data.status !== 'ACTIVE') { act.setError('This beneficiary account is not active, money can\'t be sent to it.'); return; }
    setBeneficiary(res.data); setStep(1);
  };
  const toReview = (e) => {
    e.preventDefault(); act.clear();
    if (balance !== undefined && Number(amount) > Number(balance)) { act.setError(`Amount is more than your available balance of ${money(balance)}.`); return; }
    setStep(2);
  };
  const send = async () => {
    // the same key is reused on retry, so a double click can't send twice
    const res = await act.run(() => txn.transfer({ beneficiaryAccountNo: beneficiary.accountNo, amount, remarks: remarks || undefined }, key));
    if (res) { setResult(res.data); setStep(4); onDone(); }
  };

  return (
    <div className={step === 4 ? 'narrow' : 'two'} style={step === 4 ? { margin: '0 auto' } : undefined}>
      {step < 4 && (
        <section className="panel">
          <div className="panel-head"><div><h3><Icon n="send" />Send money</h3><p className="sub">Transfer to any DIU City Bank account.</p></div></div>
          <ol className="steps" aria-label="Progress">
            {STEPS.map((s, i) => <li key={s} className={`${i <= step ? 'on' : ''} ${i === step ? 'cur' : ''}`} aria-current={i === step ? 'step' : undefined}>{s}</li>)}
          </ol>

          {step === 0 && (
            <form onSubmit={lookup} className="stack">
              <label>Beneficiary account number
                <input required autoFocus inputMode="numeric" pattern="\d{8,20}" title="8-20 digits" value={accountNo}
                  onChange={(e) => setAccountNo(e.target.value.replace(/\D/g, ''))} placeholder="e.g. 3567103100000001" />
                <span className="hint">We'll check the account and show the holder's name before you send anything.</span></label>
              <Notice error={act.error} />
              <button disabled={act.busy}>{act.busy ? 'Verifying…' : 'Verify beneficiary'}</button>
            </form>)}

          {step > 0 && beneficiary && (
            <div className="beneficiary">
              <Avatar name={beneficiary.holderName} />
              <div><b>{beneficiary.holderName}</b><span>{beneficiary.accountNo} · {beneficiary.bankName}</span></div>
              <Badge v={beneficiary.status} />
            </div>)}

          {step === 1 && (
            <form onSubmit={toReview} className="stack">
              <label>Amount<AmountInput autoFocus value={amount} onChange={setAmount} max={balance} />
                {balance !== undefined && <span className="hint">Available: {money(balance)}</span>}</label>
              <label>Remarks <span className="hint">Optional</span><input maxLength={200} value={remarks} onChange={(e) => setRemarks(e.target.value)} placeholder="e.g. Rent for October" /></label>
              <Notice error={act.error} />
              <div className="row"><button type="button" className="ghost" onClick={() => { setStep(0); act.clear(); }}><Icon n="left" />Change</button><button>Continue</button></div>
            </form>)}

          {step === 2 && (
            <div className="stack">
              <Details data={{ from: acc ? maskAcc(acc.accountNo) : '', to: beneficiary.accountNo, name: beneficiary.holderName, amount, remarks, after: balance !== undefined ? Number(balance) - Number(amount) : null }}
                fields={[['from', 'From'], ['to', 'To account'], ['name', 'Beneficiary'], ['amount', 'Amount', money], ['remarks', 'Remarks'], ['after', 'Balance after', money]]} />
              <div className="row"><button type="button" className="ghost" onClick={() => setStep(1)}><Icon n="left" />Edit</button><button type="button" onClick={() => setStep(3)}>Review done</button></div>
            </div>)}

          {step === 3 && (
            <div className="stack">
              <div className="flow"><div><span>You are sending</span><b>{money(amount)}</b></div><div><span>To</span><b style={{ fontSize: '1rem' }}>{beneficiary.holderName}</b></div></div>
              <label className="check"><input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />I confirm the beneficiary and amount are correct. This simulated transfer can't be reversed.</label>
              <Notice error={act.error} />
              <div className="row"><button type="button" className="ghost" onClick={() => setStep(2)}><Icon n="left" />Back</button>
                <button type="button" onClick={send} disabled={!agree || act.busy}>{act.busy ? 'Sending…' : `Send ${money(amount)}`}</button></div>
            </div>)}
        </section>)}

      {step < 4 && (
        <Panel title="Transfer safety" icon="shield">
          <ul className="muted" style={{ margin: 0, paddingLeft: 18, display: 'grid', gap: 8 }}>
            <li>Debit and credit happen together: if anything fails, nothing moves.</li>
            <li>Only the beneficiary's masked name is shown, never their balance or ID.</li>
            <li>Double clicks and retries are recognised and never send twice.</li>
          </ul>
        </Panel>)}

      {step === 4 && result && (
        <Receipt title="Transfer" amount={result.amount} status={result.status} replayed={result.replayed} transactionId={result.debitTransactionId}
          from={{ name: user.name, account: acc ? maskAcc(acc.accountNo) : maskAcc(result.fromAccount) }}
          to={{ name: result.beneficiaryName, account: result.toAccount }}
          rows={{ Reference: result.reference, Remarks: result.remarks || '—', 'Your balance now': money(result.balanceAfter), 'Date & time': dateTime(result.createdAt) }}
          onNew={reset} />)}
    </div>);
}

function History() {
  const [filters, setFilters] = useState({ reference: '', type: '', status: '', from: '', to: '', page: 0, size: 10 });
  const [applied, setApplied] = useState(filters);
  const list = useApi(() => txn.history(applied), [applied]);
  const [selected, setSelected] = useState(null);
  const dl = useAction();
  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });
  const apply = (e) => {
    e.preventDefault();
    if (filters.from && filters.to && filters.from > filters.to) { dl.setError('"From" date must be before "To" date.'); return; }
    dl.clear(); setApplied({ ...filters, page: 0 });
  };
  const clear = () => { const f = { reference: '', type: '', status: '', from: '', to: '', page: 0, size: 10 }; setFilters(f); setApplied(f); };

  return (<>
    <form className="panel" onSubmit={apply} style={{ marginBottom: 20 }}>
      <div className="filters">
        <label>Reference / ID<input value={filters.reference} onChange={set('reference')} placeholder="TRF…" /></label>
        <label>Type<select value={filters.type} onChange={set('type')}><option value="">All</option>{TXN_TYPES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        <label>Status<select value={filters.status} onChange={set('status')}><option value="">All</option>{TXN_STATUSES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        <label>From<input type="date" value={filters.from} onChange={set('from')} /></label>
        <label>To<input type="date" value={filters.to} onChange={set('to')} /></label>
      </div>
      <div className="row">
        <button type="button" className="ghost" disabled={dl.busy} onClick={() => dl.run(() => txn.statement(filters.from, filters.to), { quiet: true })}><Icon n="down" />Statement PDF</button>
        <div className="row gap"><button type="button" className="ghost" onClick={clear}>Clear</button><button><Icon n="search" />Apply filters</button></div>
      </div>
      <Notice error={dl.error} />
    </form>
    <Panel className="flush" title="Transaction history" icon="swap" sub={list.data ? `${list.data.totalElements} transactions` : undefined}>
      {list.loading ? <div style={{ padding: 24 }}><Loading rows={6} /></div> : list.error ? <div style={{ padding: 24 }}><Notice error={list.error} /></div> : (<>
        <Table rows={list.data.content} empty="No transactions match these filters" emptyIcon="search" onRow={setSelected} selected={selected?.transactionId}
          columns={txnColumns({ receipt: (id) => dl.run(() => txn.receipt(id), { quiet: true }) })} />
        <Pager page={list.data} onPage={(p) => setApplied({ ...applied, page: p })} />
      </>)}
    </Panel>
    {selected && (
      <div style={{ marginTop: 20 }}>
        <Panel title="Transaction details" icon="file" actions={<button type="button" className="icon-btn" aria-label="Close details" onClick={() => setSelected(null)}><Icon n="close" /></button>}>
          <Details data={selected} fields={[['transactionId', 'Transaction ID'], ['reference', 'Reference'], ['type', 'Type', words], ['direction', 'Direction', words],
            ['amount', 'Amount', money], ['balanceBefore', 'Previous balance', money], ['balanceAfter', 'Updated balance', money], ['description', 'Description'],
            ['counterpartyAccount', 'Counterparty'], ['status', 'Status', (v) => <Badge v={v} />], ['rrn', 'RRN'], ['stan', 'STAN'], ['channel', 'Channel', words], ['createdAt', 'Date & time', dateTime]]} />
        </Panel>
      </div>)}
  </>);
}

function MiniStatement() {
  const [count, setCount] = useState(5);
  const list = useApi(() => txn.mini(count), [count]);
  const dl = useAction();
  return (
    <Panel className="flush" title="Mini statement" icon="list" sub={`Your last ${count} transactions`} actions={
      <div className="row gap">
        <select value={count} onChange={(e) => setCount(Number(e.target.value))} aria-label="Number of transactions"><option value={5}>Last 5</option><option value={10}>Last 10</option></select>
        <button type="button" className="ghost sm" disabled={dl.busy} onClick={() => dl.run(() => txn.statement(), { quiet: true })}><Icon n="down" />Last month PDF</button>
      </div>}>
      {dl.error && <div style={{ padding: '12px 24px 0' }}><Notice error={dl.error} /></div>}
      {list.loading ? <div style={{ padding: 24 }}><Loading /></div> : list.error ? <div style={{ padding: 24 }}><Notice error={list.error} /></div>
        : <Table rows={list.data} empty="No transactions yet" emptyIcon="list" columns={txnColumns()} />}
    </Panel>);
}
