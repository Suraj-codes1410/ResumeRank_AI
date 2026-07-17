import { describe, it, expect, vi, beforeEach } from "vitest";
import { uploadResumeToCloudinary } from "./cloudinary";
import { apiClient } from "./api-client";
import axios from "axios";

// Mock api-client and axios
vi.mock("./api-client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

vi.mock("axios", () => {
  return {
    default: {
      post: vi.fn(),
    },
  };
});

describe("uploadResumeToCloudinary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("rejects files with invalid MIME types before calling endpoints", async () => {
    const invalidFile = new File(["dummy content"], "test.txt", {
      type: "text/plain",
    });

    await expect(uploadResumeToCloudinary(invalidFile)).rejects.toThrow(
      "Invalid file type. Only PDF and DOCX files are allowed.",
    );

    expect(apiClient.post).not.toHaveBeenCalled();
    expect(axios.post).not.toHaveBeenCalled();
  });

  it("rejects files exceeding 5MB size limit before calling endpoints", async () => {
    const oversizedFile = new File(["dummy content"], "test.pdf", {
      type: "application/pdf",
    });
    Object.defineProperty(oversizedFile, "size", { value: 6 * 1024 * 1024 }); // Mock 6MB

    await expect(uploadResumeToCloudinary(oversizedFile)).rejects.toThrow(
      "File size exceeds the 5MB limit.",
    );

    expect(apiClient.post).not.toHaveBeenCalled();
    expect(axios.post).not.toHaveBeenCalled();
  });

  it("requests signature and uploads valid files successfully", async () => {
    const validFile = new File(["dummy content"], "test.pdf", {
      type: "application/pdf",
    });

    // Mock signature response
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        timestamp: 1234567890,
        signature: "mocked-signature-hash",
        apiKey: "mocked-api-key",
        cloudName: "mocked-cloud-name",
        folder: "resumes/mock-user-123",
      },
    });

    // Mock Cloudinary success response
    vi.mocked(axios.post).mockResolvedValue({
      data: {
        secure_url:
          "https://res.cloudinary.com/mock-cloud/raw/upload/resumes/mock-user-123/test.pdf",
      },
    });

    const resultUrl = await uploadResumeToCloudinary(validFile);

    expect(apiClient.post).toHaveBeenCalledWith("/uploads/signature");
    expect(axios.post).toHaveBeenCalledWith(
      "https://api.cloudinary.com/v1_1/mocked-cloud-name/auto/upload",
      expect.any(FormData),
      expect.objectContaining({
        headers: {
          "Content-Type": "multipart/form-data",
        },
      }),
    );
    expect(resultUrl).toBe(
      "https://res.cloudinary.com/mock-cloud/raw/upload/resumes/mock-user-123/test.pdf",
    );
  });
});
