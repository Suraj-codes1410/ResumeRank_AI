'use client';

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { Fraunces, Inter, JetBrains_Mono } from 'next/font/google';
import { useAuth } from '@/context/auth-context';
import { apiClient } from '@/lib/api-client';
import ProtectedRoute from '../components/protected-route';

const fraunces = Fraunces({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400', '500'],
  variable: '--font-fraunces',
});

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400', '600'],
  variable: '--font-inter',
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400'],
  variable: '--font-mono',
});

interface JobPosting {
  id: string;
  title: string;
  description: string;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  minYearsExperience: number | null;
  seniorityLevel: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

interface JobPostingListResponse {
  items: JobPosting[];
  page: number;
  size: number;
  totalItems: number;
}

export default function JobPostingsPage() {
  const { accessToken } = useAuth();
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'ARCHIVED'>('ALL');
  const [page, setPage] = useState(0);

  const { data, status, error, refetch } = useQuery<JobPostingListResponse>({
    queryKey: ['jobPostings', statusFilter, page, accessToken],
    queryFn: async () => {
      const url = statusFilter === 'ALL'
        ? `/job-postings?page=${page}&size=20`
        : `/job-postings?page=${page}&size=20&status=${statusFilter}`;
      
      const response = await apiClient.get(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    enabled: !!accessToken,
  });

  const formatDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-US', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      });
    } catch (e) {
      return dateStr;
    }
  };

  return (
    <ProtectedRoute>
      <div className={`min-h-screen bg-brand-bg text-brand-text-primary px-4 py-8 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable} ${jetbrainsMono.variable}`}>
        <div className="max-w-7xl mx-auto space-y-8">
          
          {/* Header */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-brand-border pb-6">
            <div>
              <h1 className={`text-4xl font-medium tracking-tight ${fraunces.className}`}>
                Job Postings
              </h1>
              <p className="text-sm text-brand-text-secondary mt-1">
                Manage your active and archived job openings for resume screening.
              </p>
            </div>
            <div>
              <Link
                href="/job-postings/create"
                className="inline-flex justify-center items-center border border-brand-accent px-6 py-2.5 text-sm font-semibold rounded-lg text-brand-accent bg-transparent hover:bg-brand-accent/10 transition-all duration-200"
              >
                Create job posting
              </Link>
            </div>
          </div>

          {/* Filters Tab Bar */}
          {status === 'success' && data.totalItems > 0 && (
            <div className="flex border-b border-brand-border/60 pb-3 gap-6 text-sm">
              <button
                onClick={() => { setStatusFilter('ALL'); setPage(0); }}
                className={`pb-2 transition-colors relative ${statusFilter === 'ALL' ? 'text-brand-accent font-semibold' : 'text-brand-text-secondary hover:text-brand-text-primary'}`}
              >
                All Postings
                {statusFilter === 'ALL' && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-accent" />}
              </button>
              <button
                onClick={() => { setStatusFilter('ACTIVE'); setPage(0); }}
                className={`pb-2 transition-colors relative ${statusFilter === 'ACTIVE' ? 'text-brand-accent-secondary font-semibold' : 'text-brand-text-secondary hover:text-brand-text-primary'}`}
              >
                Active
                {statusFilter === 'ACTIVE' && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-accent-secondary" />}
              </button>
              <button
                onClick={() => { setStatusFilter('ARCHIVED'); setPage(0); }}
                className={`pb-2 transition-colors relative ${statusFilter === 'ARCHIVED' ? 'text-brand-text-primary font-semibold' : 'text-brand-text-secondary hover:text-brand-text-primary'}`}
              >
                Archived
                {statusFilter === 'ARCHIVED' && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-text-primary" />}
              </button>
            </div>
          )}

          {/* LOADING STATE */}
          {status === 'pending' && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" data-testid="loading-state">
              {[1, 2, 3].map((idx) => (
                <div
                  key={idx}
                  className="bg-brand-surface border border-brand-border p-6 rounded-xl space-y-4 animate-pulse"
                >
                  <div className="flex justify-between items-start">
                    <div className="h-6 w-2/3 bg-neutral-800 rounded" />
                    <div className="h-5 w-16 bg-neutral-800 rounded-full" />
                  </div>
                  <div className="space-y-2">
                    <div className="h-4 w-full bg-neutral-800 rounded" />
                    <div className="h-4 w-5/6 bg-neutral-800 rounded" />
                  </div>
                  <div className="flex gap-2">
                    <div className="h-5 w-12 bg-neutral-800 rounded" />
                    <div className="h-5 w-12 bg-neutral-800 rounded" />
                  </div>
                  <div className="border-t border-brand-border/40 pt-4 flex justify-between">
                    <div className="h-4 w-20 bg-neutral-800 rounded" />
                    <div className="h-4 w-12 bg-neutral-800 rounded" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* ERROR STATE */}
          {status === 'error' && (
            <div className="flex flex-col items-center justify-center py-12 px-4 bg-brand-surface border border-brand-border rounded-xl max-w-md mx-auto text-center space-y-4">
              <div className="p-3 rounded-full bg-rose-950/20 border border-rose-500/30 text-rose-400">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <h3 className={`text-lg font-medium ${fraunces.className}`}>Failed to load job postings</h3>
              <p className="text-sm text-brand-text-secondary">
                We encountered an error while communicating with the server. Please check your connection and try again.
              </p>
              <button
                onClick={() => refetch()}
                className="border border-brand-accent text-brand-accent px-5 py-2 rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-colors"
              >
                Retry Request
              </button>
            </div>
          )}

          {/* EMPTY STATE */}
          {status === 'success' && data.items.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 px-4 bg-brand-surface border border-brand-border rounded-xl text-center space-y-6 max-w-xl mx-auto" data-testid="empty-state">
              <div className="p-4 rounded-full bg-brand-accent/5 border border-brand-accent/20 text-brand-accent">
                <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
              </div>
              <div className="space-y-2">
                <h3 className={`text-2xl font-medium tracking-tight ${fraunces.className}`}>
                  Your Recruiting Space
                </h3>
                <p className="text-sm text-brand-text-secondary max-w-md mx-auto">
                  You haven't posted any job openings yet. Start by creating your first post to screen candidates.
                </p>
              </div>
              <Link
                href="/job-postings/create"
                className="inline-flex justify-center items-center border border-brand-accent px-6 py-2.5 text-sm font-semibold rounded-lg text-brand-accent bg-transparent hover:bg-brand-accent/10 transition-all duration-200"
              >
                Create job posting
              </Link>
            </div>
          )}

          {/* SUCCESS STATE */}
          {status === 'success' && data.items.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" data-testid="success-state">
              {data.items.map((job) => (
                <div
                  key={job.id}
                  className="bg-brand-surface border border-brand-border p-6 rounded-xl flex flex-col justify-between hover:border-brand-accent/20 transition-all duration-300 shadow-lg shadow-black/10 group"
                >
                  <div className="space-y-4">
                    <div className="flex justify-between items-start gap-2">
                      <h3 className="font-semibold text-lg text-brand-text-primary group-hover:text-brand-accent transition-colors line-clamp-1">
                        {job.title}
                      </h3>
                      <span
                        className={`text-xs px-2.5 py-0.5 rounded-full font-medium border shrink-0 ${
                          job.status === 'ACTIVE'
                            ? 'border-brand-accent-secondary/30 text-brand-accent-secondary bg-brand-accent-secondary/5'
                            : 'border-brand-border text-brand-text-secondary bg-brand-bg/40'
                        }`}
                      >
                        {job.status}
                      </span>
                    </div>

                    <p className="text-sm text-brand-text-secondary line-clamp-2 leading-relaxed">
                      {job.description}
                    </p>

                    {/* Skill Tags */}
                    {job.requiredSkills.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 pt-2">
                        {job.requiredSkills.slice(0, 3).map((skill, index) => (
                          <span
                            key={index}
                            className={`text-xs px-2 py-0.5 bg-brand-bg/60 text-brand-text-secondary border border-brand-border rounded font-mono ${jetbrainsMono.className}`}
                          >
                            {skill}
                          </span>
                        ))}
                        {job.requiredSkills.length > 3 && (
                          <span className={`text-xs px-2 py-0.5 text-brand-text-secondary font-mono ${jetbrainsMono.className}`}>
                            +{job.requiredSkills.length - 3}
                          </span>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="border-t border-brand-border/40 pt-4 mt-6 flex items-center justify-between text-xs text-brand-text-secondary">
                    <div className="flex items-center gap-1.5">
                      <span>Candidates:</span>
                      <span className="font-semibold text-brand-text-primary">—</span>
                    </div>
                    <span>{formatDate(job.createdAt)}</span>
                  </div>

                  <div className="pt-4 flex gap-3 mt-4">
                    <Link
                      href={`/job-postings/${job.id}`}
                      className="flex-1 text-center py-2 border border-brand-border rounded-lg text-xs font-semibold hover:border-brand-accent hover:text-brand-accent transition-colors"
                    >
                      Details
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}

        </div>
      </div>
    </ProtectedRoute>
  );
}
