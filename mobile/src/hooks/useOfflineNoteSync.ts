import NetInfo from '@react-native-community/netinfo';
import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getPendingNotes, markNoteSynced } from '../db/notesQueue';
import { addNote } from '../api/jobsApi';

export function useOfflineNoteSync(jobId: string) {
  const queryClient = useQueryClient();

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(async (state) => {
      if (!state.isConnected) return;
      const pending = getPendingNotes(jobId);
      for (const note of pending) {
        try {
          await addNote(note.job_id, note.body);
          markNoteSynced(note.id);
          queryClient.invalidateQueries({ queryKey: ['jobNotes', jobId] });
        } catch {
          // Leave in queue for next connectivity event
        }
      }
    });
    return () => unsubscribe();
  }, [jobId, queryClient]);
}
