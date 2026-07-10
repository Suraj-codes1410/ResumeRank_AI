import { NextResponse } from 'next/server';
import axios from 'axios';

const BACKEND_URL = process.env.BACKEND_API_URL || 'http://localhost:8081';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const response = await axios.post(`${BACKEND_URL}/api/auth/login`, body, {
      headers: { 'Content-Type': 'application/json' },
    });

    const { accessToken, refreshToken, emailVerified } = response.data;

    const nextResponse = NextResponse.json(
      { accessToken, emailVerified },
      { status: 200 }
    );

    nextResponse.cookies.set('refreshToken', refreshToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      path: '/',
      maxAge: 604800, // 7 days
    });

    return nextResponse;
  } catch (error: any) {
    const status = error.response?.status || 500;
    const detail = error.response?.data || { detail: 'Internal Server Error' };
    return NextResponse.json(detail, { status });
  }
}
