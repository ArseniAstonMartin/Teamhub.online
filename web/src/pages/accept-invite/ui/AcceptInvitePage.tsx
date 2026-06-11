import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';

import apiClient from '@/shared/api/client';
import { useAuthStore } from '@/entities/user/model/authStore';
import { acceptInviteSchema } from '@/shared/lib/validations';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';

// ── Types ─────────────────────────────────────────────────────────────────────

type FormValues = z.infer<typeof acceptInviteSchema>;

interface AcceptInviteResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

interface ApiError {
  error: { code: string; message: string };
}

interface InvitePayload {
  email: string;
  tenant_name?: string;
  tenant_id?: string;
  sub?: string;
  exp?: number;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const INVITE_TOKEN_KEY = 'teamhub_invite_token';
const EXPIRED_CODES = new Set(['INVITE_USED_OR_EXPIRED', 'INVITE_NOT_FOUND']);

function redirectToGoogleOAuth(inviteToken: string): void {
  sessionStorage.setItem(INVITE_TOKEN_KEY, inviteToken);
  window.location.href = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/oauth2/authorization/google`;
}

/**
 * Optional display hints from a JWT-shaped token. Production invite links use opaque tokens
 * from the backend (InviteService.generateToken); validation happens on POST /invites/accept.
 */
function decodeJwtInvitePayload(token: string): InvitePayload | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const payloadB64 = parts[1];
    if (!payloadB64) return null;
    const json = atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as InvitePayload;
  } catch {
    return null;
  }
}

// ── Error states ──────────────────────────────────────────────────────────────

function NoTokenState() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Invalid invite</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-brand-muted text-sm">
            This invite link is invalid or missing. Contact your administrator for a new invite.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function ExpiredInviteState() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">
            Invite expired
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-brand-muted text-sm">
            This invite link has expired or has already been used. Contact your administrator
            to receive a new invite.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AcceptInvitePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [inviteExpired, setInviteExpired] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(acceptInviteSchema) });

  async function onSubmit(values: FormValues) {
    try {
      const { data } = await apiClient.post<AcceptInviteResponse>('/api/v1/invites/accept', {
        token,
        password: values.password,
      });
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      navigate('/schedule', { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      const code = axiosErr.response?.data?.error?.code ?? '';
      if (EXPIRED_CODES.has(code)) {
        setInviteExpired(true);
      } else {
        toast.error(
          axiosErr.response?.data?.error?.message ?? 'Something went wrong. Please try again.',
        );
      }
    }
  }

  if (!token.trim()) return <NoTokenState />;

  if (inviteExpired) return <ExpiredInviteState />;

  const payload = decodeJwtInvitePayload(token);
  const inviteeEmail = payload?.email;
  const tenantName = payload?.tenant_name;

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">
            You&apos;re invited{tenantName ? ` to ${tenantName}` : ''}
          </CardTitle>
          <CardDescription className="text-brand-muted">
            {inviteeEmail
              ? `Set a password for ${inviteeEmail} to activate your account.`
              : 'Set a password to activate your account.'}
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form id="invite-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="password" className="text-brand-navy">
                Password
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                placeholder="••••••••"
                aria-describedby={errors.password ? 'password-error' : undefined}
                {...register('password')}
              />
              {errors.password && (
                <p id="password-error" className="text-sm text-destructive">
                  {errors.password.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword" className="text-brand-navy">
                Confirm password
              </Label>
              <Input
                id="confirmPassword"
                type="password"
                autoComplete="new-password"
                placeholder="••••••••"
                aria-describedby={errors.confirmPassword ? 'confirm-password-error' : undefined}
                {...register('confirmPassword')}
              />
              {errors.confirmPassword && (
                <p id="confirm-password-error" className="text-sm text-destructive">
                  {errors.confirmPassword.message}
                </p>
              )}
            </div>
          </form>
        </CardContent>

        <CardFooter className="flex flex-col gap-3">
          <Button
            form="invite-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Activating…' : 'Activate account'}
          </Button>

          <div className="relative w-full">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-brand-surface px-2 text-brand-muted">or</span>
            </div>
          </div>

          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={() => redirectToGoogleOAuth(token)}
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              className="size-4 shrink-0"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                fill="#4285F4"
              />
              <path
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                fill="#34A853"
              />
              <path
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
                fill="#FBBC05"
              />
              <path
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                fill="#EA4335"
              />
            </svg>
            Continue with Google
          </Button>

          <Button
            type="button"
            variant="outline"
            className="w-full opacity-50 cursor-not-allowed"
            disabled
          >
            Sign in with Apple (coming soon)
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
