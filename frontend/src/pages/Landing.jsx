import { Link } from 'react-router-dom';
import { Icon } from '../components/ui';
import { BankCard } from './customer/Cards';
import { useAuth } from '../context/AuthContext';

const FEATURES = [
  ['send', 'Instant transfers', 'Send money to any DIU City Bank account with beneficiary checks and a downloadable receipt.'],
  ['vault', 'Fixed deposits', 'Book an FD, see your maturity amount up front and watch your investment grow.'],
  ['card', 'Debit & credit cards', 'Apply, set your PIN, track limits and block a card in one tap.'],
  ['loan', 'Loans & EMI', 'Estimate your EMI, apply online and follow every instalment of your schedule.'],
];
const SERVICES = [
  ['wallet', 'Savings account', 'Balance, statements and account details in one place.'],
  ['swap', 'Transactions', 'Fund transfers with instant email alerts and full history with filters.'],
  ['receipt', 'Bill payments', 'Mobile recharge, electricity, water, internet and more by card or bank draft.'],
  ['vault', 'Fixed deposits', 'Quarterly compounding with clear premature-closure rules.'],
  ['card', 'Cards', 'Premium debit and credit cards with spend and credit limits.'],
  ['id', 'Profile & KYC', 'Keep your details current and verify your identity online.'],
];

/** Decorative only: the example cards and widgets below are an illustration, not account data. */
function HeroVisual() {
  const sample = { cardType: 'DEBIT', cardScheme: 'VISA', status: 'ACTIVE', maskedNumber: 'XXXX XXXX XXXX 4821', holderName: 'YOUR NAME', expiry: '10/31' };
  return (
    <div className="l-visual" aria-hidden="true">
      <div className="bankcard credit c1" style={{ pointerEvents: 'none' }}>
        <div className="top"><b>DIU City Bank<small>Credit</small></b><span className="scheme">RUPAY</span></div>
        <span className="chip" />
        <div className="pan">•••• •••• •••• 7310</div>
        <div className="bottom"><div><span>Card holder</span><b>YOUR NAME</b></div><div style={{ textAlign: 'right' }}><span>Valid thru</span><b>08/30</b></div></div>
      </div>
      <div className="c2">
        <BankCard card={sample} onClick={() => {}} />
      </div>
      <div className="float f1">
        <span>Example balance</span><b>₹1,24,580.00</b>
        <svg className="spark" viewBox="0 0 200 38" preserveAspectRatio="none"><path d="M0 30 L30 26 L60 28 L90 18 L120 20 L150 10 L200 6" fill="none" stroke="#2457D6" strokeWidth="2.5" /></svg>
      </div>
      <div className="float f2">
        <div className="row"><span className="txn-ic cr"><Icon n="check" size={16} /></span>
          <div><span>Transfer successful</span><b style={{ fontSize: '1.05rem' }}>₹2,500.00</b></div></div>
      </div>
    </div>);
}

