export type JobStatus =
  | 'SUBMITTED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'PENDING'
  | 'DONE_PENDING_APPROVAL'
  | 'COMPLETED'
  | 'CANCELED';

export type JobSortField = 'scheduledStart' | 'createdAt' | 'status';
export type SortDirection = 'asc' | 'desc';

export interface JobDto {
  id: string;
  status: JobStatus;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  customer: { id: string; name: string; address: string } | null;
  assignedTechnician: { id: string; name: string } | null;
  createdAt: string;
}

/** Shape returned by Spring's Page<JobDto>. */
export interface JobsPageResponse {
  content: JobDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Full job detail shape returned by `GET /api/v1/jobs/{id}`.
 * TO-DO: update `customer`/`assignedTechnician` when backend returns nested objects.
 */
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
  /** Enriched — present only when backend includes it (TO-DO). */
  customer?: { id: string; name: string; address: string } | null;
  /** Enriched — present only when backend includes it (TO-DO). */
  assignedTechnician?: { id: string; name: string } | null;
}

export interface JobNoteResponse {
  id: string;
  jobId: string;
  authorUserId: string;
  body: string;
  createdAt: string;
}

/** Shape returned by GET /api/v1/jobs/{id}/photos (PRD §14.2; TASK-123). */
export interface JobPhotoResponse {
  id: string;
  jobId: string;
  objectKey: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  downloadUrl: string | null;
}

export interface JobsQueryParams {
  status?: JobStatus;
  customerId?: string;
  assignedTechnicianId?: string;
  scheduledFrom?: string;
  scheduledTo?: string;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}
