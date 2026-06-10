import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import JobDetailPage from './JobDetailPage';
import type { JobNoteResponse, JobPhotoResponse, JobResponse } from '@/entities/job/model/types';
import type { UserRole } from '@/entities/user/model/authStore';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockUseJob,
  mockUseJobNotes,
  mockUseJobPhotos,
  mockCreateNote,
  mockFireTransition,
  mockSetQueryData,
  mockInvalidateQueries,
  mockToastError,
  mockToastInfo,
  mockUseParams,
  mockUseAuthStore,
} = vi.hoisted(() => ({
  mockUseJob:            vi.fn(),
  mockUseJobNotes:       vi.fn(),
  mockUseJobPhotos:      vi.fn(),
  mockCreateNote:        vi.fn(),
  mockFireTransition:    vi.fn(),
  mockSetQueryData:      vi.fn(),
  mockInvalidateQueries: vi.fn(),
  mockToastError:        vi.fn(),
  mockToastInfo:         vi.fn(),
  mockUseParams:         vi.fn(() => ({ id: 'job-123' })),
  mockUseAuthStore:      vi.fn(),
}));

vi.mock('@/entities/job/api/jobsApi', () => ({
  useJob:         mockUseJob,
  useJobNotes:    mockUseJobNotes,
  useJobPhotos:   mockUseJobPhotos,
  createNote:     mockCreateNote,
  fireTransition: mockFireTransition,
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();
  return {
    ...actual,
    useQueryClient: () => ({
      setQueryData:      mockSetQueryData,
      invalidateQueries: mockInvalidateQueries,
    }),
  };
});

vi.mock('sonner', () => ({
  toast: { error: mockToastError, info: mockToastInfo, success: vi.fn() },
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useParams: mockUseParams,
  };
});

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return {
    id: 'job-123',
    tenantId: 'tenant-1',
    customerId: 'customer-1',
    assignedTechnicianId: 'tech-1',
    appointmentSchemaId: null,
    status: 'SUBMITTED',
    inProgressSubstatus: null,
    pendingReason: null,
    cancellationReason: null,
    customFields: {},
    lat: null,
    lon: null,
    scheduledStart: '2026-06-10T09:00:00Z',
    scheduledEnd: '2026-06-10T11:00:00Z',
    actualStart: null,
    actualEnd: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    archivedAt: null,
    customerPhone: null,
    customer: { id: 'customer-1', name: 'Acme Corp', address: '1 Main St' },
    ...overrides,
  };
}

function makeNote(overrides: Partial<JobNoteResponse> = {}): JobNoteResponse {
  return {
    id: 'note-1',
    jobId: 'job-123',
    authorUserId: 'user-42',
    body: 'Fixed the issue.',
    createdAt: '2026-06-01T12:00:00Z',
    ...overrides,
  };
}

function makePhoto(overrides: Partial<JobPhotoResponse> = {}): JobPhotoResponse {
  return {
    id: 'photo-1',
    jobId: 'job-123',
    objectKey: 'tenant/jobs/job-123/abc.jpg',
    contentType: 'image/jpeg',
    sizeBytes: 1024,
    createdAt: '2026-06-01T12:00:00Z',
    downloadUrl: 'https://example.com/photo.jpg?sig=abc',
    ...overrides,
  };
}

