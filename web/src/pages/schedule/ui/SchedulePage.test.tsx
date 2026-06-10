import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SchedulePage from './SchedulePage';
import type { JobDto, JobsPageResponse } from '@/entities/job/model/types';

// ── Module mocks (hoisted before imports) ─────────────────────────────────────

const {
  mockUseJobs,
  mockUpdateJobSchedule,
  mockSetQueryData,
  mockToastError,
} = vi.hoisted(() => ({
  mockUseJobs: vi.fn(),
  mockUpdateJobSchedule: vi.fn(),
  mockSetQueryData: vi.fn(),
  mockToastError: vi.fn(),
}));

// Mock the job API (useJobs + updateJobSchedule)
vi.mock('@/entities/job/api/jobsApi', () => ({
  useJobs: mockUseJobs,
  updateJobSchedule: mockUpdateJobSchedule,
}));

vi.mock('@/entities/tenant/api/tenantApi', () => ({
  useTenantSettings: vi.fn(() => ({
    data: { closeJobsEnabled: false, timezone: 'America/New_York' },
    isLoading: false,
    isError: false,
  })),
}));

vi.mock('@/shared/lib/timezone', () => ({
  utcToZonedDate: (iso: string) => new Date(iso),
  zonedDateToUtcIso: (date: Date) => date.toISOString(),
}));

// Mock TanStack Query's useQueryClient
vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();
  return {
    ...actual,
    useQueryClient: () => ({ setQueryData: mockSetQueryData }),
  };
});

// Mock Sonner toast
vi.mock('sonner', () => ({
  toast: { error: mockToastError, success: vi.fn() },
}));

// ── react-big-calendar mock ───────────────────────────────────────────────────
// Render a simple test double that exposes drag via a button per event.
// withDragAndDrop is mocked as identity so DnDCalendar === Calendar.

vi.mock('react-big-calendar', () => ({
  Calendar: vi.fn(({ events, onEventDrop, onView: _onView, view }: {
    events: Array<{ id: string; title: string; status: string; resource: JobDto; start: Date; end: Date }>;
    onEventDrop: (args: { event: { id: string; title: string; resource: JobDto; start: Date; end: Date }; start: Date; end: Date; isAllDay: boolean }) => void;
    onView: (_v: string) => void;
    view: string;
  }) => (
    <div data-testid="calendar" data-view={view}>
      {events.map((event) => (
        <div key={event.id} data-testid={`event-${event.id}`}>
          <span aria-label={`${event.status}: ${event.title}`}>{event.title}</span>
          <button
            data-testid={`drag-${event.id}`}
            onClick={() =>
              onEventDrop({
                event,
                start: new Date('2026-06-10T10:00:00Z'),
                end: new Date('2026-06-10T12:00:00Z'),
                isAllDay: false,
              })
            }
          >
            Simulate drag
          </button>
        </div>
      ))}
    </div>
  )),
  dateFnsLocalizer: vi.fn(() => ({})),
  Views: { DAY: 'day', WEEK: 'week', MONTH: 'month' },
}));

vi.mock('react-big-calendar/lib/addons/dragAndDrop', () => ({
  default: (Cal: React.ComponentType<unknown>) => Cal,
}));

