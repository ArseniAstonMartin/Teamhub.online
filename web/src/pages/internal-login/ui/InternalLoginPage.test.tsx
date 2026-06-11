import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import InternalLoginPage from './InternalLoginPage';

const { mockPost, mockSetAccessToken, mockNavigate, mockToastError } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockSetAccessToken: vi.fn(),
  mockNavigate: vi.fn(),
  mockToastError: vi.fn(),
}));

vi.mock('axios', () => ({
  default: { post: mockPost },
}));

vi.mock('@/entities/staff/model/staffAuthStore', () => ({
  useStaffAuthStore: Object.assign(
    (selector?: (s: { isStaffAuthenticated: () => boolean }) => unknown) => {
      const state = { isStaffAuthenticated: () => false };
      return selector ? selector(state) : state;
    },
    {
      getState: () => ({
        setAccessToken: mockSetAccessToken,
        isStaffAuthenticated: () => false,
      }),
    },
  ),
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

function renderPage(route = '/__internal/login') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <InternalLoginPage />
    </MemoryRouter>,
  );
}

describe('InternalLoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders staff login form', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /staff sign in/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('calls POST /api/v1/internal/auth/login on submit', async () => {
    mockPost.mockResolvedValueOnce({
      data: { accessToken: 'staff-jwt', tokenType: 'Bearer', expiresIn: 900 },
    });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/email/i), 'dev@teamhub.online');
    await user.type(screen.getByLabelText(/password/i), 'TeamhubDev131!');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(mockPost).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/internal/auth/login'),
      { email: 'dev@teamhub.online', password: 'TeamhubDev131!' },
    );
    expect(mockSetAccessToken).toHaveBeenCalledWith('staff-jwt');
    expect(mockNavigate).toHaveBeenCalledWith('/__internal/tenants', { replace: true });
  });
});
