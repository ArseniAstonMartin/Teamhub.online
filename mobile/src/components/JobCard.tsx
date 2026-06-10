import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { colors } from '../theme/colors';
import type { JobResponse, JobStatus } from '../api/jobsApi';

const STATUS_LABELS: Record<JobStatus, string> = {
  SUBMITTED: 'Submitted',
  ASSIGNED_TO_TECHNICIAN: 'Assigned',
  IN_PROGRESS: 'In Progress',
  PENDING: 'Pending',
  DONE_PENDING_APPROVAL: 'Done — Pending Approval',
  COMPLETED: 'Completed',
  CANCELED: 'Canceled',
};

const STATUS_COLORS: Record<JobStatus, string> = {
  SUBMITTED: colors.statusSubmitted,
  ASSIGNED_TO_TECHNICIAN: colors.statusAssigned,
  IN_PROGRESS: colors.statusInProgress,
  PENDING: colors.statusPending,
  DONE_PENDING_APPROVAL: colors.statusDonePending,
  COMPLETED: colors.statusCompleted,
  CANCELED: colors.statusCanceled,
};

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getServiceLabel(job: JobResponse): string {
  const cf = job.customFields;
  if (cf && typeof cf['serviceType'] === 'string') return cf['serviceType'] as string;
  if (cf && typeof cf['service_type'] === 'string') return cf['service_type'] as string;
  return 'Job';
}

interface Props {
  job: JobResponse;
  onPress?: () => void;
}

export default function JobCard({ job, onPress }: Props) {
  const statusColor = STATUS_COLORS[job.status];
  const timeLabel =
    job.scheduledStart
      ? `${formatTime(job.scheduledStart)}${job.scheduledEnd ? `–${formatTime(job.scheduledEnd)}` : ''}`
      : 'Unscheduled';

  return (
    <TouchableOpacity
      style={[styles.card, { borderLeftColor: statusColor }]}
      onPress={onPress}
      activeOpacity={0.75}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`${getServiceLabel(job)}, ${STATUS_LABELS[job.status]}, ${timeLabel}`}
    >
      <View style={styles.row}>
        <View style={[styles.dot, { backgroundColor: statusColor }]} />
        <Text style={styles.status}>{STATUS_LABELS[job.status]}</Text>
      </View>
      <Text style={styles.service}>{getServiceLabel(job)}</Text>
      <Text style={styles.time}>{timeLabel}</Text>
      {job.customerPhone ? (
        <Text style={styles.phone}>{job.customerPhone}</Text>
      ) : null}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.card,
    borderRadius: 10,
    borderLeftWidth: 4,
    padding: 12,
    marginVertical: 4,
    marginHorizontal: 16,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  status: {
    fontSize: 11,
    fontWeight: '600',
    color: colors.subtext,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  service: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
    marginBottom: 2,
  },
  time: {
    fontSize: 13,
    color: colors.subtext,
  },
  phone: {
    fontSize: 13,
    color: colors.primary,
    marginTop: 4,
  },
});
