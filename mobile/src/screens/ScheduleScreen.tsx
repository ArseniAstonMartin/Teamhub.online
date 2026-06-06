import React, { useState } from 'react';
import {
  FlatList,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { colors } from '../theme/colors';
import { useScheduleJobs } from '../api/jobsApi';
import type { JobResponse } from '../api/jobsApi';
import JobCard from '../components/JobCard';
import SharedPoolCard from '../components/SharedPoolCard';
import AcceptJobSheet from '../components/AcceptJobSheet';

type ViewMode = 'day' | 'week';

// ─── Date helpers ─────────────────────────────────────────────────────────────

function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

function endOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(23, 59, 59, 999);
  return d;
}

function addDays(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}

function toIso(date: Date): string {
  return date.toISOString().replace('Z', '+00:00');
}

function formatDayLabel(date: Date): string {
  return date.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
}

function formatWeekDayShort(date: Date): string {
  return date.toLocaleDateString([], { weekday: 'short', day: 'numeric' });
}

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function getWeekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

// ─── Job sorting/bucketing ────────────────────────────────────────────────────

function sortJobs(jobs: JobResponse[]): JobResponse[] {
  return [...jobs].sort((a, b) => {
    if (!a.scheduledStart && !b.scheduledStart) return 0;
    if (!a.scheduledStart) return 1;
    if (!b.scheduledStart) return -1;
    return new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime();
  });
}

function jobsForDay(jobs: JobResponse[], date: Date): JobResponse[] {
  return jobs.filter((j) => {
    if (!j.scheduledStart) return false;
    return isSameDay(new Date(j.scheduledStart), date);
  });
}

function unscheduledJobs(jobs: JobResponse[]): JobResponse[] {
  return jobs.filter((j) => !j.scheduledStart);
}

// ─── Sub-components ───────────────────────────────────────────────────────────

interface DayJobListProps {
  jobs: JobResponse[];
  onPressSharedPool: (job: JobResponse) => void;
}

function DayJobList({ jobs, onPressSharedPool }: DayJobListProps) {
  const scheduled = sortJobs(jobs.filter((j) => j.scheduledStart));
  const unscheduled = unscheduledJobs(jobs);

  if (scheduled.length === 0 && unscheduled.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No jobs for this day</Text>
      </View>
    );
  }

  return (
    <>
      {scheduled.map((job) =>
        job.status === 'SUBMITTED' ? (
          <SharedPoolCard key={job.id} job={job} onPress={() => onPressSharedPool(job)} />
        ) : (
          <JobCard key={job.id} job={job} />
        ),
      )}
      {unscheduled.length > 0 && (
        <>
          <Text style={styles.sectionLabel}>Unscheduled</Text>
          {unscheduled.map((job) =>
            job.status === 'SUBMITTED' ? (
              <SharedPoolCard key={job.id} job={job} onPress={() => onPressSharedPool(job)} />
            ) : (
              <JobCard key={job.id} job={job} />
            ),
          )}
        </>
      )}
    </>
  );
}

// ─── Main screen ──────────────────────────────────────────────────────────────

