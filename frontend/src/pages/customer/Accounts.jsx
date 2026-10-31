import { useState } from 'react';
import { Link } from 'react-router-dom';
import { account, cards, fd, loans, profile, txn } from '../../api/services';
import { Badge, Icon, Loading, Notice, PageHead, Panel, Sim, Stat, Table, date, dateTime, money, words, useAction, useApi } from '../../components/ui';

export default function Accounts() {
  const acc = useApi(() => account.me());
  const me = useApi(() => profile.get());
  const myCards = useApi(() => cards.list());
  const myFds = useApi(() => fd.list());
  const myLoans = useApi(() => loans.list());
  const [range, setRange] = useState({ from: '', to: '' });
  const dl = useAction();
  const a = acc.data;

  return (<>
    <PageHead title="My accounts" sub="Your savings account and everything linked to it."><Sim /></PageHead>
    {acc.loading ? <Loading /> : acc.error ? <Notice error={acc.error} /> : (<>
      <div className="stats">
        <Stat icon="wallet" label="Current balance" value={money(a.balance)} />
        <Stat icon="checkc" tone="good" label="Available balance" value={money(a.availableBalance)} />
        <Stat icon="shield" tone={a.status === 'ACTIVE' ? 'good' : 'bad'} label="Account status" value={words(a.status)} />
        <Stat icon="clock" tone="violet" label="Last activity" value={a.lastTransactionAt ? date(a.lastTransactionAt) : '—'} />
      </div>
      <div className="two">
        <Panel title={`${words(a.accountType)} account`} icon="bank" actions={<Badge v={a.status} />}>
          <div className="info-grid">
            <div><span>Account number</span><b className="num">{a.accountNo}</b></div>
            <div><span>Account holder</span><b>{a.holderName}</b></div>
            <div><span>Customer / Bank ID</span><b>{a.bankId}</b></div>
            <div><span>Currency</span><b>{a.currency}</b></div>
            <div><span>Opened on</span><b>{date(a.openedAt)}</b></div>
            <div><span>KYC status</span><b>{me.data ? <Badge v={me.data.kycStatus} /> : '…'}</b></div>
          </div>
        </Panel>
        <Panel title="Account statement" icon="file" sub="Download a PDF statement for any period up to one year.">
          <div className="form-grid">
            <label>From<input type="date" value={range.from} max={range.to || undefined} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
            <label>To<input type="date" value={range.to} min={range.from || undefined} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
          </div>
          <span className="hint">Leave empty for the last month.</span>
          <button type="button" disabled={dl.busy} onClick={() => dl.run(() => txn.statement(range.from, range.to))}><Icon n="down" />{dl.busy ? 'Preparing…' : 'Download statement'}</button>
          <Notice error={dl.error} />
        </Panel>
      </div>
    </>)}

    <Panel className="flush" title="Linked products" icon="grid">
      {(myCards.loading || myFds.loading || myLoans.loading) ? <div style={{ padding: 24 }}><Loading /></div> : (
        <Table empty="No cards, deposits or loans linked yet" emptyIcon="grid" rows={[
          ...(myCards.data || []).map((c) => ({ id: `c${c.cardId}`, kind: `${words(c.cardType)} card`, ref: c.maskedNumber || `Application #${c.cardId}`, amount: c.cardLimit, note: c.cardScheme, status: c.status, since: c.issuedDate || c.appliedAt, to: '/app/cards' })),
          ...(myFds.data || []).map((f) => ({ id: f.fdId, kind: 'Fixed deposit', ref: f.fdId, amount: f.principal, note: `${f.interestRate}% · ${f.tenureMonths} months`, status: f.status, since: f.createdAt, to: '/app/fd' })),
          ...(myLoans.data || []).map((l) => ({ id: l.loanId, kind: `${words(l.loanType)} loan`, ref: l.loanId, amount: l.approvedAmount ?? l.requestedAmount, note: l.outstandingPrincipal ? `${money(l.outstandingPrincipal)} outstanding` : `${l.tenureMonths} months`, status: l.status, since: l.appliedAt, to: '/app/loans' })),
        ]} columns={[
          { key: 'kind', label: 'Product', render: (r) => <b>{r.kind}</b> },
          { key: 'ref', label: 'Reference' },
          { key: 'amount', label: 'Amount / limit', align: 'right', render: (r) => money(r.amount) },
          { key: 'note', label: 'Details' },
          { key: 'since', label: 'Since', render: (r) => dateTime(r.since) },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
          { key: 'go', label: '', render: (r) => <Link className="link small" to={r.to}>Manage</Link> },
        ]} />)}
    </Panel>
  </>);
}
