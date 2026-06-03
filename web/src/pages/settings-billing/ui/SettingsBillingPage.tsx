import { useEffect, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { CreditCard, ExternalLink, AlertTriangle, AlertCircle, ChevronDown, Loader2 } from 'lucide-react';

import { useAuthStore } from '@/entities/user/model/authStore';
import {
  useBillingStatus,
  useCreateCheckout,
  useCreatePortal,
} from '@/entities/billing/api/billingApi';
import type { BillingStatus, PlanKey, PriceKey } from '@/entities/billing/model/types';
import { PLAN_LIMITS, PLAN_DISPLAY_NAMES, PLAN_PRICES } from '@/entities/billing/model/types';
import { Button } from '@/shared/ui/button';
import { cn } from '@/shared/lib/utils';

// ── Plan card skeleton ────────────────────────────────────────────────────────

function PlanCardSkeleton() {
  return (
    <div
      className="rounded-lg border border-border bg-brand-surface p-6"
      aria-label="Loading plan info"
    >
      <div className="flex items-start justify-between mb-6">
        <div className="space-y-2">
          <div className="h-5 w-24 bg-brand-bg rounded animate-pulse" />
          <div className="h-4 w-16 bg-brand-bg rounded animate-pulse" />
        </div>
        <div className="h-6 w-20 bg-brand-bg rounded animate-pulse" />
      </div>
      <div className="grid grid-cols-2 gap-6">
        <div className="space-y-1">
          <div className="h-3 w-20 bg-brand-bg rounded animate-pulse" />
          <div className="h-4 w-32 bg-brand-bg rounded animate-pulse" />
        </div>
        <div className="space-y-1">
          <div className="h-3 w-16 bg-brand-bg rounded animate-pulse" />
          <div className="h-4 w-28 bg-brand-bg rounded animate-pulse" />
        </div>
        <div className="space-y-1">
          <div className="h-3 w-24 bg-brand-bg rounded animate-pulse" />
          <div className="h-4 w-20 bg-brand-bg rounded animate-pulse" />
        </div>
        <div className="space-y-1">
          <div className="h-3 w-20 bg-brand-bg rounded animate-pulse" />
          <div className="h-4 w-24 bg-brand-bg rounded animate-pulse" />
        </div>
      </div>
      {/* TO-DO: wire to GET /api/v1/billing/status when endpoint is added */}
      <p className="mt-4 text-xs text-brand-muted italic">Loading plan info…</p>
    </div>
  );
}

// ── Usage bar ─────────────────────────────────────────────────────────────────

interface UsageBarProps {
  used: number;
  limit: number | null;
  label: string;
}

function UsageBar({ used, limit, label }: UsageBarProps) {
  const pct = limit === null ? 0 : Math.min((used / limit) * 100, 100);
  const isWarning = limit !== null && pct >= 80 && pct < 100;
  const isExceeded = limit !== null && pct >= 100;

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-xs">
        <span className="text-brand-muted">{label}</span>
        <span className={cn(
          'font-medium',
          isExceeded ? 'text-red-600' : isWarning ? 'text-amber-600' : 'text-brand-navy',
        )}>
          {limit === null ? `${used} / ∞` : `${used} / ${limit}`}
        </span>
      </div>
      {limit !== null && (
        <div className="h-1.5 w-full rounded-full bg-brand-bg overflow-hidden">
          <div
            className={cn(
              'h-full rounded-full transition-all',
              isExceeded ? 'bg-red-500' : isWarning ? 'bg-amber-400' : 'bg-brand-blue',
            )}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  );
}

// ── Plan card (with real data) ────────────────────────────────────────────────

function PlanCard({ status }: { status: BillingStatus }) {
  const limits = PLAN_LIMITS[status.plan];
  const periodEnd = new Date(status.periodEnd).toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });

  return (
    <div className="rounded-lg border border-border bg-brand-surface p-6">
      <div className="flex items-start justify-between mb-6">
        <div>
          <h2 className="text-lg font-semibold text-brand-navy">
            {PLAN_DISPLAY_NAMES[status.plan]}
          </h2>
          <p className="text-sm text-brand-muted">{PLAN_PRICES[status.plan]}</p>
        </div>
        <span className="inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-xs font-medium text-brand-navy bg-brand-bg">
          Active
        </span>
      </div>

      <div className="space-y-4">
        <UsageBar
          used={status.jobsThisPeriod}
          limit={limits.jobsPerMonth}
          label="Jobs this period"
        />
        <UsageBar
          used={status.seats}
          limit={limits.seats}
          label="Seats"
        />
      </div>

      <p className="mt-4 text-xs text-brand-muted">
        Current period ends {periodEnd}
      </p>
    </div>
  );
}

// ── Banners ───────────────────────────────────────────────────────────────────

interface BannersProps {
  // TO-DO: replace mock with real API data from GET /api/v1/billing/status
  billingStatus: BillingStatus | null;
  onOpenPortal: () => void;
}

