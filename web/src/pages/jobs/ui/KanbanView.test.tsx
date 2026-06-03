import { render, screen, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import KanbanView, { ALLOWED_TRANSITIONS, TRANSITION_ENDPOINT } from './KanbanView';
import type { JobDto, JobsPageResponse } from '@/entities/job/model/types';
import type { DropResult } from '@hello-pangea/dnd';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockUseJobs,
  mockFireTransition,
  mockSetQueryData,
  mockToastError,
  mockToastInfo,
} = vi.hoisted(() => ({
  mockUseJobs:         vi.fn(),
  mockFireTransition:  vi.fn(),
  mockSetQueryData:    vi.fn(),
  mockToastError:      vi.fn(),
  mockToastInfo:       vi.fn(),
}));

vi.mock('@/entities/job/api/jobsApi', () => ({
  useJobs:        mockUseJobs,
  fireTransition: mockFireTransition,
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();
  return {
    ...actual,
    useQueryClient: () => ({ setQueryData: mockSetQueryData }),
  };
});

vi.mock('sonner', () => ({
  toast: { error: mockToastError, info: mockToastInfo, success: vi.fn() },
}));

// ── @hello-pangea/dnd mock ───────────────────────────────────────────────────
// Capture onDragEnd so tests can simulate drops directly.
// Droppable/Draggable are rendered as plain divs with test IDs.

let capturedOnDragEnd: ((result: DropResult) => void) | null = null;

vi.mock('@hello-pangea/dnd', () => ({
  DragDropContext: ({ onDragEnd, children }: { onDragEnd: (r: DropResult) => void; children: React.ReactNode }) => {
    capturedOnDragEnd = onDragEnd;
    return <div data-testid="dnd-context">{children}</div>;
  },
  Droppable: ({ children, droppableId }: { children: (...args: unknown[]) => React.ReactNode; droppableId: string }) => (
    <div data-testid={`droppable-${droppableId}`}>
      {children(
        { innerRef: () => {}, droppableProps: {}, placeholder: null },
        { isDraggingOver: false, draggingFromThisWith: null, draggingOverWith: null, isUsingPlaceholder: false },
      )}
    </div>
  ),
  Draggable: ({ children, draggableId, index }: { children: (...args: unknown[]) => React.ReactNode; draggableId: string; index: number }) => (
    <div data-testid={`draggable-${draggableId}`} data-index={index}>
      {children(
        { innerRef: () => {}, draggableProps: {}, dragHandleProps: {} },
        { isDragging: false, isDropAnimating: false, isDraggingOver: false, dropAnimation: null, draggingOver: null, combineWith: null, combineTargetFor: null, mode: 'FLUID' },
      )}
    </div>
  ),
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<JobDto> = {}): JobDto {
  return {
    id: 'job-1',
    status: 'SUBMITTED',
    scheduledStart: '2026-06-10T09:00:00Z',
    scheduledEnd: '2026-06-10T11:00:00Z',
    customer: { id: 'c1', name: 'Acme Corp', address: '1 Main St' },
    assignedTechnician: { id: 't1', name: 'Jane Smith' },
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makePageResponse(jobs: JobDto[]): JobsPageResponse {
  return { content: jobs, totalElements: jobs.length, totalPages: 1, number: 0, size: 200 };
}

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

function renderKanban() {
  return render(
    <MemoryRouter>
      <KanbanView />
    </MemoryRouter>,
  );
}

/** Helper: simulate a drag from one column to another. */
function simulateDrag(draggableId: string, fromStatus: string, toStatus: string, index = 0) {
  act(() => {
    capturedOnDragEnd!({
      draggableId,
      type: 'DEFAULT',
      source: { droppableId: fromStatus, index },
      destination: { droppableId: toStatus, index: 0 },
      reason: 'DROP',
      combine: null,
      mode: 'FLUID',
    });
  });
}

/** Simulate dropping on the same column (no-op). */
function simulateSameDrop(draggableId: string, status: string) {
  act(() => {
    capturedOnDragEnd!({
      draggableId,
      type: 'DEFAULT',
      source: { droppableId: status, index: 0 },
      destination: { droppableId: status, index: 0 },
      reason: 'DROP',
      combine: null,
      mode: 'FLUID',
    });
  });
}

/** Simulate pressing Escape (cancelled drag). */
function simulateCancelled(draggableId: string, status: string) {
  act(() => {
    capturedOnDragEnd!({
      draggableId,
      type: 'DEFAULT',
      source: { droppableId: status, index: 0 },
      destination: null,
      reason: 'CANCEL',
      combine: null,
      mode: 'FLUID',
    });
  });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('KanbanView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOnDragEnd = null;
  });

  // ── State machine map (unit tests — no render needed) ─────────────────────

  describe('ALLOWED_TRANSITIONS set', () => {
    it('allows SUBMITTED → ASSIGNED', () => {
      expect(ALLOWED_TRANSITIONS.has('SUBMITTED→ASSIGNED')).toBe(true);
    });

    it('allows ASSIGNED → IN_PROGRESS', () => {
      expect(ALLOWED_TRANSITIONS.has('ASSIGNED→IN_PROGRESS')).toBe(true);
    });

    it('allows IN_PROGRESS → DONE_PENDING_APPROVAL', () => {
      expect(ALLOWED_TRANSITIONS.has('IN_PROGRESS→DONE_PENDING_APPROVAL')).toBe(true);
    });

    it('allows DONE_PENDING_APPROVAL → COMPLETED', () => {
      expect(ALLOWED_TRANSITIONS.has('DONE_PENDING_APPROVAL→COMPLETED')).toBe(true);
    });

    it('Step 2: does NOT allow SUBMITTED → COMPLETED (disallowed by state machine)', () => {
      expect(ALLOWED_TRANSITIONS.has('SUBMITTED→COMPLETED')).toBe(false);
    });

    it('does NOT allow COMPLETED → IN_PROGRESS (no re-open from COMPLETED yet)', () => {
      expect(ALLOWED_TRANSITIONS.has('COMPLETED→IN_PROGRESS')).toBe(false);
    });

    it('allows cancel from SUBMITTED → CANCELED', () => {
      expect(ALLOWED_TRANSITIONS.has('SUBMITTED→CANCELED')).toBe(true);
    });

    it('allows reopen CANCELED → SUBMITTED', () => {
      expect(ALLOWED_TRANSITIONS.has('CANCELED→SUBMITTED')).toBe(true);
    });
  });

  describe('TRANSITION_ENDPOINT map', () => {
    it('maps SUBMITTED→ASSIGNED to "claim"', () => {
      expect(TRANSITION_ENDPOINT['SUBMITTED→ASSIGNED']).toBe('claim');
    });

    it('maps ASSIGNED→IN_PROGRESS to "start"', () => {
      expect(TRANSITION_ENDPOINT['ASSIGNED→IN_PROGRESS']).toBe('start');
    });

    it('maps IN_PROGRESS→PENDING to "pending"', () => {
      expect(TRANSITION_ENDPOINT['IN_PROGRESS→PENDING']).toBe('pending');
    });

    it('maps IN_PROGRESS→DONE_PENDING_APPROVAL to "done-pending-approval"', () => {
      expect(TRANSITION_ENDPOINT['IN_PROGRESS→DONE_PENDING_APPROVAL']).toBe('done-pending-approval');
    });

    it('maps DONE_PENDING_APPROVAL→COMPLETED to "complete"', () => {
      expect(TRANSITION_ENDPOINT['DONE_PENDING_APPROVAL→COMPLETED']).toBe('complete');
    });
  });

  // ── Render ────────────────────────────────────────────────────────────────

  it('shows loading indicator while fetching', () => {
    mockJobsLoading();
    renderKanban();
    expect(screen.getByText(/loading jobs/i)).toBeInTheDocument();
  });

  it('renders all 7 status columns', () => {
    mockJobsSuccess([]);
    renderKanban();
    expect(screen.getByTestId('droppable-SUBMITTED')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-ASSIGNED')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-IN_PROGRESS')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-PENDING')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-DONE_PENDING_APPROVAL')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-COMPLETED')).toBeInTheDocument();
    expect(screen.getByTestId('droppable-CANCELED')).toBeInTheDocument();
  });

  it('renders a card for each job in its correct column', () => {
    mockJobsSuccess([
      makeJob({ id: 'j1', status: 'SUBMITTED' }),
      makeJob({ id: 'j2', status: 'IN_PROGRESS' }),
    ]);
    renderKanban();
    expect(screen.getByTestId('kanban-card-j1')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-card-j2')).toBeInTheDocument();

    // j1 is inside the SUBMITTED droppable
    const submittedCol = screen.getByTestId('droppable-SUBMITTED');
    expect(submittedCol.contains(screen.getByTestId('kanban-card-j1'))).toBe(true);
    expect(submittedCol.contains(screen.getByTestId('kanban-card-j2'))).toBe(false);
  });

  it('AC3: card shows customer name, address, scheduled start, assignee initial', () => {
    mockJobsSuccess([makeJob({ id: 'j1' })]);
    renderKanban();
    const card = screen.getByTestId('kanban-card-j1');
    expect(card).toHaveTextContent('Acme Corp');
    expect(card).toHaveTextContent('1 Main St');
    expect(card).toHaveTextContent(/jun 10/i);
    expect(card).toHaveTextContent('J'); // Jane Smith initial
  });

  it('AC3: card shows "Unassigned" icon when no technician', () => {
    mockJobsSuccess([makeJob({ id: 'j1', assignedTechnician: null })]);
    renderKanban();
    const card = screen.getByTestId('kanban-card-j1');
    // UserCircle icon is rendered with aria-label="Unassigned"
    expect(card.querySelector('[aria-label="Unassigned"]')).toBeInTheDocument();
  });

  // ── AC4: keyboard accessibility via @hello-pangea/dnd ────────────────────

  it('AC4: draggable card has descriptive aria-label for screen readers', () => {
    mockJobsSuccess([makeJob({ id: 'j1' })]);
    renderKanban();
    const draggable = screen.getByTestId('draggable-j1');
    // The inner div should have the aria-label
    const labeled = draggable.querySelector('[aria-label]');
    expect(labeled?.getAttribute('aria-label')).toMatch(/job card.*acme corp.*submitted/i);
  });

  it('AC4: droppable column has descriptive aria-label', () => {
    mockJobsSuccess([makeJob({ id: 'j1' })]);
    renderKanban();
    const col = screen.getByTestId('droppable-SUBMITTED');
    const inner = col.querySelector('[aria-label]');
    expect(inner?.getAttribute('aria-label')).toMatch(/submitted column/i);
  });

  // ── Drag no-op cases ──────────────────────────────────────────────────────

  it('does nothing when drag is cancelled (Escape)', () => {
    mockJobsSuccess([makeJob({ id: 'j1' })]);
    renderKanban();
    simulateCancelled('j1', 'SUBMITTED');
    expect(mockFireTransition).not.toHaveBeenCalled();
    expect(mockSetQueryData).not.toHaveBeenCalled();
  });

  it('does nothing when dropped in the same column', () => {
    mockJobsSuccess([makeJob({ id: 'j1' })]);
    renderKanban();
    simulateSameDrop('j1', 'SUBMITTED');
    expect(mockFireTransition).not.toHaveBeenCalled();
  });

  // ── AC2: client-side state machine guard ──────────────────────────────────

  it('Step 2: rejects SUBMITTED → COMPLETED client-side without calling API', () => {
    mockJobsSuccess([makeJob({ id: 'j1', status: 'SUBMITTED' })]);
    renderKanban();
    simulateDrag('j1', 'SUBMITTED', 'COMPLETED');
    expect(mockFireTransition).not.toHaveBeenCalled();
    expect(mockSetQueryData).not.toHaveBeenCalled();
    expect(mockToastError).toHaveBeenCalledWith(expect.stringMatching(/cannot move/i));
  });

  it('rejects IN_PROGRESS → SUBMITTED client-side', () => {
    mockJobsSuccess([makeJob({ id: 'j1', status: 'IN_PROGRESS' })]);
    renderKanban();
    simulateDrag('j1', 'IN_PROGRESS', 'SUBMITTED');
    expect(mockFireTransition).not.toHaveBeenCalled();
    expect(mockToastError).toHaveBeenCalled();
  });

  it('shows info toast for cancel transition (no backend endpoint yet)', () => {
    mockJobsSuccess([makeJob({ id: 'j1', status: 'SUBMITTED' })]);
    renderKanban();
    simulateDrag('j1', 'SUBMITTED', 'CANCELED');
    // SUBMITTED→CANCELED is allowed but has no endpoint → info toast
    expect(mockFireTransition).not.toHaveBeenCalled();
    expect(mockToastInfo).toHaveBeenCalledWith(expect.stringMatching(/not yet available/i));
  });

  // ── AC1: successful drag ──────────────────────────────────────────────────

  it('Step 1: SUBMITTED → ASSIGNED calls fireTransition with "claim" endpoint', async () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockResolvedValueOnce({ ...job, status: 'ASSIGNED' });
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    await waitFor(() => {
      expect(mockFireTransition).toHaveBeenCalledWith('j1', 'claim');
    });
  });

  it('Step 1: applies optimistic setQueryData before API call', () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockImplementation(() => new Promise(() => {})); // never resolves
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    // setQueryData should be called immediately (synchronously during the drag)
    expect(mockSetQueryData).toHaveBeenCalledOnce();
  });

  it('ASSIGNED → IN_PROGRESS calls fireTransition with "start"', async () => {
    const job = makeJob({ id: 'j1', status: 'ASSIGNED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockResolvedValueOnce({ ...job, status: 'IN_PROGRESS' });
    renderKanban();

    simulateDrag('j1', 'ASSIGNED', 'IN_PROGRESS');

    await waitFor(() => {
      expect(mockFireTransition).toHaveBeenCalledWith('j1', 'start');
    });
  });

  it('does not show error toast on successful drag', async () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockResolvedValueOnce({ ...job, status: 'ASSIGNED' });
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    await waitFor(() => expect(mockFireTransition).toHaveBeenCalled());
    expect(mockToastError).not.toHaveBeenCalled();
  });

  // ── AC1 + AC2: backend rejection → rollback ───────────────────────────────

  it('Step 1 error path: shows error toast on backend rejection', async () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockRejectedValueOnce({ response: { data: { error: { message: 'Job already claimed' } } } });
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith('Job already claimed');
    });
  });

  it('calls setQueryData twice on rejection — optimistic then rollback', async () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    mockJobsSuccess([job]);
    mockFireTransition.mockRejectedValueOnce(new Error('409 Conflict'));
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    await waitFor(() => {
      expect(mockSetQueryData).toHaveBeenCalledTimes(2);
    });
  });

  it('rollback updater restores original status', async () => {
    const job = makeJob({ id: 'j1', status: 'SUBMITTED' });
    const pageData = makePageResponse([job]);
    mockJobsSuccess([job]);
    mockFireTransition.mockRejectedValueOnce(new Error('Server error'));
    renderKanban();

    simulateDrag('j1', 'SUBMITTED', 'ASSIGNED');

    await waitFor(() => expect(mockSetQueryData).toHaveBeenCalledTimes(2));

    // The rollback updater (2nd call) should restore the original status
    const rollbackUpdater = mockSetQueryData.mock.calls[1]?.[1] as
      | ((old: JobsPageResponse | undefined) => JobsPageResponse | undefined)
      | undefined;

    expect(rollbackUpdater).toBeDefined();
    const mutated: JobsPageResponse = { ...pageData, content: [{ ...job, status: 'ASSIGNED' }] };
    const restored = rollbackUpdater!(mutated);
    expect(restored?.content[0]?.status).toBe('SUBMITTED');
  });

  // ── View toggle on JobsPage ───────────────────────────────────────────────
  // (Tested separately in JobsPage — the toggle appears on the parent)

  it('useJobs is called with size: 200 (no pagination)', () => {
    mockJobsSuccess([]);
    renderKanban();
    expect(mockUseJobs).toHaveBeenCalledWith({ size: 200 });
  });
});
