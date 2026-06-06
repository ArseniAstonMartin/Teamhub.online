import React, { useState, useCallback } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import { useQueryClient } from '@tanstack/react-query';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import { addNote } from '../api/jobsApi';
import { enqueueNote } from '../db/notesQueue';

type NavProp = NativeStackNavigationProp<RootStackParamList, 'AddNote'>;
type RouteP = RouteProp<RootStackParamList, 'AddNote'>;

const MAX_CHARS = 4000;

export default function AddNoteScreen() {
  const navigation = useNavigation<NavProp>();
  const route = useRoute<RouteP>();
  const { jobId } = route.params;
  const queryClient = useQueryClient();

  const [body, setBody] = useState('');
  const [saving, setSaving] = useState(false);

  const handleCancel = useCallback(() => {
    navigation.goBack();
  }, [navigation]);

  const handleSave = useCallback(async () => {
    const trimmed = body.trim();
    if (!trimmed || saving) return;

    setSaving(true);
    try {
      const netState = await NetInfo.fetch();

      if (netState.isConnected) {
        await addNote(jobId, trimmed);
        queryClient.invalidateQueries({ queryKey: ['jobNotes', jobId] });
      } else {
        enqueueNote(jobId, trimmed);
        queryClient.invalidateQueries({ queryKey: ['jobNotes', jobId] });
      }
      navigation.goBack();
    } catch {
      enqueueNote(jobId, trimmed);
      queryClient.invalidateQueries({ queryKey: ['jobNotes', jobId] });
      navigation.goBack();
    } finally {
      setSaving(false);
    }
  }, [body, jobId, navigation, queryClient, saving]);

  const remaining = MAX_CHARS - body.length;
  const canSave = body.trim().length > 0 && body.length <= MAX_CHARS;

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.header}>
        <Pressable onPress={handleCancel} style={styles.headerSide} accessibilityLabel="Cancel">
          <Text style={styles.cancelText}>Cancel</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Add Note</Text>
        <Pressable
          onPress={handleSave}
          disabled={!canSave || saving}
          style={styles.headerSide}
          accessibilityLabel="Save note"
          testID="btn-save"
        >
          <Text style={[styles.saveText, (!canSave || saving) && styles.saveTextDisabled]}>
            {saving ? 'Saving…' : 'Save'}
          </Text>
        </Pressable>
      </View>

      <ScrollView
        style={styles.flex}
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <TextInput
          style={styles.input}
          value={body}
          onChangeText={setBody}
          placeholder="Describe what was done…"
          placeholderTextColor={colors.tabInactive}
          multiline
          autoFocus
          maxLength={MAX_CHARS}
          textAlignVertical="top"
          testID="note-input"
        />
        <Text style={[styles.charCount, remaining < 100 && styles.charCountWarning]}>
          {body.length} / {MAX_CHARS} characters
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: Platform.OS === 'ios' ? 56 : 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
  },
  headerSide: {
    minWidth: 64,
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: colors.text,
  },
  cancelText: {
    fontSize: 17,
    color: colors.primary,
  },
  saveText: {
    fontSize: 17,
    fontWeight: '600',
    color: colors.primary,
    textAlign: 'right',
  },
  saveTextDisabled: {
    opacity: 0.4,
  },
  content: {
    padding: 16,
    gap: 8,
  },
  input: {
    fontSize: 16,
    color: colors.text,
    minHeight: 200,
    lineHeight: 24,
  },
  charCount: {
    fontSize: 13,
    color: colors.tabInactive,
    textAlign: 'right',
  },
  charCountWarning: {
    color: '#EF4444',
  },
});
