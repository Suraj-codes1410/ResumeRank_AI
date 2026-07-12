import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import JobPostingDetailPage from './page';
import { apiClient } from '@/lib/api-client';
import axios from 'axios';

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
    post: vi.fn(),
  },
}));

// Mock Axios directly for Cloudinary direct POST calls
vi.mock('axios', async (importOriginal) => {
  const actual: any = await importOriginal();
  return {
    ...actual,
    default: {
      ...actual.default,
      post: vi.fn(),
      create: vi.fn(() => ({
        get: vi.fn(),
        post: vi.fn(),
        patch: vi.fn(),
      })),
    },
  };
});

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });

describe('JobPostingDetailPage Edit & Upload Tests', () => {
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
    // Mock GET call to return existing job posting details and empty candidates
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: [] };
      }
      return {
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
      };
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

  it('uploading a file transitions UI states and polls correctly until scored', async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: 'posting-123' });
      },
      status: 'fulfilled',
      value: { id: 'posting-123' }
    } as any;

    // 1. Mock GET calls
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: [] }; // Initial empty candidate list
      }
      return {
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
      };
    });

    // 2. Mock signature generation responses
    vi.mocked(apiClient.post).mockImplementation(async (url) => {
      if (url.includes('/signature')) {
        return {
          data: {
            signature: 'mock-sig',
            timestamp: 12345678,
            apiKey: 'mock-key',
            cloudName: 'mock-cloud',
            folder: 'resumes/user-abc'
          }
        };
      }
      return { data: {} };
    });

    // 3. Mock Cloudinary POST
    vi.mocked(axios.post).mockResolvedValue({
      data: {
        secure_url: 'http://cloudinary.com/dummy-file.pdf'
      }
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>
    );

    // Wait for successfully rendering the edit details panel
    await waitFor(() => {
      expect(screen.getByTestId('success-state')).toBeInTheDocument();
    });

    // Verify candidates list displays empty state
    expect(screen.getByTestId('no-candidates')).toBeInTheDocument();

    // 4. Mock candidate registration callback response
    const mockPendingCandidate = {
      id: 'candidate-111',
      jobPostingId: 'posting-123',
      name: null,
      email: null,
      resumeFileUrl: 'http://cloudinary.com/dummy-file.pdf',
      resumeStatus: 'PENDING',
      parseError: null,
      overallScore: null,
      createdAt: '2026-07-10T12:05:00Z',
      updatedAt: '2026-07-10T12:05:00Z'
    };

    vi.mocked(apiClient.post).mockImplementation(async (url, body) => {
      if (url.includes('/signature')) {
        return {
          data: {
            signature: 'mock-sig',
            timestamp: 12345678,
            apiKey: 'mock-key',
            cloudName: 'mock-cloud',
            folder: 'resumes/user-abc'
          }
        };
      }
      // Create candidate response
      return { data: mockPendingCandidate };
    });

    // Simulate input file change
    const file = new File(['dummy content'], 'dummy-file.pdf', { type: 'application/pdf' });
    // Use container selector to get file input by id since text elements might not match directly
    const fileInput = document.getElementById('resume-file-input') as HTMLInputElement;
    expect(fileInput).toBeInTheDocument();

    fireEvent.change(fileInput, { target: { files: [file] } });

    // Wait for mutation to complete and trigger candidates reload
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/uploads/signature',
        expect.any(Object),
        expect.any(Object)
      );
      expect(axios.post).toHaveBeenCalledTimes(1);
    });

    // Now mock candidate list query to return the pending candidate (triggers polling loop)
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: [mockPendingCandidate] };
      }
      return { data: { id: 'posting-123', title: 'Original Title' } };
    });

    queryClient.invalidateQueries({ queryKey: ['candidates'] });

    // Confirm that a candidate with PENDING badge is now visible in the list
    await waitFor(() => {
      expect(screen.getByText('Pending')).toBeInTheDocument();
    });

    // 5. Verify Polling Stops when status transitions to scored
    const mockScoredCandidate = {
      ...mockPendingCandidate,
      resumeStatus: 'SCORED',
      overallScore: 85,
      name: 'John Doe',
      email: 'john@example.com'
    };

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: [mockScoredCandidate] };
      }
      return { data: { id: 'posting-123', title: 'Original Title' } };
    });

    queryClient.invalidateQueries({ queryKey: ['candidates'] });

    // Expect SCORED badge containing the overallScore
    await waitFor(() => {
      expect(screen.getByText('Score: 85/100')).toBeInTheDocument();
    });

    // Assert that candidates list fetching call count stabilizes
    const initialCallCount = vi.mocked(apiClient.get).mock.calls.length;

    // Wait short delay
    await new Promise((resolve) => setTimeout(resolve, 100));

    const finalCallCount = vi.mocked(apiClient.get).mock.calls.length;
    // Since scored is a terminal state, no further candidate listing polls should run
    expect(finalCallCount).toBe(initialCallCount);
  });
});
