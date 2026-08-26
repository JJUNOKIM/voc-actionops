import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { clearSession, createSession, readSession, saveSession, subscribeSession } from './session';
import type { TokenResponse } from '../types/api';

const tokens: TokenResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresIn: 1_800,
  refreshTokenExpiresIn: 1_209_600,
};

describe('auth session', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('stores token expiration timestamps relative to the issue time', () => {
    const session = createSession(tokens, 1_000);

    saveSession(session);

    expect(readSession(2_000)).toEqual({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: 1_801_000,
      refreshTokenExpiresAt: 1_209_601_000,
    });
  });

  it('clears an expired refresh session', () => {
    saveSession(createSession(tokens, 1_000));

    expect(readSession(1_209_601_001)).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
  });

  it('notifies subscribers when the session is cleared', () => {
    const listener = vi.fn();
    const unsubscribe = subscribeSession(listener);
    const session = createSession(tokens, 1_000);

    saveSession(session);
    clearSession();
    unsubscribe();

    expect(listener).toHaveBeenNthCalledWith(1, session);
    expect(listener).toHaveBeenNthCalledWith(2, null);
  });
});
