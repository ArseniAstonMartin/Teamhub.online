import { useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  LabelList,
} from 'recharts';
import { TrendingUp, Lock } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import {
  useJobCounts,
  useAvgTimes,
  usePerTechnician,
} from '@/entities/report/api/reportsApi';
import type { ReportPeriod } from '@/entities/report/model/types';

// ── Date helpers ──────────────────────────────────────────────────────────────

function toISODate(d: Date): string {
  return d.toISOString().split('T')[0];
}

function today(): string {
  return toISODate(new Date());
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toISODate(d);
}

function defaultRange(period: ReportPeriod): { from: string; to: string } {
  switch (period) {
    case 'day':
      return { from: today(), to: today() };
    case 'week':
      return { from: daysAgo(6), to: today() };
    case 'year':
      return { from: daysAgo(364), to: today() };
    case 'month':
    default:
      return { from: daysAgo(29), to: today() };
  }
}

// ── Seconds → human-readable ──────────────────────────────────────────────────

function formatSeconds(s: number | null): string {
  if (s === null) return 'No data';
  if (s < 60) return 'under 1 min';
  const totalMin = Math.floor(s / 60);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h === 0) return `${m}m`;
  return `${h}h ${m}m`;
}

// ── X-axis date formatter ─────────────────────────────────────────────────────

function formatXDate(dateStr: string, period: ReportPeriod): string {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T00:00:00');
  switch (period) {
    case 'day':
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    case 'week':
      return `Wk ${d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`;
    case 'month':
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    case 'year':
      return d.toLocaleDateString('en-US', { month: 'short' });
  }
}

// ── Period tabs ───────────────────────────────────────────────────────────────

const PERIODS: { id: ReportPeriod; label: string }[] = [
  { id: 'day', label: 'Day' },
  { id: 'week', label: 'Week' },
  { id: 'month', label: 'Month' },
  { id: 'year', label: 'Year' },
];

// ── Avg time card ─────────────────────────────────────────────────────────────

interface AvgCardProps {
  label: string;
  value: number | null | undefined;
  loading: boolean;
}

function AvgCard({ label, value, loading }: AvgCardProps) {
  return (
    <div className="flex-1 min-w-[160px] bg-brand-surface border border-border rounded-lg p-5">
      <p className="text-xs font-medium text-brand-muted uppercase tracking-wide mb-1">
        {label}
      </p>
      {loading ? (
        <div className="h-7 w-24 bg-border rounded animate-pulse" />
      ) : (
        <p
          className="text-2xl font-bold text-brand-navy"
          data-testid={`avg-card-${label.replace(/\s+/g, '-').toLowerCase()}`}
        >
          {value === undefined ? 'No data' : formatSeconds(value)}
        </p>
      )}
    </div>
  );
}

// ── Upgrade card (plan gate) ──────────────────────────────────────────────────

function UpgradeCard() {
  return (
    <div
      className="flex flex-col items-center justify-center gap-4 bg-brand-surface border border-border rounded-lg p-10 text-center"
      data-testid="upgrade-card"
    >
      <Lock className="h-8 w-8 text-brand-muted" aria-hidden="true" />
      <div>
        <p className="text-base font-semibold text-brand-navy">
          Advanced analytics is a Team feature
        </p>
        <p className="text-sm text-brand-muted mt-1">
          Upgrade to Team or Enterprise to unlock per-technician completion rates.
        </p>
      </div>
      <Link
        to="/settings/billing"
        className="inline-flex items-center gap-2 rounded-md bg-brand-blue px-4 py-2 text-sm font-medium text-white hover:bg-brand-blue/90 transition-colors"
        data-testid="upgrade-link"
      >
        <TrendingUp className="h-4 w-4" aria-hidden="true" />
        Upgrade to Team+
      </Link>
    </div>
  );
}

// ── Chart legend icon shapes (AC4 — no color-only signal) ────────────────────

const LINE_SERIES = [
  { key: 'created', color: '#3B82F6', label: 'Created', shape: '●' },
  { key: 'completed', color: '#22C55E', label: 'Completed', shape: '■' },
  { key: 'cancelled', color: '#EF4444', label: 'Cancelled', shape: '▲' },
] as const;

// ── Custom dot shapes for recharts lines ──────────────────────────────────────

