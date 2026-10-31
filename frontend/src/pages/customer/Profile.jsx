import { useState } from 'react';
import { Link } from 'react-router-dom';
import { kyc, profile } from '../../api/services';
import { Badge, Icon, Loading, Notice, PageHead, Panel, Table, date, dateTime, money, words, useAction, useApi, useMyPhoto } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';

const photoChanged = () => window.dispatchEvent(new Event('dcb:photo'));

/** Shared by customers and admins; KYC and account details only show for customers. */
export default function Profile({ adminMode = false }) {
  const p = useApi(() => profile.get());
  return (<>
    <PageHead title={adminMode ? 'Admin profile' : 'My profile'} sub="Your personal details, photo and verification status.">
      <Link className="btn ghost" to={adminMode ? '/admin/settings' : '/app/settings'}><Icon n="settings" />Security settings</Link>
    </PageHead>
    {p.loading ? <Panel><Loading rows={5} /></Panel> : p.error ? <Notice error={p.error} /> : (<>
      <Header data={p.data} adminMode={adminMode} onChange={p.reload} />
      <div className="two">
        <PersonalDetails data={p.data} onSaved={p.reload} />
        <div className="stack">
          <Panel title="Contact details" icon="mail">
            <div className="info-grid">
              <div><span>Email</span><b>{p.data.mailId}</b></div>
              <div><span>Mobile</span><b>{p.data.phoneNumber || '—'}</b></div>
              <div style={{ gridColumn: '1 / -1' }}><span>Address</span><b>{p.data.addressDetails || '—'}</b></div>
            </div>
          </Panel>
          {!adminMode && (
            <Panel title="Account information" icon="bank">
              <div className="info-grid">
                <div><span>Account number</span><b className="num">{p.data.accountNo || 'Not opened yet'}</b></div>
                <div><span>Account type</span><b>{words(p.data.accountType)}</b></div>
                <div><span>Balance</span><b className="num">{money(p.data.accountBalance)}</b></div>
                <div><span>Account status</span><b>{p.data.accountStatus ? <Badge v={p.data.accountStatus} /> : '—'}</b></div>
                <div><span>Customer since</span><b>{date(p.data.createdDate)}</b></div>
                <div><span>Application</span><b>{p.data.applicationStatus || '—'}</b></div>
              </div>
            </Panel>)}
        </div>
      </div>
      {!adminMode && <Kyc onChange={p.reload} />}
    </>)}
  </>);
}

function Header({ data, adminMode, onChange }) {
  const url = useMyPhoto(true);
  const act = useAction();
  const upload = async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { act.setError('Please choose a JPG, PNG or WebP image.'); return; }
    if (file.size > 2 * 1024 * 1024) { act.setError('Image must be 2 MB or smaller.'); return; }
    const res = await act.run(() => profile.uploadPhoto(file));
    if (res) { photoChanged(); onChange(); }
  };
  const remove = async () => { const r = await act.run(() => profile.deletePhoto()); if (r) { photoChanged(); onChange(); } };
  return (
    <section className="panel section">
      <div className="profile-hero">
        {url ? <img className="avatar" src={url} alt="Your profile" /> : <div className="avatar placeholder" aria-label="Default profile picture">{data.firstName?.[0] || '?'}</div>}
        <div style={{ flex: 1, minWidth: 220 }}>
          <h3>{data.fullName}</h3>
          <span className="muted">{adminMode ? 'Administrator' : 'Customer'} · {data.bankId}</span>
          <div className="badges">
            {!adminMode && <span title="KYC"><Badge v={data.kycStatus === 'APPROVED' ? 'APPROVED' : data.kycStatus} /></span>}
            <span className={`badge ${data.isVerified ? 'good' : 'warn'}`}>{data.isVerified ? 'Email verified' : 'Email not verified'}</span>
            {!adminMode && <span className={`badge ${data.isApproved ? 'good' : 'warn'}`}>{data.isApproved ? 'Account approved' : 'Approval pending'}</span>}
          </div>
          <div className="row gap">
            <label className="btn file"><Icon n="camera" />{data.hasPhoto ? 'Change photo' : 'Upload photo'}
              <input type="file" accept="image/jpeg,image/png,image/webp" onChange={upload} hidden /></label>
            {data.hasPhoto && <button type="button" className="ghost" disabled={act.busy} onClick={remove}>Remove</button>}
            <span className="hint">JPG, PNG or WebP · max 2 MB</span>
          </div>
        </div>
      </div>
      <Notice error={act.error} />
    </section>);
}

