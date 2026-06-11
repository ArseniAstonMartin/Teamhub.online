import { useState, type FormEvent } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { Navigate } from 'react-router-dom';
import { toast } from 'sonner';
import { UserPlus, ChevronLeft, ChevronRight } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';

import {
  useUsers,
  useInviteUser,
  useChangeRole,
  useDeactivateUser,
  useReactivateUser,
  updateUserRoleInCache,
} from '@/entities/user/api/usersApi';
import type { UserResponse, UserRole } from '@/entities/user/model/userTypes';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from '@/shared/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import { cn } from '@/shared/lib/utils';

// ── Constants ─────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20;

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Admin',
  DISPATCHER: 'Dispatcher',
  TECHNICIAN: 'Technician',
};

const ROLE_BADGE_CLASS: Record<UserRole, string> = {
  ADMIN: 'bg-blue-100 text-blue-700 border-blue-200',
  DISPATCHER: 'bg-indigo-100 text-indigo-700 border-indigo-200',
  TECHNICIAN: 'bg-cyan-100 text-cyan-700 border-cyan-200',
};

// ── Role Badge ────────────────────────────────────────────────────────────────

function RoleBadge({ role }: { role: UserRole }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold',
        ROLE_BADGE_CLASS[role],
      )}
    >
      {ROLE_LABELS[role]}
    </span>
  );
}

// ── Status Badge ──────────────────────────────────────────────────────────────

function StatusBadge({ deactivatedAt }: { deactivatedAt: string | null }) {
  const isActive = deactivatedAt === null;
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold',
        isActive
          ? 'bg-green-100 text-green-700 border-green-200'
          : 'bg-gray-100 text-gray-600 border-gray-200',
      )}
    >
      {isActive ? 'Active' : 'Deactivated'}
    </span>
  );
}

// ── Invite Dialog ─────────────────────────────────────────────────────────────

