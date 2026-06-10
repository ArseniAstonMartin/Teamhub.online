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
import { defineEta } from '../api/jobsApi';
import type { JobResponse } from '../api/jobsApi';

// TO-DO (v1.1): SMS notification will be sent to the customer on ETA definition (PRD §13.2)

const SHEET_HEIGHT = 360;
const MIN_CUSTOM_MINUTES = 5;
const MAX_CUSTOM_MINUTES = 480;

type LoadingState = 'none' | '30' | '45' | 'custom';

interface DefineEtaSheetProps {
  visible: boolean;
  jobId: string;
  onClose: () => void;
  onEtaDefined: (updatedJob: JobResponse) => void;
}

export default function DefineEtaSheet({
  visible,
  jobId,
  onClose,
  onEtaDefined,
}: DefineEtaSheetProps) {
  const [translateY] = useState(() => new Animated.Value(SHEET_HEIGHT));
  const [loading, setLoading] = useState<LoadingState>('none');
  const [customMinutes, setCustomMinutes] = useState('');
  const isSubmitting = useRef(false);

  const handleModalShow = useCallback(() => {
    setCustomMinutes('');
    setLoading('none');
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

  async function submit(minutes: number, which: LoadingState) {
    if (isSubmitting.current) return;
    isSubmitting.current = true;
    setLoading(which);
    try {
      const updated = await defineEta(jobId, minutes);
      onEtaDefined(updated);
      onClose();
    } catch {
      Alert.alert('Error', 'Failed to set ETA. Please try again.');
    } finally {
      setLoading('none');
      isSubmitting.current = false;
    }
  }

  function handleQuick(minutes: 30 | 45) {
    void submit(minutes, String(minutes) as LoadingState);
  }

  function handleCustomSubmit() {
    const parsed = parseInt(customMinutes, 10);
    if (isNaN(parsed) || parsed < MIN_CUSTOM_MINUTES || parsed > MAX_CUSTOM_MINUTES) {
      Alert.alert(
        'Invalid ETA',
        `Please enter a value between ${MIN_CUSTOM_MINUTES} and ${MAX_CUSTOM_MINUTES} minutes.`,
      );
      return;
    }
    void submit(parsed, 'custom');
  }

  const isAnyLoading = loading !== 'none';

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
          accessibilityLabel="Define ETA sheet"
        >
          <View style={styles.handle} />

          {/* Header */}
          <View style={styles.headerRow}>
            <Text style={styles.title}>Define ETA</Text>
            <TouchableOpacity
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close"
              style={styles.closeBtn}
              testID="close-btn"
            >
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {/* Quick options */}
          <View style={styles.quickRow}>
            <TouchableOpacity
              style={[styles.quickBtn, isAnyLoading && styles.btnDisabled]}
              onPress={() => handleQuick(30)}
              disabled={isAnyLoading}
              accessibilityRole="button"
              accessibilityLabel="30 minutes"
              testID="btn-30"
            >
              {loading === '30' ? (
                <ActivityIndicator color="#fff" testID="spinner-30" />
              ) : (
                <Text style={styles.quickBtnText}>30 min</Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.quickBtn, isAnyLoading && styles.btnDisabled]}
              onPress={() => handleQuick(45)}
              disabled={isAnyLoading}
              accessibilityRole="button"
              accessibilityLabel="45 minutes"
              testID="btn-45"
            >
              {loading === '45' ? (
                <ActivityIndicator color="#fff" testID="spinner-45" />
              ) : (
                <Text style={styles.quickBtnText}>45 min</Text>
              )}
            </TouchableOpacity>
          </View>

          {/* Custom input row */}
          <View style={styles.customRow}>
            <Text style={styles.customLabel}>Custom:</Text>
            <TextInput
              style={styles.customInput}
              value={customMinutes}
              onChangeText={setCustomMinutes}
              keyboardType="numeric"
              placeholder="min"
              placeholderTextColor={colors.tabInactive}
              accessibilityLabel="Custom minutes input"
              testID="custom-input"
              maxLength={3}
              editable={!isAnyLoading}
            />
            <TouchableOpacity
              style={[styles.submitBtn, (!customMinutes || isAnyLoading) && styles.btnDisabled]}
              onPress={handleCustomSubmit}
              disabled={!customMinutes || isAnyLoading}
              accessibilityRole="button"
              accessibilityLabel="Submit custom ETA"
              testID="custom-submit"
            >
              {loading === 'custom' ? (
                <ActivityIndicator color="#fff" testID="spinner-custom" />
              ) : (
                <Text style={styles.submitBtnText}>Submit</Text>
              )}
            </TouchableOpacity>
          </View>

          <Text style={styles.rangeHint}>
            Custom range: {MIN_CUSTOM_MINUTES}–{MAX_CUSTOM_MINUTES} minutes
          </Text>
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
    marginBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: colors.text,
  },
  closeBtn: {
    padding: 4,
  },
  closeText: {
    fontSize: 18,
    color: colors.subtext,
  },
  quickRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  quickBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: 18,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 56,
  },
  quickBtnText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
  },
  btnDisabled: {
    opacity: 0.5,
  },
  customRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 8,
  },
  customLabel: {
    fontSize: 15,
    color: colors.text,
    fontWeight: '600',
  },
  customInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.card,
  },
  submitBtn: {
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 72,
    minHeight: 40,
  },
  submitBtnText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600',
  },
  rangeHint: {
    fontSize: 12,
    color: colors.tabInactive,
    textAlign: 'center',
  },
});
