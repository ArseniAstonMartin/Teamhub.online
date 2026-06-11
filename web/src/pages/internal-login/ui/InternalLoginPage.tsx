import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import axios, { type AxiosError } from 'axios';

import { useStaffAuthStore } from '@/entities/staff/model/staffAuthStore';
import type { StaffLoginResponse } from '@/entities/staff/model/types';
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

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

interface ApiError {
  error: {
    code: string;
    message: string;
  };
}

export default function InternalLoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirect = searchParams.get('redirect') ?? '/__internal/tenants';
  const isStaffAuthenticated = useStaffAuthStore((s) => s.isStaffAuthenticated);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (isStaffAuthenticated()) {
    return <Navigate to={redirect} replace />;
  }

  async function onSubmit(values: FormValues) {
    try {
      const { data } = await axios.post<StaffLoginResponse>(
        `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api/v1/internal/auth/login`,
        values,
      );
      useStaffAuthStore.getState().setAccessToken(data.accessToken);
      navigate(redirect, { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      toast.error(axiosErr.response?.data?.error?.message ?? 'Staff login failed');
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Staff sign in</CardTitle>
          <CardDescription className="text-brand-muted">
            Teamhub internal console — staff accounts only
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form
            id="internal-login-form"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label htmlFor="staff-email" className="text-brand-navy">
                Email
              </Label>
              <Input
                id="staff-email"
                type="email"
                autoComplete="email"
                placeholder="you@teamhub.online"
                aria-describedby={errors.email ? 'staff-email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p id="staff-email-error" className="text-sm text-destructive">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="staff-password" className="text-brand-navy">
                Password
              </Label>
              <Input
                id="staff-password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                aria-describedby={errors.password ? 'staff-password-error' : undefined}
                {...register('password')}
              />
              {errors.password && (
                <p id="staff-password-error" className="text-sm text-destructive">
                  {errors.password.message}
                </p>
              )}
            </div>
          </form>
        </CardContent>

        <CardFooter>
          <Button
            form="internal-login-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
