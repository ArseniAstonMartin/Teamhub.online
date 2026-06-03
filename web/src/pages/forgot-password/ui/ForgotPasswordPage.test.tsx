import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import ForgotPasswordPage from './ForgotPasswordPage';

// ── Mocks ────────────────────────────────────────────────────────────────────

const { mockPost, mockToastError } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockToastError: vi.fn(),
}));

vi.mock('@/shared/api/client', () => ({
  default: { post: mockPost },
}));

vi.mock('sonner', () => ({
  toast: { error: mockToastError, success: vi.fn() },
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <ForgotPasswordPage />
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Render ────────────────────────────────────────────────────────────────

  it('renders the email field with label', () => {
    renderPage();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  });

  it('renders the submit button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /send reset link/i })).toBeInTheDocument();
  });

  it('email input has autoComplete="email"', () => {
    renderPage();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('autocomplete', 'email');
  });

  // ── AC1: Client-side validation ───────────────────────────────────────────

  it('shows validation error when submitted with blank email', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));
    expect(await screen.findByText(/enter a valid email/i)).toBeInTheDocument();
  });

  it('shows validation error for invalid email format', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText(/email/i), 'notanemail');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));
    expect(await screen.findByText(/enter a valid email/i)).toBeInTheDocument();
  });

  // ── AC1: Success state (anti-enumeration) ─────────────────────────────────

  it('Step 1: shows generic success state after submission (204 response)', async () => {
    mockPost.mockResolvedValueOnce({ status: 204 });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/check your email/i)).toBeInTheDocument();
    expect(screen.getByText(/if that email is registered/i)).toBeInTheDocument();
  });

  it('Step 1: shows same success state even if email is not registered (404 response)', async () => {
    mockPost.mockRejectedValueOnce({ response: { status: 404 } });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'nobody@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/check your email/i)).toBeInTheDocument();
    expect(mockToastError).not.toHaveBeenCalled();
  });

  it('success state includes a "Back to login" link', async () => {
    mockPost.mockResolvedValueOnce({ status: 204 });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('link', { name: /back to login/i })).toBeInTheDocument();
  });

  // ── AC4: 429 rate-limit feedback ──────────────────────────────────────────

  it('Step 4: shows "try again in N minutes" toast on 429 response with Retry-After header', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 429,
        headers: { 'retry-after': '300' }, // 300 seconds = 5 minutes
        data: {},
      },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('button', { name: /send reset link/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringMatching(/try again in \d+ minute/i),
    );
  });

  it('Step 4: shows fallback 5-minute toast on 429 with no Retry-After header', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        status: 429,
        headers: {},
        data: {},
      },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('button', { name: /send reset link/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringMatching(/try again in 5 minutes/i),
    );
  });

  it('Step 4: does NOT show success state on 429 — stays on form', async () => {
    mockPost.mockRejectedValueOnce({
      response: { status: 429, headers: {}, data: {} },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    // After 429 the form should still be visible, not the success card
    await screen.findByRole('button', { name: /send reset link/i });
    expect(screen.queryByText(/check your email/i)).not.toBeInTheDocument();
  });

  // ── 5xx — generic error ────────────────────────────────────────────────────

  it('shows generic error toast on 500 server error', async () => {
    mockPost.mockRejectedValueOnce({
      response: { status: 500 },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('button', { name: /send reset link/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Something went wrong. Please try again.');
  });
});
