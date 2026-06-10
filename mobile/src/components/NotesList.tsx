import React from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';

export interface NoteItem {
  id: string;
  body: string;
  authorUserId: string;
  createdAt: string;
  pending?: boolean;
}

interface NotesListProps {
  notes: NoteItem[];
}

function formatNoteTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function NoteRow({ item }: { item: NoteItem }) {
  return (
    <View style={styles.noteItem} testID={`note-item-${item.id}`}>
      <View style={styles.noteHeader}>
        {item.pending && (
          <View style={styles.pendingBadge} testID={`pending-badge-${item.id}`}>
            <Text style={styles.pendingBadgeText}>🔄 Pending sync</Text>
          </View>
        )}
      </View>
      <Text style={styles.noteBody}>{item.body}</Text>
      <Text style={styles.noteMeta}>{formatNoteTime(item.createdAt)}</Text>
    </View>
  );
}

export default function NotesList({ notes }: NotesListProps) {
  if (notes.length === 0) return null;

  return (
    <FlatList
      data={notes}
      keyExtractor={(item) => item.id}
      renderItem={({ item }) => <NoteRow item={item} />}
      scrollEnabled={false}
      testID="notes-list"
    />
  );
}

const styles = StyleSheet.create({
  noteItem: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingVertical: 10,
    gap: 4,
  },
  noteHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 2,
  },
  pendingBadge: {
    backgroundColor: '#FEF3C7',
    borderWidth: 1,
    borderColor: '#F59E0B',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  pendingBadgeText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#92400E',
  },
  noteBody: {
    fontSize: 14,
    color: colors.text,
    lineHeight: 20,
  },
  noteMeta: {
    fontSize: 12,
    color: colors.tabInactive,
  },
});
