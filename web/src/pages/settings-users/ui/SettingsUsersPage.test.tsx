import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SettingsUsersPage from './SettingsUsersPage';
import type { UserResponse, UsersPageResponse } from '@/entities/user/model/userTypes';

// ── Radix UI pointer capture polyfills ───────────────────────────────────────
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => undefined;
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => undefined;
}

// ── Mock Select ───────────────────────────────────────────────────────────────
vi.mock('@/shared/ui/select', () => ({
  Select: ({
    value,
    onValueChange,
    children,
  }: {
    value?: string;
    onValueChange?: (v: string) => void;
    children?: React.ReactNode;
  }) => (
    <select
      data-testid="select-root"
      value={value ?? ''}
      onChange={(e) => onValueChange?.(e.target.value)}
    >
      {children}
    </select>
  ),
  SelectTrigger: ({ children, id }: { children?: React.ReactNode; id?: string; [k: string]: unknown }) => (
    <span id={id}>{children}</span>
  ),
  SelectValue: () => null,
  SelectContent: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ value, children }: { value: string; children?: React.ReactNode }) => (
    <option value={value}>{children}</option>
  ),
  SelectGroup: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
  SelectLabel: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
  SelectSeparator: () => null,
  SelectScrollUpButton: () => null,
  SelectScrollDownButton: () => null,
}));

// ── Mock sonner toast ─────────────────────────────────────────────────────────
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock('sonner', () => ({
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}));

// ── Mock API hooks ────────────────────────────────────────────────────────────
const { mockUseUsers, mockUseInviteUser, mockUseChangeRole, mockUseDeactivateUser, mockUseReactivateUser } =
  vi.hoisted(() => ({
    mockUseUsers: vi.fn(),
    mockUseInviteUser: vi.fn(),
    mockUseChangeRole: vi.fn(),
    mockUseDeactivateUser: vi.fn(),
    mockUseReactivateUser: vi.fn(),
  }));

vi.mock('@/entities/user/api/usersApi', () => ({
  useUsers: mockUseUsers,
  useInviteUser: mockUseInviteUser,
  useChangeRole: mockUseChangeRole,
  useDeactivateUser: mockUseDeactivateUser,
  useReactivateUser: mockUseReactivateUser,
  usersQueryKey: (page: number, size: number) => ['users', { page, size }],
  updateUserRoleInCache: vi.fn(),
}));

// ── Mock authStore ────────────────────────────────────────────────────────────
const { mockUseAuthStore } = vi.hoisted(() => ({ mockUseAuthStore: vi.fn() }));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

// ── Mock axios (top-level — required for LAST_ADMIN_PROTECTED test) ───────────
vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>();
  return {
    ...actual,
    default: actual.default,
    isAxiosError: (e: unknown) =>
      typeof e === 'object' && e !== null && (e as { isAxiosError?: boolean }).isAxiosError === true,
  };
});

// ── Mock TanStack Query ───────────────────────────────────────────────────────
vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();
  return {
    ...actual,
    useQueryClient: () => ({ setQueryData: vi.fn(), invalidateQueries: vi.fn() }),
  };
});

// ── Factories ─────────────────────────────────────────────────────────────────
function makeUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 'user-1',
    email: 'admin@example.com',
    role: 'ADMIN',
    deactivatedAt: null,
    createdAt: '2026-01-15T10:00:00Z',
    ...overrides,
  };
}

function makePageResponse(users: UserResponse[]): UsersPageResponse {
  return {
    content: users,
    totalElements: users.length,
    totalPages: 1,
    number: 0,
    size: 20,
  };
}

function makeInviteMutateAsync(impl?: () => Promise<unknown>) {
  const mutateAsync = vi.fn().mockImplementation(impl ?? (() => Promise.resolve({ id: 'inv-1' })));
  return { mutateAsync, isPending: false };
}

function makeChangeRoleMutateAsync(impl?: () => Promise<unknown>) {
  const mutateAsync = vi.fn().mockImplementation(impl ?? (() => Promise.resolve()));
  return { mutateAsync, isPending: false };
}

function makeDeactivateMutateAsync(impl?: () => Promise<unknown>) {
  const mutateAsync = vi.fn().mockImplementation(impl ?? (() => Promise.resolve()));
  return { mutateAsync, isPending: false };
}

function makeReactivateMutateAsync(impl?: () => Promise<unknown>) {
  const mutateAsync = vi.fn().mockImplementation(impl ?? (() => Promise.resolve()));
  return { mutateAsync, isPending: false };
}

// ── Render helper ─────────────────────────────────────────────────────────────
function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsUsersPage />
    </MemoryRouter>,
  );
}

