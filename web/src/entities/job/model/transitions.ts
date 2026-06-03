/**
 * Client-side mirror of the backend job FSM.
 * Shared between KanbanView and JobDetailPage.
 */
import type { JobStatus } from './types';
import type { UserRole } from '@/entities/user/model/authStore';

export type TransitionKey = `${JobStatus}→${JobStatus}`;

/**
 * Every client-allowed transition.
 * Drags / buttons NOT in this set are rejected immediately without an API call.
 */
export const ALLOWED_TRANSITIONS = new Set<TransitionKey>([
  'SUBMITTED→ASSIGNED',
  'ASSIGNED→IN_PROGRESS',
  'IN_PROGRESS→PENDING',
  'IN_PROGRESS→DONE_PENDING_APPROVAL',
  'IN_PROGRESS→COMPLETED',
  'DONE_PENDING_APPROVAL→COMPLETED',
  'SUBMITTED→CANCELED',
  'ASSIGNED→CANCELED',
  'IN_PROGRESS→CANCELED',
  'PENDING→CANCELED',
  'DONE_PENDING_APPROVAL→CANCELED',
  'CANCELED→SUBMITTED',
  // COMPLETED→* is Admin-only re-open — TO-DO: add when backend supports it
]);

/**
 * Named backend endpoint for each allowed transition.
 * Entries absent from this map are "allowed but not yet wired" (TO-DO).
 * Cancel / re-open transitions have no dedicated endpoint yet.
 */
export const TRANSITION_ENDPOINT: Partial<Record<TransitionKey, string>> = {
  'SUBMITTED→ASSIGNED':                'claim',
  'ASSIGNED→IN_PROGRESS':              'start',
  'IN_PROGRESS→PENDING':               'pending',
  'IN_PROGRESS→DONE_PENDING_APPROVAL': 'done-pending-approval',
  'IN_PROGRESS→COMPLETED':             'complete',
  'DONE_PENDING_APPROVAL→COMPLETED':   'complete',
  // TO-DO: cancel/reopen — no dedicated backend endpoint yet (PRD §9.4)
};

/** A single rendered action button on the Job Detail page. */
export interface TransitionAction {
  toStatus: JobStatus;
  label: string;
  /** Backend endpoint slug, or null if the endpoint is not yet available. */
  endpoint: string | null;
  /** Roles that may perform this transition. */
  roles: UserRole[];
}

/**
 * Available outbound transitions per source status.
 * Used by JobDetailPage to render role-filtered action buttons (AC2).
 */
export const TRANSITIONS_FROM: Partial<Record<JobStatus, TransitionAction[]>> = {
  SUBMITTED: [
    { toStatus: 'ASSIGNED',  label: 'Claim',  endpoint: 'claim', roles: ['TECHNICIAN', 'DISPATCHER', 'ADMIN'] },
    { toStatus: 'CANCELED',  label: 'Cancel', endpoint: null,    roles: ['DISPATCHER', 'ADMIN'] },
  ],
  ASSIGNED: [
    { toStatus: 'IN_PROGRESS', label: 'Start',  endpoint: 'start', roles: ['TECHNICIAN'] },
    { toStatus: 'CANCELED',    label: 'Cancel', endpoint: null,    roles: ['DISPATCHER', 'ADMIN'] },
  ],
  IN_PROGRESS: [
    { toStatus: 'PENDING',               label: 'Mark Pending',            endpoint: 'pending',               roles: ['TECHNICIAN'] },
    { toStatus: 'DONE_PENDING_APPROVAL', label: 'Done — Awaiting Approval', endpoint: 'done-pending-approval', roles: ['TECHNICIAN'] },
    { toStatus: 'COMPLETED',             label: 'Complete',                endpoint: 'complete',              roles: ['TECHNICIAN'] },
    { toStatus: 'CANCELED',             label: 'Cancel',                   endpoint: null,                   roles: ['DISPATCHER', 'ADMIN'] },
  ],
  PENDING: [
    { toStatus: 'CANCELED', label: 'Cancel', endpoint: null, roles: ['DISPATCHER', 'ADMIN'] },
  ],
  DONE_PENDING_APPROVAL: [
    { toStatus: 'COMPLETED', label: 'Complete', endpoint: 'complete', roles: ['DISPATCHER', 'ADMIN'] },
    { toStatus: 'CANCELED',  label: 'Cancel',   endpoint: null,      roles: ['DISPATCHER', 'ADMIN'] },
  ],
  CANCELED: [
    { toStatus: 'SUBMITTED', label: 'Re-open', endpoint: null, roles: ['DISPATCHER', 'ADMIN'] },
  ],
  // COMPLETED has no outbound transitions client-side yet (admin re-open is TO-DO)
};
