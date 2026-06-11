import { Link, Outlet, useNavigate } from 'react-router-dom';
import { Building2, LogOut } from 'lucide-react';
import { useStaffAuthStore } from '@/entities/staff/model/staffAuthStore';

export default function InternalLayout() {
  const logout = useStaffAuthStore((s) => s.logout);
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/__internal/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-brand-bg font-sans">
      <header className="border-b border-border bg-brand-surface">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <div className="flex items-center gap-6">
            <span className="text-sm font-bold text-brand-blue tracking-tight">Teamhub Internal</span>
            <nav className="flex items-center gap-4 text-sm">
              <Link
                to="/__internal/tenants"
                className="flex items-center gap-1.5 text-brand-muted hover:text-brand-navy"
              >
                <Building2 className="h-4 w-4" />
                Tenants
              </Link>
            </nav>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="flex items-center gap-2 text-sm text-brand-muted hover:text-brand-urgent"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
