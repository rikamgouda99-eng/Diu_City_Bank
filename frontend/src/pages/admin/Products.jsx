import { useState } from 'react';
import { admin } from '../../api/services';
import { Badge, Details, Dialog, Loading, Notice, PageHead, Pager, Panel, Table, Tabs, date, dateTime, money, words, useAction, useApi } from '../../components/ui';
import { Person, StatusChips } from '../../components/ui';
import { emiColumns } from '../customer/Loans';

const StatusFilter = ({ value, onChange, options, label = 'All statuses' }) => (
  <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Status filter">
    <option value="">{label}</option>{options.map((o) => <option key={o} value={o}>{words(o)}</option>)}
  </select>);

const rejectDialog = (title, fn) => ({
  title, danger: true, confirm: 'Reject',
  fields: [{ name: 'comment', label: 'Reason (shown to the customer)', required: true, maxLength: 250 }],
  onSubmit: (v) => fn(v.comment),
});

// ------------------------------------------------------------- fixed deposits
export function FixedDeposits() {
  const [q, setQ] = useState({ status: 'PENDING', page: 0 });
  const list = useApi(() => admin.fds(q.status, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const [selected, setSelected] = useState(null);
  const act = useAction();
  const approve = (f) => setDialog({
    title: `Approve FD ${f.fdId}`, text: `${f.customerName} · ${money(f.principal)} for ${f.tenureMonths} months. The FD starts today.`,
    fields: [{ name: 'interestRate', label: 'Interest rate % (leave to keep quoted rate)', type: 'number', step: '0.01', placeholder: String(f.interestRate) },
      { name: 'comment', label: 'Comment (optional)', maxLength: 250 }],
    confirm: 'Approve FD',
    onSubmit: (v) => admin.approveFd(f.fdId, { interestRate: v.interestRate || undefined, comment: v.comment || undefined }),
  });
  return (<>
    <PageHead title="FD management" sub="Approve fixed deposit requests and pay out matured FDs.">
      <button type="button" className="ghost" disabled={act.busy} onClick={() => act.run(() => admin.processMaturity()).then((r) => r && list.reload())}>Run maturity payout now</button>
    </PageHead>
    <Notice error={act.error} ok={act.ok} />
    <Panel title="Fixed deposits" icon="vault">
      <StatusChips statuses={['PENDING', 'ACTIVE', 'MATURED', 'CLOSED', 'REJECTED']} value={q.status} onChange={(s) => setQ({ status: s, page: 0 })} count={(s) => admin.fds(s, 0)} refreshKey={list.data} />
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No fixed deposits." onRow={setSelected} selected={selected?.fdId} columns={[
          { key: 'customerName', label: 'Customer', render: (f) => <Person name={f.customerName} sub={f.fdId} /> }, { key: 'principal', label: 'Principal', align: 'right', render: (f) => money(f.principal) },
          { key: 'interestRate', label: 'Rate', render: (f) => `${f.interestRate}%` }, { key: 'tenureMonths', label: 'Tenure', render: (f) => `${f.tenureMonths} m` },
          { key: 'maturityDate', label: 'Matures', render: (f) => date(f.maturityDate) }, { key: 'maturityAmount', label: 'Maturity', align: 'right', render: (f) => money(f.maturityAmount) },
          { key: 'status', label: 'Status', render: (f) => <Badge v={f.status} /> },
          { key: 'act', label: '', render: (f) => f.status === 'PENDING' && (
            <span className="row gap" onClick={(e) => e.stopPropagation()}>
              <button type="button" className="link" onClick={() => approve(f)}>Approve</button>
              <button type="button" className="link danger" onClick={() => setDialog(rejectDialog(`Reject FD ${f.fdId}`, (c) => admin.rejectFd(f.fdId, c)))}>Reject</button>
            </span>) },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {selected && (
      <Panel title={`FD ${selected.fdId}`} actions={<button type="button" className="link" onClick={() => setSelected(null)}>Close</button>}>
        <Details data={selected} fields={[['bankId', 'Bank ID'], ['customerName', 'Customer'], ['accountNo', 'Account'], ['principal', 'Principal', money], ['interestRate', 'Rate', (x) => `${x}%`],
          ['tenureMonths', 'Tenure (months)'], ['depositDate', 'Deposit date', date], ['maturityDate', 'Maturity date', date], ['expectedInterest', 'Expected interest', money],
          ['maturityAmount', 'Maturity amount', money], ['status', 'Status', (x) => <Badge v={x} />], ['closureType', 'Closure', words], ['payoutAmount', 'Paid out', money],
          ['adminComment', 'Comment'], ['fundingTransactionId', 'Booking txn'], ['payoutTransactionId', 'Payout txn'], ['createdAt', 'Requested', dateTime]]} />
      </Panel>)}
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) list.reload(); }} />}
  </>);
}

