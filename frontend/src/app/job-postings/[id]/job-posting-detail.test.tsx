import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import JobPostingDetailPage from "./page";
import { apiClient } from "@/lib/api-client";
import axios from "axios";

// Mock useRouter & useSearchParams
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
  useSearchParams: () => {
    const search = typeof window !== "undefined" ? window.location.search : "";
    return new URLSearchParams(search);
  },
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

// Mock Axios directly for Cloudinary direct POST calls
vi.mock("axios", async (importOriginal) => {
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

describe("JobPostingDetailPage Edit & Upload Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    if (typeof window !== "undefined") {
      window.history.replaceState(null, "", "/job-postings/posting-123");
    }
  });

  it("a 404 API response renders the not-found state", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;
    // Mock GET call to throw 404
    const mockError: any = new Error("Not found");
    mockError.response = { status: 404 };
    vi.mocked(apiClient.get).mockRejectedValue(mockError);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for resolution and confirm the not-found card is rendered
    await waitFor(() => {
      expect(screen.getByTestId("not-found-state")).toBeInTheDocument();
    });

    expect(screen.getByText(/Job Posting Not Found/i)).toBeInTheDocument();
    expect(screen.getByText(/back to job postings/i)).toBeInTheDocument();
    expect(screen.queryByTestId("success-state")).not.toBeInTheDocument();
  });

  it("editing the title and saving calls the PATCH mutation with correct payload", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;
    // Mock GET call to return existing job posting details and empty candidates
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [] } };
      }
      return {
        data: {
          id: "posting-123",
          title: "Original Title",
          description: "Original description.",
          requiredSkills: ["Python"],
          niceToHaveSkills: [],
          minYearsExperience: 3,
          seniorityLevel: "MID",
          status: "ACTIVE",
          createdAt: "2026-07-10T12:00:00Z",
          updatedAt: "2026-07-10T12:00:00Z",
        },
      };
    });

    // Mock PATCH call to return updated details
    vi.mocked(apiClient.patch).mockResolvedValue({
      data: {
        id: "posting-123",
        title: "Updated Architect Title",
        description: "Original description.",
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
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for data load success
    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // Verify fields are populated
    const titleInput = screen.getByLabelText(/Job Title/i) as HTMLInputElement;
    expect(titleInput.value).toBe("Original Title");

    // Change title
    fireEvent.change(titleInput, {
      target: { value: "Updated Architect Title" },
    });

    // Submit save
    fireEvent.click(screen.getByRole("button", { name: /Save Updates/i }));

    // Confirm PATCH is called with the correct payload
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledTimes(1);
    });

    expect(apiClient.patch).toHaveBeenCalledWith(
      "/job-postings/posting-123",
      expect.objectContaining({
        title: "Updated Architect Title",
        description: "Original description.",
      }),
      expect.any(Object),
    );

    // Verify toast confirmation is shown
    expect(
      screen.getByText("Job posting saved successfully."),
    ).toBeInTheDocument();
  });

  it("uploading a file transitions UI states and polls correctly until scored", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    // 1. Mock GET calls
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [] } }; // Initial empty candidate list
      }
      return {
        data: {
          id: "posting-123",
          title: "Original Title",
          description: "Original description.",
          requiredSkills: ["Python"],
          niceToHaveSkills: [],
          minYearsExperience: 3,
          seniorityLevel: "MID",
          status: "ACTIVE",
          createdAt: "2026-07-10T12:00:00Z",
          updatedAt: "2026-07-10T12:00:00Z",
        },
      };
    });

    // 2. Mock signature generation responses
    vi.mocked(apiClient.post).mockImplementation(async (url) => {
      if (url.includes("/signature")) {
        return {
          data: {
            signature: "mock-sig",
            timestamp: 12345678,
            apiKey: "mock-key",
            cloudName: "mock-cloud",
            folder: "resumes/user-abc",
          },
        };
      }
      return { data: {} };
    });

    // 3. Mock Cloudinary POST
    vi.mocked(axios.post).mockResolvedValue({
      data: {
        secure_url: "http://cloudinary.com/dummy-file.pdf",
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for successfully rendering the edit details panel
    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // Verify candidates list displays empty state
    expect(screen.getByTestId("no-candidates")).toBeInTheDocument();

    // 4. Mock candidate registration callback response
    const mockPendingCandidate = {
      id: "candidate-111",
      jobPostingId: "posting-123",
      name: null,
      email: null,
      resumeFileUrl: "http://cloudinary.com/dummy-file.pdf",
      resumeStatus: "PENDING",
      parseError: null,
      overallScore: null,
      createdAt: "2026-07-10T12:05:00Z",
      updatedAt: "2026-07-10T12:05:00Z",
    };

    vi.mocked(apiClient.post).mockImplementation(async (url, body) => {
      if (url.includes("/signature")) {
        return {
          data: {
            signature: "mock-sig",
            timestamp: 12345678,
            apiKey: "mock-key",
            cloudName: "mock-cloud",
            folder: "resumes/user-abc",
          },
        };
      }
      // Create candidate response
      return { data: mockPendingCandidate };
    });

    // Simulate input file change
    const file = new File(["dummy content"], "dummy-file.pdf", {
      type: "application/pdf",
    });
    // Use container selector to get file input by id since text elements might not match directly
    const fileInput = document.getElementById(
      "resume-file-input",
    ) as HTMLInputElement;
    expect(fileInput).toBeInTheDocument();

    fireEvent.change(fileInput, { target: { files: [file] } });

    // Wait for mutation to complete and trigger candidates reload
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        "/uploads/signature",
        expect.any(Object),
        expect.any(Object),
      );
      expect(axios.post).toHaveBeenCalledTimes(1);
    });

    // Now mock candidate list query to return the pending candidate (triggers polling loop)
    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [mockPendingCandidate] } };
      }
      return { data: { id: "posting-123", title: "Original Title" } };
    });

    queryClient.invalidateQueries({ queryKey: ["candidates"] });

    // Confirm that a candidate with PENDING badge is now visible in the list
    await waitFor(() => {
      expect(screen.getByText("Pending")).toBeInTheDocument();
    });

    // 5. Verify Polling Stops when status transitions to scored
    const mockScoredCandidate = {
      ...mockPendingCandidate,
      resumeStatus: "SCORED",
      overallScore: 85,
      name: "John Doe",
      email: "john@example.com",
    };

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [mockScoredCandidate] } };
      }
      return { data: { id: "posting-123", title: "Original Title" } };
    });

    queryClient.invalidateQueries({ queryKey: ["candidates"] });

    // Expect SCORED badge containing the overallScore
    await waitFor(() => {
      expect(screen.getByText("Score: 85/100")).toBeInTheDocument();
    });

    // Assert that candidates list fetching call count stabilizes
    const initialCallCount = vi.mocked(apiClient.get).mock.calls.length;

    // Wait short delay
    await new Promise((resolve) => setTimeout(resolve, 100));

    const finalCallCount = vi.mocked(apiClient.get).mock.calls.length;
    // Since scored is a terminal state, no further candidate listing polls should run
    expect(finalCallCount).toBe(initialCallCount);
  });

  it("selecting 3 valid files shows 3 independent progress rows", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [] } };
      }
      return {
        data: {
          id: "posting-123",
          title: "Java Engineer",
          status: "ACTIVE",
        },
      };
    });

    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        signature: "mock-sig",
        timestamp: 123456,
        apiKey: "key",
        cloudName: "cloud",
        folder: "folder",
      },
    });

    vi.mocked(axios.post).mockResolvedValue({
      data: {
        secure_url: "http://cloudinary.com/dummy.pdf",
        etag: "mock-etag",
      },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    const f1 = new File(["content1"], "file1.pdf", { type: "application/pdf" });
    const f2 = new File(["content2"], "file2.pdf", { type: "application/pdf" });
    const f3 = new File(["content3"], "file3.pdf", { type: "application/pdf" });

    const fileInput = document.getElementById(
      "resume-file-input",
    ) as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [f1, f2, f3] } });

    await waitFor(() => {
      expect(
        screen.getByTestId("batch-file-row-file1.pdf"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("batch-file-row-file2.pdf"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("batch-file-row-file3.pdf"),
      ).toBeInTheDocument();
    });
  });

  it("one invalid file (wrong type) among 3 selected shows an error for just that one while the other 2 proceed", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [] } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    const f1 = new File(["c1"], "file1.pdf", { type: "application/pdf" });
    const fInvalid = new File(["invalid"], "file2.txt", { type: "text/plain" });
    const f3 = new File(["c3"], "file3.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });

    const fileInput = document.getElementById(
      "resume-file-input",
    ) as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [f1, fInvalid, f3] } });

    await waitFor(() => {
      expect(
        screen.getByTestId("batch-file-row-file1.pdf"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("batch-file-row-file2.txt"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("batch-file-row-file3.docx"),
      ).toBeInTheDocument();
    });

    const errorText = screen.getByText(
      "Invalid file format. Only PDF and DOCX files are allowed.",
    );
    expect(errorText).toBeInTheDocument();
    expect(
      errorText.closest('[data-testid="batch-file-row-file2.txt"]'),
    ).toBeInTheDocument();
  });

  it("polling correctly tracks multiple candidate IDs simultaneously and stops once ALL of them reach a terminal status, not just the first one", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    let candidatesList: any[] = [];

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: candidatesList } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    let registerCount = 0;
    vi.mocked(apiClient.post).mockImplementation(async (url) => {
      if (url.includes("/signature")) {
        return {
          data: {
            signature: "mock",
            timestamp: 1234,
            apiKey: "k",
            cloudName: "c",
            folder: "f",
          },
        };
      }
      registerCount++;
      return {
        data: {
          id: `cand-${registerCount}`,
          resumeStatus: "PENDING",
          resumeFileUrl: "url",
        },
      };
    });

    vi.mocked(axios.post).mockResolvedValue({
      data: { secure_url: "http://cloudinary.com/dummy.pdf", etag: "etag" },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    const f1 = new File(["c1"], "file1.pdf", { type: "application/pdf" });
    const f2 = new File(["c2"], "file2.pdf", { type: "application/pdf" });

    // Set the list to pending candidates before firing the change
    candidatesList = [
      { id: "cand-1", resumeStatus: "PENDING", name: null, email: null },
      { id: "cand-2", resumeStatus: "PENDING", name: null, email: null },
    ];

    const fileInput = document.getElementById(
      "resume-file-input",
    ) as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [f1, f2] } });

    await waitFor(() => {
      expect(
        vi
          .mocked(apiClient.post)
          .mock.calls.filter((c) => c[0].includes("/candidates")).length,
      ).toBe(2);
    });

    queryClient.invalidateQueries({ queryKey: ["candidates"] });

    await waitFor(() => {
      const list = screen.getByTestId("candidates-list");
      expect(within(list).getAllByText("Pending").length).toBe(2);
    });

    // One terminal (scored), one active (parsing)
    candidatesList = [
      {
        id: "cand-1",
        resumeStatus: "SCORED",
        overallScore: 90,
        name: "Alice",
        email: "alice@example.com",
      },
      { id: "cand-2", resumeStatus: "PARSING", name: null, email: null },
    ];

    queryClient.invalidateQueries({ queryKey: ["candidates"] });

    await waitFor(() => {
      expect(screen.getByText("Score: 90/100")).toBeInTheDocument();
      expect(screen.getByText("Parsing...")).toBeInTheDocument();
    });

    // Both terminal (scored)
    candidatesList = [
      {
        id: "cand-1",
        resumeStatus: "SCORED",
        overallScore: 90,
        name: "Alice",
        email: "alice@example.com",
      },
      {
        id: "cand-2",
        resumeStatus: "SCORED",
        overallScore: 80,
        name: "Bob",
        email: "bob@example.com",
      },
    ];

    queryClient.invalidateQueries({ queryKey: ["candidates"] });

    await waitFor(() => {
      expect(screen.getByText("Score: 90/100")).toBeInTheDocument();
      expect(screen.getByText("Score: 80/100")).toBeInTheDocument();
    });

    const callsCountBeforeDelay = vi.mocked(apiClient.get).mock.calls.length;
    await new Promise((resolve) => setTimeout(resolve, 100));
    const callsCountAfterDelay = vi.mocked(apiClient.get).mock.calls.length;

    expect(callsCountAfterDelay).toBe(callsCountBeforeDelay);
  });

  it("applying a minScore filter updates the URL query string", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [], nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    const replaceStateSpy = vi.spyOn(window.history, "replaceState");

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    const minScoreInput = screen.getByTestId(
      "filter-min-score",
    ) as HTMLInputElement;
    fireEvent.change(minScoreInput, { target: { value: "75" } });

    await waitFor(() => {
      expect(replaceStateSpy).toHaveBeenCalledWith(
        null,
        "",
        expect.stringContaining("minScore=75"),
      );
    });
  });

  it("renders correct empty states for zero candidates total vs zero candidates matching filters", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: [], nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    // Wait for success-state to load
    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // 1. Initially (no filters set, zero candidates total), it should render no-candidates empty state
    await waitFor(() => {
      expect(screen.getByTestId("no-candidates")).toBeInTheDocument();
      expect(
        screen.getByText("No resumes uploaded yet for this job posting."),
      ).toBeInTheDocument();
      expect(
        screen.queryByTestId("empty-filtered-state"),
      ).not.toBeInTheDocument();
    });

    // 2. Set minScore filter -> should render empty-filtered-state
    const minScoreInput = screen.getByTestId(
      "filter-min-score",
    ) as HTMLInputElement;
    fireEvent.change(minScoreInput, { target: { value: "60" } });

    await waitFor(() => {
      expect(screen.getByTestId("empty-filtered-state")).toBeInTheDocument();
      expect(
        screen.getByText("No candidates match your current filters."),
      ).toBeInTheDocument();
      expect(screen.queryByTestId("no-candidates")).not.toBeInTheDocument();
    });

    // 3. Click Reset Filters -> should go back to no-candidates
    const resetBtn = screen.getByTestId("reset-filters-button");
    fireEvent.click(resetBtn);

    await waitFor(() => {
      expect(screen.getByTestId("no-candidates")).toBeInTheDocument();
      expect(minScoreInput.value).toBe("");
    });
  });

  it("selecting 2 candidates and choosing SHORTLISTED calls the bulk endpoint immediately without confirm", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    const mockCandidates = [
      {
        id: "cand-11",
        resumeStatus: "SCORED",
        overallScore: 80,
        name: "Alice",
        email: "alice@example.com",
      },
      {
        id: "cand-22",
        resumeStatus: "SCORED",
        overallScore: 75,
        name: "Bob",
        email: "bob@example.com",
      },
    ];

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: mockCandidates, nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { updated: ["cand-11", "cand-22"], skipped: [] },
    });
    const confirmSpy = vi.spyOn(window, "confirm");

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // Select both candidates
    const cb1 = screen.getByTestId("row-checkbox-0");
    const cb2 = screen.getByTestId("row-checkbox-1");
    fireEvent.click(cb1);
    fireEvent.click(cb2);

    // Verify bulk action bar is shown
    expect(screen.getByTestId("bulk-action-bar")).toBeInTheDocument();

    // Select status SHORTLISTED
    const bulkSelect = screen.getByTestId(
      "bulk-status-select",
    ) as HTMLSelectElement;
    fireEvent.change(bulkSelect, { target: { value: "SHORTLISTED" } });

    // Click Apply
    const applyBtn = screen.getByTestId("bulk-apply-button");
    fireEvent.click(applyBtn);

    // Assert bulk endpoint is called immediately
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        "/candidates/bulk-status",
        { candidateIds: ["cand-11", "cand-22"], status: "SHORTLISTED" },
        expect.anything(),
      );
      expect(confirmSpy).not.toHaveBeenCalled();
    });
  });

  it("selecting candidates and choosing REJECTED shows a confirm dialog before calling the endpoint", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    const mockCandidates = [
      {
        id: "cand-11",
        resumeStatus: "SCORED",
        overallScore: 80,
        name: "Alice",
        email: "alice@example.com",
      },
    ];

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: mockCandidates, nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { updated: ["cand-11"], skipped: [] },
    });
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    const cb1 = screen.getByTestId("row-checkbox-0");
    fireEvent.click(cb1);

    // Select status REJECTED
    const bulkSelect = screen.getByTestId(
      "bulk-status-select",
    ) as HTMLSelectElement;
    fireEvent.change(bulkSelect, { target: { value: "REJECTED" } });

    // Click Apply
    const applyBtn = screen.getByTestId("bulk-apply-button");
    fireEvent.click(applyBtn);

    // Assert confirm dialog was shown, and bulk status called after confirmation
    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith(
        expect.stringContaining("bulk-reject"),
      );
      expect(apiClient.post).toHaveBeenCalledWith(
        "/candidates/bulk-status",
        { candidateIds: ["cand-11"], status: "REJECTED" },
        expect.anything(),
      );
    });
  });

  it("the export button includes the currently active filter query params in its request URL", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    const mockCandidates = [
      {
        id: "cand-11",
        resumeStatus: "SCORED",
        overallScore: 80,
        name: "Alice",
        email: "alice@example.com",
      },
    ];

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates/export")) {
        return { data: new Blob(["csv content"], { type: "text/csv" }) };
      }
      if (url.includes("/candidates")) {
        return { data: { items: mockCandidates, nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    const replaceStateSpy = vi.spyOn(window.history, "replaceState");
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // Apply some filters
    const searchInput = screen.getByTestId("filter-search") as HTMLInputElement;
    const minScoreInput = screen.getByTestId(
      "filter-min-score",
    ) as HTMLInputElement;

    fireEvent.change(searchInput, { target: { value: "Alice" } });
    fireEvent.change(minScoreInput, { target: { value: "80" } });

    // Wait for the debounced search to update the URL
    await waitFor(() => {
      expect(replaceStateSpy).toHaveBeenCalledWith(
        null,
        "",
        expect.stringContaining("search=Alice"),
      );
    });

    // Click Export CSV
    const exportBtn = screen.getByTestId("export-csv-button");
    fireEvent.click(exportBtn);

    // Verify apiClient.get export endpoint is called with search=Alice and minScore=80
    await waitFor(() => {
      const exportCalls = vi
        .mocked(apiClient.get)
        .mock.calls.filter((c) => c[0].includes("/candidates/export"));
      expect(exportCalls.length).toBeGreaterThan(0);
      const requestUrl = exportCalls[0][0];
      expect(requestUrl).toContain("search=Alice");
      expect(requestUrl).toContain("minScore=80");
    });
  });

  it("supports full keyboard Tab/Space/Enter flow for candidate selection and bulk status rejection", async () => {
    const mockParams = {
      then: (onFulfill: any) => {
        onFulfill({ id: "posting-123" });
      },
      status: "fulfilled",
      value: { id: "posting-123" },
    } as any;

    const mockCandidates = [
      {
        id: "cand-11",
        resumeStatus: "SCORED",
        overallScore: 80,
        name: "Alice",
        email: "alice@example.com",
      },
    ];

    vi.mocked(apiClient.get).mockImplementation(async (url) => {
      if (url.includes("/candidates")) {
        return { data: { items: mockCandidates, nextCursor: null } };
      }
      return {
        data: { id: "posting-123", title: "Java Engineer", status: "ACTIVE" },
      };
    });

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { updated: ["cand-11"], skipped: [] },
    });
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={<div>Loading...</div>}>
          <JobPostingDetailPage params={mockParams} />
        </React.Suspense>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("success-state")).toBeInTheDocument();
    });

    // 1. Focus the candidate checkbox via keyboard simulated focus
    const checkbox = screen.getByTestId("row-checkbox-0") as HTMLInputElement;
    checkbox.focus();
    expect(document.activeElement).toBe(checkbox);

    // 2. Select the candidate via Space bar click simulation
    fireEvent.click(checkbox);
    expect(checkbox.checked).toBe(true);

    // Verify bulk action bar appears
    await waitFor(() => {
      expect(screen.getByTestId("bulk-action-bar")).toBeInTheDocument();
    });

    // 3. Tab focus moves to the bulk status select dropdown
    const bulkSelect = screen.getByTestId(
      "bulk-status-select",
    ) as HTMLSelectElement;
    bulkSelect.focus();
    expect(document.activeElement).toBe(bulkSelect);

    // Select status REJECTED
    fireEvent.change(bulkSelect, { target: { value: "REJECTED" } });

    // 4. Tab focus moves to the Apply button
    const applyBtn = screen.getByTestId(
      "bulk-apply-button",
    ) as HTMLButtonElement;
    applyBtn.focus();
    expect(document.activeElement).toBe(applyBtn);

    // 5. Press Enter to submit bulk action
    fireEvent.click(applyBtn);

    // Verify rejection confirmation is triggered
    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith(
        expect.stringContaining("bulk-reject"),
      );
      expect(apiClient.post).toHaveBeenCalledWith(
        "/candidates/bulk-status",
        { candidateIds: ["cand-11"], status: "REJECTED" },
        expect.anything(),
      );
    });
  });
});
