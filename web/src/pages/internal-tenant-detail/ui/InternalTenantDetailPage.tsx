import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';
import { ArrowLeft } from 'lucide-react';

import { useInternalTenant, useResendOwnerInvite } from '@/entities/staff/api/internalApi';
import { Button } from '@/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';

interface ApiError {
  error: {
    code: string;
    message: string;
  };
}

function formatDateTime(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function InternalTenantDetailPage() {
  const { id = '' } = useParams();
  const { data: tenant, isLoading, isError } = useInternalTenant(id);
  const resendInvite = useResendOwnerInvite();

  async function handleResendInvite() {
    try {
      await resendInvite.mutateAsync(id);
      toast.success('Owner invite resent');
      if (import.meta.env.DEV) {
        toast.message('Check MailHog at http://localhost:8025');
      }
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      toast.error(axiosErr.response?.data?.error?.message ?? 'Failed to resend invite');
    }
  }

  if (isLoading) {
    return <p className="text-sm text-brand-muted">Loading tenant…</p>;
  }

  if (isError || !tenant) {
    return (
      <div className="space-y-4">
        <Link
          to="/__internal/tenants"
          className="inline-flex items-center gap-1 text-sm text-brand-blue hover:underline"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to tenants
        </Link>
        <p className="text-sm text-destructive">Tenant not found.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <Link
          to="/__internal/tenants"
          className="inline-flex items-center gap-1 text-sm text-brand-blue hover:underline mb-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to tenants
        </Link>
        <h1 className="text-2xl font-semibold text-brand-navy">{tenant.name}</h1>
        <p className="text-sm text-brand-muted font-mono mt-1">{tenant.id}</p>
      </div>

      <Card className="bg-brand-surface border-border">
        <CardHeader>
          <CardTitle className="text-lg text-brand-navy">Overview</CardTitle>
          <CardDescription>Tenant workspace details</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2 text-sm">
            <div>
              <dt className="text-brand-muted">Plan</dt>
              <dd className="font-medium capitalize">{tenant.plan.toLowerCase()}</dd>
            </div>
            <div>
              <dt className="text-brand-muted">Created</dt>
              <dd className="font-medium">{formatDateTime(tenant.createdAt)}</dd>
            </div>
            <div>
              <dt className="text-brand-muted">Users</dt>
              <dd className="font-medium">{tenant.userCount}</dd>
            </div>
            <div>
              <dt className="text-brand-muted">Jobs</dt>
              <dd className="font-medium">{tenant.jobCount}</dd>
            </div>
            <div>
              <dt className="text-brand-muted">Billing period end</dt>
              <dd className="font-medium">{formatDateTime(tenant.currentPeriodEnd)}</dd>
            </div>
            <div>
              <dt className="text-brand-muted">Active schema</dt>
              <dd className="font-medium">{tenant.activeSchemaName ?? '—'}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card className="bg-brand-surface border-border">
        <CardHeader>
          <CardTitle className="text-lg text-brand-navy">Owner invite</CardTitle>
          <CardDescription>
            Resend the first Admin invite email if the owner has not accepted yet.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            type="button"
            variant="outline"
            onClick={() => void handleResendInvite()}
            disabled={resendInvite.isPending}
          >
            {resendInvite.isPending ? 'Sending…' : 'Resend owner invite'}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
