# DIU City Bank: banking modules

Transactions, bill payments, profile and KYC, fixed deposits, cards, loans and EMI, customer requests, and the admin dashboard.
These were added on top of the existing auth flow: sign-up, email OTP, login, password reset, admin approval, and activate/deactivate. None of that flow was removed or rewritten.

> **DEMO / SIMULATION.** This is an academic project. No real banking network, card network or payment gateway is used, and no real money moves. Every financial API response carries `"simulated": true`, and the UI shows a *Demo / Simulation* label.

---

## 1. How to run

### Prerequisites
- Java 21, MySQL 8 with the `DCB` schema, Node 18+.

### One-time database migration (existing installations)
The original `bank_account.account_balance` column was a `DOUBLE`. Three tables (`card_entity`, `loan_details`, `transaction_status_table`) existed but were empty and could not hold the new modules; `card_entity` even had a plaintext `cvv` column. Run once:

```bash
mysql -u root -p DCB < db/migration-v2-banking-modules.sql
```

The script changes the balance column to `DECIMAL(19,2)` and keeps every existing balance. It drops each of the three legacy tables only if the table still has its old shape and has no rows; a table with data is left alone and reported. Hibernate (`ddl-auto=update`) then creates all new tables at startup. The script is safe to re-run.

### Backend
```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test                     # 42 automated tests on an in-memory H2 database (never touches MySQL)
```
API documentation (Swagger UI): http://localhost:8080/swagger-ui/index.html

### Frontend
```bash
cd frontend
npm install
npm run dev                     # http://localhost:5173
```
`frontend/.env` sets `VITE_API_URL=http://localhost:8080/api`.

### Logging in
- **Customers**: Bank ID or email, plus password. Sign-up can now take a password, so a new customer can log in right after verifying the OTP. Accounts without a password still set one through *Forgot password*, as before.
- **Admins**: the *Admin* tab on the login page.

---

## 2. What was added, module by module

### 2.1 Transactions
| Feature | How it works |
|---|---|
| Balance / account overview | `GET /accounts/me`: account number, type, status, balance, last activity |
| Deposit / withdraw | Validated amount (> 0, max 2 decimals, configurable min/max). The account row is locked (`SELECT … FOR UPDATE`), the balance is updated, and a ledger row is written with balance before and after |
| Transfer | Beneficiary lookup returns a masked name only. Both accounts are locked in account-number order, so two opposite transfers can't deadlock. Debit and credit happen in **one DB transaction**; any failure rolls back both. Unique `TRF…` reference shared by both ledger rows plus a `fund_transfer` record |
| Rules | Can't send to your own account; both accounts must be ACTIVE; the receiver's user must be active; sufficient balance required |
| Duplicate protection | Every financial POST needs an `Idempotency-Key` header. A repeated key returns the original result (`"replayed": true`) and moves no money |
| Failed attempts | A debit rejected for insufficient funds rolls back, then a **FAILED** ledger row is written in a separate transaction so it appears in history |
| History | Filters: reference/ID, type, status, date range; pagination; per-row PDF receipt |
| Mini statement | Last 5 or 10 entries; account statement as **PDF** for any period up to one year |
| Statuses | `INITIATED, PENDING, SUCCESS, FAILED, REVERSED`. Each entry has a 12-digit RRN and a 6-digit STAN |

### 2.2 Bill payments
Bill types: electricity, water, mobile recharge, internet, gas, DTH, other. Payment modes: **UPI, bank draft** (debit the account), **debit card** (debits the account through the card; counts toward its daily limit; PIN required), and **credit card** (uses the credit line; PIN required). Each `bill_payment` row links to its ledger transaction or card transaction.

### 2.3 Profile, photo and KYC
- View every profile field, including KYC and verification status. Users can edit name, mobile, address, gender and marital status. Bank ID, role, balance and approval flags can't be changed: extra fields in the request are ignored.
- **Email change** sends an OTP to the *new* address through the existing `OtpService`, and the email only changes after that OTP is verified.
- **Password change** requires the current password.
- **Profile photo** is stored in MySQL as `LONGBLOB` (`profile_photo` table). JPG, PNG and WebP only, checked by **file signature**, not the client's content type. Maximum 2 MB, replaceable and removable. It is served only to its owner through `GET /profile/photo`, and the UI shows the user's initial when there is no photo.
- **KYC**: document type plus number (Aadhaar 12 digits, PAN `ABCDE1234F`), with a PDF or image up to 5 MB stored as `LONGBLOB`. Statuses `PENDING → APPROVED / REJECTED`. The admin can view the document and approve or reject; a rejection needs a comment, and the customer sees it.

