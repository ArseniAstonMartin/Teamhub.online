import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SettingsTenantPage from './SettingsTenantPage';
import type { TenantSettingsResponse } from '@/entities/tenant/model/types';

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
    disabled,
  }: {
    value?: string;
    onValueChange?: (v: string) => void;
    children?: React.ReactNode;
    disabled?: boolean;
  }) => (
    <select
      data-testid="select-root"
      value={value ?? ''}
      disabled={disabled}
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
}));

// ── Mock sonner toast ─────────────────────────────────────────────────────────
const mockToastError = vi.fn();

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}));

// ── Mock API hooks ────────────────────────────────────────────────────────────
const { mockUseTenantSettings, mockUseUpdateTenantSettings } = vi.hoisted(() => ({
  mockUseTenantSettings: vi.fn(),
  mockUseUpdateTenantSettings: vi.fn(),
}));

vi.mock('@/entities/tenant/api/tenantApi', () => ({
  useTenantSettings: mockUseTenantSettings,
  useUpdateTenantSettings: mockUseUpdateTenantSettings,
}));

// ── Mock authStore ────────────────────────────────────────────────────────────
const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: (selector: (s: { user: { id: string; tenantId: string; role: string } | null }) => unknown) =>
    mockUseAuthStore(selector),
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeSettings(overrides: Partial<TenantSettingsResponse> = {}): TenantSettingsResponse {
  return { closeJobsEnabled: false, ...overrides };
}

function makeUpdateMutation(overrides: {
  mutateAsync?: ReturnType<typeof vi.fn>;
  isPending?: boolean;
} = {}) {
  return {
    mutateAsync: overrides.mutateAsync ?? vi.fn().mockResolvedValue(makeSettings()),
    isPending: overrides.isPending ?? false,
  };
}

function setupAdminUser() {
  mockUseAuthStore.mockImplementation(
    (selector: (s: { user: { id: string; tenantId: string; role: string } }) => unknown) =>
      selector({ user: { id: 'user-1', tenantId: 'tenant-1', role: 'ADMIN' } }),
  );
}

function setupNonAdminUser(role: string) {
  mockUseAuthStore.mockImplementation(
    (selector: (s: { user: { id: string; tenantId: string; role: string } }) => unknown) =>
      selector({ user: { id: 'user-2', tenantId: 'tenant-1', role } }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/settings/tenant']}>
      <SettingsTenantPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SettingsTenantPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupAdminUser();
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings(),
      isLoading: false,
      isError: false,
    });
    mockUseUpdateTenantSettings.mockReturnValue(makeUpdateMutation());
  });

  // ── 1. Renders the page with Close Jobs toggle ────────────────────────────
  it('renders the Tenant Settings page with Close Jobs toggle', () => {
    renderPage();

    expect(screen.getByText('Tenant Settings')).toBeInTheDocument();
    expect(screen.getByText(/Allow Technicians to mark jobs Completed/i)).toBeInTheDocument();
    expect(screen.getByRole('switch')).toBeInTheDocument();
  });

  // ── 2. Toggle checked state reflects closeJobsEnabled: true ──────────────
  it('shows toggle as checked when closeJobsEnabled is true', () => {
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings({ closeJobsEnabled: true }),
      isLoading: false,
      isError: false,
    });

    renderPage();

    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
  });

  // ── 3. Toggle reflects closeJobsEnabled: false ────────────────────────────
  it('shows toggle as unchecked when closeJobsEnabled is false', () => {
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings({ closeJobsEnabled: false }),
      isLoading: false,
      isError: false,
    });

    renderPage();

    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');
  });

  // ── 4. Toggle change calls PATCH with { closeJobsEnabled: false } ─────────
  it('calls PATCH /api/v1/tenant/settings with { closeJobsEnabled: false } when toggled off', async () => {
    const mutateAsync = vi.fn().mockResolvedValue(makeSettings({ closeJobsEnabled: false }));
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings({ closeJobsEnabled: true }),
      isLoading: false,
      isError: false,
    });
    mockUseUpdateTenantSettings.mockReturnValue(makeUpdateMutation({ mutateAsync }));

    renderPage();

    await userEvent.click(screen.getByRole('switch'));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith({ closeJobsEnabled: false }),
    );
  });

  // ── 5. Optimistic toggle — UI updates immediately before response ──────────
  it('updates toggle UI immediately (optimistically) before server responds', async () => {
    let resolveMutation!: (value: TenantSettingsResponse) => void;
    const pendingMutateAsync = vi.fn().mockReturnValue(
      new Promise<TenantSettingsResponse>((resolve) => {
        resolveMutation = resolve;
      }),
    );
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings({ closeJobsEnabled: false }),
      isLoading: false,
      isError: false,
    });
    mockUseUpdateTenantSettings.mockReturnValue(makeUpdateMutation({ mutateAsync: pendingMutateAsync }));

    renderPage();

    // Initially unchecked
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');

    // Click toggle — don't await resolution
    void userEvent.click(screen.getByRole('switch'));

    // UI should update immediately (optimistic)
    await waitFor(() =>
      expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true'),
    );

    // Resolve the pending mutation
    resolveMutation(makeSettings({ closeJobsEnabled: true }));
  });

  // ── 6. API error rolls back toggle and shows toast ────────────────────────
  it('rolls back toggle and shows error toast when PATCH fails', async () => {
    const mutateAsync = vi.fn().mockRejectedValue(new Error('Network error'));
    mockUseTenantSettings.mockReturnValue({
      data: makeSettings({ closeJobsEnabled: false }),
      isLoading: false,
      isError: false,
    });
    mockUseUpdateTenantSettings.mockReturnValue(makeUpdateMutation({ mutateAsync }));

    renderPage();

    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');

    await userEvent.click(screen.getByRole('switch'));

    // Should rollback to original value
    await waitFor(() =>
      expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false'),
    );

    expect(mockToastError).toHaveBeenCalledWith('Failed to update setting. Please try again.');
  });

  // ── 7. Team display name field is rendered (but save disabled) ────────────
  it('renders team display name field with disabled Save button', () => {
    renderPage();

    expect(screen.getByLabelText(/Team display name/i)).toBeInTheDocument();
    const saveButtons = screen.getAllByRole('button', { name: /save team name/i });
    expect(saveButtons[0]).toBeDisabled();
  });

  // ── 8. Time zone select is rendered (but save disabled) ───────────────────
  it('renders time zone select with disabled Save button', () => {
    renderPage();

    expect(screen.getByTestId('select-root')).toBeInTheDocument();
    const saveButtons = screen.getAllByRole('button', { name: /save time zone/i });
    expect(saveButtons[0]).toBeDisabled();
  });

  // ── 9. Non-admin is redirected away from the page ─────────────────────────
  it('redirects non-admin (Dispatcher) to /schedule', () => {
    setupNonAdminUser('DISPATCHER');
    renderPage();

    expect(screen.queryByText('Tenant Settings')).not.toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });

  // ── 10. Non-admin Technician is also redirected ───────────────────────────
  it('redirects non-admin (Technician) to /schedule', () => {
    setupNonAdminUser('TECHNICIAN');
    renderPage();

    expect(screen.queryByText('Tenant Settings')).not.toBeInTheDocument();
  });

  // ── 11. Loading state is shown ────────────────────────────────────────────
  it('shows loading state while fetching settings', () => {
    mockUseTenantSettings.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    });

    renderPage();

    expect(screen.getByTestId('loading-state')).toBeInTheDocument();
  });
});
