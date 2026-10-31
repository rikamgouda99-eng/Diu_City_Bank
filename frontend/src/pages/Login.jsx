import { useState, useEffect } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { auth } from '../api/services';
import { useAuth } from '../context/AuthContext';
import { Icon } from '../components/ui';

const TITLES = {
  login: 'Welcome back',
  register: 'Create your account',
  forgot: 'Forgot your password?',
  reset: 'Set a new password',
  verify: 'Verify your email',
};

const OTP_MS = 10 * 60 * 1000;

const mmss = (s) =>
  `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(
    s % 60
  ).padStart(2, '0')}`;

export default function Login() {
  const [params] = useSearchParams();
  const [admin, setAdmin] = useState(params.get('role') === 'admin');
  const [mode, setMode] = useState(params.get('mode') === 'register' ? 'register' : 'login');
  const [v, setV] = useState({});
  const [msg, setMsg] = useState(null);
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [until, setUntil] = useState(0);
  const [left, setLeft] = useState(0);

  const { login, user } = useAuth();
  const nav = useNavigate();

  useEffect(() => {
    if (!until) return;

    const tick = () => {
      setLeft(Math.max(0, Math.ceil((until - Date.now()) / 1000)));
    };

    tick();
    const id = setInterval(tick, 1000);

    return () => clearInterval(id);
  }, [until]);

  const set = (name) => (e) => {
    setV((prev) => ({
      ...prev,
      [name]: e.target.value,
    }));
  };

  const go = (nextMode, text, ok = true) => {
    setMode(nextMode);
    setMsg(text ? { text, ok } : null);
  };

  const submit = async (e) => {
    e.preventDefault();
    setMsg(null);
    setBusy(true);

    try {
      if (mode === 'register') {
        const signupData = {
          mailId: v.email,
          firstName: v.firstName,
          middleName: v.middleName || '',
          lastName: v.lastName,
          addressDetails: v.addressDetails,
          phoneNumber: v.phoneNumber,
          gender: v.gender || '',
          dob: v.dob,
          maritalStatus: v.maritalStatus,
          password: v.password,
        };

        await auth.signup(signupData);

        setUntil(Date.now() + OTP_MS);
        setV((prev) => ({ ...prev, otp: '' }));

        go(
          'verify',
          `We sent a verification code to ${v.email}.`
        );
      } else if (mode === 'verify') {
        await auth.verifyOtp({
          mailId: v.email,
          otp: v.otp,
        });

        setUntil(0);
        setV((prev) => ({
          email: prev.email,
          password: '',
        }));

        go(
          'login',
          'Email verified. Try logging in with your account.'
        );
      } else if (mode === 'forgot') {
        await auth.forgot(v.email.trim());
        // the reset step verifies by email, so ask for it if a Bank ID was used
        if (!v.email.includes('@')) setV((prev) => ({ ...prev, email: '' }));

        go(
          'reset',
          `We sent a reset code to the registered email of ${v.email}.`
        );
      } else if (mode === 'reset') {
        if (v.newPassword !== v.confirm) {
          throw new Error('The two passwords do not match.');
        }

        await auth.reset({
          mailId: v.email.trim(),
          otp: v.otp,
          password: v.newPassword,
        });

        setV({ email: v.email });
        go(
          'login',
          'Password reset request completed. Log in with your new password.'
        );
      } else {
        // the backend accepts either a mail ID or a bank ID
        const id = v.email.trim();
        const res = await auth.login({
          bankId: id.includes('@') ? '' : id,
          mailId: id.includes('@') ? id : '',
          password: v.password,
        });
        const d = res.data;
        if ((d.role === 'ADMIN') !== admin) {
          throw new Error(admin ? 'This is not an admin account. Use the Customer tab.' : 'Admins must log in from the Admin tab.');
        }
        login({ token: d.accessToken, refreshToken: d.refreshToken, role: d.role, name: d.name || d.bankId, bankId: d.bankId });
        nav(d.role === 'ADMIN' ? '/admin' : '/app');
      }
    } catch (error) {
      setMsg({
        text: error.message || 'Something went wrong. Please try again.',
        ok: false,
      });
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setMsg(null);
    setBusy(true);

    try {
      await auth.resendOtp(v.email);

      setUntil(Date.now() + OTP_MS);
      setV((prev) => ({ ...prev, otp: '' }));
      setMsg({ text: 'A new OTP request was sent.', ok: true });
    } catch (error) {
      setMsg({
        text: error.message || 'Could not resend OTP.',
        ok: false,
      });
    } finally {
      setBusy(false);
    }
  };

  const pick = (isAdmin) => {
    setAdmin(isAdmin);
    go('login');
  };

  const pwInput = (name, label) => (
    <label key={name}>
      {label}
      <span className="pw">
        <input
          type={show ? 'text' : 'password'}
          required
          minLength={name === 'password' && mode === 'login' ? undefined : 8}
          value={v[name] || ''}
          onChange={set(name)}
          autoComplete={
            name === 'password' ? 'current-password' : 'new-password'
          }
        />
        <button
          type="button"
          className="eye"
          aria-label={show ? 'Hide password' : 'Show password'}
          onClick={() => setShow((prev) => !prev)}
        >
          <Icon n={show ? 'eyeoff' : 'eye'} />
        </button>
      </span>
    </label>
  );

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/app'} replace />;

  return (
    <div className="login">
      <div className="hero">
        <div>
          <div className="row">
            <Link to="/" className="brand">
              <span className="logo">D</span>
              <b>DIU City Bank</b>
            </Link>
            <Link to="/" className="back-home"><Icon n="left" size={16} />Back to home</Link>
          </div>

          <h1>Banking that keeps pace with you.</h1>

          <p>
            Transfers, cards, fixed deposits and loans in one secure place.
          </p>
        </div>

        <svg className="art" viewBox="0 0 480 260" aria-hidden="true">
          <defs>
            <linearGradient id="lc1" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#2A4C8F" /><stop offset="1" stopColor="#101B35" /></linearGradient>
            <linearGradient id="lc2" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#3B2F55" /><stop offset="1" stopColor="#120E1C" /></linearGradient>
            <linearGradient id="chip" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#EAD9A6" /><stop offset="1" stopColor="#B8975A" /></linearGradient>
          </defs>
          <g transform="rotate(-8 300 120)">
            <rect x="170" y="30" width="270" height="170" rx="18" fill="url(#lc2)" stroke="rgba(255,255,255,.08)" />
            <rect x="196" y="78" width="40" height="30" rx="6" fill="url(#chip)" />
            <text x="414" y="66" textAnchor="end" fill="#C9A55A" fontSize="18" fontWeight="800" fontStyle="italic" fontFamily="Inter, sans-serif">RUPAY</text>
          </g>
          <rect x="30" y="70" width="280" height="176" rx="18" fill="url(#lc1)" stroke="rgba(255,255,255,.1)" />
          <text x="54" y="104" fill="#fff" fontSize="14" fontWeight="700" fontFamily="Inter, sans-serif">DIU City Bank</text>
          <rect x="54" y="122" width="40" height="30" rx="6" fill="url(#chip)" />
          <text x="54" y="190" fill="#E2E8F0" fontSize="17" letterSpacing="3" fontFamily="ui-monospace, Menlo, monospace">•••• •••• •••• 4821</text>
          <text x="54" y="224" fill="#9FB2D3" fontSize="10" letterSpacing="1.5" fontFamily="Inter, sans-serif">CARD HOLDER</text>
          <text x="286" y="224" textAnchor="end" fill="#fff" fontSize="16" fontWeight="800" fontStyle="italic" fontFamily="Inter, sans-serif">VISA</text>
        </svg>

        <div className="trust">
          <span><Icon n="shield" /> Encrypted sessions</span>
          <span><Icon n="lock" /> PINs and passwords hashed</span>
          <span><Icon n="checkc" /> Demo / simulation only</span>
        </div>
      </div>

      <div className="formside">
        <form onSubmit={submit} className={mode === 'register' ? 'wide' : ''}>
          <div className="tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={!admin}
              className={!admin ? 'on' : ''}
              onClick={() => pick(false)}
            >
              Customer
            </button>

            <button
              type="button"
              role="tab"
              aria-selected={admin}
              className={admin ? 'on' : ''}
              onClick={() => pick(true)}
            >
              Admin
            </button>
          </div>

          <div>
            <h2>{TITLES[mode]}</h2>

            {mode === 'forgot' && (
              <p className="muted">
                Enter your {admin ? 'admin' : 'customer'} email or Bank ID
                to request a reset code.
              </p>
            )}

            {mode === 'login' && (
              <p className="muted">
                Log in as {admin ? 'a bank admin' : 'a customer'}.
              </p>
            )}
          </div>

          {mode === 'register' && (
            <div className="reg-grid">
              <label>
                First name
                <input
                  required
                  value={v.firstName || ''}
                  onChange={set('firstName')}
                />
              </label>

              <label>
                Last name
                <input
                  required
                  value={v.lastName || ''}
                  onChange={set('lastName')}
                />
              </label>

              <label className="full">
                Middle name (optional)
                <input
                  value={v.middleName || ''}
                  onChange={set('middleName')}
                />
              </label>

              <label className="full">
                Address
                <input
                  required
                  value={v.addressDetails || ''}
                  onChange={set('addressDetails')}
                />
              </label>

              <label>
                Phone number
                <input
                  required
                  type="tel"
                  inputMode="tel"
                  value={v.phoneNumber || ''}
                  onChange={set('phoneNumber')}
                />
              </label>

              <label>
                Date of birth
                <input
                  required
                  type="date"
                  value={v.dob || ''}
                  onChange={set('dob')}
                />
              </label>

              <label>
                Marital status
                <select
                  required
                  value={v.maritalStatus || ''}
                  onChange={set('maritalStatus')}
                >
                  <option value="">Select marital status</option>
                  <option value="single">Single</option>
                  <option value="married">Married</option>
                  <option value="divorced">Divorced</option>
                  <option value="prefer not to disclose">
                    Prefer not to disclose
                  </option>
                </select>
              </label>

              <label>
                Gender (optional)
                <select
                  value={v.gender || ''}
                  onChange={set('gender')}
                >
                  <option value="">Select gender</option>
                  <option value="Male">Male</option>
                  <option value="Female">Female</option>
                  <option value="Other">Other</option>
                  <option value="Prefer not to disclose">
                    Prefer not to disclose
                  </option>
                </select>
              </label>
            </div>
          )}

          <label>
            {mode === 'login' || mode === 'forgot' ? 'Email or Bank ID' : mode === 'reset' ? 'Registered email' : 'Email'}
            <input
              type={mode === 'login' || mode === 'forgot' ? 'text' : 'email'}
              autoComplete="username"
              required
              value={v.email || ''}
              onChange={set('email')}
              readOnly={mode === 'verify' || (mode === 'reset' && v.email?.includes('@'))}
            />
          </label>

          {mode === 'verify' && (
            <>
              <label>
                6-digit OTP
                <input
                  required
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  autoComplete="one-time-code"
                  value={v.otp || ''}
                  onChange={set('otp')}
                />
              </label>

              <div className="row">
                <span className="muted">
                  {left > 0
                    ? `Resend OTP available in ${mmss(left)}`
                    : 'Did not get the OTP?'}
                </span>

                <button
                  type="button"
                  className="link"
                  disabled={left > 0 || busy}
                  onClick={resend}
                >
                  Resend OTP
                </button>
              </div>
            </>
          )}

          {mode === 'reset' && (
            <>
              <label>
                6-digit reset code
                <input
                  required
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={v.otp || ''}
                  onChange={set('otp')}
                />
              </label>

              {pwInput('newPassword', 'New password')}
              {pwInput('confirm', 'Confirm new password')}
            </>
          )}

          {(mode === 'login' || mode === 'register') &&
            pwInput('password', 'Password')}

          {mode === 'login' && (
            <div className="row">
              <span />
              <button
                type="button"
                className="link"
                onClick={() => go('forgot')}
              >
                Forgot password?
              </button>
            </div>
          )}

          <button type="submit" disabled={busy}>
            {busy
              ? 'Please wait…'
              : {
                  login: 'Log in',
                  register: 'Generate OTP',
                  verify: 'Verify OTP',
                  forgot: 'Send reset code',
                  reset: 'Change password',
                }[mode]}
          </button>

          {msg && (
            <p
              className={`msg ${msg.ok ? 'ok' : ''}`}
              role="alert"
            >
              {msg.text}
            </p>
          )}

          {(mode === 'forgot' || mode === 'reset') && (
            <button
              type="button"
              className="link"
              onClick={() => go('login')}
            >
              Back to log in
            </button>
          )}

          {mode === 'verify' && (
            <button
              type="button"
              className="link"
              onClick={() => go('register')}
            >
              Change my details
            </button>
          )}

          {!admin && (mode === 'login' || mode === 'register') && (
            <button
              type="button"
              className="link"
              onClick={() =>
                go(mode === 'login' ? 'register' : 'login')
              }
            >
              {mode === 'register'
                ? 'I already have an account'
                : 'New here? Create an account'}
            </button>
          )}
        </form>
      </div>
    </div>
  );
}