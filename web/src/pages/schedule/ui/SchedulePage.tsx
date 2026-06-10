import { useState, useMemo } from 'react';
import { Calendar, dateFnsLocalizer, type View } from 'react-big-calendar';
import withDragAndDrop from 'react-big-calendar/lib/addons/dragAndDrop';
import type { EventInteractionArgs } from 'react-big-calendar/lib/addons/dragAndDrop';
import { format, parse, startOfWeek, getDay, addHours } from 'date-fns';
import { enUS } from 'date-fns/locale';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import 'react-big-calendar/lib/addons/dragAndDrop/styles.css';

import { useJobs, updateJobSchedule } from '@/entities/job/api/jobsApi';
import type { JobDto, JobStatus, JobsPageResponse, JobsQueryParams } from '@/entities/job/model/types';
import { useTenantSettings } from '@/entities/tenant/api/tenantApi';
import { DEFAULT_TENANT_TIMEZONE } from '@/entities/tenant/model/types';
import { utcToZonedDate, zonedDateToUtcIso } from '@/shared/lib/timezone';
import { Button } from '@/shared/ui/button';
import { cn } from '@/shared/lib/utils';

// ── date-fns localizer ────────────────────────────────────────────────────────

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek: () => startOfWeek(new Date(), { weekStartsOn: 0 }),
  getDay,
  locales: { 'en-US': enUS },
});

// ── DnD-enhanced calendar ─────────────────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const DnDCalendar = withDragAndDrop<CalendarEvent>(Calendar as any);

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<JobStatus, { label: string; bg: string; text: string }> = {
  SUBMITTED:            { label: 'Submitted',              bg: 'bg-gray-200',   text: 'text-gray-800' },
  ASSIGNED:             { label: 'Assigned',               bg: 'bg-blue-200',   text: 'text-blue-900' },
  IN_PROGRESS:          { label: 'In Progress',            bg: 'bg-amber-200',  text: 'text-amber-900' },
  PENDING:              { label: 'Pending',                bg: 'bg-orange-200', text: 'text-orange-900' },
  DONE_PENDING_APPROVAL:{ label: 'Done — Pending Approval',bg: 'bg-purple-200', text: 'text-purple-900' },
  COMPLETED:            { label: 'Completed',              bg: 'bg-green-200',  text: 'text-green-900' },
  CANCELED:             { label: 'Canceled',               bg: 'bg-red-200',    text: 'text-red-900' },
};

// ── Types ─────────────────────────────────────────────────────────────────────

