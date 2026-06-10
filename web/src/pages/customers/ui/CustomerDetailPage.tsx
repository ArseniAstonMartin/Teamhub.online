import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Edit2,
  Archive,
  ArchiveRestore,
  MapPin,
  Phone,
  Mail,
  Building2,
  Clock,
  ChevronRight,
} from 'lucide-react';
import { toast } from 'sonner';

import {
  useCustomer,
  useUpdateCustomer,
  useArchiveCustomer,
  useRestoreCustomer,
} from '@/entities/customer/api/customersApi';
import type { UpdateCustomerRequest } from '@/entities/customer/model/types';
import { useJobs } from '@/entities/job/api/jobsApi';
import type { JobDto } from '@/entities/job/model/types';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { AddressAutocomplete } from '@/shared/ui/AddressAutocomplete';
import type { AddressValue } from '@/shared/ui/AddressAutocomplete';
import { cn } from '@/shared/lib/utils';

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatAddress(c: {
  addressLine: string;
  city: string;
  state: string;
  zip: string;
}) {
  return `${c.addressLine}, ${c.city}, ${c.state} ${c.zip}`;
}

// ── Edit dialog ───────────────────────────────────────────────────────────────

interface EditFormState {
  name: string;
  email: string;
  companyName: string;
  addressLine: string;
  city: string;
  state: string;
  zip: string;
  lat: number | null;
  lon: number | null;
}

interface EditCustomerDialogProps {
  customerId: string;
  initialState: EditFormState;
  phone: string;
  onClose: () => void;
}

