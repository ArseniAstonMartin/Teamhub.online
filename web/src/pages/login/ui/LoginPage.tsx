import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
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

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

interface LoginResponse {
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

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirect = searchParams.get('redirect') ?? '/schedule';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    try {
      const { data } = await apiClient.post<LoginResponse>('/api/v1/auth/login', values);
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      navigate(redirect, { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiError>;
      toast.error(axiosErr.response?.data?.error?.message ?? 'Login failed');
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Sign in</CardTitle>
          <CardDescription className="text-brand-muted">
            Enter your credentials to access your account
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form id="login-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email" className="text-brand-navy">
                Email
              </Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@company.com"
                aria-describedby={errors.email ? 'email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p id="email-error" className="text-sm text-destructive">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="password" className="text-brand-navy">
                  Password
                </Label>
                <Link
                  to="/forgot-password"
                  className="text-sm text-brand-blue hover:underline"
                >
                  Forgot password?
                </Link>
              </div>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
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
          </form>
        </CardContent>

        <CardFooter className="flex flex-col gap-3">
          <Button
            form="login-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>

          <div className="relative w-full">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-brand-surface px-2 text-brand-muted">or</span>
            </div>
          </div>

          <Button variant="outline" className="w-full opacity-50 cursor-not-allowed" disabled>
            Continue with Google (coming soon)
          </Button>
          <Button variant="outline" className="w-full opacity-50 cursor-not-allowed" disabled>
            Sign in with Apple (coming soon)
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
