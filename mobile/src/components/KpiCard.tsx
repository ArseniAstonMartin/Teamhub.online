import React from 'react';
import { Text, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { colors } from '../theme/colors';

interface KpiCardProps {
  label: string;
  value: number | undefined;
  isLoading: boolean;
  accentColor?: string;
  onPress?: () => void;
}

export default function KpiCard({
  label,
  value,
  isLoading,
  accentColor = colors.primary,
  onPress,
}: KpiCardProps) {
  return (
    <Pressable
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${label}: ${value ?? 'loading'}`}
    >
      <Text style={styles.label}>{label}</Text>
      {isLoading ? (
        <ActivityIndicator color={accentColor} style={styles.loader} />
      ) : (
        <Text style={[styles.value, { color: accentColor }]}>{value ?? '—'}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: 20,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 100,
    justifyContent: 'space-between',
  },
  pressed: { opacity: 0.75 },
  label: {
    fontSize: 13,
    fontWeight: '500',
    color: colors.subtext,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  value: { fontSize: 40, fontWeight: '800', letterSpacing: -1 },
  loader: { marginTop: 8 },
});
