import { useState } from 'react';
import {
  DragDropContext,
  Droppable,
  Draggable,
  type DropResult,
} from '@hello-pangea/dnd';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';
import {
  Clock,
  UserCheck,
  Wrench,
  PauseCircle,
  CheckCircle,
  CheckCircle2,
  XCircle,
  UserCircle,
} from 'lucide-react';

import { useJobs, fireTransition } from '@/entities/job/api/jobsApi';
import type { JobDto, JobStatus, JobsPageResponse, JobsQueryParams } from '@/entities/job/model/types';
import {
  ALLOWED_TRANSITIONS,
  TRANSITION_ENDPOINT,
  isTransitionAllowedForRole,
  type TransitionKey,
} from '@/entities/job/model/transitions';
import { useAuthStore } from '@/entities/user/model/authStore';
// Re-export for backward compatibility — tests import these from KanbanView.
export { ALLOWED_TRANSITIONS, TRANSITION_ENDPOINT };
import { cn } from '@/shared/lib/utils';

// ── Column definitions ────────────────────────────────────────────────────────

export const KANBAN_COLUMNS: Array<{
  status: JobStatus;
  label: string;
  headerClass: string;
  Icon: React.ElementType;
}> = [
  { status: 'SUBMITTED',             label: 'Submitted',        headerClass: 'bg-gray-100   text-gray-700',   Icon: Clock },
  { status: 'ASSIGNED',              label: 'Assigned',         headerClass: 'bg-blue-100   text-blue-700',   Icon: UserCheck },
  { status: 'IN_PROGRESS',           label: 'In Progress',      headerClass: 'bg-amber-100  text-amber-700',  Icon: Wrench },
  { status: 'PENDING',               label: 'Pending',          headerClass: 'bg-orange-100 text-orange-700', Icon: PauseCircle },
  { status: 'DONE_PENDING_APPROVAL', label: 'Done — Approval',  headerClass: 'bg-purple-100 text-purple-700', Icon: CheckCircle },
  { status: 'COMPLETED',             label: 'Completed',        headerClass: 'bg-green-100  text-green-700',  Icon: CheckCircle2 },
  { status: 'CANCELED',              label: 'Canceled',         headerClass: 'bg-red-100    text-red-700',    Icon: XCircle },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

// ── Job Card ──────────────────────────────────────────────────────────────────

function JobCard({ job, isShaking }: { job: JobDto; isShaking: boolean }) {
  const assignee = job.assignedTechnician;
  const initials = assignee?.name?.charAt(0).toUpperCase() ?? '?';

  return (
    <div
      className={cn(
        'bg-white rounded-lg border border-border p-3 shadow-sm space-y-1.5 cursor-grab select-none',
        isShaking && 'animate-shake',
      )}
    >
      {/* Customer info */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-sm text-brand-navy truncate leading-tight">
            {job.customer?.name ?? '—'}
          </p>
          {job.customer?.address && (
            <p className="text-xs text-brand-muted truncate leading-tight">
              {job.customer.address}
            </p>
          )}
        </div>

        {/* Assignee avatar */}
        {assignee ? (
          <div
            className="shrink-0 size-6 rounded-full bg-brand-blue text-white text-[10px] flex items-center justify-center font-bold leading-none"
            aria-label={`Assigned to ${assignee.name}`}
            title={assignee.name}
          >
            {initials}
          </div>
        ) : (
          <UserCircle
            className="shrink-0 size-6 text-gray-300"
            aria-label="Unassigned"
          />
        )}
      </div>

      {/* Scheduled time */}
      {job.scheduledStart && (
        <p className="text-xs text-brand-muted">
          {formatDate(job.scheduledStart)}
        </p>
      )}
    </div>
  );
}

// ── Kanban Board ──────────────────────────────────────────────────────────────

const KANBAN_QUERY_PARAMS: JobsQueryParams = { size: 200 };

export default function KanbanView() {
  const queryClient = useQueryClient();
  const [shakingIds, setShakingIds] = useState<ReadonlySet<string>>(new Set());
  const userRole = useAuthStore((s) => s.user?.role);

  const { data, isLoading } = useJobs(KANBAN_QUERY_PARAMS);

  // Group jobs by status
  const grouped: Partial<Record<JobStatus, JobDto[]>> = {};
  for (const job of data?.content ?? []) {
    (grouped[job.status] ??= []).push(job);
  }

  async function handleDragEnd(result: DropResult) {
    const { draggableId: jobId, source, destination } = result;
    if (!destination || destination.droppableId === source.droppableId) return;

    const fromStatus = source.droppableId as JobStatus;
    const toStatus   = destination.droppableId as JobStatus;
    const key: TransitionKey = `${fromStatus}→${toStatus}`;

    // ── AC2: client-side state machine guard ─────────────────────────────────
    if (!ALLOWED_TRANSITIONS.has(key)) {
      toast.error(
        `Cannot move a job from "${fromStatus}" to "${toStatus}". This transition is not allowed.`,
      );
      return;
    }

    if (!userRole || !isTransitionAllowedForRole(key, userRole)) {
      toast.error('You do not have permission to perform this transition.');
      return;
    }

    const endpoint = TRANSITION_ENDPOINT[key];
    if (!endpoint) {
      toast.info('This transition is not yet available in Kanban. Please use the job detail view.');
      return;
    }

    const job = data?.content.find((j) => j.id === jobId);
    if (!job) return;

    // ── Optimistic update (AC1) ───────────────────────────────────────────────
    queryClient.setQueryData(
      ['jobs', KANBAN_QUERY_PARAMS],
      (old: JobsPageResponse | undefined) => {
        if (!old) return old;
        return {
          ...old,
          content: old.content.map((j) =>
            j.id === jobId ? { ...j, status: toStatus } : j,
          ),
        };
      },
    );

    try {
      const body = endpoint === 'cancel' ? { reason: 'OTHER' } : undefined;
      await fireTransition(jobId, endpoint, body);
    } catch (err) {
      // ── Rollback on backend rejection (AC1 + AC2 backend path) ───────────
      queryClient.setQueryData(
        ['jobs', KANBAN_QUERY_PARAMS],
        (old: JobsPageResponse | undefined) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((j) => (j.id === jobId ? job : j)),
          };
        },
      );

      // Shake animation
      setShakingIds((prev) => new Set([...prev, jobId]));
      setTimeout(() => {
        setShakingIds((prev) => {
          const next = new Set(prev);
          next.delete(jobId);
          return next;
        });
      }, 600);

      const axiosErr = err as AxiosError<{ error: { message: string } }>;
      const msg =
        axiosErr.response?.data?.error?.message ??
        'Failed to update job status. The card has been moved back.';
      toast.error(msg);
    }
  }

  if (isLoading) {
    return (
      <div className="animate-pulse text-sm text-brand-muted py-8" aria-live="polite">
        Loading jobs…
      </div>
    );
  }

  return (
    <DragDropContext onDragEnd={handleDragEnd}>
      {/* Horizontally scrollable board */}
      <div
        role="region"
        aria-label="Job Kanban board"
        className="flex gap-3 overflow-x-auto pb-4 min-h-[400px]"
      >
        {KANBAN_COLUMNS.map(({ status, label, headerClass, Icon }) => {
          const colJobs = grouped[status] ?? [];
          return (
            <div key={status} className="flex flex-col min-w-[220px] w-56 shrink-0">
              {/* Column header */}
              <div
                className={cn(
                  'flex items-center gap-1.5 rounded-t-lg px-3 py-2 text-sm font-semibold',
                  headerClass,
                )}
              >
                <Icon className="size-3.5 shrink-0" aria-hidden="true" />
                <span className="truncate">{label}</span>
                <span className="ml-auto font-normal text-xs opacity-70" aria-label={`${colJobs.length} jobs`}>
                  {colJobs.length}
                </span>
              </div>

              {/* Droppable column body */}
              <Droppable droppableId={status}>
                {(provided, snapshot) => (
                  <div
                    ref={provided.innerRef}
                    {...provided.droppableProps}
                    aria-label={`${label} column — ${colJobs.length} job${colJobs.length !== 1 ? 's' : ''}`}
                    className={cn(
                      'flex-1 min-h-[160px] rounded-b-lg border border-t-0 border-border p-2 space-y-2 bg-gray-50 transition-colors',
                      snapshot.isDraggingOver && 'bg-brand-blue/5',
                    )}
                  >
                    {colJobs.map((job, index) => (
                      <Draggable key={job.id} draggableId={job.id} index={index}>
                        {(prov, snap) => (
                          <div
                            ref={prov.innerRef}
                            {...prov.draggableProps}
                            {...prov.dragHandleProps}
                            aria-label={`Job card: ${job.customer?.name ?? 'Unnamed'}, ${label}. Drag or press Space to move.`}
                            data-testid={`kanban-card-${job.id}`}
                            className={cn(snap.isDragging && 'rotate-1 shadow-lg')}
                          >
                            <JobCard job={job} isShaking={shakingIds.has(job.id)} />
                          </div>
                        )}
                      </Draggable>
                    ))}
                    {provided.placeholder}
                  </div>
                )}
              </Droppable>
            </div>
          );
        })}
      </div>
    </DragDropContext>
  );
}
