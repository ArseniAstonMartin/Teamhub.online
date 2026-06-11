import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useCurrentUser } from './usersApi';

vi.mock('@/shared/api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

import apiClient from '@/shared/api/client';

const mockGet = vi.mocked(apiClient.get);

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe('useCurrentUser', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches GET /api/v1/users/me', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'user-1',
        email: 'admin@acme.com',
        name: 'Admin User',
        role: 'ADMIN',
        tenantId: 'tenant-1',
        lastLoginAt: '2026-06-10T12:00:00Z',
      },
    });

    const { result } = renderHook(() => useCurrentUser(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGet).toHaveBeenCalledWith('/api/v1/users/me');
    expect(result.current.data?.name).toBe('Admin User');
    expect(result.current.data?.email).toBe('admin@acme.com');
  });
});
