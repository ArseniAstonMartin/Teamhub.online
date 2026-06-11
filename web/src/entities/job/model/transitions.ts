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
  // COMPLETED→* admin re-open deferred MVP — PRD §9.4 (no backend endpoint yet)
]);

/** Named backend endpoint for each allowed transition. */
export const TRANSITION_ENDPOINT: Partial<Record<TransitionKey, string>> = {
  'SUBMITTED→ASSIGNED':                'claim',
  'ASSIGNED→IN_PROGRESS':              'start',
  'IN_PROGRESS→PENDING':               'pending',
  'IN_PROGRESS→DONE_PENDING_APPROVAL': 'done-pending-approval',
  'IN_PROGRESS→COMPLETED':             'complete',
  'DONE_PENDING_APPROVAL→COMPLETED':   'complete',
  'SUBMITTED→CANCELED':                'cancel',
  'ASSIGNED→CANCELED':                 'cancel',
  'IN_PROGRESS→CANCELED':              'cancel',
  'PENDING→CANCELED':                  'cancel',
  'DONE_PENDING_APPROVAL→CANCELED':    'cancel',
  'CANCELED→SUBMITTED':                'reopen',
};

/** A single rendered action button on the Job Detail page. */
export interface TransitionAction {
  toStatus: JobStatus;
  label: string;
  /** Backend endpoint slug, or null when deferred (e.g. COMPLETED re-open — PRD §9.4). */
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
    { toStatus: 'CANCELED',  label: 'Cancel', endpoint: 'cancel', roles: ['DISPATCHER', 'ADMIN'] },
  ],
  ASSIGNED: [
    { toStatus: 'IN_PROGRESS', label: 'Start',  endpoint: 'start', roles: ['TECHNICIAN'] },
    { toStatus: 'CANCELED',    label: 'Cancel', endpoint: 'cancel', roles: ['DISPATCHER', 'ADMIN'] },
  ],
  IN_PROGRESS: [
    { toStatus: 'PENDING',               label: 'Mark Pending',            endpoint: 'pending',               roles: ['TECHNICIAN'] },
    { toStatus: 'DONE_PENDING_APPROVAL', label: 'Done — Awaiting Approval', endpoint: 'done-pending-approval', roles: ['TECHNICIAN'] },
    { toStatus: 'COMPLETED',             label: 'Complete',                endpoint: 'complete',              roles: ['TECHNICIAN'] },
    { toStatus: 'CANCELED',             label: 'Cancel',                   endpoint: 'cancel',               roles: ['DISPATCHER', 'ADMIN'] },
  ],
  PENDING: [
    { toStatus: 'CANCELED', label: 'Cancel', endpoint: 'cancel', roles: ['DISPATCHER', 'ADMIN'] },
  ],
  DONE_PENDING_APPROVAL: [
    { toStatus: 'COMPLETED', label: 'Complete', endpoint: 'complete', roles: ['DISPATCHER', 'ADMIN'] },
    { toStatus: 'CANCELED',  label: 'Cancel',   endpoint: 'cancel',  roles: ['DISPATCHER', 'ADMIN'] },
  ],
  CANCELED: [
    { toStatus: 'SUBMITTED', label: 'Re-open', endpoint: 'reopen', roles: ['ADMIN'] },
  ],
  // COMPLETED has no outbound transitions — admin re-open deferred MVP (PRD §9.4)
};

/** Whether the given role may perform a client-allowed transition (Kanban guard). */
export function isTransitionAllowedForRole(key: TransitionKey, role: UserRole): boolean {
  const [from, to] = key.split('→') as [JobStatus, JobStatus];
  const actions = TRANSITIONS_FROM[from] ?? [];
  return actions.some((a) => a.toStatus === to && a.roles.includes(role));
}
