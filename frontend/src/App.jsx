import { Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Landing from './pages/Landing';
import { Shell } from './components/ui';
import { customerNotifications, adminNotifications } from './components/notifications';
import CustomerDashboard from './pages/customer/Dashboard';
import Accounts from './pages/customer/Accounts';
import Transactions, { FundTransfer } from './pages/customer/Transactions';
import Bills from './pages/customer/Bills';
import FixedDeposits from './pages/customer/FixedDeposits';
import Cards from './pages/customer/Cards';
import Loans from './pages/customer/Loans';
import Profile from './pages/customer/Profile';
import Settings from './pages/customer/Settings';
import Requests, { CloseAccount } from './pages/customer/Requests';
import AdminDashboard from './pages/admin/Dashboard';
import Reports from './pages/admin/Reports';
import * as M from './pages/admin/Management';
import * as P from './pages/admin/Products';

// [path, label, icon, danger?]
const customerNav = [
  { section: 'Overview', items: [['/app', 'Dashboard', 'grid'], ['/app/accounts', 'My Accounts', 'wallet']] },
  { section: 'Banking', items: [['/app/transactions', 'Transactions', 'swap'], ['/app/transfer', 'Fund Transfer', 'send'],
    ['/app/bills', 'Bill Payments', 'receipt'], ['/app/fd', 'Fixed Deposits', 'vault'], ['/app/cards', 'Cards', 'card'], ['/app/loans', 'Loans & EMI', 'loan']] },
  { section: 'Account', items: [['/app/profile', 'My Profile', 'user'], ['/app/requests', 'Requests', 'inbox'], ['/app/settings', 'Settings', 'settings']] },
];
const adminNav = [
  { section: 'Overview', items: [['/admin', 'Dashboard', 'grid'], ['/admin/reports', 'Reports', 'pie']] },
  { section: 'Customers', items: [['/admin/customers', 'Customers', 'users'], ['/admin/accounts', 'Accounts', 'bank'], ['/admin/kyc', 'KYC Verification', 'id'], ['/admin/requests', 'Requests', 'inbox']] },
  { section: 'Operations', items: [['/admin/transactions', 'Transactions', 'swap'], ['/admin/fd', 'FD Approvals', 'vault'], ['/admin/cards', 'Card Approvals', 'card'],
    ['/admin/loans', 'Loan Approvals', 'loan'], ['/admin/bills', 'Bill Payments', 'receipt']] },
  { section: 'Account', items: [['/admin/profile', 'Profile', 'user'], ['/admin/settings', 'Settings', 'settings']] },
];
const adminSearch = (q, navigate) => navigate(`/admin/customers?search=${encodeURIComponent(q)}`);

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/app" element={<Shell role="CUSTOMER" nav={customerNav} notifications={customerNotifications} />}>
        <Route index element={<CustomerDashboard />} />
        <Route path="accounts" element={<Accounts />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="transfer" element={<FundTransfer />} />
        <Route path="bills" element={<Bills />} />
        <Route path="fd" element={<FixedDeposits />} />
        <Route path="cards" element={<Cards />} />
        <Route path="loans" element={<Loans />} />
        <Route path="profile" element={<Profile />} />
        <Route path="settings" element={<Settings />} />
        <Route path="requests" element={<Requests />} />
        <Route path="close-account" element={<CloseAccount />} />
      </Route>
      <Route path="/admin" element={<Shell role="ADMIN" nav={adminNav} notifications={adminNotifications} searchHint="Search pages or customers…" onSearch={adminSearch} />}>
        <Route index element={<AdminDashboard />} />
        <Route path="reports" element={<Reports />} />
        <Route path="customers" element={<M.Customers />} />
        <Route path="accounts" element={<M.Accounts />} />
        <Route path="transactions" element={<M.Transactions />} />
        <Route path="bills" element={<M.Bills />} />
        <Route path="kyc" element={<M.Kyc />} />
        <Route path="requests" element={<M.Requests />} />
        <Route path="fd" element={<P.FixedDeposits />} />
        <Route path="cards" element={<P.Cards />} />
        <Route path="loans" element={<P.Loans />} />
        <Route path="profile" element={<Profile adminMode />} />
        <Route path="settings" element={<Settings adminMode />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>);
}
