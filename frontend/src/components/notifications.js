// Header notifications, derived from real account state (no stored notification feed exists in the backend).
import { admin, cards, fd, kyc, loans } from '../api/services';

const safe = (p) => p.then((r) => r.data).catch(() => null);
const daysUntil = (d) => Math.ceil((new Date(`${d}T00:00:00`) - new Date()) / 86400000);

export async function customerNotifications() {
  const [k, ls, cs, fds] = await Promise.all([safe(kyc.mine()), safe(loans.list()), safe(cards.list()), safe(fd.list())]);
  const out = [];
  if (k?.kycStatus === 'NOT_SUBMITTED') out.push({ icon: 'id', tone: 'amber', title: 'Complete your KYC', text: 'Upload an identity document to verify your account.', to: '/app/profile' });
  if (k?.kycStatus === 'REJECTED') out.push({ icon: 'alert', tone: 'red', title: 'KYC needs attention', text: k.latest?.adminComment || 'Your document was rejected, please resubmit.', to: '/app/profile' });
  (ls || []).filter((l) => l.status === 'ACTIVE' && l.nextEmiDueDate && daysUntil(l.nextEmiDueDate) <= 10).forEach((l) => out.push({
    icon: 'clock', tone: 'amber', title: `EMI due ${daysUntil(l.nextEmiDueDate) <= 0 ? 'now' : `in ${daysUntil(l.nextEmiDueDate)} days`}`, text: `Loan ${l.loanId}`, to: '/app/loans' }));
  (cs || []).filter((c) => c.status === 'ACTIVE' && !c.pinSet).forEach((c) => out.push({ icon: 'lock', tone: 'blue', title: 'Set your card PIN', text: `${c.cardScheme} ${c.maskedNumber}`, to: '/app/cards' }));
  (cs || []).filter((c) => c.status === 'APPLIED').forEach((c) => out.push({ icon: 'card', tone: 'violet', title: 'Card application under review', text: `${c.cardScheme} ${c.cardType.toLowerCase()} card`, to: '/app/cards' }));
  (fds || []).filter((f) => f.status === 'PENDING').forEach((f) => out.push({ icon: 'vault', tone: 'violet', title: 'FD awaiting approval', text: f.fdId, to: '/app/fd' }));
  return out;
}

export async function adminNotifications() {
  const d = await safe(admin.dashboard());
  if (!d) return [];
  return [
    [d.pendingApprovals, 'users', 'account opening request', '/admin/customers'],
    [d.pendingKyc, 'id', 'KYC submission', '/admin/kyc'],
    [d.pendingFds, 'vault', 'FD request', '/admin/fd'],
    [d.pendingCardApplications, 'card', 'card application', '/admin/cards'],
    [d.pendingLoans, 'loan', 'loan application', '/admin/loans'],
    [d.openRequests, 'inbox', 'customer request', '/admin/requests'],
  ].filter(([n]) => n > 0).map(([n, icon, what, to]) => ({ icon, tone: 'blue', title: `${n} ${what}${n > 1 ? 's' : ''}`, text: 'Waiting for your review', to }));
}
