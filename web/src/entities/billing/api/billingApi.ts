import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { BillingStatus, CheckoutResponse, PortalResponse, PriceKey } from '../model/types';

export const BILLING_STATUS_QUERY_KEY = ['billing', 'status'] as const;

export function useBillingStatus(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: BILLING_STATUS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<BillingStatus>('/api/v1/billing/status');
      return data;
    },
    enabled: options?.enabled ?? true,
  });
}

export function useCreateCheckout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (priceKey: PriceKey) => {
      const { data } = await apiClient.post<CheckoutResponse>(
        `/api/v1/billing/checkout/${priceKey}`,
      );
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BILLING_STATUS_QUERY_KEY });
    },
  });
}

export function useCreatePortal() {
  return useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.post<PortalResponse>('/api/v1/billing/portal');
      return data;
    },
  });
}
