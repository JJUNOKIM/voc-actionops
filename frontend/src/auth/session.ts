import type { TokenResponse } from '../types/api';

const SESSION_KEY = 'voc-actionops.auth-session';

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: number;
  refreshTokenExpiresAt: number;
}

type SessionListener = (session: AuthSession | null) => void;

const listeners = new Set<SessionListener>();

export function createSession(tokens: TokenResponse, now = Date.now()): AuthSession {
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    accessTokenExpiresAt: now + tokens.expiresIn * 1000,
    refreshTokenExpiresAt: now + tokens.refreshTokenExpiresIn * 1000,
  };
}

export function readSession(now = Date.now()): AuthSession | null {
  const stored = window.sessionStorage.getItem(SESSION_KEY);
  if (stored === null) {
    return null;
  }

  try {
    const session = JSON.parse(stored) as Partial<AuthSession>;
    if (!isValidSession(session) || session.refreshTokenExpiresAt <= now) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(session: AuthSession): void {
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  notifyListeners(session);
}

export function clearSession(): void {
  window.sessionStorage.removeItem(SESSION_KEY);
  notifyListeners(null);
}

export function subscribeSession(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function isValidSession(session: Partial<AuthSession>): session is AuthSession {
  return (
    typeof session.accessToken === 'string' &&
    session.accessToken.length > 0 &&
    typeof session.refreshToken === 'string' &&
    session.refreshToken.length > 0 &&
    typeof session.accessTokenExpiresAt === 'number' &&
    Number.isFinite(session.accessTokenExpiresAt) &&
    typeof session.refreshTokenExpiresAt === 'number' &&
    Number.isFinite(session.refreshTokenExpiresAt)
  );
}

function notifyListeners(session: AuthSession | null): void {
  listeners.forEach((listener) => listener(session));
}
