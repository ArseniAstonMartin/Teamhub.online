import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { TenantSettingsResponse, UpdateTenantSettingsRequest } from '../model/types';

export const tenantSettingsQueryKey = ['tenant', 'settings'] as const;

export function useTenantSettings() {
  return useQuery({
    queryKey: tenantSettingsQueryKey,
    queryFn: async () => {
      const { data } = await apiClient.get<TenantSettingsResponse>('/api/v1/tenant/settings');
      return data;
    },
  });
}

export function useUpdateTenantSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateTenantSettingsRequest) => {
      const { data } = await apiClient.patch<TenantSettingsResponse>(
        '/api/v1/tenant/settings',
        body,
      );
      return data;
    },
    onSuccess: (data) => {
      qc.setQueryData(tenantSettingsQueryKey, data);
    },
  });
}
