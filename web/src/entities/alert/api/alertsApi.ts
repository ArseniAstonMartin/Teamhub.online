import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';

export interface AlertDto {
  id: string;
  jobId: string;
  kind: string;
  createdAt: string;
}

export const ALERTS_QUERY_KEY = ['alerts'] as const;

/**
 * Fetches pending (undismissed) in-app alerts for the authenticated Admin.
 * Polls every 30 s when the window is focused; pauses in background (PRD §28, TASK-118).
 */
export function useAlerts() {
  return useQuery<AlertDto[]>({
    queryKey: ALERTS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<AlertDto[]>('/api/v1/alerts');
      return data;
    },
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}

/** Dismisses a single alert and invalidates the alerts query. */
export function useDismissAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) => apiClient.post(`/api/v1/alerts/${alertId}/dismiss`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ALERTS_QUERY_KEY }),
  });
}