### 2.4 Fixed deposits
- Formula: **A = P × (1 + R / (100 × N)) ^ (N × T)**, all in `BigDecimal`. When N × T isn't a whole number of periods, the broken period earns simple interest at the period rate.
- Rates come from configurable tenure slabs. The default is quarterly compounding (N = 4).
- **Flow**: the customer books an FD. The principal is debited (`FD_BOOKING`) and the FD is `PENDING`. If the admin approves, the FD becomes `ACTIVE` and starts that day; the admin can override the rate. If the admin rejects, the principal is refunded (`FD_REFUND`) and the booking entry is marked `REVERSED`.
- **Maturity**: a daily job (cron configurable), or *Run maturity payout now* in the admin UI, credits the maturity amount (`FD_PAYOUT`) and sets the FD to `MATURED`.
- **Premature closure**: the customer sees an estimate first. Interest is paid at (contracted rate − penalty) for the days held, and nothing if the FD is closed within the minimum number of days. The FD then becomes `CLOSED`.
- **No double payout**: the FD row is locked, the status is re-checked under the lock, and `payoutTransactionId` is unique.
- The dashboard shows total, active, pending, matured and closed FDs, amount invested, expected interest and maturity value.

### 2.5 Cards
- Customers apply for a debit or credit card (VISA, Mastercard or RuPay), with one open card per type. The admin approves or rejects. On approval a 16-digit Luhn-valid number and a CVV are generated and then **discarded**: only the masked number (`XXXX XXXX XXXX 1234`), a random token and a **BCrypt hash of the CVV** are stored. The PIN is also stored only as a BCrypt hash. Common PINs like 1234 or 0000 are rejected.
- **Debit card**: `cardLimit` is a daily spend limit. **Credit card**: `cardLimit` is the credit limit, assigned by the admin. Credit cards also have credit used, available credit, and billing (outstanding, minimum due, statement date, due date). Customers can pay dues from their savings account.
- Customers can set or change their PIN, block, unblock, and request a replacement (the old card is blocked and becomes `REPLACED` once the new card is issued). A customer can't unblock a card that the **bank** blocked.
- **Simulated purchase**: the card must be active and unexpired and the PIN correct. Debit cards are checked against the daily limit and the account balance; credit cards against available credit. Rejected attempts are recorded as FAILED card transactions.

### 2.6 Loans and EMI
- **EMI = P × r × (1 + r)ⁿ / ((1 + r)ⁿ − 1)**. The calculator shows the EMI, total interest and total repayment, with a simulation disclaimer.
- Application details: type (personal, home, education, vehicle), amount, tenure, employment and income. One pending application is allowed at a time.
- **Admin approval**: the admin sets the approved amount (no more than requested) and the rate (default from config). A month-wise schedule is built (principal, interest, EMI, remaining principal), and the amount is credited to the savings account (`LOAN_DISBURSAL`).
- **EMI payment**: always the earliest unpaid EMI. The account and the loan are locked; an optional `emiNumber` guard returns "EMI #1 is already paid". The idempotency key prevents double payment. The loan becomes `CLOSED` after the last EMI.

### 2.7 Customer requests
Requests: cheque book, physical statement, address proof update, other. **Account closure** requires the password and is refused while a loan is active. When the admin approves a closure, the account is set to `CLOSED`, and closed or frozen accounts can't transact.

### 2.8 Admin dashboard
Sections: Dashboard (figures plus an "awaiting review" list), Customers (pending approvals through the **existing** `/admin/approvals` and `/admin/approve`, search, activate/deactivate through the **existing** endpoints), Accounts (freeze, unfreeze, close), Transactions (all ledger entries with filters), FD, Cards, Loans and EMI, KYC, Bill payments, Requests, and Profile.

---

## 3. Database design