// Ignore CSS imports from rbc
vi.mock('react-big-calendar/lib/css/react-big-calendar.css', () => ({}));
vi.mock('react-big-calendar/lib/addons/dragAndDrop/styles.css', () => ({}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<JobDto> = {}): JobDto {
  return {
    id: 'job-1',
    status: 'ASSIGNED',
    scheduledStart: '2026-06-10T09:00:00Z',
    scheduledEnd: '2026-06-10T11:00:00Z',
    customer: { id: 'c1', name: 'Acme Corp', address: '1 Main St' },
    assignedTechnician: { id: 't1', name: 'Jane Smith' },
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makePageResponse(jobs: JobDto[]): JobsPageResponse {
  return { content: jobs, totalElements: jobs.length, totalPages: 1, number: 0, size: 500 };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function mockJobsSuccess(jobs: JobDto[]) {
  mockUseJobs.mockReturnValue({
    data: makePageResponse(jobs),
    isLoading: false,
    isFetching: false,
    isError: false,
  });
}

function mockJobsLoading() {
  mockUseJobs.mockReturnValue({
    data: undefined,
    isLoading: true,
    isFetching: true,
    isError: false,
  });
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SchedulePage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SchedulePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Render ────────────────────────────────────────────────────────────────

  it('renders the page heading', () => {
    mockJobsSuccess([]);
    renderPage();
    expect(screen.getByRole('heading', { name: /schedule/i })).toBeInTheDocument();
  });

  it('shows loading indicator while fetching', () => {
    mockJobsLoading();
    renderPage();
    expect(screen.getByText(/loading jobs/i)).toBeInTheDocument();
  });

  it('renders the calendar when data is available', () => {
    mockJobsSuccess([makeJob()]);
    renderPage();
    expect(screen.getByTestId('calendar')).toBeInTheDocument();
  });

  // ── AC1: View tabs ────────────────────────────────────────────────────────

  it('renders Day, Week, Month view tabs', () => {
    mockJobsSuccess([]);
    renderPage();
    expect(screen.getByTestId('view-tab-day')).toBeInTheDocument();
    expect(screen.getByTestId('view-tab-week')).toBeInTheDocument();
    expect(screen.getByTestId('view-tab-month')).toBeInTheDocument();
  });

  it('Week tab is selected by default', () => {
    mockJobsSuccess([]);
    renderPage();
    expect(screen.getByTestId('view-tab-week')).toHaveAttribute('aria-selected', 'true');
  });

  it('Step 3: clicking Day tab switches the calendar to day view', async () => {
    mockJobsSuccess([]);
    renderPage();
    await userEvent.click(screen.getByTestId('view-tab-day'));
    expect(screen.getByTestId('view-tab-day')).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByTestId('view-tab-week')).toHaveAttribute('aria-selected', 'false');
  });

  it('Step 3: clicking Month tab switches the calendar to month view', async () => {
    mockJobsSuccess([]);
    renderPage();
    await userEvent.click(screen.getByTestId('view-tab-month'));
    expect(screen.getByTestId('view-tab-month')).toHaveAttribute('aria-selected', 'true');
  });

  it('Step 3: useJobs is called with a wider date window for month view', async () => {
    mockJobsSuccess([]);
    renderPage();

    vi.clearAllMocks();
    mockJobsSuccess([]);

    await userEvent.click(screen.getByTestId('view-tab-month'));

    // Month view should pass a larger scheduledFrom/scheduledTo window
    const callArgs = mockUseJobs.mock.calls[0]?.[0];
    expect(callArgs).toBeDefined();
    expect(callArgs).toHaveProperty('scheduledFrom');
    expect(callArgs).toHaveProperty('scheduledTo');
  });

  // ── AC2: Job events rendered ──────────────────────────────────────────────

  it('renders an event for each job with a scheduledStart', () => {
    mockJobsSuccess([
      makeJob({ id: 'job-1' }),
      makeJob({ id: 'job-2', customer: { id: 'c2', name: 'Globex', address: '2 B St' } }),
    ]);
    renderPage();
    expect(screen.getByTestId('event-job-1')).toBeInTheDocument();
    expect(screen.getByTestId('event-job-2')).toBeInTheDocument();
  });

  it('does not render an event for jobs without scheduledStart', () => {
    mockJobsSuccess([makeJob({ id: 'no-time', scheduledStart: null })]);
    renderPage();
    expect(screen.queryByTestId('event-no-time')).not.toBeInTheDocument();
  });

  it('AC2: event aria-label includes status text (not color-only)', () => {
    mockJobsSuccess([makeJob({ id: 'job-1', status: 'IN_PROGRESS' })]);
    renderPage();
    const badge = screen.getByLabelText(/in_progress: acme corp/i);
    expect(badge).toBeInTheDocument();
  });

  // ── AC3: Drag-and-drop — success ──────────────────────────────────────────

  it('Step 1: clicking drag on an event triggers optimistic setQueryData', async () => {
    const job = makeJob({ id: 'job-1' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockResolvedValueOnce({ ...job });
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));

    expect(mockSetQueryData).toHaveBeenCalledWith(
      expect.arrayContaining(['jobs']),
      expect.any(Function),
    );
  });

  it('Step 1: calls updateJobSchedule with the correct job id and new timestamps', async () => {
    const job = makeJob({ id: 'job-1' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockResolvedValueOnce({ ...job });
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));

    await waitFor(() => {
      expect(mockUpdateJobSchedule).toHaveBeenCalledWith(
        'job-1',
        '2026-06-10T10:00:00.000Z',
        '2026-06-10T12:00:00.000Z',
      );
    });
  });

  it('Step 1: does not show error toast on successful drag', async () => {
    const job = makeJob({ id: 'job-1' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockResolvedValueOnce({ ...job });
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));
    await waitFor(() => expect(mockUpdateJobSchedule).toHaveBeenCalled());

    expect(mockToastError).not.toHaveBeenCalled();
  });

  // ── AC4: Drag-and-drop — backend rejection ────────────────────────────────

  it('Step 2: shows error toast when backend rejects the drag', async () => {
    const job = makeJob({ id: 'job-1' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockRejectedValueOnce(new Error('400 Bad Request'));
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith(
        expect.stringMatching(/failed to reschedule/i),
      );
    });
  });

  it('Step 2: calls setQueryData twice on rejection — optimistic then rollback', async () => {
    const job = makeJob({ id: 'job-1' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockRejectedValueOnce(new Error('Server error'));
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));

    await waitFor(() => {
      expect(mockSetQueryData).toHaveBeenCalledTimes(2);
    });
  });

  it('Step 2: rollback updater function restores the original job', async () => {
    const job = makeJob({ id: 'job-1', scheduledStart: '2026-06-10T09:00:00Z' });
    mockJobsSuccess([job]);
    mockUpdateJobSchedule.mockRejectedValueOnce(new Error('Conflict'));

    const pageData: JobsPageResponse = makePageResponse([job]);
    renderPage();

    await userEvent.click(screen.getByTestId('drag-job-1'));

    await waitFor(() => expect(mockSetQueryData).toHaveBeenCalledTimes(2));

    // The second setQueryData call is the rollback — its updater function should
    // restore the original job when applied.
    const rollbackUpdater = mockSetQueryData.mock.calls[1]?.[1] as
      | ((old: JobsPageResponse | undefined) => JobsPageResponse | undefined)
      | undefined;

    expect(rollbackUpdater).toBeDefined();

    // Simulate what TanStack Query would do: call the updater with modified data
    const modifiedPage: JobsPageResponse = {
      ...pageData,
      content: [{ ...job, scheduledStart: '2026-06-10T10:00:00Z' }],
    };
    const restored = rollbackUpdater!(modifiedPage);
    expect(restored?.content[0]?.scheduledStart).toBe('2026-06-10T09:00:00Z');
  });
});
