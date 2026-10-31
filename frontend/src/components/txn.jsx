import { Badge, Icon, money, dateTime, words } from './ui';

const TYPE_ICON = {
  DEPOSIT: 'deposit', WITHDRAWAL: 'withdraw', TRANSFER: 'send', BILL_PAYMENT: 'receipt', CARD_PAYMENT: 'card', CARD_BILL_PAYMENT: 'card',
  FD_BOOKING: 'vault', FD_REFUND: 'vault', FD_PAYOUT: 'vault', LOAN_DISBURSAL: 'loan', EMI_PAYMENT: 'loan',
};
export const TXN_TYPES = Object.keys(TYPE_ICON);
export const TXN_STATUSES = ['SUCCESS', 'FAILED', 'REVERSED', 'PENDING', 'INITIATED'];

export function TxnCell({ t }) {
  const tone = t.status === 'FAILED' ? 'fail' : t.direction === 'CREDIT' ? 'cr' : 'dr';
  return (
    <div className="txn-cell">
      <span className={`txn-ic ${tone}`}><Icon n={TYPE_ICON[t.type] || 'swap'} size={16} /></span>
      <div style={{ minWidth: 0 }}><b title={t.description}>{t.description || words(t.type)}</b><span className="sub">{t.reference}</span></div>
    </div>);
}

export const Amount = ({ t }) => (
  <span className={t.status === 'FAILED' ? 'muted' : t.direction === 'CREDIT' ? 'cr' : 'dr'}>
    {t.direction === 'CREDIT' ? '+' : '−'}{money(t.amount)}
  </span>);

/** Ledger table columns. opts: { receipt(id), account, balances } */
export const txnColumns = ({ receipt, account, balances = true } = {}) => [
  ...(account ? [{ key: 'accountNo', label: 'Account' }] : []),
  { key: 'description', label: 'Transaction', render: (t) => <TxnCell t={t} /> },
  { key: 'type', label: 'Type', render: (t) => words(t.type) },
  { key: 'createdAt', label: 'Date', render: (t) => dateTime(t.createdAt) },
  { key: 'amount', label: 'Amount', align: 'right', render: (t) => <Amount t={t} /> },
  ...(balances ? [{ key: 'balanceAfter', label: 'Balance', align: 'right', render: (t) => <span className="num">{money(t.balanceAfter)}</span> }] : []),
  { key: 'status', label: 'Status', render: (t) => <Badge v={t.status} /> },
  ...(receipt ? [{ key: 'receipt', label: '', render: (t) => t.status !== 'FAILED' && (
    <button type="button" className="ghost sm" onClick={(e) => { e.stopPropagation(); receipt(t.transactionId); }}><Icon n="down" size={14} />Receipt</button>) }] : []),
];