| Table | Entity | Purpose | Key relations |
|---|---|---|---|
| `user_login_table` | UserEntity | *existing*: customers and admins | |
| `bank_account` | BankAccountEntity | *existing*; balance is now `DECIMAL(19,2)`, plus `status`, `account_type`, timestamps | `customer_id` → user |
| `transaction_status_table` | TransactionStatusEntity | *reused name*: the account ledger, one row per account movement | FK `account_no` → bank_account |
| `fund_transfer` | TransferEntity | Sender and receiver of each transfer | FKs to both accounts |
| `idempotency_record` | IdempotencyRecordEntity | `bankId:key` → result of the original request | |
| `bill_payment` | BillPaymentEntity | Bill payments | FK user, FK ledger transaction |
| `fixed_deposit` | FixedDepositEntity | FDs; `payout_transaction_id` is UNIQUE | FK account, FK user |
| `card_entity` | CardEntity | *reused name*: cards, masked number and hashes only | FK account, FK user |
| `card_transaction` | CardTransactionEntity | Simulated card spends and repayments | FK card |
| `loan_details` | LoanEntity | *reused name*: loan applications and loans | FK account, FK user |
| `emi_schedule` | EmiScheduleEntity | Month-wise EMIs; UNIQUE (loan_id, emi_number) | FK loan |
| `profile_photo` | ProfilePhotoEntity | Photo as `LONGBLOB` | FK user (PK = bank_id) |
| `kyc_request` | KycRequestEntity | KYC submissions, document as `LONGBLOB` | FK user |
| `customer_request` | CustomerRequestEntity | Service requests | FK user |
| `otp_entity` | OtpEntity | *existing*, also used for email change | |

Every amount is `DECIMAL(19,2)` and `BigDecimal` in Java; no `double` or `float` is used for money. Indexes cover account + date (history), reference, status, and owner columns.

---

## 4. API reference

Base path `/api/v1`. Every JSON response uses the existing envelope `{ isSuccess, data, message, timestamp }`. Lists are paged as `{ content, page, size, totalElements, totalPages }`.
**Idem** = requires the `Idempotency-Key` header (8–100 characters: letters, digits, `-`, `_`).

### Customer (role CUSTOMER)
| Method | Path | Notes |
|---|---|---|
| GET | `/accounts/me` | account overview and balance |
| POST | `/transactions/deposit` | Idem · `{amount, description?}` |
| POST | `/transactions/withdraw` | Idem · `{amount, description?}` |
| GET | `/transactions/beneficiary?accountNo=` | masked beneficiary |
| POST | `/transactions/transfer` | Idem · `{beneficiaryAccountNo, amount, remarks?}` |
| GET | `/transactions/history` | `reference, type, status, from, to, page, size` |
| GET | `/transactions/{id}` | |
| GET | `/transactions/{id}/receipt` | PDF |
| GET | `/transactions/mini-statement?count=5\|10` | |
| GET | `/transactions/statement/pdf?from&to` | PDF |
| GET | `/bills/options` | bill types and modes |
| POST | `/bills/pay` | Idem · `{billType, consumerNumber, amount, paymentMode, billerName?, cardId?, cardPin?}` |
| GET | `/bills/history` | paged |
| GET | `/kyc` | status and history |
| POST | `/kyc` | multipart: `documentType, documentNumber, file` |
| GET | `/kyc/{id}/document` | own document |
| GET | `/fd/rates` · POST `/fd/quote` | rate slabs and maturity calculator |
| POST | `/fd` | Idem · `{amount, tenureMonths, interestRate?}` |
| GET | `/fd` · `/fd/summary` · `/fd/{id}` · `/fd/{id}/maturity` · `/fd/{id}/close-preview` | |
| POST | `/fd/{id}/close` | Idem |
| POST | `/cards/apply` | `{cardType, cardScheme, requestedLimit?}` |
| GET | `/cards` · `/cards/{id}` · `/cards/{id}/limit` · `/cards/{id}/transactions` | |
| PUT | `/cards/{id}/pin` | `{oldPin?, newPin}` |
| PUT | `/cards/{id}/block` · `/cards/{id}/unblock` | |
| POST | `/cards/{id}/replace` | |
| POST | `/cards/{id}/simulate-transaction` | Idem · `{merchantName, amount, pin}` |
| POST | `/cards/{id}/pay-dues` | Idem · `{amount}` (credit cards) |
| GET | `/loans/rates` · POST `/loans/calculate-emi` | |
| POST | `/loans/apply` | `{loanType, amount, tenureMonths, employmentType, monthlyIncome, employerName?, purpose?}` |
| GET | `/loans` · `/loans/{id}` · `/loans/{id}/schedule` · `/loans/{id}/emi/history` | |
| POST | `/loans/{id}/emi/pay` | Idem · `{emiNumber?}` |
| POST | `/requests` · `/requests/account-closure` · GET `/requests` | |

