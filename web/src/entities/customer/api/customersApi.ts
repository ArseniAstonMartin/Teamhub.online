import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type {
  CustomerResponse,
  CustomersPageResponse,
  CustomersQueryParams,
  CreateCustomerRequest,
  UpdateCustomerRequest,
} from '../model/types';

export const CUSTOMERS_QUERY_KEY = ['customers'] as const;

export function useCustomers(params: CustomersQueryParams) {
  return useQuery({
    queryKey: [...CUSTOMERS_QUERY_KEY, params],
    queryFn: async () => {
      const { data } = await apiClient.get<CustomersPageResponse>('/api/v1/customers', {
        params,
      });
      return data;
    },
  });
}

export function useCustomer(id: string) {
  return useQuery({
    queryKey: ['customer', id],
    queryFn: async () => {
      const { data } = await apiClient.get<CustomerResponse>(`/api/v1/customers/${id}`);
      return data;
    },
    enabled: Boolean(id),
  });
}

export function useCreateCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateCustomerRequest) => {
      const { data } = await apiClient.post<CustomerResponse>('/api/v1/customers', body);
      return data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CUSTOMERS_QUERY_KEY });
    },
  });
}

export function useUpdateCustomer(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateCustomerRequest) => {
      const { data } = await apiClient.patch<CustomerResponse>(`/api/v1/customers/${id}`, body);
      return data;
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['customer', id], updated);
      void queryClient.invalidateQueries({ queryKey: CUSTOMERS_QUERY_KEY });
    },
  });
}

export function useArchiveCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/customers/${id}`);
      return id;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CUSTOMERS_QUERY_KEY });
    },
  });
}

export function useRestoreCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.post<CustomerResponse>(
        `/api/v1/customers/${id}/restore`,
      );
      return data;
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['customer', updated.id], updated);
      void queryClient.invalidateQueries({ queryKey: CUSTOMERS_QUERY_KEY });
    },
  });
}
