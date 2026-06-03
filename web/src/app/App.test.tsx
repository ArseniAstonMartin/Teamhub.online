import { render } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import App from '../App';
import { useAuthStore } from '@/entities/user/model/authStore';

// ── App smoke test ─────────────────────────────────────────────────────────
describe('App', () => {
  it('renders without crashing', () => {
    // The app renders via RouterProvider; as long as no exception is thrown
    // the shell is working.
    expect(() => render(<App />)).not.toThrow();
  });
});

// ── authStore unit tests ───────────────────────────────────────────────────

// Minimal valid JWT with payload:
//   { sub: "user-1", tenant_id: "tenant-1", role: "ADMIN", exp: 9999999999, iat: 1 }
const MOCK_ACCESS_TOKEN =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.' +
  btoa(
    JSON.stringify({
      sub: 'user-1',
      tenant_id: 'tenant-1',
      role: 'ADMIN',
      exp: 9999999999,
      iat: 1,
    }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '') +
  '.signature';

const MOCK_REFRESH_TOKEN = 'opaque-refresh-token';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ accessToken: null, user: null });
    vi.clearAllMocks();
  });

  it('setTokens stores access token in memory and refresh token in localStorage', () => {
    useAuthStore.getState().setTokens(MOCK_ACCESS_TOKEN, MOCK_REFRESH_TOKEN);

    expect(useAuthStore.getState().accessToken).toBe(MOCK_ACCESS_TOKEN);
    expect(localStorage.getItem('teamhub_refresh_token')).toBe(MOCK_REFRESH_TOKEN);
  });

  it('setTokens parses JWT claims into user object', () => {
    useAuthStore.getState().setTokens(MOCK_ACCESS_TOKEN, MOCK_REFRESH_TOKEN);

    const { user } = useAuthStore.getState();
    expect(user).not.toBeNull();
    expect(user?.id).toBe('user-1');
    expect(user?.tenantId).toBe('tenant-1');
    expect(user?.role).toBe('ADMIN');
  });

  it('logout clears access token, user, and localStorage refresh token', () => {
    useAuthStore.getState().setTokens(MOCK_ACCESS_TOKEN, MOCK_REFRESH_TOKEN);
    useAuthStore.getState().logout();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(localStorage.getItem('teamhub_refresh_token')).toBeNull();
  });
});