interface CalendarEvent {
  id: string;
  title: string;
  start: Date;
  end: Date;
  status: JobStatus;
  resource: JobDto;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function getDateWindow(date: Date, view: View): { scheduledFrom: string; scheduledTo: string } {
  const d = new Date(date);

  if (view === 'month') {
    const from = new Date(d.getFullYear(), d.getMonth() - 1, 1);
    const to   = new Date(d.getFullYear(), d.getMonth() + 2, 0);
    return {
      scheduledFrom: from.toISOString().slice(0, 10),
      scheduledTo:   to.toISOString().slice(0, 10),
    };
  }

  if (view === 'week') {
    const day  = d.getDay();
    const from = new Date(d);
    from.setDate(d.getDate() - day - 1);
    const to = new Date(d);
    to.setDate(d.getDate() + (6 - day) + 1);
    return {
      scheduledFrom: from.toISOString().slice(0, 10),
      scheduledTo:   to.toISOString().slice(0, 10),
    };
  }

  // day
  return {
    scheduledFrom: d.toISOString().slice(0, 10),
    scheduledTo:   d.toISOString().slice(0, 10),
  };
}

// ── Event render component ────────────────────────────────────────────────────

function EventComponent({ event }: { event: CalendarEvent }) {
  const { label } = STATUS_CONFIG[event.status] ?? STATUS_CONFIG.SUBMITTED;
  return (
    <span className="block truncate text-xs font-medium leading-tight px-1">
      <span className="font-semibold">{label}</span>
      {' — '}
      {event.title}
    </span>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

const VIEWS: View[] = ['day', 'week', 'month'];
const VIEW_LABELS: Record<View, string> = { day: 'Day', week: 'Week', month: 'Month', work_week: 'Work Week', agenda: 'Agenda' };

export default function SchedulePage() {
  const queryClient = useQueryClient();
  const { data: tenantSettings } = useTenantSettings();
  const tenantTimezone = tenantSettings?.timezone ?? DEFAULT_TENANT_TIMEZONE;
  const [view, setView] = useState<View>('week');
  const [date, setDate] = useState(new Date());
  const [shakingIds, setShakingIds] = useState<ReadonlySet<string>>(new Set());

  const { scheduledFrom, scheduledTo } = getDateWindow(date, view);

  const queryParams: JobsQueryParams = {
    scheduledFrom,
    scheduledTo,
    size: 500,
  };

  const { data, isLoading } = useJobs(queryParams);

  const events = useMemo<CalendarEvent[]>(
    () =>
      (data?.content ?? [])
        .filter((job) => job.scheduledStart !== null)
        .map((job) => ({
          id: job.id,
          title: job.customer?.name ?? 'Unnamed Job',
          start: utcToZonedDate(job.scheduledStart!, tenantTimezone),
          end: job.scheduledEnd
            ? utcToZonedDate(job.scheduledEnd, tenantTimezone)
            : addHours(utcToZonedDate(job.scheduledStart!, tenantTimezone), 1),
          status: job.status,
          resource: job,
        })),
    [data, tenantTimezone],
  );

  async function handleEventDrop({ event, start, end }: EventInteractionArgs<CalendarEvent>) {
    const startISO = zonedDateToUtcIso(start as Date, tenantTimezone);
    const endISO = zonedDateToUtcIso(end as Date, tenantTimezone);
    const original = event.resource;

    // Optimistic update — apply immediately so the UI feels instant (AC3)
    queryClient.setQueryData(
      ['jobs', queryParams],
      (old: JobsPageResponse | undefined) => {
        if (!old) return old;
        return {
          ...old,
          content: old.content.map((j) =>
            j.id === original.id
              ? { ...j, scheduledStart: startISO, scheduledEnd: endISO }
              : j,
          ),
        };
      },
    );

    try {
      await updateJobSchedule(original.id, startISO, endISO);
    } catch {
      // Rollback — restore original slot (AC4)
      queryClient.setQueryData(
        ['jobs', queryParams],
        (old: JobsPageResponse | undefined) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((j) => (j.id === original.id ? original : j)),
          };
        },
      );

      // Shake animation (AC4)
      setShakingIds((prev) => new Set([...prev, original.id]));
      setTimeout(() => {
        setShakingIds((prev) => {
          const next = new Set(prev);
          next.delete(original.id);
          return next;
        });
      }, 600);

      toast.error('Failed to reschedule job. The original time slot has been restored.');
    }
  }

  return (
    <div className="flex flex-col h-full font-sans space-y-3">
      {/* ── Header ── */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-brand-navy">Schedule</h1>

        {/* View tabs (AC1) */}
        <div className="flex gap-1" role="tablist" aria-label="Calendar view">
          {VIEWS.map((v) => (
            <Button
              key={v}
              role="tab"
              aria-selected={view === v}
              variant={view === v ? 'default' : 'outline'}
              size="sm"
              onClick={() => setView(v)}
              data-testid={`view-tab-${v}`}
            >
              {VIEW_LABELS[v]}
            </Button>
          ))}
        </div>
      </div>

      {/* ── Loading indicator ── */}
      {isLoading && (
        <div className="text-sm text-brand-muted animate-pulse" aria-live="polite">
          Loading jobs…
        </div>
      )}

      {/* ── Calendar ── */}
      <div className="flex-1 min-h-0 rounded-lg border border-border overflow-hidden bg-brand-surface">
        <DnDCalendar
          localizer={localizer}
          events={events}
          view={view}
          date={date}
          onView={setView}
          onNavigate={setDate}
          onEventDrop={handleEventDrop}
          resizable={false}
          draggableAccessor={() => true}
          style={{ height: '100%' }}
          eventPropGetter={(event) => {
            const { bg, text } = STATUS_CONFIG[event.status] ?? STATUS_CONFIG.SUBMITTED;
            return {
              className: cn(
                bg,
                text,
                'rounded border-0 px-1',
                shakingIds.has(event.id) && 'animate-shake',
              ),
              'aria-label': `${STATUS_CONFIG[event.status]?.label ?? event.status}: ${event.title}`,
            };
          }}
          components={{ event: EventComponent }}
          aria-label="Job schedule calendar"
        />
      </div>
    </div>
  );
}
