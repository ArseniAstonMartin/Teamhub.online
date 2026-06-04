import { create } from 'zustand';
import { parseJwtPayload } from '../utils/parseJwt';
import { saveRefreshToken, deleteRefreshToken } from '../utils/secureStore';

interface AuthUser {
  id: string;
  tenantId: string;
  role: 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';
}

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  setTokens: (accessToken: string, refreshToken: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  async setTokens(accessToken, refreshToken) {
    const payload = parseJwtPayload(accessToken);
    const user: AuthUser | null = payload
      ? {
          id: String(payload.sub),
          tenantId: String(payload.tenant_id),
          role: payload.role as AuthUser['role'],
        }
      : null;
    await saveRefreshToken(refreshToken);
    set({ accessToken, user });
  },
  async logout() {
    await deleteRefreshToken();
    set({ accessToken: null, user: null });
  },
}));
