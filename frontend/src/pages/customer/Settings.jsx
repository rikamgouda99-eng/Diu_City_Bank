import { useState } from 'react';
import { Link } from 'react-router-dom';
import { profile } from '../../api/services';
import { Icon, Loading, Notice, PageHead, Panel, useAction, useApi } from '../../components/ui';

/** Security and preferences. Shared by customers and admins. */
export default function Settings({ adminMode = false }) {
  const p = useApi(() => profile.get());
  return (<>
    <PageHead title="Settings" sub="Security, sign-in email and preferences." />
    <div className="two">
      <ChangePassword />
      <div className="stack">
        {p.loading ? <Panel><Loading /></Panel> : <ChangeEmail current={p.data?.mailId} onSaved={p.reload} />}
        <Preferences />
      </div>
    </div>
    {!adminMode && (
      <div className="two">
        <Panel title="Service requests" icon="inbox" sub="Cheque book, physical statement, address proof and more.">
          <Link className="btn ghost" to="/app/requests" style={{ alignSelf: 'flex-start' }}>Open requests<Icon n="arrow" size={16} /></Link>
        </Panel>
        <Panel title="Close account" icon="x" className="danger-zone" sub="Ask the bank to permanently close your savings account.">
          <Link className="btn btn-danger" to="/app/close-account" style={{ alignSelf: 'flex-start' }}>Request closure</Link>
        </Panel>
      </div>)}
  </>);
}

function ChangePassword() {
  const [v, setV] = useState({ oldPassword: '', newPassword: '', confirm: '' });
  const act = useAction();
  const strength = [/.{8,}/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((r) => r.test(v.newPassword)).length;
  const submit = async (e) => {
    e.preventDefault();
    if (v.newPassword !== v.confirm) { act.setError('The two new passwords do not match.'); return; }
    const res = await act.run(() => profile.password({ oldPassword: v.oldPassword, newPassword: v.newPassword }));
    if (res) setV({ oldPassword: '', newPassword: '', confirm: '' });
  };
  return (
    <form className="panel" onSubmit={submit}>
      <div className="panel-head"><div><h3><Icon n="lock" />Change password</h3><p className="sub">Use at least 8 characters with a number and a symbol.</p></div></div>
      <label>Current password<input type="password" required autoComplete="current-password" value={v.oldPassword} onChange={(e) => setV({ ...v, oldPassword: e.target.value })} /></label>
      <label>New password<input type="password" required minLength={8} maxLength={64} autoComplete="new-password" value={v.newPassword} onChange={(e) => setV({ ...v, newPassword: e.target.value })} />
        {v.newPassword && (<>
          <div className={`progress ${strength < 3 ? 'warn' : ''}`}><i style={{ width: `${strength * 25}%` }} /></div>
          <span className="hint">Strength: {['Very weak', 'Weak', 'Fair', 'Good', 'Strong'][strength]}</span>
        </>)}</label>
      <label>Confirm new password<input type="password" required minLength={8} autoComplete="new-password" value={v.confirm} onChange={(e) => setV({ ...v, confirm: e.target.value })} /></label>
      <Notice error={act.error} />
      <button disabled={act.busy}>{act.busy ? 'Updating…' : 'Update password'}</button>
    </form>);
}

function ChangeEmail({ current, onSaved }) {
  const [step, setStep] = useState('ask');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const act = useAction();
  const submit = async (e) => {
    e.preventDefault();
    if (step === 'ask') {
      const res = await act.run(() => profile.requestEmail(email.trim()));
      if (res) setStep('verify');
    } else {
      const res = await act.run(() => profile.verifyEmail({ newEmail: email.trim(), otp }));
      if (res) { setStep('ask'); setEmail(''); setOtp(''); onSaved(); }
    }
  };
  return (
    <form className="panel" onSubmit={submit}>
      <div className="panel-head"><div><h3><Icon n="mail" />Sign-in email</h3><p className="sub">Current: {current}</p></div></div>
      <label>New email<input type="email" required readOnly={step === 'verify'} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" /></label>
      {step === 'verify' && (
        <label>6-digit code sent to the new email<input required autoFocus inputMode="numeric" pattern="\d{6}" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="••••••" /></label>)}
      <Notice error={act.error} />
      <div className="row gap">
        {step === 'verify' && <button type="button" className="ghost" onClick={() => { setStep('ask'); act.clear(); }}>Back</button>}
        <button disabled={act.busy}>{step === 'ask' ? 'Send verification code' : 'Verify and update'}</button>
      </div>
    </form>);
}

function Preferences() {
  const read = (k) => { try { return localStorage.getItem(k) === '1'; } catch { return false; } };
  const [hide, setHide] = useState(() => read('dcb.hideBalance'));
  const [compact, setCompact] = useState(() => read('dcb.collapsed'));
  const save = (k, v, setter) => { try { localStorage.setItem(k, v ? '1' : '0'); } catch { /* ignore */ } setter(v); };
  return (
    <Panel title="Preferences" icon="settings" sub="Stored on this device only.">
      <label className="check"><input type="checkbox" checked={hide} onChange={(e) => save('dcb.hideBalance', e.target.checked, setHide)} />Hide my balance on the dashboard by default</label>
      <label className="check"><input type="checkbox" checked={compact} onChange={(e) => save('dcb.collapsed', e.target.checked, setCompact)} />Start with a collapsed sidebar (applies after reload)</label>
    </Panel>);
}