// ------------------------------------------------------------- cards
export function Cards() {
  const [q, setQ] = useState({ status: 'APPLIED', cardType: '', page: 0 });
  const list = useApi(() => admin.cards(q.status, q.cardType, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const [selected, setSelected] = useState(null);
  const act = useAction();
  const refresh = () => { list.reload(); setSelected(null); };
  const approve = (c) => setDialog({
    title: `Issue ${words(c.cardType).toLowerCase()} card to ${c.holderName}`,
    text: c.cardType === 'CREDIT' ? `Requested limit: ${money(c.requestedLimit)}` : 'Set the daily spend limit for this debit card.',
    fields: [{ name: 'cardLimit', label: c.cardType === 'CREDIT' ? 'Credit limit (₹)' : 'Daily limit (₹, optional)', type: 'number', step: '1000', min: '1000',
      required: false, value: c.cardType === 'CREDIT' ? (c.requestedLimit ?? '') : '' }, { name: 'comment', label: 'Comment (optional)', maxLength: 250 }],
    confirm: 'Approve & issue',
    onSubmit: (v) => admin.approveCard(c.cardId, { cardLimit: v.cardLimit || undefined, comment: v.comment || undefined }),
  });
  const limit = (c) => setDialog({
    title: `Change limit for ${c.maskedNumber}`,
    fields: [{ name: 'cardLimit', label: c.cardType === 'CREDIT' ? 'Credit limit (₹)' : 'Daily limit (₹)', type: 'number', min: '1000', step: '1000', required: true, value: c.cardLimit }],
    confirm: 'Update limit', onSubmit: (v) => admin.cardLimit(c.cardId, v.cardLimit),
  });
  return (<>
    <PageHead title="Card management" sub="Issue cards, set limits and block cards." />
    <Notice error={act.error} ok={act.ok} />
    <Panel title="Cards" actions={<div className="row gap">
      <StatusFilter value={q.cardType} onChange={(t) => setQ({ ...q, cardType: t, page: 0 })} options={['DEBIT', 'CREDIT']} label="All types" />
      </div>}>
      <StatusChips statuses={['APPLIED', 'ACTIVE', 'BLOCKED', 'REJECTED', 'REPLACED']} value={q.status} onChange={(s) => setQ({ ...q, status: s, page: 0 })} count={(s) => admin.cards(s, q.cardType, 0)} refreshKey={`${q.cardType}-${list.data?.totalElements}-${q.page}`} />
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No cards." onRow={setSelected} selected={selected?.cardId} columns={[
          { key: 'holderName', label: 'Card holder', render: (c) => <Person name={c.holderName} sub={`${c.bankId} · #${c.cardId}`} /> },
          { key: 'cardType', label: 'Type', render: (c) => `${words(c.cardType)} · ${c.cardScheme}` }, { key: 'maskedNumber', label: 'Card' },
          { key: 'cardLimit', label: 'Limit', align: 'right', render: (c) => money(c.cardLimit ?? c.requestedLimit) },
          { key: 'creditUsed', label: 'Used', align: 'right', render: (c) => money(c.creditUsed ?? c.spentToday) },
          { key: 'status', label: 'Status', render: (c) => <Badge v={c.status} /> },
          { key: 'act', label: '', render: (c) => (
            <span className="row gap" onClick={(e) => e.stopPropagation()}>
              {c.status === 'APPLIED' && <><button type="button" className="link" onClick={() => approve(c)}>Approve</button>
                <button type="button" className="link danger" onClick={() => setDialog(rejectDialog('Reject card application', (m) => admin.rejectCard(c.cardId, m)))}>Reject</button></>}
              {c.status === 'ACTIVE' && <><button type="button" className="link" onClick={() => limit(c)}>Limit</button>
                <button type="button" className="link danger" onClick={() => act.run(() => admin.blockCard(c.cardId)).then((r) => r && refresh())}>Block</button></>}
              {c.status === 'BLOCKED' && <button type="button" className="link" onClick={() => act.run(() => admin.unblockCard(c.cardId)).then((r) => r && refresh())}>Unblock</button>}
            </span>) },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {selected?.issuedDate && <CardTxns card={selected} />}
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) refresh(); }} />}
  </>);
}

function CardTxns({ card }) {
  const [page, setPage] = useState(0);
  const t = useApi(() => admin.cardTransactions(card.cardId, page), [card.cardId, page]);
  return (
    <Panel title={`Transactions on ${card.maskedNumber}`}>
      {t.loading ? <Loading /> : t.error ? <Notice error={t.error} /> : (<>
        <Table rows={t.data.content} empty="No card transactions." columns={[
          { key: 'createdAt', label: 'Date', render: (r) => dateTime(r.createdAt) }, { key: 'reference', label: 'Reference' }, { key: 'merchantName', label: 'Merchant' },
          { key: 'type', label: 'Type', render: (r) => words(r.type) }, { key: 'amount', label: 'Amount', align: 'right', render: (r) => money(r.amount) },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> }, { key: 'failureReason', label: 'Note' },
        ]} />
        <Pager page={t.data} onPage={setPage} />
      </>)}
    </Panel>);
}

// ------------------------------------------------------------- loans
export function Loans() {
  const [tab, setTab] = useState('loans');
  return (<>
    <PageHead title="Loan & EMI management" sub="Approve loan applications and follow repayments. Simulation only." />
    <Tabs value={tab} onChange={setTab} tabs={[['loans', 'Loans'], ['emis', 'EMI payments']]} />
    {tab === 'loans' ? <LoanList /> : <EmiPayments />}
  </>);
}

