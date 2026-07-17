"use client";

import React, { useState, useEffect, useMemo, KeyboardEvent, use } from "react";
import {
  useQuery,
  useMutation,
  useQueryClient,
  useInfiniteQuery,
} from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Fraunces, Inter } from "next/font/google";
import Link from "next/link";
import axios from "axios";
import { useAuth } from "@/context/auth-context";
import { apiClient } from "@/lib/api-client";
import ProtectedRoute from "../../components/protected-route";
import { jobPostingFormSchema, JobPostingFormData } from "../schema";

const fraunces = Fraunces({
  subsets: ["latin"],
  display: "swap",
  weight: ["400", "500"],
  variable: "--font-fraunces",
});

const inter = Inter({
  subsets: ["latin"],
  display: "swap",
  weight: ["400", "600"],
  variable: "--font-inter",
});

interface JobPosting {
  id: string;
  title: string;
  description: string;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  minYearsExperience: number | null;
  seniorityLevel: "JUNIOR" | "MID" | "SENIOR" | "LEAD" | null;
  status: "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
}

interface Candidate {
  id: string;
  jobPostingId: string;
  name: string | null;
  email: string | null;
  resumeFileUrl: string;
  resumeStatus: "PENDING" | "PARSING" | "SCORED" | "FAILED";
  parseError: string | null;
  overallScore: number | null;
  createdAt: string;
  updatedAt: string;
}

interface BatchFile {
  id: string;
  name: string;
  size: number;
  status:
    | "validating"
    | "uploading"
    | "pending"
    | "parsing"
    | "scored"
    | "failed"
    | "rejected";
  progress: number;
  error: string | null;
  candidateId: string | null;
}

const runWithConcurrency = async (
  tasks: (() => Promise<void>)[],
  limit: number = 3,
) => {
  const executing: Promise<any>[] = [];
  for (const task of tasks) {
    const p = Promise.resolve().then(() => task());
    if (limit <= tasks.length) {
      const e: Promise<any> = p.then(() =>
        executing.splice(executing.indexOf(e), 1),
      );
      executing.push(e);
      if (executing.length >= limit) {
        await Promise.race(executing);
      }
    }
  }
  return Promise.all(executing);
};

