"use client";

import React, { Suspense, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Fraunces, Inter } from "next/font/google";
import { apiClient } from "@/lib/api-client";

const fraunces = Fraunces({
  subsets: ["latin"],
  display: "swap",
  weight: ["400", "500"],
  variable: "--font-fraunces",
});

const inter = Inter({
  subsets: ["latin"],
  display: "swap",
  weight: ["400"],
  variable: "--font-inter",
});

function VerifyEmailForm() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") || "";

  const { data, status, error } = useQuery({
    queryKey: ["verifyEmail", token],
    queryFn: async () => {
      if (!token) throw new Error("No verification token provided.");
      const response = await apiClient.get(`/auth/verify-email?token=${token}`);
      return response.data;
    },
    enabled: !!token,
  });

  const serverError = error
    ? axios.isAxiosError(error)
      ? error.response?.data?.detail ||
        error.response?.data?.message ||
        "Verification failed"
      : error.message
    : null;

  return (
    <div className="max-w-md w-full space-y-8 bg-brand-surface border border-brand-border p-8 rounded-xl shadow-2xl text-center">
      <div>
        <h2
          className={`mt-6 text-3xl font-medium text-brand-text-primary tracking-tight ${fraunces.className}`}
        >
          Email Verification
        </h2>
      </div>

      {!token && (
        <div className="rounded bg-rose-950/20 border border-rose-500/30 p-4">
          <div className="text-sm font-medium text-rose-400">
            Missing verification token. Please copy the complete URL link from
            your console.
          </div>
        </div>
      )}

      {token && status === "pending" && (
        <div className="flex flex-col items-center space-y-4 py-8">
          <svg
            className="animate-spin h-10 w-10 text-brand-accent"
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
          <div className="text-brand-text-secondary text-sm">
            Verifying your email address...
          </div>
        </div>
      )}

      {status === "success" && (
        <div className="space-y-6">
          <div className="rounded bg-brand-accent-secondary/10 border border-brand-accent-secondary/30 p-4">
            <div className="text-sm font-medium text-brand-accent-secondary">
              Email verified successfully!
            </div>
          </div>
          <Link
            href="/login"
            className="inline-flex justify-center items-center border border-brand-accent px-6 py-2.5 text-sm font-semibold rounded-lg text-brand-accent bg-transparent hover:bg-brand-accent/10 focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none transition-all duration-200"
          >
            Sign in now
          </Link>
        </div>
      )}

      {status === "error" && (
        <div className="space-y-6">
          <div className="rounded bg-rose-950/20 border border-rose-500/30 p-4">
            <div className="text-sm font-medium text-rose-400">
              {serverError}
            </div>
          </div>
          <Link
            href="/signup"
            className="text-sm font-semibold text-brand-accent hover:text-brand-accent/80 transition-colors focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded"
          >
            Back to Sign up
          </Link>
        </div>
      )}
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <div
      className={`min-h-screen flex items-center justify-center bg-brand-bg text-brand-text-primary px-4 py-12 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}
    >
      <Suspense
        fallback={
          <div className="max-w-md w-full text-center text-brand-text-secondary py-12">
            Loading verification info...
          </div>
        }
      >
        <VerifyEmailForm />
      </Suspense>
    </div>
  );
}
