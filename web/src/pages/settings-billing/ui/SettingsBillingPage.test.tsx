import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SettingsBillingPage from './SettingsBillingPage';

// ── Mock sonner toast ─────────────────────────────────────────────────────────
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock('sonner', () => ({
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}));

// ── Mock billing API ──────────────────────────────────────────────────────────
const { mockUseBillingStatus, mockUseCreateCheckout, mockUseCreatePortal } =
  vi.hoisted(() => ({
    mockUseBillingStatus: vi.fn(),
    mockUseCreateCheckout: vi.fn(),
    mockUseCreatePortal: vi.fn(),
  }));

vi.mock('@/entities/billing/api/billingApi', () => ({
  useBillingStatus: mockUseBillingStatus,
  useCreateCheckout: mockUseCreateCheckout,
  useCreatePortal: mockUseCreatePortal,
}));

// ── Mock authStore ────────────────────────────────────────────────────────────
const { mockUseAuthStore } = vi.hoisted(() => ({ mockUseAuthStore: vi.fn() }));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

// ── Mock react-router-dom (preserve MemoryRouter / Navigate) ─────────────────
const mockSetSearchParams = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useSearchParams: vi.fn(() => [new URLSearchParams(), mockSetSearchParams]),
  };
});

import { useSearchParams } from 'react-router-dom';

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeCheckoutMutation(impl?: () => Promise<{ url: string }>) {
  const mutateAsync = vi.fn().mockImplementation(
    impl ?? (() => Promise.resolve({ url: 'https://checkout.stripe.com/test' })),
  );
  return { mutateAsync, isPending: false };
}

function makePortalMutation(impl?: () => Promise<{ url: string }>) {
  const mutateAsync = vi.fn().mockImplementation(
    impl ?? (() => Promise.resolve({ url: 'https://billing.stripe.com/test' })),
  );
  return { mutateAsync, isPending: false };
}

