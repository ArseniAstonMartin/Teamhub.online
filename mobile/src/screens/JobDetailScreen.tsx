import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import DateTimePicker from '@react-native-community/datetimepicker';
import * as ImagePicker from 'expo-image-picker';
import { useQueryClient } from '@tanstack/react-query';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import {
  useJob,
  useJobNotes,
  addNote,
  startJob,
  updateJobSchedule,
  requestPhotoUploadUrl,
  type JobStatus,
} from '../api/jobsApi';

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

function formatNoteTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function JobDetailScreen() {
  const route = useRoute<RouteP>();
  const navigation = useNavigation<NavProp>();
  const jobId: string = route.params?.jobId ?? '';
  const queryClient = useQueryClient();

  const { data: job, isLoading, isError } = useJob(jobId);
  const { data: notes = [] } = useJobNotes(jobId);

  // In-progress timer
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (job?.status !== 'IN_PROGRESS' || !job.actualStart) return;
    const start = new Date(job.actualStart).getTime();
    const interval = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 1000);
    return () => clearInterval(interval);
  }, [job?.status, job?.actualStart]);

  // ETA date picker
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [pickerDate, setPickerDate] = useState<Date>(new Date());

  // Notes composer
  const [showNoteInput, setShowNoteInput] = useState(false);
  const [noteText, setNoteText] = useState('');
  const [noteSending, setNoteSending] = useState(false);

  const handleDefineEta = useCallback(() => {
    setPickerDate(job?.scheduledEnd ? new Date(job.scheduledEnd) : new Date());
    setShowDatePicker(true);
  }, [job]);

  const handleDateChange = useCallback(
    async (_: unknown, date?: Date) => {
      if (Platform.OS === 'android') setShowDatePicker(false);
      if (!date || !job) return;
      try {
        await updateJobSchedule(job.id, date.toISOString());
        queryClient.invalidateQueries({ queryKey: ['job', job.id] });
      } catch {
        Alert.alert('Error', 'Could not update ETA. Please try again.');
      }
      if (Platform.OS !== 'android') setShowDatePicker(false);
    },
    [job, queryClient],
  );

  const handleStartJob = useCallback(async () => {
    if (!job) return;
    try {
      const updated = await startJob(job.id);
      queryClient.setQueryData(['job', job.id], updated);
    } catch {
      Alert.alert('Error', 'Could not start job. Please try again.');
    }
  }, [job, queryClient]);

  const handlePay = useCallback(() => {
    Alert.alert('Coming in v1.1', 'In-app payment is coming in the next release.');
  }, []);

  const handleAddNotes = useCallback(() => {
    setShowNoteInput((prev) => !prev);
  }, []);

  const handleSendNote = useCallback(async () => {
    if (!job || !noteText.trim()) return;
    setNoteSending(true);
    try {
      await addNote(job.id, noteText.trim());
      queryClient.invalidateQueries({ queryKey: ['jobNotes', job.id] });
      setNoteText('');
      setShowNoteInput(false);
    } catch {
      Alert.alert('Error', 'Could not save note. Please try again.');
    } finally {
      setNoteSending(false);
    }
  }, [job, noteText, queryClient]);

  const handleAttachImage = useCallback(async () => {
    if (!job) return;
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
      allowsEditing: false,
    });
    if (result.canceled || !result.assets?.length) return;
    const asset = result.assets[0];
    const sizeBytes = asset.fileSize ?? 0;
    try {
      await requestPhotoUploadUrl(job.id, 'image/jpeg', sizeBytes);
      // TO-DO: complete multipart upload flow (TASK-098)
      Alert.alert('Image Selected', 'Upload flow will be completed in TASK-098.');
    } catch {
      Alert.alert('Error', 'Could not initiate image upload. Please try again.');
    }
  }, [job]);

  // ─── Derived state ──────────────────────────────────────────────────────────

  const status = job?.status;
  const canDefineEta =
    status === 'ASSIGNED_TO_TECHNICIAN' || status === 'IN_PROGRESS';
  const canStartJob = status === 'ASSIGNED_TO_TECHNICIAN';
  const canAddNotes = status !== 'COMPLETED' && status !== 'CANCELED';
  const canAttachImage = status === 'IN_PROGRESS';

  // ─── Render helpers ─────────────────────────────────────────────────────────

  const customerName =
    job?.customFields?.customerName ||
    job?.customFields?.customer_name ||
    'Customer';

  const addressLine =
    [job?.customFields?.address, job?.customFields?.city, job?.customFields?.state]
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

  if (isError || !job) {
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
          <Text style={styles.jobId}>Job #{shortId(job.id)}</Text>
        </View>
        <View style={[styles.badge, { backgroundColor: STATUS_COLOR[job.status] }]}>
          <Text style={styles.badgeText}>{STATUS_LABEL[job.status]}</Text>
        </View>
      </View>

      {/* Customer info */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>Customer</Text>
        <Text style={styles.customerName}>{String(customerName)}</Text>

        {job.customerPhone ? (
          <Pressable
            onPress={() => Linking.openURL(`tel:${job.customerPhone}`)}
            accessibilityRole="button"
            accessibilityLabel={`Call ${job.customerPhone}`}
            style={styles.infoRow}
          >
            <Text style={styles.infoIcon}>📞</Text>
            <Text style={[styles.infoText, styles.link]}>{job.customerPhone}</Text>
          </Pressable>
        ) : null}

        {addressLine ? (
          <Pressable
            onPress={() => navigation.navigate('MapView', { jobId: job.id })}
            accessibilityRole="button"
            accessibilityLabel="Open map"
            style={styles.infoRow}
          >
            <Text style={styles.infoIcon}>📍</Text>
            <Text style={[styles.infoText, styles.link]}>{addressLine}</Text>
          </Pressable>
        ) : null}
      </View>

      {/* In-progress timer */}
      {job.status === 'IN_PROGRESS' && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Time on site</Text>
          <Text style={styles.timer} testID="elapsed-timer">
            {formatElapsed(elapsed)}
          </Text>
        </View>
      )}

      {/* Schedule */}
      {(job.scheduledStart || job.scheduledEnd) && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Scheduled</Text>
          <Text style={styles.scheduleText}>
            {formatDate(job.scheduledStart)} · {formatTime(job.scheduledStart)}
            {job.scheduledEnd ? ` – ${formatTime(job.scheduledEnd)}` : ''}
          </Text>
        </View>
      )}

      {/* Custom fields */}
      {job.customFields && Object.keys(job.customFields).length > 0 && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Details</Text>
          {Object.entries(job.customFields).map(([k, v]) => (
            <View key={k} style={styles.fieldRow}>
              <Text style={styles.fieldKey}>{snakeToTitle(k)}</Text>
              <Text style={styles.fieldValue}>{String(v)}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Actions */}
      <View style={styles.card}>
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
            disabled={!canStartJob}
            accessibilityLabel="Start the Job"
            accessibilityHint={!canStartJob ? 'Not available in current status' : undefined}
          >
            <Text style={styles.actionIcon}>▶️</Text>
            <Text style={styles.actionLabel}>Start the Job</Text>
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
        </View>
      </View>

      {/* Inline note composer */}
      {showNoteInput && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>New Note</Text>
          <TextInput
            style={styles.noteInput}
            value={noteText}
            onChangeText={setNoteText}
            placeholder="Type your note…"
            placeholderTextColor={colors.tabInactive}
            multiline
            autoFocus
            testID="note-input"
          />
          <TouchableOpacity
            style={[styles.sendBtn, !noteText.trim() && styles.actionBtnDisabled]}
            onPress={handleSendNote}
            disabled={noteSending || !noteText.trim()}
            accessibilityLabel="Send note"
          >
            <Text style={styles.sendBtnText}>{noteSending ? 'Saving…' : 'Save Note'}</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Date picker for ETA */}
      {showDatePicker && (
        <DateTimePicker
          value={pickerDate}
          mode="datetime"
          display={Platform.OS === 'ios' ? 'spinner' : 'default'}
          onChange={handleDateChange}
          minimumDate={new Date()}
        />
      )}

      {/* Notes list */}
      {notes.length > 0 && (
        <View style={styles.card}>
          <Text style={styles.sectionLabel}>Notes</Text>
          {[...notes]
            .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
            .map((note) => (
              <View key={note.id} style={styles.noteItem}>
                <Text style={styles.noteBody}>{note.body}</Text>
                <Text style={styles.noteMeta}>{formatNoteTime(note.createdAt)}</Text>
              </View>
            ))}
        </View>
      )}
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
  noteInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: 10,
    fontSize: 15,
    color: colors.text,
    minHeight: 80,
    textAlignVertical: 'top',
  },
  sendBtn: {
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  sendBtnText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 15,
  },
  noteItem: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingVertical: 8,
    gap: 2,
  },
  noteBody: {
    fontSize: 14,
    color: colors.text,
    lineHeight: 20,
  },
  noteMeta: {
    fontSize: 12,
    color: colors.tabInactive,
  },
});
