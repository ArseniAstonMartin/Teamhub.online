import { create } from 'zustand';
import { isJwtExpired, parseJwtPayload } from '@/shared/lib/jwt';
import type { StaffRole, StaffUser } from './types';

interface StaffAuthState {
  accessToken: string | null;
  user: StaffUser | null;
  setAccessToken(token: string): void;
  logout(): void;
  isStaffAuthenticated(): boolean;
}

export const STAFF_TOKEN_KEY = 'teamhub_staff_token';

function staffUserFromToken(token: string): StaffUser | null {
  const payload = parseJwtPayload(token);
  if (!payload || payload.role !== ('TEAMHUB_STAFF' satisfies StaffRole)) {
    return null;
  }
  return { id: payload.sub, role: 'TEAMHUB_STAFF' };
}

function loadStoredStaffToken(): string | null {
  const token = sessionStorage.getItem(STAFF_TOKEN_KEY);
  if (!token || isJwtExpired(token)) {
    sessionStorage.removeItem(STAFF_TOKEN_KEY);
    return null;
  }
  return staffUserFromToken(token) ? token : null;
}

const initialToken = loadStoredStaffToken();

export const useStaffAuthStore = create<StaffAuthState>((set, get) => ({
  accessToken: initialToken,
  user: initialToken ? staffUserFromToken(initialToken) : null,

  setAccessToken(token) {
    sessionStorage.setItem(STAFF_TOKEN_KEY, token);
    set({ accessToken: token, user: staffUserFromToken(token) });
  },

  logout() {
    sessionStorage.removeItem(STAFF_TOKEN_KEY);
    set({ accessToken: null, user: null });
  },

  isStaffAuthenticated() {
    const { accessToken, user } = get();
    return Boolean(accessToken && user?.role === 'TEAMHUB_STAFF' && !isJwtExpired(accessToken));
  },
}));
