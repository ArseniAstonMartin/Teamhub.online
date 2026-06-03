import { useQuery } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { JobDto, JobsPageResponse, JobsQueryParams } from '../model/types';

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

/**
 * POST /api/v1/jobs/{id}/{endpoint} — fires a named transition on a job
 * (claim, start, pending, done-pending-approval, complete).
 */
export async function fireTransition(id: string, endpoint: string): Promise<JobDto> {
  const { data } = await apiClient.post<JobDto>(`/api/v1/jobs/${id}/${endpoint}`, {});
  return data;
}

/** PATCH /api/v1/jobs/{id} — reschedule a job (used by calendar drag-and-drop). */
export async function updateJobSchedule(
  id: string,
  scheduledStart: string,
  scheduledEnd: string,
): Promise<JobDto> {
  const { data } = await apiClient.patch<JobDto>(`/api/v1/jobs/${id}`, {
    scheduledStart,
    scheduledEnd,
  });
  return data;
}
