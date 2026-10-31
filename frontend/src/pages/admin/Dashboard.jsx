import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { admin } from '../../api/services';
import { EmptyState, Icon, Loading, Notice, PageHead, Panel, Stat, money, dateTime, words, useApi } from '../../components/ui';
import { Amount, TxnCell } from '../../components/txn';

const total = (p) => p.then((r) => ({ data: r.data.totalElements }));

export default function AdminDashboard() {
  const d = useApi(() => admin.dashboard());
  const txCount = useApi(() => total(admin.transactions({ size: 1 })));
  const fdCount = useApi(() => total(admin.fds('', 0)));
  const s = d.data;
  const [q, setQ] = useState('');
  const navigate = useNavigate();

  return (<>
    <PageHead title="Bank overview" sub="Live figures across customers, accounts and products.">
      <form className="row gap" onSubmit={(e) => { e.preventDefault(); navigate(`/admin/customers?search=${encodeURIComponent(q.trim())}`); }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Find a customer (ID, name, email)" aria-label="Customer search" style={{ width: 260 }} />
        <button><Icon n="search" />Search</button>
      </form>
    </PageHead>
    {d.loading ? <Panel><Loading rows={3} /></Panel> : d.error ? <Notice error={d.error} /> : (<>
      <div className="stats six">
        <Stat icon="users" label="Total customers" value={s.totalCustomers} />
        <Stat icon="bank" tone="good" label="Bank accounts" value={s.totalAccounts} sub={`${money(s.totalDeposits)} in deposits`} />
        <Stat icon="shield" tone="warn" label="Pending verifications" value={s.pendingApprovals + s.pendingKyc} sub={`${s.pendingApprovals} accounts · ${s.pendingKyc} KYC`} />
        <Stat icon="swap" tone="violet" label="Total transactions" value={txCount.data ?? '…'} sub={`${s.transactionsToday} today · ${s.failedTransactions} failed`} />
        <Stat icon="vault" label="FD applications" value={fdCount.data ?? '…'} sub={`${s.pendingFds} pending · ${s.activeFds} active`} />
        <Stat icon="loan" tone="bad" label="Pending loans" value={s.pendingLoans} sub={`${s.activeLoans} active · ${money(s.loanOutstanding)} outstanding`} />
      </div>
      <Panel title="Review queue" icon="inbox" sub="Items waiting for an admin decision">
        <div className="todo">
          {[['/admin/customers', 'Account approvals', s.pendingApprovals], ['/admin/kyc', 'KYC reviews', s.pendingKyc], ['/admin/fd', 'FD requests', s.pendingFds],
            ['/admin/cards', 'Card applications', s.pendingCardApplications], ['/admin/loans', 'Loan applications', s.pendingLoans], ['/admin/requests', 'Customer requests', s.openRequests]]
            .map(([to, label, n]) => <Link key={to} to={to} className={`todo-item ${n ? 'hot' : ''}`}><b>{n}</b><span>{label}</span></Link>)}
        </div>
      </Panel>
    </>)}
    <div className="split" style={{ marginTop: 24 }}>
      <RecentActivity />
      <PendingLists />
    </div>
  </>);
}

function RecentActivity() {
  const t = useApi(() => admin.transactions({ size: 8 }));
  return (
    <Panel title="Recent activity" icon="clock" actions={<Link className="btn ghost sm" to="/admin/transactions">All transactions</Link>}>
      {t.loading ? <Loading rows={6} /> : t.error ? <Notice error={t.error} /> : !t.data.content.length ? <EmptyState icon="swap" title="No transactions yet" /> : (
        <div className="activity">
          {t.data.content.map((x) => (
            <div key={x.transactionId}>
              <div className="txt"><TxnCell t={x} /></div>
              <div style={{ textAlign: 'right' }}><Amount t={x} /><span className="sub muted" style={{ display: 'block', fontSize: '.74rem' }}>{x.accountNo.slice(-4).padStart(8, '•')} · {dateTime(x.createdAt)}</span></div>
            </div>))}
        </div>)}
    </Panel>);
}

function PendingLists() {
  const users = useApi(() => admin.pendingApprovals());
  const fds = useApi(() => admin.fds('PENDING', 0));
  const cardsQ = useApi(() => admin.cards('APPLIED', '', 0));
  const loansQ = useApi(() => admin.loans('PENDING', 0));
  const kycQ = useApi(() => admin.kyc('PENDING', 0));
  const items = [
    ...(users.data || []).map((u) => ({ k: `u${u.bankId}`, icon: 'users', title: [u.firstName, u.lastName].join(' '), text: `Account opening · ${u.bankId}`, to: '/admin/customers' })),
    ...(kycQ.data?.content || []).map((k) => ({ k: `k${k.id}`, icon: 'id', title: k.customerName, text: `KYC · ${words(k.documentType)}`, to: '/admin/kyc' })),
    ...(fds.data?.content || []).map((f) => ({ k: f.fdId, icon: 'vault', title: f.customerName, text: `FD · ${money(f.principal)} for ${f.tenureMonths} m`, to: '/admin/fd' })),
    ...(cardsQ.data?.content || []).map((c) => ({ k: `c${c.cardId}`, icon: 'card', title: c.holderName, text: `${words(c.cardType)} card · ${c.cardScheme}`, to: '/admin/cards' })),
    ...(loansQ.data?.content || []).map((l) => ({ k: l.loanId, icon: 'loan', title: l.customerName, text: `${words(l.loanType)} loan · ${money(l.requestedAmount)}`, to: '/admin/loans' })),
  ];
  const loading = users.loading || fds.loading || cardsQ.loading || loansQ.loading || kycQ.loading;
  return (
    <Panel title="Pending approvals" icon="checkc" sub={loading ? undefined : `${items.length} waiting`}>
      {loading ? <Loading rows={5} /> : !items.length ? <EmptyState icon="checkc" title="All caught up" text="Nothing is waiting for review." /> : (
        <div className="pending-list">
          {items.slice(0, 8).map((i) => (
            <div key={i.k}>
              <div className="row gap" style={{ flexWrap: 'nowrap', minWidth: 0 }}><span className="txn-ic"><Icon n={i.icon} size={16} /></span><div style={{ minWidth: 0 }}><b>{i.title}</b><span>{i.text}</span></div></div>
              <Link className="btn ghost sm" to={i.to}>Review</Link>
            </div>))}
          {items.length > 8 && <p className="muted small" style={{ paddingTop: 8 }}>+{items.length - 8} more in the review queue</p>}
        </div>)}
    </Panel>);
}

