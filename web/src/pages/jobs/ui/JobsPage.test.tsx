import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import JobsPage from './JobsPage';
import type { JobDto, JobsPageResponse } from '@/entities/job/model/types';

// ── Mocks ─────────────────────────────────────────────────────────────────────

const { mockUseJobs } = vi.hoisted(() => ({ mockUseJobs: vi.fn() }));

vi.mock('@/entities/job/api/jobsApi', () => ({
  useJobs: mockUseJobs,
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<JobDto> = {}): JobDto {
  return {
    id: 'job-1',
    status: 'SUBMITTED',
    scheduledStart: '2026-03-01T09:00:00Z',
    scheduledEnd: '2026-03-01T11:00:00Z',
    customerId: 'cust-1',
    customerName: 'Acme Corp',
    assigneeName: 'Jane Smith',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-15T10:00:00Z',
    ...overrides,
  };
}

function makePageResponse(
  jobs: JobDto[],
  extras: Partial<JobsPageResponse> = {},
): JobsPageResponse {
  return {
    content: jobs,
    totalElements: jobs.length,
    totalPages: Math.ceil(jobs.length / 20) || 1,
    number: 0,
    size: 20,
    ...extras,
  };
}

// ── Query hook return value helpers ───────────────────────────────────────────

function mockLoading() {
  mockUseJobs.mockReturnValue({
    data: undefined,
    isLoading: true,
    isFetching: true,
    isError: false,
    refetch: vi.fn(),
  });
}

function mockSuccess(jobs: JobDto[], pageExtras?: Partial<JobsPageResponse>) {
  mockUseJobs.mockReturnValue({
    data: makePageResponse(jobs, pageExtras),
    isLoading: false,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  });
}

function mockError() {
  const refetch = vi.fn();
  mockUseJobs.mockReturnValue({
    data: undefined,
    isLoading: false,
    isFetching: false,
    isError: true,
    refetch,
  });
  return refetch;
}

// ── Render helper ─────────────────────────────────────────────────────────────

function renderPage(route = '/jobs') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <JobsPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('JobsPage', () => {
  beforeEach(() => vi.clearAllMocks());

  // ── AC4: Loading skeletons ────────────────────────────────────────────────

  it('renders skeleton rows while loading', () => {
    mockLoading();
    renderPage();
    expect(screen.getAllByTestId('skeleton-row').length).toBeGreaterThan(0);
  });

  it('does not render job rows while loading', () => {
    mockLoading();
    renderPage();
    expect(screen.queryByText('Acme Corp')).not.toBeInTheDocument();
  });

  // ── AC4: Job rows on success ──────────────────────────────────────────────

  it('renders a row for each job returned', () => {
    mockSuccess([
      makeJob({ id: 'job-1', customerId: 'c1', customerName: 'Acme Corp' }),
      makeJob({ id: 'job-2', customerId: 'c2', customerName: 'Globex Inc' }),
    ]);
    renderPage();
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('Globex Inc')).toBeInTheDocument();
  });

  it('renders technician name in the correct column', () => {
    mockSuccess([makeJob({ assignedTechnicianId: 't1', assigneeName: 'Bob Builder' })]);
    renderPage();
    expect(screen.getByText('Bob Builder')).toBeInTheDocument();
  });

  it('renders em-dash when no technician is assigned', () => {
    mockSuccess([makeJob({ assignedTechnicianId: null, assigneeName: null })]);
    renderPage();
    // The technician cell should show —
    const cells = screen.getAllByRole('cell');
    const techCell = cells.find((c) => c.textContent === '—');
    expect(techCell).toBeTruthy();
  });

  it('hides skeleton rows after data arrives', () => {
    mockSuccess([makeJob()]);
    renderPage();
    expect(screen.queryByTestId('skeleton-row')).not.toBeInTheDocument();
  });

  // ── AC4: Empty state ──────────────────────────────────────────────────────

  it('shows empty state message when no jobs are returned', () => {
    mockSuccess([]);
    renderPage();
    expect(screen.getByText(/no jobs found/i)).toBeInTheDocument();
  });

  it('does not show skeleton or error when result is empty', () => {
    mockSuccess([]);
    renderPage();
    expect(screen.queryByTestId('skeleton-row')).not.toBeInTheDocument();
    expect(screen.queryByText(/failed to load/i)).not.toBeInTheDocument();
  });

  // ── AC4: Error state ──────────────────────────────────────────────────────

  it('shows error state when query fails', () => {
    mockError();
    renderPage();
    expect(screen.getByText(/failed to load jobs/i)).toBeInTheDocument();
  });

  it('calls refetch when "Try again" is clicked', async () => {
    const refetch = mockError();
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(refetch).toHaveBeenCalled();
  });

  // ── AC5: Accessible status badges ────────────────────────────────────────

  it('status badge has aria-label "Submitted" for SUBMITTED status', () => {
    mockSuccess([makeJob({ status: 'SUBMITTED' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'Submitted' })).toBeInTheDocument();
  });

  it('status badge has aria-label "Assigned" for ASSIGNED status', () => {
    mockSuccess([makeJob({ status: 'ASSIGNED' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'Assigned' })).toBeInTheDocument();
  });

  it('status badge has aria-label "In Progress" for IN_PROGRESS status', () => {
    mockSuccess([makeJob({ status: 'IN_PROGRESS' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'In Progress' })).toBeInTheDocument();
  });

  it('status badge has aria-label "Completed" for COMPLETED status', () => {
    mockSuccess([makeJob({ status: 'COMPLETED' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'Completed' })).toBeInTheDocument();
  });

  it('status badge has aria-label "Canceled" for CANCELED status', () => {
    mockSuccess([makeJob({ status: 'CANCELED' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'Canceled' })).toBeInTheDocument();
  });

  it('status badge has aria-label "Done — Pending Approval" for DONE_PENDING_APPROVAL', () => {
    mockSuccess([makeJob({ status: 'DONE_PENDING_APPROVAL' })]);
    renderPage();
    expect(screen.getByRole('status', { name: 'Done — Pending Approval' })).toBeInTheDocument();
  });

  // ── AC3: URL-synced query params ──────────────────────────────────────────

  it('calls useJobs with status from URL on initial render', () => {
    mockLoading();
    renderPage('/jobs?status=ASSIGNED');
    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ASSIGNED' }),
    );
  });

  it('calls useJobs with q from URL on initial render', () => {
    mockLoading();
    renderPage('/jobs?q=plumbing');
    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'plumbing' }),
    );
  });

  it('calls useJobs with page from URL on initial render', () => {
    mockLoading();
    renderPage('/jobs?page=3');
    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({ page: 3 }),
    );
  });

  it('calls useJobs with date range from URL on initial render', () => {
    mockLoading();
    renderPage('/jobs?scheduledFrom=2026-01-01&scheduledTo=2026-12-31');
    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({
        scheduledFrom: '2026-01-01',
        scheduledTo: '2026-12-31',
      }),
    );
  });

  it('Step 1: combines multiple URL filters and passes them to useJobs', () => {
    mockLoading();
    renderPage('/jobs?status=IN_PROGRESS&q=roof&scheduledFrom=2026-06-01');
    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'IN_PROGRESS',
        q: 'roof',
        scheduledFrom: '2026-06-01',
      }),
    );
  });

  // ── AC3: Search form updates URL ──────────────────────────────────────────

  it('search submit updates useJobs params with q value', async () => {
    mockSuccess([]);
    renderPage();
    const input = screen.getByRole('searchbox');
    await userEvent.clear(input);
    await userEvent.type(input, 'plumbing');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => {
      expect(mockUseJobs).toHaveBeenCalledWith(expect.objectContaining({ q: 'plumbing' }));
    });
  });

  it('date from filter updates useJobs scheduledFrom param', async () => {
    mockSuccess([]);
    renderPage();
    const fromInput = screen.getByLabelText(/scheduled from date/i);
    await userEvent.type(fromInput, '2026-07-01');

    await waitFor(() => {
      expect(mockUseJobs).toHaveBeenCalledWith(
        expect.objectContaining({ scheduledFrom: '2026-07-01' }),
      );
    });
  });

  // ── AC2: Pagination ───────────────────────────────────────────────────────

  it('Step 2: shows pagination controls when there are multiple pages', () => {
    mockSuccess(
      [makeJob()],
      { totalElements: 45, totalPages: 3, number: 0 },
    );
    renderPage();
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();
  });

  it('Step 2: shows total job count', () => {
    mockSuccess([makeJob()], { totalElements: 42, totalPages: 3, number: 0 });
    renderPage();
    expect(screen.getByText(/42 jobs total/i)).toBeInTheDocument();
  });

  it('Step 2: Previous button is disabled on the first page', () => {
    mockSuccess([makeJob()], { totalPages: 3, number: 0 });
    renderPage('/jobs?page=0');
    expect(screen.getByRole('button', { name: /previous page/i })).toBeDisabled();
  });

  it('Step 2: Next button is disabled on the last page', () => {
    mockSuccess([makeJob()], { totalPages: 3, number: 2 });
    renderPage('/jobs?page=2');
    expect(screen.getByRole('button', { name: /next page/i })).toBeDisabled();
  });

  it('Step 2: clicking Next calls useJobs with incremented page', async () => {
    mockSuccess([makeJob()], { totalElements: 45, totalPages: 3, number: 0 });
    renderPage('/jobs');

    await userEvent.click(screen.getByRole('button', { name: /next page/i }));

    await waitFor(() => {
      expect(mockUseJobs).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });
  });

  it('Step 2: clicking Previous calls useJobs with decremented page', async () => {
    mockSuccess([makeJob()], { totalElements: 45, totalPages: 3, number: 1 });
    renderPage('/jobs?page=1');

    await userEvent.click(screen.getByRole('button', { name: /previous page/i }));

    await waitFor(() => {
      expect(mockUseJobs).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
    });
  });

  // ── Accessibility ─────────────────────────────────────────────────────────

  it('table has a caption for screen readers', () => {
    mockSuccess([]);
    renderPage();
    expect(document.querySelector('caption')).toBeInTheDocument();
  });

  it('search input has an accessible label', () => {
    mockSuccess([]);
    renderPage();
    expect(screen.getByRole('searchbox', { name: /search jobs/i })).toBeInTheDocument();
  });

  it('pagination shows page info in an aria-live region', () => {
    mockSuccess([makeJob()], { totalPages: 5, number: 2 });
    renderPage('/jobs?page=2');
    const liveRegion = screen.getByText(/page 3 of 5/i);
    expect(liveRegion).toHaveAttribute('aria-live', 'polite');
  });
});
