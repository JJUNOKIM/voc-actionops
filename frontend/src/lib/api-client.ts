import { clearSession, createSession, readSession, saveSession } from '../auth/session';
import type { ApiEnvelope, ApiErrorEnvelope, TokenResponse } from '../types/api';

const REFRESH_MARGIN_MS = 1_000;

let refreshInFlight: Promise<void> | null = null;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export async function publicApiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  return sendRequest<T>(path, init);
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let session = readSession();
  if (session === null) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증이 필요합니다.');
  }

  if (session.accessTokenExpiresAt <= Date.now() + REFRESH_MARGIN_MS) {
    await refreshSession();
    session = readSession();
  }

  if (session === null) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증이 필요합니다.');
  }

  try {
    return await sendRequest<T>(path, withAccessToken(init, session.accessToken));
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      throw error;
    }
  }

  await refreshSession();
  const refreshedSession = readSession();
  if (refreshedSession === null) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증이 필요합니다.');
  }
  return sendRequest<T>(path, withAccessToken(init, refreshedSession.accessToken));
}

export function resetApiClientForTests(): void {
  refreshInFlight = null;
}

async function refreshSession(): Promise<void> {
  if (refreshInFlight !== null) {
    return refreshInFlight;
  }

  refreshInFlight = performRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function performRefresh(): Promise<void> {
  const session = readSession();
  if (session === null) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증이 필요합니다.');
  }

  try {
    const tokens = await publicApiRequest<TokenResponse>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
    saveSession(createSession(tokens));
  } catch (error) {
    clearSession();
    throw error;
  }
}

async function sendRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: withJsonHeaders(init.headers, init.body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.');
  }

  const payload = await readPayload<T>(response);
  if (!response.ok || payload.success === false) {
    const errorPayload = payload as ApiErrorEnvelope;
    throw new ApiError(
      response.status,
      errorPayload.error?.code ?? 'REQUEST_FAILED',
      errorPayload.message ?? '요청을 처리할 수 없습니다.',
    );
  }
  return (payload as ApiEnvelope<T>).data;
}

async function readPayload<T>(response: Response): Promise<ApiEnvelope<T> | ApiErrorEnvelope> {
  try {
    return (await response.json()) as ApiEnvelope<T> | ApiErrorEnvelope;
  } catch {
    throw new ApiError(response.status, 'INVALID_RESPONSE', '서버 응답을 읽을 수 없습니다.');
  }
}

function withAccessToken(init: RequestInit, accessToken: string): RequestInit {
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${accessToken}`);
  return { ...init, headers };
}

function withJsonHeaders(headersInit: HeadersInit | undefined, body: BodyInit | null | undefined): Headers {
  const headers = new Headers(headersInit);
  if (body !== undefined && body !== null && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json');
  return headers;
}