function CircleDot(props: { cx?: number; cy?: number; fill?: string }) {
  const { cx = 0, cy = 0, fill } = props;
  return <circle cx={cx} cy={cy} r={4} fill={fill} stroke="white" strokeWidth={1.5} />;
}

function SquareDot(props: { cx?: number; cy?: number; fill?: string }) {
  const { cx = 0, cy = 0, fill } = props;
  return <rect x={cx - 4} y={cy - 4} width={8} height={8} fill={fill} stroke="white" strokeWidth={1.5} />;
}

function TriangleDot(props: { cx?: number; cy?: number; fill?: string }) {
  const { cx = 0, cy = 0, fill } = props;
  const pts = `${cx},${cy - 5} ${cx - 4.5},${cy + 3} ${cx + 4.5},${cy + 3}`;
  return <polygon points={pts} fill={fill} stroke="white" strokeWidth={1.5} />;
}

// ── Custom legend renderer ────────────────────────────────────────────────────

function JobsLegend() {
  return (
    <ul className="flex flex-wrap gap-x-6 gap-y-1 justify-center text-xs mt-2" role="list">
      {LINE_SERIES.map((s) => (
        <li key={s.key} className="flex items-center gap-1.5">
          <span style={{ color: s.color }} aria-hidden="true">{s.shape}</span>
          <span className="text-brand-muted">{s.label}</span>
        </li>
      ))}
    </ul>
  );
}

// ── Per-cent label above bars ─────────────────────────────────────────────────

