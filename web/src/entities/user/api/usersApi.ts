import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/shared/api/client';
import type {
  CurrentUserResponse,
  UserResponse,
  UsersPageResponse,
  InviteResponse,
  InviteRequest,
  ChangeRoleRequest,
} from '../model/userTypes';

export const currentUserQueryKey = ['currentUser'] as const;

export const usersQueryKey = (page: number, size: number) =>
  ['users', { page, size }] as const;

export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: async () => {
      const { data } = await apiClient.get<CurrentUserResponse>('/api/v1/users/me');
      return data;
    },
    staleTime: 60_000,
  });
}

export function useUsers(page = 0, size = 20) {
  return useQuery({
    queryKey: usersQueryKey(page, size),
    queryFn: async () => {
      const { data } = await apiClient.get<UsersPageResponse>('/api/v1/users', {
        params: { page, size },
      });
      return data;
    },
  });
}

export function useInviteUser() {
  return useMutation({
    mutationFn: async (body: InviteRequest) => {
      const { data } = await apiClient.post<InviteResponse>('/api/v1/invites', body);
      return data;
    },
  });
}

export function useChangeRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, role }: { id: string } & ChangeRoleRequest) => {
      await apiClient.patch(`/api/v1/users/${id}/role`, { role });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

export function useDeactivateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/users/${id}/deactivate`, {});
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

export function useReactivateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/users/${id}/reactivate`, {});
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

/** Optimistically update a single user's role in the cache. */
export function updateUserRoleInCache(
  qc: ReturnType<typeof useQueryClient>,
  page: number,
  size: number,
  userId: string,
  role: UserResponse['role'],
) {
  qc.setQueryData<UsersPageResponse>(usersQueryKey(page, size), (prev) => {
    if (!prev) return prev;
    return {
      ...prev,
      content: prev.content.map((u) => (u.id === userId ? { ...u, role } : u)),
    };
  });
}
