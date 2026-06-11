/**
 * In-memory API fixture factories used by Playwright mocks.
 * These are plain JS objects — no imports from src/ so the e2e layer
 * stays decoupled from application internals.
 */

export type JobStatus =
  | 'SUBMITTED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'PENDING'
  | 'DONE_PENDING_APPROVAL'
  | 'COMPLETED'
  | 'CANCELED';

// ── Job fixtures ─────────────────────────────────────────────────────────────

export interface JobDto {
  id: string;
  status: JobStatus;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  customerId: string | null;
  customerName: string | null;
  assignedTechnicianId: string | null;
  assigneeName?: string | null;
  createdAt: string;
}

export interface JobsPage {
  content: JobDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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
  customerName?: string | null;
  assigneeName?: string | null;
}

export function makeJobDto(id: string, status: JobStatus = 'SUBMITTED'): JobDto {
  return {
    id,
    status,
    scheduledStart: '2026-06-15T09:00:00Z',
    scheduledEnd: '2026-06-15T11:00:00Z',
    customerId: 'cust-1',
    customerName: 'Acme Corp',
    assignedTechnicianId: null,
    assigneeName: null,
    createdAt: '2026-06-10T08:00:00Z',
  };
}

export function makeJobResponse(id: string, status: JobStatus = 'SUBMITTED'): JobResponse {
  return {
    id,
    tenantId: 'tenant-456',
    customerId: 'cust-1',
    assignedTechnicianId: null,
    appointmentSchemaId: 'schema-1',
    status,
    inProgressSubstatus: null,
    pendingReason: null,
    cancellationReason: null,
    customFields: {},
    lat: null,
    lon: null,
    scheduledStart: '2026-06-15T09:00:00Z',
    scheduledEnd: '2026-06-15T11:00:00Z',
    actualStart: null,
    actualEnd: null,
    createdAt: '2026-06-10T08:00:00Z',
    updatedAt: '2026-06-10T08:00:00Z',
    archivedAt: null,
    customerPhone: null,
    customerName: 'Acme Corp',
    assigneeName: null,
  };
}

export function makeJobsPage(jobs: JobDto[] = []): JobsPage {
  return {
    content: jobs,
    totalElements: jobs.length,
    totalPages: jobs.length > 0 ? 1 : 0,
    number: 0,
    size: 20,
  };
}

// ── Customer fixtures ────────────────────────────────────────────────────────

export interface CustomerDto {
  id: string;
  tenantId: string;
  name: string;
  companyName: string | null;
  phone: string;
  email: string | null;
  addressLine: string;
  city: string;
  state: string;
  zip: string;
  lat: number | null;
  lon: number | null;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
}

export interface CustomersPage {
  content: CustomerDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export function makeCustomer(overrides: Partial<CustomerDto> = {}): CustomerDto {
  return {
    id: 'cust-1',
    tenantId: 'tenant-456',
    name: 'Alice Smith',
    companyName: 'Acme Corp',
    phone: '+15035551234',
    email: 'alice@example.com',
    addressLine: '123 Main St',
    city: 'Portland',
    state: 'OR',
    zip: '97201',
    lat: 45.5,
    lon: -122.6,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    archivedAt: null,
    ...overrides,
  };
}

export function makeCustomersPage(customers: CustomerDto[] = []): CustomersPage {
  return {
    content: customers,
    totalElements: customers.length,
    totalPages: customers.length > 0 ? 1 : 0,
    number: 0,
    size: 8,
  };
}

// ── Schema fixture ───────────────────────────────────────────────────────────

export function makeSchema(id: string = 'schema-1') {
  return {
    id,
    name: 'Service Request',
    version: 1,
    active: true,
    fieldDefinitions: [
      {
        key: 'service_type',
        label: 'Service Type',
        type: 'text',
        required: true,
        order: 1,
        options: null,
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
      {
        key: 'priority',
        label: 'Priority',
        type: 'dropdown',
        required: false,
        order: 2,
        options: ['Low', 'Medium', 'High'],
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
    ],
    createdAt: '2026-01-01T00:00:00Z',
  };
}

// ── Note fixture ─────────────────────────────────────────────────────────────

export interface NoteResponse {
  id: string;
  jobId: string;
  authorUserId: string;
  body: string;
  createdAt: string;
}

export function makeNote(id: string, jobId: string, body: string, authorUserId: string = 'user-123'): NoteResponse {
  return {
    id,
    jobId,
    authorUserId,
    body,
    createdAt: new Date().toISOString(),
  };
}
