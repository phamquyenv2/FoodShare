import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import type { User } from '../types';
import { apiFetch, clearAccessToken, setAccessToken } from '../services/api';
import { unregisterBrowserDevice } from '../services/deviceService';

interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: Partial<User> | null;
}

interface AuthContextValue extends AuthState {
  login: (token: string, user: Partial<User>) => void;
  logout: () => void;
  checkAuth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    isLoading: true,
    user: null,
  });

  const checkAuth = async () => {
    try {
      const user = await apiFetch<Partial<User>>('/users/me');
      setState({ isAuthenticated: true, isLoading: false, user });
    } catch (error) {
      console.error('Check auth failed:', error);
      clearAccessToken();
      setState({ isAuthenticated: false, isLoading: false, user: null });
    }
  };

  useEffect(() => {
    checkAuth();

    const handleAuthExpired = () => {
      clearAccessToken();
      setState({ isAuthenticated: false, isLoading: false, user: null });
    };

    window.addEventListener('auth:expired', handleAuthExpired);
    return () => {
      window.removeEventListener('auth:expired', handleAuthExpired);
    };
  }, []);

  const login = (token: string, user: Partial<User>) => {
    setAccessToken(token);
    setState({ isAuthenticated: true, isLoading: false, user });
  };

  const logout = () => {
    void unregisterBrowserDevice().catch(() => undefined);
    void apiFetch('/auth/logout', { method: 'POST' }).catch(() => undefined);
    clearAccessToken();
    setState({ isAuthenticated: false, isLoading: false, user: null });
  };

  if (state.isLoading) {
    return <div className="min-h-screen flex items-center justify-center bg-[#f5f7f5]">
      <div className="w-8 h-8 border-4 border-[#2db84c] border-t-transparent rounded-full animate-spin"></div>
    </div>;
  }

  return (
    <AuthContext.Provider value={{ ...state, login, logout, checkAuth }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
