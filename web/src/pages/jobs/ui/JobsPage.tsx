import { useRef, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Clock,
  UserCheck,
  Wrench,
  PauseCircle,
  CheckCircle,
  CheckCircle2,
  XCircle,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
  SearchX,
  List,
  Columns,
} from 'lucide-react';

import KanbanView from './KanbanView';

import { useJobs } from '@/entities/job/api/jobsApi';
import type { JobDto, JobStatus } from '@/entities/job/model/types';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import { cn } from '@/shared/lib/utils';

// ── Status config (color + icon + text — never color alone) ──────────────────

const STATUS_CONFIG: Record<
  JobStatus,
  { label: string; Icon: React.ElementType; className: string }
> = {
  SUBMITTED: {
    label: 'Submitted',
    Icon: Clock,
    className: 'bg-gray-100 text-gray-700 border-gray-200',
  },
  ASSIGNED: {
    label: 'Assigned',
    Icon: UserCheck,
    className: 'bg-blue-100 text-blue-700 border-blue-200',
  },
  IN_PROGRESS: {
    label: 'In Progress',
    Icon: Wrench,
    className: 'bg-amber-100 text-amber-700 border-amber-200',
  },
  PENDING: {
    label: 'Pending',
    Icon: PauseCircle,
    className: 'bg-orange-100 text-orange-700 border-orange-200',
  },
  DONE_PENDING_APPROVAL: {
    label: 'Done — Pending Approval',
    Icon: CheckCircle,
    className: 'bg-purple-100 text-purple-700 border-purple-200',
  },
  COMPLETED: {
    label: 'Completed',
    Icon: CheckCircle2,
    className: 'bg-green-100 text-green-700 border-green-200',
  },
  CANCELED: {
    label: 'Canceled',
    Icon: XCircle,
    className: 'bg-red-100 text-red-700 border-red-200',
  },
};

// ── Sub-components ────────────────────────────────────────────────────────────

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
      <span>{label}</span>
    </span>
  );
}

function SkeletonCell({ className }: { className?: string }) {
  return <div className={cn('h-4 rounded bg-gray-200 animate-pulse', className)} />;
}

function SkeletonRow() {
  return (
    <tr aria-hidden="true" data-testid="skeleton-row">
      <td className="px-4 py-3"><SkeletonCell className="w-28" /></td>
      <td className="px-4 py-3"><SkeletonCell className="w-32" /></td>
      <td className="px-4 py-3"><SkeletonCell className="w-24" /></td>
      <td className="px-4 py-3"><SkeletonCell className="w-28" /></td>
      <td className="px-4 py-3"><SkeletonCell className="w-24" /></td>
    </tr>
  );
}

