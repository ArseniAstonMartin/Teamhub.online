import { useState, useMemo, type FormEvent } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';
import {
  ChevronLeft,
  Clock,
  UserCheck,
  Wrench,
  PauseCircle,
  CheckCircle,
  CheckCircle2,
  XCircle,
  AlertCircle,
  ImageIcon,
  History,
  Upload,
} from 'lucide-react';

import { formatDistanceToNow } from 'date-fns';
import { useJob, useJobNotes, useJobPhotos, useJobStatusHistory, createNote, fireTransition } from '@/entities/job/api/jobsApi';
import type { JobNoteResponse, JobPhotoResponse, JobResponse, JobStatus, JobStatusHistoryResponse } from '@/entities/job/model/types';
import { TRANSITIONS_FROM, type TransitionAction } from '@/entities/job/model/transitions';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from '@/shared/ui/dialog';
import { cn } from '@/shared/lib/utils';

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<JobStatus, { label: string; Icon: React.ElementType; className: string }> = {
  SUBMITTED:             { label: 'Submitted',              Icon: Clock,         className: 'bg-gray-100   text-gray-700   border-gray-200' },
  ASSIGNED:              { label: 'Assigned',               Icon: UserCheck,     className: 'bg-blue-100   text-blue-700   border-blue-200' },
  IN_PROGRESS:           { label: 'In Progress',            Icon: Wrench,        className: 'bg-amber-100  text-amber-700  border-amber-200' },
  PENDING:               { label: 'Pending',                Icon: PauseCircle,   className: 'bg-orange-100 text-orange-700 border-orange-200' },
  DONE_PENDING_APPROVAL: { label: 'Done — Pending Approval',Icon: CheckCircle,   className: 'bg-purple-100 text-purple-700 border-purple-200' },
  COMPLETED:             { label: 'Completed',              Icon: CheckCircle2,  className: 'bg-green-100  text-green-700  border-green-200' },
  CANCELED:              { label: 'Canceled',               Icon: XCircle,       className: 'bg-red-100    text-red-700    border-red-200' },
};

/** Maps backend enum names to the frontend JobStatus union. */
function normalizeStatus(raw: string | null | undefined): JobStatus {
  if (raw === 'ASSIGNED_TO_TECHNICIAN') return 'ASSIGNED';
  if (raw && raw in STATUS_CONFIG) return raw as JobStatus;
  return 'SUBMITTED';
}

interface TimelineStep {
  status: JobStatus;
  createdAt: string;
  note: string | null;
}

function buildTimelineSteps(
  history: JobStatusHistoryResponse[],
  jobCreatedAt: string,
): TimelineStep[] {
  if (history.length === 0) return [];

  const steps: TimelineStep[] = [];
  const first = history[0];
  if (first.fromStatus) {
    steps.push({
      status: normalizeStatus(first.fromStatus),
      createdAt: jobCreatedAt,
      note: null,
    });
  }
  for (const entry of history) {
    steps.push({
      status: normalizeStatus(entry.toStatus),
      createdAt: entry.createdAt,
      note: entry.transitionNote,
    });
  }
  return steps;
}

// ── Sub-components ────────────────────────────────────────────────────────────

