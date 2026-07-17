"use client";

import React, { useState, KeyboardEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { jobPostingFormSchema, JobPostingFormData } from "../schema";
import { Fraunces, Inter } from "next/font/google";
import { useAuth } from "@/context/auth-context";
import { apiClient } from "@/lib/api-client";
import ProtectedRoute from "../../components/protected-route";

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

export default function CreateJobPostingPage() {
  const { accessToken } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [reqSkillInput, setReqSkillInput] = useState("");
  const [niceSkillInput, setNiceSkillInput] = useState("");

  const {
    register,
    handleSubmit,
    control,
    setValue,
    watch,
    setError,
    formState: { errors },
  } = useForm<JobPostingFormData>({
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

  const requiredSkills = watch("requiredSkills") || [];
  const niceToHaveSkills = watch("niceToHaveSkills") || [];

  const createMutation = useMutation({
    mutationFn: async (data: JobPostingFormData) => {
      const response = await apiClient.post("/job-postings", data, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      return response.data;
    },
    onSuccess: (newPosting) => {
      // Optimistically inject the new posting into cache to show up immediately
      queryClient.setQueriesData<{ items: unknown[]; totalItems: number }>(
        { queryKey: ["jobPostings"] },
        (oldData) => {
          if (!oldData) return oldData;
          return {
            ...oldData,
            items: [newPosting, ...oldData.items],
            totalItems: oldData.totalItems + 1,
          };
        },
      );
      router.push("/job-postings");
    },
    onError: (error: unknown) => {
      const detail = (error as { response?: { data?: { detail?: string } } })
        .response?.data?.detail;
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
        // Fallback for general server errors
        setError("root", {
          type: "server",
          message: "Failed to create job posting. Please try again.",
        });
      }
    },
  });

  const onSubmit = (data: JobPostingFormData) => {
    createMutation.mutate(data);
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

  return (
    <ProtectedRoute>
      <div
        className={`min-h-screen bg-brand-bg text-brand-text-primary px-4 py-8 sm:px-6 lg:px-8 font-sans ${inter.variable} ${fraunces.variable}`}
      >
        <div className="max-w-3xl mx-auto space-y-8">
          {/* Header */}
          <div className="border-b border-brand-border pb-6 flex items-center justify-between">
            <div>
              <h1
                className={`text-3xl font-medium tracking-tight ${fraunces.className}`}
              >
                Create Job Posting
              </h1>
              <p className="text-sm text-brand-text-secondary mt-1">
                Fill in the details to publish a new position for resume
                screening.
              </p>
            </div>
            <button
              onClick={() => router.push("/job-postings")}
              className="text-xs font-semibold text-brand-text-secondary hover:text-brand-text-primary transition-colors"
            >
              Cancel
            </button>
          </div>

          {/* Form Card */}
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
                Job Description <span className="text-brand-accent">*</span>
              </label>
              <textarea
                id="description"
                rows={6}
                {...register("description")}
                placeholder="Describe the responsibilities, project scope, and daily requirements..."
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

            {/* Split row: Seniority Level & Min Experience */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              {/* Seniority Level */}
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

              {/* Min Experience */}
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
                  {...register("minYearsExperience", { valueAsNumber: true })}
                  className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg"
                />
              </div>
            </div>

            {/* Required Skills (Tag Input) */}
            <div className="flex flex-col gap-2">
              <label
                htmlFor="reqSkillInput"
                className="text-sm font-semibold text-brand-text-primary"
              >
                Required Skills
              </label>
              <div className="text-xs text-brand-text-secondary mb-1">
                Type a skill and press{" "}
                <kbd className="bg-neutral-800 px-1 py-0.5 rounded text-neutral-400">
                  Enter
                </kbd>{" "}
                to add it.
              </div>
              <input
                id="reqSkillInput"
                type="text"
                value={reqSkillInput}
                onChange={(e) => setReqSkillInput(e.target.value)}
                onKeyDown={(e) => handleAddSkill(e, "required")}
                placeholder="e.g. React, Python"
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
                        onClick={() => handleRemoveSkill(skill, "required")}
                        className="hover:text-rose-400 transition-colors"
                        aria-label={`Remove required skill ${skill}`}
                      >
                        &times;
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Nice to Have Skills (Tag Input) */}
            <div className="flex flex-col gap-2">
              <label
                htmlFor="niceSkillInput"
                className="text-sm font-semibold text-brand-text-primary"
              >
                Nice to Have Skills
              </label>
              <div className="text-xs text-brand-text-secondary mb-1">
                Type a skill and press{" "}
                <kbd className="bg-neutral-800 px-1 py-0.5 rounded text-neutral-400">
                  Enter
                </kbd>{" "}
                to add it.
              </div>
              <input
                id="niceSkillInput"
                type="text"
                value={niceSkillInput}
                onChange={(e) => setNiceSkillInput(e.target.value)}
                onKeyDown={(e) => handleAddSkill(e, "nice")}
                placeholder="e.g. Kubernetes, Figma"
                className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg"
              />
              {niceToHaveSkills.length > 0 && (
                <div className="flex flex-wrap gap-2 mt-2">
                  {niceToHaveSkills.map((skill: string) => (
                    <span
                      key={skill}
                      className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-bg/60 border border-brand-border text-brand-text-secondary rounded-lg text-xs font-semibold font-sans"
                    >
                      {skill}
                      <button
                        type="button"
                        onClick={() => handleRemoveSkill(skill, "nice")}
                        className="hover:text-rose-400 transition-colors focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none rounded"
                        aria-label={`Remove nice-to-have skill ${skill}`}
                      >
                        &times;
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Action Buttons */}
            <div className="border-t border-brand-border/40 pt-6 mt-6 flex flex-col-reverse sm:flex-row sm:justify-end gap-4">
              <button
                type="button"
                onClick={() => router.push("/job-postings")}
                className="h-11 px-5 border border-brand-border text-brand-text-secondary rounded-lg text-sm font-semibold hover:border-brand-text-primary hover:text-brand-text-primary focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none transition-all flex items-center justify-center w-full sm:w-auto"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="h-11 px-6 border border-brand-accent bg-transparent text-brand-accent rounded-lg text-sm font-semibold hover:bg-brand-accent/10 focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center w-full sm:w-auto"
              >
                {createMutation.isPending
                  ? "Publishing..."
                  : "Publish Job Posting"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </ProtectedRoute>
  );
}