export default function Landing() {
  const { user } = useAuth();
  const home = user ? (user.role === 'ADMIN' ? '/admin' : '/app') : null;
  return (
    <div className="landing">
      <header className="l-nav">
        <div className="l-wrap">
          <Link to="/" className="brand"><span className="logo">D</span><b>DIU City Bank</b></Link>
          <nav className="l-links" aria-label="Sections"><a href="#features">Features</a><a href="#services">Services</a><a href="#security">Security</a></nav>
          <div className="l-cta">
            {home ? <Link className="btn" to={home}>Go to dashboard</Link> : (<>
              <Link className="btn light ghost" to="/login">Log in</Link>
              <Link className="btn" to="/login?mode=register">Get started</Link>
            </>)}
          </div>
        </div>
      </header>

      <section className="l-hero">
        <div className="l-wrap">
          <div>
            <span className="eyebrow"><i />Digital banking · demo / simulation</span>
            <h1>Banking that keeps <em>pace with you.</em></h1>
            <p className="lead">Open an account, move money, invest in fixed deposits, manage cards and repay loans, all from one secure, beautifully simple app.</p>
            <div className="row gap actions">
              <Link className="btn" to={home || '/login?mode=register'}>{home ? 'Open dashboard' : 'Open an account'}<Icon n="arrow" size={16} /></Link>
              {!home && <Link className="btn light" to="/login">Log in</Link>}
            </div>
            <div className="proof">
              <div><b>6</b><span>banking modules</span></div>
              <div><b>2-step</b><span>OTP verified sign-up</span></div>
              <div><b>0</b><span>plaintext PINs stored</span></div>
            </div>
          </div>
          <HeroVisual />
        </div>
      </section>

      <section className="l-section" id="features">
        <div className="l-wrap">
          <div className="l-head center"><span className="kicker">Why DIU City Bank</span><h2>Everything you need, nothing you don't</h2>
            <p>A focused set of banking tools designed to be clear at a glance and safe by default.</p></div>
          <div className="feature-grid">
            {FEATURES.map(([ic, t, d]) => <div key={t} className="feature"><span className="ic"><Icon n={ic} size={22} /></span><h3>{t}</h3><p>{d}</p></div>)}
          </div>
        </div>
      </section>

      <section className="l-section alt" id="services">
        <div className="l-wrap">
          <div className="l-head"><span className="kicker">Banking services</span><h2>One account, every service</h2>
            <p>Sign in once and reach every part of your banking from a single dashboard.</p></div>
          <div className="service-grid">
            {SERVICES.map(([ic, t, d]) => <Link key={t} to={home || '/login'} className="service"><span className="ic"><Icon n={ic} size={20} /></span><div><b>{t}</b><span>{d}</span></div></Link>)}
          </div>
        </div>
      </section>

      <section className="l-section" id="security">
        <div className="l-wrap security">
          <div>
            <div className="l-head" style={{ marginBottom: 28 }}><span className="kicker">Security & trust</span><h2>Built to protect every rupee</h2>
              <p>Security isn't a feature you switch on. It's how every request is handled.</p></div>
            <div className="sec-list">
              <div><span className="ic"><Icon n="shield" /></span><div><b>Verified sign-up</b><span>Every new account is confirmed with an email OTP and approved by the bank.</span></div></div>
              <div><span className="ic"><Icon n="lock" /></span><div><b>Hashed credentials</b><span>Passwords, card PINs and CVVs are stored only as one-way hashes, never in plain text.</span></div></div>
              <div><span className="ic"><Icon n="swap" /></span><div><b>All-or-nothing transfers</b><span>Debit and credit happen together, and duplicate requests can never send money twice.</span></div></div>
            </div>
          </div>
          <div className="sec-panel">
            <div className="row"><span>Session</span><b>Signed token, expires automatically</b></div>
            <div className="row"><span>Card number</span><b>Masked, last 4 digits only</b></div>
            <div className="row"><span>Your data</span><b>Visible only to you and the bank</b></div>
            <div className="row"><span>Mode</span><b>Demo / simulation, no real money</b></div>
          </div>
        </div>
      </section>

      <section className="l-section alt" style={{ paddingTop: 0, background: 'var(--surface)' }}>
        <div className="l-wrap">
          <div className="cta-band">
            <div><h2>Ready to open your account?</h2><p>It takes a couple of minutes. Verify your email and the bank does the rest.</p></div>
            <Link className="btn" to={home || '/login?mode=register'}>{home ? 'Go to dashboard' : 'Get started'}<Icon n="arrow" size={16} /></Link>
          </div>
        </div>
      </section>

      <footer className="l-foot">
        <div className="l-wrap">
          <div className="cols">
            <div><Link to="/" className="brand" style={{ padding: 0 }}><span className="logo">D</span><b>DIU City Bank</b></Link>
              <p style={{ marginTop: 14, maxWidth: '32ch' }}>A digital banking simulation built for an academic project.</p></div>
            <div><h4>Banking</h4><a href="#services">Accounts</a><a href="#services">Transfers</a><a href="#services">Fixed deposits</a><a href="#services">Cards & loans</a></div>
            <div><h4>Get started</h4><Link to="/login?mode=register">Open an account</Link><Link to="/login">Customer login</Link><Link to="/login?role=admin">Admin login</Link></div>
            <div><h4>Trust</h4><a href="#security">Security</a><a href="#features">Features</a></div>
          </div>
          <div className="bottom"><span>© {new Date().getFullYear()} DIU City Bank. Demo / simulation, no real money moves.</span><span>Diu, India</span></div>
        </div>
      </footer>
    </div>);
}
