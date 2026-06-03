import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import ResetPasswordPage from './ResetPasswordPage';

// ── Mocks ────────────────────────────────────────────────────────────────────

const { mockPost, mockNavigate, mockToastError, mockToastSuccess } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockNavigate: vi.fn(),
  mockToastError: vi.fn(),
  mockToastSuccess: vi.fn(),
}));

vi.mock('@/shared/api/client', () => ({
  default: { post: mockPost },
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('sonner', () => ({
  toast: { error: mockToastError, success: mockToastSuccess },
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderWithToken(token = 'valid-token-abc') {
  const route = token ? `/reset-password?token=${token}` : '/reset-password';
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Missing / invalid token guard ─────────────────────────────────────────

  it('shows "Invalid link" state when no token is in the URL', () => {
    render(
      <MemoryRouter initialEntries={['/reset-password']}>
        <ResetPasswordPage />
      </MemoryRouter>,
    );
    expect(screen.getByText(/invalid link/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /request a new reset link/i })).toBeInTheDocument();
  });

  // ── Render ────────────────────────────────────────────────────────────────

  it('renders new password and confirm password fields when token present', () => {
    renderWithToken();
    expect(screen.getByLabelText(/new password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
  });

  it('new password field has autoComplete="new-password"', () => {
    renderWithToken();
    expect(screen.getByLabelText(/new password/i)).toHaveAttribute('autocomplete', 'new-password');
  });

  // ── Client-side validation ────────────────────────────────────────────────

  it('shows validation errors on blank submit', async () => {
    renderWithToken();
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(await screen.findByText(/please confirm/i)).toBeInTheDocument();
  });

  it('shows "Passwords do not match" when confirm differs from new password', async () => {
    renderWithToken();
    await userEvent.type(screen.getByLabelText(/new password/i), 'CorrectH0rse');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'DifferentPass');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));
    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
  });

  // ── AC2: Success flow ─────────────────────────────────────────────────────

  it('Step 2: calls POST /api/v1/auth/reset-password with token and newPassword', async () => {
    mockPost.mockResolvedValueOnce({ status: 204 });
    renderWithToken('my-reset-token');

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/reset-password', {
      token: 'my-reset-token',
      newPassword: 'NewPass123!',
    });
  });

  it('Step 2: shows success toast and redirects to /login on valid token', async () => {
    mockPost.mockResolvedValueOnce({ status: 204 });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(mockToastSuccess).toHaveBeenCalledWith(
      expect.stringMatching(/password reset successfully/i),
    );
    expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
  });

  // ── AC2 / Step 3: Expired / used token ────────────────────────────────────

  it('Step 3: shows friendly error toast on RESET_TOKEN_USED_OR_EXPIRED', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { error: { code: 'RESET_TOKEN_USED_OR_EXPIRED', message: 'Token expired.' } },
      },
    });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('button', { name: /reset password/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringMatching(/expired or already been used/i),
    );
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('Step 3: does not redirect on expired token error', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { error: { code: 'RESET_TOKEN_USED_OR_EXPIRED', message: 'Token expired.' } },
      },
    });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    await screen.findByRole('button', { name: /reset password/i });
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ── AC4: 429 rate-limit ────────────────────────────────────────────────────

  it('Step 4: shows "try again in N minutes" toast on 429 with Retry-After header', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 429,
        headers: { 'retry-after': '180' }, // 180s = 3 minutes
        data: {},
      },
    });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('button', { name: /reset password/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringMatching(/try again in \d+ minute/i),
    );
  });

  it('Step 4: shows fallback 5-minute toast on 429 with no Retry-After header', async () => {
    mockPost.mockRejectedValueOnce({
      response: { status: 429, headers: {}, data: {} },
    });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('button', { name: /reset password/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringMatching(/try again in 5 minutes/i),
    );
  });

  // ── Generic server error ──────────────────────────────────────────────────

  it('shows server error.message for unexpected API errors', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { error: { code: 'UNKNOWN', message: 'An unexpected error occurred.' } },
      },
    });
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('button', { name: /reset password/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('An unexpected error occurred.');
  });

  it('shows fallback message when error envelope is absent', async () => {
    mockPost.mockRejectedValueOnce(new Error('Network Error'));
    renderWithToken();

    await userEvent.type(screen.getByLabelText(/new password/i), 'NewPass123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'NewPass123!');
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('button', { name: /reset password/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Something went wrong. Please try again.');
  });
});
