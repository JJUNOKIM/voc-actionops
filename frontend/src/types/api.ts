export type Role = 'ADMIN' | 'PM' | 'CS' | 'DEVELOPER' | 'VIEWER';

export interface ApiEnvelope<T> {
  success: true;
  data: T;
  message: string | null;
}

export interface ApiErrorEnvelope {
  success: false;
  data: null;
  message: string;
  error: {
    code: string;
    details: Array<{
      field: string;
      message: string;
    }>;
  };
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  refreshTokenExpiresIn: number;
}

export interface UserProfile {
  id: number;
  organizationId: number;
  organizationName: string;
  email: string;
  name: string;
  role: Role;
}
