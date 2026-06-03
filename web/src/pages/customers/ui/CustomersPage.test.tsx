import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import CustomersPage from './CustomersPage';
import type { CustomerResponse, CustomersPageResponse } from '@/entities/customer/model/types';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockUseCustomers,
  mockUseArchiveCustomer,
  mockUseAuthStore,
  mockNavigate,
} = vi.hoisted(() => ({
  mockUseCustomers:      vi.fn(),
  mockUseArchiveCustomer: vi.fn(),
  mockUseAuthStore:      vi.fn(),
  mockNavigate:          vi.fn(),
}));

vi.mock('@/entities/customer/api/customersApi', () => ({
  useCustomers:      mockUseCustomers,
  useArchiveCustomer: mockUseArchiveCustomer,
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeCustomer(overrides: Partial<CustomerResponse> = {}): CustomerResponse {
  return {
    id: 'cust-1',
    tenantId: 'tenant-1',
    name: 'Alice Johnson',
    companyName: null,
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

function makePageResponse(
  customers: CustomerResponse[],
  extras: Partial<CustomersPageResponse> = {},
): CustomersPageResponse {
  return {
    content: customers,
    totalElements: customers.length,
    totalPages: 1,
    number: 0,
    size: 20,
    ...extras,
  };
}

function mockSuccess(customers: CustomerResponse[], pageExtras?: Partial<CustomersPageResponse>) {
  mockUseCustomers.mockReturnValue({
    data: makePageResponse(customers, pageExtras),
    isLoading: false,
    isError: false,
  });
}

function mockLoading() {
  mockUseCustomers.mockReturnValue({
    data: undefined,
    isLoading: true,
    isError: false,
  });
}

function mockArchiveMutation(mutateFn = vi.fn()) {
  mockUseArchiveCustomer.mockReturnValue({
    mutate: mutateFn,
    isPending: false,
  });
  return mutateFn;
}

function renderPage(initialPath = '/customers') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <CustomersPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('CustomersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers(); // ensure real timers for each test
    // Default: admin user
    mockUseAuthStore.mockImplementation((selector: (s: { user: { role: string } }) => unknown) =>
      selector({ user: { role: 'ADMIN' } }),
    );
    mockArchiveMutation();
  });

  // 1. Renders table with customer data
  it('renders customer table with name, phone, and status', () => {
    mockSuccess([makeCustomer()]);
    renderPage();

    expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
    expect(screen.getByText('+15033334444')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  // 2. Search input debounces and calls API with q param
  it('debounces search input and updates q param after 300ms', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockSuccess([]);
    renderPage();

    const input = screen.getByTestId('customer-search');
    await userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) }).type(input, 'Alice');

    // After debounce
    vi.advanceTimersByTime(400);
    await waitFor(() => {
      expect(mockUseCustomers).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'Alice' }),
      );
    });
    vi.useRealTimers();
  });

  // 3. "Show archived" checkbox adds include_archived to query
  it('adds include_archived=true when show-archived checkbox is checked', async () => {
    mockSuccess([]);
    renderPage();

    const checkbox = screen.getByTestId('show-archived-checkbox');
    await userEvent.click(checkbox);

    await waitFor(() => {
      expect(mockUseCustomers).toHaveBeenCalledWith(
        expect.objectContaining({ include_archived: true }),
      );
    });
  });

  // 4. Row click navigates to /customers/:id
  it('navigates to customer detail on row click', async () => {
    mockSuccess([makeCustomer({ id: 'cust-42' })]);
    renderPage();

    const row = screen.getByTestId('customer-row-cust-42');
    await userEvent.click(row);

    expect(mockNavigate).toHaveBeenCalledWith('/customers/cust-42');
  });

  // 5. Archive button opens confirm dialog
  it('opens confirm dialog when archive button is clicked', async () => {
    mockSuccess([makeCustomer({ id: 'cust-1', name: 'Alice Johnson' })]);
    renderPage();

    const archiveBtn = screen.getByTestId('archive-btn-cust-1');
    await userEvent.click(archiveBtn);

    expect(screen.getByRole('dialog', { name: /confirm archive/i })).toBeInTheDocument();
    // Check that the dialog mentions the customer name
    const dialog = screen.getByRole('dialog', { name: /confirm archive/i });
    expect(dialog).toHaveTextContent(/alice johnson/i);
  });

  // 6. Confirm archive calls DELETE mutation
  it('calls archive mutation on confirm', async () => {
    const mutateFn = vi.fn((_id: string, opts?: { onSuccess?: () => void }) => {
      opts?.onSuccess?.();
    });
    mockArchiveMutation(mutateFn);
    mockSuccess([makeCustomer({ id: 'cust-1' })]);
    renderPage();

    await userEvent.click(screen.getByTestId('archive-btn-cust-1'));
    await userEvent.click(screen.getByRole('button', { name: /^archive$/i }));

    expect(mutateFn).toHaveBeenCalledWith('cust-1', expect.any(Object));
  });

  // 7. Archived customer shows "Archived" badge
  it('shows Archived badge for archived customers', () => {
    mockSuccess([makeCustomer({ archivedAt: '2026-02-01T00:00:00Z' })]);
    renderPage();

    expect(screen.getByText('Archived')).toBeInTheDocument();
    expect(screen.queryByText('Active')).not.toBeInTheDocument();
  });

  // 8. Non-dispatcher/admin sees no archive button
  it('hides archive button for TECHNICIAN role', () => {
    mockUseAuthStore.mockImplementation((selector: (s: { user: { role: string } }) => unknown) =>
      selector({ user: { role: 'TECHNICIAN' } }),
    );
    mockSuccess([makeCustomer({ id: 'cust-1' })]);
    renderPage();

    expect(screen.queryByTestId('archive-btn-cust-1')).not.toBeInTheDocument();
  });

  // 9. Loading state shown
  it('shows loading indicator while fetching', () => {
    mockLoading();
    renderPage();

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  // 10. Empty state shown
  it('shows empty state when no customers returned', () => {
    mockSuccess([]);
    renderPage();

    expect(screen.getByText(/no customers found/i)).toBeInTheDocument();
  });
});
