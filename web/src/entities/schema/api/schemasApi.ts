import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { SchemaResponse, PublishSchemaRequest } from '../model/types';

export const SCHEMAS_QUERY_KEY = ['schemas'] as const;

export function useSchemas() {
  return useQuery({
    queryKey: SCHEMAS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<SchemaResponse[]>('/api/v1/schemas');
      return data;
    },
  });
}

export function usePublishSchema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: PublishSchemaRequest) => {
      const { data } = await apiClient.post<SchemaResponse>('/api/v1/schemas', body);
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SCHEMAS_QUERY_KEY });
    },
  });
}

export function useDeactivateSchema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.patch(`/api/v1/schemas/${id}`, { active: false });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SCHEMAS_QUERY_KEY });
    },
  });
}
