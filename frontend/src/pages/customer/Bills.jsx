import { useState } from 'react';
import { bills, cards } from '../../api/services';
import { newKey } from '../../api/client';
import { AmountInput, Badge, Details, Icon, Loading, Notice, PageHead, Pager, Panel, Sim, Table, money, dateTime, words, useAction, useApi } from '../../components/ui';

const EMPTY = { billType: 'ELECTRICITY', billerName: '', consumerNumber: '', amount: '', paymentMode: 'UPI', cardId: '', cardPin: '' };
const BILL_ICON = { ELECTRICITY: 'trend', WATER: 'deposit', MOBILE_RECHARGE: 'send', INTERNET: 'grid', GAS: 'alert', DTH: 'eye', OTHER: 'receipt' };

export default function Bills() {
  const options = useApi(() => bills.options());
  const myCards = useApi(() => cards.list());
  const [page, setPage] = useState(0);
  const history = useApi(() => bills.history(page), [page]);
  const [v, setV] = useState(EMPTY);
  const [key, setKey] = useState(newKey);
  const [result, setResult] = useState(null);
  const act = useAction();
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });

  const cardMode = v.paymentMode === 'DEBIT_CARD' || v.paymentMode === 'CREDIT_CARD';
  const usable = (myCards.data || []).filter((c) => c.status === 'ACTIVE' && c.cardType === (v.paymentMode === 'DEBIT_CARD' ? 'DEBIT' : 'CREDIT'));
  const types = options.data?.billTypes || ['ELECTRICITY'];

  const submit = async (e) => {
    e.preventDefault();
    if (cardMode && !v.cardId) { act.setError('Please choose a card.'); return; }
    const body = { billType: v.billType, billerName: v.billerName || undefined, consumerNumber: v.consumerNumber.trim(), amount: v.amount, paymentMode: v.paymentMode,
      ...(cardMode && { cardId: Number(v.cardId), cardPin: v.cardPin }) };
    const res = await act.run(() => bills.pay(body, key));
    if (res) { setResult(res.data); setV(EMPTY); setKey(newKey()); history.reload(); myCards.reload(); }
  };

  return (<>
    <PageHead title="Bill payments" sub="Pay utility bills from your account or cards."><Sim /></PageHead>
    <div className="two">
      <form className="panel" onSubmit={submit}>
        <div className="panel-head"><div><h3><Icon n="receipt" />Pay a bill</h3><p className="sub">Choose a biller category, then enter the consumer number.</p></div></div>
        <div className="seg" role="radiogroup" aria-label="Bill type" style={{ marginBottom: 0 }}>
          {types.map((t) => <button key={t} type="button" role="radio" aria-checked={v.billType === t} className={v.billType === t ? 'on' : ''} onClick={() => setV({ ...v, billType: t })}>
            <Icon n={BILL_ICON[t] || 'receipt'} size={14} />{words(t)}</button>)}
        </div>
        <div className="form-grid">
          <label>Consumer / customer number<input required pattern="[A-Za-z0-9\-]{4,30}" title="4-30 letters, digits or hyphens" value={v.consumerNumber} onChange={set('consumerNumber')} placeholder="e.g. EL-445566" /></label>
          <label>Biller name <span className="hint">Optional</span><input maxLength={100} value={v.billerName} onChange={set('billerName')} placeholder="e.g. Diu Electricity Dept." /></label>
        </div>
        <label>Amount<AmountInput value={v.amount} onChange={(a) => setV({ ...v, amount: a })} /></label>
        <label>Pay with<select value={v.paymentMode} onChange={set('paymentMode')}>{(options.data?.paymentModes || ['UPI']).map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        {cardMode && (
          <div className="form-grid">
            <label>Card<select required value={v.cardId} onChange={set('cardId')}>
              <option value="">{usable.length ? 'Choose a card' : 'No active card of this type'}</option>
              {usable.map((c) => <option key={c.cardId} value={c.cardId}>{c.cardScheme} {c.maskedNumber}</option>)}</select></label>
            <label>Card PIN<input type="password" required inputMode="numeric" pattern="\d{4}" maxLength={4} value={v.cardPin} onChange={set('cardPin')} autoComplete="off" placeholder="••••" /></label>
          </div>)}
        <Notice error={act.error || options.error} />
        <button disabled={act.busy}>{act.busy ? 'Paying…' : v.amount ? `Pay ${money(v.amount)}` : 'Pay bill'}</button>
      </form>
      {result ? (
        <div className="receipt" role="status">
          <div className="receipt-top"><div className="tick"><Icon n="check" /></div><p className="muted">{words(result.billType)} bill paid</p>
            <div className="amt">{money(result.amount)}</div><Badge v={result.status} /></div>
          <div className="receipt-body">
            <Details data={result} fields={[['reference', 'Reference'], ['consumerNumber', 'Consumer no.'], ['billerName', 'Biller'], ['paymentMode', 'Paid by', words],
              ['cardMasked', 'Card'], ['transactionId', 'Transaction ID'], ['paymentDate', 'Date', dateTime]]} />
          </div>
          <div className="receipt-foot"><button type="button" onClick={() => setResult(null)}>Pay another bill</button></div>
        </div>
      ) : (
        <Panel title="Payment options" icon="wallet">
          <Details data={{ upi: 'Debited from savings', draft: 'Debited from savings', debit: 'Debited from savings, counts toward the daily card limit', credit: 'Uses your credit limit' }}
            fields={[['upi', 'UPI'], ['draft', 'Bank draft'], ['debit', 'Debit card'], ['credit', 'Credit card']]} />
        </Panel>)}
    </div>
    <Panel className="flush" title="Payment history" icon="clock">
      {history.loading ? <div style={{ padding: 24 }}><Loading /></div> : history.error ? <div style={{ padding: 24 }}><Notice error={history.error} /></div> : (<>
        <Table rows={history.data.content} empty="No bills paid yet" emptyIcon="receipt" columns={[
          { key: 'billType', label: 'Bill', render: (r) => <div className="txn-cell"><span className="txn-ic dr"><Icon n={BILL_ICON[r.billType] || 'receipt'} size={16} /></span><div><b>{words(r.billType)}</b><span className="sub">{r.consumerNumber}</span></div></div> },
          { key: 'reference', label: 'Reference' },
          { key: 'paymentMode', label: 'Paid by', render: (r) => words(r.paymentMode) },
          { key: 'paymentDate', label: 'Date', render: (r) => dateTime(r.paymentDate) },
          { key: 'amount', label: 'Amount', align: 'right', render: (r) => <b className="num">{money(r.amount)}</b> },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
        ]} />
        <Pager page={history.data} onPage={setPage} />
      </>)}
    </Panel>
  </>);
}
