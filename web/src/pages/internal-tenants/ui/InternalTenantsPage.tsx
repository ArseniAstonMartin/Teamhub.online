import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';

import {
  useInternalTenants,
  useProvisionTenant,
  useSchemaPresets,
} from '@/entities/staff/api/internalApi';
import type { ProvisionTenantResponse } from '@/entities/staff/model/types';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';

const createSchema = z.object({
  name: z.string().min(1, 'Tenant name is required'),
  ownerEmail: z.string().email('Enter a valid owner email'),
});

type CreateFormValues = z.infer<typeof createSchema>;

interface ApiError {
  error: {
    code: string;
    message: string;
  };
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export default function InternalTenantsPage() {
  const { data: tenantsPage, isLoading } = useInternalTenants();
  const { data: presets } = useSchemaPresets();
  const provision = useProvisionTenant();
  const [lastProvision, setLastProvision] = useState<ProvisionTenantResponse | null>(null);
  const [selectedPreset, setSelectedPreset] = useState<string>('');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
  });

  async function onCreate(values: CreateFormValues) {
    try {
      const result = await provision.mutateAsync({
        name: values.name,
        ownerEmail: values.ownerEmail,
        presetKey: selectedPreset || undefined,
      });
      setLastProvision(result);
      reset({ name: '', ownerEmail: '' });
      setSelectedPreset('');
      toast.success('Tenant provisioned — invite email sent');
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      toast.error(axiosErr.response?.data?.error?.message ?? 'Failed to provision tenant');
    }
  }

  const tenants = tenantsPage?.content ?? [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-brand-navy">Tenants</h1>
        <p className="text-sm text-brand-muted mt-1">
          Provision new tenant workspaces and manage onboarding invites.
        </p>
      </div>

      <Card className="bg-brand-surface border-border">
        <CardHeader>
          <CardTitle className="text-lg text-brand-navy">Create tenant</CardTitle>
          <CardDescription>
            Creates the workspace, applies a schema preset, and sends the first Admin invite.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onCreate)} className="space-y-4 max-w-md">
            <div className="space-y-1.5">
              <Label htmlFor="tenant-name">Tenant name</Label>
              <Input id="tenant-name" placeholder="Acme Plumbing" {...register('name')} />
              {errors.name && (
                <p className="text-sm text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="owner-email">Owner email</Label>
              <Input
                id="owner-email"
                type="email"
                placeholder="owner@company.com"
                {...register('ownerEmail')}
              />
              {errors.ownerEmail && (
                <p className="text-sm text-destructive">{errors.ownerEmail.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="preset-key">Schema preset</Label>
              <Select
                value={selectedPreset || undefined}
                onValueChange={setSelectedPreset}
              >
                <SelectTrigger id="preset-key">
                  <SelectValue placeholder="Select a preset (optional)" />
                </SelectTrigger>
                <SelectContent>
                  {(presets ?? []).map((preset) => (
                    <SelectItem key={preset.presetKey} value={preset.presetKey}>
                      {preset.schemaName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button type="submit" disabled={isSubmitting || provision.isPending}>
              {isSubmitting || provision.isPending ? 'Provisioning…' : 'Create tenant'}
            </Button>
          </form>

          {lastProvision && (
            <div
              className="mt-6 rounded-md border border-border bg-brand-bg p-4 text-sm space-y-2"
              data-testid="provision-success"
            >
              <p className="font-medium text-brand-navy">Invite sent successfully</p>
              <p className="text-brand-muted">
                Tenant <span className="font-mono text-xs">{lastProvision.tenantId}</span> created.
                Invite <span className="font-mono text-xs">{lastProvision.inviteId}</span> sent to{' '}
                <span className="font-medium">{lastProvision.ownerEmail}</span>.
              </p>
              {import.meta.env.DEV && (
                <p className="text-brand-muted">
                  Check MailHog at{' '}
                  <a
                    href="http://localhost:8025"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-brand-blue hover:underline"
                  >
                    http://localhost:8025
                  </a>{' '}
                  for the invite email.
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="bg-brand-surface border-border">
        <CardHeader>
          <CardTitle className="text-lg text-brand-navy">All tenants</CardTitle>
          <CardDescription>
            {tenantsPage ? `${tenantsPage.totalElements} tenant(s)` : 'Loading…'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-brand-muted">Loading tenants…</p>
          ) : tenants.length === 0 ? (
            <p className="text-sm text-brand-muted">No tenants yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-brand-muted">
                    <th className="pb-2 pr-4 font-medium">Name</th>
                    <th className="pb-2 pr-4 font-medium">Plan</th>
                    <th className="pb-2 pr-4 font-medium">Users</th>
                    <th className="pb-2 font-medium">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {tenants.map((tenant) => (
                    <tr key={tenant.id} className="border-b border-border last:border-0">
                      <td className="py-3 pr-4">
                        <Link
                          to={`/__internal/tenants/${tenant.id}`}
                          className="font-medium text-brand-blue hover:underline"
                        >
                          {tenant.name}
                        </Link>
                      </td>
                      <td className="py-3 pr-4 capitalize text-brand-muted">
                        {tenant.plan.toLowerCase()}
                      </td>
                      <td className="py-3 pr-4 text-brand-muted">{tenant.userCount}</td>
                      <td className="py-3 text-brand-muted">{formatDate(tenant.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
