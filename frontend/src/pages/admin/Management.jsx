import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { admin } from '../../api/services';
import { Badge, Details, Dialog, Loading, Notice, PageHead, Pager, Panel, Table, Tabs, date, dateTime, money, words, useAction, useApi } from '../../components/ui';
import { Person, StatusChips } from '../../components/ui';
import { TXN_STATUSES, TXN_TYPES, txnColumns } from '../../components/txn';

const Search = ({ value, onSearch, placeholder }) => {
  const [v, setV] = useState(value);
  return (
    <form className="row gap" onSubmit={(e) => { e.preventDefault(); onSearch(v.trim()); }}>
      <input value={v} onChange={(e) => setV(e.target.value)} placeholder={placeholder} aria-label="Search" />
      <button>Search</button>
    </form>);
};

// ------------------------------------------------------------- customers
export function Customers() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(params.get('search') ? 'all' : 'pending');
  useEffect(() => { if (params.get('search')) setTab('all'); }, [params]);
  return (<>
    <PageHead title="Customer management" sub="Approve new account requests and manage customers." />
    <Tabs value={tab} onChange={setTab} tabs={[['pending', 'Pending approvals'], ['all', 'All customers']]} />
    {tab === 'pending' ? <PendingApprovals /> : <AllCustomers key={params.get('search') || ''} initial={params.get('search') || ''} />}
  </>);
}

function PendingApprovals() {
  const list = useApi(() => admin.pendingApprovals());
  const [dialog, setDialog] = useState(null);
  const decide = (u, approve) => setDialog({
    title: `${approve ? 'Approve' : 'Reject'} ${u.firstName} ${u.lastName}`,
    text: approve ? 'A savings account is opened and the customer is emailed the account number.' : 'The customer is emailed the reason.',
    danger: !approve,
    fields: [{ name: 'status', label: approve ? 'Status note (optional)' : 'Reason', required: !approve, maxLength: 200 }],
    confirm: approve ? 'Approve & open account' : 'Reject',
    onSubmit: (v) => admin.decideUser(u.bankId, approve ? 'APPROVE' : 'REJECT', v.status || undefined),
  });
  return (
    <Panel title="Account opening requests">
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (
        <Table rows={list.data} empty="No pending account requests." columns={[
          { key: 'bankId', label: 'Bank ID' },
          { key: 'name', label: 'Applicant', render: (u) => <Person name={[u.firstName, u.middleName, u.lastName].filter(Boolean).join(' ')} sub={u.mailId} /> },
          { key: 'phoneNumber', label: 'Phone' },
          { key: 'createdDate', label: 'Applied', render: (u) => date(u.createdDate) },
          { key: 'isVerified', label: 'Email verified', render: (u) => (u.isVerified ? 'Yes' : 'No') },
          { key: 'act', label: '', render: (u) => <span className="row gap"><button type="button" className="link" onClick={() => decide(u, true)}>Approve</button><button type="button" className="link danger" onClick={() => decide(u, false)}>Reject</button></span> },
        ]} />)}
      {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) list.reload(); }} />}
    </Panel>);
}

