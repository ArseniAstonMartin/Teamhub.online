import { createBrowserRouter, Navigate } from 'react-router-dom';
import AuthGate from '@/app/AuthGate';
import AppLayout from '@/widgets/app-layout/ui/AppLayout';
import LoginPage from '@/pages/login/ui/LoginPage';
import ForgotPasswordPage from '@/pages/forgot-password/ui/ForgotPasswordPage';
import ResetPasswordPage from '@/pages/reset-password/ui/ResetPasswordPage';
import AcceptInvitePage from '@/pages/accept-invite/ui/AcceptInvitePage';
import DesignSystemPage from '@/pages/design-system/ui/DesignSystemPage';
import SchedulePage from '@/pages/schedule/ui/SchedulePage';
import JobsPage from '@/pages/jobs/ui/JobsPage';
import CustomersPage from '@/pages/customers/ui/CustomersPage';
import ReportsPage from '@/pages/reports/ui/ReportsPage';
import SettingsPage from '@/pages/settings/ui/SettingsPage';

export const router = createBrowserRouter([
  // ── Public routes ───────────────────────────────────────────────────
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/accept-invite', element: <AcceptInvitePage /> },
  { path: '/design-system', element: <DesignSystemPage /> },

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
          { path: '/customers', element: <CustomersPage /> },
          { path: '/reports', element: <ReportsPage /> },
          { path: '/settings/*', element: <SettingsPage /> },
        ],
      },
    ],
  },
]);
