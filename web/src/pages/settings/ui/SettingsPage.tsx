import { Navigate } from 'react-router-dom';

/** Settings index — sub-routes (tenant, users, billing) hold the real UI. */
export default function SettingsPage() {
  return <Navigate to="/settings/tenant" replace />;
}
