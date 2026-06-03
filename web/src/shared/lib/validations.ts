import { z } from 'zod';

export const emailSchema = z.string().email('Enter a valid email address');

export const passwordSchema = z.string().min(8, 'Password must be at least 8 characters');

export const forgotPasswordSchema = z.object({
  email: emailSchema,
});

export const resetPasswordSchema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