### Customer and admin (any logged-in user)
`GET/PUT /profile`, `POST /profile/email`, `POST /profile/email/verify`, `PUT /profile/password`, `POST/GET/DELETE /profile/photo`

### Admin (role ADMIN), base `/admin`
Existing: `GET /admin`, `GET /approvals`, `POST /approve`, `GET /activate`, `GET /deactivate`.
New: `GET /dashboard`, `GET /customers`, `GET /customers/{bankId}`, `GET /accounts`, `PUT /accounts/{no}/status`, `GET /transactions`, `GET /bills`,
`GET /fd`, `GET /fd/{id}`, `PUT /fd/{id}/approve`, `PUT /fd/{id}/reject`, `POST /fd/process-maturity`,
`GET /cards`, `GET /cards/{id}`, `PUT /cards/{id}/approve|reject|block|unblock|limit`, `GET /cards/{id}/transactions`,
`GET /loans`, `GET /loans/{id}`, `PUT /loans/{id}/approve|reject`, `GET /loans/{id}/schedule`, `GET /loans/{id}/emi/history`, `GET /loans/emi-payments`,
`GET /kyc`, `GET /kyc/{id}/document`, `PUT /kyc/{id}/approve|reject`, `GET /requests`, `PUT /requests/{id}/approve|reject`.

**Postman**: import `docs/DIU-City-Bank.postman_collection.json` (93 requests). Set `customerId`, `adminId` and `password`, then run the two *Auth* requests; the tokens and IDs are stored automatically.

### HTTP status codes
`200/201` success · `400` validation or business rule · `401` not logged in or bad token · `403` wrong role · `404` not found **or not yours** (ownership failures deliberately look like "not found") · `409` conflicting duplicate · `413` upload too large · `422` insufficient balance, credit or card limit · `500` unexpected error (the message is generic; details stay in the server log).

---

## 5. Security and data integrity
- The caller's identity always comes from the JWT (`@AuthenticationPrincipal`). No endpoint trusts a bank ID or account number from the client for ownership.
- `/api/v1/admin/**` requires ADMIN. Banking endpoints require CUSTOMER, so admins can't move money as customers. `/profile/**` is open to both roles.
- Balance changes happen only in `LedgerService`, inside a DB transaction, on a row locked with `SELECT … FOR UPDATE`. A negative balance is impossible.
- Idempotency keys are checked after the lock is taken, so even two concurrent requests with the same key can't both succeed.
- Passwords, PINs and CVVs are stored only as BCrypt hashes and are never returned. The existing `/admin/approvals` response no longer includes the password hash (`@JsonIgnore`). Login, OTP and password-reset requests are no longer written to the log.
- Uploads are checked by file signature and size, served with `nosniff` and `no-store`, and visible only to their owner and admins.

## 6. Configuration
All rules live in `application.properties` under `bank.*`: transaction min/max, FD rate slabs, compounding, tenure, penalty and maturity cron, loan rates per type and limits, card limits and billing days, and upload sizes. Changing them needs no code change.

---

## 7. Test results

`./mvnw test`: **42 tests, 0 failures.** They run against in-memory H2 and call the real HTTP API with real JWTs.

