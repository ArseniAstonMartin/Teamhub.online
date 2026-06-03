import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

import { useAuthStore } from '@/entities/user/model/authStore';
import { useTenantSettings, useUpdateTenantSettings } from '@/entities/tenant/api/tenantApi';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import { cn } from '@/shared/lib/utils';

// ── Constants ─────────────────────────────────────────────────────────────────

const US_TIMEZONES = [
  { value: 'America/New_York', label: 'Eastern Time (ET)' },
  { value: 'America/Chicago', label: 'Central Time (CT)' },
  { value: 'America/Denver', label: 'Mountain Time (MT)' },
  { value: 'America/Los_Angeles', label: 'Pacific Time (PT)' },
  { value: 'America/Phoenix', label: 'Arizona (no DST)' },
  { value: 'America/Anchorage', label: 'Alaska Time (AKT)' },
  { value: 'Pacific/Honolulu', label: 'Hawaii Time (HST)' },
];

// ── Toggle Switch ─────────────────────────────────────────────────────────────

interface ToggleSwitchProps {
  id: string;
  checked: boolean;
  disabled?: boolean;
  loading?: boolean;
  onChange: (checked: boolean) => void;
}

function ToggleSwitch({ id, checked, disabled, loading, onChange }: ToggleSwitchProps) {
  return (
    <button
      id={id}
      role="switch"
      aria-checked={checked}
      disabled={disabled || loading}
      type="button"
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed disabled:opacity-50',
        checked ? 'bg-brand-blue' : 'bg-gray-200',
      )}
    >
      {loading ? (
        <span className="flex h-5 w-5 items-center justify-center">
          <Loader2 className="h-3 w-3 animate-spin text-white" />
        </span>
      ) : (
        <span
          className={cn(
            'pointer-events-none block h-5 w-5 rounded-full bg-white shadow-md ring-0 transition-transform',
            checked ? 'translate-x-5' : 'translate-x-0',
          )}
        />
      )}
    </button>
  );
}

// ── Section Card ──────────────────────────────────────────────────────────────

function SectionCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-brand-surface">
      <div className="border-b border-border px-5 py-3">
        <h2 className="text-sm font-semibold text-brand-navy">{title}</h2>
      </div>
      <div className="px-5 py-4 space-y-4">{children}</div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function SettingsTenantPage() {
  const currentUser = useAuthStore((s) => s.user);

  if (currentUser && currentUser.role !== 'ADMIN') {
    return <Navigate to="/schedule" replace />;
  }

  return <SettingsTenantPageInner />;
}

function SettingsTenantPageInner() {
  const { data: settings, isLoading, isError } = useTenantSettings();
  const updateSettings = useUpdateTenantSettings();

  // Local optimistic state for close jobs toggle
  const [optimisticCloseJobs, setOptimisticCloseJobs] = useState<boolean | null>(null);

  // TO-DO: fetch from GET /api/v1/tenant when endpoint exists
  const [teamName] = useState('');

  // TO-DO: wire to PATCH /api/v1/tenant/settings when timezone field added
  const [timezone, setTimezone] = useState('America/New_York');

  const closeJobsEnabled = optimisticCloseJobs ?? settings?.closeJobsEnabled ?? false;

  async function handleCloseJobsToggle(newValue: boolean) {
    const previousValue = closeJobsEnabled;
    // Optimistic update — update UI immediately before response
    setOptimisticCloseJobs(newValue);
    try {
      await updateSettings.mutateAsync({ closeJobsEnabled: newValue });
      // Success — server confirmed; clear optimistic override (server state in cache now)
      setOptimisticCloseJobs(null);
    } catch {
      // Rollback on failure
      setOptimisticCloseJobs(previousValue);
      toast.error('Failed to update setting. Please try again.');
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 text-sm text-brand-muted" data-testid="loading-state">
        <Loader2 className="h-5 w-5 animate-spin mr-2" />
        Loading settings…
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center py-20 text-sm text-red-600">
        Failed to load tenant settings.
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <h1 className="text-2xl font-semibold text-brand-navy">Tenant Settings</h1>

      {/* Jobs Settings */}
      <SectionCard title="Jobs Settings">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-0.5">
            <Label htmlFor="close-jobs-toggle" className="text-sm font-medium text-brand-navy cursor-pointer">
              Allow Technicians to mark jobs Completed
            </Label>
            <p className="text-xs text-brand-muted">
              When enabled, Technicians can close their assigned jobs directly from the mobile app.
              When disabled, only Admins and Dispatchers can mark jobs as Completed.
            </p>
          </div>
          <ToggleSwitch
            id="close-jobs-toggle"
            checked={closeJobsEnabled}
            loading={updateSettings.isPending}
            onChange={(v) => void handleCloseJobsToggle(v)}
          />
        </div>
      </SectionCard>

      {/* Workspace */}
      <SectionCard title="Workspace">
        {/* Team display name */}
        <div className="space-y-1.5">
          <Label htmlFor="team-name" className="text-sm font-medium text-brand-navy">
            Team display name
          </Label>
          {/* TO-DO: wire to PATCH /api/v1/tenant endpoint when available */}
          <div className="flex gap-2">
            <Input
              id="team-name"
              value={teamName}
              placeholder="Your team name"
              disabled
              className="flex-1"
              aria-describedby="team-name-hint"
            />
            <Button
              type="button"
              variant="outline"
              disabled
              title="Coming soon"
              aria-label="Save team name (coming soon)"
            >
              Save
            </Button>
          </div>
          <p id="team-name-hint" className="text-xs text-brand-muted">
            Coming soon — team name editing will be available in a future update.
          </p>
        </div>

        {/* Default time zone */}
        <div className="space-y-1.5">
          <Label htmlFor="timezone-select" className="text-sm font-medium text-brand-navy">
            Default time zone
          </Label>
          {/* TO-DO: wire to PATCH /api/v1/tenant/settings when timezone field added */}
          <div className="flex gap-2">
            <Select value={timezone} onValueChange={setTimezone} disabled>
              <SelectTrigger id="timezone-select" className="flex-1">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {US_TIMEZONES.map((tz) => (
                  <SelectItem key={tz.value} value={tz.value}>
                    {tz.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant="outline"
              disabled
              title="Coming soon"
              aria-label="Save time zone (coming soon)"
            >
              Save
            </Button>
          </div>
          <p className="text-xs text-brand-muted">
            Coming soon — time zone configuration will be available in a future update.
          </p>
        </div>
      </SectionCard>
    </div>
  );
}
