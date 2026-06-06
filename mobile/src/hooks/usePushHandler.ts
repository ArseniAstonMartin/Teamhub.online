import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { useQueryClient } from '@tanstack/react-query';

/**
 * Handles silent push notifications from the backend.
 *
 * JOB_CLAIMED: Sent to all OTHER technicians when a job in the shared pool
 * is claimed. We invalidate the schedule query so the card disappears without
 * a visible alert.
 */
export function usePushHandler() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const sub = Notifications.addNotificationReceivedListener((notification) => {
      const data = notification.request.content.data as Record<string, unknown> | undefined;
      if (data?.type === 'JOB_CLAIMED') {
        queryClient.invalidateQueries({ queryKey: ['scheduleJobs'] });
      }
    });
    return () => sub.remove();
  }, [queryClient]);
}
