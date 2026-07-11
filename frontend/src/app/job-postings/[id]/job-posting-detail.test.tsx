import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import JobPostingDetailPage from './page';
import { apiClient } from '@/lib/api-client';

// Mock useRouter
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Auth Context
vi.mock('@/context/auth-context', () => ({
  useAuth: () => ({
    accessToken: 'mocked-token-123',
    user: { email: 'recruiter@example.com' },
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock Protected Route
vi.mock('../../components/protected-route', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock API Client
vi.mock('@/lib/api-client', () => ({
  apiClient: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });

describe('JobPostingDetailPage Edit Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('a 404 API response renders the not-found state', async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: 'posting-123' });
      },
      status: 'fulfilled',
      value: { id: 'posting-123' }
    } as any;
    // Mock GET call to throw 404
    const mockError: any = new Error('Not found');
    mockError.response = { status: 404 };
    vi.mocked(apiClient.get).mockRejectedValue(mockError);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>
    );

    // Wait for resolution and confirm the not-found card is rendered
    await waitFor(() => {
      expect(screen.getByTestId('not-found-state')).toBeInTheDocument();
    });

    expect(screen.getByText(/Job Posting Not Found/i)).toBeInTheDocument();
    expect(screen.getByText(/back to job postings/i)).toBeInTheDocument();
    expect(screen.queryByTestId('success-state')).not.toBeInTheDocument();
  });

  it('editing the title and saving calls the PATCH mutation with correct payload', async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: 'posting-123' });
      },
      status: 'fulfilled',
      value: { id: 'posting-123' }
    } as any;
    // Mock GET call to return existing job posting details
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        id: 'posting-123',
        title: 'Original Title',
        description: 'Original description.',
        requiredSkills: ['Python'],
        niceToHaveSkills: [],
        minYearsExperience: 3,
        seniorityLevel: 'MID',
        status: 'ACTIVE',
        createdAt: '2026-07-10T12:00:00Z',
        updatedAt: '2026-07-10T12:00:00Z',
      },
    });

    // Mock PATCH call to return updated details
    vi.mocked(apiClient.patch).mockResolvedValue({
      data: {
        id: 'posting-123',
        title: 'Updated Architect Title',
        description: 'Original description.',
        requiredSkills: ['Python'],
        niceToHaveSkills: [],
        minYearsExperience: 3,
        seniorityLevel: 'MID',
        status: 'ACTIVE',
        createdAt: '2026-07-10T12:00:00Z',
        updatedAt: '2026-07-10T12:00:00Z',
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>
    );

    // Wait for data load success
    await waitFor(() => {
      expect(screen.getByTestId('success-state')).toBeInTheDocument();
    });

    // Verify fields are populated
    const titleInput = screen.getByLabelText(/Job Title/i) as HTMLInputElement;
    expect(titleInput.value).toBe('Original Title');

    // Change title
    fireEvent.change(titleInput, { target: { value: 'Updated Architect Title' } });

    // Submit save
    fireEvent.click(screen.getByRole('button', { name: /Save Updates/i }));

    // Confirm PATCH is called with the correct payload
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledTimes(1);
    });

    expect(apiClient.patch).toHaveBeenCalledWith(
      '/job-postings/posting-123',
      expect.objectContaining({
        title: 'Updated Architect Title',
        description: 'Original description.',
      }),
      expect.any(Object)
    );

    // Verify toast confirmation is shown
    expect(screen.getByText('Job posting saved successfully.')).toBeInTheDocument();
  });
});
