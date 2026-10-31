# DIU City Bank: frontend (React + Vite)

## Run it
    npm install
    npm run dev          # http://localhost:5173

The backend must be running on `http://localhost:8080` (see `../docs/BANKING_MODULES.md`). Change `VITE_API_URL` in `.env` to point somewhere else.

## Where things live
| File | What it does |
|---|---|
| `src/styles.css` | Design system: colour, radius, shadow and spacing tokens at the top, then base, shell, components, pages, motion, responsive |
| `src/api/client.js` | The only place that calls `fetch`: base URL, `Authorization: Bearer`, `Idempotency-Key`, error format, 401 → back to login, protected PDF/image downloads |
| `src/api/services.js` | Every backend endpoint, grouped by module |
| `src/context/AuthContext.jsx` | Stores `{ token, role, name, bankId }` after login |
| `src/components/ui.jsx` | Shell (collapsible sidebar, top bar with search, notifications, avatar menu, mobile drawer), toasts, skeletons, empty states, dialog, tables, pager, tabs, stats, `useApi` / `useAction` hooks |
| `src/components/charts.jsx` | Dependency-free SVG charts (area, stacked bars, donut, bar list) |
| `src/components/txn.jsx` | Shared transaction row and columns |
| `src/components/notifications.js` | Header notifications derived from live account state |
| `src/pages/Landing.jsx` | Public landing page at `/` (hero, features, services, security, footer) |
| `src/pages/Login.jsx` | Existing sign-up, OTP, login and password-reset screens at `/login` (`?mode=register`, `?role=admin`) |
| `src/pages/customer/*` | Dashboard, My Accounts, Transactions (+ Fund Transfer), Bills, Fixed Deposits, Cards, Loans & EMI, Profile, Settings, Requests, Close account |
| `src/pages/admin/*` | Dashboard, Reports, Customers, Accounts, KYC, Requests, Transactions, FD/Card/Loan approvals, Bills (Profile and Settings reuse the customer pages) |

## Routes
Public: `/` (landing), `/login`, `/login?mode=register`, `/login?role=admin`
Customer: `/app`, `/app/accounts`, `/app/transactions?tab=transfer|deposit|withdraw|history|mini`, `/app/bills`, `/app/fd`, `/app/cards`, `/app/loans?tab=mine|calc|apply`, `/app/profile`, `/app/settings`, `/app/requests`, `/app/close-account`
Admin: `/admin`, `/admin/reports`, `/admin/customers?search=`, `/admin/accounts`, `/admin/kyc`, `/admin/requests`, `/admin/transactions`, `/admin/fd`, `/admin/cards`, `/admin/loans`, `/admin/bills`, `/admin/profile`, `/admin/settings`

Every money-moving form creates an idempotency key once and reuses it if the request is retried, so a double click or a retry after a network error never sends money twice.
No UI libraries are used beyond React and React Router; animations are CSS-only and respect `prefers-reduced-motion`.
