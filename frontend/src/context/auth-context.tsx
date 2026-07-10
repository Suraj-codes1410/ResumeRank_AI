'use client';

import React, { createContext, useContext, useState } from 'react';

interface AuthContextType {
  accessToken: string | null;
  isAuthenticated: boolean;
  setAuth: (token: string | null) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);

  const setAuth = (token: string | null) => {
    setAccessToken(token);
  };

  const logout = () => {
    setAccessToken(null);
  };

  return (
    <AuthContext.Provider value={{
      accessToken,
      isAuthenticated: !!accessToken,
      setAuth,
      logout
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