function renderPage(route = '/settings/billing') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <SettingsBillingPage />
    </MemoryRouter>,
  );
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  // Default: Admin user
  mockUseAuthStore.mockReturnValue({ role: 'ADMIN', id: 'user-1' });
  // Default: no billing status (skeleton)
  mockUseBillingStatus.mockReturnValue(null);
  // Default: idle mutations
  mockUseCreateCheckout.mockReturnValue(makeCheckoutMutation());
  mockUseCreatePortal.mockReturnValue(makePortalMutation());
  // Default: no search params
  vi.mocked(useSearchParams).mockReturnValue([new URLSearchParams(), mockSetSearchParams]);
});

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SettingsBillingPage', () => {
  // 1. Skeleton shown when no billing status available
  it('renders with skeleton plan card when billing status is null', () => {
    renderPage();

    expect(screen.getByText('Billing')).toBeInTheDocument();
    expect(screen.getByLabelText('Loading plan info')).toBeInTheDocument();
    expect(screen.getByText('Loading plan info…')).toBeInTheDocument();
  });

  // 2. Upgrade button visible for Admin
  it('shows Upgrade button for Admin role', () => {
    renderPage();

    expect(screen.getByRole('button', { name: /upgrade/i })).toBeInTheDocument();
  });

  // 2b. Upgrade button (and full page) hidden for non-Admin → redirect
  it('redirects non-Admin users away from billing page', () => {
    mockUseAuthStore.mockReturnValue({ role: 'DISPATCHER', id: 'user-2' });

    renderPage();

    // Non-admins get redirected; the billing heading should not be present
    expect(screen.queryByText('Billing')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /upgrade/i })).not.toBeInTheDocument();
  });

  // 3. Clicking Upgrade (Team) calls POST checkout/team and redirects
  it('clicking Upgrade → Team calls checkout mutation and redirects', async () => {
    const originalHref = window.location.href;
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...window.location, set href(v: string) { hrefSetter(v); } },
    });

    const checkoutUrl = 'https://checkout.stripe.com/team-session';
    const mutateAsync = vi.fn().mockResolvedValue({ url: checkoutUrl });
    mockUseCreateCheckout.mockReturnValue({ mutateAsync, isPending: false });

    renderPage();

    const upgradeBtn = screen.getByRole('button', { name: /upgrade/i });
    await userEvent.click(upgradeBtn);

    // Dropdown should open; click Team option
    const teamOption = await screen.findByRole('menuitem', { name: /team/i });
    await userEvent.click(teamOption);

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith('team');
      expect(hrefSetter).toHaveBeenCalledWith(checkoutUrl);
    });

    // Restore
    Object.defineProperty(window, 'location', { writable: true, value: { href: originalHref } });
  });

  // 4. Manage Billing button calls portal mutation and redirects
  it('clicking Manage Billing calls portal mutation and redirects', async () => {
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...window.location, set href(v: string) { hrefSetter(v); } },
    });

    const portalUrl = 'https://billing.stripe.com/session';
    const mutateAsync = vi.fn().mockResolvedValue({ url: portalUrl });
    mockUseCreatePortal.mockReturnValue({ mutateAsync, isPending: false });

    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /manage billing/i }));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalled();
      expect(hrefSetter).toHaveBeenCalledWith(portalUrl);
    });
  });

  // 5. ?checkout=success shows toast and clears param
  it('shows success toast and clears ?checkout=success param on mount', () => {
    vi.mocked(useSearchParams).mockReturnValue([
      new URLSearchParams('checkout=success'),
      mockSetSearchParams,
    ]);

    renderPage('/settings/billing?checkout=success');

    expect(mockToastSuccess).toHaveBeenCalledWith('Subscription updated successfully!');
    expect(mockSetSearchParams).toHaveBeenCalled();
  });

  // 6. Loading spinner shown on Upgrade button during API call
  it('shows loading spinner on Upgrade button when checkout is pending', () => {
    mockUseCreateCheckout.mockReturnValue({ mutateAsync: vi.fn(), isPending: true });

    renderPage();

    const upgradeBtn = screen.getByRole('button', { name: /upgrade/i });
    expect(upgradeBtn).toBeDisabled();
    expect(screen.getByText('Redirecting…')).toBeInTheDocument();
  });

  // 7. Loading spinner shown on Manage Billing button during API call
  it('shows loading spinner on Manage Billing button when portal is pending', () => {
    mockUseCreatePortal.mockReturnValue({ mutateAsync: vi.fn(), isPending: true });

    renderPage();

    const manageBillingBtn = screen.getByRole('button', { name: /manage billing/i });
    expect(manageBillingBtn).toBeDisabled();
  });

  // 8. Non-admin redirected
  it('does not render billing content for TECHNICIAN role', () => {
    mockUseAuthStore.mockReturnValue({ role: 'TECHNICIAN', id: 'user-3' });

    renderPage();

    expect(screen.queryByText('Billing')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /manage billing/i })).not.toBeInTheDocument();
  });

  // Bonus: Upgrade dropdown opens and shows all tiers
  it('shows all plan upgrade options in the dropdown', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /upgrade/i }));

    expect(await screen.findByRole('menuitem', { name: /starter/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /team/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /enterprise/i })).toBeInTheDocument();
  });

  // Bonus: toast.error shown when checkout fails
  it('shows error toast when checkout fails', async () => {
    const mutateAsync = vi.fn().mockRejectedValue(new Error('Network error'));
    mockUseCreateCheckout.mockReturnValue({ mutateAsync, isPending: false });

    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /upgrade/i }));
    const teamOption = await screen.findByRole('menuitem', { name: /team/i });
    await userEvent.click(teamOption);

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith(
        'Failed to open checkout. Please try again.',
      );
    });
  });

  // Bonus: toast.error shown when portal fails
  it('shows error toast when portal call fails', async () => {
    const mutateAsync = vi.fn().mockRejectedValue(new Error('Network error'));
    mockUseCreatePortal.mockReturnValue({ mutateAsync, isPending: false });

    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /manage billing/i }));

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith(
        'Failed to open billing portal. Please try again.',
      );
    });
  });
});
