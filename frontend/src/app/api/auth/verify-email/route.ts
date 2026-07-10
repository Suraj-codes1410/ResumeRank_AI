import { NextResponse } from 'next/server';
import axios from 'axios';

const BACKEND_URL = process.env.BACKEND_API_URL || 'http://localhost:8081';

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const token = searchParams.get('token') || '';
    await axios.get(`${BACKEND_URL}/api/auth/verify-email?token=${token}`);
    return new NextResponse(null, { status: 200 });
  } catch (error: any) {
    const status = error.response?.status || 500;
    const detail = error.response?.data || { detail: 'Internal Server Error' };
    return NextResponse.json(detail, { status });
  }
}
