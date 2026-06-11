import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AppLayout from './AppLayout';

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: (selector: (s: { user: { id: string; role: string }; logout: () => void }) => unknown) =>
    selector({
      user: { id: 'user-uuid', role: 'DISPATCHER' },
      logout: vi.fn(),
    }),
}));

vi.mock('@/entities/tenant/api/tenantApi', () => ({
  useTenant: () => ({
    data: undefined,
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/entities/user/api/usersApi', () => ({
  useCurrentUser: () => ({
    data: {
      id: 'user-uuid',
      email: 'disp@acme.com',
      name: 'Dispatcher User',
      role: 'DISPATCHER',
      tenantId: 'tenant-uuid',
      lastLoginAt: null,
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/widgets/AlertBanner', () => ({
  AlertBanner: () => null,
}));

vi.mock('@/app/UnsavedChangesGuard', () => ({
  UnsavedChangesGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('@/shared/api/client', () => ({
  default: { post: vi.fn() },
}));

describe('AppLayout', () => {
  it('shows current user display name from /users/me in sidebar and header', () => {
    render(
      <MemoryRouter initialEntries={['/schedule']}>
        <Routes>
          <Route path="/" element={<AppLayout />}>
            <Route path="schedule" element={<div>Schedule</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('sidebar-user-info')).toHaveTextContent('Dispatcher User');
    expect(screen.getByTestId('header-user-info')).toHaveTextContent('Dispatcher User');
  });
});