function StatusTimeline({
  history,
  jobCreatedAt,
}: {
  history: JobStatusHistoryResponse[];
  jobCreatedAt: string;
}) {
  const steps = buildTimelineSteps(history, jobCreatedAt);

  if (steps.length === 0) {
    return (
      <p className="text-xs text-brand-muted" data-testid="status-history-empty">
        No status changes recorded yet.
      </p>
    );
  }

  return (
    <ol className="relative space-y-0" data-testid="status-history-timeline">
      {steps.map((step, index) => {
        const { label, Icon } = STATUS_CONFIG[step.status] ?? STATUS_CONFIG.SUBMITTED;
        const isLast = index === steps.length - 1;
        return (
          <li
            key={`${step.status}-${step.createdAt}-${index}`}
            className="relative flex gap-3 pb-4 last:pb-0"
            data-testid={`status-timeline-step-${step.status}`}
          >
            {!isLast && (
              <span
                className="absolute left-[11px] top-6 bottom-0 w-px bg-border"
                aria-hidden="true"
              />
            )}
            <span
              className={cn(
                'relative z-10 flex size-6 shrink-0 items-center justify-center rounded-full border bg-brand-surface',
                isLast ? 'border-brand-blue text-brand-blue' : 'border-border text-brand-muted',
              )}
              aria-hidden="true"
            >
              <Icon className="size-3.5" />
            </span>
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="text-sm font-medium text-brand-navy">{label}</p>
              <p className="text-xs text-brand-muted">
                {formatDistanceToNow(new Date(step.createdAt), { addSuffix: true })}
              </p>
              {step.note && (
                <p className="mt-1 text-xs text-brand-muted italic">{step.note}</p>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function StatusBadge({ status }: { status: JobStatus }) {
  const { label, Icon, className } = STATUS_CONFIG[status] ?? STATUS_CONFIG.SUBMITTED;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-semibold',
        className,
      )}
      aria-label={label}
      role="status"
    >
      <Icon className="size-3 shrink-0" aria-hidden="true" />
      <span>{status === 'DONE_PENDING_APPROVAL' ? 'Done — Approval' : label}</span>
    </span>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-6 animate-pulse" data-testid="job-loading-skeleton" aria-busy="true">
      <div className="h-8 w-64 rounded bg-gray-200" />
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6">
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-5 rounded bg-gray-200" />
          ))}
        </div>
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-5 rounded bg-gray-200" />
          ))}
        </div>
      </div>
    </div>
  );
}

