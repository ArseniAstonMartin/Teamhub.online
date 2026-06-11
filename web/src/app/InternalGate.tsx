import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore, REFRESH_TOKEN_KEY } from '@/entities/user/model/authStore';
import { useStaffAuthStore } from '@/entities/staff/model/staffAuthStore';
import NotFoundPage from '@/pages/not-found/ui/NotFoundPage';

/**
 * Protects `/__internal/*` routes (except login) for TEAMHUB_STAFF only.
 *
 * - Valid staff JWT → render child routes.
 * - Active tenant session (no staff role) → generic 404 (PRD §6.4).
 * - No staff session → redirect to staff login.
 */
export default function InternalGate() {
  const staffUser = useStaffAuthStore((s) => s.user);
  const accessToken = useStaffAuthStore((s) => s.accessToken);
  const isStaffAuthenticated = useStaffAuthStore((s) => s.isStaffAuthenticated);
  const tenantAccessToken = useAuthStore((s) => s.accessToken);
  const hasTenantRefresh = Boolean(localStorage.getItem(REFRESH_TOKEN_KEY));
  const location = useLocation();

  if (isStaffAuthenticated() && staffUser?.role === 'TEAMHUB_STAFF' && accessToken) {
    return <Outlet />;
  }

  if (tenantAccessToken || hasTenantRefresh) {
    return <NotFoundPage />;
  }

  return (
    <Navigate
      to={`/__internal/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}
      replace
    />
  );
}