function EmptyState() {
  return (
    <tr>
      <td colSpan={5}>
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-brand-muted">
          <SearchX className="size-8 opacity-40" aria-hidden="true" />
          <p className="text-sm">No jobs found. Try adjusting your filters.</p>
        </div>
      </td>
    </tr>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <tr>
      <td colSpan={5}>
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-destructive">
          <AlertCircle className="size-8 opacity-60" aria-hidden="true" />
          <p className="text-sm">Failed to load jobs.</p>
          <Button variant="outline" size="sm" onClick={onRetry}>
            Try again
          </Button>
        </div>
      </td>
    </tr>
  );
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// ── Main page ─────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20;
const DEFAULT_SORT = 'scheduledStart,desc';

const STATUS_OPTIONS: Array<{ value: JobStatus | 'all'; label: string }> = [
  { value: 'all', label: 'All statuses' },
  { value: 'SUBMITTED', label: 'Submitted' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'DONE_PENDING_APPROVAL', label: 'Done — Pending Approval' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELED', label: 'Canceled' },
];

const SORT_OPTIONS = [
  { value: 'scheduledStart,desc', label: 'Scheduled ↓' },
  { value: 'scheduledStart,asc', label: 'Scheduled ↑' },
  { value: 'createdAt,desc', label: 'Created ↓' },
  { value: 'createdAt,asc', label: 'Created ↑' },
  { value: 'status,asc', label: 'Status A–Z' },
];

export default function JobsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  // View mode: 'list' | 'kanban' — URL-synced
  const viewMode = searchParams.get('view') ?? 'list';

  // Derive filter state from URL
  const q = searchParams.get('q') ?? '';
  const statusParam = (searchParams.get('status') as JobStatus) || undefined;
  const sort = searchParams.get('sort') ?? DEFAULT_SORT;
  const scheduledFrom = searchParams.get('scheduledFrom') ?? undefined;
  const scheduledTo = searchParams.get('scheduledTo') ?? undefined;
  const page = Math.max(0, Number(searchParams.get('page') ?? '0'));

  // Uncontrolled input — `key={q}` resets it whenever the URL q changes
  const searchRef = useRef<HTMLInputElement>(null);

  const queryParams = {
    q: q || undefined,
    status: statusParam,
    sort,
    scheduledFrom,
    scheduledTo,
    page,
    size: PAGE_SIZE,
  };

  const { data, isLoading, isFetching, isError, refetch } = useJobs(queryParams);

  // ── URL param helpers ────────────────────────────────────────────────────────

  function setParam(key: string, value: string | null | undefined) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value && value !== 'all') {
        next.set(key, value);
      } else {
        next.delete(key);
      }
      if (key !== 'page') next.delete('page');
      return next;
    });
  }

  function handlePageChange(newPage: number) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (newPage === 0) {
        next.delete('page');
      } else {
        next.set('page', String(newPage));
      }
      return next;
    });
  }

  function handleSearchSubmit(e: FormEvent) {
    e.preventDefault();
    setParam('q', searchRef.current?.value || null);
  }

  function handleClearFilters() {
    setSearchParams(new URLSearchParams());
  }

  const hasFilters = !!(q || statusParam || scheduledFrom || scheduledTo);
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const showSkeleton = isLoading;
  const jobs: JobDto[] = data?.content ?? [];

  return (
    <div className="space-y-4 font-sans">
      {/* ── Header ── */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-2xl font-semibold text-brand-navy">Jobs</h1>

        <div className="flex items-center gap-3">
          {isFetching && !isLoading && (
            <span className="text-xs text-brand-muted animate-pulse">Refreshing…</span>
          )}

          {/* List / Kanban toggle */}
          <div className="flex gap-1" role="group" aria-label="View mode">
            <Button
              variant={viewMode === 'list' ? 'default' : 'outline'}
              size="sm"
              aria-pressed={viewMode === 'list'}
              data-testid="view-toggle-list"
              onClick={() => setParam('view', 'list')}
            >
              <List className="size-4 mr-1" aria-hidden="true" />
              List
            </Button>
            <Button
              variant={viewMode === 'kanban' ? 'default' : 'outline'}
              size="sm"
              aria-pressed={viewMode === 'kanban'}
              data-testid="view-toggle-kanban"
              onClick={() => setParam('view', 'kanban')}
            >
              <Columns className="size-4 mr-1" aria-hidden="true" />
              Kanban
            </Button>
          </div>
        </div>
      </div>

      {/* ── Filter bar ── */}
      <div className="flex flex-wrap gap-2 items-end">
        {/* Search */}
        <form onSubmit={handleSearchSubmit} className="flex gap-1.5" role="search">
          <label htmlFor="job-search" className="sr-only">Search jobs</label>
          <Input
            key={q}
            ref={searchRef}
            id="job-search"
            type="search"
            placeholder="Search…"
            defaultValue={q}
            className="w-44"
          />
          <Button type="submit" variant="outline" size="sm">
            Search
          </Button>
        </form>

        {/* Status filter */}
        <div>
          <label htmlFor="status-filter" className="sr-only">Filter by status</label>
          <Select
            value={statusParam ?? 'all'}
            onValueChange={(val) => setParam('status', val)}
          >
            <SelectTrigger id="status-filter" className="w-44" aria-label="Filter by status">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Sort */}
        <div>
          <label htmlFor="sort-select" className="sr-only">Sort jobs</label>
          <Select value={sort} onValueChange={(val) => setParam('sort', val)}>
            <SelectTrigger id="sort-select" className="w-44" aria-label="Sort jobs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SORT_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Date range */}
        <div className="flex items-center gap-1.5">
          <label htmlFor="date-from" className="text-xs text-brand-muted whitespace-nowrap">
            From
          </label>
          <Input
            id="date-from"
            type="date"
            value={scheduledFrom ?? ''}
            onChange={(e) => setParam('scheduledFrom', e.target.value || null)}
            className="w-36 text-sm"
            aria-label="Scheduled from date"
          />
          <label htmlFor="date-to" className="text-xs text-brand-muted whitespace-nowrap">
            To
          </label>
          <Input
            id="date-to"
            type="date"
            value={scheduledTo ?? ''}
            onChange={(e) => setParam('scheduledTo', e.target.value || null)}
            className="w-36 text-sm"
            aria-label="Scheduled to date"
          />
        </div>

        {/* Clear */}
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={handleClearFilters}>
            Clear filters
          </Button>
        )}
      </div>

      {/* ── Kanban view ── */}
      {viewMode === 'kanban' && <KanbanView />}

      {/* ── Table + Pagination (list view only) ── */}
      {viewMode !== 'kanban' && (
        <>
          <div className="rounded-lg border border-border overflow-hidden bg-brand-surface">
            <table className="w-full text-sm">
              <caption className="sr-only">Job list</caption>
              <thead>
                <tr className="border-b border-border bg-gray-50 text-brand-muted text-left text-xs font-medium uppercase tracking-wide">
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Customer</th>
                  <th scope="col" className="px-4 py-3">Technician</th>
                  <th scope="col" className="px-4 py-3">Scheduled</th>
                  <th scope="col" className="px-4 py-3">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {showSkeleton &&
                  Array.from({ length: PAGE_SIZE }).map((_, i) => <SkeletonRow key={i} />)}

                {!showSkeleton && isError && (
                  <ErrorState onRetry={() => { void refetch(); }} />
                )}

                {!showSkeleton && !isError && jobs.length === 0 && <EmptyState />}

                {!showSkeleton && !isError && jobs.map((job) => (
                  <tr
                    key={job.id}
                    className="hover:bg-gray-50 transition-colors"
                  >
                    <td className="px-4 py-3">
                      <StatusBadge status={job.status} />
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-medium text-brand-navy">
                        {job.customer?.name ?? '—'}
                      </p>
                      {job.customer?.address && (
                        <p className="text-xs text-brand-muted">{job.customer.address}</p>
                      )}
                    </td>
                    <td className="px-4 py-3 text-brand-navy">
                      {job.assignedTechnician?.name ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-brand-muted tabular-nums">
                      {formatDateTime(job.scheduledStart)}
                    </td>
                    <td className="px-4 py-3 text-brand-muted tabular-nums">
                      {formatDateTime(job.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {!isLoading && !isError && totalPages > 0 && (
            <div className="flex items-center justify-between text-sm text-brand-muted">
              <span>
                {totalElements} job{totalElements !== 1 ? 's' : ''} total
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => handlePageChange(page - 1)}
                  aria-label="Previous page"
                >
                  <ChevronLeft className="size-4" aria-hidden="true" />
                  Previous
                </Button>
                <span aria-live="polite" aria-atomic="true">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => handlePageChange(page + 1)}
                  aria-label="Next page"
                >
                  Next
                  <ChevronRight className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