function makeUser(role: UserRole, id = 'user-42') {
  return { id, tenantId: 'tenant-1', role };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function mockJobSuccess(job: JobResponse) {
  mockUseJob.mockReturnValue({ data: job, isLoading: false, isError: false });
}

function mockNotesSuccess(notes: JobNoteResponse[]) {
  mockUseJobNotes.mockReturnValue({ data: notes, isLoading: false });
}

function mockPhotosSuccess(photos: JobPhotoResponse[]) {
  mockUseJobPhotos.mockReturnValue({ data: photos, isLoading: false });
}

function mockPhotosEmpty() {
  mockUseJobPhotos.mockReturnValue({ data: [], isLoading: false });
}

function setUser(role: UserRole, id = 'user-42') {
  mockUseAuthStore.mockImplementation((selector: (s: { user: ReturnType<typeof makeUser> }) => unknown) =>
    selector({ user: makeUser(role, id) }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter>
      <JobDetailPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('JobDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseParams.mockReturnValue({ id: 'job-123' });
    mockInvalidateQueries.mockResolvedValue(undefined);
    mockPhotosEmpty();
  });

  // ── Loading state ─────────────────────────────────────────────────────────

  it('shows skeleton while loading', () => {
    mockUseJob.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    mockUseJobNotes.mockReturnValue({ data: [], isLoading: true });
    mockUseJobPhotos.mockReturnValue({ data: [], isLoading: true });
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('job-loading-skeleton')).toBeInTheDocument();
  });

  // ── AC1: Header render ────────────────────────────────────────────────────

  it('renders job header with customer name and status badge', () => {
    mockJobSuccess(makeJob({ status: 'SUBMITTED' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();

    expect(screen.getByTestId('job-header')).toBeInTheDocument();
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getAllByRole('status')[0]).toHaveAttribute('aria-label', 'Submitted');
  });

  it('falls back to customer ID when nested customer object is absent', () => {
    mockJobSuccess(makeJob({ customer: null, customerId: 'abc-12345678' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getByText(/customer #abc-1234/i)).toBeInTheDocument();
  });

  // ── AC2: Action buttons hidden based on role + state ─────────────────────

  it('ADMIN sees Claim button on SUBMITTED job', () => {
    mockJobSuccess(makeJob({ status: 'SUBMITTED' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('action-ASSIGNED')).toBeInTheDocument();
    expect(screen.getByTestId('action-ASSIGNED')).toHaveTextContent('Claim');
  });

  it('non-assigned TECHNICIAN sees Claim button on SUBMITTED (claim is role-allowed)', () => {
    mockJobSuccess(makeJob({ status: 'SUBMITTED', assignedTechnicianId: null }));
    mockNotesSuccess([]);
    setUser('TECHNICIAN', 'other-tech');
    renderPage();
    expect(screen.getByTestId('action-ASSIGNED')).toBeInTheDocument();
  });

  it('TECHNICIAN does NOT see Complete button on DONE_PENDING_APPROVAL (ADMIN/DISPATCHER only)', () => {
    mockJobSuccess(makeJob({ status: 'DONE_PENDING_APPROVAL' }));
    mockNotesSuccess([]);
    setUser('TECHNICIAN', 'tech-1');
    renderPage();
    expect(screen.queryByTestId('action-COMPLETED')).not.toBeInTheDocument();
  });

  it('DISPATCHER sees Complete button on DONE_PENDING_APPROVAL', () => {
    mockJobSuccess(makeJob({ status: 'DONE_PENDING_APPROVAL' }));
    mockNotesSuccess([]);
    setUser('DISPATCHER');
    renderPage();
    expect(screen.getByTestId('action-COMPLETED')).toBeInTheDocument();
  });

  it('COMPLETED job shows no action buttons', () => {
    mockJobSuccess(makeJob({ status: 'COMPLETED' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.queryByRole('group', { name: /job actions/i })).not.toBeInTheDocument();
  });

  // ── AC3: Note composer visibility ─────────────────────────────────────────

  it('shows note composer for ADMIN', () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('note-composer')).toBeInTheDocument();
  });

  it('shows note composer for DISPATCHER', () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('DISPATCHER');
    renderPage();
    expect(screen.getByTestId('note-composer')).toBeInTheDocument();
  });

  it('shows note composer for the assigned TECHNICIAN', () => {
    mockJobSuccess(makeJob({ assignedTechnicianId: 'tech-1' }));
    mockNotesSuccess([]);
    setUser('TECHNICIAN', 'tech-1');
    renderPage();
    expect(screen.getByTestId('note-composer')).toBeInTheDocument();
  });

  it('hides note composer for a non-assigned TECHNICIAN', () => {
    mockJobSuccess(makeJob({ assignedTechnicianId: 'other-tech' }));
    mockNotesSuccess([]);
    setUser('TECHNICIAN', 'tech-1');
    renderPage();
    expect(screen.queryByTestId('note-composer')).not.toBeInTheDocument();
  });

  // ── AC3: Note submission ──────────────────────────────────────────────────

  it('submitting a note calls createNote with the note body', async () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockCreateNote.mockResolvedValueOnce(makeNote({ body: 'Hello' }));
    renderPage();

    await userEvent.type(screen.getByLabelText(/note text/i), 'Hello');
    await userEvent.click(screen.getByRole('button', { name: /add note/i }));

    await waitFor(() => {
      expect(mockCreateNote).toHaveBeenCalledWith('job-123', 'Hello');
    });
  });

  it('optimistically prepends note before createNote resolves', async () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockCreateNote.mockImplementation(() => new Promise(() => {})); // never resolves
    renderPage();

    await userEvent.type(screen.getByLabelText(/note text/i), 'Optimistic note');
    await userEvent.click(screen.getByRole('button', { name: /add note/i }));

    expect(mockSetQueryData).toHaveBeenCalledWith(
      ['job-notes', 'job-123'],
      expect.any(Function),
    );
  });

  it('rolls back optimistic note and shows toast on createNote error', async () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockCreateNote.mockRejectedValueOnce(new Error('Network error'));
    renderPage();

    await userEvent.type(screen.getByLabelText(/note text/i), 'Failing note');
    await userEvent.click(screen.getByRole('button', { name: /add note/i }));

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith(expect.stringMatching(/failed to save note/i));
    });
  });

  // ── AC5: Transition with optimistic update + rollback ─────────────────────

  it('clicking Claim fires fireTransition with "claim" and updates cache optimistically', async () => {
    mockJobSuccess(makeJob({ status: 'SUBMITTED' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockFireTransition.mockResolvedValueOnce(makeJob({ status: 'ASSIGNED' }));
    renderPage();

    await userEvent.click(screen.getByTestId('action-ASSIGNED'));

    expect(mockSetQueryData).toHaveBeenCalledWith(
      ['job', 'job-123'],
      expect.any(Function),
    );

    await waitFor(() => {
      expect(mockFireTransition).toHaveBeenCalledWith('job-123', 'claim');
    });
  });

  it('409 on transition rolls back status via second setQueryData and shows toast', async () => {
    mockJobSuccess(makeJob({ status: 'SUBMITTED' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockFireTransition.mockRejectedValueOnce({
      response: { data: { error: { message: 'Job already claimed' } } },
    });
    renderPage();

    await userEvent.click(screen.getByTestId('action-ASSIGNED'));

    await waitFor(() => {
      expect(mockSetQueryData).toHaveBeenCalledTimes(2);
      expect(mockToastError).toHaveBeenCalledWith('Job already claimed');
    });
  });

  it('rollback updater restores original status', async () => {
    const job = makeJob({ status: 'SUBMITTED' });
    mockJobSuccess(job);
    mockNotesSuccess([]);
    setUser('ADMIN');
    mockFireTransition.mockRejectedValueOnce(new Error('409'));
    renderPage();

    await userEvent.click(screen.getByTestId('action-ASSIGNED'));

    await waitFor(() => expect(mockSetQueryData).toHaveBeenCalledTimes(2));

    const rollbackUpdater = mockSetQueryData.mock.calls[1]?.[1] as
      | ((old: JobResponse | undefined) => JobResponse | undefined)
      | undefined;

    expect(rollbackUpdater).toBeDefined();
    const mutated = { ...job, status: 'ASSIGNED' as const };
    const restored = rollbackUpdater!(mutated);
    expect(restored?.status).toBe('SUBMITTED');
  });

  // ── Photos (TASK-123) ─────────────────────────────────────────────────────

  it('shows empty photos state when job has no photos', () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    mockPhotosEmpty();
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('photos-empty')).toBeInTheDocument();
    expect(screen.getByText('No photos yet')).toBeInTheDocument();
  });

  it('renders photo thumbnail grid when photos exist', () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    mockPhotosSuccess([makePhoto()]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('photos-grid')).toBeInTheDocument();
    expect(screen.getByTestId('photo-thumb-photo-1')).toBeInTheDocument();
  });

  it('opens lightbox with full image when thumbnail is clicked', async () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    mockPhotosSuccess([makePhoto()]);
    setUser('ADMIN');
    renderPage();

    await userEvent.click(screen.getByTestId('photo-thumb-photo-1'));

    expect(screen.getByTestId('photo-lightbox')).toBeInTheDocument();
    expect(screen.getByTestId('photo-lightbox-image')).toHaveAttribute(
      'src',
      'https://example.com/photo.jpg?sig=abc',
    );
  });

  // ── Status history placeholder ────────────────────────────────────────────

  it('shows status history placeholder (TO-DO)', () => {
    mockJobSuccess(makeJob());
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getByTestId('status-history')).toBeInTheDocument();
    expect(screen.getByTestId('status-history')).toHaveTextContent(/full history coming soon/i);
  });

  // ── Sidebar ───────────────────────────────────────────────────────────────

  it('renders schedule dates in sidebar', () => {
    mockJobSuccess(makeJob({ scheduledStart: '2026-06-10T09:00:00Z' }));
    mockNotesSuccess([]);
    setUser('ADMIN');
    renderPage();
    expect(screen.getAllByText(/jun 10/i).length).toBeGreaterThan(0);
  });
});