function AllCustomers({ initial }) {
  const [q, setQ] = useState({ search: initial, page: 0 });
  const list = useApi(() => admin.customers(q.search, q.page), [q]);
  const act = useAction();
  const toggle = async (c) => { const r = await act.run(() => (c.isActive ? admin.deactivate(c.bankId) : admin.activate(c.bankId))); if (r) list.reload(); };
  return (
    <Panel title="Customers" actions={<Search value={q.search} onSearch={(s) => setQ({ search: s, page: 0 })} placeholder="Bank ID, name or email" />}>
      <Notice error={act.error || list.error} ok={act.ok} />
      {list.loading ? <Loading /> : list.data && (<>
        <Table rows={list.data.content} empty="No customers found." columns={[
          { key: 'name', label: 'Customer', render: (c) => <Person name={c.name} sub={`${c.bankId} · ${c.mailId}`} /> }, { key: 'phoneNumber', label: 'Phone' },
          { key: 'accountNo', label: 'Account' }, { key: 'balance', label: 'Balance', align: 'right', render: (c) => money(c.balance) },
          { key: 'kycStatus', label: 'KYC', render: (c) => <Badge v={c.kycStatus} /> },
          { key: 'isActive', label: 'Profile', render: (c) => <Badge v={c.isActive ? 'ACTIVE' : 'BLOCKED'} /> },
          { key: 'act', label: '', render: (c) => <button type="button" className={`link ${c.isActive ? 'danger' : ''}`} onClick={() => toggle(c)}>{c.isActive ? 'Deactivate' : 'Activate'}</button> },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>);
}

// ------------------------------------------------------------- accounts
export function Accounts() {
  const [q, setQ] = useState({ search: '', page: 0 });
  const list = useApi(() => admin.accounts(q.search, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const change = (a) => setDialog({
    title: `Account ${a.accountNo}`,
    text: 'Frozen and closed accounts can\'t send or receive money.',
    fields: [{ name: 'status', label: 'New status', options: ['ACTIVE', 'FROZEN', 'CLOSED'], value: a.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE' },
      { name: 'reason', label: 'Reason', maxLength: 200 }],
    confirm: 'Update status',
    onSubmit: (v) => admin.accountStatus(a.accountNo, v.status, v.reason || undefined),
  });
  return (<>
    <PageHead title="Account management" sub="Freeze, unfreeze or close savings accounts." />
    <Panel title="Accounts" actions={<Search value={q.search} onSearch={(s) => setQ({ search: s, page: 0 })} placeholder="Account number or Bank ID" />}>
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No accounts found." columns={[
          { key: 'holderName', label: 'Holder', render: (a) => <Person name={a.holderName} sub={a.bankId} /> }, { key: 'accountNo', label: 'Account' },
          { key: 'accountType', label: 'Type', render: (a) => words(a.accountType) },
          { key: 'balance', label: 'Balance', align: 'right', render: (a) => money(a.balance) },
          { key: 'status', label: 'Status', render: (a) => <Badge v={a.status} /> }, { key: 'statusReason', label: 'Reason' },
          { key: 'act', label: '', render: (a) => <button type="button" className="link" onClick={() => change(a)}>Change status</button> },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) list.reload(); }} />}
  </>);
}

// ------------------------------------------------------------- transactions
const TYPES = TXN_TYPES;
export function Transactions() {
  const [f, setF] = useState({ accountNo: '', reference: '', type: '', status: '', from: '', to: '', page: 0, size: 15 });
  const [applied, setApplied] = useState(f);
  const list = useApi(() => admin.transactions(applied), [applied]);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (<>
    <PageHead title="Transaction management" sub="Every ledger entry across all accounts." />
    <form className="panel filters" onSubmit={(e) => { e.preventDefault(); setApplied({ ...f, page: 0 }); }}>
      <label>Account<input value={f.accountNo} onChange={set('accountNo')} /></label>
      <label>Reference / ID<input value={f.reference} onChange={set('reference')} /></label>
      <label>Type<select value={f.type} onChange={set('type')}><option value="">All</option>{TYPES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
      <label>Status<select value={f.status} onChange={set('status')}><option value="">All</option>{TXN_STATUSES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
      <label>From<input type="date" value={f.from} onChange={set('from')} /></label>
      <label>To<input type="date" value={f.to} onChange={set('to')} /></label>
      <div className="row end"><button>Apply</button></div>
    </form>
    <Panel>
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No transactions match" emptyIcon="swap" columns={txnColumns({ account: true })} />
        <Pager page={list.data} onPage={(p) => setApplied({ ...applied, page: p })} />
      </>)}
    </Panel>
  </>);
}

// ------------------------------------------------------------- bills
export function Bills() {
  const [q, setQ] = useState({ billType: '', bankId: '', page: 0 });
  const list = useApi(() => admin.bills({ ...q, size: 15 }), [q]);
  return (<>
    <PageHead title="Bill payment management" sub="All simulated bill payments." />
    <Panel title="Bill payments" actions={
      <div className="row gap">
        <select value={q.billType} onChange={(e) => setQ({ ...q, billType: e.target.value, page: 0 })} aria-label="Bill type">
          <option value="">All bill types</option>{['ELECTRICITY', 'WATER', 'MOBILE_RECHARGE', 'INTERNET', 'GAS', 'DTH', 'OTHER'].map((t) => <option key={t} value={t}>{words(t)}</option>)}</select>
        <Search value={q.bankId} onSearch={(s) => setQ({ ...q, bankId: s, page: 0 })} placeholder="Bank ID" />
      </div>}>
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No bill payments." columns={[
          { key: 'paymentDate', label: 'Date', render: (r) => dateTime(r.paymentDate) }, { key: 'bankId', label: 'Bank ID' }, { key: 'reference', label: 'Reference' },
          { key: 'billType', label: 'Bill', render: (r) => words(r.billType) }, { key: 'consumerNumber', label: 'Consumer no.' },
          { key: 'paymentMode', label: 'Mode', render: (r) => words(r.paymentMode) }, { key: 'amount', label: 'Amount', align: 'right', render: (r) => money(r.amount) },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
  </>);
}

// ------------------------------------------------------------- KYC
export function Kyc() {
  const [q, setQ] = useState({ status: 'PENDING', page: 0 });
  const list = useApi(() => admin.kyc(q.status, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const view = useAction();
  const decide = (k, approve) => setDialog({
    title: `${approve ? 'Approve' : 'Reject'} KYC of ${k.customerName}`, danger: !approve,
    fields: [{ name: 'comment', label: approve ? 'Comment (optional)' : 'Reason for rejection', required: !approve, maxLength: 250 }],
    confirm: approve ? 'Approve' : 'Reject',
    onSubmit: (v) => (approve ? admin.approveKyc : admin.rejectKyc)(k.id, v.comment || undefined),
  });
  return (<>
    <PageHead title="KYC management" sub="Review identity documents submitted by customers." />
    <Panel title="KYC requests" icon="id">
      <StatusChips statuses={['PENDING', 'APPROVED', 'REJECTED']} value={q.status} onChange={(s) => setQ({ status: s, page: 0 })} count={(s) => admin.kyc(s, 0)} refreshKey={list.data} />
      <Notice error={view.error} />
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No KYC requests." columns={[
          { key: 'submittedAt', label: 'Submitted', render: (k) => dateTime(k.submittedAt) }, { key: 'customerName', label: 'Customer', render: (k) => <Person name={k.customerName} sub={k.bankId} /> },
          { key: 'documentType', label: 'Document', render: (k) => words(k.documentType) }, { key: 'documentNumber', label: 'Number' },
          { key: 'status', label: 'Status', render: (k) => <Badge v={k.status} /> }, { key: 'adminComment', label: 'Comment' },
          { key: 'act', label: '', render: (k) => (
            <span className="row gap">
              <button type="button" className="link" onClick={() => view.run(() => admin.kycDocument(k.id))}>View document</button>
              {k.status === 'PENDING' && <><button type="button" className="link" onClick={() => decide(k, true)}>Approve</button><button type="button" className="link danger" onClick={() => decide(k, false)}>Reject</button></>}
            </span>) },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) list.reload(); }} />}
  </>);
}

// ------------------------------------------------------------- requests
export function Requests() {
  const [q, setQ] = useState({ status: 'OPEN', page: 0 });
  const list = useApi(() => admin.requests(q.status, q.page), [q]);
  const [dialog, setDialog] = useState(null);
  const decide = (r, approve) => setDialog({
    title: `${approve ? 'Approve' : 'Reject'} request #${r.id}`, danger: !approve || r.requestType === 'ACCOUNT_CLOSURE',
    text: approve && r.requestType === 'ACCOUNT_CLOSURE' ? 'Approving closes the customer\'s account. It will no longer be able to transact.' : r.subject,
    fields: [{ name: 'comment', label: approve ? 'Comment (optional)' : 'Reason', required: !approve, maxLength: 250 }],
    confirm: approve ? 'Approve' : 'Reject',
    onSubmit: (v) => (approve ? admin.approveRequest : admin.rejectRequest)(r.id, v.comment || undefined),
  });
  return (<>
    <PageHead title="Request management" sub="Service requests raised by customers." />
    <Panel title="Customer requests" icon="inbox">
      <StatusChips statuses={['OPEN', 'APPROVED', 'REJECTED']} value={q.status} onChange={(s) => setQ({ status: s, page: 0 })} count={(s) => admin.requests(s, 0)} refreshKey={list.data} />
      {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (<>
        <Table rows={list.data.content} empty="No requests." columns={[
          { key: 'createdAt', label: 'Raised', render: (r) => dateTime(r.createdAt) }, { key: 'customerName', label: 'Customer', render: (r) => <Person name={r.customerName} sub={r.bankId} /> },
          { key: 'requestType', label: 'Type', render: (r) => words(r.requestType) }, { key: 'subject', label: 'Subject' }, { key: 'description', label: 'Details' },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> }, { key: 'adminComment', label: 'Comment' },
          { key: 'act', label: '', render: (r) => r.status === 'OPEN' && <span className="row gap"><button type="button" className="link" onClick={() => decide(r, true)}>Approve</button><button type="button" className="link danger" onClick={() => decide(r, false)}>Reject</button></span> },
        ]} />
        <Pager page={list.data} onPage={(p) => setQ({ ...q, page: p })} />
      </>)}
    </Panel>
    {dialog && <Dialog {...dialog} onClose={(r) => { setDialog(null); if (r) list.reload(); }} />}
  </>);
}

