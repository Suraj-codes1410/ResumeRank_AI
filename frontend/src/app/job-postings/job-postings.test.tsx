import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import JobPostingsPage from "./page";
import { apiClient } from "@/lib/api-client";

// Mock the Auth Context
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
vi.mock("../components/protected-route", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock API Client
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });

describe("JobPostingsPage Listing Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loading state shows skeletons", async () => {
    // Return a promise that remains pending
    vi.mocked(apiClient.get).mockReturnValue(new Promise(() => {}));

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <JobPostingsPage />
      </QueryClientProvider>,
    );

    // Verify skeleton containers are displayed
    expect(screen.getByTestId("loading-state")).toBeInTheDocument();
    expect(screen.queryByTestId("empty-state")).not.toBeInTheDocument();
    expect(screen.queryByTestId("success-state")).not.toBeInTheDocument();
  });

  it("empty state shows the CTA invitation", async () => {
    // Mock empty response
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        items: [],
        page: 0,
        size: 20,
        totalItems: 0,
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <JobPostingsPage />
      </QueryClientProvider>,
    );

    // Wait for resolution and confirm the empty state is displayed
    await waitFor(() => {
      expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    });

    expect(screen.getByText(/Your Recruiting Space/i)).toBeInTheDocument();
    expect(
      screen.getByText(/You haven't posted any job openings yet/i),
    ).toBeInTheDocument();

    // We should have two "Create job posting" buttons (one in header, one in empty state CTA)
    const ctaButtons = screen.getAllByRole("link", {
      name: /Create job posting/i,
    });
    expect(ctaButtons.length).toBe(2);
  });

  it("successful response renders the correct number of cards with the right titles", async () => {
    // Mock successful postings response
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        items: [
          {
            id: "post-1",
            title: "Backend Engineer",
            description: "Responsible for building APIs and backend databases.",
            requiredSkills: ["Java", "SQL"],
            niceToHaveSkills: ["AWS"],
            minYearsExperience: 3,
            seniorityLevel: "MID",
            status: "ACTIVE",
            createdAt: "2026-07-10T12:00:00Z",
            updatedAt: "2026-07-10T12:00:00Z",
          },
          {
            id: "post-2",
            title: "Frontend Architect",
            description: "Leading Next.js and frontend styling direction.",
            requiredSkills: ["React", "TypeScript", "CSS"],
            niceToHaveSkills: ["Figma"],
            minYearsExperience: 8,
            seniorityLevel: "LEAD",
            status: "ACTIVE",
            createdAt: "2026-07-10T12:00:00Z",
            updatedAt: "2026-07-10T12:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalItems: 2,
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <JobPostingsPage />
      </QueryClientProvider>,
    );

    // Wait for resolution and verify successful cards rendering
    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
    expect(screen.getByText("Frontend Architect")).toBeInTheDocument();
    expect(screen.queryByTestId("empty-state")).not.toBeInTheDocument();
  });
});
