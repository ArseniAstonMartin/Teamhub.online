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

export type JobStatus =
  | 'SUBMITTED'
  | 'ASSIGNED_TO_TECHNICIAN'
  | 'IN_PROGRESS'
  | 'PENDING'
  | 'DONE_PENDING_APPROVAL'
  | 'COMPLETED'
  | 'CANCELED';

export interface JobResponse {
  id: string;
  tenantId: string;
  customerId: string | null;
  assignedTechnicianId: string | null;
  appointmentSchemaId: string | null;
  status: JobStatus;
  inProgressSubstatus: string | null;
  pendingReason: string | null;
  cancellationReason: string | null;
  customFields: Record<string, unknown>;
  lat: number | null;
  lon: number | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  actualStart: string | null;
  actualEnd: string | null;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
  customerPhone: string | null;
}

interface JobsPage {
  content: JobResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

async function fetchScheduleJobs(from: string, to: string): Promise<JobResponse[]> {
  const { data } = await apiClient.get<JobsPage>('/api/v1/jobs', {
    params: { from, to, page: 0, size: 100 },
  });
  return data.content;
}

export function useScheduleJobs(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ['scheduleJobs', from, to],
    queryFn: () => fetchScheduleJobs(from, to),
    enabled,
    staleTime: 60_000,
  });
}

export async function claimJob(id: string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/v1/jobs/${id}/claim`);
  return data;
}
