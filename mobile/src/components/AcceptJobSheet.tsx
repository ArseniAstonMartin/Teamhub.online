import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Animated,
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { colors } from '../theme/colors';
import { claimJob } from '../api/jobsApi';
import type { JobResponse } from '../api/jobsApi';
import type { RootStackParamList } from '../navigation/types';

const SHEET_HEIGHT = 320;

type NavProp = NativeStackNavigationProp<RootStackParamList>;

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
}

function getServiceLabel(job: JobResponse): string {
  const cf = job.customFields;
  if (cf && typeof cf['serviceType'] === 'string') return cf['serviceType'] as string;
  if (cf && typeof cf['service_type'] === 'string') return cf['service_type'] as string;
  return 'New Job';
}

async function claimWithRetry(id: string, maxAttempts = 3): Promise<JobResponse> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await claimJob(id);
    } catch (err: unknown) {
      const status =
        err &&
        typeof err === 'object' &&
        'response' in err &&
        (err as { response?: { status?: number } }).response?.status;
      // 409 is a definitive answer — do not retry
      if (status === 409) throw err;
      if (attempt === maxAttempts) throw err;
      // Exponential backoff: 500ms, 1000ms, 2000ms
      await new Promise<void>((resolve) =>
        setTimeout(resolve, 500 * Math.pow(2, attempt - 1)),
      );
    }
  }
  throw new Error('unreachable');
}

interface Props {
  job: JobResponse | null;
  visible: boolean;
  onClose: () => void;
  onClaimed: (job: JobResponse) => void;
  onJobClaimed: (jobId: string) => void;
}

export default function AcceptJobSheet({
  job,
  visible,
  onClose,
  onClaimed,
  onJobClaimed,
}: Props) {
  const navigation = useNavigation<NavProp>();
  const [translateY] = useState(() => new Animated.Value(SHEET_HEIGHT));
  const [loading, setLoading] = useState(false);
  const isSubmitting = useRef(false);

  useEffect(() => {
    if (visible) {
      Animated.spring(translateY, {
        toValue: 0,
        useNativeDriver: false,
        damping: 20,
        stiffness: 200,
      }).start();
    } else {
      Animated.timing(translateY, {
        toValue: SHEET_HEIGHT,
        duration: 200,
        useNativeDriver: false,
      }).start();
    }
  }, [visible, translateY]);

  const handleAccept = async () => {
    if (!job) return;
    if (isSubmitting.current) return;
    isSubmitting.current = true;
    setLoading(true);

    try {
      const claimed = await claimWithRetry(job.id);
      onClose();
      onClaimed(claimed);
      navigation.navigate('JobDetail', { jobId: claimed.id });
    } catch (err: unknown) {
      const status =
        err &&
        typeof err === 'object' &&
        'response' in err &&
        (err as { response?: { status?: number } }).response?.status;

      if (status === 409) {
        onClose();
        onJobClaimed(job.id);
        Alert.alert(
          'Already Claimed',
          'Job already taken — it was claimed by another technician.',
          [{ text: 'OK' }],
        );
      } else {
        Alert.alert(
          'Connection Error',
          'Connection error. Please check your network and try again.',
          [{ text: 'OK' }],
        );
      }
    } finally {
      setLoading(false);
      isSubmitting.current = false;
    }
  };

  if (!job) return null;

  const timeLabel =
    job.scheduledStart
      ? `${formatDate(job.scheduledStart)} · ${formatTime(job.scheduledStart)}${job.scheduledEnd ? `–${formatTime(job.scheduledEnd)}` : ''}`
      : 'No scheduled time';

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <TouchableWithoutFeedback onPress={onClose} accessible={false}>
        <View style={styles.overlay} />
      </TouchableWithoutFeedback>

      <Animated.View
        style={[styles.sheet, { transform: [{ translateY }] }]}
        accessible
        accessibilityViewIsModal
        accessibilityLabel="Accept job confirmation"
      >
        <View style={styles.handle} />

        <Text style={styles.title}>{getServiceLabel(job)}</Text>
        <Text style={styles.time}>{timeLabel}</Text>

        {Object.entries(job.customFields ?? {}).length > 0 && (
          <View style={styles.fieldsBlock}>
            {Object.entries(job.customFields).map(([k, v]) => (
              <Text key={k} style={styles.fieldRow}>
                <Text style={styles.fieldKey}>{k}: </Text>
                {String(v)}
              </Text>
            ))}
          </View>
        )}

        <TouchableOpacity
          style={[styles.acceptBtn, loading && styles.acceptBtnDisabled]}
          onPress={handleAccept}
          disabled={loading}
          accessibilityRole="button"
          accessibilityLabel="Accept Job"
          accessibilityState={{ busy: loading }}
        >
          {loading ? (
            <ActivityIndicator color="#fff" testID="accept-spinner" />
          ) : (
            <Text style={styles.acceptBtnText}>Accept Job</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.cancelBtn} onPress={onClose}>
          <Text style={styles.cancelBtnText}>Cancel</Text>
        </TouchableOpacity>
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: SHEET_HEIGHT,
    backgroundColor: colors.background,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    paddingBottom: 36,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: -4 },
    elevation: 16,
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
    alignSelf: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 6,
  },
  time: {
    fontSize: 14,
    color: colors.subtext,
    marginBottom: 12,
  },
  fieldsBlock: {
    backgroundColor: colors.card,
    borderRadius: 8,
    padding: 10,
    marginBottom: 16,
  },
  fieldRow: {
    fontSize: 13,
    color: colors.text,
    marginBottom: 2,
  },
  fieldKey: {
    fontWeight: '600',
    color: colors.subtext,
  },
  acceptBtn: {
    backgroundColor: colors.statusSubmitted,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginBottom: 10,
  },
  acceptBtnDisabled: {
    opacity: 0.6,
  },
  acceptBtnText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  cancelBtn: {
    alignItems: 'center',
    paddingVertical: 10,
  },
  cancelBtnText: {
    fontSize: 15,
    color: colors.subtext,
  },
});
