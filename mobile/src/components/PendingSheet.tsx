import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  KeyboardAvoidingView,
  Modal,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import { colors } from '../theme/colors';
import { markPending, type PendingReason, type JobResponse } from '../api/jobsApi';

const SHEET_HEIGHT = 400;

const REASONS: { value: PendingReason; label: string }[] = [
  { value: 'WAITING_FOR_PARTS', label: 'Waiting for Parts' },
  { value: 'RESCHEDULING_NEEDED', label: 'Rescheduling Needed' },
];

interface PendingSheetProps {
  visible: boolean;
  jobId: string;
  onClose: () => void;
  onPending: (updatedJob: JobResponse) => void;
}

export default function PendingSheet({
  visible,
  jobId,
  onClose,
  onPending,
}: PendingSheetProps) {
  const [translateY] = useState(() => new Animated.Value(SHEET_HEIGHT));
  const [selectedReason, setSelectedReason] = useState<PendingReason | null>(null);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);
  const isSubmitting = useRef(false);

  const handleModalShow = useCallback(() => {
    setSelectedReason(null);
    setNote('');
    setLoading(false);
  }, []);

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

  const handleSubmit = useCallback(async () => {
    if (!selectedReason || isSubmitting.current) return;
    isSubmitting.current = true;
    setLoading(true);
    try {
      const updated = await markPending(jobId, selectedReason, note.trim() || undefined);
      onPending(updated);
      onClose();
    } catch {
      Alert.alert('Error', 'Failed. Please try again.');
    } finally {
      setLoading(false);
      isSubmitting.current = false;
    }
  }, [jobId, selectedReason, note, onPending, onClose]);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
      onShow={handleModalShow}
      statusBarTranslucent
    >
      <TouchableWithoutFeedback onPress={onClose} accessible={false}>
        <View style={styles.overlay} />
      </TouchableWithoutFeedback>

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'position' : undefined}
        style={styles.kavContainer}
        pointerEvents="box-none"
      >
        <Animated.View
          style={[styles.sheet, { transform: [{ translateY }] }]}
          accessible
          accessibilityViewIsModal
          accessibilityLabel="Mark as Pending sheet"
        >
          <View style={styles.handle} />

          <View style={styles.headerRow}>
            <Text style={styles.title}>Why is this job paused?</Text>
            <TouchableOpacity
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close"
              style={styles.closeBtn}
              testID="pending-close-btn"
            >
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {REASONS.map((r) => (
            <TouchableOpacity
              key={r.value}
              style={[styles.reasonRow, selectedReason === r.value && styles.reasonRowSelected]}
              onPress={() => setSelectedReason(r.value)}
              disabled={loading}
              accessibilityRole="radio"
              accessibilityState={{ checked: selectedReason === r.value }}
              accessibilityLabel={r.label}
              testID={`reason-${r.value}`}
            >
              <View style={[styles.radio, selectedReason === r.value && styles.radioSelected]} />
              <Text style={styles.reasonLabel}>{r.label}</Text>
            </TouchableOpacity>
          ))}

          <TextInput
            style={styles.noteInput}
            value={note}
            onChangeText={setNote}
            placeholder="Note (optional)"
            placeholderTextColor={colors.tabInactive}
            multiline
            editable={!loading}
            accessibilityLabel="Note optional"
            testID="pending-note-input"
          />

          <TouchableOpacity
            style={[styles.submitBtn, (!selectedReason || loading) && styles.btnDisabled]}
            onPress={handleSubmit}
            disabled={!selectedReason || loading}
            accessibilityRole="button"
            accessibilityLabel="Mark as Pending"
            testID="pending-submit-btn"
          >
            {loading ? (
              <ActivityIndicator color="#fff" testID="pending-spinner" />
            ) : (
              <Text style={styles.submitBtnText}>Mark as Pending</Text>
            )}
          </TouchableOpacity>
        </Animated.View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  kavContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  sheet: {
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
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
    flex: 1,
    paddingRight: 8,
  },
  closeBtn: {
    padding: 4,
  },
  closeText: {
    fontSize: 18,
    color: colors.subtext,
  },
  reasonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 10,
  },
  reasonRowSelected: {
    borderColor: colors.primary,
    backgroundColor: `${colors.primary}15`,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: colors.tabInactive,
  },
  radioSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primary,
  },
  reasonLabel: {
    fontSize: 15,
    color: colors.text,
    fontWeight: '500',
  },
  noteInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: 10,
    fontSize: 15,
    color: colors.text,
    minHeight: 64,
    textAlignVertical: 'top',
    marginBottom: 16,
    marginTop: 4,
  },
  submitBtn: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  btnDisabled: {
    opacity: 0.5,
  },
  submitBtnText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
});
