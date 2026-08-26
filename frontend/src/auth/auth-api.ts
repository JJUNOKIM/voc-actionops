import { apiRequest, publicApiRequest } from '../lib/api-client';
import type { TokenResponse, UserProfile } from '../types/api';

export function loginRequest(email: string, password: string): Promise<TokenResponse> {
  return publicApiRequest<TokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function logoutRequest(refreshToken: string): Promise<void> {
  return publicApiRequest<void>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}

export function currentUserRequest(): Promise<UserProfile> {
  return apiRequest<UserProfile>('/api/v1/users/me');
}
