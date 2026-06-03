import { useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search, Archive, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';

import { useCustomers, useArchiveCustomer } from '@/entities/customer/api/customersApi';
import type { CustomerResponse } from '@/entities/customer/model/types';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { cn } from '@/shared/lib/utils';

const PAGE_SIZE = 20;

// ── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ archivedAt }: { archivedAt: string | null }) {
  if (archivedAt) {
    return (
      <span className="inline-flex items-center rounded-full border border-gray-200 bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
        Archived
      </span>
    );
  }
  return (
    <span className="inline-flex items-center rounded-full border border-green-200 bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
      Active
    </span>
  );
}

// ── Confirm dialog ────────────────────────────────────────────────────────────

interface ConfirmArchiveDialogProps {
  customer: CustomerResponse;
  onConfirm: () => void;
  onCancel: () => void;
  isPending: boolean;
}

function ConfirmArchiveDialog({ customer, onConfirm, onCancel, isPending }: ConfirmArchiveDialogProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-label="Confirm archive"
    >
      <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
        <h2 className="mb-2 text-lg font-semibold text-gray-900">Archive customer?</h2>
        <p className="mb-6 text-sm text-gray-600">
          <strong>{customer.name}</strong> will be hidden from the default customer list.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={onCancel} disabled={isPending}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={isPending}>
            {isPending ? 'Archiving…' : 'Archive'}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function CustomersPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const qParam = searchParams.get('q') ?? '';
  const includeArchived = searchParams.get('include_archived') === 'true';
  const page = Math.max(0, Number(searchParams.get('page') ?? '0'));

  const [inputValue, setInputValue] = useState(qParam);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const role = useAuthStore((s) => s.user?.role);
  const canArchive = role === 'ADMIN' || role === 'DISPATCHER';

  const { data, isLoading, isError } = useCustomers({
    q: qParam || undefined,
    include_archived: includeArchived || undefined,
    page,
    size: PAGE_SIZE,
  });

  const archiveMutation = useArchiveCustomer();
  const [pendingArchive, setPendingArchive] = useState<CustomerResponse | null>(null);


  function handleSearchChange(e: React.ChangeEvent<HTMLInputElement>) {
    const val = e.target.value;
    setInputValue(val);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        if (val) {
          next.set('q', val);
        } else {
          next.delete('q');
        }
        next.delete('page');
        return next;
      });
    }, 300);
  }

  function handleArchivedToggle(e: React.ChangeEvent<HTMLInputElement>) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (e.target.checked) {
        next.set('include_archived', 'true');
      } else {
        next.delete('include_archived');
      }
      next.delete('page');
      return next;
    });
  }

  function handlePageChange(newPage: number) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (newPage === 0) {
        next.delete('page');
      } else {
        next.set('page', String(newPage));
      }
      return next;
    });
  }

  function handleArchiveConfirm() {
    if (!pendingArchive) return;
    archiveMutation.mutate(pendingArchive.id, {
      onSuccess: () => {
        toast.success(`${pendingArchive.name} archived`);
        setPendingArchive(null);
      },
      onError: () => {
        toast.error('Failed to archive customer');
        setPendingArchive(null);
      },
    });
  }

  const totalPages = data?.totalPages ?? 0;
  const customers = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4 p-6">
      {/* Header */}
      <div className="flex flex-wrap items-center gap-4">
        <h1 className="text-2xl font-semibold text-brand-navy">Customers</h1>

        {/* Search */}
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Search name, phone, email…"
            value={inputValue}
            onChange={handleSearchChange}
            aria-label="Search customers"
            data-testid="customer-search"
          />
        </div>

        {/* Show archived checkbox */}
        <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-gray-300"
            checked={includeArchived}
            onChange={handleArchivedToggle}
            data-testid="show-archived-checkbox"
          />
          Show archived
        </label>
      </div>

      {/* Table */}
      {isError && (
        <div className="rounded-md border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          Failed to load customers. Please try again.
        </div>
      )}

      {isLoading && (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
          Loading…
        </div>
      )}

      {!isLoading && !isError && (
        <>
          <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
            <table className="w-full text-sm" data-testid="customers-table">
              <thead className="border-b bg-gray-50 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Phone</th>
                  <th className="px-4 py-3">Email</th>
                  <th className="px-4 py-3">City</th>
                  <th className="px-4 py-3">Status</th>
                  {canArchive && <th className="px-4 py-3 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {customers.length === 0 ? (
                  <tr>
                    <td
                      colSpan={canArchive ? 6 : 5}
                      className="px-4 py-12 text-center text-muted-foreground"
                    >
                      No customers found.
                    </td>
                  </tr>
                ) : (
                  customers.map((c) => (
                    <tr
                      key={c.id}
                      className={cn(
                        'cursor-pointer transition-colors hover:bg-gray-50',
                        c.archivedAt && 'opacity-60',
                      )}
                      onClick={() => navigate(`/customers/${c.id}`)}
                      data-testid={`customer-row-${c.id}`}
                    >
                      <td className="px-4 py-3 font-medium text-gray-900">
                        <div>{c.name}</div>
                        {c.companyName && (
                          <div className="text-xs text-muted-foreground">{c.companyName}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-700">{c.phone}</td>
                      <td className="px-4 py-3 text-gray-700">{c.email ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-700">{c.city}</td>
                      <td className="px-4 py-3">
                        <StatusBadge archivedAt={c.archivedAt} />
                      </td>
                      {canArchive && (
                        <td
                          className="px-4 py-3 text-right"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {!c.archivedAt && (
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={`Archive ${c.name}`}
                              data-testid={`archive-btn-${c.id}`}
                              onClick={() => setPendingArchive(c)}
                            >
                              <Archive className="h-4 w-4 text-gray-500" />
                            </Button>
                          )}
                          {/* TO-DO: add POST /api/v1/customers/{id}/restore endpoint */}
                        </td>
                      )}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-gray-600">
              <span>
                Page {page + 1} of {totalPages} ({data?.totalElements ?? 0} total)
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => handlePageChange(page - 1)}
                  aria-label="Previous page"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => handlePageChange(page + 1)}
                  aria-label="Next page"
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Archive confirm dialog */}
      {pendingArchive && (
        <ConfirmArchiveDialog
          customer={pendingArchive}
          onConfirm={handleArchiveConfirm}
          onCancel={() => setPendingArchive(null)}
          isPending={archiveMutation.isPending}
        />
      )}
    </div>
  );
}
