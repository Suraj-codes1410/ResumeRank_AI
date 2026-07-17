import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CandidateDetailPage from "./page";
import { apiClient } from "@/lib/api-client";

// Mock useRouter
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Auth Context
vi.mock("@/context/auth-context", () => ({
  useAuth: () => ({
    accessToken: "mocked-token-123",
    user: { email: "recruiter@example.com" },
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

// Mock Protected Route
vi.mock("../../components/protected-route", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock API Client
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
  },
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

describe("CandidateDetailPage Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders all score sections for a scored candidate", async () => {
    const mockParams = {
      then: (onFulfill: (value: { id: string }) => void) => {
        onFulfill({ id: "candidate-123" });
      },
      status: "fulfilled",
      value: { id: "candidate-123" },
    } as unknown as Promise<{ id: string }>;

    const mockCandidate = {
      id: "candidate-123",
      jobPostingId: "posting-123",
      name: "John Doe",
      email: "john@example.com",
      resumeFileUrl: "https://cloudinary.com/dummy.pdf",
      resumeStatus: "SCORED",
      overallScore: 85,
      skillsScore: 90,
      experienceScore: 80,
      seniorityScore: 85,
      matchedSkills: ["Java", "Spring Boot"],
      missingSkills: ["React", "Next.js"],
      summary: "Excellent match for the role.",
      yearsExperienceDetected: 5,
      pipelineStatus: "NEW",
      createdAt: "2026-07-15T12:00:00Z",
      updatedAt: "2026-07-15T12:00:00Z",
    };

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/status-log")) {
        return { data: [] };
      }
      return { data: mockCandidate };
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <CandidateDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Assert overall info
    await waitFor(() => {
      expect(screen.getByText("John Doe")).toBeInTheDocument();
      expect(screen.getByText("john@example.com")).toBeInTheDocument();
      expect(screen.getByTestId("scored-state")).toBeInTheDocument();
    });

    // Assert overall score
    expect(screen.getByText("85")).toBeInTheDocument();
    expect(screen.getByText("/ 100")).toBeInTheDocument();

    // Assert sub-scores
    expect(screen.getByText("Skills Match")).toBeInTheDocument();
    expect(screen.getByText("90/100")).toBeInTheDocument();
    expect(screen.getByText("Experience Relevance")).toBeInTheDocument();
    expect(screen.getByText("80/100")).toBeInTheDocument();
    expect(screen.getByText("Seniority Alignment")).toBeInTheDocument();
    expect(screen.getByText("85/100")).toBeInTheDocument();

    // Assert skills tags
    expect(screen.getByText("Java")).toBeInTheDocument();
    expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    expect(screen.getByText("React")).toBeInTheDocument();
    expect(screen.getByText("Next.js")).toBeInTheDocument();

    // Assert summary and experience
    expect(
      screen.getByText("Excellent match for the role."),
    ).toBeInTheDocument();
    expect(screen.getByText("5 years")).toBeInTheDocument();
  });

  it("renders parseError and retry button for a failed candidate", async () => {
    const mockParams = {
      then: (onFulfill: (value: { id: string }) => void) => {
        onFulfill({ id: "candidate-123" });
      },
      status: "fulfilled",
      value: { id: "candidate-123" },
    } as unknown as Promise<{ id: string }>;

    const mockCandidate = {
      id: "candidate-123",
      jobPostingId: "posting-123",
      name: null,
      email: null,
      resumeFileUrl: "https://cloudinary.com/dummy.pdf",
      resumeStatus: "FAILED",
      parseError: "Failed to extract text from PDF.",
      pipelineStatus: "NEW",
      createdAt: "2026-07-15T12:00:00Z",
      updatedAt: "2026-07-15T12:00:00Z",
    };

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/status-log")) {
        return { data: [] };
      }
      return { data: mockCandidate };
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <CandidateDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for fail state to render
    await waitFor(() => {
      expect(screen.getByTestId("failed-state")).toBeInTheDocument();
    });

    // Check error details and retry button
    expect(screen.getByTestId("parse-error")).toHaveTextContent(
      "Failed to extract text from PDF.",
    );
    const retryBtn = screen.getByTestId("retry-button");
    expect(retryBtn).toBeInTheDocument();

    // Verify scored details are NOT rendered
    expect(screen.queryByTestId("scored-state")).not.toBeInTheDocument();

    // Click retry
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { id: "new-candidate-456" },
    });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        "/job-postings/posting-123/candidates",
        { resumeFileUrl: "https://cloudinary.com/dummy.pdf" },
        expect.anything(),
      );
      expect(mockPush).toHaveBeenCalledWith("/candidates/new-candidate-456");
    });
  });

  it("changing pipeline status calls the PATCH endpoint with the correct payload", async () => {
    const mockParams = {
      then: (onFulfill: (value: { id: string }) => void) => {
        onFulfill({ id: "candidate-123" });
      },
      status: "fulfilled",
      value: { id: "candidate-123" },
    } as unknown as Promise<{ id: string }>;

    const mockCandidate = {
      id: "candidate-123",
      jobPostingId: "posting-123",
      name: "John Doe",
      email: "john@example.com",
      resumeFileUrl: "https://cloudinary.com/dummy.pdf",
      resumeStatus: "SCORED",
      overallScore: 85,
      pipelineStatus: "NEW",
      createdAt: "2026-07-15T12:00:00Z",
      updatedAt: "2026-07-15T12:00:00Z",
    };

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/status-log")) {
        return { data: [] };
      }
      return { data: mockCandidate };
    });

    vi.mocked(apiClient.patch).mockResolvedValueOnce({
      data: { ...mockCandidate, pipelineStatus: "SHORTLISTED" },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <CandidateDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for render
    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // Select new status
    const statusSelect = screen.getByTestId(
      "pipeline-status-dropdown",
    ) as HTMLSelectElement;
    fireEvent.change(statusSelect, { target: { value: "SHORTLISTED" } });

    // Expect PATCH to be called
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith(
        "/candidates/candidate-123/status",
        { status: "SHORTLISTED" },
        expect.anything(),
      );
    });
  });
});
