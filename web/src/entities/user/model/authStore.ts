import { create } from 'zustand';

export type UserRole = 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';

interface JwtPayload {
  sub: string;
  tenant_id: string;
  role: UserRole;
  exp: number;
  iat: number;
}

interface AuthUser {
  id: string;
  tenantId: string;
  role: UserRole;
}

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  setTokens(accessToken: string, refreshToken: string): void;
  logout(): void;
}

const REFRESH_TOKEN_KEY = 'teamhub_refresh_token';

function parseJwtPayload(token: string): JwtPayload | null {
  try {
    const [, payloadB64] = token.split('.');
    // atob works in modern browsers; Node ≥16 also supports it via globalThis
    const json = atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,

  setTokens(accessToken, refreshToken) {
    const payload = parseJwtPayload(accessToken);
    const user: AuthUser | null = payload
      ? { id: payload.sub, tenantId: payload.tenant_id, role: payload.role }
      : null;

    // Access token lives in Zustand memory only — never written to any
    // persistent storage so it cannot be read by XSS code after a hard reload.
    // Refresh token goes to localStorage because httpOnly cookie transport
    // requires CORS `credentials: 'include'` + matching server-side
    // Access-Control-Allow-Credentials config that is not wired yet.
    // localStorage is acceptable here given: short-lived access tokens (15 min),
    // SameSite context (same origin SPA), and the refresh token itself provides
    // no access without the server-side secret.
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);

    set({ accessToken, user });
  },

  logout() {
    // Caller is responsible for calling POST /api/v1/auth/logout first when
    // possible so the refresh token is invalidated server-side.
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    set({ accessToken: null, user: null });
  },
}));

export { REFRESH_TOKEN_KEY };
