// Single place for all HTTP: base URL, JWT header, Idempotency-Key, error format.
// Every backend response is { isSuccess, data, message, timestamp }.
const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const newKey = () =>
  (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`);

async function request(method, path, { body, key, raw } = {}) {
  const token = localStorage.getItem('token');
  const isForm = body instanceof FormData;
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(!isForm && body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(key && { 'Idempotency-Key': key }),
    },
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  });

  // expired or invalid session: back to the login page
  if (res.status === 401 && token) {
    localStorage.clear();
    window.location.assign('/login');
    throw new Error('Your session has expired, please log in again.');
  }
  if (raw) {
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      const e = new Error(err.message || res.statusText);
      e.status = res.status;
      throw e;
    }
    return res;
  }
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.isSuccess === false) {
    const e = new Error(json.message || `Request failed (${res.status})`);
    e.status = res.status;
    throw e;
  }
  return json; // { data, message }
}

export const http = {
  get: (path) => request('GET', path),
  post: (path, body, key) => request('POST', path, { body: body ?? {}, key }),
  put: (path, body) => request('PUT', path, { body: body ?? {} }),
  del: (path) => request('DELETE', path),
  upload: (path, form) => request('POST', path, { body: form }),
  blob: async (path) => (await request('GET', path, { raw: true })).blob(),
};

/** Fetches a protected file (PDF, image) with the JWT and saves it. */
export async function download(path, fileName) {
  const blob = await http.blob(path);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

/** Opens a protected file in a new tab (KYC documents). */
export async function openFile(path) {
  const blob = await http.blob(path);
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

/** Query string from an object, skipping empty values. */
export const qs = (params) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.append(k, v); });
  const s = p.toString();
  return s ? `?${s}` : '';
};
