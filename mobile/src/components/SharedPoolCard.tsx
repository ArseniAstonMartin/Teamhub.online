import React, { useEffect, useState } from 'react';
import {
  Animated,
  Easing,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { colors } from '../theme/colors';
import { useReducedMotion } from '../hooks/useReducedMotion';
import type { JobResponse } from '../api/jobsApi';

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getServiceLabel(job: JobResponse): string {
  const cf = job.customFields;
  if (cf && typeof cf['serviceType'] === 'string') return cf['serviceType'] as string;
  if (cf && typeof cf['service_type'] === 'string') return cf['service_type'] as string;
  return 'New Job';
}

interface Props {
  job: JobResponse;
  onPress: () => void;
}

export default function SharedPoolCard({ job, onPress }: Props) {
  const reducedMotion = useReducedMotion();
  const [opacity] = useState(() => new Animated.Value(1));

  useEffect(() => {
    if (reducedMotion) {
      opacity.setValue(1);
      return;
    }
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, {
          toValue: 0.4,
          duration: 750,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(opacity, {
          toValue: 1,
          duration: 750,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ]),
    );
    animation.start();
    return () => animation.stop();
  }, [reducedMotion, opacity]);

  const timeLabel =
    job.scheduledStart
      ? `${formatTime(job.scheduledStart)}${job.scheduledEnd ? `–${formatTime(job.scheduledEnd)}` : ''}`
      : 'Unscheduled';

  const cardStyle = reducedMotion
    ? [styles.card, styles.cardReducedMotion]
    : styles.card;

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.8}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`Accept the job. ${getServiceLabel(job)}, ${timeLabel}`}
      accessibilityHint="Double tap to open acceptance confirmation"
    >
      <Animated.View style={[cardStyle, { opacity }]}>
        <View style={styles.headerRow}>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>ACCEPT THE JOB</Text>
          </View>
        </View>
        <Text style={styles.service}>{getServiceLabel(job)}</Text>
        <Text style={styles.time}>{timeLabel}</Text>
      </Animated.View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FEF2F2',
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: colors.statusSubmitted,
    padding: 12,
    marginVertical: 4,
    marginHorizontal: 16,
    shadowColor: colors.statusSubmitted,
    shadowOpacity: 0.15,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  cardReducedMotion: {
    borderLeftWidth: 5,
    borderLeftColor: colors.statusSubmitted,
  },
  headerRow: {
    flexDirection: 'row',
    marginBottom: 6,
  },
  badge: {
    backgroundColor: colors.statusSubmitted,
    borderRadius: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
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
});
