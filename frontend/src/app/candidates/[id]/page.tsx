'use client';

import React, { useState, useEffect, useMemo, use } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { Fraunces, Inter } from 'next/font/google';
import Link from 'next/link';
import { useAuth } from '@/context/auth-context';
import { apiClient } from '@/lib/api-client';
import ProtectedRoute from '../../components/protected-route';

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

interface Candidate {
  id: string;
  jobPostingId: string;
  name: string | null;
  email: string | null;
  resumeFileUrl: string;
  resumeStatus: 'PENDING' | 'PARSING' | 'SCORED' | 'FAILED';
  parseError: string | null;
  overallScore: number | null;
  skillsScore: number | null;
  experienceScore: number | null;
  seniorityScore: number | null;
  matchedSkills: string[] | null;
  missingSkills: string[] | null;
  summary: string | null;
  yearsExperienceDetected: number | null;
  pipelineStatus: 'NEW' | 'REVIEWING' | 'SHORTLISTED' | 'REJECTED';
  createdAt: string;
  updatedAt: string;
}

interface CandidateStatusLogResponse {
  id: string;
  fromStatus: string;
  toStatus: string;
  changedByEmail: string;
  createdAt: string;
}

export default function CandidateDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { accessToken } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState('');
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);

  // Fetch candidate details
  const { data: candidate, status, error, refetch } = useQuery<Candidate>({
    queryKey: ['candidate', id, accessToken],
    queryFn: async () => {
      const response = await apiClient.get(`/candidates/${id}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    enabled: !!accessToken && !!id,
    refetchInterval: (query) => {
      const candidate = query.state.data;
      if (!candidate) return false;
      return (candidate.resumeStatus === 'PENDING' || candidate.resumeStatus === 'PARSING') ? 3000 : false;
    },
  });

  // Fetch candidate status logs
  const { data: statusLogs, refetch: refetchLogs } = useQuery<CandidateStatusLogResponse[]>({
    queryKey: ['candidateStatusLogs', id, accessToken],
    queryFn: async () => {
      const response = await apiClient.get(`/candidates/${id}/status-log`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    enabled: !!accessToken && !!id,
  });

  // Update pipeline status mutation with optimistic updates
  const updateStatusMutation = useMutation({
    mutationFn: async (newStatus: string) => {
      const response = await apiClient.patch(
        `/candidates/${id}/status`,
        { status: newStatus },
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        }
      );
      return response.data;
    },
    onMutate: async (newStatus) => {
      await queryClient.cancelQueries({ queryKey: ['candidate', id, accessToken] });
      const previousCandidate = queryClient.getQueryData<Candidate>(['candidate', id, accessToken]);
      if (previousCandidate) {
        queryClient.setQueryData<Candidate>(['candidate', id, accessToken], {
          ...previousCandidate,
          pipelineStatus: newStatus as any,
        });
      }
      return { previousCandidate };
    },
    onError: (err, newStatus, context) => {
      if (context?.previousCandidate) {
        queryClient.setQueryData(['candidate', id, accessToken], context.previousCandidate);
      }
      setToastMessage('Failed to update pipeline status. Reverted change.');
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
    onSuccess: () => {
      refetchLogs();
      setToastMessage('Pipeline status updated successfully.');
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
  });

  // Retry mutation
  const retryMutation = useMutation({
    mutationFn: async () => {
      if (!candidate) return;
      const response = await apiClient.post(
        `/job-postings/${candidate.jobPostingId}/candidates`,
        { resumeFileUrl: candidate.resumeFileUrl },
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        }
      );
      return response.data;
    },
    onSuccess: (newCandidate) => {
      if (newCandidate) {
        router.push(`/candidates/${newCandidate.id}`);
        setToastMessage('Retry submitted. Created new screening record.');
        setShowToast(true);
        setTimeout(() => setShowToast(false), 4000);
      }
    },
    onError: () => {
      setToastMessage('Failed to retry candidate screening.');
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    }
  });

  const is404 = (error as any)?.response?.status === 404;

  const handleStatusChange = (newStatus: string) => {
    updateStatusMutation.mutate(newStatus);
  };

  const getScoreColorClass = (score: number) => {
    if (score >= 70) return 'text-emerald-400 border-emerald-500/30 bg-emerald-950/20';
    if (score >= 40) return 'text-amber-400 border-amber-500/30 bg-amber-950/20';
    return 'text-rose-400 border-rose-500/30 bg-rose-950/20';
  };

  return (
    <ProtectedRoute>
      <div className={`min-h-screen bg-brand-bg text-brand-text-primary px-4 py-8 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}>
        <div className="max-w-4xl mx-auto space-y-8">
          
          {/* Toast Notification */}
          {showToast && (
            <div className="fixed bottom-5 right-5 z-50 bg-brand-surface border border-brand-accent-secondary/30 text-brand-accent-secondary px-5 py-3 rounded-lg shadow-xl flex items-center gap-3 animate-slide-in">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span className="text-sm font-semibold">{toastMessage}</span>
            </div>
          )}

          {/* NOT FOUND STATE */}
          {status === 'error' && is404 && (
            <div className="flex flex-col items-center justify-center py-20 px-4 bg-brand-surface border border-brand-border rounded-xl text-center space-y-6 max-w-xl mx-auto" data-testid="not-found-state">
              <div className="p-4 rounded-full bg-neutral-800/40 border border-brand-border text-brand-text-secondary">
                <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <div className="space-y-2">
                <h3 className={`text-2xl font-medium tracking-tight ${fraunces.className}`}>
                  Candidate Not Found
                </h3>
                <p className="text-sm text-brand-text-secondary max-w-md mx-auto">
                  The candidate you are looking for does not exist, or you do not have permission to view this details record.
                </p>
              </div>
              <Link
                href="/job-postings"
                className="inline-flex justify-center items-center border border-brand-accent px-6 py-2.5 text-sm font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/10 transition-colors"
              >
                Back to Job Postings
              </Link>
            </div>
          )}

          {/* LOADING STATE */}
          {status === 'pending' && (
            <div className="space-y-6 animate-pulse" data-testid="loading-state">
              <div className="h-10 w-1/3 bg-neutral-800 rounded" />
              <div className="h-96 w-full bg-brand-surface border border-brand-border rounded-xl" />
            </div>
          )}

          {/* GENERIC ERROR STATE */}
          {status === 'error' && !is404 && (
            <div className="flex flex-col items-center justify-center py-12 px-4 bg-brand-surface border border-brand-border rounded-xl max-w-md mx-auto text-center space-y-4">
              <h3 className={`text-lg font-medium ${fraunces.className}`}>Failed to load candidate</h3>
              <p className="text-sm text-brand-text-secondary">
                We encountered an error loading candidate details. Please check your connection.
              </p>
              <button
                onClick={() => refetch()}
                className="border border-brand-accent text-brand-accent px-5 py-2 rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-colors"
              >
                Retry
              </button>
            </div>
          )}

          {/* SUCCESS STATE */}
          {status === 'success' && candidate && (
            <div className="space-y-8" data-testid="success-state">
              
              {/* Header Card */}
              <div className="bg-brand-surface border border-brand-border rounded-xl p-6 sm:p-8 shadow-xl shadow-black/20 space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-6">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-3">
                      <h1 className={`text-2xl sm:text-3xl font-medium tracking-tight text-brand-text-primary ${fraunces.className}`}>
                        {candidate.name || 'Extraction pending'}
                      </h1>
                      <span className={`text-[10px] sm:text-xs px-2.5 py-0.5 rounded-full font-bold border capitalize ${
                        candidate.pipelineStatus === 'SHORTLISTED' ? 'border-emerald-500/30 text-emerald-400 bg-emerald-950/20' :
                        candidate.pipelineStatus === 'REJECTED' ? 'border-rose-500/30 text-rose-400 bg-rose-950/20' :
                        candidate.pipelineStatus === 'REVIEWING' ? 'border-amber-500/30 text-amber-400 bg-amber-950/20' :
                        'border-neutral-700 text-neutral-400 bg-neutral-800/40'
                      }`}>
                        {candidate.pipelineStatus}
                      </span>
                    </div>
                    <p className="text-xs sm:text-sm text-brand-text-secondary">
                      {candidate.email || 'Email: Extraction pending'}
                    </p>
                    <div className="pt-2">
                      <a
                        href={candidate.resumeFileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1.5 text-xs text-brand-accent hover:underline"
                      >
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        View Original Resume File
                      </a>
                    </div>
                  </div>

                  {/* Pipeline Status Control Dropdown */}
                  <div className="flex flex-col gap-1.5 w-full sm:w-48">
                    <label htmlFor="pipeline-status-select" className="text-[10px] font-bold uppercase tracking-wider text-brand-text-secondary">
                      Change Pipeline Status
                    </label>
                    <select
                      id="pipeline-status-select"
                      value={candidate.pipelineStatus}
                      onChange={(e) => handleStatusChange(e.target.value)}
                      disabled={updateStatusMutation.isPending}
                      className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-xs transition-all focus:outline-none focus:border-brand-accent text-brand-text-primary disabled:opacity-50"
                      data-testid="pipeline-status-dropdown"
                    >
                      <option value="NEW">New</option>
                      <option value="REVIEWING">Reviewing</option>
                      <option value="SHORTLISTED">Shortlisted</option>
                      <option value="REJECTED">Rejected</option>
                    </select>
                  </div>
                </div>
              </div>

              {/* Parsing/Pending State */}
              {(candidate.resumeStatus === 'PENDING' || candidate.resumeStatus === 'PARSING') && (
                <div className="bg-brand-surface border border-brand-border rounded-xl p-8 text-center space-y-4 shadow-xl" data-testid="in-progress-state">
                  <div className="inline-flex items-center justify-center p-3 bg-neutral-800/40 border border-brand-border rounded-full text-brand-accent animate-spin">
                    <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 19l-1.272-1.272" />
                    </svg>
                  </div>
                  <h3 className={`text-xl font-medium text-brand-text-primary ${fraunces.className}`}>
                    Resume Evaluation in Progress
                  </h3>
                  <p className="text-sm text-brand-text-secondary max-w-md mx-auto">
                    We are currently extracting and ranking this candidate's resume. This page will update automatically when the evaluation completes.
                  </p>
                </div>
              )}

              {/* Failed State */}
              {candidate.resumeStatus === 'FAILED' && (
                <div className="bg-brand-surface border border-brand-border rounded-xl p-6 sm:p-8 space-y-6 shadow-xl shadow-black/20" data-testid="failed-state">
                  <div className="flex items-start gap-4 p-4 bg-rose-950/15 border border-rose-500/20 rounded-lg">
                    <svg className="h-5 w-5 text-rose-400 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <div className="space-y-1">
                      <h4 className="text-sm font-semibold text-rose-400">Screening Failure</h4>
                      <p className="text-xs text-rose-300 leading-relaxed" data-testid="parse-error">
                        Error: {candidate.parseError || 'An unknown error occurred during resume parsing and AI scoring.'}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => retryMutation.mutate()}
                      disabled={retryMutation.isPending}
                      className="border border-brand-accent text-brand-accent px-5 py-2 rounded-lg text-xs font-semibold hover:bg-brand-accent/15 transition-all disabled:opacity-50"
                      data-testid="retry-button"
                    >
                      {retryMutation.isPending ? 'Submitting Retry...' : 'Retry Evaluation'}
                    </button>
                    <span className="text-[10px] text-brand-text-secondary italic">
                      * Initiates a new AI evaluation cycle under a new screening record.
                    </span>
                  </div>
                </div>
              )}

              {/* Scored Details State */}
              {candidate.resumeStatus === 'SCORED' && (
                <div className="grid grid-cols-1 md:grid-cols-12 gap-8" data-testid="scored-state">
                  
                  {/* Left Column: Overall Score & Sub-scores */}
                  <div className="md:col-span-5 space-y-6">
                    <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl text-center space-y-4">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-brand-text-secondary">
                        Overall Score
                      </h3>
                      {candidate.overallScore !== null ? (
                        <div className="flex flex-col items-center justify-center space-y-2">
                          <div className={`h-28 w-28 rounded-full border-4 flex flex-col items-center justify-center ${getScoreColorClass(candidate.overallScore)}`}>
                            <span className="text-3xl font-extrabold">{candidate.overallScore}</span>
                            <span className="text-[10px] uppercase font-bold text-brand-text-secondary">/ 100</span>
                          </div>
                        </div>
                      ) : (
                        <p className="text-sm text-brand-text-secondary">No score generated</p>
                      )}
                      
                      {candidate.yearsExperienceDetected !== null && (
                        <div className="pt-2 border-t border-brand-border/40">
                          <span className="text-xs text-brand-text-secondary">Detected Experience: </span>
                          <span className="text-xs font-semibold text-brand-text-primary">
                            {candidate.yearsExperienceDetected} {candidate.yearsExperienceDetected === 1 ? 'year' : 'years'}
                          </span>
                        </div>
                      )}
                    </div>

                    {/* Labeled sub-score bars */}
                    <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl space-y-4">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-brand-text-secondary">
                        Sub-Score Breakdown
                      </h4>
                      <div className="space-y-4" data-testid="score-breakdown">
                        {/* Skills Score */}
                        <div className="space-y-1.5">
                          <div className="flex justify-between text-xs font-medium">
                            <span className="text-brand-text-primary">Skills Match</span>
                            <span className="text-brand-text-primary">{candidate.skillsScore ?? 0}/100</span>
                          </div>
                          <div className="h-1.5 bg-neutral-800 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-brand-accent transition-all duration-500"
                              style={{ width: `${candidate.skillsScore ?? 0}%` }}
                            />
                          </div>
                        </div>

                        {/* Experience Score */}
                        <div className="space-y-1.5">
                          <div className="flex justify-between text-xs font-medium">
                            <span className="text-brand-text-primary">Experience Relevance</span>
                            <span className="text-brand-text-primary">{candidate.experienceScore ?? 0}/100</span>
                          </div>
                          <div className="h-1.5 bg-neutral-800 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-brand-accent transition-all duration-500"
                              style={{ width: `${candidate.experienceScore ?? 0}%` }}
                            />
                          </div>
                        </div>

                        {/* Seniority Score */}
                        <div className="space-y-1.5">
                          <div className="flex justify-between text-xs font-medium">
                            <span className="text-brand-text-primary">Seniority Alignment</span>
                            <span className="text-brand-text-primary">{candidate.seniorityScore ?? 0}/100</span>
                          </div>
                          <div className="h-1.5 bg-neutral-800 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-brand-accent transition-all duration-500"
                              style={{ width: `${candidate.seniorityScore ?? 0}%` }}
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Right Column: Matched/Missing Skills & Summary */}
                  <div className="md:col-span-7 space-y-6">
                    
                    {/* Candidate Summary */}
                    <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl space-y-3">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-brand-text-secondary">
                        AI Screening Summary
                      </h4>
                      <p className="text-xs sm:text-sm text-brand-text-primary leading-relaxed whitespace-pre-line">
                        {candidate.summary || 'No summary breakdown available.'}
                      </p>
                    </div>

                    {/* Matched & Missing Skills */}
                    <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl space-y-6">
                      
                      {/* Matched Skills */}
                      <div className="space-y-3">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-brand-text-secondary">
                          Matched Skills ({candidate.matchedSkills?.length || 0})
                        </h4>
                        {candidate.matchedSkills && candidate.matchedSkills.length > 0 ? (
                          <div className="flex flex-wrap gap-2">
                            {candidate.matchedSkills.map((skill) => (
                              <span
                                key={skill}
                                className="px-2.5 py-1 bg-emerald-950/20 text-emerald-400 border border-emerald-500/30 rounded-lg text-xs font-medium"
                              >
                                {skill}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <p className="text-xs text-brand-text-secondary italic">No matching skills detected.</p>
                        )}
                      </div>

                      {/* Missing Skills */}
                      <div className="space-y-3">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-brand-text-secondary">
                          Missing Skills ({candidate.missingSkills?.length || 0})
                        </h4>
                        {candidate.missingSkills && candidate.missingSkills.length > 0 ? (
                          <div className="flex flex-wrap gap-2">
                            {candidate.missingSkills.map((skill) => (
                              <span
                                key={skill}
                                className="px-2.5 py-1 bg-neutral-800 text-neutral-400 border border-neutral-700 rounded-lg text-xs font-medium"
                              >
                                {skill}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <p className="text-xs text-brand-text-secondary italic">No missing skills detected.</p>
                        )}
                      </div>

                    </div>
                  </div>
                </div>
              )}

              {/* Status History Logs Collapsible Section */}
              <div className="bg-brand-surface border border-brand-border rounded-xl shadow-xl overflow-hidden">
                <button
                  type="button"
                  onClick={() => setIsHistoryOpen(!isHistoryOpen)}
                  className="w-full flex items-center justify-between p-5 text-sm font-semibold text-brand-text-primary hover:bg-neutral-800/10 transition-colors"
                  data-testid="status-history-toggle"
                >
                  <span>Pipeline Audit History</span>
                  <svg
                    className={`h-5 w-5 transform transition-transform ${isHistoryOpen ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {isHistoryOpen && (
                  <div className="p-5 border-t border-brand-border/40 bg-brand-bg/20 space-y-4" data-testid="status-history-content">
                    {statusLogs && statusLogs.length > 0 ? (
                      <div className="relative border-l border-brand-border/60 ml-3 space-y-6">
                        {statusLogs.map((log) => {
                          const date = new Date(log.createdAt).toLocaleString();
                          return (
                            <div key={log.id} className="relative pl-6" data-testid="status-history-log-row">
                              <div className="absolute -left-1.5 top-1.5 h-3 w-3 rounded-full bg-brand-accent border border-brand-bg" />
                              <div className="space-y-1">
                                <p className="text-xs text-brand-text-primary">
                                  Moved from <span className="font-semibold text-brand-text-secondary">{log.fromStatus}</span> to{' '}
                                  <span className="font-semibold text-brand-accent-secondary">{log.toStatus}</span>
                                </p>
                                <p className="text-[10px] text-brand-text-secondary">
                                  By {log.changedByEmail} • {date}
                                </p>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="text-xs text-brand-text-secondary italic">No history logged yet.</p>
                    )}
                  </div>
                )}
              </div>

              {/* Back navigation */}
              <div className="flex justify-start">
                <Link
                  href={`/job-postings/${candidate.jobPostingId}`}
                  className="px-5 py-2.5 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold hover:border-brand-text-primary hover:text-brand-text-primary transition-all"
                >
                  Back to Job Posting Detail
                </Link>
              </div>

            </div>
          )}

        </div>
      </div>
    </ProtectedRoute>
  );
}
