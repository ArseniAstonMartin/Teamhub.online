import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
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

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
});

type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    try {
      await apiClient.post('/api/v1/auth/forgot-password', values);
    } catch (err) {
      const axiosErr = err as AxiosError;
      if (!axiosErr.response || axiosErr.response.status >= 500) {
        toast.error('Something went wrong. Please try again.');
        return;
      }
      // 4xx — intentionally fall through to show the generic success state
      // (anti-enumeration: never leak whether the email exists)
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
