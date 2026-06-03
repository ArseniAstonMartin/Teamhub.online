import { useMutation } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type { BillingStatus, CheckoutResponse, PortalResponse, PriceKey } from '../model/types';

// TO-DO: wire to GET /api/v1/billing/status when endpoint is added
// Returns null until the endpoint is available.
export function useBillingStatus(): BillingStatus | null {
  return null;
}

export function useCreateCheckout() {
  return useMutation({
    mutationFn: async (priceKey: PriceKey) => {
      const { data } = await apiClient.post<CheckoutResponse>(
        `/api/v1/billing/checkout/${priceKey}`,
      );
      return data;
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