export default function ScheduleScreen() {
  const [viewMode, setViewMode] = useState<ViewMode>('day');
  const [selectedDate, setSelectedDate] = useState<Date>(startOfDay(new Date()));
  const [sheetJob, setSheetJob] = useState<JobResponse | null>(null);
  const [sheetVisible, setSheetVisible] = useState(false);

  const queryClient = useQueryClient();

  const weekStart =
    viewMode === 'week' ? getWeekStart(selectedDate) : startOfDay(selectedDate);
  const weekDays = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));

  const fromDate = viewMode === 'day' ? startOfDay(selectedDate) : weekStart;
  const toDate = viewMode === 'day' ? endOfDay(selectedDate) : endOfDay(weekDays[6]);

  const { data: jobs = [], isFetching, refetch } = useScheduleJobs(
    toIso(fromDate),
    toIso(toDate),
  );

  function navigate(dir: -1 | 1) {
    setSelectedDate((prev) => addDays(prev, dir * (viewMode === 'week' ? 7 : 1)));
  }

  function openSheet(job: JobResponse) {
    setSheetJob(job);
    setSheetVisible(true);
  }

  function handleClaimed(claimed: JobResponse) {
    queryClient.setQueryData(
      ['scheduleJobs', toIso(fromDate), toIso(toDate)],
      (old: JobResponse[] | undefined) =>
        (old ?? []).map((j) => (j.id === claimed.id ? claimed : j)),
    );
  }

  function handleJobClaimed(jobId: string) {
    queryClient.setQueryData(
      ['scheduleJobs', toIso(fromDate), toIso(toDate)],
      (old: JobResponse[] | undefined) => (old ?? []).filter((j) => j.id !== jobId),
    );
    setSheetVisible(false);
  }

  const today = startOfDay(new Date());

  return (
    <View style={styles.container}>
      {/* ── Header ──────────────────────────────────────────────────────── */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Schedule</Text>
        <View style={styles.toggle}>
          <TouchableOpacity
            style={[styles.toggleBtn, viewMode === 'day' && styles.toggleBtnActive]}
            onPress={() => setViewMode('day')}
            accessibilityRole="button"
            accessibilityState={{ selected: viewMode === 'day' }}
          >
            <Text style={[styles.toggleBtnText, viewMode === 'day' && styles.toggleBtnTextActive]}>
              Day
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.toggleBtn, viewMode === 'week' && styles.toggleBtnActive]}
            onPress={() => setViewMode('week')}
            accessibilityRole="button"
            accessibilityState={{ selected: viewMode === 'week' }}
          >
            <Text
              style={[styles.toggleBtnText, viewMode === 'week' && styles.toggleBtnTextActive]}
            >
              Week
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* ── Navigation bar ──────────────────────────────────────────────── */}
      <View style={styles.navBar}>
        <TouchableOpacity
          style={styles.navArrow}
          onPress={() => navigate(-1)}
          accessibilityRole="button"
          accessibilityLabel="Previous"
        >
          <Text style={styles.navArrowText}>‹</Text>
        </TouchableOpacity>

        {viewMode === 'day' ? (
          <Text style={styles.navLabel}>{formatDayLabel(selectedDate)}</Text>
        ) : (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.weekStrip}
          >
            {weekDays.map((day) => {
              const isSelected = isSameDay(day, selectedDate);
              const isToday = isSameDay(day, today);
              return (
                <TouchableOpacity
                  key={day.toISOString()}
                  style={[styles.dayChip, isSelected && styles.dayChipSelected]}
                  onPress={() => setSelectedDate(startOfDay(day))}
                  accessibilityRole="button"
                  accessibilityState={{ selected: isSelected }}
                >
                  <Text
                    style={[
                      styles.dayChipText,
                      isSelected && styles.dayChipTextSelected,
                      isToday && !isSelected && styles.dayChipTextToday,
                    ]}
                  >
                    {formatWeekDayShort(day)}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        )}

        <TouchableOpacity
          style={styles.navArrow}
          onPress={() => navigate(1)}
          accessibilityRole="button"
          accessibilityLabel="Next"
        >
          <Text style={styles.navArrowText}>›</Text>
        </TouchableOpacity>
      </View>

      {/* ── Job list ────────────────────────────────────────────────────── */}
      {viewMode === 'day' ? (
        <FlatList
          data={[1]}
          keyExtractor={() => 'day-list'}
          renderItem={() => (
            <DayJobList jobs={jobs} onPressSharedPool={openSheet} />
          )}
          refreshControl={
            <RefreshControl
              refreshing={isFetching}
              onRefresh={refetch}
              tintColor={colors.primary}
            />
          }
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
        />
      ) : (
        <ScrollView
          refreshControl={
            <RefreshControl
              refreshing={isFetching}
              onRefresh={refetch}
              tintColor={colors.primary}
            />
          }
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
        >
          <DayJobList
            jobs={jobsForDay(jobs, selectedDate)}
            onPressSharedPool={openSheet}
          />
        </ScrollView>
      )}

      {/* ── Accept sheet ────────────────────────────────────────────────── */}
      <AcceptJobSheet
        job={sheetJob}
        visible={sheetVisible}
        onClose={() => setSheetVisible(false)}
        onClaimed={handleClaimed}
        onJobClaimed={handleJobClaimed}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 8,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: colors.text,
  },
  toggle: {
    flexDirection: 'row',
    backgroundColor: colors.card,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  toggleBtn: {
    paddingHorizontal: 16,
    paddingVertical: 6,
  },
  toggleBtnActive: {
    backgroundColor: colors.primary,
  },
  toggleBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.subtext,
  },
  toggleBtnTextActive: {
    color: '#FFFFFF',
  },
  navBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  navArrow: {
    width: 40,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 4,
  },
  navArrowText: {
    fontSize: 28,
    color: colors.primary,
    lineHeight: 30,
  },
  navLabel: {
    flex: 1,
    textAlign: 'center',
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  weekStrip: {
    flexGrow: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
    gap: 4,
  },
  dayChip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    minWidth: 52,
    alignItems: 'center',
  },
  dayChipSelected: {
    backgroundColor: colors.primary,
  },
  dayChipText: {
    fontSize: 12,
    fontWeight: '500',
    color: colors.subtext,
  },
  dayChipTextSelected: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  dayChipTextToday: {
    color: colors.primary,
    fontWeight: '700',
  },
  listContent: {
    paddingTop: 8,
    paddingBottom: 24,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingTop: 48,
  },
  emptyText: {
    fontSize: 15,
    color: colors.subtext,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.subtext,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginTop: 16,
    marginBottom: 4,
    marginHorizontal: 16,
  },
});
