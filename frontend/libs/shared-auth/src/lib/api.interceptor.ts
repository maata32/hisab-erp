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
import { MessageService } from 'primeng/api';
import { API_CONFIG } from '@hisaberp/shared-api';
import { AUTH_SERVICE, AuthServicePort } from './auth.port';

/* Generic infra-failure messages. Kept inline (not ngx-translate) on purpose: a
   TranslateService injected into the HTTP interceptor — which itself feeds the
   translation loader's HttpClient — resolves to an instance whose dictionary is
   empty, so translate keys would leak verbatim into the toast. The set is tiny
   and stable, so an inline lang map is the reliable choice here. */
const ERR_TEXT: Record<string, { error: string; server: string; network: string }> = {
  fr: { error: 'Erreur', server: 'Erreur serveur, veuillez réessayer.', network: 'Connexion impossible. Vérifiez votre réseau.' },
  ar: { error: 'خطأ', server: 'خطأ في الخادم، يرجى إعادة المحاولة.', network: 'تعذّر الاتصال. تحقق من شبكتك.' },
  en: { error: 'Error', server: 'Server error, please try again.', network: 'Cannot connect. Check your network.' },
};

/* Live UI locale — LocaleService writes the active code onto <html lang> on every
   switch, so this follows the locale the user currently sees (unlike the persisted
   auth language). */
function uiLang(): string {
  const code = (typeof document !== 'undefined' && document.documentElement.lang) || 'fr';
  return code.split('-')[0];
}

/* Throttle global error toasts: when the backend is down a burst of parallel
   requests all fail at once — show at most one toast per window. */
let lastErrorToastAt = 0;
function notifyError(
  messages: MessageService,
  lang: string,
  kind: 'server' | 'network',
  serverDetail?: string,
): void {
  const now = Date.now();
  if (now - lastErrorToastAt < 4000) return;
  lastErrorToastAt = now;
  const txt = ERR_TEXT[lang] ?? ERR_TEXT['fr'];
  messages.add({ severity: 'error', summary: txt.error, detail: serverDetail || txt[kind], life: 5000 });
}

export const apiInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const auth = inject(AUTH_SERVICE) as AuthServicePort;
  const config = inject(API_CONFIG);
  const router = inject(Router);
  const messages = inject(MessageService);

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
      // A 403 on an auth endpoint (login/refresh) is a domain condition — locked
      // account, or a suspended/pending/archived tenant — not a "you opened a page
      // you may not see" situation. Let it propagate so the caller (the login form)
      // can surface the real reason instead of masking it behind the generic 403 page.
      if (err.status === 403 && !isAuthEndpoint(req.url)) {
        router.navigate(['/forbidden']);
      } else if (err.status === 0) {
        // Network/CORS failure — no response reached the browser.
        notifyError(messages, uiLang(), 'network');
      } else if (err.status >= 500) {
        // Server error — surface the backend message if present, else a generic one.
        // 4xx are intentionally left to per-page handlers (business validation messages).
        const serverMsg = (err.error as { message?: string } | null)?.message;
        notifyError(messages, uiLang(), 'server', serverMsg);
      }
      return throwError(() => err);
    }),
  );
};

function isAuthEndpoint(url: string): boolean {
  return url.includes('/api/v1/auth/');
}

function cryptoRandomId(): string {
  const buf = new Uint8Array(16);
  crypto.getRandomValues(buf);
  return Array.from(buf, (b) => b.toString(16).padStart(2, '0')).join('');
}
