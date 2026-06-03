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

export interface JobsQueryParams {
  status?: JobStatus;
  assignedTechnicianId?: string;
  scheduledFrom?: string;
  scheduledTo?: string;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}
