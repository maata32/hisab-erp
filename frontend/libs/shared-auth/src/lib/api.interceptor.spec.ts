import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { API_CONFIG } from '@minierp/shared-api';
import { apiInterceptor } from './api.interceptor';
import { AUTH_SERVICE, AuthServicePort } from './auth.port';

describe('apiInterceptor — 403 handling', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let navigate: jest.Mock;

  beforeEach(() => {
    navigate = jest.fn();
    const authStub: Partial<AuthServicePort> = {
      getAccessToken: () => null,
      getCurrentLanguage: () => 'fr',
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
        { provide: AUTH_SERVICE, useValue: authStub },
        { provide: API_CONFIG, useValue: { baseUrl: 'http://localhost:8080' } },
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('redirects to /forbidden on a 403 from a non-auth API call', () => {
    http.get('/api/v1/products').subscribe({ next: () => undefined, error: () => undefined });
    httpMock
      .expectOne('http://localhost:8080/api/v1/products')
      .flush({ code: 'auth.forbidden' }, { status: 403, statusText: 'Forbidden' });
    expect(navigate).toHaveBeenCalledWith(['/forbidden']);
  });

  it('does NOT redirect on a 403 from the auth/login endpoint (lets the form show the reason)', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ next: () => undefined, error: () => undefined });
    httpMock
      .expectOne('http://localhost:8080/api/v1/auth/login')
      .flush({ code: 'auth.tenant_suspended' }, { status: 403, statusText: 'Forbidden' });
    expect(navigate).not.toHaveBeenCalled();
  });
});
