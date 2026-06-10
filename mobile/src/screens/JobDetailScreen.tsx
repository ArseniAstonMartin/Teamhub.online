import React, { useCallback, useEffect, useState } from 'react';
import {
  ActionSheetIOS,
  ActivityIndicator,
  Alert,
  Animated,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { openMaps, copyAddress } from '../utils/openMaps';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import { useQueryClient } from '@tanstack/react-query';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import {
  useJob,
  useJobNotes,
  startJob,
  markDonePendingApproval,
  completeJob,
  type JobStatus,
  type JobResponse,
} from '../api/jobsApi';
import { useTenantSettings } from '../api/tenantApi';
import { createShakeAnimation } from '../utils/shake';
import DefineEtaSheet from '../components/DefineEtaSheet';
import PendingSheet from '../components/PendingSheet';
import NotesList from '../components/NotesList';
import type { NoteItem } from '../components/NotesList';
import { getPendingNotes } from '../db/notesQueue';
import { useOfflineNoteSync } from '../hooks/useOfflineNoteSync';

type NavProp = NativeStackNavigationProp<RootStackParamList, 'JobDetail'>;
type RouteP = RouteProp<RootStackParamList, 'JobDetail'>;

// ─── Helpers ──────────────────────────────────────────────────────────────────

const STATUS_LABEL: Record<JobStatus, string> = {
  SUBMITTED: 'Submitted',
  ASSIGNED_TO_TECHNICIAN: 'Assigned',
  IN_PROGRESS: 'In Progress',
  PENDING: 'Pending',
  DONE_PENDING_APPROVAL: 'Done · Pending Approval',
  COMPLETED: 'Completed',
  CANCELED: 'Canceled',
};

const STATUS_COLOR: Record<JobStatus, string> = {
  SUBMITTED: colors.statusSubmitted,
  ASSIGNED_TO_TECHNICIAN: colors.statusAssigned,
  IN_PROGRESS: colors.statusInProgress,
  PENDING: colors.statusPending,
  DONE_PENDING_APPROVAL: colors.statusDonePending,
  COMPLETED: colors.statusCompleted,
  CANCELED: colors.statusCanceled,
};

function shortId(id: string) {
  return id.slice(0, 8).toUpperCase();
}

function snakeToTitle(key: string) {
  return key
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}

function formatDate(iso: string | null) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

function formatTime(iso: string | null) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function formatElapsed(s: number) {
  return [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60]
    .map((n) => String(n).padStart(2, '0'))
    .join(':');
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function JobDetailScreen() {
  const route = useRoute<RouteP>();
  const navigation = useNavigation<NavProp>();
  const jobId: string = route.params?.jobId ?? '';
  const queryClient = useQueryClient();

  const { data: job, isLoading, isError } = useJob(jobId);
  const { data: notes = [] } = useJobNotes(jobId);
  const { data: tenantSettings } = useTenantSettings();

  // Offline sync — fires when connectivity is restored
  useOfflineNoteSync(jobId);

  const [localJob, setLocalJob] = useState<JobResponse | null>(null);
  const displayJob = localJob ?? job ?? null;

  const [shakeAnim] = useState(() => new Animated.Value(0));

  // Sheets
  const [showEtaSheet, setShowEtaSheet] = useState(false);
  const [showPendingSheet, setShowPendingSheet] = useState(false);

  // Per-button loading states
  const [startingJob, setStartingJob] = useState(false);
  const [markingDone, setMarkingDone] = useState(false);
  const [completingJob, setCompletingJob] = useState(false);

  // In-progress timer
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (displayJob?.status !== 'IN_PROGRESS' || !displayJob.actualStart) return;
    const start = new Date(displayJob.actualStart).getTime();
    const interval = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 1000);
    return () => clearInterval(interval);
  }, [displayJob?.status, displayJob?.actualStart]);

  // ─── Optimistic transition helper ─────────────────────────────────────────

  const handleTransition = useCallback(
    async (
      action: () => Promise<JobResponse>,
      optimisticJob: Partial<JobResponse>,
      setLoading: (v: boolean) => void,
    ) => {
      const previous = localJob ?? job ?? null;
      setLocalJob((prev) => ({ ...(prev ?? job)!, ...optimisticJob }));
      setLoading(true);
      try {
        const updated = await action();
        setLocalJob(updated);
        queryClient.setQueryData(['job', jobId], updated);
      } catch (err: unknown) {
        setLocalJob(previous);
        createShakeAnimation(shakeAnim).start();
        const status =
          err &&
          typeof err === 'object' &&
          'response' in err &&
          (err as { response?: { status?: number } }).response?.status;
        if (status === 409) {
          Alert.alert('Conflict', 'This job status was already changed. Refreshing...');
          queryClient.invalidateQueries({ queryKey: ['job', jobId] });
        } else {
          Alert.alert('Error', 'Action failed. Please try again.');
        }
      } finally {
        setLoading(false);
      }
    },
    [job, jobId, localJob, queryClient, shakeAnim],
  );

  // ─── Button handlers ───────────────────────────────────────────────────────

  const handleDefineEta = useCallback(() => {
    setShowEtaSheet(true);
  }, []);

  const handleEtaDefined = useCallback(
    (updatedJob: JobResponse) => {
      setLocalJob(updatedJob);
      queryClient.setQueryData(['job', updatedJob.id], updatedJob);
    },
    [queryClient],
  );

  const handleStartJob = useCallback(() => {
    if (!displayJob) return;
    void handleTransition(
      () => startJob(displayJob.id),
      { status: 'IN_PROGRESS', inProgressSubstatus: 'WORKING' },
      setStartingJob,
    );
  }, [displayJob, handleTransition]);

  const handleMarkPending = useCallback(() => {
    setShowPendingSheet(true);
  }, []);

  const handlePendingDone = useCallback(
    (updatedJob: JobResponse) => {
      setLocalJob(updatedJob);
      queryClient.setQueryData(['job', jobId], updatedJob);
    },
    [jobId, queryClient],
  );

  const handleDonePendingApproval = useCallback(() => {
    if (!displayJob) return;
    void handleTransition(
      () => markDonePendingApproval(displayJob.id),
      { status: 'DONE_PENDING_APPROVAL' },
      setMarkingDone,
    );
  }, [displayJob, handleTransition]);

  const handleComplete = useCallback(() => {
    if (!displayJob) return;
    void handleTransition(
      () => completeJob(displayJob.id),
      { status: 'COMPLETED' },
      setCompletingJob,
    );
  }, [displayJob, handleTransition]);

  const handlePay = useCallback(() => {
    Alert.alert('Coming in v1.1', 'In-app payment is coming in the next release.');
  }, []);

  const handleAddressPress = useCallback(() => {
    if (!displayJob) return;
    const fullAddress = [
      displayJob.customFields?.address_line ?? displayJob.customFields?.address,
      displayJob.customFields?.city,
    ]
      .filter(Boolean)
      .join(', ');
    void openMaps({ lat: displayJob.lat, lon: displayJob.lon, address: fullAddress, label: 'Job site' });
  }, [displayJob]);

  const handleAddressLongPress = useCallback(() => {
    if (!displayJob) return;
    const fullAddress = [
      displayJob.customFields?.address_line ?? displayJob.customFields?.address,
      displayJob.customFields?.city,
    ]
      .filter(Boolean)
      .join(', ');
    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        { options: ['Copy Address', 'Cancel'], cancelButtonIndex: 1 },
        (index) => {
          if (index === 0) copyAddress(fullAddress);
        },
      );
    } else {
      Alert.alert('Address', fullAddress, [
        { text: 'Copy', onPress: () => copyAddress(fullAddress) },
        { text: 'Cancel', style: 'cancel' },
      ]);
    }
  }, [displayJob]);

  const handleAddNotes = useCallback(() => {
    if (!displayJob) return;
    navigation.navigate('AddNote', { jobId: displayJob.id });
  }, [displayJob, navigation]);

  const handleAttachImage = useCallback(() => {
    if (!displayJob) return;
    navigation.navigate('AttachImage', { jobId: displayJob.id });
  }, [displayJob, navigation]);

  // ─── Derived state ──────────────────────────────────────────────────────────

  const status = displayJob?.status;
  const canDefineEta = status === 'ASSIGNED_TO_TECHNICIAN' || status === 'IN_PROGRESS';
  const canStartJob = status === 'ASSIGNED_TO_TECHNICIAN';
  const canMarkPending = status === 'IN_PROGRESS';
  const canMarkDone = status === 'IN_PROGRESS';
  const canComplete =
    status === 'DONE_PENDING_APPROVAL' && tenantSettings?.closeJobsEnabled === true;
  const canAddNotes = status !== 'COMPLETED' && status !== 'CANCELED';
  const canAttachImage = status === 'IN_PROGRESS';

  // ─── Render helpers ─────────────────────────────────────────────────────────

  const customerName =
    displayJob?.customFields?.customerName ||
    displayJob?.customFields?.customer_name ||
    'Customer';

  const addressLine =
    [displayJob?.customFields?.address, displayJob?.customFields?.city, displayJob?.customFields?.state]
      .filter(Boolean)
      .join(', ') || null;

  // ─── Loading / error ────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (isError || !displayJob) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>Failed to load job. Pull to refresh.</Text>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // ─── Main render ────────────────────────────────────────────────────────────

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Header */}
      <View style={styles.headerRow}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          style={styles.backBtn}
        >
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
        <View style={styles.headerCenter}>
          <Text style={styles.jobId}>Job #{shortId(displayJob.id)}</Text>
        </View>
        <View style={styles.badgeRow}>
          <View style={[styles.badge, { backgroundColor: STATUS_COLOR[displayJob.status] }]}>
            <Text style={styles.badgeText}>{STATUS_LABEL[displayJob.status]}</Text>
          </View>
          {displayJob.status === 'IN_PROGRESS' && displayJob.inProgressSubstatus === 'ON_THE_WAY' && (
            <View style={styles.onTheWayBadge}>
              <Text style={styles.onTheWayText}>On the Way</Text>
            </View>
          )}
        </View>
      </View>

      {/* Customer info */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>Customer</Text>
        <Text style={styles.customerName}>{String(customerName)}</Text>

        {displayJob.customerPhone ? (
          <Pressable
            onPress={() => Linking.openURL(`tel:${displayJob.customerPhone}`)}
            accessibilityRole="button"
            accessibilityLabel={`Call ${displayJob.customerPhone}`}
            style={styles.infoRow}
          >
            <Text style={styles.infoIcon}>📞</Text>
            <Text style={[styles.infoText, styles.link]}>{displayJob.customerPhone}</Text>
          </Pressable>
        ) : null}

        {addressLine ? (
          <Pressable
            onPress={handleAddressPress}
            onLongPress={handleAddressLongPress}
            accessibilityRole="button"
            accessibilityLabel="Open map"
            testID="address-row"
            style={styles.infoRow}
          >
            <Text style={styles.infoIcon}>📍</Text>
            <Text style={[styles.infoText, styles.link]}>{addressLine}</Text>
          </Pressable>
        ) : null}
      </View>

      {/* In-progress timer */}
      {displayJob.status === 'IN_PROGRESS' && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Time on site</Text>
          <Text style={styles.timer} testID="elapsed-timer">
            {formatElapsed(elapsed)}
          </Text>
        </View>
      )}

      {/* Schedule */}
      {(displayJob.scheduledStart || displayJob.scheduledEnd) && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Scheduled</Text>
          <Text style={styles.scheduleText}>
            {formatDate(displayJob.scheduledStart)} · {formatTime(displayJob.scheduledStart)}
            {displayJob.scheduledEnd ? ` – ${formatTime(displayJob.scheduledEnd)}` : ''}
          </Text>
        </View>
      )}

      {/* Custom fields */}
      {displayJob.customFields && Object.keys(displayJob.customFields).length > 0 && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Details</Text>
          {Object.entries(displayJob.customFields).map(([k, v]) => (
            <View key={k} style={styles.fieldRow}>
              <Text style={styles.fieldKey}>{snakeToTitle(k)}</Text>
              <Text style={styles.fieldValue}>{String(v)}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Actions */}
      <Animated.View style={[styles.card, { transform: [{ translateX: shakeAnim }] }]}>
        <Text style={styles.sectionLabel}>Actions</Text>

        <View style={styles.actionsGrid}>
          {/* 1. Define ETA */}
          <TouchableOpacity
            style={[styles.actionBtn, !canDefineEta && styles.actionBtnDisabled]}
            onPress={handleDefineEta}
            disabled={!canDefineEta}
            accessibilityLabel="Define ETA"
            accessibilityHint={!canDefineEta ? 'Not available in current status' : undefined}
          >
            <Text style={styles.actionIcon}>🕐</Text>
            <Text style={styles.actionLabel}>Define ETA</Text>
          </TouchableOpacity>

          {/* 2. Start the Job */}
          <TouchableOpacity
            style={[styles.actionBtn, !canStartJob && styles.actionBtnDisabled]}
            onPress={handleStartJob}
            disabled={!canStartJob || startingJob}
            accessibilityLabel="Start the Job"
            accessibilityHint={!canStartJob ? 'Not available in current status' : undefined}
            testID="btn-start-job"
          >
            {startingJob ? (
              <ActivityIndicator color="#fff" testID="spinner-start" />
            ) : (
              <>
                <Text style={styles.actionIcon}>▶️</Text>
                <Text style={styles.actionLabel}>Start the Job</Text>
              </>
            )}
          </TouchableOpacity>

          {/* 3. Pay (always disabled in MVP) */}
          <TouchableOpacity
            style={[styles.actionBtn, styles.actionBtnDisabled]}
            onPress={handlePay}
            accessibilityLabel="Pay"
            accessibilityHint="Coming in v1.1"
          >
            <Text style={styles.actionIcon}>💳</Text>
            <Text style={styles.actionLabel}>Pay</Text>
            <Text style={styles.actionHint}>v1.1</Text>
          </TouchableOpacity>

          {/* 4. Add Notes */}
          <TouchableOpacity
            style={[styles.actionBtn, !canAddNotes && styles.actionBtnDisabled]}
            onPress={handleAddNotes}
            disabled={!canAddNotes}
            accessibilityLabel="Add Notes"
            accessibilityHint={!canAddNotes ? 'Not available in current status' : undefined}
          >
            <Text style={styles.actionIcon}>📝</Text>
            <Text style={styles.actionLabel}>Add Notes</Text>
          </TouchableOpacity>

          {/* 5. Attach Image */}
          <TouchableOpacity
            style={[styles.actionBtn, !canAttachImage && styles.actionBtnDisabled]}
            onPress={handleAttachImage}
            disabled={!canAttachImage}
            accessibilityLabel="Attach Result Image"
            accessibilityHint={!canAttachImage ? 'Only available while job is in progress' : undefined}
          >
            <Text style={styles.actionIcon}>📷</Text>
            <Text style={styles.actionLabel}>Attach Image</Text>
          </TouchableOpacity>

          {/* 6. Mark as Pending — visible when IN_PROGRESS */}
          {canMarkPending && (
            <TouchableOpacity
              style={styles.actionBtn}
              onPress={handleMarkPending}
              accessibilityLabel="Mark as Pending"
              testID="btn-mark-pending"
            >
              <Text style={styles.actionIcon}>⏸️</Text>
              <Text style={styles.actionLabel}>Mark as Pending</Text>
            </TouchableOpacity>
          )}

          {/* 7. Done Pending Approval — visible when IN_PROGRESS */}
          {canMarkDone && (
            <TouchableOpacity
              style={[styles.actionBtn, markingDone && styles.actionBtnDisabled]}
              onPress={handleDonePendingApproval}
              disabled={markingDone}
              accessibilityLabel="Done Pending Approval"
              testID="btn-done-pending"
            >
              {markingDone ? (
                <ActivityIndicator color="#fff" testID="spinner-done" />
              ) : (
                <>
                  <Text style={styles.actionIcon}>✅</Text>
                  <Text style={styles.actionLabel}>Done · Pending Approval</Text>
                </>
              )}
            </TouchableOpacity>
          )}

          {/* 8. Complete — visible only when status=DONE_PENDING_APPROVAL AND closeJobsEnabled */}
          {canComplete && (
            <TouchableOpacity
              style={[styles.actionBtnComplete, completingJob && styles.actionBtnDisabled]}
              onPress={handleComplete}
              disabled={completingJob}
              accessibilityLabel="Complete Job"
              testID="btn-complete"
            >
              {completingJob ? (
                <ActivityIndicator color="#fff" testID="spinner-complete" />
              ) : (
                <>
                  <Text style={styles.actionIcon}>🏁</Text>
                  <Text style={styles.actionLabel}>Complete</Text>
                </>
              )}
            </TouchableOpacity>
          )}
        </View>
      </Animated.View>

      {/* Inline note composer */}

      {/* ETA action sheet */}
      <DefineEtaSheet
        visible={showEtaSheet}
        jobId={displayJob.id}
        onClose={() => setShowEtaSheet(false)}
        onEtaDefined={handleEtaDefined}
      />

      {/* Pending action sheet */}
      <PendingSheet
        visible={showPendingSheet}
        jobId={displayJob.id}
        onClose={() => setShowPendingSheet(false)}
        onPending={handlePendingDone}
      />

      {/* Notes list — server notes merged with offline pending notes */}
      {(() => {
        const pendingRaw = getPendingNotes(jobId);
        const pendingItems: NoteItem[] = pendingRaw.map((n) => ({
          id: n.id,
          body: n.body,
          authorUserId: '',
          createdAt: n.created_at,
          pending: true,
        }));
        const serverItems: NoteItem[] = notes.map((n) => ({
          id: n.id,
          body: n.body,
          authorUserId: n.authorUserId,
          createdAt: n.createdAt,
          pending: false,
        }));
        const allNotes = [...pendingItems, ...serverItems].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
        );
        if (allNotes.length === 0) return null;
        return (
          <View style={styles.card}>
            <Text style={styles.sectionLabel}>Notes</Text>
            <NotesList notes={allNotes} />
          </View>
        );
      })()}
    </ScrollView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 40,
    gap: 12,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
    padding: 24,
  },
  errorText: {
    fontSize: 15,
    color: colors.subtext,
    textAlign: 'center',
    marginBottom: 16,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  backBtn: {
    paddingVertical: 4,
    paddingRight: 8,
  },
  backText: {
    fontSize: 17,
    color: colors.primary,
  },
  headerCenter: {
    flex: 1,
    alignItems: 'center',
  },
  jobId: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  badge: {
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
    letterSpacing: 0.3,
  },
  onTheWayBadge: {
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    backgroundColor: '#3B82F6',
  },
  onTheWayText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
    letterSpacing: 0.3,
  },
  card: {
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 8,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: colors.tabInactive,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginBottom: 2,
  },
  customerName: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 2,
  },
  infoIcon: {
    fontSize: 16,
  },
  infoText: {
    fontSize: 15,
    color: colors.text,
  },
  link: {
    color: colors.primary,
    textDecorationLine: 'underline',
  },
  timer: {
    fontSize: 32,
    fontWeight: '400',
    color: colors.subtext,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    letterSpacing: 2,
  },
  scheduleText: {
    fontSize: 15,
    color: colors.text,
  },
  fieldRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  fieldKey: {
    fontSize: 14,
    color: colors.subtext,
    flex: 1,
  },
  fieldValue: {
    fontSize: 14,
    color: colors.text,
    flex: 2,
    textAlign: 'right',
  },
  actionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  actionBtn: {
    backgroundColor: colors.primary,
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 14,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: '47%',
    flex: 1,
    gap: 4,
  },
  actionBtnComplete: {
    backgroundColor: '#16a34a',
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 14,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: '47%',
    flex: 1,
    gap: 4,
  },
  actionBtnDisabled: {
    opacity: 0.5,
  },
  actionIcon: {
    fontSize: 20,
  },
  actionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  actionHint: {
    fontSize: 10,
    color: 'rgba(255,255,255,0.75)',
  },
});
