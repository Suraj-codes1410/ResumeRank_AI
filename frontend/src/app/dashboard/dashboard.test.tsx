import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useRouter } from "next/navigation";
import DashboardPage from "./page";
import { AuthProvider } from "@/context/auth-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

// Mock the API client
vi.mock("@/lib/api-client", () => ({
  apiClient: {
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

describe("DashboardPage Route Protection", () => {
  const mockPush = vi.fn();
  const mockReplace = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useRouter).mockReturnValue({
      push: mockPush,
      replace: mockReplace,
      prefetch: vi.fn(),
    } as unknown as ReturnType<typeof useRouter>);
  });

  it("unauthenticated access redirects to /login when silent refresh fails", async () => {
    // Mock refresh to fail
    vi.mocked(apiClient.post).mockRejectedValue(new Error("Unauthorized"));

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </QueryClientProvider>,
    );

    // Should render the verifying session state first
    expect(screen.getByText(/verifying session/i)).toBeInTheDocument();

    // Verify router replacement to /login occurs
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
    });
  });

  it("valid session reaches the placeholder page showing correct email", async () => {
    // Mock refresh to succeed with a valid mock token containing the email claim
    const mockToken =
      "dummy.eyJlbWFpbCI6ICJ0ZXN0dXNlckBleGFtcGxlLmNvbSJ9.dummy";
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { accessToken: mockToken, emailVerified: true },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </QueryClientProvider>,
    );

    // Wait for the verifying session state to resolve and display the authenticated page
    await waitFor(() => {
      expect(screen.getByText(/you are logged in as/i)).toBeInTheDocument();
    });

    expect(screen.getByText("testuser@example.com")).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
