import { NextResponse } from "next/server";
import axios from "axios";

const BACKEND_URL = process.env.BACKEND_API_URL || "http://localhost:8081";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const authHeader = request.headers.get("Authorization") || "";
    const { id } = await params;

    const response = await axios.get(`${BACKEND_URL}/api/job-postings/${id}`, {
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
    });

    return NextResponse.json(response.data, { status: response.status });
  } catch (error: unknown) {
    const err = error as { response?: { status?: number; data?: unknown } };
    const status = err.response?.status || 500;
    const data = err.response?.data || { detail: "Request failed" };
    return NextResponse.json(data, { status });
  }
}

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const authHeader = request.headers.get("Authorization") || "";
    const { id } = await params;
    const body = await request.json();

    const response = await axios.patch(
      `${BACKEND_URL}/api/job-postings/${id}`,
      body,
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
    const data = err.response?.data || { detail: "Request failed" };
    return NextResponse.json(data, { status });
  }
}