| Suite | Covers |
|---|---|
| CalculatorTest (6) | FD compound interest incl. broken periods, EMI formula, schedule repays exactly the principal |
| TransactionApiTest (9) | deposit, withdraw, insufficient balance + FAILED record, invalid amounts, missing idempotency key, transfer with both ledger legs, same/unknown account, **duplicate transfer replayed**, **rollback** on insufficient funds and on frozen receiver, history filters/pagination, mini statement, PDF receipt/statement, other customers' data → 404 |
| ProfileKycApiTest (7) | get/update profile (protected fields untouched), photo upload/get/replace/delete, invalid format, >2 MB, unauthenticated, password change, email change OTP, KYC submit/duplicate/reject-needs-reason/approve |
| FixedDepositApiTest (6) | quote, validation, create/approve/premature close, insufficient balance, reject refunds + REVERSED, **maturity paid exactly once**, cross-customer access |
| CardBillApiTest (4) | debit lifecycle, PIN rules, wrong PIN, daily limit exceeded, block/unblock, bank block, credit card uses credit line, pay dues, reject, replacement, bills by UPI and credit card, bill over balance |
| LoanApiTest (3) | EMI calculator, apply/approve/disburse/schedule/pay EMI, **duplicate EMI blocked**, insufficient balance, reject |
| SecurityAdminApiTest (6) | customer → admin 403, admin → customer banking 403, anonymous 401, admin dashboard/customers/transactions, no password hash in approvals, account closure flow, login returns role |
| DiuCityBankApplicationTests (1) | existing context-load test |

These were also checked by hand against MySQL, through `curl` and the React UI driven in Chrome: transfer, withdrawal, history filter, bill payment, FD quote and booking, EMI calculator, photo upload, and the admin dashboard, approvals, FD approval and card issue. There were no browser console errors.

---

## 8. Files

**Modified**: `pom.xml` (OpenPDF, H2 for tests), `DiuCityBankApplication`, `SecurityConfig`, `BankAccountEntity`, `TransactionStatusEntity`, `CardEntity`, `LoanEntity`, `UserEntity`, `GlobalExceptionHandler`, `LoginResponse`, `SignupRequest`, `AuthServiceImpl`, `AdminServiceImpl`, `OtpService`, `Util`, `BankAccountRepo`, `UserRepo`, `application.properties`.

**New backend**
- `config/BankProperties`
- `enums/*` (19 enums)
- `entity/`: `TransferEntity`, `IdempotencyRecordEntity`, `BillPaymentEntity`, `FixedDepositEntity`, `CardTransactionEntity`, `EmiScheduleEntity`, `ProfilePhotoEntity`, `KycRequestEntity`, `CustomerRequestEntity`
- `repository/`: one per entity
- `service/`: `AccountService`, `LedgerService`, `IdempotencyService`, `FinancialExecutor`, `TransactionService`, `BillService`, `CardService`, `ProfileService`, `KycService`, `FixedDepositService`, `LoanService`, `CustomerRequestService`, `AdminBankingService`, `PdfService`, `calc/InterestCalculator`, `calc/EmiCalculator`
- `controller/`: `TransactionController`, `BillController`, `ProfileController`, `KycController`, `FixedDepositController`, `CardController`, `LoanController`, `CustomerRequestController`, `AdminBankingController`, `AdminProductController`
- `exception/`: `NotFoundException`, `InsufficientFundsException`
- `util/FileTypes`
- `model/`: 47 request/response DTOs

**New tests**: `src/test/resources/application.properties`, `ApiTestSupport`, `CalculatorTest`, `TransactionApiTest`, `ProfileKycApiTest`, `FixedDepositApiTest`, `CardBillApiTest`, `LoanApiTest`, `SecurityAdminApiTest`.

**Database**: `db/migration-v2-banking-modules.sql`. **Docs**: this file and the Postman collection.

**Frontend** (`frontend/`, moved into the repo from `DIU_City_Bank_Frontend_Fixed.zip`)
- `api/client.js`: one HTTP client with JWT, Idempotency-Key, 401 → login, and protected file downloads. Mock mode removed.
- `api/services.js`: every endpoint.
- `components/ui.jsx`: tables, pager, tabs, dialogs, formatting, loading and error states.
- `pages/Login.jsx`: fixed to read `data.accessToken` and `role`, send `password` on reset, and accept an email or Bank ID.
- `pages/customer/*`, `pages/admin/*`, `App.jsx` routes, `styles.css`.

---

## 9. Notes and limitations
- **Customer Request entity**: a closure request goes to the admin rather than closing the account immediately. That makes it reviewable and keeps the existing admin-approval pattern.
- **FD start date** is the approval date, not a date the customer picks. Back-dated deposits would let a customer collect interest for time the bank didn't hold the money.
- **Email**: the OTP mail for email change uses the same Gmail sender as sign-up.
- **Secrets**: `application.properties` contains the MySQL password, the Gmail app password and the JWT secret. Before pushing to GitHub, move them to environment variables, for example `spring.mail.password=${MAIL_PASSWORD}`.
