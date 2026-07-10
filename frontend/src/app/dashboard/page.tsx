'use client';

import React from 'react';
import { useAuth } from '@/context/auth-context';
import ProtectedRoute from '../components/protected-route';

export default function DashboardPage() {
  const { email, logout } = useAuth();

  return (
    <ProtectedRoute>
      <div className="min-h-screen flex items-center justify-center bg-brand-bg text-brand-text-primary px-4">
        <div className="max-w-md w-full text-center space-y-6 bg-brand-surface border border-brand-border p-8 rounded-xl shadow-2xl">
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-md text-brand-text-secondary">
            You are logged in as <span className="text-brand-accent font-semibold">{email}</span>
          </p>
          <button
            onClick={logout}
            className="inline-flex justify-center items-center border border-brand-accent px-6 py-2 text-sm font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/10 transition-all duration-200"
          >
            Logout
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
