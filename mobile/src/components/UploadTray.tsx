import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';

export interface UploadTrayItem {
  id: string;
  filename: string;
  status: 'uploading' | 'done' | 'pending' | 'error';
  progress?: number;
}

interface Props {
  items: UploadTrayItem[];
}

function statusIcon(status: UploadTrayItem['status']): string {
  switch (status) {
    case 'done':
      return '✓';
    case 'uploading':
      return '↑';
    case 'error':
      return '✕';
    default:
      return '⏳';
  }
}

function statusLabel(item: UploadTrayItem): string {
  switch (item.status) {
    case 'done':
      return 'Done';
    case 'uploading':
      return item.progress !== undefined ? `${item.progress}%` : 'Uploading…';
    case 'error':
      return 'Failed — will retry';
    default:
      return 'Pending (offline)';
  }
}

export default function UploadTray({ items }: Props) {
  if (items.length === 0) return null;

  return (
    <View style={styles.container} testID="upload-tray">
      <Text style={styles.heading}>UPLOADS</Text>
      {items.map((item) => (
        <View key={item.id} style={styles.row} testID={`tray-item-${item.id}`}>
          <Text style={[styles.icon, item.status === 'done' && styles.iconDone]}>{statusIcon(item.status)}</Text>
          <Text style={styles.filename} numberOfLines={1}>
            {item.filename}
          </Text>
          {item.status === 'uploading' && item.progress !== undefined && (
            <View style={styles.barTrack}>
              <View style={[styles.barFill, { width: `${item.progress}%` }]} />
            </View>
          )}
          <Text style={[styles.statusText, item.status === 'error' && styles.statusError]}>
            {statusLabel(item)}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.card,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12,
    gap: 8,
  },
  heading: {
    fontSize: 10,
    fontWeight: '700',
    color: colors.tabInactive,
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap',
  },
  icon: {
    fontSize: 14,
    color: colors.subtext,
    width: 16,
    textAlign: 'center',
  },
  iconDone: {
    color: '#10B981',
  },
  filename: {
    flex: 1,
    fontSize: 13,
    color: colors.text,
  },
  barTrack: {
    height: 4,
    width: 60,
    backgroundColor: colors.border,
    borderRadius: 2,
    overflow: 'hidden',
  },
  barFill: {
    height: 4,
    backgroundColor: colors.primary,
    borderRadius: 2,
  },
  statusText: {
    fontSize: 12,
    color: colors.subtext,
    minWidth: 80,
    textAlign: 'right',
  },
  statusError: {
    color: '#EF4444',
  },
});
