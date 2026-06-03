import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import ReportsPage from './ReportsPage';

// ── Mock recharts (JSDOM has no SVG layout engine) ────────────────────────────
vi.mock('recharts', async () => {
  const React = await import('react');
  const Passthrough = ({ children }: { children?: React.ReactNode }) =>
    React.createElement('div', { 'data-testid': 'chart' }, children);
  const DataChild = ({ data, dataKey }: { data?: unknown[]; dataKey?: string }) => {
    if (!Array.isArray(data)) return null;
    return React.createElement(
      'ul',
      { 'data-testid': `series-${dataKey ?? 'unknown'}` },
      data.map((d: unknown, i: number) =>
        React.createElement('li', { key: i }, JSON.stringify(d)),
      ),
    );
  };
  return {
    LineChart: ({ children, data }: { children?: React.ReactNode; data?: unknown[] }) =>
      React.createElement(
        'div',
        { 'data-testid': 'line-chart' },
        React.createElement(DataChild, { data, dataKey: 'buckets' }),
        children,
      ),
    BarChart: ({ children, data }: { children?: React.ReactNode; data?: unknown[] }) =>
      React.createElement(
        'div',
        { 'data-testid': 'bar-chart' },
        React.createElement(DataChild, { data, dataKey: 'tech' }),
        children,
      ),
    Line: () => null,
    Bar: () => null,
    XAxis: () => null,
    YAxis: () => null,
    CartesianGrid: () => null,
    Tooltip: () => null,
    Legend: () => null,
    ResponsiveContainer: Passthrough,
    LabelList: () => null,
  };
});

// ── Mock react-router-dom (preserve MemoryRouter) ─────────────────────────────
const mockSetSearchParams = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useSearchParams: vi.fn(() => [new URLSearchParams('period=month&from=2026-05-04&to=2026-06-03'), mockSetSearchParams]),
  };
});

import { useSearchParams } from 'react-router-dom';

// ── Mock reports API ──────────────────────────────────────────────────────────
const { mockUseJobCounts, mockUseAvgTimes, mockUsePerTechnician } = vi.hoisted(() => ({
  mockUseJobCounts: vi.fn(),
  mockUseAvgTimes: vi.fn(),
  mockUsePerTechnician: vi.fn(),
}));

vi.mock('@/entities/report/api/reportsApi', () => ({
  useJobCounts: mockUseJobCounts,
  useAvgTimes: mockUseAvgTimes,
  usePerTechnician: mockUsePerTechnician,
}));

// ── Default stubs ─────────────────────────────────────────────────────────────

const SAMPLE_BUCKETS = [
  { date: '2026-06-01', created: 5, completed: 4, cancelled: 1 },
  { date: '2026-06-02', created: 3, completed: 3, cancelled: 0 },
  { date: '2026-06-03', created: 7, completed: 6, cancelled: 1 },
];

const SAMPLE_AVG_TIMES = {
  avgTimeToClaimSeconds: 15780,    // 4h 23m
  avgTimeToCompletionSeconds: 43500, // 12h 5m
};

const SAMPLE_TECH = [
  { technicianId: 'uuid-1', totalAssigned: 10, totalCompleted: 9, completionRate: 0.9 },
  { technicianId: 'uuid-2', totalAssigned: 8, totalCompleted: 6, completionRate: 0.75 },
];

function makeQuery<T>(overrides: Partial<{ isLoading: boolean; data: T }>) {
  return { isLoading: false, data: undefined, ...overrides };
}

function renderPage(route = '/reports') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ReportsPage />
    </MemoryRouter>,
  );
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSearchParams).mockReturnValue([
    new URLSearchParams('period=month&from=2026-05-04&to=2026-06-03'),
    mockSetSearchParams,
  ]);
  mockUseJobCounts.mockReturnValue(makeQuery({ data: { buckets: SAMPLE_BUCKETS } }));
  mockUseAvgTimes.mockReturnValue(makeQuery({ data: SAMPLE_AVG_TIMES }));
  mockUsePerTechnician.mockReturnValue(makeQuery({ data: SAMPLE_TECH }));
});

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ReportsPage', () => {
  // 1. Renders avg-time number cards from API data
  it('renders avg-time cards with formatted values from API', () => {
    renderPage();

    // Claim: 15780s = 263 min = 4h 23m
    expect(screen.getByText('4h 23m')).toBeInTheDocument();
    // Completion: 43500s = 725 min = 12h 5m
    expect(screen.getByText('12h 5m')).toBeInTheDocument();
  });

  // 2. "No data" shown when avgTimeToClaimSeconds is null
  it('shows "No data" for avg claim card when value is null', () => {
    mockUseAvgTimes.mockReturnValue(
      makeQuery({ data: { avgTimeToClaimSeconds: null, avgTimeToCompletionSeconds: 43500 } }),
    );

    renderPage();

    const noDataElements = screen.getAllByText('No data');
    expect(noDataElements.length).toBeGreaterThanOrEqual(1);
  });

  // 3. Line chart renders with correct number of buckets
  it('renders line chart containing the expected number of data buckets', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('line-chart')).toBeInTheDocument();
      // Our mock renders a list item per bucket
      const items = screen.getAllByRole('listitem');
      // 3 buckets from SAMPLE_BUCKETS should appear in the DataChild ul for buckets
      const bucketItems = items.filter((el) =>
        el.closest('[data-testid="series-buckets"]'),
      );
      expect(bucketItems).toHaveLength(SAMPLE_BUCKETS.length);
    });
  });

  // 4. "Month" tab selected by default; clicking "Week" updates query params
  it('selects Month tab by default and clicking Week calls setSearchParams', async () => {
    const user = userEvent.setup();
    renderPage();

    const monthTab = screen.getByTestId('period-tab-month');
    expect(monthTab).toHaveAttribute('aria-selected', 'true');

    const weekTab = screen.getByTestId('period-tab-week');
    await user.click(weekTab);

    expect(mockSetSearchParams).toHaveBeenCalledWith(
      expect.objectContaining({ period: 'week' }),
    );
  });

  // 5. Per-technician bar chart renders on successful API response
  it('renders the bar chart when per-technician data is available', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('upgrade-card')).not.toBeInTheDocument();
  });

  // 6. 402 from per-technician endpoint shows upgrade card (AC3)
  it('shows upgrade card when per-technician returns null (402 gate)', async () => {
    mockUsePerTechnician.mockReturnValue(makeQuery({ data: null }));

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('upgrade-card')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('bar-chart')).not.toBeInTheDocument();
  });

  // 7. Upgrade card has link to /settings/billing
  it('upgrade card contains a link to /settings/billing', async () => {
    mockUsePerTechnician.mockReturnValue(makeQuery({ data: null }));

    renderPage();

    const link = await screen.findByTestId('upgrade-link');
    expect(link).toHaveAttribute('href', '/settings/billing');
  });

  // 8. Custom date range tab is rendered (disabled; Team+ gate)
  it('renders Custom tab in disabled state', () => {
    renderPage();

    const customTab = screen.getByTestId('period-tab-custom');
    expect(customTab).toBeDisabled();
    expect(customTab).toHaveAttribute('aria-disabled', 'true');
  });
});
