import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';

export interface JobNoteResponse {
  id: string;
  jobId: string;
  authorUserId: string;
  body: string;
  createdAt: string;
}

export interface JobPhotoUploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
}

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

async function fetchJob(id: string): Promise<JobResponse> {
  const { data } = await apiClient.get<JobResponse>(`/api/v1/jobs/${id}`);
  return data;
}

export function useJob(id: string) {
  return useQuery({
    queryKey: ['job', id],
    queryFn: () => fetchJob(id),
    enabled: Boolean(id),
    staleTime: 30_000,
  });
}

async function fetchJobNotes(id: string): Promise<JobNoteResponse[]> {
  const { data } = await apiClient.get<JobNoteResponse[]>(`/api/v1/jobs/${id}/notes`);
  return data;
}

export function useJobNotes(id: string) {
  return useQuery({
    queryKey: ['jobNotes', id],
    queryFn: () => fetchJobNotes(id),
    enabled: Boolean(id),
    staleTime: 30_000,
  });
}

export async function addNote(jobId: string, body: string): Promise<JobNoteResponse> {
  const { data } = await apiClient.post<JobNoteResponse>(`/api/v1/jobs/${jobId}/notes`, { body });
  return data;
}

export async function startJob(id: string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/v1/jobs/${id}/start`, {});
  return data;
}

export async function markDonePendingApproval(id: string, note?: string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/v1/jobs/${id}/done-pending-approval`, { note });
  return data;
}

export type PendingReason = 'WAITING_FOR_PARTS' | 'RESCHEDULING_NEEDED';

export async function markPending(
  id: string,
  pendingReason: PendingReason,
  note?: string,
): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/v1/jobs/${id}/pending`, {
    pendingReason,
    ...(note ? { note } : {}),
  });
  return data;
}

export async function completeJob(id: string, note?: string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/v1/jobs/${id}/complete`, {
    ...(note ? { note } : {}),
  });
  return data;
}

export async function updateJobSchedule(id: string, scheduledEnd: string): Promise<JobResponse> {
  const { data } = await apiClient.patch<JobResponse>(`/api/v1/jobs/${id}`, { scheduledEnd });
  return data;
}

// TO-DO: backend needs to implement POST /api/v1/jobs/{id}/eta
// → sets inProgressSubstatus = ON_THE_WAY (PRD §13.2)
export async function defineEta(jobId: string, etaMinutes: number): Promise<JobResponse> {
  const scheduledEnd = new Date(Date.now() + etaMinutes * 60_000).toISOString();
  // Partial workaround: update scheduledEnd via PATCH until /eta endpoint exists
  const { data } = await apiClient.patch<JobResponse>(`/api/v1/jobs/${jobId}`, {
    scheduledEnd,
  });
  // Return with optimistic ON_THE_WAY substatus override
  return { ...data, inProgressSubstatus: 'ON_THE_WAY' };
}

export async function requestPhotoUploadUrl(
  jobId: string,
  contentType: string,
  sizeBytes: number,
): Promise<JobPhotoUploadUrlResponse> {
  const { data } = await apiClient.post<JobPhotoUploadUrlResponse>(
    `/api/v1/jobs/${jobId}/photo-upload-url`,
    { contentType, sizeBytes },
  );
  return data;
}