function BarLabel(props: { x?: number; y?: number; width?: number; value?: number }) {
  const { x = 0, y = 0, width = 0, value = 0 } = props;
  return (
    <text
      x={x + width / 2}
      y={y - 4}
      textAnchor="middle"
      className="fill-brand-navy"
      style={{ fontSize: 11, fontWeight: 600 }}
    >
      {`${Math.round(value)}%`}
    </text>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const periodParam = (searchParams.get('period') as ReportPeriod | null) ?? 'month';
  const fromParam = searchParams.get('from');
  const toParam = searchParams.get('to');

  const period: ReportPeriod = PERIODS.some((p) => p.id === periodParam)
    ? periodParam
    : 'month';

  // Derive date range: prefer URL params, fall back to period defaults
  const { from, to } = useMemo(() => {
    if (fromParam && toParam) return { from: fromParam, to: toParam };
    return defaultRange(period);
  }, [period, fromParam, toParam]);

  // Sync defaults into URL on first load
  useEffect(() => {
    if (!searchParams.get('from') || !searchParams.get('to')) {
      const defaults = defaultRange(period);
      setSearchParams(
        { period, from: defaults.from, to: defaults.to },
        { replace: true },
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function selectPeriod(p: ReportPeriod) {
    const range = defaultRange(p);
    setSearchParams({ period: p, from: range.from, to: range.to });
  }

  // ── API queries ─────────────────────────────────────────────────────────────
  const jobsQuery = useJobCounts({ period, from, to });
  const avgQuery = useAvgTimes({ from, to });
  const techQuery = usePerTechnician({ from, to });

  // ── Derived chart data ──────────────────────────────────────────────────────
  const jobChartData = useMemo(
    () =>
      (jobsQuery.data?.buckets ?? []).map((b) => ({
        ...b,
        label: formatXDate(b.date, period),
      })),
    [jobsQuery.data, period],
  );

  const techChartData = useMemo(
    () =>
      (techQuery.data ?? []).map((t, i) => ({
        name: `Tech ${i + 1}`,
        technicianId: t.technicianId,
        rate: Math.round(t.completionRate * 100),
      })),
    [techQuery.data],
  );

  const avgTimes = avgQuery.data;

  return (
    <div className="space-y-6" data-testid="reports-page">
      {/* ── Page header ──────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h1 className="text-2xl font-semibold text-brand-navy">Reports</h1>

        {/* Period tabs */}
        <div className="flex items-center gap-1 bg-brand-surface border border-border rounded-lg p-1" role="tablist" aria-label="Date range">
          {PERIODS.map((p) => (
            <button
              key={p.id}
              role="tab"
              aria-selected={period === p.id}
              data-testid={`period-tab-${p.id}`}
              onClick={() => selectPeriod(p.id)}
              className={cn(
                'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                period === p.id
                  ? 'bg-brand-blue text-white shadow-sm'
                  : 'text-brand-muted hover:text-brand-navy',
              )}
            >
              {p.label}
            </button>
          ))}

          {/* Custom range — Team+ only (TO-DO: enable when plan info is available) */}
          <button
            role="tab"
            aria-selected={false}
            aria-disabled="true"
            data-testid="period-tab-custom"
            title="Custom date range is a Team+ feature"
            className="px-3 py-1.5 rounded-md text-sm font-medium text-brand-muted/50 cursor-not-allowed flex items-center gap-1"
            disabled
          >
            <Lock className="h-3 w-3" aria-hidden="true" />
            Custom
          </button>
        </div>
      </div>

      {/* ── Avg time cards ────────────────────────────────────────────────── */}
      <div className="flex flex-wrap gap-4">
        <AvgCard
          label="Avg Time to Claim"
          value={avgTimes?.avgTimeToClaimSeconds}
          loading={avgQuery.isLoading}
        />
        <AvgCard
          label="Avg Time to Completion"
          value={avgTimes?.avgTimeToCompletionSeconds}
          loading={avgQuery.isLoading}
        />
      </div>

      {/* ── Jobs over time chart ──────────────────────────────────────────── */}
      <section
        className="bg-brand-surface border border-border rounded-lg p-5"
        aria-label="Jobs over time"
      >
        <h2 className="text-sm font-semibold text-brand-navy mb-4">Jobs Over Time</h2>

        {jobsQuery.isLoading && (
          <div className="h-64 flex items-center justify-center text-brand-muted text-sm">
            Loading…
          </div>
        )}
        {!jobsQuery.isLoading && jobChartData.length === 0 && (
          <div className="h-64 flex items-center justify-center text-brand-muted text-sm">
            No data for selected range.
          </div>
        )}
        {!jobsQuery.isLoading && jobChartData.length > 0 && (
          <>
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={jobChartData} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: '#6B7280' }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: '#6B7280' }}
                  tickLine={false}
                  axisLine={false}
                  allowDecimals={false}
                />
                <Tooltip
                  contentStyle={{ fontSize: 12, borderRadius: 6 }}
                />
                <Legend content={<JobsLegend />} />
                <Line
                  type="monotone"
                  dataKey="created"
                  name="Created"
                  stroke="#3B82F6"
                  strokeWidth={2}
                  dot={<CircleDot fill="#3B82F6" />}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="completed"
                  name="Completed"
                  stroke="#22C55E"
                  strokeWidth={2}
                  dot={<SquareDot fill="#22C55E" />}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="cancelled"
                  name="Cancelled"
                  stroke="#EF4444"
                  strokeWidth={2}
                  dot={<TriangleDot fill="#EF4444" />}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </>
        )}
      </section>

      {/* ── Per-technician chart or upgrade card ─────────────────────────── */}
      <section
        className="bg-brand-surface border border-border rounded-lg p-5"
        aria-label="Per-technician completion rate"
      >
        <h2 className="text-sm font-semibold text-brand-navy mb-4">
          Per-Technician Completion Rate
        </h2>

        {techQuery.isLoading && (
          <div className="h-64 flex items-center justify-center text-brand-muted text-sm">
            Loading…
          </div>
        )}

        {/* null means 402 → plan gate */}
        {!techQuery.isLoading && techQuery.data === null && <UpgradeCard />}

        {!techQuery.isLoading && Array.isArray(techQuery.data) && techQuery.data.length === 0 && (
          <div className="h-64 flex items-center justify-center text-brand-muted text-sm">
            No data for selected range.
          </div>
        )}

        {!techQuery.isLoading && Array.isArray(techQuery.data) && techQuery.data.length > 0 && (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart
              data={techChartData}
              margin={{ top: 24, right: 16, left: 0, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" vertical={false} />
              <XAxis
                dataKey="name"
                tick={{ fontSize: 11, fill: '#6B7280' }}
                tickLine={false}
              />
              <YAxis
                domain={[0, 100]}
                unit="%"
                tick={{ fontSize: 11, fill: '#6B7280' }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip
                  contentStyle={{ fontSize: 12, borderRadius: 6 }}
                />
              <Legend
                formatter={() => 'Completion Rate'}
                wrapperStyle={{ fontSize: 12 }}
              />
              <Bar dataKey="rate" name="Completion Rate" fill="#3B82F6" radius={[4, 4, 0, 0]}>
                <LabelList content={<BarLabel />} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </section>
    </div>
  );
}
