import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';

interface JobsPageMeta {
  totalElements: number;
}

interface JobCountParams {
  status?: string;
  from?: string;
  to?: string;
}

async function fetchJobCount(params: JobCountParams): Promise<number> {
  const { data } = await apiClient.get<JobsPageMeta>('/api/v1/jobs', {
    params: { ...params, page: 0, size: 1 },
  });
  return data.totalElements;
}

export function useJobCount(key: string, params: JobCountParams, enabled = true) {
  return useQuery({
    queryKey: ['jobCount', key, params],
    queryFn: () => fetchJobCount(params),
    enabled,
    staleTime: 60_000,
  });
}
