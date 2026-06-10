import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import AlertBanner from './AlertBanner';
import type { AlertDto } from '@/entities/alert/api/alertsApi';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const { mockUseAlerts, mockUseDismissAlert, mockUseAuthStore, mockNavigate } = vi.hoisted(() => ({
  mockUseAlerts: vi.fn(),
  mockUseDismissAlert: vi.fn(),
  mockUseAuthStore: vi.fn(),
  mockNavigate: vi.fn(),
}));

vi.mock('@/entities/alert/api/alertsApi', () => ({
  useAlerts: mockUseAlerts,
  useDismissAlert: mockUseDismissAlert,
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeAlert(overrides: Partial<AlertDto> = {}): AlertDto {
  return {
    id: 'alert-1',
    jobId: '550e8400-e29b-41d4-a716-446655440000',
    kind: 'JOB_NOT_HANDLED',
    createdAt: '2026-06-10T10:00:00Z',
    ...overrides,
  };
}

const mutateMock = vi.fn();

interface MockAuthState {
  user: { id: string; tenantId: string; role: string } | null;
}

function setupMocks({
  role = 'ADMIN' as string,
  alerts = [makeAlert()] as AlertDto[],
} = {}) {
  mockUseAuthStore.mockImplementation((selector: (s: MockAuthState) => unknown) =>
    selector({ user: { id: 'user-1', tenantId: 'tenant-1', role } }),
  );
  mockUseAlerts.mockReturnValue({ data: alerts });
  mockUseDismissAlert.mockReturnValue({ mutate: mutateMock, isPending: false });
}

function renderBanner() {
  return render(
    <MemoryRouter>
      <AlertBanner />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('AlertBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders red banner when alerts present', () => {
    setupMocks();
    renderBanner();

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/JOB NOT HANDLED/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /view job/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /dismiss/i })).toBeInTheDocument();
  });

  it('"View Job" navigates to /jobs/{jobId}', async () => {
    setupMocks();
    renderBanner();

    await userEvent.click(screen.getByRole('button', { name: /view job/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/jobs/550e8400-e29b-41d4-a716-446655440000');
  });

  it('"Dismiss" calls dismiss mutation', async () => {
    setupMocks();
    renderBanner();

    await userEvent.click(screen.getByRole('button', { name: /dismiss/i }));

    expect(mutateMock).toHaveBeenCalledWith('alert-1');
  });

  it('renders nothing when alerts array is empty', () => {
    setupMocks({ alerts: [] });
    const { container } = renderBanner();

    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when user is not ADMIN', () => {
    setupMocks({ role: 'TECHNICIAN' });
    const { container } = renderBanner();

    expect(container).toBeEmptyDOMElement();
  });
});
