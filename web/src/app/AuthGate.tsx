import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore, REFRESH_TOKEN_KEY } from '@/entities/user/model/authStore';

/**
 * Protects child routes from unauthenticated access.
 *
 * Strategy:
 * - If the access token is present in the Zustand store → already authenticated, render.
 * - If no access token BUT a refresh token exists in localStorage → the user has
 *   reloaded the page and lost the in-memory token. Render the outlet; the first
 *   protected API call will get a 401, the Axios interceptor will exchange the
 *   refresh token transparently, and the user never sees a redirect.
 * - If neither is present → redirect to /login with the original path as a
 *   `redirect` query param so the login page can deep-link back after sign-in.
 */
export default function AuthGate() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const location = useLocation();

  const hasRefreshToken = Boolean(localStorage.getItem(REFRESH_TOKEN_KEY));

  if (!accessToken && !hasRefreshToken) {
    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  }

  return <Outlet />;
}
