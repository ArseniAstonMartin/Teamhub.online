import { createBrowserRouter, Navigate } from 'react-router-dom';
import AuthGate from '@/app/AuthGate';
import InternalGate from '@/app/InternalGate';
import AppLayout from '@/widgets/app-layout/ui/AppLayout';
import InternalLayout from '@/widgets/internal-layout/ui/InternalLayout';
import LoginPage from '@/pages/login/ui/LoginPage';
import InternalLoginPage from '@/pages/internal-login/ui/InternalLoginPage';
import InternalTenantsPage from '@/pages/internal-tenants/ui/InternalTenantsPage';
import InternalTenantDetailPage from '@/pages/internal-tenant-detail/ui/InternalTenantDetailPage';
import ForgotPasswordPage from '@/pages/forgot-password/ui/ForgotPasswordPage';
import ResetPasswordPage from '@/pages/reset-password/ui/ResetPasswordPage';
import AcceptInvitePage from '@/pages/accept-invite/ui/AcceptInvitePage';
import DesignSystemPage from '@/pages/design-system/ui/DesignSystemPage';
import SchedulePage from '@/pages/schedule/ui/SchedulePage';
import JobsPage from '@/pages/jobs/ui/JobsPage';
import JobDetailPage from '@/pages/job-detail/ui/JobDetailPage';
import CustomersPage from '@/pages/customers/ui/CustomersPage';
import CustomerDetailPage from '@/pages/customers/ui/CustomerDetailPage';
import ReportsPage from '@/pages/reports/ui/ReportsPage';
import SettingsPage from '@/pages/settings/ui/SettingsPage';
import AuthCallbackPage from '@/pages/auth-callback/ui/AuthCallbackPage';
import AuthErrorPage from '@/pages/auth-error/ui/AuthErrorPage';
import SchemaEditorPage from '@/pages/schema-editor/ui/SchemaEditorPage';
import AutomationsPage from '@/pages/automations/ui/AutomationsPage';
import SettingsUsersPage from '@/pages/settings-users/ui/SettingsUsersPage';
import SettingsTenantPage from '@/pages/settings-tenant/ui/SettingsTenantPage';
import SettingsBillingPage from '@/pages/settings-billing/ui/SettingsBillingPage';

export const router = createBrowserRouter([
  // ── Public routes ───────────────────────────────────────────────────
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/accept-invite', element: <AcceptInvitePage /> },
  { path: '/design-system', element: <DesignSystemPage /> },
  { path: '/auth/callback', element: <AuthCallbackPage /> },
  { path: '/auth/error', element: <AuthErrorPage /> },

  // ── Internal staff console (PRD §6.4) — not linked from tenant nav ──
  { path: '/__internal/login', element: <InternalLoginPage /> },
  {
    element: <InternalGate />,
    children: [
      {
        element: <InternalLayout />,
        children: [
          { path: '/__internal/tenants', element: <InternalTenantsPage /> },
          { path: '/__internal/tenants/:id', element: <InternalTenantDetailPage /> },
        ],
      },
    ],
  },

  // ── Protected routes ────────────────────────────────────────────────
  {
    element: <AuthGate />,
    children: [
      // Root redirect → schedule
      { path: '/', element: <Navigate to="/schedule" replace /> },

      {
        element: <AppLayout />,
        children: [
          { path: '/schedule', element: <SchedulePage /> },
          { path: '/jobs', element: <JobsPage /> },
          { path: '/jobs/:id', element: <JobDetailPage /> },
          { path: '/customers', element: <CustomersPage /> },
          { path: '/customers/:id', element: <CustomerDetailPage /> },
          { path: '/reports', element: <ReportsPage /> },
          { path: '/settings/*', element: <SettingsPage /> },
          { path: '/settings/users', element: <SettingsUsersPage /> },
          { path: '/settings/tenant', element: <SettingsTenantPage /> },
          { path: '/settings/billing', element: <SettingsBillingPage /> },
          { path: '/schema-editor', element: <SchemaEditorPage /> },
          { path: '/automations', element: <AutomationsPage /> },
        ],
      },
    ],
  },
]);
