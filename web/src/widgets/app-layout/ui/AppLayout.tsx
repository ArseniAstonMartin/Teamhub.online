import { NavLink, Outlet, useNavigate, useMatch } from 'react-router-dom';
import {
  Calendar,
  Briefcase,
  Users,
  BarChart2,
  Settings,
  LogOut,
  Building2,
  UserCog,
  CreditCard,
} from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { useAuthStore, type UserRole } from '@/entities/user/model/authStore';
import { useTenant } from '@/entities/tenant/api/tenantApi';
import { useCurrentUser } from '@/entities/user/api/usersApi';
import { UnsavedChangesGuard } from '@/app/UnsavedChangesGuard';
import apiClient from '@/shared/api/client';
import { AlertBanner } from '@/widgets/AlertBanner';

interface NavItem {
  label: string;
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  allowedRoles: UserRole[];
  subItems?: Array<{ label: string; to: string; icon: React.ComponentType<{ className?: string }> }>;
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
    subItems: [
      { label: 'Tenant', to: '/settings/tenant', icon: Building2 },
      { label: 'Users', to: '/settings/users', icon: UserCog },
      { label: 'Billing', to: '/settings/billing', icon: CreditCard },
    ],
  },
];

export default function AppLayout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const isInSettings = useMatch('/settings/*');
  const role = user?.role ?? 'TECHNICIAN';
  const { data: tenant } = useTenant({ enabled: role === 'ADMIN' });
  const { data: currentUser } = useCurrentUser();
  const displayName = currentUser?.name ?? currentUser?.email ?? user?.id;

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
            <div className="min-w-0">
              <span className="text-lg font-bold text-brand-blue tracking-tight block">
                Teamhub
              </span>
              {tenant?.name && (
                <span className="text-xs text-brand-muted truncate block" data-testid="tenant-name">
                  {tenant.name}
                </span>
              )}
            </div>
          </div>

          {/* Nav */}
          <nav className="flex-1 py-4 px-3 space-y-0.5 overflow-y-auto">
            {visibleItems.map((item) => (
              <div key={item.to}>
                <NavLink
                  to={item.to}
                  end={!item.subItems}
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

                {/* Sub-items — shown when parent route is active */}
                {item.subItems && isInSettings && (
                  <div className="ml-4 mt-0.5 space-y-0.5">
                    {item.subItems.map((sub) => (
                      <NavLink
                        key={sub.to}
                        to={sub.to}
                        className={({ isActive }) =>
                          cn(
                            'flex items-center gap-3 px-3 py-1.5 rounded-md text-xs font-medium transition-colors',
                            isActive
                              ? 'bg-brand-blue/10 text-brand-blue font-semibold'
                              : 'text-brand-muted hover:bg-brand-blue/5 hover:text-brand-navy',
                          )
                        }
                      >
                        <sub.icon className="h-3.5 w-3.5 shrink-0" />
                        {sub.label}
                      </NavLink>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </nav>

          {/* User info + Logout */}
          <div className="border-t border-border px-3 py-4 space-y-2">
            {user && (
              <div className="px-3 py-1" data-testid="sidebar-user-info">
                <p className="text-xs font-semibold text-brand-navy truncate">
                  {displayName}
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
            {tenant?.name && (
              <p className="text-sm font-semibold text-brand-navy truncate" data-testid="header-tenant-name">
                {tenant.name}
              </p>
            )}
            <div className="flex-1" />
            {user && (
              <div
                className="flex items-center gap-2"
                data-testid="header-user-info"
                title={currentUser?.email ?? undefined}
              >
                <span className="text-sm text-brand-muted hidden sm:inline truncate max-w-[12rem]">
                  {displayName}
                </span>
                <div className="h-8 w-8 rounded-full bg-brand-blue/10 flex items-center justify-center">
                  <span className="text-xs font-semibold text-brand-blue">
                    {(currentUser?.name ?? user.role).charAt(0).toUpperCase()}
                  </span>
                </div>
              </div>
            )}
          </header>

          {/* Escalation alert banners — visible to ADMINs only (PRD §28, TASK-118) */}
          <AlertBanner />

          {/* Content */}
          <main className="flex-1 overflow-y-auto px-6 py-6 bg-brand-bg">
            <Outlet />
          </main>
        </div>
      </div>
    </UnsavedChangesGuard>
  );
}
