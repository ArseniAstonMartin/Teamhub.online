import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Calendar,
  Briefcase,
  Users,
  BarChart2,
  Settings,
  LogOut,
} from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { useAuthStore, type UserRole } from '@/entities/user/model/authStore';
import { UnsavedChangesGuard } from '@/app/UnsavedChangesGuard';
import apiClient from '@/shared/api/client';

interface NavItem {
  label: string;
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  allowedRoles: UserRole[];
}

const NAV_ITEMS: NavItem[] = [
  {
    label: 'Schedule',
    to: '/schedule',
    icon: Calendar,
    allowedRoles: ['ADMIN', 'DISPATCHER', 'TECHNICIAN'],
  },
  {
    label: 'Jobs',
    to: '/jobs',
    icon: Briefcase,
    allowedRoles: ['ADMIN', 'DISPATCHER', 'TECHNICIAN'],
  },
  {
    label: 'Customers',
    to: '/customers',
    icon: Users,
    allowedRoles: ['ADMIN', 'DISPATCHER'],
  },
  {
    label: 'Reports',
    to: '/reports',
    icon: BarChart2,
    allowedRoles: ['ADMIN', 'DISPATCHER'],
  },
  {
    label: 'Settings',
    to: '/settings',
    icon: Settings,
    allowedRoles: ['ADMIN'],
  },
];

export default function AppLayout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const role = user?.role ?? 'TECHNICIAN';

  const visibleItems = NAV_ITEMS.filter((item) =>
    item.allowedRoles.includes(role),
  );

  async function handleLogout() {
    try {
      await apiClient.post('/api/v1/auth/logout', {
        refreshToken: localStorage.getItem('teamhub_refresh_token'),
      });
    } catch {
      // Best-effort: invalidate server-side if possible, but always clear locally
    } finally {
      logout();
      navigate('/login', { replace: true });
    }
  }

  return (
    <UnsavedChangesGuard>
      <div className="flex h-screen overflow-hidden bg-brand-bg">
        {/* ── Sidebar ─────────────────────────────────────────────────── */}
        <aside className="flex flex-col w-60 shrink-0 bg-brand-surface border-r border-border">
          {/* Logo */}
          <div className="h-16 flex items-center px-5 border-b border-border">
            <span className="text-lg font-bold text-brand-blue tracking-tight">
              Teamhub
            </span>
          </div>

          {/* Nav */}
          <nav className="flex-1 py-4 px-3 space-y-0.5 overflow-y-auto">
            {visibleItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-brand-blue/10 text-brand-blue font-semibold'
                      : 'text-brand-muted hover:bg-brand-blue/5 hover:text-brand-navy',
                  )
                }
              >
                <item.icon className="h-4 w-4 shrink-0" />
                {item.label}
              </NavLink>
            ))}
          </nav>

          {/* User info + Logout */}
          <div className="border-t border-border px-3 py-4 space-y-2">
            {user && (
              <div className="px-3 py-1">
                <p className="text-xs font-semibold text-brand-navy truncate">
                  {user.id}
                </p>
                <p className="text-xs text-brand-muted capitalize">
                  {user.role.toLowerCase()}
                </p>
              </div>
            )}
            <button
              onClick={() => void handleLogout()}
              className="flex items-center gap-3 w-full px-3 py-2 rounded-md text-sm font-medium text-brand-muted hover:text-brand-urgent hover:bg-brand-urgent/5 transition-colors"
            >
              <LogOut className="h-4 w-4 shrink-0" />
              Sign out
            </button>
          </div>
        </aside>

        {/* ── Main area ────────────────────────────────────────────────── */}
        <div className="flex flex-col flex-1 overflow-hidden">
          {/* Header */}
          <header className="h-16 shrink-0 flex items-center px-6 bg-brand-surface border-b border-border">
            <div className="flex-1" />
            {user && (
              <div className="flex items-center gap-2">
                <div className="h-8 w-8 rounded-full bg-brand-blue/10 flex items-center justify-center">
                  <span className="text-xs font-semibold text-brand-blue">
                    {user.role.charAt(0)}
                  </span>
                </div>
              </div>
            )}
          </header>

          {/* Content */}
          <main className="flex-1 overflow-y-auto px-6 py-6 bg-brand-bg">
            <Outlet />
          </main>
        </div>
      </div>
    </UnsavedChangesGuard>
  );
}
