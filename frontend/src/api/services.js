import { http, qs, download, openFile } from './client';

const enc = encodeURIComponent;

// ------------------------------------------------------------- public (existing auth flow)
export const auth = {
  signup: (data) => http.post('/v1/public/signup', data),
  verifyOtp: (data) => http.post('/v1/public/otp', data),
  login: (data) => http.post('/v1/public/login', data),
  forgot: (id) => http.get(`/v1/public/password?id=${enc(id)}`),
  reset: (data) => http.post('/v1/public/submitOtp', data),
  resendOtp: (mailId) => http.get(`/v1/public/otp?mailId=${enc(mailId)}`),
  status: (bankId) => http.get(`/v1/public/status?bankId=${enc(bankId)}`),
};

// ------------------------------------------------------------- customer
export const account = {
  me: () => http.get('/v1/accounts/me'),
};

export const txn = {
  beneficiary: (accountNo) => http.get(`/v1/transactions/beneficiary?accountNo=${enc(accountNo)}`),
  transfer: (data, key) => http.post('/v1/transactions/transfer', data, key),
  history: (filters) => http.get(`/v1/transactions/history${qs(filters)}`),
  get: (id) => http.get(`/v1/transactions/${enc(id)}`),
  mini: (count) => http.get(`/v1/transactions/mini-statement?count=${count}`),
  receipt: (id) => download(`/v1/transactions/${enc(id)}/receipt`, `receipt-${id}.pdf`),
  statement: (from, to) => download(`/v1/transactions/statement/pdf${qs({ from, to })}`, `statement-${from || 'last-month'}-${to || 'today'}.pdf`),
};

export const bills = {
  options: () => http.get('/v1/bills/options'),
  pay: (data, key) => http.post('/v1/bills/pay', data, key),
  history: (page, size = 10) => http.get(`/v1/bills/history${qs({ page, size })}`),
};

export const profile = {
  get: () => http.get('/v1/profile'),
  update: (data) => http.put('/v1/profile', data),
  requestEmail: (newEmail) => http.post('/v1/profile/email', { newEmail }),
  verifyEmail: (data) => http.post('/v1/profile/email/verify', data),
  password: (data) => http.put('/v1/profile/password', data),
  uploadPhoto: (file) => { const f = new FormData(); f.append('file', file); return http.upload('/v1/profile/photo', f); },
  photoBlob: () => http.blob('/v1/profile/photo'),
  deletePhoto: () => http.del('/v1/profile/photo'),
};

export const kyc = {
  mine: () => http.get('/v1/kyc'),
  submit: (documentType, documentNumber, file) => {
    const f = new FormData();
    f.append('documentType', documentType);
    f.append('documentNumber', documentNumber);
    f.append('file', file);
    return http.upload('/v1/kyc', f);
  },
  document: (id) => openFile(`/v1/kyc/${id}/document`),
};

export const fd = {
  rates: () => http.get('/v1/fd/rates'),
  quote: (data) => http.post('/v1/fd/quote', data),
  create: (data, key) => http.post('/v1/fd', data, key),
  list: () => http.get('/v1/fd'),
  summary: () => http.get('/v1/fd/summary'),
  get: (id) => http.get(`/v1/fd/${enc(id)}`),
  maturity: (id) => http.get(`/v1/fd/${enc(id)}/maturity`),
  closePreview: (id) => http.get(`/v1/fd/${enc(id)}/close-preview`),
  close: (id, key) => http.post(`/v1/fd/${enc(id)}/close`, {}, key),
};

export const cards = {
  apply: (data) => http.post('/v1/cards/apply', data),
  list: () => http.get('/v1/cards'),
  get: (id) => http.get(`/v1/cards/${id}`),
  limit: (id) => http.get(`/v1/cards/${id}/limit`),
  number: (id) => http.get(`/v1/cards/${id}/number`),
  sendOtp: (id) => http.post(`/v1/cards/${id}/otp`),
  pin: (id, data) => http.put(`/v1/cards/${id}/pin`, data),
  block: (id) => http.put(`/v1/cards/${id}/block`),
  unblock: (id) => http.put(`/v1/cards/${id}/unblock`),
  replace: (id) => http.post(`/v1/cards/${id}/replace`),
  simulate: (id, data, key) => http.post(`/v1/cards/${id}/simulate-transaction`, data, key),
  payDues: (id, data, key) => http.post(`/v1/cards/${id}/pay-dues`, data, key),
  transactions: (id, page) => http.get(`/v1/cards/${id}/transactions${qs({ page, size: 10 })}`),
};

