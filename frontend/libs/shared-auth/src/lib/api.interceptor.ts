import {
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  HttpErrorResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { API_CONFIG } from '@minierp/shared-api';
import { AUTH_SERVICE, AuthServicePort } from './auth.port';

export const apiInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const auth = inject(AUTH_SERVICE) as AuthServicePort;
  const config = inject(API_CONFIG);
  const router = inject(Router);

  const isApiCall = req.url.startsWith('/api') || req.url.startsWith(config.baseUrl);
  const url = isApiCall && req.url.startsWith('/api') ? `${config.baseUrl}${req.url}` : req.url;

  let headers = req.headers;
  const accessToken = auth.getAccessToken();
  if (accessToken && isApiCall && !req.headers.has('Authorization')) {
    headers = headers.set('Authorization', `Bearer ${accessToken}`);
  }
  const lang = auth.getCurrentLanguage();
  if (lang && !req.headers.has('Accept-Language')) {
    headers = headers.set('Accept-Language', lang);
  }
  if (!req.headers.has('X-Trace-Id')) {
    headers = headers.set('X-Trace-Id', cryptoRandomId());
  }

  const cloned = req.clone({ url, headers });
  return next(cloned).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && accessToken) {
        return auth.refresh().pipe(
          switchMap(() => {
            const newToken = auth.getAccessToken();
            if (!newToken) {
              router.navigate(['/auth/login']);
              return throwError(() => err);
            }
            const retried = cloned.clone({
              headers: cloned.headers.set('Authorization', `Bearer ${newToken}`),
            });
            return next(retried);
          }),
          catchError(() => {
            auth.logout();
            router.navigate(['/auth/login']);
            return throwError(() => err);
          }),
        );
      }
      if (err.status === 403) {
        router.navigate(['/forbidden']);
      }
      return throwError(() => err);
    }),
  );
};

function cryptoRandomId(): string {
  const buf = new Uint8Array(16);
  crypto.getRandomValues(buf);
  return Array.from(buf, (b) => b.toString(16).padStart(2, '0')).join('');
}
