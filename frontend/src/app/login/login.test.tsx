import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import LoginPage from "./page";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

// Mock useRouter
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Auth Context
const mockSetAuth = vi.fn();
vi.mock("@/context/auth-context", () => ({
  useAuth: () => ({
    setAuth: mockSetAuth,
  }),
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
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders required fields", () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <LoginPage />
      </QueryClientProvider>,
    );

    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /sign in/i }),
    ).toBeInTheDocument();
  });

  it("shows validation error on invalid email", async () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <LoginPage />
      </QueryClientProvider>,
    );

    const emailInput = screen.getByLabelText(/email address/i);
    const submitButton = screen.getByRole("button", { name: /sign in/i });

    // Enter invalid email
    fireEvent.change(emailInput, { target: { value: "invalid-email" } });
    fireEvent.click(submitButton);

    expect(
      await screen.findByText(/invalid email format/i),
    ).toBeInTheDocument();
  });

  it("successful login stores token and redirects to /job-postings", async () => {
    const queryClient = createTestQueryClient();

    // Mock the post request to return token
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        accessToken: "mocked-jwt-token-123",
        emailVerified: true,
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <LoginPage />
      </QueryClientProvider>,
    );

    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitButton = screen.getByRole("button", { name: /sign in/i });

    fireEvent.change(emailInput, {
      target: { value: "recruiter@example.com" },
    });
    fireEvent.change(passwordInput, { target: { value: "password123" } });
    fireEvent.click(submitButton);

    // Verify token storage and routing redirect
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith("/auth/login", {
        email: "recruiter@example.com",
        password: "password123",
      });
      expect(mockSetAuth).toHaveBeenCalledWith("mocked-jwt-token-123");
      expect(mockPush).toHaveBeenCalledWith("/job-postings");
    });
  });
});