export default function JobPostingDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { accessToken } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [reqSkillInput, setReqSkillInput] = useState("");
  const [niceSkillInput, setNiceSkillInput] = useState("");
  const [showArchiveConfirm, setShowArchiveConfirm] = useState(false);
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  const [batchFiles, setBatchFiles] = useState<BatchFile[]>([]);
  const [activeCandidateIds, setActiveCandidateIds] = useState<string[]>([]);

  useEffect(() => {
    if (!showArchiveConfirm) return;
    const handleKeyDown = (e: any) => {
      if (e.key === "Escape") {
        setShowArchiveConfirm(false);
        return;
      }
      if (e.key === "Tab") {
        const modalElement = document.getElementById("archive-modal-content");
        if (!modalElement) return;
        const focusableElements = modalElement.querySelectorAll(
          'button, [href], input, select, textarea, [tabindex="0"]',
        );
        if (focusableElements.length === 0) return;
        const first = focusableElements[0] as HTMLElement;
        const last = focusableElements[
          focusableElements.length - 1
        ] as HTMLElement;

        if (e.shiftKey) {
          if (document.activeElement === first) {
            last.focus();
            e.preventDefault();
          }
        } else {
          if (document.activeElement === last) {
            first.focus();
            e.preventDefault();
          }
        }
      }
    };

    const previousFocus = document.activeElement as HTMLElement;
    window.addEventListener("keydown", handleKeyDown);

    setTimeout(() => {
      const modalElement = document.getElementById("archive-modal-content");
      const firstFocusable = modalElement?.querySelector(
        "button",
      ) as HTMLElement;
      firstFocusable?.focus();
    }, 50);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      previousFocus?.focus();
    };
  }, [showArchiveConfirm]);

  // Fetch job posting details
  const { data, status, error, refetch } = useQuery<JobPosting>({
    queryKey: ["jobPosting", id, accessToken],
    queryFn: async () => {
      const response = await apiClient.get(`/job-postings/${id}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    enabled: !!accessToken && !!id,
    retry: false,
  });

  const searchParams = useSearchParams();

  const [sort, setSort] = useState(
    () => searchParams?.get("sort") || "score_desc",
  );
  const [minScore, setMinScore] = useState(() => {
    const val = searchParams?.get("minScore");
    return val ? parseInt(val, 10) : "";
  });
  const [skill, setSkill] = useState(() => searchParams?.get("skill") || "");
  const [search, setSearch] = useState(() => searchParams?.get("search") || "");
  const [resumeStatus, setResumeStatus] = useState(
    () => searchParams?.get("resumeStatus") || "",
  );
  const [searchInput, setSearchInput] = useState(
    () => searchParams?.get("search") || "",
  );

  useEffect(() => {
    const handler = setTimeout(() => {
      setSearch(searchInput);
    }, 300);
    return () => clearTimeout(handler);
  }, [searchInput]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (sort && sort !== "score_desc") params.set("sort", sort);
    if (minScore) params.set("minScore", minScore.toString());
    if (skill) params.set("skill", skill);
    if (search) params.set("search", search);
    if (resumeStatus) params.set("resumeStatus", resumeStatus);

    const query = params.toString();
    const newUrl = `${window.location.pathname}${query ? `?${query}` : ""}`;
    window.history.replaceState(null, "", newUrl);
  }, [sort, minScore, skill, search, resumeStatus]);

  const handleResetFilters = () => {
    setSearchInput("");
    setSearch("");
    setSort("score_desc");
    setMinScore("");
    setSkill("");
    setResumeStatus("");
  };

  // Fetch candidates list & poll if at least one is pending or parsing
  const {
    data: candidatesData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch: refetchCandidates,
  } = useInfiniteQuery({
    queryKey: [
      "candidates",
      id,
      accessToken,
      sort,
      minScore,
      skill,
      search,
      resumeStatus,
    ],
    queryFn: async ({ pageParam = "" }) => {
      const params = new URLSearchParams();
      if (sort) params.append("sort", sort);
      if (minScore) params.append("minScore", minScore.toString());
      if (skill) params.append("skill", skill);
      if (search) params.append("search", search);
      if (resumeStatus) params.append("resumeStatus", resumeStatus);
      if (pageParam) params.append("cursor", pageParam as string);
      params.append("limit", "25");

      const response = await apiClient.get(
        `/job-postings/${id}/candidates?${params.toString()}`,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );
      return response.data;
    },
    initialPageParam: "",
    getNextPageParam: (lastPage) => lastPage.nextCursor || undefined,
    enabled: !!accessToken && !!id,
    refetchInterval: (query) => {
      const pages = query.state.data?.pages;
      if (!pages) return false;
      const allCandidates = pages.flatMap((p) => p.items);
      const hasActive = allCandidates.some(
        (c) =>
          c.resumeStatus === "PENDING" ||
          c.resumeStatus === "PARSING" ||
          activeCandidateIds.includes(c.id),
      );
      return hasActive ? 3000 : false;
    },
  });

  const candidates = useMemo(() => {
    return candidatesData
      ? candidatesData.pages.flatMap((page) => page.items)
      : [];
  }, [candidatesData]);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors },
  } = useForm<any>({
    resolver: zodResolver(jobPostingFormSchema),
    defaultValues: {
      title: "",
      description: "",
      requiredSkills: [],
      niceToHaveSkills: [],
      minYearsExperience: "",
      seniorityLevel: "",
    },
  });

  // Pre-populate form once details are fetched
  useEffect(() => {
    if (data) {
      reset({
        title: data.title,
        description: data.description,
        requiredSkills: data.requiredSkills || [],
        niceToHaveSkills: data.niceToHaveSkills || [],
        minYearsExperience: data.minYearsExperience ?? "",
        seniorityLevel: data.seniorityLevel ?? "",
      });
    }
  }, [data, reset]);

  const requiredSkills = watch("requiredSkills") || [];
  const niceToHaveSkills = watch("niceToHaveSkills") || [];

  const updateMutation = useMutation({
    mutationFn: async (payload: Partial<JobPosting>) => {
      const response = await apiClient.patch(`/job-postings/${id}`, payload, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    onSuccess: (updatedData) => {
      queryClient.setQueryData(["jobPosting", id, accessToken], updatedData);
      queryClient.setQueriesData(
        { queryKey: ["jobPostings"] },
        (oldData: any) => {
          if (!oldData) return oldData;
          return {
            ...oldData,
            items: oldData.items.map((item: any) =>
              item.id === id ? updatedData : item,
            ),
          };
        },
      );
      setToastMessage("Job posting saved successfully.");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
    onError: (err: any) => {
      const detail = err.response?.data?.detail;
      if (typeof detail === "string") {
        const fieldErrors = detail.split(", ");
        fieldErrors.forEach((errStr) => {
          const parts = errStr.split(": ");
          if (parts.length >= 2) {
            const field = parts[0].trim();
            const message = parts.slice(1).join(": ").trim();
            if (field === "title" || field === "description") {
              setError(field as "title" | "description", {
                type: "server",
                message,
              });
            }
          }
        });
      } else {
        setError("root", {
          type: "server",
          message: "Failed to update job posting. Please try again.",
        });
      }
    },
  });

  const uploadFile = async (fileObj: BatchFile, nativeFile: File) => {
    setBatchFiles((prev) =>
      prev.map((f) =>
        f.id === fileObj.id ? { ...f, status: "uploading", progress: 0 } : f,
      ),
    );

    try {
      const sigResponse = await apiClient.post(
        "/uploads/signature",
        {},
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );

      const { signature, timestamp, apiKey, cloudName, folder } =
        sigResponse.data;

      const formData = new FormData();
      formData.append("file", nativeFile);
      formData.append("api_key", apiKey);
      formData.append("timestamp", timestamp.toString());
      formData.append("signature", signature);
      formData.append("folder", folder);
      formData.append("allowed_formats", "pdf,docx");

      const uploadResponse = await axios.post(
        `https://api.cloudinary.com/v1_1/${cloudName}/raw/upload`,
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
          },
          onUploadProgress: (progressEvent) => {
            const percent = Math.round(
              (progressEvent.loaded * 100) / (progressEvent.total || 1),
            );
            setBatchFiles((prev) =>
              prev.map((f) =>
                f.id === fileObj.id ? { ...f, progress: percent } : f,
              ),
            );
          },
        },
      );

      const secureUrl = uploadResponse.data.secure_url;
      const rawEtag = uploadResponse.data.etag;
      const cleanEtag = rawEtag ? rawEtag.replace(/"/g, "") : null;

      const candidateResponse = await apiClient.post(
        `/job-postings/${id}/candidates`,
        { resumeFileUrl: secureUrl, resumeHash: cleanEtag },
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );

      const candidateData = candidateResponse.data;

      setBatchFiles((prev) =>
        prev.map((f) =>
          f.id === fileObj.id
            ? {
                ...f,
                status: "pending",
                candidateId: candidateData.id,
              }
            : f,
        ),
      );

      setActiveCandidateIds((prev) => [...prev, candidateData.id]);
      refetchCandidates();
    } catch (err: any) {
      const errMsg =
        err.response?.data?.detail || err.message || "Upload failed";
      setBatchFiles((prev) =>
        prev.map((f) =>
          f.id === fileObj.id
            ? {
                ...f,
                status: "failed",
                error: errMsg,
              }
            : f,
        ),
      );
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const filesList = e.target.files;
    if (!filesList || filesList.length === 0) return;

    const filesArray = Array.from(filesList);

    if (filesArray.length > 20) {
      setToastMessage("Maximum 20 files allowed at once.");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
      return;
    }

    const allowedExtensions = ["pdf", "docx"];
    const maxFileSize = 10 * 1024 * 1024; // 10MB

    const newBatchFiles: BatchFile[] = [];
    const validUploadTasks: (() => Promise<void>)[] = [];

    filesArray.forEach((file, index) => {
      const fileId = `${file.name}-${Date.now()}-${index}`;
      const fileExtension = file.name.split(".").pop()?.toLowerCase();

      let status: BatchFile["status"] = "pending";
      let error: string | null = null;

      if (!fileExtension || !allowedExtensions.includes(fileExtension)) {
        status = "rejected";
        error = "Invalid file format. Only PDF and DOCX files are allowed.";
      } else if (file.size > maxFileSize) {
        status = "rejected";
        error = "File size exceeds limit of 10MB.";
      }

      const batchFileItem: BatchFile = {
        id: fileId,
        name: file.name,
        size: file.size,
        status,
        progress: 0,
        error,
        candidateId: null,
      };

      newBatchFiles.push(batchFileItem);

      if (status === "pending") {
        validUploadTasks.push(async () => {
          await uploadFile(batchFileItem, file);
        });
      }
    });

    setBatchFiles((prev) => [...newBatchFiles, ...prev]);
    runWithConcurrency(validUploadTasks, 3);
  };

  useEffect(() => {
    if (candidates && activeCandidateIds.length > 0) {
      let updatedActiveIds = [...activeCandidateIds];

      setBatchFiles((prevFiles) =>
        prevFiles.map((file) => {
          if (
            file.candidateId &&
            activeCandidateIds.includes(file.candidateId)
          ) {
            const match = candidates.find((c) => c.id === file.candidateId);
            if (match) {
              const status = match.resumeStatus;

              if (status === "SCORED" || status === "FAILED") {
                updatedActiveIds = updatedActiveIds.filter(
                  (id) => id !== file.candidateId,
                );
              }

              return {
                ...file,
                status: status.toLowerCase() as any,
                error:
                  status === "FAILED"
                    ? match.parseError || "Parsing failed"
                    : file.error,
              };
            }
          }
          return file;
        }),
      );

      const hasChanged = updatedActiveIds.length !== activeCandidateIds.length;
      if (hasChanged) {
        setActiveCandidateIds(updatedActiveIds);
      }
    }
  }, [candidates, activeCandidateIds]);

  const retryMutation = useMutation({
    mutationFn: async (resumeFileUrl: string) => {
      const response = await apiClient.post(
        `/job-postings/${id}/candidates`,
        { resumeFileUrl },
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );
      return response.data;
    },
    onSuccess: () => {
      refetchCandidates();
      setToastMessage("Retry submitted successfully.");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
    onError: (err: any) => {
      setToastMessage(
        err.response?.data?.detail || err.message || "Retry failed",
      );
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
  });
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<string[]>(
    [],
  );
  const [bulkStatus, setBulkStatus] = useState("NEW");

  const bulkStatusMutation = useMutation({
    mutationFn: async (payload: { candidateIds: string[]; status: string }) => {
      const response = await apiClient.post(
        "/candidates/bulk-status",
        payload,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );
      return response.data;
    },
    onSuccess: (data: any) => {
      setSelectedCandidateIds([]);
      refetchCandidates();
      const updatedCount = data.updated?.length || 0;
      const skippedCount = data.skipped?.length || 0;
      setToastMessage(`${updatedCount} updated, ${skippedCount} skipped`);
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
    onError: () => {
      setToastMessage("Failed to update candidates status.");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    },
  });

  const handleBulkStatusApply = () => {
    if (bulkStatus === "REJECTED") {
      const confirmed = window.confirm(
        "Are you sure you want to bulk-reject the selected candidates? This action is destructive.",
      );
      if (!confirmed) return;
    }
    bulkStatusMutation.mutate({
      candidateIds: selectedCandidateIds,
      status: bulkStatus,
    });
  };

  const handleExportCsv = async () => {
    try {
      const params = new URLSearchParams();
      if (sort) params.set("sort", sort);
      if (minScore) params.set("minScore", minScore.toString());
      if (skill) params.set("skill", skill);
      if (search) params.set("search", search);
      if (resumeStatus) params.set("resumeStatus", resumeStatus);

      const response = await apiClient.get(
        `/job-postings/${id}/candidates/export?${params.toString()}`,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          responseType: "blob",
        },
      );

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute(
        "download",
        `candidates-export-${id}-${new Date().toISOString().slice(0, 10)}.csv`,
      );
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setToastMessage("Failed to export candidates CSV.");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 4000);
    }
  };

  const onSubmit = (formData: JobPostingFormData) => {
    updateMutation.mutate(formData);
  };

  const handleAddSkill = (
    e: KeyboardEvent<HTMLInputElement>,
    type: "required" | "nice",
  ) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const input = type === "required" ? reqSkillInput : niceSkillInput;
      const setInput =
        type === "required" ? setReqSkillInput : setNiceSkillInput;
      const currentList =
        type === "required" ? requiredSkills : niceToHaveSkills;
      const field = type === "required" ? "requiredSkills" : "niceToHaveSkills";

      const trimmed = input.trim();
      if (trimmed && !currentList.includes(trimmed)) {
        setValue(field, [...currentList, trimmed]);
        setInput("");
      }
    }
  };

  const handleRemoveSkill = (
    skillToRemove: string,
    type: "required" | "nice",
  ) => {
    const currentList = type === "required" ? requiredSkills : niceToHaveSkills;
    const field = type === "required" ? "requiredSkills" : "niceToHaveSkills";
    setValue(
      field,
      currentList.filter((s: string) => s !== skillToRemove),
    );
  };

  const handleToggleStatus = () => {
    if (!data) return;
    const nextStatus = data.status === "ACTIVE" ? "ARCHIVED" : "ACTIVE";
    updateMutation.mutate(
      { status: nextStatus },
      {
        onSuccess: (updated) => {
          queryClient.setQueryData(["jobPosting", id, accessToken], updated);
          setToastMessage(
            `Job posting has been ${nextStatus === "ACTIVE" ? "reactivated" : "archived"}.`,
          );
          setShowToast(true);
          setShowArchiveConfirm(false);
          setTimeout(() => setShowToast(false), 4000);
        },
      },
    );
  };

  const renderStatusBadge = (candidate: Candidate) => {
    switch (candidate.resumeStatus) {
      case "PENDING":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-neutral-800 text-neutral-400 border border-neutral-700">
            Pending
          </span>
        );
      case "PARSING":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-950/40 text-blue-400 border border-blue-900/60 animate-pulse">
            Parsing...
          </span>
        );
      case "SCORED":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-brand-accent-secondary/15 text-brand-accent-secondary border border-brand-accent-secondary/30">
            Score: {candidate.overallScore ?? "N/A"}/100
          </span>
        );
      case "FAILED":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-950/40 text-rose-400 border border-rose-900/60">
            Failed
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-neutral-800 text-neutral-400">
            {candidate.resumeStatus}
          </span>
        );
    }
  };

  const is404 = (error as any)?.response?.status === 404;

  return (
    <ProtectedRoute>
      <div
        className={`min-h-screen bg-brand-bg text-brand-text-primary px-4 py-8 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}
      >
        <div className="max-w-7xl mx-auto space-y-8">
          {/* Toast Notification */}
          {showToast && (
            <div className="fixed bottom-5 right-5 z-50 bg-brand-surface border border-brand-accent-secondary/30 text-brand-accent-secondary px-5 py-3 rounded-lg shadow-xl flex items-center gap-3 animate-slide-in">
              <svg
                className="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M5 13l4 4L19 7"
                />
              </svg>
              <span className="text-sm font-semibold">{toastMessage}</span>
            </div>
          )}

          {/* NOT FOUND STATE */}
          {status === "error" && is404 && (
            <div
              className="flex flex-col items-center justify-center py-20 px-4 bg-brand-surface border border-brand-border rounded-xl text-center space-y-6 max-w-xl mx-auto"
              data-testid="not-found-state"
            >
              <div className="p-4 rounded-full bg-neutral-800/40 border border-brand-border text-brand-text-secondary">
                <svg
                  className="h-8 w-8"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <div className="space-y-2">
                <h3
                  className={`text-2xl font-medium tracking-tight ${fraunces.className}`}
                >
                  Job Posting Not Found
                </h3>
                <p className="text-sm text-brand-text-secondary max-w-md mx-auto">
                  The job posting you are looking for does not exist, or you do
                  not have permission to view/edit it.
                </p>
              </div>
              <Link
                href="/job-postings"
                className="inline-flex justify-center items-center border border-brand-accent px-6 py-2.5 text-sm font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/10 transition-colors"
              >
                Back to Job Postings
              </Link>
            </div>
          )}

          {/* LOADING STATE */}
          {status === "pending" && (
            <div
              className="space-y-6 animate-pulse"
              data-testid="loading-state"
            >
              <div className="h-10 w-1/3 bg-neutral-800 rounded" />
              <div className="h-96 w-full bg-brand-surface border border-brand-border rounded-xl" />
            </div>
          )}

          {/* GENERIC ERROR STATE */}
          {status === "error" && !is404 && (
            <div className="flex flex-col items-center justify-center py-12 px-4 bg-brand-surface border border-brand-border rounded-xl max-w-md mx-auto text-center space-y-4">
              <h3 className={`text-lg font-medium ${fraunces.className}`}>
                Failed to load posting
              </h3>
              <p className="text-sm text-brand-text-secondary">
                We encountered an error loading details. Please check your
                connection.
              </p>
              <button
                onClick={() => refetch()}
                className="border border-brand-accent text-brand-accent px-5 py-2 rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-colors"
              >
                Retry
              </button>
            </div>
          )}

          {/* SUCCESS EDIT FORM */}
          {status === "success" && data && (
            <div className="space-y-8" data-testid="success-state">
              {/* Header */}
              <div className="border-b border-brand-border pb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h1
                      className={`text-3xl font-medium tracking-tight ${fraunces.className}`}
                    >
                      Edit Job Posting
                    </h1>
                    <span
                      className={`text-xs px-2.5 py-0.5 rounded-full font-medium border ${
                        data.status === "ACTIVE"
                          ? "border-brand-accent-secondary/30 text-brand-accent-secondary bg-brand-accent-secondary/5"
                          : "border-brand-border text-brand-text-secondary bg-brand-bg/40"
                      }`}
                    >
                      {data.status}
                    </span>
                  </div>
                  <p className="text-sm text-brand-text-secondary mt-1">
                    Update details or toggle status visibility for your
                    screening pipeline.
                  </p>
                </div>

                {/* Archive / Reactivate Button */}
                <div>
                  {data.status === "ACTIVE" ? (
                    <button
                      type="button"
                      onClick={() => setShowArchiveConfirm(true)}
                      className="inline-flex items-center justify-center border border-rose-500/40 px-5 py-2 text-xs font-semibold rounded-lg text-rose-400 hover:bg-rose-500/10 transition-all"
                    >
                      Archive Posting
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleToggleStatus}
                      className="inline-flex items-center justify-center border border-brand-accent px-5 py-2 text-xs font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/10 transition-all"
                    >
                      Reactivate Posting
                    </button>
                  )}
                </div>
              </div>

              {/* Two-Column Layout */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
                {/* Left Column: Form container */}
                <div className="lg:col-span-7">
                  <form
                    onSubmit={handleSubmit(onSubmit)}
                    className="bg-brand-surface border border-brand-border rounded-xl p-6 sm:p-8 space-y-6 shadow-xl shadow-black/20"
                  >
                    {errors.root && (
                      <div className="p-4 bg-rose-950/20 border border-rose-500/30 text-rose-400 rounded-lg text-sm">
                        {errors.root.message?.toString()}
                      </div>
                    )}

                    {/* Title */}
                    <div className="flex flex-col gap-2">
                      <label
                        htmlFor="title"
                        className="text-sm font-semibold text-brand-text-primary"
                      >
                        Job Title <span className="text-brand-accent">*</span>
                      </label>
                      <input
                        id="title"
                        type="text"
                        {...register("title")}
                        placeholder="e.g. Senior Software Architect"
                        className={`w-full bg-brand-bg border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg ${
                          errors.title
                            ? "border-rose-500/60 focus-visible:ring-rose-500"
                            : "border-brand-border"
                        }`}
                      />
                      {errors.title && (
                        <span className="text-xs text-rose-400 font-medium">
                          {errors.title.message?.toString()}
                        </span>
                      )}
                    </div>

                    {/* Description */}
                    <div className="flex flex-col gap-2">
                      <label
                        htmlFor="description"
                        className="text-sm font-semibold text-brand-text-primary"
                      >
                        Job Description{" "}
                        <span className="text-brand-accent">*</span>
                      </label>
                      <textarea
                        id="description"
                        rows={6}
                        {...register("description")}
                        placeholder="Describe the responsibilities..."
                        className={`w-full bg-brand-bg border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg ${
                          errors.description
                            ? "border-rose-500/60 focus-visible:ring-rose-500"
                            : "border-brand-border"
                        }`}
                      />
                      {errors.description && (
                        <span className="text-xs text-rose-400 font-medium">
                          {errors.description.message?.toString()}
                        </span>
                      )}
                    </div>

                    {/* Split row */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                      {/* Seniority */}
                      <div className="flex flex-col gap-2">
                        <label
                          htmlFor="seniorityLevel"
                          className="text-sm font-semibold text-brand-text-primary"
                        >
                          Seniority Level
                        </label>
                        <select
                          id="seniorityLevel"
                          {...register("seniorityLevel")}
                          className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                        >
                          <option value="">Select Casing...</option>
                          <option value="JUNIOR">Junior</option>
                          <option value="MID">Mid</option>
                          <option value="SENIOR">Senior</option>
                          <option value="LEAD">Lead</option>
                        </select>
                      </div>

                      {/* Years */}
                      <div className="flex flex-col gap-2">
                        <label
                          htmlFor="minYearsExperience"
                          className="text-sm font-semibold text-brand-text-primary"
                        >
                          Minimum Experience (Years)
                        </label>
                        <input
                          id="minYearsExperience"
                          type="number"
                          placeholder="e.g. 5"
                          {...register("minYearsExperience", {
                            valueAsNumber: true,
                          })}
                          className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg"
                        />
                      </div>
                    </div>

                    {/* Required Skills */}
                    <div className="flex flex-col gap-2">
                      <label
                        htmlFor="reqSkillInput"
                        className="text-sm font-semibold text-brand-text-primary"
                      >
                        Required Skills
                      </label>
                      <input
                        id="reqSkillInput"
                        type="text"
                        value={reqSkillInput}
                        onChange={(e) => setReqSkillInput(e.target.value)}
                        onKeyDown={(e) => handleAddSkill(e, "required")}
                        placeholder="Type skill and press Enter"
                        className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg"
                      />
                      {requiredSkills.length > 0 && (
                        <div className="flex flex-wrap gap-2 mt-2">
                          {requiredSkills.map((skill: string) => (
                            <span
                              key={skill}
                              className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-accent-secondary/5 border border-brand-accent-secondary/30 text-brand-accent-secondary rounded-lg text-xs font-semibold"
                            >
                              {skill}
                              <button
                                type="button"
                                onClick={() =>
                                  handleRemoveSkill(skill, "required")
                                }
                                className="hover:text-rose-400 transition-colors focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded"
                              >
                                &times;
                              </button>
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Nice to Have Skills */}
                    <div className="flex flex-col gap-2">
                      <label
                        htmlFor="niceSkillInput"
                        className="text-sm font-semibold text-brand-text-primary"
                      >
                        Nice to Have Skills
                      </label>
                      <input
                        id="niceSkillInput"
                        type="text"
                        value={niceSkillInput}
                        onChange={(e) => setNiceSkillInput(e.target.value)}
                        onKeyDown={(e) => handleAddSkill(e, "nice")}
                        placeholder="Type skill and press Enter"
                        className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg"
                      />
                      {niceToHaveSkills.length > 0 && (
                        <div className="flex flex-wrap gap-2 mt-2">
                          {niceToHaveSkills.map((skill: string) => (
                            <span
                              key={skill}
                              className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-bg/60 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold"
                            >
                              {skill}
                              <button
                                type="button"
                                onClick={() => handleRemoveSkill(skill, "nice")}
                                className="hover:text-rose-400 transition-colors focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded"
                              >
                                &times;
                              </button>
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div className="border-t border-brand-border/40 pt-6 mt-6 flex flex-col-reverse sm:flex-row sm:justify-end gap-4">
                      <Link
                        href="/job-postings"
                        className="h-11 px-5 border border-brand-border text-brand-text-secondary rounded-lg text-sm font-semibold hover:border-brand-text-primary hover:text-brand-text-primary transition-all flex items-center justify-center w-full sm:w-auto focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none"
                      >
                        Back
                      </Link>
                      <button
                        type="submit"
                        disabled={updateMutation.isPending}
                        className="h-11 px-6 border border-brand-accent bg-transparent text-brand-accent rounded-lg text-sm font-semibold hover:bg-brand-accent/10 transition-all disabled:opacity-50 disabled:cursor-not-allowed w-full sm:w-auto flex items-center justify-center focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none"
                      >
                        {updateMutation.isPending
                          ? "Saving..."
                          : "Save Updates"}
                      </button>
                    </div>
                  </form>
                </div>

                {/* Right Column: Upload Panel & Candidates */}
                <div className="lg:col-span-5 space-y-6">
                  {/* Upload Panel */}
                  <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl shadow-black/20 space-y-4">
                    <h3
                      className={`text-xl font-medium tracking-tight text-brand-text-primary ${fraunces.className}`}
                    >
                      Upload Resume
                    </h3>
                    <p className="text-xs text-brand-text-secondary leading-relaxed">
                      Select a candidate's resume (PDF or DOCX format) to
                      generate signature, upload to Cloudinary, and kickstart AI
                      evaluation.
                    </p>

                    <div className="flex flex-col items-center justify-center border-2 border-dashed border-brand-border hover:border-brand-accent/50 rounded-lg p-6 bg-brand-bg/40 cursor-pointer relative group transition-all has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-brand-accent has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:ring-offset-brand-bg">
                      <label htmlFor="resume-file-input" className="sr-only">
                        Choose PDF or DOCX files to upload
                      </label>
                      <input
                        type="file"
                        accept=".pdf,.docx"
                        multiple
                        onChange={handleFileChange}
                        disabled={batchFiles.some(
                          (f) => f.status === "uploading",
                        )}
                        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed focus-visible:outline-none"
                        id="resume-file-input"
                      />
                      <div className="text-center space-y-2">
                        <div className="p-3 bg-neutral-800/40 rounded-full border border-brand-border inline-block text-brand-text-secondary group-hover:text-brand-accent transition-colors">
                          <svg
                            className="h-6 w-6"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={1.5}
                              d="M12 4v16m8-8H4"
                            />
                          </svg>
                        </div>
                        <p className="text-xs font-semibold text-brand-text-primary">
                          {batchFiles.some((f) => f.status === "uploading")
                            ? "Uploading files..."
                            : "Choose PDF or DOCX files"}
                        </p>
                        <p className="text-[10px] text-brand-text-secondary">
                          Maximum file size 10MB
                        </p>
                      </div>
                    </div>

                    {batchFiles.length > 0 && (
                      <div
                        className="space-y-3 mt-4 border-t border-brand-border/40 pt-4"
                        data-testid="upload-batch-list"
                      >
                        <h4 className="text-xs font-semibold text-brand-text-primary">
                          Upload Queue
                        </h4>
                        <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                          {batchFiles.map((file) => (
                            <div
                              key={file.id}
                              className="p-3 bg-brand-bg/40 border border-brand-border rounded-lg space-y-2"
                              data-testid={`batch-file-row-${file.name}`}
                            >
                              <div className="flex items-center justify-between gap-4 text-xs">
                                <span
                                  className="font-medium text-brand-text-primary truncate flex-1 min-w-0"
                                  title={file.name}
                                >
                                  {file.name}
                                </span>
                                <span
                                  className={`font-semibold capitalize text-[10px] px-2 py-0.5 rounded-full ${
                                    file.status === "scored"
                                      ? "bg-brand-accent-secondary/15 text-brand-accent-secondary"
                                      : file.status === "failed" ||
                                          file.status === "rejected"
                                        ? "bg-rose-950/40 text-rose-400"
                                        : file.status === "uploading"
                                          ? "bg-blue-950/40 text-blue-400"
                                          : "bg-neutral-800 text-neutral-400 border border-neutral-700"
                                  }`}
                                >
                                  {file.status === "uploading"
                                    ? `Uploading ${file.progress}%`
                                    : file.status}
                                </span>
                              </div>

                              {file.status === "uploading" && (
                                <div className="w-full bg-brand-bg rounded-full h-1 overflow-hidden">
                                  <div
                                    className="bg-brand-accent h-1 rounded-full transition-all duration-300"
                                    style={{ width: `${file.progress}%` }}
                                  />
                                </div>
                              )}

                              {file.error && (
                                <p
                                  className="text-[10px] text-rose-400 leading-relaxed"
                                  data-testid="batch-file-error"
                                >
                                  {file.error}
                                </p>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Candidates Panel */}
                  <div className="bg-brand-surface border border-brand-border rounded-xl p-6 shadow-xl shadow-black/20 space-y-6">
                    <div className="flex items-center justify-between gap-4">
                      <h3
                        className={`text-xl font-medium tracking-tight text-brand-text-primary ${fraunces.className}`}
                      >
                        Screened Candidates
                      </h3>
                      <button
                        type="button"
                        onClick={handleExportCsv}
                        className="inline-flex items-center gap-1.5 border border-brand-accent px-4 py-1.5 rounded-lg text-xs font-semibold text-brand-accent hover:bg-brand-accent/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg transition-colors"
                        data-testid="export-csv-button"
                      >
                        <svg
                          className="h-4 w-4"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                          />
                        </svg>
                        Export CSV
                      </button>
                    </div>

                    {/* Filter & Sort controls */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-brand-bg/40 p-4 border border-brand-border rounded-xl">
                      <div className="flex flex-col gap-1.5">
                        <label
                          htmlFor="filter-search-input"
                          className="text-[10px] font-bold uppercase tracking-wider text-brand-text-secondary"
                        >
                          Search Name
                        </label>
                        <input
                          id="filter-search-input"
                          type="text"
                          placeholder="Search candidates by name..."
                          value={searchInput}
                          onChange={(e) => setSearchInput(e.target.value)}
                          className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                          data-testid="filter-search"
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <label
                          htmlFor="filter-sort-select"
                          className="text-[10px] font-bold uppercase tracking-wider text-brand-text-secondary"
                        >
                          Sort By
                        </label>
                        <select
                          id="filter-sort-select"
                          value={sort}
                          onChange={(e) => setSort(e.target.value)}
                          className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                          data-testid="filter-sort"
                        >
                          <option value="score_desc">Highest Score</option>
                          <option value="score_asc">Lowest Score</option>
                          <option value="newest">Newest</option>
                          <option value="oldest">Oldest</option>
                        </select>
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <span className="text-[10px] font-bold uppercase tracking-wider text-brand-text-secondary">
                          Skills & Min Score
                        </span>
                        <div className="flex items-center gap-2">
                          <label
                            htmlFor="filter-skill-input"
                            className="sr-only"
                          >
                            Filter by skill
                          </label>
                          <input
                            id="filter-skill-input"
                            type="text"
                            placeholder="Filter by skill..."
                            value={skill}
                            onChange={(e) => setSkill(e.target.value)}
                            className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                            data-testid="filter-skill"
                          />
                          <label
                            htmlFor="filter-min-score-input"
                            className="sr-only"
                          >
                            Minimum score
                          </label>
                          <input
                            id="filter-min-score-input"
                            type="number"
                            min="0"
                            max="100"
                            placeholder="Min score (0-100)"
                            value={minScore}
                            onChange={(e) => {
                              const val = e.target.value;
                              setMinScore(
                                val === ""
                                  ? ""
                                  : Math.min(
                                      100,
                                      Math.max(0, parseInt(val, 10)),
                                    ),
                              );
                            }}
                            className="w-28 bg-brand-bg border border-brand-border rounded-lg px-2 py-2 text-xs text-center transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                            data-testid="filter-min-score"
                          />
                        </div>
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <label
                          htmlFor="filter-status-select"
                          className="text-[10px] font-bold uppercase tracking-wider text-brand-text-secondary"
                        >
                          Resume Status
                        </label>
                        <select
                          id="filter-status-select"
                          value={resumeStatus}
                          onChange={(e) => setResumeStatus(e.target.value)}
                          className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary"
                          data-testid="filter-resume-status"
                        >
                          <option value="">All Resume Statuses</option>
                          <option value="PENDING">Pending</option>
                          <option value="PARSING">Parsing</option>
                          <option value="SCORED">Scored</option>
                          <option value="FAILED">Failed</option>
                        </select>
                      </div>
                    </div>

                    {/* Bulk Action Bar */}
                    {selectedCandidateIds.length > 0 && (
                      <div
                        className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-brand-accent/5 border border-brand-accent/30 p-4 rounded-xl"
                        data-testid="bulk-action-bar"
                      >
                        <div className="text-xs font-semibold text-brand-text-primary">
                          {selectedCandidateIds.length} candidate
                          {selectedCandidateIds.length === 1 ? "" : "s"}{" "}
                          selected
                        </div>
                        <div className="flex flex-col sm:flex-row items-center gap-2 w-full sm:w-auto">
                          <label
                            htmlFor="bulk-status-select"
                            className="sr-only"
                          >
                            Bulk change status
                          </label>
                          <select
                            id="bulk-status-select"
                            value={bulkStatus}
                            onChange={(e) => setBulkStatus(e.target.value)}
                            className="h-11 px-3 bg-brand-bg border border-brand-border rounded-lg text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg text-brand-text-primary w-full sm:w-40"
                            data-testid="bulk-status-select"
                          >
                            <option value="NEW">New</option>
                            <option value="REVIEWING">Reviewing</option>
                            <option value="SHORTLISTED">Shortlisted</option>
                            <option value="REJECTED">Rejected</option>
                          </select>
                          <button
                            type="button"
                            onClick={handleBulkStatusApply}
                            disabled={bulkStatusMutation.isPending}
                            className="h-11 px-6 bg-brand-accent border border-brand-accent text-brand-bg rounded-lg text-xs font-bold hover:bg-brand-accent/80 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg disabled:opacity-50 w-full sm:w-auto flex items-center justify-center"
                            data-testid="bulk-apply-button"
                          >
                            {bulkStatusMutation.isPending
                              ? "Applying..."
                              : "Apply"}
                          </button>
                        </div>
                      </div>
                    )}

                    {candidates && candidates.length > 0 ? (
                      <div className="space-y-4">
                        {/* Select All checkbox header */}
                        <div className="flex items-center justify-between bg-neutral-800/10 p-2.5 rounded-lg border border-brand-border/40 text-xs">
                          <label className="flex items-center gap-2 cursor-pointer text-brand-text-secondary">
                            <input
                              type="checkbox"
                              checked={
                                candidates.length > 0 &&
                                candidates.every((c) =>
                                  selectedCandidateIds.includes(c.id),
                                )
                              }
                              onChange={(e) => {
                                if (e.target.checked) {
                                  const currentIds = candidates.map(
                                    (c) => c.id,
                                  );
                                  setSelectedCandidateIds((prev) =>
                                    Array.from(
                                      new Set([...prev, ...currentIds]),
                                    ),
                                  );
                                } else {
                                  const currentIds = candidates.map(
                                    (c) => c.id,
                                  );
                                  setSelectedCandidateIds((prev) =>
                                    prev.filter(
                                      (id) => !currentIds.includes(id),
                                    ),
                                  );
                                }
                              }}
                              className="rounded border-neutral-700 bg-neutral-900 text-brand-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg h-4 w-4"
                              data-testid="select-all-checkbox"
                            />
                            <span>Select all on currently loaded page</span>
                          </label>
                          <span className="text-[10px] text-brand-text-secondary italic">
                            * Note: Cursor pagination is active. This selects
                            loaded candidates only.
                          </span>
                        </div>

                        <div
                          className="divide-y divide-brand-border/40 space-y-4"
                          data-testid="candidates-list"
                        >
                          {candidates.map(
                            (candidate: Candidate, index: number) => {
                              const fileName = candidate.resumeFileUrl
                                ? decodeURIComponent(
                                    candidate.resumeFileUrl.split("/").pop() ||
                                      "Resume",
                                  )
                                : "Resume";

                              let scoreColorClass =
                                "text-neutral-400 border-neutral-700 bg-neutral-800/40";
                              if (
                                candidate.overallScore !== null &&
                                candidate.overallScore !== undefined
                              ) {
                                if (candidate.overallScore >= 70) {
                                  scoreColorClass =
                                    "text-emerald-400 border-emerald-500/30 bg-emerald-950/20";
                                } else if (candidate.overallScore >= 40) {
                                  scoreColorClass =
                                    "text-amber-400 border-amber-500/30 bg-amber-950/20";
                                } else {
                                  scoreColorClass =
                                    "text-rose-400 border-rose-500/30 bg-rose-950/20";
                                }
                              }

                              return (
                                <div
                                  key={candidate.id}
                                  onClick={() =>
                                    router.push(`/candidates/${candidate.id}`)
                                  }
                                  className="pt-4 first:pt-0 space-y-3 cursor-pointer hover:bg-neutral-800/10 p-2 rounded-lg transition-colors flex items-start gap-3"
                                  data-testid={`candidate-row-${index}`}
                                >
                                  {/* Row Checkbox */}
                                  <div
                                    className="pt-1"
                                    onClick={(e) => e.stopPropagation()}
                                  >
                                    <input
                                      type="checkbox"
                                      checked={selectedCandidateIds.includes(
                                        candidate.id,
                                      )}
                                      onChange={(e) => {
                                        if (e.target.checked) {
                                          setSelectedCandidateIds((prev) => [
                                            ...prev,
                                            candidate.id,
                                          ]);
                                        } else {
                                          setSelectedCandidateIds((prev) =>
                                            prev.filter(
                                              (id) => id !== candidate.id,
                                            ),
                                          );
                                        }
                                      }}
                                      className="rounded border-neutral-700 bg-neutral-900 text-brand-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg h-4 w-4"
                                      data-testid={`row-checkbox-${index}`}
                                    />
                                  </div>

                                  <div className="flex-1 min-w-0 space-y-3">
                                    <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2 sm:gap-4">
                                      <div className="space-y-1 min-w-0">
                                        <a
                                          href={candidate.resumeFileUrl}
                                          target="_blank"
                                          rel="noopener noreferrer"
                                          onClick={(e) => e.stopPropagation()}
                                          className="text-sm font-semibold text-brand-accent hover:underline focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded block truncate"
                                        >
                                          {fileName}
                                        </a>
                                        <div
                                          onClick={(e) => e.stopPropagation()}
                                        >
                                          <Link
                                            href={`/candidates/${candidate.id}`}
                                            className="text-xs text-brand-text-primary font-semibold hover:underline focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded block mt-0.5"
                                          >
                                            {candidate.name ||
                                              "Name: Extraction pending"}
                                          </Link>
                                        </div>
                                        <p className="text-xs text-brand-text-secondary truncate">
                                          {candidate.email ||
                                            "Email: Extraction pending"}
                                        </p>
                                      </div>
                                      <div className="flex items-center gap-2 flex-shrink-0">
                                        {candidate.overallScore !== null &&
                                        candidate.overallScore !== undefined ? (
                                          <span
                                            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold border ${scoreColorClass}`}
                                          >
                                            Score: {candidate.overallScore}/100
                                          </span>
                                        ) : (
                                          renderStatusBadge(candidate)
                                        )}
                                      </div>
                                    </div>

                                    {candidate.resumeStatus === "FAILED" && (
                                      <div
                                        className="space-y-2"
                                        onClick={(e) => e.stopPropagation()}
                                      >
                                        <p
                                          className="text-xs text-rose-400 leading-relaxed bg-rose-950/15 border border-rose-500/20 rounded p-2"
                                          data-testid="parse-error"
                                        >
                                          Error:{" "}
                                          {candidate.parseError ||
                                            "Unknown parsing failure"}
                                        </p>
                                        <div className="flex items-center gap-2">
                                          <button
                                            type="button"
                                            onClick={() =>
                                              retryMutation.mutate(
                                                candidate.resumeFileUrl,
                                              )
                                            }
                                            disabled={retryMutation.isPending}
                                            className="inline-flex justify-center items-center border border-brand-accent/50 px-3 py-1.5 text-xs font-semibold rounded-lg text-brand-accent hover:bg-brand-accent/15 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg disabled:opacity-50"
                                            data-testid="retry-button"
                                          >
                                            {retryMutation.isPending
                                              ? "Retrying..."
                                              : "Retry"}
                                          </button>
                                          <span className="text-[10px] text-brand-text-secondary italic">
                                            * Creates new screening record
                                          </span>
                                        </div>
                                      </div>
                                    )}
                                  </div>
                                </div>
                              );
                            },
                          )}
                        </div>
                        {hasNextPage && (
                          <div className="pt-4 flex justify-center border-t border-brand-border/40">
                            <button
                              type="button"
                              onClick={() => fetchNextPage()}
                              disabled={isFetchingNextPage}
                              className="border border-brand-accent bg-transparent text-brand-accent px-5 py-2 rounded-lg text-xs font-semibold hover:bg-brand-accent/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                              data-testid="load-more-button"
                            >
                              {isFetchingNextPage
                                ? "Loading more..."
                                : "Load More Candidates"}
                            </button>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="text-center py-10 px-4 bg-brand-bg/20 border border-brand-border rounded-xl space-y-4">
                        {!!minScore || !!skill || !!search || !!resumeStatus ? (
                          <div
                            className="space-y-3"
                            data-testid="empty-filtered-state"
                          >
                            <p className="text-sm text-brand-text-secondary">
                              No candidates match your current filters.
                            </p>
                            <button
                              type="button"
                              onClick={handleResetFilters}
                              className="border border-brand-accent text-brand-accent px-4 py-2 rounded-lg text-xs font-semibold hover:bg-brand-accent/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg transition-all"
                              data-testid="reset-filters-button"
                            >
                              Reset Filters
                            </button>
                          </div>
                        ) : (
                          <div
                            className="space-y-2"
                            data-testid="no-candidates"
                          >
                            <p className="text-xs text-brand-text-secondary">
                              No resumes uploaded yet for this job posting.
                            </p>
                            <p className="text-[10px] text-brand-text-secondary italic">
                              Drag and drop files in the panel above to get
                              started.
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ARCHIVE CONFIRMATION MODAL */}
          {showArchiveConfirm && (
            <div
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fade-in"
              data-testid="archive-modal"
              role="dialog"
              aria-modal="true"
              aria-labelledby="archive-modal-title"
            >
              <div
                id="archive-modal-content"
                className="bg-brand-surface border border-brand-border rounded-xl max-w-md w-full p-6 space-y-6 shadow-2xl animate-zoom-in"
              >
                <div className="space-y-2">
                  <h3
                    id="archive-modal-title"
                    className={`text-xl font-medium tracking-tight text-brand-text-primary ${fraunces.className}`}
                  >
                    Archive Job Posting?
                  </h3>
                  <p className="text-sm text-brand-text-secondary leading-relaxed">
                    Archiving this job posting will hide it from the active
                    screen lists and dashboard workflows. Candidates mapped to
                    this post will no longer be visible.
                  </p>
                </div>
                <div className="flex justify-end gap-4">
                  <button
                    type="button"
                    onClick={() => setShowArchiveConfirm(false)}
                    className="px-4 py-2 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold hover:border-brand-text-primary hover:text-brand-text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg transition-colors"
                  >
                    No, Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleToggleStatus}
                    className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg transition-colors"
                  >
                    Yes, Archive
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
