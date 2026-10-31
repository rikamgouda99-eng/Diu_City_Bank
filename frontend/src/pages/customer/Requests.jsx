import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { requests } from '../../api/services';
import { Badge, Icon, Loading, Notice, PageHead, Panel, Table, dateTime, words, useAction, useApi } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';

const TYPES = ['CHEQUE_BOOK', 'PHYSICAL_STATEMENT', 'ADDRESS_PROOF_UPDATE', 'OTHER'];

export default function Requests() {
  const list = useApi(() => requests.mine());
  const [v, setV] = useState({ requestType: 'CHEQUE_BOOK', subject: '', description: '' });
  const act = useAction();
  const submit = async (e) => {
    e.preventDefault();
    const res = await act.run(() => requests.create({ ...v, description: v.description || undefined }));
    if (res) { setV({ requestType: 'CHEQUE_BOOK', subject: '', description: '' }); list.reload(); }
  };
  return (<>
    <PageHead title="Service requests" sub="Ask the bank for a cheque book, statement and more." />
    <div className="two">
      <form className="panel" onSubmit={submit}>
        <h3>New request</h3>
        <label>Request type<select value={v.requestType} onChange={(e) => setV({ ...v, requestType: e.target.value })}>{TYPES.map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
        <label>Subject<input required maxLength={120} value={v.subject} onChange={(e) => setV({ ...v, subject: e.target.value })} /></label>
        <label>Details (optional)<textarea maxLength={1000} rows={4} value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} /></label>
        <button disabled={act.busy}>Submit request</button>
        <Notice error={act.error} ok={act.ok} />
      </form>
      <Panel title="My requests">
        {list.loading ? <Loading /> : list.error ? <Notice error={list.error} /> : (
          <Table rows={list.data} empty="No requests yet." columns={[
            { key: 'createdAt', label: 'Raised', render: (r) => dateTime(r.createdAt) },
            { key: 'requestType', label: 'Type', render: (r) => words(r.requestType) },
            { key: 'subject', label: 'Subject' },
            { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
            { key: 'adminComment', label: 'Bank comment' },
          ]} />)}
      </Panel>
    </div>
  </>);
}

export function CloseAccount() {
  const [v, setV] = useState({ reason: 'Moving to another bank', password: '' }); const [sure, setSure] = useState(false);
  const [done, setDone] = useState(false);
  const act = useAction();
  const { logout } = useAuth(); const nav = useNavigate();
  const set = (n) => (e) => setV({ ...v, [n]: e.target.value });
  const submit = async (e) => {
    e.preventDefault();
    const res = await act.run(() => requests.closeAccount(v));
    if (res) setDone(true);
  };
  if (done) return (
    <div className="panel danger-zone"><h2>Closure requested</h2>
      <p className="done"><Icon n="check" /> Your account closure request was sent to the bank.</p>
      <p className="muted">The bank will review it and close the account. You can follow it under Requests.</p>
      <button onClick={() => { logout(); nav('/'); }}>Log out</button></div>);
  return (<>
    <h2>Close bank account</h2>
    <form className="panel danger-zone" onSubmit={submit}>
      <p className="msg">Closing your account is permanent once the bank approves it. Move your money out and repay any loans first.</p>
      <label>Why are you leaving?
        <select value={v.reason} onChange={set('reason')}>
          {['Moving to another bank', 'Fees are too high', 'I no longer need this account', 'Other'].map((r) => <option key={r}>{r}</option>)}</select></label>
      <label>Confirm your password<input type="password" required value={v.password} onChange={set('password')} autoComplete="current-password" /></label>
      <label className="check"><input type="checkbox" checked={sure} onChange={(e) => setSure(e.target.checked)} />I understand this cannot be undone.</label>
      <button className="btn-danger" disabled={!sure || act.busy}>{act.busy ? 'Sending…' : 'Request account closure'}</button>
      <Notice error={act.error} />
    </form></>);
}
