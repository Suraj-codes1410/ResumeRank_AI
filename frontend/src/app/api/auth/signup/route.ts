import { NextResponse } from 'next/server';
import axios from 'axios';

const BACKEND_URL = process.env.BACKEND_API_URL || 'http://localhost:8081';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const response = await axios.post(`${BACKEND_URL}/api/auth/signup`, body, {
      headers: { 'Content-Type': 'application/json' },
    });
    return NextResponse.json(response.data, { status: 201 });
  } catch (error: unknown) {
    const err = error as { response?: { status?: number; data?: unknown } };
    const status = err.response?.status || 500;
    const detail = err.response?.data || { detail: 'Internal Server Error' };
    return NextResponse.json(detail, { status });
  }
}
