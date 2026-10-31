import { useEffect, useState } from 'react';
import { cards } from '../../api/services';
import { newKey } from '../../api/client';
import { Badge, Details, Dialog, EmptyState, Icon, Loading, Notice, PageHead, Pager, Panel, Sim, Table, money, date, dateTime, words, useAction, useApi } from '../../components/ui';

/** Simulated card face. Only the masked number is ever available. */
export function BankCard({ card, selected, onClick }) {
  const issued = !!card.maskedNumber;
  return (
    <button type="button" className={`bankcard ${card.cardType.toLowerCase()} ${selected ? 'sel' : ''} ${['BLOCKED', 'REJECTED', 'REPLACED'].includes(card.status) ? 'muted-card' : ''}`}
      onClick={onClick} aria-label={`${card.cardScheme} ${card.cardType} card ${card.maskedNumber || 'application'}`}>
      <div className="top"><b>DIU City Bank<small>{card.cardType === 'DEBIT' ? 'Debit' : 'Credit'} · Simulation</small></b><span className="scheme">{card.cardScheme === 'MASTERCARD' ? 'mastercard' : card.cardScheme}</span></div>
      <div className="row"><span className="chip" />{card.status !== 'ACTIVE' && <Badge v={card.status} />}</div>
      <div className="pan">{issued ? card.maskedNumber.replace(/X/g, '•') : 'Application in review'}</div>
      <div className="bottom">
        <div><span>Card holder</span><b>{card.holderName}</b></div>
        <div style={{ textAlign: 'right' }}><span>Valid thru</span><b>{card.expiry || '—/—'}</b></div>
      </div>
    </button>);
}

export default function Cards() {
  const list = useApi(() => cards.list());
  const [selectedId, setSelectedId] = useState(null);
  const all = list.data || [];
  const selected = all.find((c) => c.cardId === selectedId);
  useEffect(() => { if (!selectedId && all.length) setSelectedId((all.find((c) => c.status === 'ACTIVE') || all[0]).cardId); }, [all, selectedId]);

  const section = (type, title) => {
    const rows = all.filter((c) => c.cardType === type);
    return (
      <div className="card-section">
        <div className="row" style={{ marginBottom: 12 }}><h4 style={{ margin: 0 }}>{title}</h4><span className="muted small">{rows.length} card{rows.length === 1 ? '' : 's'}</span></div>
        {rows.length ? <div className="cards">{rows.map((c) => <BankCard key={c.cardId} card={c} selected={c.cardId === selectedId} onClick={() => setSelectedId(c.cardId)} />)}</div>
          : <EmptyState icon="card" title={`No ${title.toLowerCase()} yet`} text="Apply using the form on this page." />}
      </div>);
  };

  return (<>
    <PageHead title="Cards" sub="Manage your simulated debit and credit cards."><Sim /></PageHead>
    <div className="split">
      <Panel title="My cards" icon="card">
        {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>{section('DEBIT', 'Debit cards')}{section('CREDIT', 'Credit cards')}</>)}
      </Panel>
      <Apply onDone={list.reload} existing={all} />
    </div>
    {selected && <CardDetail card={selected} onChange={list.reload} />}
  </>);
}

function Apply({ onDone, existing }) {
  const [v, setV] = useState({ cardType: 'DEBIT', cardScheme: 'VISA', requestedLimit: '' });
  const act = useAction();
  const blocked = existing.some((c) => c.cardType === v.cardType && ['APPLIED', 'ACTIVE', 'BLOCKED'].includes(c.status));
  const submit = async (e) => {
    e.preventDefault();
    const res = await act.run(() => cards.apply({ cardType: v.cardType, cardScheme: v.cardScheme, requestedLimit: v.cardType === 'CREDIT' && v.requestedLimit ? v.requestedLimit : undefined }));
    if (res) onDone();
  };
  return (
    <form className="panel" onSubmit={submit}>
      <div className="panel-head"><div><h3><Icon n="plus" />Apply for a card</h3><p className="sub">Every application is reviewed by the bank.</p></div></div>
      <div className="seg" role="radiogroup" aria-label="Card type" style={{ marginBottom: 0 }}>
        {['DEBIT', 'CREDIT'].map((t) => <button key={t} type="button" role="radio" aria-checked={v.cardType === t} className={v.cardType === t ? 'on' : ''} onClick={() => setV({ ...v, cardType: t })}>{words(t)} card</button>)}
      </div>
      <label>Card scheme<select value={v.cardScheme} onChange={(e) => setV({ ...v, cardScheme: e.target.value })}><option>VISA</option><option>MASTERCARD</option><option>RUPAY</option></select></label>
      {v.cardType === 'CREDIT' && <label>Requested credit limit <span className="hint">Optional · the bank decides the final limit</span>
        <input type="number" min="1000" step="1000" value={v.requestedLimit} onChange={(e) => setV({ ...v, requestedLimit: e.target.value })} placeholder="e.g. 50000" /></label>}
      <p className="hint">{v.cardType === 'DEBIT' ? 'Linked to your savings account with a daily spend limit.' : 'Spends use your credit line; repay dues from your savings account.'}</p>
      {blocked && <p className="msg ok">You already have a {v.cardType.toLowerCase()} card or an application in review. Use “Replace card” for a new one.</p>}
      <Notice error={act.error} />
      <button disabled={act.busy || blocked}>{act.busy ? 'Submitting…' : 'Submit application'}</button>
    </form>);
}

