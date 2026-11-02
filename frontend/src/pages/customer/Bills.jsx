import { useState } from 'react';
import { bills, cards } from '../../api/services';
import { newKey } from '../../api/client';
import { AmountInput, Badge, Details, Icon, Loading, Notice, PageHead, Pager, Panel, Sim, Table, money, dateTime, words, useAction, useApi } from '../../components/ui';

const EMPTY = { billType: 'ELECTRICITY', billerName: '', consumerNumber: '', amount: '', paymentMode: 'DEBIT_CARD', cardId: '', cardPin: '', otp: '' };
const BILL_ICON = { ELECTRICITY: 'trend', WATER: 'deposit', MOBILE_RECHARGE: 'send', INTERNET: 'grid', GAS: 'alert', DTH: 'eye', OTHER: 'receipt' };
const RECHARGE_PLANS = ['199', '299', '479', '719'];
const MODE_LABEL = { DEBIT_CARD: 'Debit card', CREDIT_CARD: 'Credit card', BANK_DRAFT: 'Savings account (bank draft)' };

export default function Bills() {
  const options = useApi(() => bills.options());
  const myCards = useApi(() => cards.list());
  const [page, setPage] = useState(0);
  const history = useApi(() => bills.history(page), [page]);
  const [v, setV] = useState(EMPTY);
  const [key, setKey] = useState(newKey);
  const [result, setResult] = useState(null);
  const act = useAction();
  const otp = useAction();
  const [otpSent, setOtpSent] = useState(false);
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });

  const cardMode = v.paymentMode === 'DEBIT_CARD' || v.paymentMode === 'CREDIT_CARD';
  const credit = v.paymentMode === 'CREDIT_CARD';
  const usable = (myCards.data || []).filter((c) => c.status === 'ACTIVE' && c.cardType === (v.paymentMode === 'DEBIT_CARD' ? 'DEBIT' : 'CREDIT'));
  const types = options.data?.billTypes || ['ELECTRICITY'];
  const modes = options.data?.paymentModes || ['DEBIT_CARD', 'CREDIT_CARD', 'BANK_DRAFT'];
  const biller = options.data?.billers?.[v.billType];
  const recharge = v.billType === 'MOBILE_RECHARGE';

  // every bill type asks for its own number and provider, so start fresh when it changes
  const chooseType = (t) => { setV({ ...EMPTY, billType: t, paymentMode: v.paymentMode, cardId: v.cardId }); act.clear(); };
  const chooseMode = (e) => { setV({ ...v, paymentMode: e.target.value, cardId: '', cardPin: '', otp: '' }); setOtpSent(false); otp.clear(); };
  const sendOtp = async () => {
    if (!v.cardId) { otp.setError('Please choose a card first.'); return; }
    if (await otp.run(() => cards.sendOtp(v.cardId))) setOtpSent(true);
  };

  const submit = async (e) => {
    e.preventDefault();
    if (cardMode && !v.cardId) { act.setError('Please choose a card.'); return; }
    if (credit && !otpSent) { act.setError('Please send the OTP to your email and enter it.'); return; }
    const body = { billType: v.billType, billerName: v.billerName.trim(), consumerNumber: v.consumerNumber.trim(), amount: v.amount, paymentMode: v.paymentMode,
      ...(cardMode && { cardId: Number(v.cardId), cardPin: v.cardPin }), ...(credit && { otp: v.otp }) };
    const res = await act.run(() => bills.pay(body, key));
    if (res) { setResult(res.data); setV({ ...EMPTY, billType: v.billType, paymentMode: v.paymentMode }); setKey(newKey()); setOtpSent(false); history.reload(); myCards.reload(); }
  };

  return (<>
    <PageHead title="Bill payments" sub="Recharge your mobile and pay utility bills from your account or cards."><Sim /></PageHead>
    <div className="two">
      <form className="panel" onSubmit={submit}>
        <div className="panel-head"><div><h3><Icon n="receipt" />{recharge ? 'Mobile recharge' : 'Pay a bill'}</h3>
          <p className="sub">{recharge ? 'Enter the mobile number and choose the network provider.' : 'Choose a category, the provider and enter your number.'}</p></div></div>
        <div className="seg" role="radiogroup" aria-label="Bill type" style={{ marginBottom: 0 }}>
          {types.map((t) => <button key={t} type="button" role="radio" aria-checked={v.billType === t} className={v.billType === t ? 'on' : ''} onClick={() => chooseType(t)}>
            <Icon n={BILL_ICON[t] || 'receipt'} size={14} />{words(t)}</button>)}
        </div>
        {biller ? (
          <div className="form-grid">
            <label>{biller.numberLabel}
              <input required pattern={biller.numberPattern} title={biller.numberHint} value={v.consumerNumber}
                inputMode={recharge || v.billType === 'DTH' ? 'numeric' : undefined} maxLength={recharge ? 10 : 30}
                onChange={(e) => setV({ ...v, consumerNumber: recharge ? e.target.value.replace(/\D/g, '') : e.target.value })}
                placeholder={recharge ? 'e.g. 9876543210' : biller.numberHint} />
              <span className="hint">{biller.numberHint}</span></label>
            <label>{biller.providerLabel}
              {biller.providers.length ? (
                <select required value={v.billerName} onChange={set('billerName')}>
                  <option value="">Choose {biller.providerLabel.toLowerCase()}</option>
                  {biller.providers.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              ) : <input required maxLength={100} value={v.billerName} onChange={set('billerName')} placeholder="e.g. Diu Housing Society" />}</label>
          </div>
        ) : <Loading rows={2} />}
        <label>{recharge ? 'Recharge amount' : 'Amount'}<AmountInput value={v.amount} onChange={(a) => setV({ ...v, amount: a })} />
          {recharge && (
            <span className="row gap" style={{ justifyContent: 'flex-start', flexWrap: 'wrap', marginTop: 6 }}>
              {RECHARGE_PLANS.map((p) => <button key={p} type="button" className={v.amount === p ? 'sm' : 'ghost sm'} aria-pressed={v.amount === p} onClick={() => setV({ ...v, amount: p })}>{money(p)}</button>)}
            </span>)}</label>
        <label>Pay with<select value={v.paymentMode} onChange={chooseMode}>{modes.map((t) => <option key={t} value={t}>{MODE_LABEL[t] || words(t)}</option>)}</select></label>
        {cardMode && (
          <div className="form-grid">
            <label>Card<select required value={v.cardId} onChange={(e) => { setV({ ...v, cardId: e.target.value, otp: '' }); setOtpSent(false); }}>
              <option value="">{usable.length ? 'Choose a card' : 'No active card of this type'}</option>
              {usable.map((c) => <option key={c.cardId} value={c.cardId}>{c.cardScheme} {c.maskedNumber}</option>)}</select></label>
            <label>Card PIN<input type="password" required inputMode="numeric" pattern="\d{4}" maxLength={4} value={v.cardPin} onChange={set('cardPin')} autoComplete="off" placeholder="••••" /></label>
          </div>)}
        {credit && (
          <label>Email OTP
            <span className="row gap">
              <input required={otpSent} inputMode="numeric" pattern="\d{6}" maxLength={6} value={v.otp} disabled={!otpSent} autoComplete="one-time-code"
                onChange={(e) => setV({ ...v, otp: e.target.value.replace(/\D/g, '') })} placeholder={otpSent ? '6 digit OTP' : 'Send OTP first'} style={{ flex: 1 }} />
              <button type="button" className="ghost" disabled={otp.busy || !v.cardId} onClick={sendOtp}>{otp.busy ? 'Sending…' : otpSent ? 'Resend OTP' : 'Send OTP'}</button>
            </span>
            <span className="hint">{otpSent ? 'OTP sent to your registered email. It is valid for 5 minutes.' : 'Credit card payments are confirmed with your card PIN and an OTP sent to your email.'}</span></label>)}
        <Notice error={otp.error} />
        <Notice error={act.error || options.error} />
        <button disabled={act.busy}>{act.busy ? 'Paying…' : v.amount ? `${recharge ? 'Recharge' : 'Pay'} ${money(v.amount)}` : recharge ? 'Recharge' : 'Pay bill'}</button>
      </form>
      {result ? (
        <div className="receipt" role="status">
          <div className="receipt-top"><div className="tick"><Icon n="check" /></div><p className="muted">{result.billType === 'MOBILE_RECHARGE' ? 'Mobile recharge successful' : `${words(result.billType)} bill paid`}</p>
            <div className="amt">{money(result.amount)}</div><Badge v={result.status} /></div>
          <div className="receipt-body">
            <Details data={result} fields={[['reference', 'Reference'],
              ['consumerNumber', result.billType === 'MOBILE_RECHARGE' ? 'Mobile number' : 'Consumer no.'], ['billerName', result.billType === 'MOBILE_RECHARGE' ? 'Operator' : 'Biller'],
              ['paymentMode', 'Paid by', (m) => MODE_LABEL[m] || words(m)], ['cardMasked', 'Card'], ['transactionId', 'Transaction ID'], ['paymentDate', 'Date', dateTime]]} />
            <p className="hint" style={{ marginTop: 12 }}><Icon n="check" size={13} /> A confirmation has been sent to your email.</p>
          </div>
          <div className="receipt-foot"><button type="button" onClick={() => setResult(null)}>{result.billType === 'MOBILE_RECHARGE' ? 'New recharge' : 'Pay another bill'}</button></div>
        </div>
      ) : (
        <Panel title="Payment options" icon="wallet">
          <Details data={{ draft: 'Debited from your savings account', debit: 'Debited from savings, counts toward the daily card limit', credit: 'Uses your credit limit, confirmed with PIN and email OTP' }}
            fields={[['debit', 'Debit card'], ['credit', 'Credit card'], ['draft', 'Bank draft']]} />
        </Panel>)}
    </div>
    <Panel className="flush" title="Payment history" icon="clock">
      {history.loading ? <div style={{ padding: 24 }}><Loading /></div> : history.error ? <div style={{ padding: 24 }}><Notice error={history.error} /></div> : (<>
        <Table rows={history.data.content} empty="No bills paid yet" emptyIcon="receipt" columns={[
          { key: 'billType', label: 'Bill', render: (r) => <div className="txn-cell"><span className="txn-ic dr"><Icon n={BILL_ICON[r.billType] || 'receipt'} size={16} /></span><div><b>{words(r.billType)}</b><span className="sub">{r.consumerNumber}{r.billerName ? ` · ${r.billerName}` : ''}</span></div></div> },
          { key: 'reference', label: 'Reference' },
          { key: 'paymentMode', label: 'Paid by', render: (r) => MODE_LABEL[r.paymentMode] || words(r.paymentMode) },
          { key: 'paymentDate', label: 'Date', render: (r) => dateTime(r.paymentDate) },
          { key: 'amount', label: 'Amount', align: 'right', render: (r) => <b className="num">{money(r.amount)}</b> },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
        ]} />
        <Pager page={history.data} onPage={setPage} />
      </>)}
    </Panel>
  </>);
}
