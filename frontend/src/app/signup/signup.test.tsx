import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import SignupPage from './page';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

// Mock the API client
vi.mock('@/lib/api-client', () => ({
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

describe('SignupPage', () => {
  it('renders required fields', () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <SignupPage />
      </QueryClientProvider>
    );

    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign up/i })).toBeInTheDocument();
  });

  it('shows validation error on invalid email', async () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <SignupPage />
      </QueryClientProvider>
    );

    const emailInput = screen.getByLabelText(/email address/i);
    const submitButton = screen.getByRole('button', { name: /sign up/i });

    // Enter invalid email
    fireEvent.change(emailInput, { target: { value: 'invalid-email' } });
    fireEvent.click(submitButton);

    expect(await screen.findByText(/invalid email format/i)).toBeInTheDocument();
  });

  it('disables submit while pending', async () => {
    const queryClient = createTestQueryClient();
    
    // Make post mock hang so we can test the pending state
    let resolvePost: any;
    const promise = new Promise((resolve) => {
      resolvePost = resolve;
    });
    vi.mocked(apiClient.post).mockReturnValue(promise);

    render(
      <QueryClientProvider client={queryClient}>
        <SignupPage />
      </QueryClientProvider>
    );

    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitButton = screen.getByRole('button', { name: /sign up/i });

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } });
    fireEvent.change(passwordInput, { target: { value: 'password123' } });
    fireEvent.click(submitButton);

    // Verify submit button is disabled
    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    });

    // Resolve post to avoid leak and wait for form to settle
    await waitFor(async () => {
      resolvePost({ data: {} });
    });
    
    await waitFor(() => {
      expect(submitButton).not.toBeDisabled();
    });
  });
});
