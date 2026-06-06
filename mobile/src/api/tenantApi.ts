import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useFocusEffect } from '@react-navigation/native';
import { apiClient } from './client';

export interface TenantSettings {
  closeJobsEnabled: boolean;
}

export function useTenantSettings() {
  const query = useQuery({
    queryKey: ['tenantSettings'],
    queryFn: async () => {
      const { data } = await apiClient.get<TenantSettings>('/api/v1/tenant/settings');
      return data;
    },
    staleTime: 30_000,
  });

  useFocusEffect(
    useCallback(() => {
      void query.refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []),
  );

  return query;
}