function Banners({ billingStatus, onOpenPortal }: BannersProps) {
  if (billingStatus === null) return null;

  const limits = PLAN_LIMITS[billingStatus.plan];
  const jobsPct =
    limits.jobsPerMonth !== null
      ? billingStatus.jobsThisPeriod / limits.jobsPerMonth
      : 0;

  const isAt80 = jobsPct >= 0.8 && jobsPct < 1;
  const isAt100 = jobsPct >= 1;
  const isPaymentFailed = billingStatus.paymentFailed;

  return (
    <div className="space-y-3">
      {isAt100 && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3"
        >
          <AlertCircle className="h-4 w-4 text-red-600 mt-0.5 shrink-0" />
          <div className="text-sm text-red-800">
            <span className="font-semibold">Job quota reached.</span> New jobs cannot be created until your plan resets or you upgrade.
          </div>
        </div>
      )}

      {isAt80 && !isAt100 && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3"
        >
          <AlertTriangle className="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
          <div className="text-sm text-amber-800">
            <span className="font-semibold">80% of job quota used.</span> Consider upgrading your plan to avoid disruption.
          </div>
        </div>
      )}

      {isPaymentFailed && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3"
        >
          <AlertCircle className="h-4 w-4 text-red-600 mt-0.5 shrink-0" />
          <div className="flex-1 text-sm text-red-800">
            <span className="font-semibold">Payment failed.</span> Your workspace may be suspended soon.{' '}
            <button
              onClick={onOpenPortal}
              className="underline hover:no-underline font-medium"
            >
              Update payment method →
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Upgrade dropdown ──────────────────────────────────────────────────────────

const UPGRADE_TIERS: Array<{ key: PriceKey; label: string; planKey: PlanKey }> = [
  { key: 'starter', label: 'Starter — $49/mo · 200 jobs · 5 seats', planKey: 'STARTER' },
  { key: 'team',    label: 'Team — $149/mo · 1,000 jobs · 20 seats', planKey: 'TEAM' },
  { key: 'enterprise', label: 'Enterprise — Custom · Unlimited', planKey: 'ENTERPRISE' },
];

interface UpgradeButtonProps {
  onUpgrade: (priceKey: PriceKey) => void;
  isPending: boolean;
}

function UpgradeButton({ onUpgrade, isPending }: UpgradeButtonProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <Button
        onClick={() => setOpen((v) => !v)}
        disabled={isPending}
        aria-label="Upgrade"
        aria-haspopup="true"
        aria-expanded={open}
      >
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 mr-2 animate-spin" aria-label="Loading" />
            Redirecting…
          </>
        ) : (
          <>
            <CreditCard className="h-4 w-4 mr-2" />
            Upgrade
            <ChevronDown className="h-4 w-4 ml-1" />
          </>
        )}
      </Button>

      {open && !isPending && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-10"
            onClick={() => setOpen(false)}
            aria-hidden
          />
          <div
            className="absolute left-0 mt-2 w-72 rounded-md border border-border bg-brand-surface shadow-lg z-20"
            role="menu"
          >
            {UPGRADE_TIERS.map((tier) => (
              <button
                key={tier.key}
                role="menuitem"
                className="block w-full px-4 py-2.5 text-left text-sm text-brand-navy hover:bg-brand-bg transition-colors first:rounded-t-md last:rounded-b-md"
                onClick={() => {
                  setOpen(false);
                  onUpgrade(tier.key);
                }}
              >
                {tier.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function SettingsBillingPage() {
  const user = useAuthStore((s) => s.user);

  if (user && user.role !== 'ADMIN') {
    return <Navigate to="/settings" replace />;
  }

  return <SettingsBillingPageInner />;
}

function SettingsBillingPageInner() {
  const [searchParams, setSearchParams] = useSearchParams();

  // TO-DO: replace mock with real API data from GET /api/v1/billing/status
  const billingStatus = useBillingStatus();

  const checkoutMutation = useCreateCheckout();
  const portalMutation = useCreatePortal();

  // Handle Stripe return with ?checkout=success
  useEffect(() => {
    if (searchParams.get('checkout') === 'success') {
      toast.success('Subscription updated successfully!');
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.delete('checkout');
        return next;
      });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleUpgrade(priceKey: PriceKey) {
    try {
      const { url } = await checkoutMutation.mutateAsync(priceKey);
      window.location.href = url;
    } catch {
      toast.error('Failed to open checkout. Please try again.');
    }
  }

  async function handlePortal() {
    try {
      const { url } = await portalMutation.mutateAsync();
      window.location.href = url;
    } catch {
      toast.error('Failed to open billing portal. Please try again.');
    }
  }

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-semibold text-brand-navy">Billing</h1>
        <p className="mt-1 text-sm text-brand-muted">
          Manage your subscription and payment details.
        </p>
      </div>

      {/* Banners */}
      <Banners
        billingStatus={billingStatus}
        onOpenPortal={() => void handlePortal()}
      />

      {/* Plan card */}
      {billingStatus === null ? (
        <PlanCardSkeleton />
      ) : (
        <PlanCard status={billingStatus} />
      )}

      {/* Action buttons */}
      <div className="flex items-center gap-3">
        <UpgradeButton
          onUpgrade={(key) => void handleUpgrade(key)}
          isPending={checkoutMutation.isPending}
        />

        <Button
          variant="outline"
          onClick={() => void handlePortal()}
          disabled={portalMutation.isPending}
          aria-label="Manage billing"
        >
          {portalMutation.isPending ? (
            <>
              <Loader2 className="h-4 w-4 mr-2 animate-spin" aria-label="Loading" />
              Redirecting…
            </>
          ) : (
            <>
              <ExternalLink className="h-4 w-4 mr-2" />
              Manage Billing
            </>
          )}
        </Button>
      </div>
    </div>
  );
}
