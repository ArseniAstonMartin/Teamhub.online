import React from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { loadRefreshToken } from '../utils/secureStore';
import { colors } from '../theme/colors';

// TO-DO: add GET /api/v1/users/me endpoint to expose name and email

type Row = { label: string; value: string };
type Section = { title: string; rows: Row[] };

export default function ProfileScreen() {
  const user = useAuthStore((s) => s.user);

  const sections: Section[] = [
    {
      title: 'ACCOUNT',
      rows: [
        { label: 'Role', value: user?.role ?? '—' },
        { label: 'Team', value: user ? `${user.tenantId.slice(0, 8)}…` : '—' },
        { label: 'User ID', value: user ? `${user.id.slice(0, 8)}…` : '—' },
      ],
    },
    {
      title: 'ABOUT',
      rows: [
        { label: 'App Version', value: Constants.expoConfig?.version ?? '1.0.0' },
        { label: 'Device', value: Device.modelName ?? 'Unknown' },
      ],
    },
  ];

  const handleSignOut = () => {
    Alert.alert('Sign out', 'Are you sure you want to sign out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Sign Out',
        style: 'destructive',
        onPress: async () => {
          const refreshToken = await loadRefreshToken();
          if (refreshToken) {
            try {
              await apiClient.post('/api/v1/auth/logout', { refreshToken });
            } catch {
              // best-effort: revocation failure should not block local sign-out
            }
          }
          await useAuthStore.getState().logout();
        },
      },
    ]);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.screenTitle}>Profile</Text>

      {sections.map((section) => (
        <View key={section.title} style={styles.section}>
          <Text style={styles.sectionHeader}>{section.title}</Text>
          <View style={styles.card}>
            {section.rows.map((row, idx) => (
              <View
                key={row.label}
                style={[
                  styles.row,
                  idx < section.rows.length - 1 && styles.rowBorder,
                ]}
              >
                <Text style={styles.rowLabel}>{row.label}</Text>
                <Text style={styles.rowValue}>{row.value}</Text>
              </View>
            ))}
          </View>
        </View>
      ))}

      <TouchableOpacity
        style={styles.signOutButton}
        onPress={handleSignOut}
        activeOpacity={0.8}
        testID="sign-out-button"
      >
        <Text style={styles.signOutText}>Sign Out</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const DANGER = '#E53935';

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: 16,
    paddingBottom: 32,
  },
  screenTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 24,
  },
  section: {
    marginBottom: 24,
  },
  sectionHeader: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.subtext,
    letterSpacing: 0.8,
    marginBottom: 8,
    marginLeft: 4,
  },
  card: {
    backgroundColor: colors.card,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  rowBorder: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  rowLabel: {
    fontSize: 15,
    color: colors.text,
  },
  rowValue: {
    fontSize: 15,
    color: colors.subtext,
    fontVariant: ['tabular-nums'],
  },
  signOutButton: {
    backgroundColor: DANGER,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  signOutText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});
