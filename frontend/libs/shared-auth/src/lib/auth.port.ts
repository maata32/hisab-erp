import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { LoginRequest, LoginResponse } from './auth.types';
import { CurrentUser } from '@hisaberp/shared-api';

export interface AuthServicePort {
  login(req: LoginRequest): Observable<LoginResponse>;
  /** Platform (super-admin) sign in — no tenant code; issues a cross-tenant session. */
  platformLogin(email: string, password: string): Observable<LoginResponse>;
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
