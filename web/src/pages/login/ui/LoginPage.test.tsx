import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';

// ── Mocks ────────────────────────────────────────────────────────────────────
// Use vi.hoisted so these variables are available when vi.mock factories run
// (vi.mock calls are hoisted to the top of the file by Vitest)

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
  useAuthStore: {
    getState: () => ({ setTokens: mockSetTokens }),
  },
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('sonner', () => ({
  toast: { error: mockToastError },
}));

// ── Helpers ──────────────────────────────────────────────────────────────────

function renderLoginPage(route = '/login') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LoginPage />
    </MemoryRouter>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset window.location.href assignment tracking
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '' },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── AC1 & AC5: Render / accessibility ────────────────────────────────────

  it('renders labelled email and password fields', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('email field has autoComplete="email"', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('autocomplete', 'email');
  });

  it('password field has autoComplete="current-password"', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/password/i)).toHaveAttribute(
      'autocomplete',
      'current-password',
    );
  });

  // ── AC1: Client-side validation ───────────────────────────────────────────

  it('shows field-level errors when submitted blank', async () => {
    renderLoginPage();
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));
    expect(await screen.findByText(/enter a valid email/i)).toBeInTheDocument();
    expect(await screen.findByText(/password is required/i)).toBeInTheDocument();
  });

  it('email error links to input via aria-describedby', async () => {
    renderLoginPage();
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));
    const errorMsg = await screen.findByText(/enter a valid email/i);
    expect(errorMsg).toHaveAttribute('id', 'email-error');
    expect(screen.getByLabelText(/email/i)).toHaveAttribute(
      'aria-describedby',
      'email-error',
    );
  });

  // ── AC2: Successful login ─────────────────────────────────────────────────

  it('calls POST /api/v1/auth/login with credentials on valid submit', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        accessToken: 'acc',
        refreshToken: 'ref',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    });
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/login', {
      email: 'user@example.com',
      password: 'secret123',
    });
  });

  it('stores tokens via authStore on success', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    });
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(mockSetTokens).toHaveBeenCalledWith('access-token', 'refresh-token');
  });

  it('redirects to /schedule by default on success', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        accessToken: 'acc',
        refreshToken: 'ref',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    });
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/schedule', { replace: true });
  });

  it('redirects to ?redirect param on success', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        accessToken: 'acc',
        refreshToken: 'ref',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    });
    renderLoginPage('/login?redirect=/jobs');

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/jobs', { replace: true });
  });

  // ── AC4: Error toast ──────────────────────────────────────────────────────

  it('shows toast with server error.message on login failure', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        data: { error: { code: 'INVALID_CREDENTIALS', message: 'Wrong email or password.' } },
      },
    });
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrongpass');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(await screen.findByRole('button', { name: /^sign in$/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Wrong email or password.');
  });

  it('shows fallback toast message when error envelope is absent', async () => {
    mockPost.mockRejectedValueOnce(new Error('Network Error'));
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(await screen.findByRole('button', { name: /^sign in$/i })).toBeEnabled();
    expect(mockToastError).toHaveBeenCalledWith('Login failed');
  });

  // ── AC3: Google OAuth ─────────────────────────────────────────────────────

  it('Google button is visible and labelled', () => {
    renderLoginPage();
    expect(
      screen.getByRole('button', { name: /continue with google/i }),
    ).toBeInTheDocument();
  });

  it('Google button sets window.location.href to the OAuth endpoint', async () => {
    renderLoginPage();
    await userEvent.click(screen.getByRole('button', { name: /continue with google/i }));
    expect(window.location.href).toContain('/oauth2/authorization/google');
  });

  // ── AC3: Apple Sign-in (disabled placeholder) ─────────────────────────────

  it('Apple button is visible and shows "coming soon" text', () => {
    renderLoginPage();
    expect(screen.getByRole('button', { name: /sign in with apple/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /coming soon/i })).toBeDisabled();
  });

  // ── AC5: Keyboard accessibility ───────────────────────────────────────────

  it('submits the form when Enter is pressed while submit button has focus', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        accessToken: 'acc',
        refreshToken: 'ref',
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    });
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'user@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');

    // Focus the submit button and press Enter — keyboard-only submission
    screen.getByRole('button', { name: /^sign in$/i }).focus();
    await userEvent.keyboard('{Enter}');

    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/login', {
      email: 'user@example.com',
      password: 'secret123',
    });
  });

  it('Tab key visits email, forgot-password link, password, then submit button', async () => {
    renderLoginPage();
    const emailInput = screen.getByLabelText(/email/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const forgotLink = screen.getByRole('link', { name: /forgot password/i });
    const submitBtn = screen.getByRole('button', { name: /^sign in$/i });

    emailInput.focus();
    expect(document.activeElement).toBe(emailInput);

    // The "Forgot password?" link is before the password input in DOM order
    await userEvent.tab();
    expect(document.activeElement).toBe(forgotLink);

    await userEvent.tab();
    expect(document.activeElement).toBe(passwordInput);

    await userEvent.tab();
    expect(document.activeElement).toBe(submitBtn);
  });
});