function CardDetail({ card, onChange }) {
  const [dialog, setDialog] = useState(null);
  const [page, setPage] = useState(0);
  const issued = !!card.issuedDate;
  const txns = useApi(() => (issued ? cards.transactions(card.cardId, page) : Promise.resolve({ data: null })), [card.cardId, page, card.creditUsed, card.spentToday]);
  const act = useAction();
  const refresh = () => { onChange(); txns.reload(); };
  const credit = card.cardType === 'CREDIT';
  const used = Number(credit ? card.creditUsed : card.spentToday) || 0;
  const limit = Number(card.cardLimit) || 0;
  const pct = limit ? Math.min(100, (used / limit) * 100) : 0;
  const pinField = (name, label) => ({ name, label, type: 'password', required: true, pattern: '\\d{4}', maxLength: 4, inputMode: 'numeric', placeholder: '••••' });

  const pinDialog = () => setDialog({
    title: card.pinSet ? 'Change card PIN' : 'Set card PIN', text: 'Your PIN is stored encrypted and is never shown again.',
    fields: [...(card.pinSet ? [pinField('oldPin', 'Current PIN')] : []), pinField('newPin', 'New 4-digit PIN'), pinField('confirm', 'Confirm new PIN')],
    confirm: card.pinSet ? 'Change PIN' : 'Set PIN',
    onSubmit: (v) => { if (v.newPin !== v.confirm) throw new Error('PINs do not match.'); return cards.pin(card.cardId, { oldPin: v.oldPin || undefined, newPin: v.newPin }); },
  });
  const simulateDialog = () => {
    const key = newKey();
    setDialog({
      title: `Simulate a ${card.cardType.toLowerCase()} card purchase`,
      text: credit ? `Available credit: ${money(card.availableCredit)}` : `Available today: ${money(card.availableToday)}`,
      fields: [{ name: 'merchantName', label: 'Merchant', required: true, maxLength: 100, placeholder: 'e.g. Diu Fort Cafe' },
        { name: 'amount', label: 'Amount (₹)', type: 'number', step: '0.01', min: '0.01', required: true }, pinField('pin', 'Card PIN')],
      confirm: 'Authorise payment', onSubmit: (v) => cards.simulate(card.cardId, v, key),
    });
  };
  const duesDialog = () => {
    const key = newKey();
    setDialog({
      title: 'Pay credit card dues', text: `Outstanding ${money(card.creditUsed)} · minimum due ${money(card.billing?.minimumDue)}. Paid from your savings account.`,
      fields: [{ name: 'amount', label: 'Amount (₹)', type: 'number', step: '0.01', min: '0.01', required: true, value: card.billing?.minimumDue || '' }],
      confirm: 'Pay dues', onSubmit: (v) => cards.payDues(card.cardId, v, key),
    });
  };
  const confirmDialog = (title, text, fn, danger, confirm) => setDialog({ title, text, danger, confirm, onSubmit: fn });

  return (<>
    <div className="two">
      <Panel title={`${card.cardScheme} ${words(card.cardType)} card`} icon="card" actions={<Badge v={card.status} />}>
        {issued ? (<>
          <div className="row"><span className="muted small">{credit ? 'Credit used' : 'Spent today'}</span><span className="small"><b className="num">{money(used)}</b> of {money(limit)}</span></div>
          <div className={`progress ${pct > 80 ? 'warn' : ''}`}><i style={{ width: `${pct}%` }} /></div>
          <div className="info-grid">
            <div><span>{credit ? 'Credit limit' : 'Daily limit'}</span><b className="num">{money(card.cardLimit)}</b></div>
            <div><span>{credit ? 'Available credit' : 'Available today'}</span><b className="num cr">{money(credit ? card.availableCredit : card.availableToday)}</b></div>
            <div><span>Card number</span><b className="num">{card.maskedNumber}</b></div>
            <div><span>Valid thru</span><b>{card.expiry}</b></div>
            <div><span>Linked account</span><b className="num">{card.linkedAccount}</b></div>
            <div><span>PIN</span><b>{card.pinSet ? 'Set' : 'Not set'}</b></div>
          </div>
          {card.billing && (<><h4>Billing</h4>
            <Details data={card.billing} fields={[['totalOutstanding', 'Total outstanding', money], ['minimumDue', 'Minimum due', money], ['statementDate', 'Statement date', date], ['paymentDueDate', 'Payment due', date]]} /></>)}
        </>) : (
          <div className="stack">
            <Details data={card} fields={[['status', 'Application status', (x) => <Badge v={x} />], ['appliedAt', 'Applied on', dateTime], ['requestedLimit', 'Requested limit', money], ['adminComment', 'Bank comment']]} />
            <p className="muted">{card.status === 'APPLIED' ? 'Your application is with the bank. You will be able to use the card once it is issued.' : 'This application was not approved.'}</p>
          </div>)}
        <p className="hint"><Icon n="shield" size={13} /> The full card number, CVV and PIN are never stored or shown.</p>
      </Panel>
      <Panel title="Card controls" icon="settings">
        {issued ? (
          <div className="actions">
            {card.status === 'ACTIVE' && <button type="button" onClick={simulateDialog} disabled={!card.pinSet} title={card.pinSet ? '' : 'Set a PIN first'}><Icon n="send" />Simulate purchase</button>}
            {card.status === 'ACTIVE' && <button type="button" className="ghost" onClick={pinDialog}><Icon n="lock" />{card.pinSet ? 'Change PIN' : 'Set PIN'}</button>}
            {credit && Number(card.creditUsed) > 0 && <button type="button" className="ghost" onClick={duesDialog}><Icon n="wallet" />Pay dues</button>}
            {card.status === 'ACTIVE' && <button type="button" className="ghost" onClick={() => confirmDialog('Block this card?', 'The card stops working until you unblock it.', () => cards.block(card.cardId), true, 'Block card')}><Icon n="x" />Block</button>}
            {card.status === 'BLOCKED' && card.blockedBy !== 'ADMIN' && <button type="button" onClick={() => act.run(() => cards.unblock(card.cardId)).then((r) => r && refresh())}><Icon n="checkc" />Unblock</button>}
            {(card.status === 'ACTIVE' || card.status === 'BLOCKED') && <button type="button" className="ghost" onClick={() => confirmDialog('Request a replacement card?', 'This card is blocked now and a new one is issued after bank approval.', () => cards.replace(card.cardId), true, 'Request replacement')}><Icon n="swap" />Replace card</button>}
          </div>) : <EmptyState icon="clock" title="No controls yet" text="Controls appear once the card is issued." />}
        {card.status === 'BLOCKED' && card.blockedBy === 'ADMIN' && <p className="msg">This card was blocked by the bank. Please contact the bank to unblock it.</p>}
        {issued && !card.pinSet && card.status === 'ACTIVE' && <p className="msg ok">Set a PIN to start using this card.</p>}
        <Notice error={act.error} />
      </Panel>
    </div>
    {issued && (
      <Panel className="flush" title="Card transactions" icon="list">
        {txns.loading ? <div style={{ padding: 24 }}><Loading /></div> : txns.error ? <div style={{ padding: 24 }}><Notice error={txns.error} /></div> : txns.data && (<>
          <Table rows={txns.data.content} empty="No card transactions yet" emptyIcon="card" columns={[
            { key: 'merchantName', label: 'Merchant', render: (r) => <div className="txn-cell"><span className={`txn-ic ${r.status === 'FAILED' ? 'fail' : r.type === 'REPAYMENT' ? 'cr' : 'dr'}`}><Icon n={r.type === 'REPAYMENT' ? 'deposit' : 'card'} size={16} /></span><div><b>{r.merchantName}</b><span className="sub">{r.reference}</span></div></div> },
            { key: 'type', label: 'Type', render: (r) => words(r.type) },
            { key: 'createdAt', label: 'Date', render: (r) => dateTime(r.createdAt) },
            { key: 'amount', label: 'Amount', align: 'right', render: (r) => <b className="num">{money(r.amount)}</b> },
            { key: 'status', label: 'Status', render: (r) => <><Badge v={r.status} />{r.failureReason && <span className="sub">{r.failureReason}</span>}</> },
          ]} />
          <Pager page={txns.data} onPage={setPage} />
        </>)}
      </Panel>)}
    {dialog && <Dialog {...dialog} onClose={(res) => { setDialog(null); if (res) refresh(); }} />}
  </>);
}
