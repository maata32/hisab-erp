import type { CurrentUser } from '@minierp/shared-api';

export interface LoginRequest {
  email: string;
  password: string;
  tenantCode: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresInSeconds: number;
  user: CurrentUser;
}

export interface AuthState {
  user: CurrentUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
}
