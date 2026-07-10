import { NextResponse } from 'next/server';
import axios from 'axios';

const BACKEND_URL = process.env.BACKEND_API_URL || 'http://localhost:8081';

export async function POST(request: Request) {
  try {
    // 1. Get refresh token from httpOnly cookie
    const cookieHeader = request.headers.get('cookie') || '';
    // Simple helper to parse cookie
    const getCookie = (name: string) => {
      const match = cookieHeader.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
      return match ? decodeURIComponent(match[2]) : null;
    };
    
    const refreshToken = getCookie('refreshToken');
    if (!refreshToken) {
      return NextResponse.json({ detail: 'No refresh token provided' }, { status: 401 });
    }

    // 2. Call backend /api/auth/refresh
    const response = await axios.post(`${BACKEND_URL}/api/auth/refresh`, { refreshToken }, {
      headers: { 'Content-Type': 'application/json' },
    });

    const { accessToken, refreshToken: newRefreshToken, emailVerified } = response.data;

    // 3. Return response with updated httpOnly cookie
    const nextResponse = NextResponse.json(
      { accessToken, emailVerified },
      { status: 200 }
    );

    nextResponse.cookies.set('refreshToken', newRefreshToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      path: '/',
      maxAge: 604800, // 7 days
    });

    return nextResponse;
  } catch (error: any) {
    const status = error.response?.status || 401;
    const detail = error.response?.data || { detail: 'Refresh failed' };
    
    // Clear cookie on failure
    const nextResponse = NextResponse.json(detail, { status });
    nextResponse.cookies.delete('refreshToken');
    return nextResponse;
  }
}
