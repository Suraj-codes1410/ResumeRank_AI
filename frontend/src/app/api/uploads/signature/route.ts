import { NextResponse } from "next/server";
import axios from "axios";

const BACKEND_URL = process.env.BACKEND_API_URL || "http://localhost:8081";

export async function POST(request: Request) {
  try {
    const authHeader =
      request.headers.get("Authorization") ||
      request.headers.get("authorization") ||
      "";

    const response = await axios.post(
      `${BACKEND_URL}/api/uploads/signature`,
      {},
      {
        headers: {
          Authorization: authHeader,
          "Content-Type": "application/json",
        },
      },
    );

    return NextResponse.json(response.data, { status: response.status });
  } catch (error: unknown) {
    const err = error as { response?: { status?: number; data?: unknown } };
    const status = err.response?.status || 500;
    const data = err.response?.data || {
      detail: "Upload signature generation failed",
    };
    return NextResponse.json(data, { status });
  }
}
