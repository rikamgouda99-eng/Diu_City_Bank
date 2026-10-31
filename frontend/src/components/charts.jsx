// Small dependency-free SVG charts. They only plot numbers passed in from API responses.
import { moneyShort } from './ui';

const DEFAULT_W = 640;

/** Area chart. points: [{ label, value }] */
export function AreaChart({ points, height = 220, color = '#2457D6', format = moneyShort, width: W = DEFAULT_W }) {
  if (!points?.length) return null;
  const pad = { l: 56, r: 12, t: 12, b: 26 };
  const max = Math.max(...points.map((p) => p.value)) * 1.08 || 1;
  const min = Math.min(...points.map((p) => p.value)) * 0.96;
  const x = (i) => pad.l + (i * (W - pad.l - pad.r)) / Math.max(points.length - 1, 1);
  const y = (v) => pad.t + (1 - (v - min) / (max - min || 1)) * (height - pad.t - pad.b);
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');
  const area = `${line} L${x(points.length - 1)},${height - pad.b} L${x(0)},${height - pad.b} Z`;
  const ticks = [0, 0.5, 1].map((f) => min + f * (max - min));
  const every = Math.ceil(points.length / 7);
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${height}`} role="img" aria-label="Growth chart">
      <defs><linearGradient id="ag" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={color} stopOpacity=".28" /><stop offset="1" stopColor={color} stopOpacity="0" /></linearGradient></defs>
      {ticks.map((t) => <g key={t}><line className="grid-line" x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} /><text x={pad.l - 8} y={y(t) + 4} textAnchor="end">{format(t)}</text></g>)}
      <path d={area} fill="url(#ag)" />
      <path d={line} fill="none" stroke={color} strokeWidth="2.5" strokeLinejoin="round" />
      {points.map((p, i) => (i % every === 0 || i === points.length - 1) && (i === 0 || p.label !== points[i - every]?.label || i === points.length - 1 && p.label !== points[0].label)
        && <text key={i} x={x(i)} y={height - 6} textAnchor="middle">{p.label}</text>)}
      <circle cx={x(points.length - 1)} cy={y(points.at(-1).value)} r="4.5" fill="#fff" stroke={color} strokeWidth="2.5" />
    </svg>);
}

/** Stacked bars. data: [{ label, a, b }] - a at the bottom, b on top. */
export function StackedBars({ data, height = 260, colors = ['#2457D6', '#E9A23B'], width: W = 1000 }) {
  if (!data?.length) return null;
  const pad = { l: 52, r: 8, t: 10, b: 24 };
  const max = Math.max(...data.map((d) => d.a + d.b)) * 1.05 || 1;
  const bw = (W - pad.l - pad.r) / data.length;
  const h = (v) => (v / max) * (height - pad.t - pad.b);
  const every = Math.ceil(data.length / 12);
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${height}`} role="img" aria-label="Principal and interest per EMI">
      {[0, 0.5, 1].map((f) => { const yy = height - pad.b - f * (height - pad.t - pad.b); return <g key={f}><line className="grid-line" x1={pad.l} x2={W - pad.r} y1={yy} y2={yy} /><text x={pad.l - 8} y={yy + 4} textAnchor="end">{moneyShort(f * max)}</text></g>; })}
      {data.map((d, i) => {
        const x = pad.l + i * bw + bw * 0.15; const w = bw * 0.7; const base = height - pad.b;
        return (
          <g key={i}>
            <title>{`${d.label}: principal ${moneyShort(d.a)}, interest ${moneyShort(d.b)}`}</title>
            <rect className="bar" x={x} y={base - h(d.a)} width={w} height={h(d.a)} fill={colors[0]} rx="2" style={{ animationDelay: `${i * 8}ms` }} />
            <rect className="bar" x={x} y={base - h(d.a) - h(d.b)} width={w} height={h(d.b)} fill={colors[1]} rx="2" style={{ animationDelay: `${i * 8}ms` }} />
            {i % every === 0 && <text x={x + w / 2} y={height - 6} textAnchor="middle">{d.label}</text>}
          </g>);
      })}
    </svg>);
}

/** Donut. segments: [{ label, value, color }] */
export function Donut({ segments, size = 160, center }) {
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  const r = 60; const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <svg width={size} height={size} viewBox="0 0 160 160" role="img" aria-label={segments.map((s) => `${s.label} ${Math.round((s.value / total) * 100)}%`).join(', ')}>
      <circle cx="80" cy="80" r={r} fill="none" stroke="#EEF1F6" strokeWidth="20" />
      {segments.map((s) => {
        const len = (s.value / total) * c;
        const el = <circle key={s.label} cx="80" cy="80" r={r} fill="none" stroke={s.color} strokeWidth="20" strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset} transform="rotate(-90 80 80)" />;
        offset += len;
        return el;
      })}
      {center && <text className="donut-center" x="80" y="84" textAnchor="middle">{center}</text>}
    </svg>);
}

export const Legend = ({ items }) => (
  <div className="legend">{items.map(([label, color, value]) => <span key={label}><i style={{ background: color }} />{label}{value !== undefined && <b style={{ color: 'var(--ink)', marginLeft: 6 }}>{value}</b>}</span>)}</div>);

/** Horizontal bars. rows: [{ label, value }] */
export function BarList({ rows, format = (v) => v }) {
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <div className="bar-list">
      {rows.map((r) => (
        <div key={r.label} className="bl-row">
          <span className="muted" style={{ fontSize: '.84rem' }}>{r.label}</span>
          <div className="progress"><i style={{ width: `${(r.value / max) * 100}%` }} /></div>
          <span>{format(r.value)}</span>
        </div>))}
    </div>);
}
