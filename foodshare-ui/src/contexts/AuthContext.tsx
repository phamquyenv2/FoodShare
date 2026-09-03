import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import type { User } from '../types';
import { apiFetch } from '../services/api';

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
    const token = localStorage.getItem('accessToken');
    if (!token) {
      setState({ isAuthenticated: false, isLoading: false, user: null });
      return;
    }

    try {
      const user = await apiFetch<Partial<User>>('/users/me');
      setState({ isAuthenticated: true, isLoading: false, user });
    } catch (error) {
      console.error('Check auth failed:', error);
      localStorage.removeItem('accessToken');
      setState({ isAuthenticated: false, isLoading: false, user: null });
    }
  };

  useEffect(() => {
    checkAuth();
  }, []);

  const login = (token: string, user: Partial<User>) => {
    localStorage.setItem('accessToken', token);
    setState({ isAuthenticated: true, isLoading: false, user });
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
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
