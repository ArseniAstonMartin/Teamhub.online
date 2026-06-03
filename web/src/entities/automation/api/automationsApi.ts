import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type {
  AutomationRuleResponse,
  CreateAutomationRuleRequest,
  UpdateAutomationRuleRequest,
  EvaluateAutomationsRequest,
  EvaluateAutomationsResponse,
} from '../model/types';

export const AUTOMATIONS_QUERY_KEY = ['automations'] as const;

export function useAutomations() {
  return useQuery({
    queryKey: AUTOMATIONS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<AutomationRuleResponse[]>('/api/v1/automations');
      return data;
    },
  });
}

export function useCreateAutomation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateAutomationRuleRequest) => {
      const { data } = await apiClient.post<AutomationRuleResponse>('/api/v1/automations', body);
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AUTOMATIONS_QUERY_KEY });
    },
  });
}

export function useUpdateAutomation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: UpdateAutomationRuleRequest }) => {
      const { data } = await apiClient.patch<AutomationRuleResponse>(
        `/api/v1/automations/${id}`,
        body,
      );
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AUTOMATIONS_QUERY_KEY });
    },
  });
}

export function useDeleteAutomation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/automations/${id}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AUTOMATIONS_QUERY_KEY });
    },
  });
}

export function useEvaluateAutomations() {
  return useMutation({
    mutationFn: async (body: EvaluateAutomationsRequest) => {
      const { data } = await apiClient.post<EvaluateAutomationsResponse>(
        '/api/v1/automations/evaluate',
        body,
      );
      return data;
    },
  });
}
