import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import internalClient from '@/shared/api/internalClient';
import type {
  InternalTenantDetail,
  InternalTenantsPageResponse,
  ProvisionTenantRequest,
  ProvisionTenantResponse,
  SchemaPresetItem,
} from '../model/types';

export const INTERNAL_TENANTS_QUERY_KEY = ['internal', 'tenants'] as const;
export const INTERNAL_SCHEMA_PRESETS_QUERY_KEY = ['internal', 'schema-presets'] as const;

export function internalTenantDetailQueryKey(id: string) {
  return ['internal', 'tenants', id] as const;
}

export function useSchemaPresets(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: INTERNAL_SCHEMA_PRESETS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await internalClient.get<SchemaPresetItem[]>(
        '/api/v1/internal/schema-presets',
      );
      return data;
    },
    enabled: options?.enabled ?? true,
  });
}

export function useInternalTenants(page = 0, size = 20) {
  return useQuery({
    queryKey: [...INTERNAL_TENANTS_QUERY_KEY, page, size],
    queryFn: async () => {
      const { data } = await internalClient.get<InternalTenantsPageResponse>(
        '/api/v1/internal/tenants',
        { params: { page, size } },
      );
      return data;
    },
  });
}

export function useInternalTenant(id: string) {
  return useQuery({
    queryKey: internalTenantDetailQueryKey(id),
    queryFn: async () => {
      const { data } = await internalClient.get<InternalTenantDetail>(
        `/api/v1/internal/tenants/${id}`,
      );
      return data;
    },
    enabled: Boolean(id),
  });
}

export function useProvisionTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ProvisionTenantRequest) => {
      const { data } = await internalClient.post<ProvisionTenantResponse>(
        '/api/v1/internal/tenants',
        body,
      );
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: INTERNAL_TENANTS_QUERY_KEY });
    },
  });
}

export function useResendOwnerInvite() {
  return useMutation({
    mutationFn: async (tenantId: string) => {
      await internalClient.post(`/api/v1/internal/tenants/${tenantId}/resend-invite`);
    },
  });
}
