import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';

import apiClient from '@/shared/api/client';
import { useAuthStore } from '@/entities/user/model/authStore';
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

const schema = z
  .object({
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

interface AcceptInviteResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

interface ApiError {
  error: {
    code: string;
    message: string;
  };
}

const EXPIRED_CODES = new Set(['INVITE_USED_OR_EXPIRED', 'INVITE_NOT_FOUND']);

function InvalidInviteState() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Invalid invite</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-brand-muted text-sm">
            Invalid invite link. Contact your administrator.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

export default function AcceptInvitePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (!token) {
    return <InvalidInviteState />;
  }

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
        toast.error(
          'This invite link has expired or already been used. Contact your administrator.',
        );
      } else {
        toast.error(
          axiosErr.response?.data?.error?.message ?? 'Something went wrong. Please try again.',
        );
      }
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Accept invite</CardTitle>
          <CardDescription className="text-brand-muted">
            Create a password to activate your account.
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

        <CardFooter>
          <Button
            form="invite-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Activating…' : 'Activate account'}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
