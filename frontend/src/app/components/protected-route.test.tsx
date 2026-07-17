import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ProtectedRoute from "./protected-route";

// Mock useRouter
const mockReplace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
  }),
}));

// Mock Auth Context
let mockIsAuthenticated = false;
let mockIsLoading = true;

vi.mock("@/context/auth-context", () => ({
  useAuth: () => ({
    isAuthenticated: mockIsAuthenticated,
    isLoading: mockIsLoading,
  }),
}));

describe("ProtectedRoute", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders loading spinner when auth is loading", () => {
    mockIsAuthenticated = false;
    mockIsLoading = true;

    render(
      <ProtectedRoute>
        <div>Secret Content</div>
      </ProtectedRoute>,
    );

    expect(screen.getByText(/verifying session.../i)).toBeInTheDocument();
    expect(screen.queryByText("Secret Content")).not.toBeInTheDocument();
  });

  it("redirects to /login and returns null when unauthenticated and not loading", async () => {
    mockIsAuthenticated = false;
    mockIsLoading = false;

    const { container } = render(
      <ProtectedRoute>
        <div>Secret Content</div>
      </ProtectedRoute>,
    );

    expect(container.firstChild).toBeNull();
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
    });
  });

  it("renders children when authenticated and loading is complete", () => {
    mockIsAuthenticated = true;
    mockIsLoading = false;

    render(
      <ProtectedRoute>
        <div>Secret Content</div>
      </ProtectedRoute>,
    );

    expect(screen.getByText("Secret Content")).toBeInTheDocument();
    expect(screen.queryByText(/verifying session.../i)).not.toBeInTheDocument();
  });
});
