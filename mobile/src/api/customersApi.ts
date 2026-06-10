import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { apiClient } from './client';

export interface CustomerSummary {
  id: string;
  name: string;
  companyName?: string;
  phone?: string;
  email?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  zip?: string;
  lat?: number;
  lon?: number;
  updatedAt: string;
}

export interface CustomersPage {
  content: CustomerSummary[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export function useCustomers(search: string) {
  return useInfiniteQuery<CustomersPage>({
    queryKey: ['customers', search],
    queryFn: async ({ pageParam = 0 }) => {
      const params: Record<string, string | number> = { page: pageParam as number, size: 20 };
      if (search.trim()) params.q = search.trim();
      const { data } = await apiClient.get('/api/v1/customers', { params });
      return data;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.number < lastPage.totalPages - 1 ? lastPage.number + 1 : undefined,
  });
}

export function useCustomer(id: string) {
  return useQuery<CustomerSummary>({
    queryKey: ['customer', id],
    queryFn: async () => {
      const { data } = await apiClient.get(`/api/v1/customers/${id}`);
      return data;
    },
    enabled: Boolean(id),
  });
}
