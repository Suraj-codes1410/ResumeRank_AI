import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CreateJobPostingPage from "./page";
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
    post: vi.fn(),
  },
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });

describe("CreateJobPostingPage Form Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("submitting with an empty title shows a validation error and does not call the mutation", async () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <CreateJobPostingPage />
      </QueryClientProvider>,
    );

    // Populate description, leave title blank
    fireEvent.change(screen.getByLabelText(/Job Description/i), {
      target: { value: "This is a description of the job posting." },
    });

    // Click submit
    fireEvent.click(
      screen.getByRole("button", { name: /Publish Job Posting/i }),
    );

    // Assert validation message shows up and API is not called
    await waitFor(() => {
      expect(screen.getByText(/Title is required/i)).toBeInTheDocument();
    });

    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it("a successful submission navigates away", async () => {
    // Mock successful API response
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        id: "new-job-123",
        title: "Python Engineer",
        description: "Building REST endpoints",
        requiredSkills: ["Python"],
        niceToHaveSkills: [],
        minYearsExperience: 3,
        seniorityLevel: "MID",
        status: "ACTIVE",
        createdAt: "2026-07-10T12:00:00Z",
        updatedAt: "2026-07-10T12:00:00Z",
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <CreateJobPostingPage />
      </QueryClientProvider>,
    );

    // Populate title and description
    fireEvent.change(screen.getByLabelText(/Job Title/i), {
      target: { value: "Python Engineer" },
    });
    fireEvent.change(screen.getByLabelText(/Job Description/i), {
      target: { value: "Building REST endpoints" },
    });

    // Submit form
    fireEvent.click(
      screen.getByRole("button", { name: /Publish Job Posting/i }),
    );

    // Confirm API call occurs with the correct payload
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        "/job-postings",
        expect.objectContaining({
          title: "Python Engineer",
          description: "Building REST endpoints",
        }),
        expect.any(Object)
      );
    });

    // Confirm navigation to /job-postings list page occurs
    expect(mockPush).toHaveBeenCalledWith("/job-postings");
  });
});
