import { z } from "zod";

export const jobPostingFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Title is required")
    .max(250, "Title must not exceed 250 characters"),
  description: z
    .string()
    .trim()
    .min(1, "Description is required")
    .max(10000, "Description must not exceed 10000 characters"),
  requiredSkills: z.array(z.string()),
  niceToHaveSkills: z.array(z.string()),
  minYearsExperience: z.preprocess((val) => {
    if (val === null || val === undefined || val === "") return null;
    if (typeof val === "number" && isNaN(val)) return null;
    if (typeof val === "string") {
      const trimmed = val.trim();
      if (trimmed === "") return null;
      const num = Number(trimmed);
      return isNaN(num) ? null : num;
    }
    return val;
  }, z.number().nullable()),
  seniorityLevel: z
    .enum(["JUNIOR", "MID", "SENIOR", "LEAD"])
    .nullable()
    .or(z.literal(""))
    .transform((val) => (val === "" ? null : val)),
});

export type JobPostingFormData = z.infer<typeof jobPostingFormSchema>;
export type JobPostingFormInput = z.input<typeof jobPostingFormSchema>;
