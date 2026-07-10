import { NextResponse } from 'next/server';
import axios from 'axios';

const BACKEND_URL = process.env.BACKEND_API_URL || 'http://localhost:8081';

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get('Authorization') || '';
    const { searchParams } = new URL(request.url);
    const queryString = searchParams.toString();

    const response = await axios.get(`${BACKEND_URL}/api/job-postings${queryString ? '?' + queryString : ''}`, {
      headers: {
        'Authorization': authHeader,
        'Content-Type': 'application/json',
      },
    });

    return NextResponse.json(response.data, { status: response.status });
  } catch (error: any) {
    const status = error.response?.status || 500;
    const data = error.response?.data || { detail: 'Request failed' };
    return NextResponse.json(data, { status });
  }
}

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get('Authorization') || '';
    const body = await request.json();

    const response = await axios.post(`${BACKEND_URL}/api/job-postings`, body, {
      headers: {
        'Authorization': authHeader,
        'Content-Type': 'application/json',
      },
    });

    return NextResponse.json(response.data, { status: response.status });
  } catch (error: any) {
    const status = error.response?.status || 500;
    const data = error.response?.data || { detail: 'Request failed' };
    return NextResponse.json(data, { status });
  }
}