export const loans = {
  rates: () => http.get('/v1/loans/rates'),
  calculate: (data) => http.post('/v1/loans/calculate-emi', data),
  apply: (data) => http.post('/v1/loans/apply', data),
  list: () => http.get('/v1/loans'),
  get: (id) => http.get(`/v1/loans/${enc(id)}`),
  schedule: (id) => http.get(`/v1/loans/${enc(id)}/schedule`),
  payEmi: (id, emiNumber, key) => http.post(`/v1/loans/${enc(id)}/emi/pay`, { emiNumber }, key),
  emiHistory: (id) => http.get(`/v1/loans/${enc(id)}/emi/history`),
};

export const requests = {
  create: (data) => http.post('/v1/requests', data),
  mine: () => http.get('/v1/requests'),
  closeAccount: (data) => http.post('/v1/requests/account-closure', data),
};

// ------------------------------------------------------------- admin
export const admin = {
  profile: () => http.get('/v1/admin'),
  dashboard: () => http.get('/v1/admin/dashboard'),
  // existing approval endpoints
  pendingApprovals: () => http.get('/v1/admin/approvals'),
  decideUser: (bankId, action, status) => http.post('/v1/admin/approve', { bankId, action, status }),
  activate: (bankId) => http.get(`/v1/admin/activate?bankId=${enc(bankId)}`),
  deactivate: (bankId) => http.get(`/v1/admin/deactivate?bankId=${enc(bankId)}`),

  customers: (search, page) => http.get(`/v1/admin/customers${qs({ search, page, size: 10 })}`),
  accounts: (search, page) => http.get(`/v1/admin/accounts${qs({ search, page, size: 10 })}`),
  deposit: (accountNo, data, key) => http.post(`/v1/admin/accounts/${enc(accountNo)}/deposit`, data, key),
  withdraw: (accountNo, data, key) => http.post(`/v1/admin/accounts/${enc(accountNo)}/withdraw`, data, key),
  accountStatus: (accountNo, status, reason) => http.put(`/v1/admin/accounts/${enc(accountNo)}/status`, { status, reason }),
  transactions: (filters) => http.get(`/v1/admin/transactions${qs(filters)}`),
  bills: (filters) => http.get(`/v1/admin/bills${qs(filters)}`),

  fds: (status, page) => http.get(`/v1/admin/fd${qs({ status, page, size: 10 })}`),
  approveFd: (id, data) => http.put(`/v1/admin/fd/${enc(id)}/approve`, data),
  rejectFd: (id, comment) => http.put(`/v1/admin/fd/${enc(id)}/reject`, { comment }),
  processMaturity: () => http.post('/v1/admin/fd/process-maturity'),

  cards: (status, cardType, page) => http.get(`/v1/admin/cards${qs({ status, cardType, page, size: 10 })}`),
  approveCard: (id, data) => http.put(`/v1/admin/cards/${id}/approve`, data),
  rejectCard: (id, comment) => http.put(`/v1/admin/cards/${id}/reject`, { comment }),
  blockCard: (id) => http.put(`/v1/admin/cards/${id}/block`),
  unblockCard: (id) => http.put(`/v1/admin/cards/${id}/unblock`),
  cardLimit: (id, cardLimit) => http.put(`/v1/admin/cards/${id}/limit`, { cardLimit }),
  cardTransactions: (id, page) => http.get(`/v1/admin/cards/${id}/transactions${qs({ page, size: 10 })}`),

  loans: (status, page) => http.get(`/v1/admin/loans${qs({ status, page, size: 10 })}`),
  approveLoan: (id, data) => http.put(`/v1/admin/loans/${enc(id)}/approve`, data),
  rejectLoan: (id, comment) => http.put(`/v1/admin/loans/${enc(id)}/reject`, { comment }),
  loanSchedule: (id) => http.get(`/v1/admin/loans/${enc(id)}/schedule`),
  emiPayments: (page) => http.get(`/v1/admin/loans/emi-payments${qs({ page, size: 10 })}`),

  kyc: (status, page) => http.get(`/v1/admin/kyc${qs({ status, page, size: 10 })}`),
  kycDocument: (id) => openFile(`/v1/admin/kyc/${id}/document`),
  approveKyc: (id, comment) => http.put(`/v1/admin/kyc/${id}/approve`, { comment }),
  rejectKyc: (id, comment) => http.put(`/v1/admin/kyc/${id}/reject`, { comment }),

  requests: (status, page) => http.get(`/v1/admin/requests${qs({ status, page, size: 10 })}`),
  approveRequest: (id, comment) => http.put(`/v1/admin/requests/${id}/approve`, { comment }),
  rejectRequest: (id, comment) => http.put(`/v1/admin/requests/${id}/reject`, { comment }),
};
