import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createSession, readSession, saveSession } from '../auth/session';
import { apiRequest, resetApiClientForTests } from './api-client';
import type { ApiEnvelope, ApiErrorEnvelope, TokenResponse } from '../types/api';

const initialTokens: TokenResponse = {
  accessToken: 'old-access-token',
  refreshToken: 'old-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 1_800,
  refreshTokenExpiresIn: 1_209_600,
};

const refreshedTokens: TokenResponse = {
  accessToken: 'new-access-token',
  refreshToken: 'new-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 1_800,
  refreshTokenExpiresIn: 1_209_600,
};

describe('api client', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    resetApiClientForTests();
    vi.restoreAllMocks();
  });

  it('retries an unauthorized request after rotating the refresh token', async () => {
    saveSession(createSession(initialTokens));
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(errorResponse(401, 'UNAUTHORIZED', '인증이 필요합니다.'))
      .mockResolvedValueOnce(successResponse(refreshedTokens))
      .mockResolvedValueOnce(successResponse({ id: 7 }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiRequest<{ id: number }>('/api/v1/users/me');

    expect(result).toEqual({ id: 7 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/refresh');
    const retriedHeaders = new Headers(fetchMock.mock.calls[2]?.[1]?.headers);
    expect(retriedHeaders.get('Authorization')).toBe('Bearer new-access-token');
    expect(readSession()?.refreshToken).toBe('new-refresh-token');
  });

  it('refreshes an expired access token before making the protected request', async () => {
    const expiredAccessSession = createSession(initialTokens, Date.now() - 2_000_000);
    expiredAccessSession.refreshTokenExpiresAt = Date.now() + 60_000;
    saveSession(expiredAccessSession);
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(successResponse(refreshedTokens))
      .mockResolvedValueOnce(successResponse({ status: 'ok' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiRequest<{ status: string }>('/api/v1/status')).resolves.toEqual({
      status: 'ok',
    });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/refresh');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/status');
  });

  it('clears the session when refresh fails', async () => {
    const expiredAccessSession = createSession(initialTokens, Date.now() - 2_000_000);
    expiredAccessSession.refreshTokenExpiresAt = Date.now() + 60_000;
    saveSession(expiredAccessSession);
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        errorResponse(401, 'INVALID_REFRESH_TOKEN', 'refresh token이 유효하지 않습니다.'),
      ),
    );

    await expect(apiRequest('/api/v1/users/me')).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_REFRESH_TOKEN',
    });
    expect(readSession()).toBeNull();
  });
});

function successResponse<T>(data: T): Response {
  const body: ApiEnvelope<T> = { success: true, data, message: null };
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function errorResponse(status: number, code: string, message: string): Response {
  const body: ApiErrorEnvelope = {
    success: false,
    data: null,
    message,
    error: { code, details: [] },
  };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
