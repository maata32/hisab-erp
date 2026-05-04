import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, finalize, share, tap, throwError } from 'rxjs';
import { AuthServicePort } from './auth.port';
import { AuthState, LoginRequest, LoginResponse } from './auth.types';
import { CurrentUser } from '@minierp/shared-api';

const STORAGE_KEY = 'minierp.auth.v1';
const LANG_KEY = 'minierp.lang';

@Injectable({ providedIn: 'root' })
export class AuthService implements AuthServicePort {
  private readonly http = inject(HttpClient);
  private readonly state$ = new BehaviorSubject<AuthState>(this.loadFromStorage());
  private refreshInFlight$: Observable<LoginResponse> | null = null;

  readonly user = signal<CurrentUser | null>(this.state$.value.user);
  readonly isAuthenticated$ = computed(() => !!this.user());

  constructor() {
    this.state$.subscribe((s) => this.user.set(s.user));
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', req).pipe(
      tap((res) => this.persist(res)),
    );
  }

  refresh(): Observable<LoginResponse> {
    if (this.refreshInFlight$) return this.refreshInFlight$;
    const refreshToken = this.state$.value.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    this.refreshInFlight$ = this.http
      .post<LoginResponse>('/api/v1/auth/refresh', { refreshToken })
      .pipe(
        tap((res) => this.persist(res)),
        finalize(() => (this.refreshInFlight$ = null)),
        share(),
      );
    return this.refreshInFlight$;
  }

  logout(): void {
    const token = this.state$.value.refreshToken;
    if (token) {
      this.http.post('/api/v1/auth/logout', { refreshToken: token }).subscribe({
        error: () => {/* fire-and-forget; clear state regardless */},
      });
    }
    this.clear();
  }

  getAccessToken(): string | null {
    return this.state$.value.accessToken;
  }

  getCurrentUser(): CurrentUser | null {
    return this.state$.value.user;
  }

  getCurrentLanguage(): string {
    return localStorage.getItem(LANG_KEY) ?? this.state$.value.user?.preferredLanguage ?? 'fr';
  }

  setCurrentLanguage(lang: string): void {
    localStorage.setItem(LANG_KEY, lang);
  }

  isAuthenticated(): boolean {
    const exp = this.state$.value.expiresAt;
    return !!this.state$.value.accessToken && (!exp || exp > Date.now());
  }

  hasPermission(code: string): boolean {
    return this.state$.value.user?.permissions?.includes(code) ?? false;
  }

  hasAnyRole(...codes: string[]): boolean {
    const userRoles = this.state$.value.user?.roles ?? [];
    return codes.some((c) => userRoles.includes(c));
  }

  private persist(res: LoginResponse): void {
    const next: AuthState = {
      user: res.user,
      accessToken: res.accessToken,
      refreshToken: res.refreshToken,
      expiresAt: Date.now() + res.accessTokenExpiresInSeconds * 1000 - 5000,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    this.state$.next(next);
  }

  private clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.state$.next({ user: null, accessToken: null, refreshToken: null, expiresAt: null });
  }

  private loadFromStorage(): AuthState {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { user: null, accessToken: null, refreshToken: null, expiresAt: null };
    try {
      return JSON.parse(raw) as AuthState;
    } catch {
      return { user: null, accessToken: null, refreshToken: null, expiresAt: null };
    }
  }
}