interface InviteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function InviteDialog({ open, onOpenChange }: InviteDialogProps) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<UserRole>('DISPATCHER');
  const [emailError, setEmailError] = useState('');
  const inviteMutation = useInviteUser();

  function resetForm() {
    setEmail('');
    setRole('DISPATCHER');
    setEmailError('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setEmailError('');
    if (!email.trim() || !email.includes('@')) {
      setEmailError('Enter a valid email address.');
      return;
    }
    try {
      await inviteMutation.mutateAsync({ email: email.trim(), role });
      toast.success(`Invite sent to ${email.trim()}`);
      onOpenChange(false);
      resetForm();
    } catch {
      toast.error('Failed to send invite. Please try again.');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) resetForm();
        onOpenChange(v);
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Invite User</DialogTitle>
        </DialogHeader>
        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label htmlFor="invite-email">Email</Label>
            <Input
              id="invite-email"
              type="email"
              placeholder="user@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-describedby={emailError ? 'invite-email-error' : undefined}
            />
            {emailError && (
              <p id="invite-email-error" className="text-xs text-red-600">
                {emailError}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="invite-role">Role</Label>
            <Select value={role} onValueChange={(v) => setRole(v as UserRole)}>
              <SelectTrigger id="invite-role">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ADMIN">Admin</SelectItem>
                <SelectItem value="DISPATCHER">Dispatcher</SelectItem>
                <SelectItem value="TECHNICIAN">Technician</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter className="pt-2">
            <DialogClose asChild>
              <Button type="button" variant="outline">
                Cancel
              </Button>
            </DialogClose>
            <Button type="submit" disabled={inviteMutation.isPending}>
              {inviteMutation.isPending ? 'Sending…' : 'Send Invite'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Change Role Dialog ────────────────────────────────────────────────────────

interface ChangeRoleDialogProps {
  user: UserResponse | null;
  onClose: () => void;
}

function ChangeRoleDialog({ user, onClose }: ChangeRoleDialogProps) {
  const [selectedRole, setSelectedRole] = useState<UserRole>(user?.role ?? 'DISPATCHER');
  const qc = useQueryClient();
  const changeRole = useChangeRole();
  const [page] = useState(0);

  // Sync selectedRole when user changes
  const effectiveRole = user?.role ?? 'DISPATCHER';

  async function handleConfirm() {
    if (!user) return;
    const previousRole = user.role;
    // Optimistic update
    updateUserRoleInCache(qc, page, PAGE_SIZE, user.id, selectedRole);
    try {
      await changeRole.mutateAsync({ id: user.id, role: selectedRole });
      toast.success(`Role updated to ${ROLE_LABELS[selectedRole]}`);
      onClose();
    } catch {
      // Rollback
      updateUserRoleInCache(qc, page, PAGE_SIZE, user.id, previousRole);
      toast.error('Failed to change role. Please try again.');
    }
  }

  return (
    <Dialog open={user !== null} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Change role for {user?.email}</DialogTitle>
        </DialogHeader>
        <div className="space-y-1.5 pt-2">
          <Label>New Role</Label>
          <Select
            value={selectedRole || effectiveRole}
            onValueChange={(v) => setSelectedRole(v as UserRole)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ADMIN">Admin</SelectItem>
              <SelectItem value="DISPATCHER">Dispatcher</SelectItem>
              <SelectItem value="TECHNICIAN">Technician</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <DialogFooter className="pt-4">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => void handleConfirm()} disabled={changeRole.isPending}>
            {changeRole.isPending ? 'Saving…' : 'Confirm'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── Confirm Dialog ────────────────────────────────────────────────────────────

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  destructive?: boolean;
  isPending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirm',
  destructive = false,
  isPending = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onCancel(); }}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">{description}</p>
        <DialogFooter className="pt-4">
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            onClick={onConfirm}
            disabled={isPending}
          >
            {isPending ? 'Processing…' : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function SettingsUsersPage() {
  const currentUser = useAuthStore((s) => s.user);

  // Admin-only guard
  if (currentUser && currentUser.role !== 'ADMIN') {
    return <Navigate to="/schedule" replace />;
  }

  return <SettingsUsersPageInner />;
}

function SettingsUsersPageInner() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useUsers(page, PAGE_SIZE);

  const [inviteOpen, setInviteOpen] = useState(false);

  // Change role
  const [changeRoleUser, setChangeRoleUser] = useState<UserResponse | null>(null);

  // Deactivate
  const [deactivateUser, setDeactivateUser] = useState<UserResponse | null>(null);
  const deactivateMutation = useDeactivateUser();

  // Reactivate
  const [reactivateUser, setReactivateUser] = useState<UserResponse | null>(null);
  const reactivateMutation = useReactivateUser();

  async function handleDeactivate() {
    if (!deactivateUser) return;
    try {
      await deactivateMutation.mutateAsync(deactivateUser.id);
      toast.success(`${deactivateUser.email} deactivated.`);
      setDeactivateUser(null);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 422) {
        // Backend returns 422 for LAST_ADMIN_PROTECTED — do NOT update state
        toast.error('Cannot deactivate the last Admin.');
      } else {
        toast.error('Failed to deactivate user.');
      }
      setDeactivateUser(null);
    }
  }

  async function handleReactivate() {
    if (!reactivateUser) return;
    try {
      await reactivateMutation.mutateAsync(reactivateUser.id);
      toast.success(`${reactivateUser.email} reactivated.`);
      setReactivateUser(null);
    } catch {
      toast.error('Failed to reactivate user.');
      setReactivateUser(null);
    }
  }

  const totalPages = data?.totalPages ?? 1;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-brand-navy">Users</h1>
        <Button onClick={() => setInviteOpen(true)}>
          <UserPlus className="h-4 w-4 mr-2" />
          Invite User
        </Button>
      </div>

      {/* Table */}
      <div className="rounded-lg border border-border bg-brand-surface overflow-hidden">
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-sm text-brand-muted">
            Loading users…
          </div>
        )}

        {isError && (
          <div className="flex items-center justify-center py-16 text-sm text-red-600">
            Failed to load users.
          </div>
        )}

        {!isLoading && !isError && (
          <table className="w-full text-sm">
            <thead className="bg-brand-bg text-brand-muted border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left font-medium">Email</th>
                <th className="px-4 py-3 text-left font-medium">Role</th>
                <th className="px-4 py-3 text-left font-medium">Status</th>
                <th className="px-4 py-3 text-left font-medium">Joined</th>
                <th className="px-4 py-3 text-left font-medium">Last seen</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {data?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-brand-muted">
                    No users found.
                  </td>
                </tr>
              )}
              {data?.content.map((user) => (
                <UserRow
                  key={user.id}
                  user={user}
                  onChangeRole={() => setChangeRoleUser(user)}
                  onDeactivate={() => setDeactivateUser(user)}
                  onReactivate={() => setReactivateUser(user)}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {!isLoading && !isError && totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-brand-muted">
          <span>
            Page {page + 1} of {totalPages} ({data?.totalElements ?? 0} users)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Dialogs */}
      <InviteDialog open={inviteOpen} onOpenChange={setInviteOpen} />

      <ChangeRoleDialog
        user={changeRoleUser}
        onClose={() => setChangeRoleUser(null)}
      />

      <ConfirmDialog
        open={deactivateUser !== null}
        title="Deactivate user?"
        description={`Deactivate ${deactivateUser?.email ?? ''}? They will lose access to the workspace.`}
        confirmLabel="Deactivate"
        destructive
        isPending={deactivateMutation.isPending}
        onConfirm={() => void handleDeactivate()}
        onCancel={() => setDeactivateUser(null)}
      />

      <ConfirmDialog
        open={reactivateUser !== null}
        title="Reactivate user?"
        description={`Reactivate ${reactivateUser?.email ?? ''}? They will regain access to the workspace.`}
        confirmLabel="Reactivate"
        isPending={reactivateMutation.isPending}
        onConfirm={() => void handleReactivate()}
        onCancel={() => setReactivateUser(null)}
      />
    </div>
  );
}

// ── User Row ──────────────────────────────────────────────────────────────────

interface UserRowProps {
  user: UserResponse;
  onChangeRole: () => void;
  onDeactivate: () => void;
  onReactivate: () => void;
}

function UserRow({ user, onChangeRole, onDeactivate, onReactivate }: UserRowProps) {
  const isActive = user.deactivatedAt === null;
  const joined = new Date(user.createdAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
  const lastSeen =
    user.lastLoginAt === null
      ? 'Never'
      : formatDistanceToNow(new Date(user.lastLoginAt), { addSuffix: true });

  return (
    <tr className="hover:bg-brand-bg/50 transition-colors">
      <td className="px-4 py-3 font-medium text-brand-navy">{user.email}</td>
      <td className="px-4 py-3">
        <RoleBadge role={user.role} />
      </td>
      <td className="px-4 py-3">
        <StatusBadge deactivatedAt={user.deactivatedAt} />
      </td>
      <td className="px-4 py-3 text-brand-muted">{joined}</td>
      <td className="px-4 py-3 text-brand-muted">{lastSeen}</td>
      <td className="px-4 py-3 text-right">
        <div className="flex items-center justify-end gap-2">
          <Button
            variant="ghost"
            size="sm"
            className="text-xs"
            onClick={onChangeRole}
          >
            Change Role
          </Button>
          {isActive ? (
            <Button
              variant="ghost"
              size="sm"
              className="text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
              onClick={onDeactivate}
            >
              Deactivate
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              className="text-xs text-green-600 hover:text-green-700 hover:bg-green-50"
              onClick={onReactivate}
            >
              Reactivate
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
