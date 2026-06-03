import { useQuery } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { JobsPageResponse, JobsQueryParams } from '../model/types';

export function useJobs(params: JobsQueryParams) {
  return useQuery({
    queryKey: ['jobs', params],
    queryFn: async () => {
      const { data } = await apiClient.get<JobsPageResponse>('/api/v1/jobs', {
        params,
      });
      return data;
    },
  });
}
