import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';

export interface CurrentUser {
  id: string;
  email: string;
  name: string;
  role: 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';
  tenantId: string;
  lastLoginAt: string | null;
}

export function useCurrentUser() {
  return useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const { data } = await apiClient.get<CurrentUser>('/api/v1/users/me');
      return data;
    },
    staleTime: 60_000,
  });
}
