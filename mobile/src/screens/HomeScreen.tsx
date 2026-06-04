import React, { useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { useNavigation, CommonActions } from '@react-navigation/native';
import { useQueryClient } from '@tanstack/react-query';

import { colors } from '../theme/colors';
import { useAuthStore } from '../stores/authStore';
import { useJobCount } from '../api/jobsApi';
import KpiCard from '../components/KpiCard';

function todayRange(): { from: string; to: string } {
  const now = new Date();
  const from = new Date(now);
  from.setUTCHours(0, 0, 0, 0);
  const to = new Date(now);
  to.setUTCHours(23, 59, 59, 999);
  return { from: from.toISOString(), to: to.toISOString() };
}

function weekRange(): { from: string; to: string } {
  const now = new Date();
  const dayOfWeek = now.getUTCDay();
  const diff = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setUTCDate(now.getUTCDate() + diff);
  monday.setUTCHours(0, 0, 0, 0);
  return { from: monday.toISOString(), to: now.toISOString() };
}

function formatGreetingDate(): string {
  return new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });
}

export default function HomeScreen() {
  const navigation = useNavigation();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const today = todayRange();
  const week = weekRange();

  const todayQuery = useJobCount('today', { from: today.from, to: today.to });
  const weekQuery = useJobCount('week', { status: 'COMPLETED', from: week.from, to: week.to });
  const pendingQuery = useJobCount('pending', { status: 'DONE_PENDING_APPROVAL' });

  const isRefreshing =
    todayQuery.isFetching || weekQuery.isFetching || pendingQuery.isFetching;

  const refetchAll = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['jobCount'] });
  }, [queryClient]);

  const navigateToSchedule = useCallback(() => {
    // TO-DO: pass filter params to ScheduleScreen when implemented
    navigation.dispatch(CommonActions.navigate({ name: 'Schedule' }));
  }, [navigation]);

  // TO-DO: replace role fallback with user's first name once backend includes it in JWT
  const greeting = user ? `Good morning, ${user.role}` : 'Good morning, Team';

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={isRefreshing}
          onRefresh={refetchAll}
          tintColor={colors.primary}
        />
      }
    >
      <View style={styles.header}>
        <Text style={styles.greeting}>{greeting}</Text>
        <Text style={styles.date}>{formatGreetingDate()}</Text>
      </View>

      <View style={styles.row}>
        <KpiCard
          label="Today"
          value={todayQuery.data}
          isLoading={todayQuery.isLoading}
          onPress={navigateToSchedule}
        />
        <View style={styles.gap} />
        <KpiCard
          label="This Week"
          value={weekQuery.data}
          isLoading={weekQuery.isLoading}
          onPress={navigateToSchedule}
        />
      </View>

      <View style={styles.fullRow}>
        <KpiCard
          label="Pending Approval"
          value={pendingQuery.data}
          isLoading={pendingQuery.isLoading}
          accentColor={colors.primaryDark}
          onPress={navigateToSchedule}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: 20,
    paddingTop: 24,
  },
  header: {
    marginBottom: 24,
  },
  greeting: {
    fontSize: 22,
    fontWeight: '700',
    color: colors.text,
  },
  date: {
    fontSize: 14,
    color: colors.subtext,
    marginTop: 4,
  },
  row: {
    flexDirection: 'row',
    marginBottom: 12,
  },
  gap: {
    width: 12,
  },
  fullRow: {
    flexDirection: 'row',
  },
});
