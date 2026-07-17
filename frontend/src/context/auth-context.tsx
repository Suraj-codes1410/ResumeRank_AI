"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { apiClient } from "@/lib/api-client";

interface AuthContextType {
  accessToken: string | null;
  email: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setAuth: (token: string | null) => void;
  logout: () => void;
  refreshSession: () => Promise<boolean>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const decodeJwt = (token: string) => {
  try {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const setAuth = (token: string | null) => {
    setAccessToken(token);
    if (token) {
      const decoded = decodeJwt(token);
      setEmail(decoded?.email || null);
    } else {
      setEmail(null);
    }
  };

  const refreshSession = async (): Promise<boolean> => {
    try {
      const response = await apiClient.post("/auth/refresh");
      const token = response.data.accessToken;
      setAuth(token);
      return true;
    } catch (error) {
      setAuth(null);
      return false;
    }
  };

  const logout = () => {
    setAuth(null);
  };

  useEffect(() => {
    const initSession = async () => {
      await refreshSession();
      setIsLoading(false);
    };
    initSession();
  }, []);

  return (
    <AuthContext.Provider
      value={{
        accessToken,
        email,
        isAuthenticated: !!accessToken,
        isLoading,
        setAuth,
        logout,
        refreshSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
