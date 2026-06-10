import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useJobs } from './jobsApi';
import apiClient from '@/shared/api/client';

vi.mock('@/shared/api/client');

// refetchInterval / refetchOnWindowFocus options are verified in the source;
// runtime polling is covered by e2e / integration tests.

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  Wrapper.displayName = 'QueryWrapper';
  return Wrapper;
}

const mockPageResponse = {
  content: [],
  totalPages: 0,
  totalElements: 0,
  number: 0,
  size: 20,
};

describe('useJobs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('resolves with the mocked page response', async () => {
    (apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: mockPageResponse,
    });

    const { result } = renderHook(() => useJobs({}), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockPageResponse);
  });

  it('resolves without error when called with empty params', async () => {
    (apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { ...mockPageResponse, content: [], totalElements: 0 },
    });

    const { result } = renderHook(() => useJobs({}), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.isError).toBe(false);
  });
});
