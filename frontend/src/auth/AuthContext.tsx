import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { currentUserRequest, loginRequest, logoutRequest } from './auth-api';
import { AuthContext, type AuthContextValue, type AuthStatus } from './auth-context';
import {
  clearSession,
  createSession,
  readSession,
  saveSession,
  subscribeSession,
} from './session';
import type { UserProfile } from '../types/api';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('initializing');
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    return subscribeSession((session) => {
      if (session === null) {
        setUser(null);
        setStatus('unauthenticated');
      }
    });
  }, []);

  useEffect(() => {
    let active = true;

    async function restoreAuthentication() {
      if (readSession() === null) {
        if (active) {
          setStatus('unauthenticated');
        }
        return;
      }

      try {
        const profile = await currentUserRequest();
        if (active) {
          setUser(profile);
          setStatus('authenticated');
        }
      } catch {
        clearSession();
      }
    }

    void restoreAuthentication();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await loginRequest(email, password);
    saveSession(createSession(tokens));

    try {
      const profile = await currentUserRequest();
      setUser(profile);
      setStatus('authenticated');
    } catch (error) {
      clearSession();
      throw error;
    }
  }, []);

  const logout = useCallback(async () => {
    const session = readSession();
    try {
      if (session !== null) {
        await logoutRequest(session.refreshToken);
      }
    } finally {
      clearSession();
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
