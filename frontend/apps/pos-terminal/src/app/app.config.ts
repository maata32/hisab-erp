import { ApplicationConfig, isDevMode, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { MessageService } from 'primeng/api';

import { appRoutes } from './app.routes';
import { apiInterceptor, API_CONFIG } from '@minierp/shared-api';
import { AUTH_SERVICE, AuthService } from '@minierp/shared-auth';
import { provideAppI18n } from '@minierp/shared-i18n';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([apiInterceptor])),
    provideAppI18n(),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    { provide: API_CONFIG, useValue: { baseUrl: environment.apiBaseUrl } },
    { provide: AUTH_SERVICE, useExisting: AuthService },
    MessageService,
  ],
};
