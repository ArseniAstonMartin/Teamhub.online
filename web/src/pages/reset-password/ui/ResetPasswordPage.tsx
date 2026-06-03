import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';

import apiClient from '@/shared/api/client';
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
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

interface ApiError {
  error: {
    code: string;
    message: string;
  };
}

function InvalidTokenState() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Invalid link</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-brand-muted text-sm">
            Invalid or missing reset link. Please request a new one.
          </p>
        </CardContent>
        <CardFooter>
          <Link to="/forgot-password" className="text-sm text-brand-blue hover:underline">
            Request a new reset link
          </Link>
        </CardFooter>
      </Card>
    </div>
  );
}

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (!token) {
    return <InvalidTokenState />;
  }

  async function onSubmit(values: FormValues) {
    try {
      await apiClient.post('/api/v1/auth/reset-password', {
        token,
        newPassword: values.newPassword,
      });
      toast.success('Password reset successfully. Please sign in.');
      navigate('/login', { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      const code = axiosErr.response?.data?.error?.code;
      if (code === 'RESET_TOKEN_USED_OR_EXPIRED') {
        toast.error('This reset link has expired or already been used.');
      } else {
        toast.error(axiosErr.response?.data?.error?.message ?? 'Something went wrong. Please try again.');
      }
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Reset password</CardTitle>
          <CardDescription className="text-brand-muted">
            Choose a new password for your account.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form id="reset-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="newPassword" className="text-brand-navy">
                New password
              </Label>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                placeholder="••••••••"
                aria-describedby={errors.newPassword ? 'new-password-error' : undefined}
                {...register('newPassword')}
              />
              {errors.newPassword && (
                <p id="new-password-error" className="text-sm text-destructive">
                  {errors.newPassword.message}
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

        <CardFooter className="flex flex-col gap-3 items-start">
          <Button
            form="reset-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Resetting…' : 'Reset password'}
          </Button>
          <Link to="/forgot-password" className="text-sm text-brand-blue hover:underline">
            Request a new reset link
          </Link>
        </CardFooter>
      </Card>
    </div>
  );
}