function setupAdminUser() {
  mockUseAuthStore.mockImplementation((selector: (s: { user: { id: string; role: string } }) => unknown) =>
    selector({ user: { id: 'u-0', role: 'ADMIN' } }),
  );
}

function setupNonAdminUser(role = 'DISPATCHER') {
  mockUseAuthStore.mockImplementation((selector: (s: { user: { id: string; role: string } }) => unknown) =>
    selector({ user: { id: 'u-0', role } }),
  );
}

function setupDefaultMutations() {
  mockUseInviteUser.mockReturnValue(makeInviteMutateAsync());
  mockUseChangeRole.mockReturnValue(makeChangeRoleMutateAsync());
  mockUseDeactivateUser.mockReturnValue(makeDeactivateMutateAsync());
  mockUseReactivateUser.mockReturnValue(makeReactivateMutateAsync());
}

// ── Tests ─────────────────────────────────────────────────────────────────────
describe('SettingsUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupAdminUser();
    setupDefaultMutations();
  });

  // ── 1. Renders user table ─────────────────────────────────────────────────
  it('renders user table with email, role badge, and status', () => {
    const users = [
      makeUser({ email: 'admin@example.com', role: 'ADMIN', deactivatedAt: null }),
      makeUser({ id: 'user-2', email: 'tech@example.com', role: 'TECHNICIAN', deactivatedAt: '2026-05-01T00:00:00Z' }),
    ];
    mockUseUsers.mockReturnValue({ data: makePageResponse(users), isLoading: false, isError: false });

    renderPage();

    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
    expect(screen.getByText('tech@example.com')).toBeInTheDocument();
    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('Technician')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Deactivated')).toBeInTheDocument();
  });

  // ── 2. Invite User button opens invite dialog ─────────────────────────────
  it('opens invite dialog when "Invite User" is clicked', async () => {
    mockUseUsers.mockReturnValue({ data: makePageResponse([makeUser()]), isLoading: false, isError: false });

    renderPage();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /invite user/i }));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    // Email input is a reliable indicator the invite dialog is open
    expect(screen.getByPlaceholderText(/user@example\.com/i)).toBeInTheDocument();
  });

  // ── 3. Submitting invite calls POST /api/v1/invites ───────────────────────
  it('submitting invite calls POST /api/v1/invites with email and role', async () => {
    mockUseUsers.mockReturnValue({ data: makePageResponse([makeUser()]), isLoading: false, isError: false });
    const inviteMock = makeInviteMutateAsync();
    mockUseInviteUser.mockReturnValue(inviteMock);

    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /invite user/i }));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    const dialog = screen.getByRole('dialog');
    const emailInput = within(dialog).getByPlaceholderText(/user@example\.com/i);
    await userEvent.clear(emailInput);
    await userEvent.type(emailInput, 'newuser@example.com');

    const roleSelect = within(dialog).getByTestId('select-root');
    await userEvent.selectOptions(roleSelect, 'TECHNICIAN');

    await userEvent.click(within(dialog).getByRole('button', { name: /send invite/i }));

    await waitFor(() =>
      expect(inviteMock.mutateAsync).toHaveBeenCalledWith({
        email: 'newuser@example.com',
        role: 'TECHNICIAN',
      }),
    );
  });

  // ── 4. Invite success shows toast ────────────────────────────────────────
  it('shows success toast after invite is sent', async () => {
    mockUseUsers.mockReturnValue({ data: makePageResponse([makeUser()]), isLoading: false, isError: false });
    const inviteMock = makeInviteMutateAsync(() => Promise.resolve({ id: 'inv-1' }));
    mockUseInviteUser.mockReturnValue(inviteMock);

    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /invite user/i }));
    await waitFor(() => screen.getByRole('dialog'));

    const dialog = screen.getByRole('dialog');
    const emailInput = within(dialog).getByPlaceholderText(/user@example\.com/i);
    await userEvent.type(emailInput, 'invited@example.com');

    await userEvent.click(within(dialog).getByRole('button', { name: /send invite/i }));

    await waitFor(() =>
      expect(mockToastSuccess).toHaveBeenCalledWith('Invite sent to invited@example.com'),
    );
  });

  // ── 5. Change role opens dialog and calls PATCH /role on confirm ──────────
  it('change role opens dialog and calls PATCH /api/v1/users/:id/role on confirm', async () => {
    const user = makeUser({ email: 'dispatcher@example.com', role: 'DISPATCHER' });
    mockUseUsers.mockReturnValue({ data: makePageResponse([user]), isLoading: false, isError: false });
    const changeRoleMock = makeChangeRoleMutateAsync();
    mockUseChangeRole.mockReturnValue(changeRoleMock);

    renderPage();

    const row = screen.getByText('dispatcher@example.com').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: /change role/i }));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.getByText(/change role for dispatcher@example\.com/i)).toBeInTheDocument();

    const roleSelect = screen.getByTestId('select-root');
    await userEvent.selectOptions(roleSelect, 'TECHNICIAN');

    await userEvent.click(screen.getByRole('button', { name: /confirm/i }));

    await waitFor(() =>
      expect(changeRoleMock.mutateAsync).toHaveBeenCalledWith({
        id: user.id,
        role: 'TECHNICIAN',
      }),
    );
  });

  // ── 6. Deactivate opens confirm dialog ───────────────────────────────────
  it('deactivate button opens confirm dialog for active user', async () => {
    const user = makeUser({ email: 'active@example.com', deactivatedAt: null });
    mockUseUsers.mockReturnValue({ data: makePageResponse([user]), isLoading: false, isError: false });

    renderPage();

    const row = screen.getByText('active@example.com').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: /deactivate/i }));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.getByText(/deactivate user/i)).toBeInTheDocument();
  });

  // ── 7. Deactivate calls POST /deactivate on confirm ──────────────────────
  it('calls POST /api/v1/users/:id/deactivate when confirm is clicked', async () => {
    const user = makeUser({ id: 'user-99', email: 'active@example.com', deactivatedAt: null });
    mockUseUsers.mockReturnValue({ data: makePageResponse([user]), isLoading: false, isError: false });
    const deactivateMock = makeDeactivateMutateAsync();
    mockUseDeactivateUser.mockReturnValue(deactivateMock);

    renderPage();

    const row = screen.getByText('active@example.com').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: /deactivate/i }));

    await waitFor(() => screen.getByRole('dialog'));
    await userEvent.click(screen.getByRole('button', { name: /^deactivate$/i }));

    await waitFor(() =>
      expect(deactivateMock.mutateAsync).toHaveBeenCalledWith('user-99'),
    );
  });

  // ── 8. 422 LAST_ADMIN_PROTECTED shows toast; UI does not change ──────────
  it('shows "Cannot deactivate the last Admin." toast on 422 and does not update UI', async () => {
    const user = makeUser({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN', deactivatedAt: null });
    mockUseUsers.mockReturnValue({ data: makePageResponse([user]), isLoading: false, isError: false });

    const error422 = {
      isAxiosError: true,
      response: { status: 422, data: { errorCode: 'LAST_ADMIN_PROTECTED' } },
    };
    const deactivateMock = {
      mutateAsync: vi.fn().mockRejectedValue(error422),
      isPending: false,
    };
    mockUseDeactivateUser.mockReturnValue(deactivateMock);

    renderPage();

    const row = screen.getByText('admin@example.com').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: /deactivate/i }));

    await waitFor(() => screen.getByRole('dialog'));
    await userEvent.click(screen.getByRole('button', { name: /^deactivate$/i }));

    await waitFor(() =>
      expect(mockToastError).toHaveBeenCalledWith('Cannot deactivate the last Admin.'),
    );

    // The user should still appear as Active (no state change)
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  // ── 9. Reactivate calls POST /reactivate ─────────────────────────────────
  it('calls POST /api/v1/users/:id/reactivate on confirm', async () => {
    const user = makeUser({
      id: 'user-deact',
      email: 'deactivated@example.com',
      deactivatedAt: '2026-03-01T00:00:00Z',
    });
    mockUseUsers.mockReturnValue({ data: makePageResponse([user]), isLoading: false, isError: false });
    const reactivateMock = makeReactivateMutateAsync();
    mockUseReactivateUser.mockReturnValue(reactivateMock);

    renderPage();

    const row = screen.getByText('deactivated@example.com').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: /reactivate/i }));

    await waitFor(() => screen.getByRole('dialog'));
    await userEvent.click(screen.getByRole('button', { name: /^reactivate$/i }));

    await waitFor(() =>
      expect(reactivateMock.mutateAsync).toHaveBeenCalledWith('user-deact'),
    );
  });

  // ── 10. Non-admin sees no page (redirected) ───────────────────────────────
  it('redirects non-admin users away from the page', () => {
    setupNonAdminUser('DISPATCHER');
    mockUseUsers.mockReturnValue({ data: undefined, isLoading: false, isError: false });

    renderPage();

    // The page content should not be rendered
    expect(screen.queryByRole('button', { name: /invite user/i })).not.toBeInTheDocument();
  });
});
