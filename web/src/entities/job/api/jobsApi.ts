import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { JobDto, JobNoteResponse, JobPhotoResponse, JobResponse, JobStatusHistoryResponse, JobsPageResponse, JobsQueryParams } from '../model/types';

export interface CreateJobRequest {
  customerId?: string;
  appointmentSchemaId?: string;
  customFields: Record<string, unknown>;
  scheduledStart?: string;
  scheduledEnd?: string;
  lat?: number;
  lon?: number;
}

export const JOBS_QUERY_KEY = ['jobs'] as const;

export function useJobs(params: JobsQueryParams) {
  return useQuery({
    queryKey: [...JOBS_QUERY_KEY, params],
    queryFn: async () => {
      const { data } = await apiClient.get<JobsPageResponse>('/api/v1/jobs', {
        params,
      });
      return data;
    },
    // Poll every 30 s when window is focused; pauses automatically when unfocused.
    // TO-DO: when WebSocket push arrives (v1.2) call queryClient.invalidateQueries({ queryKey: JOBS_QUERY_KEY }) instead — PRD §22.5.5
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}

/** GET /api/v1/jobs/{id} — fetch full job detail. */
export function useJob(id: string) {
  return useQuery({
    queryKey: ['job', id],
    queryFn: async () => {
      const { data } = await apiClient.get<JobResponse>(`/api/v1/jobs/${id}`);
      return data;
    },
    enabled: Boolean(id),
  });
}

/** GET /api/v1/jobs/{id}/notes — newest-first list of notes. */
export function useJobNotes(id: string) {
  return useQuery({
    queryKey: ['job-notes', id],
    queryFn: async () => {
      const { data } = await apiClient.get<JobNoteResponse[]>(`/api/v1/jobs/${id}/notes`);
      return data;
    },
    enabled: Boolean(id),
  });
}

/** GET /api/v1/jobs/{id}/status-history — oldest-first list of status transitions. */
export function useJobStatusHistory(id: string) {
  return useQuery({
    queryKey: ['job-status-history', id],
    queryFn: async () => {
      const { data } = await apiClient.get<JobStatusHistoryResponse[]>(`/api/v1/jobs/${id}/status-history`);
      return data;
    },
    enabled: Boolean(id),
  });
}

/** GET /api/v1/jobs/{id}/photos — newest-first list with pre-signed download URLs. */
export function useJobPhotos(id: string) {
  return useQuery({
    queryKey: ['job-photos', id],
    queryFn: async () => {
      const { data } = await apiClient.get<JobPhotoResponse[]>(`/api/v1/jobs/${id}/photos`);
      return data;
    },
    enabled: Boolean(id),
  });
}

/** POST /api/v1/jobs/{id}/notes — create a new job note. */
export async function createNote(jobId: string, body: string): Promise<JobNoteResponse> {
  const { data } = await apiClient.post<JobNoteResponse>(`/api/v1/jobs/${jobId}/notes`, { body });
  return data;
}

/**
 * POST /api/v1/jobs/{id}/{endpoint} — fires a named transition on a job
 * (claim, start, pending, done-pending-approval, complete, cancel, reopen).
 */
export async function fireTransition(
  id: string,
  endpoint: string,
  body?: Record<string, unknown>,
): Promise<JobDto> {
  const { data } = await apiClient.post<JobDto>(`/api/v1/jobs/${id}/${endpoint}`, body ?? {});
  return data;
}

/** POST /api/v1/jobs — create a new job. */
export function useCreateJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateJobRequest) => {
      const { data } = await apiClient.post<JobResponse>('/api/v1/jobs', body);
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: JOBS_QUERY_KEY });
    },
  });
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
