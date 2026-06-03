import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import CustomerDetailPage from './CustomerDetailPage';
import type { CustomerResponse } from '@/entities/customer/model/types';
import type { JobDto, JobsPageResponse } from '@/entities/job/model/types';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockUseCustomer,
  mockUseUpdateCustomer,
  mockUseArchiveCustomer,
  mockUseJobs,
  mockUseAuthStore,
  mockUseParams,
  mockNavigate,
} = vi.hoisted(() => ({
  mockUseCustomer:        vi.fn(),
  mockUseUpdateCustomer:  vi.fn(),
  mockUseArchiveCustomer: vi.fn(),
  mockUseJobs:            vi.fn(),
  mockUseAuthStore:       vi.fn(),
  mockUseParams:          vi.fn(() => ({ id: 'cust-1' })),
  mockNavigate:           vi.fn(),
}));

vi.mock('@/entities/customer/api/customersApi', () => ({
  useCustomer:        mockUseCustomer,
  useUpdateCustomer:  mockUseUpdateCustomer,
  useArchiveCustomer: mockUseArchiveCustomer,
}));

vi.mock('@/entities/job/api/jobsApi', () => ({
  useJobs: mockUseJobs,
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useParams: mockUseParams,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

// AddressAutocomplete falls back to plain input in test environment
vi.mock('@/shared/ui/AddressAutocomplete', () => ({
  AddressAutocomplete: ({ onChange, value }: { onChange: (v: unknown) => void; value: string; 'data-testid'?: string }) => (
    <input
      data-testid="address-autocomplete-fallback"
      value={value}
      onChange={(e) =>
        onChange({
          address: e.target.value,
          city: 'Portland',
          state: 'OR',
          zip: '97201',
          lat: 45.52,
          lon: -122.68,
        })
      }
    />
  ),
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeCustomer(overrides: Partial<CustomerResponse> = {}): CustomerResponse {
  return {
    id: 'cust-1',
    tenantId: 'tenant-1',
    name: 'Alice Johnson',
    companyName: 'Acme Plumbing',
    phone: '+15033334444',
    email: 'alice@example.com',
    addressLine: '123 Main St',
    city: 'Portland',
    state: 'OR',
    zip: '97201',
    lat: 45.52,
    lon: -122.68,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    archivedAt: null,
    ...overrides,
  };
}

function makeJob(overrides: Partial<JobDto> = {}): JobDto {
  return {
    id: 'job-1',
    status: 'SUBMITTED',
    scheduledStart: '2026-06-10T09:00:00Z',
    scheduledEnd: null,
    customer: { id: 'cust-1', name: 'Alice Johnson', address: '123 Main St' },
    assignedTechnician: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makeJobsPage(jobs: JobDto[]): JobsPageResponse {
  return { content: jobs, totalElements: jobs.length, totalPages: 1, number: 0, size: 50 };
}

function mockCustomerSuccess(customer: CustomerResponse) {
  mockUseCustomer.mockReturnValue({ data: customer, isLoading: false, isError: false });
}

function mockJobsSuccess(jobs: JobDto[]) {
  mockUseJobs.mockReturnValue({ data: makeJobsPage(jobs), isLoading: false });
}

function mockUpdateMutation(mutateFn = vi.fn()) {
  mockUseUpdateCustomer.mockReturnValue({ mutate: mutateFn, isPending: false });
  return mutateFn;
}

function mockArchiveMutation(mutateFn = vi.fn()) {
  mockUseArchiveCustomer.mockReturnValue({ mutate: mutateFn, isPending: false });
  return mutateFn;
}

function setRole(role: string) {
  mockUseAuthStore.mockImplementation((selector: (s: { user: { role: string } }) => unknown) =>
    selector({ user: { role } }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/customers/cust-1']}>
      <CustomerDetailPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('CustomerDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setRole('ADMIN');
    mockUpdateMutation();
    mockArchiveMutation();
    mockJobsSuccess([]);
  });

  // 1. Renders customer header
  it('renders customer header with name, phone, and email', () => {
    mockCustomerSuccess(makeCustomer());
    renderPage();

    expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
    expect(screen.getByTestId('customer-phone')).toHaveTextContent('+15033334444');
    expect(screen.getByTestId('customer-email')).toHaveTextContent('alice@example.com');
  });

  // 2. Shows map iframe when lat/lon available
  it('renders map iframe when lat/lon are present', () => {
    mockCustomerSuccess(makeCustomer({ lat: 45.52, lon: -122.68 }));
    renderPage();

    const iframe = screen.getByTestId('map-iframe');
    expect(iframe).toBeInTheDocument();
    expect(iframe).toHaveAttribute('src', expect.stringContaining('45.52'));
    expect(iframe).toHaveAttribute('src', expect.stringContaining('-122.68'));
  });

  // 3. Shows address text when lat/lon null
  it('renders address text when lat/lon are null', () => {
    mockCustomerSuccess(makeCustomer({ lat: null, lon: null }));
    renderPage();

    expect(screen.queryByTestId('map-iframe')).not.toBeInTheDocument();
    expect(screen.getByTestId('map-address-text')).toBeInTheDocument();
  });

  // 4. Jobs tab shows jobs filtered by customerId
  it('shows job list in Jobs tab filtered by customerId', () => {
    mockCustomerSuccess(makeCustomer());
    mockJobsSuccess([makeJob({ id: 'job-99', status: 'IN_PROGRESS' })]);
    renderPage();

    expect(mockUseJobs).toHaveBeenCalledWith(
      expect.objectContaining({ customerId: 'cust-1' }),
    );
    expect(screen.getByTestId('jobs-tab-list')).toBeInTheDocument();
    expect(screen.getByTestId('job-row-job-99')).toBeInTheDocument();
  });

  // 5. Notes tab shows placeholder
  it('shows notes placeholder when Notes tab is clicked', async () => {
    mockCustomerSuccess(makeCustomer());
    renderPage();

    await userEvent.click(screen.getByTestId('tab-notes'));
    expect(screen.getByTestId('notes-placeholder')).toBeInTheDocument();
    expect(screen.getByText(/notes coming soon/i)).toBeInTheDocument();
  });

  // 6. Edit button visible for Admin/Dispatcher; hidden for Technician
  it('shows Edit button for ADMIN', () => {
    setRole('ADMIN');
    mockCustomerSuccess(makeCustomer());
    renderPage();

    expect(screen.getByTestId('edit-customer-btn')).toBeInTheDocument();
  });

  it('hides Edit button for TECHNICIAN', () => {
    setRole('TECHNICIAN');
    mockCustomerSuccess(makeCustomer());
    renderPage();

    expect(screen.queryByTestId('edit-customer-btn')).not.toBeInTheDocument();
  });

  // 7. Edit dialog pre-fills current values
  it('pre-fills edit dialog with current customer values', async () => {
    mockCustomerSuccess(makeCustomer());
    renderPage();

    await userEvent.click(screen.getByTestId('edit-customer-btn'));

    expect(screen.getByTestId('edit-name-input')).toHaveValue('Alice Johnson');
    expect(screen.getByTestId('edit-email-input')).toHaveValue('alice@example.com');
    expect(screen.getByTestId('edit-phone-input')).toHaveValue('+15033334444');
    expect(screen.getByTestId('edit-phone-input')).toBeDisabled();
  });

  // 8. Saving edit calls PATCH with updated fields
  it('calls updateMutation on save with updated name', async () => {
    const mutateFn = vi.fn((_body: unknown, opts?: { onSuccess?: () => void }) => {
      opts?.onSuccess?.();
    });
    mockUpdateMutation(mutateFn);
    mockCustomerSuccess(makeCustomer());
    renderPage();

    await userEvent.click(screen.getByTestId('edit-customer-btn'));

    const nameInput = screen.getByTestId('edit-name-input');
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Bob Smith');

    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => {
      expect(mutateFn).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Bob Smith' }),
        expect.any(Object),
      );
    });
  });

  // 9. Archived badge shown on archived customer
  it('shows Archived badge when customer is archived', () => {
    mockCustomerSuccess(makeCustomer({ archivedAt: '2026-02-01T00:00:00Z' }));
    renderPage();

    expect(screen.getByText('Archived')).toBeInTheDocument();
    // Archive button should be hidden for already-archived customer
    expect(screen.queryByTestId('archive-customer-btn')).not.toBeInTheDocument();
  });

  // 10. Archive button navigates away after archive
  it('navigates to /customers after successful archive', async () => {
    const mutateFn = vi.fn((_id: string, opts?: { onSuccess?: () => void }) => {
      opts?.onSuccess?.();
    });
    mockArchiveMutation(mutateFn);
    mockCustomerSuccess(makeCustomer());
    renderPage();

    await userEvent.click(screen.getByTestId('archive-customer-btn'));
    // Click the destructive Archive button inside the confirm dialog
    const archiveBtns = screen.getAllByRole('button', { name: /^archive$/i });
    await userEvent.click(archiveBtns[archiveBtns.length - 1]);

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/customers');
    });
  });
});