function PersonalDetails({ data, onSaved }) {
  const pick = (d) => ({ firstName: d.firstName || '', middleName: d.middleName || '', lastName: d.lastName || '', phoneNumber: d.phoneNumber || '',
    addressDetails: d.addressDetails || '', gender: d.gender || '', maritalStatus: d.maritalStatus || '' });
  const [editing, setEditing] = useState(false);
  const [v, setV] = useState(() => pick(data));
  const act = useAction();
  const { user, login } = useAuth();
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });
  const submit = async (e) => {
    e.preventDefault();
    const res = await act.run(() => profile.update(v));
    if (res) { login({ ...user, name: res.data.fullName }); setEditing(false); onSaved(); }
  };
  const actions = editing
    ? <button type="button" className="ghost sm" onClick={() => { setV(pick(data)); setEditing(false); act.clear(); }}>Cancel</button>
    : <button type="button" className="ghost sm" onClick={() => setEditing(true)}>Edit</button>;

  if (!editing) return (
    <Panel title="Personal information" icon="user" actions={actions}>
      <div className="info-grid">
        <div><span>First name</span><b>{data.firstName}</b></div>
        <div><span>Middle name</span><b>{data.middleName || '—'}</b></div>
        <div><span>Last name</span><b>{data.lastName}</b></div>
        <div><span>Date of birth</span><b>{date(data.dob)}</b></div>
        <div><span>Gender</span><b>{data.gender || '—'}</b></div>
        <div><span>Marital status</span><b>{words(data.maritalStatus)}</b></div>
      </div>
      <p className="hint">Bank ID, account, role and verification status are managed by the bank and can't be edited.</p>
    </Panel>);

  return (
    <form className="panel" onSubmit={submit}>
      <div className="panel-head"><h3><Icon n="user" />Edit personal information</h3>{actions}</div>
      <div className="form-grid">
        <label>First name<input required maxLength={50} pattern="[\p{L} .'\-]+" title="Letters only" value={v.firstName} onChange={set('firstName')} /></label>
        <label>Middle name<input maxLength={50} value={v.middleName} onChange={set('middleName')} /></label>
        <label>Last name<input required maxLength={50} pattern="[\p{L} .'\-]+" title="Letters only" value={v.lastName} onChange={set('lastName')} /></label>
        <label>Mobile number<input required type="tel" pattern="\+?\d{10,13}" title="10-13 digits" value={v.phoneNumber} onChange={set('phoneNumber')} /></label>
        <label>Gender<select value={v.gender} onChange={set('gender')}><option value="">Prefer not to say</option><option>Male</option><option>Female</option><option>Other</option></select></label>
        <label>Marital status<select required value={v.maritalStatus} onChange={set('maritalStatus')}>
          <option value="">Select</option><option value="single">Single</option><option value="married">Married</option><option value="divorced">Divorced</option><option value="prefer not to disclose">Prefer not to disclose</option></select></label>
      </div>
      <label>Address<textarea required rows={2} maxLength={250} value={v.addressDetails} onChange={set('addressDetails')} /></label>
      <Notice error={act.error} />
      <button disabled={act.busy}>{act.busy ? 'Saving…' : 'Save changes'}</button>
    </form>);
}

function Kyc({ onChange }) {
  const k = useApi(() => kyc.mine());
  const [v, setV] = useState({ documentType: 'AADHAAR', documentNumber: '', file: null });
  const act = useAction();
  const open = useAction();
  const canSubmit = k.data && (k.data.kycStatus === 'NOT_SUBMITTED' || k.data.kycStatus === 'REJECTED');
  const submit = async (e) => {
    e.preventDefault();
    if (!v.file) { act.setError('Please attach the document.'); return; }
    if (v.file.size > 5 * 1024 * 1024) { act.setError('Document must be 5 MB or smaller.'); return; }
    const res = await act.run(() => kyc.submit(v.documentType, v.documentNumber, v.file));
    if (res) { setV({ documentType: 'AADHAAR', documentNumber: '', file: null }); k.reload(); onChange(); }
  };
  return (
    <div className="two">
      <Panel title="KYC verification" icon="id" actions={k.data && <Badge v={k.data.kycStatus} />}>
        {k.loading ? <Loading /> : k.error ? <Notice error={k.error} /> : (<>
          {k.data.latest?.adminComment && <p className={`msg ${k.data.kycStatus === 'APPROVED' ? 'ok' : ''}`}>Bank comment: {k.data.latest.adminComment}</p>}
          {canSubmit ? (
            <form onSubmit={submit} className="stack">
              <div className="form-grid">
                <label>Document type<select value={v.documentType} onChange={(e) => setV({ ...v, documentType: e.target.value })}>
                  {['AADHAAR', 'PAN', 'PASSPORT', 'DRIVING_LICENSE', 'VOTER_ID'].map((t) => <option key={t} value={t}>{words(t)}</option>)}</select></label>
                <label>Document number<input required maxLength={20} value={v.documentNumber} onChange={(e) => setV({ ...v, documentNumber: e.target.value })}
                  placeholder={v.documentType === 'AADHAAR' ? '12 digits' : v.documentType === 'PAN' ? 'ABCDE1234F' : ''} /></label>
              </div>
              <label>Document <span className="hint">PDF, JPG, PNG or WebP · max 5 MB</span>
                <input type="file" required accept="application/pdf,image/jpeg,image/png,image/webp" onChange={(e) => setV({ ...v, file: e.target.files[0] })} /></label>
              <Notice error={act.error} />
              <button disabled={act.busy}>{act.busy ? 'Uploading…' : 'Submit for verification'}</button>
            </form>
          ) : (
            <div className="row gap"><Icon n={k.data.kycStatus === 'PENDING' ? 'clock' : 'checkc'} />
              <span className="muted">{k.data.kycStatus === 'PENDING' ? 'Your documents are under review by the bank.' : 'Your identity is verified.'}</span></div>)}
        </>)}
      </Panel>
      <Panel className="flush" title="KYC history" icon="clock">
        <Notice error={open.error} />
        <Table rows={k.data?.history} empty="No KYC submitted yet" emptyIcon="id" columns={[
          { key: 'submittedAt', label: 'Submitted', render: (r) => dateTime(r.submittedAt) },
          { key: 'documentType', label: 'Document', render: (r) => <>{words(r.documentType)}<span className="sub">{r.documentNumber}</span></> },
          { key: 'status', label: 'Status', render: (r) => <Badge v={r.status} /> },
          { key: 'adminComment', label: 'Comment' },
          { key: 'view', label: '', render: (r) => <button type="button" className="ghost sm" onClick={() => open.run(() => kyc.document(r.id), { quiet: true })}>View</button> },
        ]} />
      </Panel>
    </div>);
}
