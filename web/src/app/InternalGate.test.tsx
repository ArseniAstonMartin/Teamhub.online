import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import InternalGate from '@/app/InternalGate';
import { useAuthStore, REFRESH_TOKEN_KEY } from '@/entities/user/model/authStore';
import { useStaffAuthStore, STAFF_TOKEN_KEY } from '@/entities/staff/model/staffAuthStore';

function ProtectedContent() {
  return <p>Internal content</p>;
}

function renderGate(initialPath = '/__internal/tenants') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<InternalGate />}>
          <Route path="/__internal/tenants" element={<ProtectedContent />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

// JWT: { sub: "staff-1", role: "TEAMHUB_STAFF", exp: 9999999999, iat: 1 }
const MOCK_STAFF_TOKEN =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.' +
  btoa(JSON.stringify({ sub: 'staff-1', role: 'TEAMHUB_STAFF', exp: 9999999999, iat: 1 }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '') +
  '.signature';

const MOCK_TENANT_TOKEN =
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

describe('InternalGate', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.setState({ accessToken: null, user: null });
    useStaffAuthStore.setState({ accessToken: null, user: null });
    vi.clearAllMocks();
  });

  it('allows TEAMHUB_STAFF through to child routes', () => {
    useStaffAuthStore.getState().setAccessToken(MOCK_STAFF_TOKEN);
    renderGate();
    expect(screen.getByText('Internal content')).toBeInTheDocument();
  });

  it('shows generic 404 when tenant ADMIN is logged in', () => {
    useAuthStore.getState().setTokens(MOCK_TENANT_TOKEN, 'refresh');
    renderGate();
    expect(screen.getByText('404')).toBeInTheDocument();
    expect(screen.getByText('Page not found')).toBeInTheDocument();
    expect(screen.queryByText('Internal content')).not.toBeInTheDocument();
  });

  it('shows generic 404 when only tenant refresh token exists', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'opaque-refresh');
    renderGate();
    expect(screen.getByText('404')).toBeInTheDocument();
  });

  it('redirects unauthenticated visitors to staff login', () => {
    renderGate();
    expect(screen.queryByText('Internal content')).not.toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
  });
});

describe('staffAuthStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    useStaffAuthStore.setState({ accessToken: null, user: null });
  });

  it('stores staff token separately in sessionStorage', () => {
    useStaffAuthStore.getState().setAccessToken(MOCK_STAFF_TOKEN);
    expect(sessionStorage.getItem(STAFF_TOKEN_KEY)).toBe(MOCK_STAFF_TOKEN);
    expect(useStaffAuthStore.getState().user?.role).toBe('TEAMHUB_STAFF');
  });

  it('logout clears staff session', () => {
    useStaffAuthStore.getState().setAccessToken(MOCK_STAFF_TOKEN);
    useStaffAuthStore.getState().logout();
    expect(sessionStorage.getItem(STAFF_TOKEN_KEY)).toBeNull();
    expect(useStaffAuthStore.getState().accessToken).toBeNull();
  });
});
