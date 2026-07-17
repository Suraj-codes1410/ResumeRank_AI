import { apiClient } from "./api-client";
import axios from "axios";

interface SignatureResponse {
  timestamp: number;
  signature: string;
  apiKey: string;
  cloudName: string;
  folder: string;
}

const ALLOWED_MIME_TYPES = [
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
];
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

export async function uploadResumeToCloudinary(file: File): Promise<string> {
  // 1. Client-side Validation (BEFORE requesting signature)
  if (!ALLOWED_MIME_TYPES.includes(file.type)) {
    throw new Error("Invalid file type. Only PDF and DOCX files are allowed.");
  }

  if (file.size > MAX_FILE_SIZE) {
    throw new Error("File size exceeds the 5MB limit.");
  }

  // 2. Fetch Upload Signature from BFF Proxy
  let signatureData: SignatureResponse;
  try {
    const response =
      await apiClient.post<SignatureResponse>("/uploads/signature");
    signatureData = response.data;
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string; detail?: string } }; message?: string };
    const errorMessage =
      err.response?.data?.message ||
      err.response?.data?.detail ||
      err.message ||
      "Failed to get upload signature";
    throw new Error(`Authentication signature failed: ${errorMessage}`);
  }

  const { timestamp, signature, apiKey, cloudName, folder } = signatureData;

  // 3. Upload Directly to Cloudinary using multipart/form-data
  const formData = new FormData();
  formData.append("file", file);
  formData.append("api_key", apiKey);
  formData.append("timestamp", timestamp.toString());
  formData.append("signature", signature);
  formData.append("folder", folder);
  formData.append("allowed_formats", "pdf,docx");

  try {
    const uploadUrl = `https://api.cloudinary.com/v1_1/${cloudName}/auto/upload`;
    const response = await axios.post(uploadUrl, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });

    if (response.data && response.data.secure_url) {
      return response.data.secure_url;
    } else {
      throw new Error("Cloudinary response did not contain secure_url");
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { error?: { message?: string } } }; message?: string };
    const errorMessage =
      err.response?.data?.error?.message ||
      err.message ||
      "Failed to upload file to Cloudinary";
    throw new Error(`Cloudinary upload failed: ${errorMessage}`);
  }
}