function EditCustomerDialog({ customerId, initialState, phone, onClose }: EditCustomerDialogProps) {
  const [form, setForm] = useState<EditFormState>(initialState);
  const updateMutation = useUpdateCustomer(customerId);

  function handleAddressChange(val: AddressValue) {
    setForm((prev) => ({
      ...prev,
      addressLine: val.address,
      city: val.city,
      state: val.state,
      zip: val.zip,
      lat: val.lat,
      lon: val.lon,
    }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const body: UpdateCustomerRequest = {
      name: form.name || undefined,
      email: form.email || undefined,
      companyName: form.companyName || undefined,
      addressLine: form.addressLine || undefined,
      city: form.city || undefined,
      state: form.state || undefined,
      zip: form.zip || undefined,
      lat: form.lat ?? undefined,
      lon: form.lon ?? undefined,
    };

    updateMutation.mutate(body, {
      onSuccess: () => {
        toast.success('Customer updated');
        onClose();
      },
      onError: () => {
        toast.error('Failed to update customer');
      },
    });
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Edit customer"
    >
      <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-5 text-lg font-semibold text-gray-900">Edit Customer</h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Name */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-name">Name *</Label>
            <Input
              id="edit-name"
              value={form.name}
              onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
              required
              data-testid="edit-name-input"
            />
          </div>

          {/* Phone — read-only */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-phone">Phone</Label>
            <Input
              id="edit-phone"
              value={phone}
              disabled
              className="opacity-50"
              data-testid="edit-phone-input"
            />
            <p className="text-xs text-muted-foreground">Phone cannot be changed</p>
          </div>

          {/* Email */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-email">Email</Label>
            <Input
              id="edit-email"
              type="email"
              value={form.email}
              onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
              data-testid="edit-email-input"
            />
          </div>

          {/* Company */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-company">Company</Label>
            <Input
              id="edit-company"
              value={form.companyName}
              onChange={(e) => setForm((p) => ({ ...p, companyName: e.target.value }))}
              data-testid="edit-company-input"
            />
          </div>

          {/* Address — re-geocodes via AddressAutocomplete */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-address">Address</Label>
            <AddressAutocomplete
              id="edit-address"
              value={form.addressLine}
              placeholder="Enter new address"
              onChange={handleAddressChange}
              aria-label="Customer address"
            />
            {form.city && (
              <p className="text-xs text-muted-foreground">
                {form.city}, {form.state} {form.zip}
              </p>
            )}
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={updateMutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Jobs tab ──────────────────────────────────────────────────────────────────

function JobsTab({ customerId }: { customerId: string }) {
  const navigate = useNavigate();
  const { data, isLoading } = useJobs({ customerId, size: 50 });
  const jobs = data?.content ?? [];

  if (isLoading) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">Loading jobs…</div>
    );
  }

  if (jobs.length === 0) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">
        No jobs found for this customer.
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border bg-white shadow-sm" data-testid="jobs-tab-list">
      <table className="w-full text-sm">
        <thead className="border-b bg-gray-50 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
          <tr>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Scheduled</th>
            <th className="px-4 py-3">Technician</th>
            <th className="px-4 py-3 w-8" />
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {jobs.map((job: JobDto) => (
            <tr
              key={job.id}
              className="cursor-pointer hover:bg-gray-50 transition-colors"
              onClick={() => navigate(`/jobs/${job.id}`)}
              data-testid={`job-row-${job.id}`}
            >
              <td className="px-4 py-3">
                <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-700 border-gray-200">
                  {job.status}
                </span>
              </td>
              <td className="px-4 py-3 text-gray-700">
                {job.scheduledStart
                  ? new Date(job.scheduledStart).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })
                  : '—'}
              </td>
              <td className="px-4 py-3 text-gray-700">
                {job.assignedTechnician?.name ?? '—'}
              </td>
              <td className="px-4 py-3">
                <ChevronRight className="h-4 w-4 text-gray-400" />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type TabId = 'jobs' | 'notes';

export default function CustomerDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canEdit = role === 'ADMIN' || role === 'DISPATCHER';

  const { data: customer, isLoading, isError } = useCustomer(id);
  const archiveMutation = useArchiveCustomer();
  const restoreMutation = useRestoreCustomer();

  const [activeTab, setActiveTab] = useState<TabId>('jobs');
  const [editOpen, setEditOpen] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState(false);

  function handleArchive() {
    if (!customer) return;
    archiveMutation.mutate(customer.id, {
      onSuccess: () => {
        toast.success(`${customer.name} archived`);
        navigate('/customers');
      },
      onError: () => {
        toast.error('Failed to archive customer');
        setConfirmArchive(false);
      },
    });
  }

  function handleRestore() {
    if (!customer) return;
    restoreMutation.mutate(customer.id, {
      onSuccess: () => {
        toast.success(`${customer.name} restored`);
      },
      onError: () => {
        toast.error('Failed to restore customer');
      },
    });
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-16 text-sm text-muted-foreground">
        Loading…
      </div>
    );
  }

  if (isError || !customer) {
    return (
      <div className="flex flex-col items-center justify-center p-16 gap-4">
        <p className="text-sm text-destructive">Customer not found.</p>
        <Button variant="outline" onClick={() => navigate('/customers')}>
          ← Back to Customers
        </Button>
      </div>
    );
  }

  const hasLocation = customer.lat !== null && customer.lon !== null;

  return (
    <div className="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
      {/* Back */}
      <button
        onClick={() => navigate('/customers')}
        className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-gray-900 transition-colors"
        aria-label="Back to customers"
      >
        <ArrowLeft className="h-4 w-4" />
        Customers
      </button>

      {/* Header card */}
      <div className="rounded-lg border bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-semibold text-gray-900">{customer.name}</h1>
              {customer.archivedAt && (
                <span className="inline-flex items-center rounded-full border border-gray-200 bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                  Archived
                </span>
              )}
            </div>
            {customer.companyName && (
              <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                <Building2 className="h-3.5 w-3.5" />
                {customer.companyName}
              </div>
            )}
            <div className="mt-2 flex flex-wrap gap-4 text-sm text-gray-700">
              <div className="flex items-center gap-1.5" data-testid="customer-phone">
                <Phone className="h-3.5 w-3.5 text-muted-foreground" />
                {customer.phone}
              </div>
              {customer.email && (
                <div className="flex items-center gap-1.5" data-testid="customer-email">
                  <Mail className="h-3.5 w-3.5 text-muted-foreground" />
                  {customer.email}
                </div>
              )}
              <div className="flex items-center gap-1.5" data-testid="customer-address">
                <MapPin className="h-3.5 w-3.5 text-muted-foreground" />
                {formatAddress(customer)}
              </div>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground mt-1">
              <Clock className="h-3 w-3" />
              Created {new Date(customer.createdAt).toLocaleDateString('en-US', {
                month: 'long',
                day: 'numeric',
                year: 'numeric',
              })}
            </div>
          </div>

          {/* Actions */}
          {canEdit && (
            <div className="flex items-center gap-2 shrink-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setEditOpen(true)}
                data-testid="edit-customer-btn"
              >
                <Edit2 className="mr-1.5 h-3.5 w-3.5" />
                Edit
              </Button>
              {!customer.archivedAt && (
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive hover:bg-destructive/10"
                  onClick={() => setConfirmArchive(true)}
                  data-testid="archive-customer-btn"
                >
                  <Archive className="mr-1.5 h-3.5 w-3.5" />
                  Archive
                </Button>
              )}
              {customer.archivedAt && (
                <Button
                  variant="outline"
                  size="sm"
                  className="text-green-700 hover:bg-green-50"
                  onClick={handleRestore}
                  disabled={restoreMutation.isPending}
                  data-testid="restore-customer-btn"
                >
                  <ArchiveRestore className="mr-1.5 h-3.5 w-3.5" />
                  {restoreMutation.isPending ? 'Restoring…' : 'Restore'}
                </Button>
              )}
            </div>
          )}
        </div>

        {/* Map */}
        <div className="mt-4" data-testid="map-section">
          {hasLocation ? (
            <iframe
              title="Customer location"
              src={`https://maps.google.com/maps?q=${customer.lat},${customer.lon}&z=15&output=embed`}
              className="w-full h-48 rounded-lg border"
              loading="lazy"
              data-testid="map-iframe"
            />
          ) : (
            <div
              className="flex items-center gap-2 rounded-lg border border-dashed border-gray-200 bg-gray-50 px-4 py-3 text-sm text-muted-foreground"
              data-testid="map-address-text"
            >
              <MapPin className="h-4 w-4 shrink-0" />
              {formatAddress(customer)}
            </div>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div>
        <div className="border-b flex gap-0">
          {(['jobs', 'notes'] as TabId[]).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={cn(
                'px-4 py-2 text-sm font-medium border-b-2 transition-colors capitalize',
                activeTab === tab
                  ? 'border-brand-navy text-brand-navy'
                  : 'border-transparent text-muted-foreground hover:text-gray-700',
              )}
              data-testid={`tab-${tab}`}
            >
              {tab === 'jobs' ? 'Jobs' : 'Notes'}
            </button>
          ))}
        </div>

        <div className="mt-4">
          {activeTab === 'jobs' && <JobsTab customerId={id} />}
          {activeTab === 'notes' && (
            <div
              className="rounded-lg border border-dashed border-gray-200 bg-gray-50 px-6 py-10 text-center text-sm text-muted-foreground"
              data-testid="notes-placeholder"
            >
              Notes coming soon
            </div>
          )}
        </div>
      </div>

      {/* Edit dialog */}
      {editOpen && (
        <EditCustomerDialog
          customerId={customer.id}
          phone={customer.phone}
          initialState={{
            name: customer.name,
            email: customer.email ?? '',
            companyName: customer.companyName ?? '',
            addressLine: customer.addressLine,
            city: customer.city,
            state: customer.state,
            zip: customer.zip,
            lat: customer.lat,
            lon: customer.lon,
          }}
          onClose={() => setEditOpen(false)}
        />
      )}

      {/* Confirm archive dialog */}
      {confirmArchive && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          role="dialog"
          aria-modal="true"
          aria-label="Confirm archive customer"
        >
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
            <h2 className="mb-2 text-lg font-semibold text-gray-900">Archive customer?</h2>
            <p className="mb-6 text-sm text-gray-600">
              <strong>{customer.name}</strong> will be hidden from the default customer list.
            </p>
            <div className="flex justify-end gap-3">
              <Button
                variant="outline"
                onClick={() => setConfirmArchive(false)}
                disabled={archiveMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleArchive}
                disabled={archiveMutation.isPending}
              >
                {archiveMutation.isPending ? 'Archiving…' : 'Archive'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
