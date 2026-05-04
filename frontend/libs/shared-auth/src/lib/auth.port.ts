import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { LoginRequest, LoginResponse } from './auth.types';
import { CurrentUser } from '@minierp/shared-api';

export interface AuthServicePort {
  login(req: LoginRequest): Observable<LoginResponse>;
  refresh(): Observable<LoginResponse>;
  logout(): void;
  getAccessToken(): string | null;
  getCurrentUser(): CurrentUser | null;
  getCurrentLanguage(): string;
  setCurrentLanguage(lang: string): void;
  isAuthenticated(): boolean;
  hasPermission(code: string): boolean;
  hasAnyRole(...codes: string[]): boolean;
}

export const AUTH_SERVICE = new InjectionToken<AuthServicePort>('AUTH_SERVICE');