function formatDt(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function formatNoteTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function JobDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const [noteText, setNoteText] = useState('');
  const [isSubmittingNote, setIsSubmittingNote] = useState(false);
  const [pendingTransition, setPendingTransition] = useState<string | null>(null);
  const [lightboxPhoto, setLightboxPhoto] = useState<JobPhotoResponse | null>(null);

  const { data: job, isLoading: jobLoading, isError: jobError } = useJob(id);
  const { data: notes = [], isLoading: notesLoading } = useJobNotes(id);
  const { data: photos = [], isLoading: photosLoading } = useJobPhotos(id);
  const { data: statusHistory = [], isLoading: statusHistoryLoading } = useJobStatusHistory(id);

  // ── Action buttons — only allowed role+state combos (AC2) ────────────────

  const availableActions = useMemo<TransitionAction[]>(() => {
    if (!job || !user) return [];
    return (TRANSITIONS_FROM[job.status] ?? []).filter((a) =>
      a.roles.includes(user.role),
    );
  }, [job, user]);

  // ── Note composer visibility (AC3) ─────────────────────────────────────

  const canAddNote =
    user && job && (
      user.role === 'ADMIN' ||
      user.role === 'DISPATCHER' ||
      (user.role === 'TECHNICIAN' && job.assignedTechnicianId === user.id)
    );

  // ── Handlers ─────────────────────────────────────────────────────────────

  async function handleTransition(action: TransitionAction) {
    if (!job) return;

    if (!action.endpoint) {
      toast.info('This action is not yet available. Please contact your administrator.');
      return;
    }

    const originalStatus = job.status;
    setPendingTransition(action.label);

    // Optimistic status update (AC5)
    queryClient.setQueryData(['job', id], (old: JobResponse | undefined) => {
      if (!old) return old;
      return { ...old, status: action.toStatus };
    });

    try {
      const body = action.endpoint === 'cancel' ? { reason: 'OTHER' } : undefined;
      await fireTransition(job.id, action.endpoint, body);
      await queryClient.invalidateQueries({ queryKey: ['job', id] });
    } catch (err) {
      // Rollback on error — handles 409 conflicts (AC5)
      queryClient.setQueryData(['job', id], (old: JobResponse | undefined) => {
        if (!old) return old;
        return { ...old, status: originalStatus };
      });
      const axiosErr = err as AxiosError<{ error: { message: string } }>;
      toast.error(
        axiosErr.response?.data?.error?.message ??
        'Transition failed. The status has been restored.',
      );
    } finally {
      setPendingTransition(null);
    }
  }

  async function handleNoteSubmit(e: FormEvent) {
    e.preventDefault();
    const body = noteText.trim();
    if (!body || !user || !job || isSubmittingNote) return;

    // Optimistic note prepend (AC3) — notes are newest-first
    const optimisticNote: JobNoteResponse = {
      id: `temp-${Date.now()}`,
      jobId: job.id,
      authorUserId: user.id,
      body,
      createdAt: new Date().toISOString(),
    };

    queryClient.setQueryData(
      ['job-notes', id],
      (old: JobNoteResponse[] | undefined) => [optimisticNote, ...(old ?? [])],
    );
    setNoteText('');
    setIsSubmittingNote(true);

    try {
      await createNote(job.id, body);
      await queryClient.invalidateQueries({ queryKey: ['job-notes', id] });
    } catch {
      // Rollback optimistic note
      queryClient.setQueryData(
        ['job-notes', id],
        (old: JobNoteResponse[] | undefined) =>
          (old ?? []).filter((n) => n.id !== optimisticNote.id),
      );
      setNoteText(body);
      toast.error('Failed to save note. Please try again.');
    } finally {
      setIsSubmittingNote(false);
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  if (jobLoading) return (
    <div className="space-y-4 font-sans p-2">
      <Link to="/jobs" className="inline-flex items-center gap-1 text-sm text-brand-muted hover:text-brand-navy">
        <ChevronLeft className="size-4" aria-hidden="true" /> Back to Jobs
      </Link>
      <DetailSkeleton />
    </div>
  );

  if (jobError || !job) return (
    <div className="space-y-4 font-sans p-2">
      <Link to="/jobs" className="inline-flex items-center gap-1 text-sm text-brand-muted hover:text-brand-navy">
        <ChevronLeft className="size-4" aria-hidden="true" /> Back to Jobs
      </Link>
      <div className="flex flex-col items-center gap-3 py-20 text-destructive" role="alert">
        <AlertCircle className="size-10 opacity-60" aria-hidden="true" />
        <p className="text-sm font-medium">Failed to load job details.</p>
        <Button variant="outline" size="sm" asChild>
          <Link to="/jobs">Back to Jobs</Link>
        </Button>
      </div>
    </div>
  );

  const customerName =
    job.customer?.name ??
    (job.customerId ? `Customer #${job.customerId.slice(0, 8)}` : 'No customer');
  // TO-DO: fetch assignee name from user service when backend returns only IDs
  const technicianName =
    job.assignedTechnician?.name ??
    (job.assignedTechnicianId ? `Tech #${job.assignedTechnicianId.slice(0, 8)}` : 'Unassigned');

  return (
    <div className="space-y-5 font-sans">
      {/* ── Back link ── */}
      <Link
        to="/jobs"
        className="inline-flex items-center gap-1 text-sm text-brand-muted hover:text-brand-navy transition-colors"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
        Back to Jobs
      </Link>

      {/* ── Header (AC1) ── */}
      <div
        className="flex flex-wrap items-start justify-between gap-4 rounded-lg border border-border bg-brand-surface p-5"
        data-testid="job-header"
      >
        <div className="space-y-1.5">
          <h1 className="text-xl font-bold text-brand-navy">{customerName}</h1>
          <StatusBadge status={job.status} />
          {job.pendingReason && (
            <p className="text-xs text-brand-muted">
              Pending reason: <span className="font-medium">{job.pendingReason}</span>
            </p>
          )}
          {job.cancellationReason && (
            <p className="text-xs text-destructive">
              Cancelled: <span className="font-medium">{job.cancellationReason}</span>
            </p>
          )}
        </div>

        {/* Action buttons — hidden (not rendered) for disallowed role+state combos (AC2) */}
        {availableActions.length > 0 && (
          <div className="flex flex-wrap gap-2" role="group" aria-label="Job actions">
            {availableActions.map((action) => (
              <Button
                key={action.toStatus}
                size="sm"
                variant={action.endpoint ? 'default' : 'outline'}
                disabled={pendingTransition !== null}
                aria-busy={pendingTransition === action.label}
                data-testid={`action-${action.toStatus}`}
                onClick={() => { void handleTransition(action); }}
              >
                {pendingTransition === action.label ? 'Working…' : action.label}
              </Button>
            ))}
          </div>
        )}
      </div>

      {/* ── Body + Sidebar grid (AC1) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-5">

        {/* ── MAIN BODY ── */}
        <div className="space-y-5">

          {/* Custom fields */}
          {Object.keys(job.customFields ?? {}).length > 0 && (
            <section
              className="rounded-lg border border-border bg-brand-surface p-4 space-y-2"
              aria-label="Custom fields"
            >
              <h2 className="text-sm font-semibold text-brand-navy">Custom Fields</h2>
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                {Object.entries(job.customFields).map(([key, value]) => (
                  <>
                    <dt key={`dt-${key}`} className="text-brand-muted">{key}</dt>
                    <dd key={`dd-${key}`} className="text-brand-navy font-medium">{String(value)}</dd>
                  </>
                ))}
              </dl>
            </section>
          )}

          {/* Notes (AC3) */}
          <section
            className="rounded-lg border border-border bg-brand-surface p-4 space-y-4"
            aria-label="Notes"
          >
            <h2 className="text-sm font-semibold text-brand-navy">Notes</h2>

            {/* Note composer — only for ADMIN, DISPATCHER, or assigned TECHNICIAN */}
            {canAddNote && (
              <form onSubmit={(e) => { void handleNoteSubmit(e); }} className="space-y-2" data-testid="note-composer">
                <label htmlFor="note-input" className="sr-only">Add a note</label>
                <textarea
                  id="note-input"
                  value={noteText}
                  onChange={(e) => setNoteText(e.target.value)}
                  placeholder="Write a note…"
                  rows={3}
                  className={cn(
                    'w-full rounded-md border border-input bg-background px-3 py-2 text-sm',
                    'placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
                    'resize-none',
                  )}
                  aria-label="Note text"
                />
                <div className="flex justify-end">
                  <Button
                    type="submit"
                    size="sm"
                    disabled={!noteText.trim() || isSubmittingNote}
                    aria-busy={isSubmittingNote}
                  >
                    {isSubmittingNote ? 'Saving…' : 'Add Note'}
                  </Button>
                </div>
              </form>
            )}

            {/* Notes list */}
            {notesLoading && (
              <div className="space-y-2 animate-pulse">
                {[1, 2].map((i) => <div key={i} className="h-10 rounded bg-gray-100" />)}
              </div>
            )}
            {!notesLoading && notes.length === 0 && (
              <p className="text-sm text-brand-muted">No notes yet.</p>
            )}
            {!notesLoading && notes.length > 0 && (
              <ul className="space-y-3" aria-label="Notes list">
                {notes.map((note) => (
                  <li
                    key={note.id}
                    className="rounded-md bg-gray-50 border border-border px-3 py-2.5 space-y-1"
                    data-testid={`note-${note.id}`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-brand-navy truncate">
                        {note.authorUserId.slice(0, 8)}
                      </span>
                      <time className="text-xs text-brand-muted whitespace-nowrap" dateTime={note.createdAt}>
                        {formatNoteTime(note.createdAt)}
                      </time>
                    </div>
                    <p className="text-sm text-brand-navy">{note.body}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Photos (PRD §14.2; TASK-123) */}
          <section
            className="rounded-lg border border-border bg-brand-surface p-4 space-y-4"
            aria-label="Photos"
            data-testid="photos-section"
          >
            <h2 className="text-sm font-semibold text-brand-navy">Photos</h2>

            {photosLoading && (
              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 animate-pulse" data-testid="photos-loading">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="aspect-square rounded-md bg-gray-100" />
                ))}
              </div>
            )}

            {!photosLoading && photos.length === 0 && (
              <div
                className="flex flex-col items-center gap-2 py-8 text-center"
                data-testid="photos-empty"
              >
                <Upload className="size-8 text-gray-300" aria-hidden="true" />
                <p className="text-sm text-brand-muted">No photos yet</p>
              </div>
            )}

            {!photosLoading && photos.length > 0 && (
              <ul
                className="grid grid-cols-3 sm:grid-cols-4 gap-2"
                aria-label="Photo thumbnails"
                data-testid="photos-grid"
              >
                {photos.map((photo) => (
                  <li key={photo.id}>
                    <button
                      type="button"
                      className="group relative aspect-square w-full overflow-hidden rounded-md border border-border bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      data-testid={`photo-thumb-${photo.id}`}
                      aria-label="View photo"
                      onClick={() => setLightboxPhoto(photo)}
                    >
                      {photo.downloadUrl ? (
                        <img
                          src={photo.downloadUrl}
                          alt=""
                          className="size-full object-cover transition-transform group-hover:scale-105"
                          loading="lazy"
                        />
                      ) : (
                        <div className="flex size-full items-center justify-center">
                          <ImageIcon className="size-6 text-gray-300" aria-hidden="true" />
                        </div>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <Dialog
            open={lightboxPhoto !== null}
            onOpenChange={(open) => { if (!open) setLightboxPhoto(null); }}
          >
            <DialogContent
              className="max-w-4xl border-none bg-transparent p-2 shadow-none sm:max-w-5xl"
              data-testid="photo-lightbox"
            >
              <DialogTitle className="sr-only">Photo preview</DialogTitle>
              {lightboxPhoto?.downloadUrl && (
                <img
                  src={lightboxPhoto.downloadUrl}
                  alt=""
                  className="mx-auto max-h-[85vh] w-auto max-w-full rounded-md object-contain"
                  data-testid="photo-lightbox-image"
                />
              )}
            </DialogContent>
          </Dialog>
        </div>

        {/* ── SIDEBAR ── */}
        <aside className="space-y-4" aria-label="Job details sidebar">

          {/* Schedule */}
          <section className="rounded-lg border border-border bg-brand-surface p-4 space-y-2.5">
            <h2 className="text-sm font-semibold text-brand-navy">Schedule</h2>
            <dl className="text-sm space-y-1.5">
              <div className="flex justify-between gap-2">
                <dt className="text-brand-muted">Start</dt>
                <dd className="text-brand-navy font-medium text-right">{formatDt(job.scheduledStart)}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-brand-muted">End</dt>
                <dd className="text-brand-navy font-medium text-right">{formatDt(job.scheduledEnd)}</dd>
              </div>
              {job.actualStart && (
                <div className="flex justify-between gap-2">
                  <dt className="text-brand-muted">Actual start</dt>
                  <dd className="text-brand-navy font-medium text-right">{formatDt(job.actualStart)}</dd>
                </div>
              )}
              {job.actualEnd && (
                <div className="flex justify-between gap-2">
                  <dt className="text-brand-muted">Actual end</dt>
                  <dd className="text-brand-navy font-medium text-right">{formatDt(job.actualEnd)}</dd>
                </div>
              )}
            </dl>
          </section>

          {/* Assignee */}
          <section className="rounded-lg border border-border bg-brand-surface p-4 space-y-2">
            <h2 className="text-sm font-semibold text-brand-navy">Assignee</h2>
            <p className="text-sm text-brand-navy">{technicianName}</p>
            {job.customerPhone && (
              <p className="text-sm text-brand-muted">
                Customer phone:{' '}
                <a href={`tel:${job.customerPhone}`} className="text-brand-blue hover:underline">
                  {job.customerPhone}
                </a>
              </p>
            )}
          </section>

          {/* Status history timeline (PRD §9; TASK-124) */}
          <section
            className="rounded-lg border border-border bg-brand-surface p-4 space-y-3"
            data-testid="status-history"
          >
            <div className="flex items-center gap-1.5">
              <History className="size-3.5 text-brand-muted" aria-hidden="true" />
              <h2 className="text-sm font-semibold text-brand-navy">Status History</h2>
            </div>
            {statusHistoryLoading ? (
              <div className="space-y-3 animate-pulse" data-testid="status-history-loading">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="h-10 rounded bg-gray-200" />
                ))}
              </div>
            ) : (
              <StatusTimeline history={statusHistory} jobCreatedAt={job.createdAt} />
            )}
          </section>

          {/* Metadata */}
          <section className="rounded-lg border border-border bg-brand-surface p-4 space-y-1.5">
            <h2 className="text-sm font-semibold text-brand-navy">Metadata</h2>
            <dl className="text-xs text-brand-muted space-y-1">
              <div className="flex justify-between">
                <dt>Created</dt>
                <dd>{formatDt(job.createdAt)}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Updated</dt>
                <dd>{formatDt(job.updatedAt)}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Job ID</dt>
                <dd className="font-mono">{job.id.slice(0, 8)}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
    </div>
  );
}
