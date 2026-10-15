import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore, type User } from './api';

interface AuthState {
  user: User | null;
  /** True until the stored token has been checked against /api/auth/me. */
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  const logout = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!tokenStore.get()) return;
    api.me()
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false));
  }, [logout]);

  const login = useCallback(async (username: string, password: string) => {
    const { token } = await api.login(username, password);
    tokenStore.set(token);
    setUser(await api.me());
  }, []);

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
