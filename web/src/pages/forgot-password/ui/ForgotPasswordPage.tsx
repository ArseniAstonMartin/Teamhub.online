import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import type { AxiosError } from 'axios';

import apiClient from '@/shared/api/client';
import { forgotPasswordSchema } from '@/shared/lib/validations';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/shared/ui/card';

type FormValues = z.infer<typeof forgotPasswordSchema>;

function retryMinutes(headers: Record<string, string> | undefined): number {
  const raw = headers?.['retry-after'];
  if (!raw) return 5;
  const seconds = Number(raw);
  return isNaN(seconds) ? 5 : Math.max(1, Math.ceil(seconds / 60));
}

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(forgotPasswordSchema) });

  async function onSubmit(_values: FormValues) {
    try {
      await apiClient.post('/api/v1/auth/forgot-password', _values);
    } catch (err) {
      const axiosErr = err as AxiosError;
      const status = axiosErr.response?.status;

      if (status === 429) {
        const mins = retryMinutes(axiosErr.response?.headers as Record<string, string>);
        toast.error(`Too many requests. Please try again in ${mins} minute${mins !== 1 ? 's' : ''}.`);
        return;
      }

      if (!status || status >= 500) {
        toast.error('Something went wrong. Please try again.');
        return;
      }

      // Other 4xx — fall through to success (anti-enumeration: never leak whether email exists)
    }
    setSubmitted(true);
  }

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
        <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
          <CardHeader className="space-y-1">
            <CardTitle className="text-2xl font-semibold text-brand-navy">Check your email</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-brand-muted text-sm">
              If that email is registered, you&apos;ll receive a reset link shortly. Check your
              inbox (and spam folder).
            </p>
          </CardContent>
          <CardFooter>
            <Link to="/login" className="text-sm text-brand-blue hover:underline">
              Back to login
            </Link>
          </CardFooter>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Forgot password?</CardTitle>
          <CardDescription className="text-brand-muted">
            Enter your email and we&apos;ll send you a reset link.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form id="forgot-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
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
          </form>
        </CardContent>

        <CardFooter className="flex flex-col gap-3 items-start">
          <Button
            form="forgot-form"
            type="submit"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Sending…' : 'Send reset link'}
          </Button>
          <Link to="/login" className="text-sm text-brand-blue hover:underline">
            Back to login
          </Link>
        </CardFooter>
      </Card>
    </div>
  );
}