function LoanList() {
  const [q, setQ] = useState({ status: 'PENDING', page: 0 });
  const list = useApi(() => admin.loans(q.status, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const [selected, setSelected] = useState(null);
  const schedule = useApi(() => (selected && selected.status !== 'PENDING' && selected.status !== 'REJECTED' ? admin.loanSchedule(selected.loanId) : Promise.resolve({ data: [] })), [selected?.loanId]);
  const approve = (l) => setDialog({
    title: `Approve ${words(l.loanType).toLowerCase()} loan ${l.loanId}`,
    text: `${l.customerName} asked for ${money(l.requestedAmount)} over ${l.tenureMonths} months · income ${money(l.monthlyIncome)}/month (${words(l.employmentType)}). The amount is credited to the customer's account.`,
    fields: [{ name: 'approvedAmount', label: 'Approved amount (₹)', type: 'number', step: '0.01', min: '1', value: l.requestedAmount, required: true },
      { name: 'interestRate', label: 'Interest rate %', type: 'number', step: '0.01', min: '0', value: l.interestRate, required: true },
      { name: 'comment', label: 'Comment (optional)', maxLength: 250 }],
    confirm: 'Approve & disburse',
    onSubmit: (v) => admin.approveLoan(l.loanId, { approvedAmount: v.approvedAmount, interestRate: v.interestRate, comment: v.comment || undefined }),
  });
  return (<>
    <Panel title="Loans" icon="loan">
      <StatusChips statuses={['PENDING', 'ACTIVE', 'CLOSED', 'REJECTED']} value={q.status} onChange={(s) => setQ({ status: s, page: 0 })} count={(s) => admin.loans(s, 0)} refreshKey={list.data} />
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No loans." onRow={setSelected} selected={selected?.loanId} columns={[
          { key: 'customerName', label: 'Customer', render: (l) => <Person name={l.customerName} sub={l.loanId} /> }, { key: 'loanType', label: 'Type', render: (l) => words(l.loanType) },
          { key: 'requestedAmount', label: 'Requested', align: 'right', render: (l) => money(l.requestedAmount) },
          { key: 'approvedAmount', label: 'Approved', align: 'right', render: (l) => money(l.approvedAmount) },
          { key: 'interestRate', label: 'Rate', render: (l) => `${l.interestRate}%` }, { key: 'tenureMonths', label: 'Tenure', render: (l) => `${l.tenureMonths} m` },
          { key: 'outstandingPrincipal', label: 'Outstanding', align: 'right', render: (l) => money(l.outstandingPrincipal) },
          { key: 'status', label: 'Status', render: (l) => <Badge v={l.status} /> },
          { key: 'act', label: '', render: (l) => l.status === 'PENDING' && (
            <span className="row gap" onClick={(e) => e.stopPropagation()}>
              <button type="button" className="link" onClick={() => approve(l)}>Approve</button>
              <button type="button" className="link danger" onClick={() => setDialog(rejectDialog(`Reject loan ${l.loanId}`, (c) => admin.rejectLoan(l.loanId, c)))}>Reject</button>
            </span>) },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {selected && (<>
      <Panel title={`Loan ${selected.loanId}`} actions={<button type="button" className="link" onClick={() => setSelected(null)}>Close</button>}>
        <Details data={selected} fields={[['bankId', 'Bank ID'], ['customerName', 'Customer'], ['accountNo', 'Account'], ['loanType', 'Type', words], ['status', 'Status', (x) => <Badge v={x} />],
          ['requestedAmount', 'Requested', money], ['approvedAmount', 'Approved', money], ['interestRate', 'Rate', (x) => `${x}%`], ['tenureMonths', 'Tenure (months)'],
          ['monthlyEmi', 'EMI', money], ['totalRepayment', 'Total repayment', money], ['outstandingPrincipal', 'Outstanding', money], ['emisPaid', 'EMIs paid'],
          ['nextEmiDueDate', 'Next EMI due', date], ['employmentType', 'Employment', words], ['employerName', 'Employer'], ['monthlyIncome', 'Monthly income', money],
          ['purpose', 'Purpose'], ['adminComment', 'Comment'], ['appliedAt', 'Applied', dateTime]]} />
      </Panel>
      {schedule.data?.length > 0 && <Panel title="Repayment schedule"><Table rows={schedule.data} columns={emiColumns} /></Panel>}
    </>)}
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) { list.reload(); setSelected(null); } }} />}
  </>);
}

function EmiPayments() {
  const [page, setPage] = useState(0);
  const list = useApi(() => admin.emiPayments(page), [page]);
  return (
    <Panel title="EMI payment history">
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No EMIs paid yet." columns={[{ key: 'loanId', label: 'Loan' }, ...emiColumns, { key: 'transactionId', label: 'Transaction' }]} />
        <Pager page={list.data} onPage={setPage} />
      </>)}
    </Panel>);
}
