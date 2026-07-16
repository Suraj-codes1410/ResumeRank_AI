'use client';

import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useMutation } from '@tanstack/react-query';
import axios from 'axios';
import Link from 'next/link';
import { Fraunces, Inter } from 'next/font/google';
import { apiClient } from '@/lib/api-client';

const fraunces = Fraunces({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400', '500'],
  variable: '--font-fraunces',
});

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400'],
  variable: '--font-inter',
});

const resetRequestSchema = z.object({
  email: z.string()
    .min(1, { message: 'Email is required' })
    .email({ message: 'Invalid email format' }),
});

type ResetRequestFormValues = z.infer<typeof resetRequestSchema>;

export default function ResetPasswordRequestPage() {
  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<ResetRequestFormValues>({
    resolver: zodResolver(resetRequestSchema),
    defaultValues: {
      email: '',
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: ResetRequestFormValues) => {
      const response = await apiClient.post('/auth/reset-password/request', values);
      return response.data;
    },
    onSuccess: () => {
      reset();
    },
  });

  const onSubmit = (data: ResetRequestFormValues) => {
    mutation.mutate(data);
  };

  const serverError = mutation.error
    ? (axios.isAxiosError(mutation.error)
      ? (mutation.error.response?.data?.detail || mutation.error.response?.data?.message || 'Failed to request reset')
      : 'Something went wrong')
    : null;

  return (
    <div className={`min-h-screen flex items-center justify-center bg-brand-bg text-brand-text-primary px-4 py-12 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}>
      <div className="max-w-md w-full space-y-8 bg-brand-surface border border-brand-border p-8 rounded-xl shadow-2xl transition-all duration-300">
        <div>
          <h2 className={`mt-6 text-center text-3xl font-medium text-brand-text-primary tracking-tight ${fraunces.className}`}>
            Reset your password
          </h2>
          <p className="mt-2 text-center text-sm text-brand-text-secondary">
            Enter your email and we will send you a reset link if the account exists.
          </p>
        </div>

        {mutation.isSuccess && (
          <div className="rounded bg-brand-accent-secondary/10 border border-brand-accent-secondary/30 p-4">
            <div className="text-sm font-medium text-brand-accent-secondary">
              If an account with that email exists, a password reset link has been logged to the server console. Check the terminal!
            </div>
          </div>
        )}

        {serverError && (
          <div className="rounded bg-rose-950/20 border border-rose-500/30 p-4">
            <div className="text-sm font-medium text-rose-400">{serverError}</div>
          </div>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="space-y-4">
            <div>
              <label htmlFor="email-address" className="block text-sm font-medium text-brand-text-secondary mb-1">
                Email address
              </label>
              <input
                id="email-address"
                type="email"
                autoComplete="email"
                {...register('email')}
                className={`appearance-none rounded-lg relative block w-full px-3 py-2.5 border bg-brand-bg/40 text-brand-text-primary placeholder-neutral-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg transition-all duration-200 ${
                  errors.email ? 'border-rose-500/60' : 'border-brand-border'
                }`}
                placeholder="you@example.com"
              />
              {errors.email && (
                <p className="mt-1 text-xs text-rose-400">{errors.email.message}</p>
              )}
            </div>
          </div>

          <div className="flex items-center justify-between">
            <div className="text-sm">
              <Link
                href="/login"
                className="font-medium text-brand-text-secondary hover:text-brand-text-primary transition-colors focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded"
              >
                Back to sign in
              </Link>
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="group relative w-full flex justify-center py-2.5 px-4 border border-brand-accent text-sm font-semibold rounded-lg text-brand-accent bg-transparent hover:bg-brand-accent/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:ring-brand-accent disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 active:scale-[0.98]"
            >
              {mutation.isPending ? (
                <svg
                  className="animate-spin -ml-1 mr-3 h-5 w-5 text-brand-accent"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    className="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    strokeWidth="4"
                  />
                  <path
                    className="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  />
                </svg>
              ) : null}
              Send reset link
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
