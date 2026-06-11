import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import AcceptInvitePage from './AcceptInvitePage';

// ── Mocks ─────────────────────────────────────────────────────────────────────

const { mockPost, mockSetTokens, mockNavigate, mockToastError } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockSetTokens: vi.fn(),
  mockNavigate: vi.fn(),
  mockToastError: vi.fn(),
}));

vi.mock('@/shared/api/client', () => ({
  default: { post: mockPost },
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: { getState: () => ({ setTokens: mockSetTokens }) },
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('sonner', () => ({
  toast: { error: mockToastError, success: vi.fn() },
}));

// ── Token helpers ─────────────────────────────────────────────────────────────

function makeJwtToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  const body = btoa(JSON.stringify(payload))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${body}.signature`;
}

const VALID_TOKEN = makeJwtToken({
  email: 'alice@acme.com',
  tenant_name: 'Acme Corp',
  tenant_id: 'tenant-123',
  exp: Math.floor(Date.now() / 1000) + 3600,
});

const TOKEN_NO_TENANT = makeJwtToken({
  email: 'bob@example.com',
  exp: Math.floor(Date.now() / 1000) + 3600,
});

/** Opaque token shape issued by InviteService.generateToken (not a JWT). */
const OPAQUE_TOKEN = '_rKfKYHSo0kkfARoOncnlZUoiFhk8s1jUDa1WnEtKss';

// ── Render helpers ────────────────────────────────────────────────────────────

function renderWithToken(token?: string) {
  const route = token ? `/accept-invite?token=${token}` : '/accept-invite';
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AcceptInvitePage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('AcceptInvitePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '' },
    });
    sessionStorage.clear();
  });

  // ── AC1 / Step 4: No token in URL ─────────────────────────────────────────

  it('Step 4: shows invalid-link error when no token in URL', () => {
    renderWithToken();
    expect(screen.getByText(/invalid invite/i)).toBeInTheDocument();
    expect(screen.getByText(/contact your administrator/i)).toBeInTheDocument();
  });

  it('renders invite form for opaque backend token', () => {
    renderWithToken(OPAQUE_TOKEN);
    expect(screen.getByRole('button', { name: /activate account/i })).toBeInTheDocument();
    expect(screen.getByText(/set a password to activate your account/i)).toBeInTheDocument();
  });

  // ── AC1 / Step 1: Valid token decoded ─────────────────────────────────────

  it('Step 1: renders the invite form when token is valid', () => {
    renderWithToken(VALID_TOKEN);
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /activate account/i })).toBeInTheDocument();
  });

  it('Step 1: displays invitee email decoded from the JWT payload', () => {
    renderWithToken(VALID_TOKEN);
    expect(screen.getByText(/alice@acme\.com/i)).toBeInTheDocument();
  });

  it('Step 1: displays tenant name decoded from the JWT payload', () => {
    renderWithToken(VALID_TOKEN);
    expect(screen.getByText(/acme corp/i)).toBeInTheDocument();
  });

  it('Step 1: renders form without tenant name when not in token', () => {
    renderWithToken(TOKEN_NO_TENANT);
    expect(screen.getByText(/bob@example\.com/i)).toBeInTheDocument();
    expect(screen.queryByText(/acme corp/i)).not.toBeInTheDocument();
  });

  // ── AC2: Google + Apple buttons ───────────────────────────────────────────

  it('renders Google OAuth button', () => {
    renderWithToken(VALID_TOKEN);
    expect(screen.getByRole('button', { name: /continue with google/i })).toBeInTheDocument();
  });

  it('Google button stores invite token in sessionStorage and redirects to OAuth', async () => {
    renderWithToken(VALID_TOKEN);
    await userEvent.click(screen.getByRole('button', { name: /continue with google/i }));
    expect(sessionStorage.getItem('teamhub_invite_token')).toBe(VALID_TOKEN);
    expect(window.location.href).toContain('/oauth2/authorization/google');
  });

  it('renders disabled Apple button with coming-soon text', () => {
    renderWithToken(VALID_TOKEN);
    expect(screen.getByRole('button', { name: /sign in with apple/i })).toBeDisabled();
    expect(screen.getByText(/coming soon/i)).toBeInTheDocument();
  });

  // ── Client-side validation ────────────────────────────────────────────────

  it('shows validation errors on blank submit', async () => {
    renderWithToken(VALID_TOKEN);
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(await screen.findByText(/please confirm/i)).toBeInTheDocument();
  });

  it('shows "Passwords do not match" when fields differ', async () => {
    renderWithToken(VALID_TOKEN);
    await userEvent.type(screen.getByLabelText(/^password/i), 'CorrectH0rse');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'Different1!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));
    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
  });

  // ── AC3 / Step 2: Success flow ────────────────────────────────────────────

  it('Step 2: calls POST /api/v1/invites/accept with token and password', async () => {
    mockPost.mockResolvedValueOnce({
      data: { accessToken: 'acc', refreshToken: 'ref', tokenType: 'Bearer', expiresIn: 900 },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(mockPost).toHaveBeenCalledWith('/api/v1/invites/accept', {
      token: VALID_TOKEN,
      password: 'NewPass123!',
    });
  });

  it('Step 2: stores tokens and navigates to /schedule on success', async () => {
    mockPost.mockResolvedValueOnce({
      data: { accessToken: 'acc', refreshToken: 'ref', tokenType: 'Bearer', expiresIn: 900 },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(mockSetTokens).toHaveBeenCalledWith('acc', 'ref');
    expect(mockNavigate).toHaveBeenCalledWith('/schedule', { replace: true });
  });

  // ── AC4 / Step 3: Expired / used invite ──────────────────────────────────

  it('Step 3: shows persistent ExpiredInvite state on INVITE_USED_OR_EXPIRED', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { error: { code: 'INVITE_USED_OR_EXPIRED', message: 'Invite expired.' } },
      },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(await screen.findByText(/invite expired/i)).toBeInTheDocument();
    expect(screen.getByText(/contact your administrator/i)).toBeInTheDocument();
    // Not just a toast — form is gone
    expect(screen.queryByRole('button', { name: /activate account/i })).not.toBeInTheDocument();
  });

  it('Step 3: shows persistent error state on INVITE_NOT_FOUND', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 404,
        data: { error: { code: 'INVITE_NOT_FOUND', message: 'Not found.' } },
      },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(await screen.findByText(/invite expired/i)).toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('Step 3: does NOT navigate on expired invite', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { error: { code: 'INVITE_USED_OR_EXPIRED', message: 'Expired.' } },
      },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    await screen.findByText(/invite expired/i);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ── Generic error (toast, not persistent state) ───────────────────────────

  it('shows toast for unexpected API errors without switching to error page', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 500,
        data: { error: { code: 'SERVER_ERROR', message: 'Internal server error.' } },
      },
    });
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(await screen.findByRole('button', { name: /activate account/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Internal server error.');
    expect(screen.queryByText(/invite expired/i)).not.toBeInTheDocument();
  });

  it('shows fallback toast when error envelope is missing', async () => {
    mockPost.mockRejectedValueOnce(new Error('Network Error'));
    renderWithToken(VALID_TOKEN);

    await userEvent.type(screen.getByLabelText(/^password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /activate account/i }));

    expect(await screen.findByRole('button', { name: /activate account/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Something went wrong. Please try again.');
  });
});
