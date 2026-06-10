import React, { useCallback } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import { useCustomer } from '../api/customersApi';
import { useJobsByCustomer, type JobResponse, type JobStatus } from '../api/jobsApi';
import { openMaps } from '../utils/openMaps';

type NavProp = NativeStackNavigationProp<RootStackParamList, 'CustomerDetail'>;
type RouteP = NativeStackScreenProps<RootStackParamList, 'CustomerDetail'>['route'];

// ─── Helpers ─────────────────────────────────────────────────────────────────

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

function formatDate(iso: string | null) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

// ─── Job history row ──────────────────────────────────────────────────────────

interface JobRowProps {
  job: JobResponse;
}

function JobRow({ job }: JobRowProps) {
  return (
    <View style={styles.jobRow} testID={`job-row-${job.id}`}>
      <View style={styles.jobInfo}>
        <Text style={styles.jobId}>Job #{shortId(job.id)}</Text>
        <Text style={styles.jobDate}>{formatDate(job.scheduledStart ?? job.createdAt)}</Text>
      </View>
      <View style={[styles.badge, { backgroundColor: STATUS_COLOR[job.status] }]}>
        <Text style={styles.badgeText}>{STATUS_LABEL[job.status]}</Text>
      </View>
    </View>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function CustomerDetailScreen() {
  const route = useRoute<RouteP>();
  const navigation = useNavigation<NavProp>();
  const { customerId } = route.params;

  const { data: customer, isLoading, isError } = useCustomer(customerId);
  const { data: jobsPage, isLoading: jobsLoading } = useJobsByCustomer(customerId);

  const jobs = jobsPage?.content ?? [];

  const handleAddressPress = useCallback(() => {
    if (!customer) return;
    const address = [customer.addressLine, customer.city, customer.state].filter(Boolean).join(', ');
    void openMaps({ lat: customer.lat, lon: customer.lon, address, label: customer.name });
  }, [customer]);

  // ─── Loading / error ───────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (isError || !customer) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>Failed to load customer.</Text>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const addressLine = [customer.addressLine, customer.city, customer.state].filter(Boolean).join(', ');

  // ─── Main render ───────────────────────────────────────────────────────────

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
        <Text style={styles.headerTitle} numberOfLines={1}>
          {customer.name}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      {/* Contact info */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>Contact Info</Text>

        {customer.phone ? (
          <View style={styles.infoRow} testID="phone-row">
            <Text style={styles.infoIcon}>📞</Text>
            <Text style={styles.infoText} testID="phone-value">
              {customer.phone}
            </Text>
          </View>
        ) : null}

        {customer.email ? (
          <View style={styles.infoRow} testID="email-row">
            <Text style={styles.infoIcon}>✉️</Text>
            <Text style={styles.infoText} testID="email-value">
              {customer.email}
            </Text>
          </View>
        ) : null}

        {addressLine ? (
          <Pressable
            onPress={handleAddressPress}
            accessibilityRole="button"
            accessibilityLabel="Open map"
            style={styles.infoRow}
            testID="address-row"
          >
            <Text style={styles.infoIcon}>📍</Text>
            <Text style={[styles.infoText, styles.link]} numberOfLines={2}>
              {addressLine}
            </Text>
          </Pressable>
        ) : null}
      </View>

      {/* Job history */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>Job History</Text>

        {jobsLoading ? (
          <ActivityIndicator color={colors.primary} style={{ marginVertical: 12 }} />
        ) : jobs.length === 0 ? (
          <Text style={styles.emptyJobs}>No jobs yet</Text>
        ) : (
          <FlatList
            data={jobs}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => <JobRow job={item} />}
            scrollEnabled={false}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
          />
        )}
      </View>
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
  headerTitle: {
    flex: 1,
    fontSize: 17,
    fontWeight: '700',
    color: colors.text,
    textAlign: 'center',
  },
  headerSpacer: {
    width: 40,
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
    flex: 1,
  },
  link: {
    color: colors.primary,
    textDecorationLine: 'underline',
  },
  jobRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  jobInfo: {
    flex: 1,
    gap: 2,
  },
  jobId: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
  },
  jobDate: {
    fontSize: 12,
    color: colors.subtext,
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
  separator: {
    height: 1,
    backgroundColor: colors.border,
  },
  emptyJobs: {
    fontSize: 14,
    color: colors.subtext,
    textAlign: 'center',
    paddingVertical: 8,
  },
});
