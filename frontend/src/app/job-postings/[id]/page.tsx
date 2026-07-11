'use client';

import React, { useState, useEffect, KeyboardEvent, use } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Fraunces, Inter } from 'next/font/google';
import Link from 'next/link';
import { useAuth } from '@/context/auth-context';
import { apiClient } from '@/lib/api-client';
import ProtectedRoute from '../../components/protected-route';
import { jobPostingFormSchema, JobPostingFormData } from '../schema';

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

interface JobPosting {
  id: string;
  title: string;
  description: string;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  minYearsExperience: number | null;
  seniorityLevel: 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD' | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

export default function JobPostingDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { accessToken } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [reqSkillInput, setReqSkillInput] = useState('');
  const [niceSkillInput, setNiceSkillInput] = useState('');
  const [showArchiveConfirm, setShowArchiveConfirm] = useState(false);
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState('');

  const { data, status, error, refetch } = useQuery<JobPosting>({
    queryKey: ['jobPosting', id, accessToken],
    queryFn: async () => {
      const response = await apiClient.get(`/job-postings/${id}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    enabled: !!accessToken && !!id,
    retry: false,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors },
  } = useForm<any>({
    resolver: zodResolver(jobPostingFormSchema),
    defaultValues: {
      title: '',
      description: '',
      requiredSkills: [],
      niceToHaveSkills: [],
      minYearsExperience: '',
      seniorityLevel: '',
    },
  });

  // Pre-populate form once details are fetched
  useEffect(() => {
    if (data) {
      reset({
        title: data.title,
        description: data.description,
        requiredSkills: data.requiredSkills || [],
        niceToHaveSkills: data.niceToHaveSkills || [],
        minYearsExperience: data.minYearsExperience ?? '',
        seniorityLevel: data.seniorityLevel ?? '',
      });
    }
  }, [data, reset]);

  const requiredSkills = watch('requiredSkills') || [];
  const niceToHaveSkills = watch('niceToHaveSkills') || [];

  const updateMutation = useMutation({
    mutationFn: async (payload: Partial<JobPosting>) => {
      const response = await apiClient.patch(`/job-postings/${id}`, payload, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    onSuccess: (updatedData) => {
      // Update both detail cache and list cache
      queryClient.setQueryData(['jobPosting', id, accessToken], updatedData);
      queryClient.setQueriesData(
        { queryKey: ['jobPostings'] },
        (oldData: any) => {
          if (!oldData) return oldData;
          return {
            ...oldData,
            items: oldData.items.map((item: any) =>
              item.id === id ? updatedData : item
            ),
          };
        }
      );
      setToastMessage('Job posting saved successfully.');
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
    onError: (err: any) => {
      const detail = err.response?.data?.detail;
      if (typeof detail === 'string') {
        const fieldErrors = detail.split(', ');
        fieldErrors.forEach((errStr) => {
          const parts = errStr.split(': ');
          if (parts.length >= 2) {
            const field = parts[0].trim();
            const message = parts.slice(1).join(': ').trim();
            if (field === 'title' || field === 'description') {
              setError(field as 'title' | 'description', { type: 'server', message });
            }
          }
        });
      } else {
        setError('root', { type: 'server', message: 'Failed to update job posting. Please try again.' });
      }
    },
  });

  const onSubmit = (formData: JobPostingFormData) => {
    updateMutation.mutate(formData);
  };

  const handleAddSkill = (
    e: KeyboardEvent<HTMLInputElement>,
    type: 'required' | 'nice'
  ) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const input = type === 'required' ? reqSkillInput : niceSkillInput;
      const setInput = type === 'required' ? setReqSkillInput : setNiceSkillInput;
      const currentList = type === 'required' ? requiredSkills : niceToHaveSkills;
      const field = type === 'required' ? 'requiredSkills' : 'niceToHaveSkills';

      const trimmed = input.trim();
      if (trimmed && !currentList.includes(trimmed)) {
        setValue(field, [...currentList, trimmed]);
        setInput('');
      }
    }
  };

  const handleRemoveSkill = (skillToRemove: string, type: 'required' | 'nice') => {
    const currentList = type === 'required' ? requiredSkills : niceToHaveSkills;
    const field = type === 'required' ? 'requiredSkills' : 'niceToHaveSkills';
    setValue(
      field,
      currentList.filter((s: string) => s !== skillToRemove)
    );
  };

  const handleToggleStatus = () => {
    if (!data) return;
    const nextStatus = data.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE';
    updateMutation.mutate(
      { status: nextStatus },
      {
        onSuccess: (updated) => {
          queryClient.setQueryData(['jobPosting', id, accessToken], updated);
          setToastMessage(`Job posting has been ${nextStatus === 'ACTIVE' ? 'reactivated' : 'archived'}.`);
          setShowToast(true);
          setShowArchiveConfirm(false);
          setTimeout(() => setShowToast(false), 4000);
        },
      }
    );
  };

  // Determine if error is a 404 (Not Found or Not Yours)
  const is404 = (error as any)?.response?.status === 404;

  return (
    <ProtectedRoute>
      <div className={`min-h-screen bg-brand-bg text-brand-text-primary px-4 py-8 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}>
        <div className="max-w-3xl mx-auto space-y-8">
          
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
                  Job Posting Not Found
                </h3>
                <p className="text-sm text-brand-text-secondary max-w-md mx-auto">
                  The job posting you are looking for does not exist, or you do not have permission to view/edit it.
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
              <h3 className={`text-lg font-medium ${fraunces.className}`}>Failed to load posting</h3>
              <p className="text-sm text-brand-text-secondary">
                We encountered an error loading details. Please check your connection.
              </p>
              <button
                onClick={() => refetch()}
                className="border border-brand-accent text-brand-accent px-5 py-2 rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-colors"
              >
                Retry
              </button>
            </div>
          )}

          {/* SUCCESS EDIT FORM */}
          {status === 'success' && data && (
            <div className="space-y-8" data-testid="success-state">
              
              {/* Header */}
              <div className="border-b border-brand-border pb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h1 className={`text-3xl font-medium tracking-tight ${fraunces.className}`}>
                      Edit Job Posting
                    </h1>
                    <span
                      className={`text-xs px-2.5 py-0.5 rounded-full font-medium border ${
                        data.status === 'ACTIVE'
                          ? 'border-brand-accent-secondary/30 text-brand-accent-secondary bg-brand-accent-secondary/5'
                          : 'border-brand-border text-brand-text-secondary bg-brand-bg/40'
                      }`}
                    >
                      {data.status}
                    </span>
                  </div>
                  <p className="text-sm text-brand-text-secondary mt-1">
                    Update details or toggle status visibility for your screening pipeline.
                  </p>
                </div>
                
                {/* Archive / Reactivate Button */}
                <div>
                  {data.status === 'ACTIVE' ? (
                    <button
                      type="button"
                      onClick={() => setShowArchiveConfirm(true)}
                      className="inline-flex items-center justify-center border border-rose-500/40 px-5 py-2 text-xs font-semibold rounded-lg text-rose-400 hover:bg-rose-500/10 transition-all"
                    >
                      Archive Posting
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleToggleStatus}
                      className="inline-flex items-center justify-center border border-brand-accent px-5 py-2 text-xs font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/10 transition-all"
                    >
                      Reactivate Posting
                    </button>
                  )}
                </div>
              </div>

              {/* Form container */}
              <form
                onSubmit={handleSubmit(onSubmit)}
                className="bg-brand-surface border border-brand-border rounded-xl p-6 sm:p-8 space-y-6 shadow-xl shadow-black/20"
              >
                {errors.root && (
                  <div className="p-4 bg-rose-950/20 border border-rose-500/30 text-rose-400 rounded-lg text-sm">
                    {errors.root.message?.toString()}
                  </div>
                )}

                {/* Title */}
                <div className="flex flex-col gap-2">
                  <label htmlFor="title" className="text-sm font-semibold text-brand-text-primary">
                    Job Title <span className="text-brand-accent">*</span>
                  </label>
                  <input
                    id="title"
                    type="text"
                    {...register('title')}
                    placeholder="e.g. Senior Software Architect"
                    className={`w-full bg-brand-bg border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent ${
                      errors.title ? 'border-rose-500/60 focus:border-rose-500' : 'border-brand-border'
                    }`}
                  />
                  {errors.title && (
                    <span className="text-xs text-rose-400 font-medium">{errors.title.message?.toString()}</span>
                  )}
                </div>

                {/* Description */}
                <div className="flex flex-col gap-2">
                  <label htmlFor="description" className="text-sm font-semibold text-brand-text-primary">
                    Job Description <span className="text-brand-accent">*</span>
                  </label>
                  <textarea
                    id="description"
                    rows={6}
                    {...register('description')}
                    placeholder="Describe the responsibilities..."
                    className={`w-full bg-brand-bg border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent ${
                      errors.description ? 'border-rose-500/60 focus:border-rose-500' : 'border-brand-border'
                    }`}
                  />
                  {errors.description && (
                    <span className="text-xs text-rose-400 font-medium">{errors.description.message?.toString()}</span>
                  )}
                </div>

                {/* Split row */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                  {/* Seniority */}
                  <div className="flex flex-col gap-2">
                    <label htmlFor="seniorityLevel" className="text-sm font-semibold text-brand-text-primary">
                      Seniority Level
                    </label>
                    <select
                      id="seniorityLevel"
                      {...register('seniorityLevel')}
                      className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent"
                    >
                      <option value="">Select Casing...</option>
                      <option value="JUNIOR">Junior</option>
                      <option value="MID">Mid</option>
                      <option value="SENIOR">Senior</option>
                      <option value="LEAD">Lead</option>
                    </select>
                  </div>

                  {/* Years */}
                  <div className="flex flex-col gap-2">
                    <label htmlFor="minYearsExperience" className="text-sm font-semibold text-brand-text-primary">
                      Minimum Experience (Years)
                    </label>
                    <input
                      id="minYearsExperience"
                      type="number"
                      placeholder="e.g. 5"
                      {...register('minYearsExperience', { valueAsNumber: true })}
                      className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent"
                    />
                  </div>
                </div>

                {/* Required Skills */}
                <div className="flex flex-col gap-2">
                  <label htmlFor="reqSkillInput" className="text-sm font-semibold text-brand-text-primary">
                    Required Skills
                  </label>
                  <input
                    id="reqSkillInput"
                    type="text"
                    value={reqSkillInput}
                    onChange={(e) => setReqSkillInput(e.target.value)}
                    onKeyDown={(e) => handleAddSkill(e, 'required')}
                    placeholder="Type skill and press Enter"
                    className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent"
                  />
                  {requiredSkills.length > 0 && (
                    <div className="flex flex-wrap gap-2 mt-2">
                      {requiredSkills.map((skill: string) => (
                        <span
                          key={skill}
                          className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-accent-secondary/5 border border-brand-accent-secondary/30 text-brand-accent-secondary rounded-lg text-xs font-semibold"
                        >
                          {skill}
                          <button
                            type="button"
                            onClick={() => handleRemoveSkill(skill, 'required')}
                            className="hover:text-rose-400 transition-colors"
                          >
                            &times;
                          </button>
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Nice to Have Skills */}
                <div className="flex flex-col gap-2">
                  <label htmlFor="niceSkillInput" className="text-sm font-semibold text-brand-text-primary">
                    Nice to Have Skills
                  </label>
                  <input
                    id="niceSkillInput"
                    type="text"
                    value={niceSkillInput}
                    onChange={(e) => setNiceSkillInput(e.target.value)}
                    onKeyDown={(e) => handleAddSkill(e, 'nice')}
                    placeholder="Type skill and press Enter"
                    className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus:border-brand-accent"
                  />
                  {niceToHaveSkills.length > 0 && (
                    <div className="flex flex-wrap gap-2 mt-2">
                      {niceToHaveSkills.map((skill: string) => (
                        <span
                          key={skill}
                          className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-bg/60 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold"
                        >
                          {skill}
                          <button
                            type="button"
                            onClick={() => handleRemoveSkill(skill, 'nice')}
                            className="hover:text-rose-400 transition-colors"
                          >
                            &times;
                          </button>
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Actions */}
                <div className="border-t border-brand-border/40 pt-6 mt-6 flex justify-end gap-4">
                  <Link
                    href="/job-postings"
                    className="px-5 py-2.5 border border-brand-border text-brand-text-secondary rounded-lg text-sm font-semibold hover:border-brand-text-primary hover:text-brand-text-primary transition-all"
                  >
                    Back
                  </Link>
                  <button
                    type="submit"
                    disabled={updateMutation.isPending}
                    className="border border-brand-accent bg-transparent text-brand-accent px-6 py-2.5 rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {updateMutation.isPending ? 'Saving...' : 'Save Updates'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* ARCHIVE CONFIRMATION MODAL */}
          {showArchiveConfirm && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fade-in" data-testid="archive-modal">
              <div className="bg-brand-surface border border-brand-border rounded-xl max-w-md w-full p-6 space-y-6 shadow-2xl animate-zoom-in">
                <div className="space-y-2">
                  <h3 className={`text-xl font-medium tracking-tight text-brand-text-primary ${fraunces.className}`}>
                    Archive Job Posting?
                  </h3>
                  <p className="text-sm text-brand-text-secondary leading-relaxed">
                    Archiving this job posting will hide it from the active screen lists and dashboard workflows. Candidates mapped to this post will no longer be visible.
                  </p>
                </div>
                <div className="flex justify-end gap-4">
                  <button
                    type="button"
                    onClick={() => setShowArchiveConfirm(false)}
                    className="px-4 py-2 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold hover:border-brand-text-primary hover:text-brand-text-primary transition-colors"
                  >
                    No, Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleToggleStatus}
                    className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-semibold transition-colors"
                  >
                    Yes, Archive
                  </button>
                </div>
              </div>
            </div>
          )}

        </div>
      </div>
    </ProtectedRoute>
  );
}
