import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';

type NavProp = NativeStackNavigationProp<RootStackParamList, 'JobDetail'>;

export default function JobDetailScreen() {
  const route = useRoute<any>();
  const navigation = useNavigation<NavProp>();
  const jobId: string = route.params?.jobId ?? '';

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={styles.backBtn}
        onPress={() => navigation.goBack()}
        accessibilityRole="button"
        accessibilityLabel="Go back"
      >
        <Text style={styles.backText}>‹ Back</Text>
      </TouchableOpacity>

      <Text style={styles.title}>Job Detail</Text>
      <Text style={styles.id} selectable>{jobId}</Text>
      <Text style={styles.placeholder}>Full job detail coming in a later task.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    paddingHorizontal: 20,
    paddingTop: 60,
  },
  backBtn: {
    marginBottom: 24,
  },
  backText: {
    fontSize: 17,
    color: colors.primary,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 12,
  },
  id: {
    fontSize: 14,
    color: colors.subtext,
    fontFamily: 'monospace' as const,
    marginBottom: 24,
  },
  placeholder: {
    fontSize: 16,
    color: colors.subtext,
    lineHeight: 24,
  },
});
