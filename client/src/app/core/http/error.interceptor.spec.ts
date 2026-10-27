import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { ApiErrorCode, Role } from '../../api';
import { provideApi } from '../../api/provide-api';
import { AuthFacadeService } from '../auth/auth-facade.service';
import { SessionExpiredService } from '../auth/session-expired.service';
import { SessionService } from '../auth/session.service';
import { ApiHttpErrorResponse } from '../data/api-error';
import { OnlineStatusService } from '../online/online-status.service';
import { TestingOnlineStatusService } from '../testing/data-state-testing';
import { errorInterceptor } from './error.interceptor';
import { NO_SESSION_EXPIRED_DIALOG } from './http-context';
import { RETRY_DELAYS_MS, retryInterceptor } from './retry.interceptor';

const apiError = (code: ApiErrorCode, retryable = false) => ({
  code,
  message: code,
  traceId: 'trace123',
  retryable,
  fieldErrors: [{ field: 'email', code: 'Email' }],
});

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: SessionService;
  let online: TestingOnlineStatusService;
  let expired: SessionExpiredService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, retryInterceptor])),
        provideHttpClientTesting(),
        provideApi(''),
        TestingOnlineStatusService,
        { provide: OnlineStatusService, useExisting: TestingOnlineStatusService },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    online = TestBed.inject(TestingOnlineStatusService);
    expired = TestBed.inject(SessionExpiredService);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  function logIn(): void {
    session.applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@b.ch',
        displayName: 'Anna',
        role: Role.Foodsaver,
        language: 'de' as never,
        theme: 'SYSTEM' as never,
        verified: true,
      },
    });
  }

  function capture(url: string, context?: HttpContext): { error?: unknown } {
    const result: { error?: unknown } = {};
    http.get(url, { context }).subscribe({ error: (error: unknown) => (result.error = error) });
    return result;
  }

  it('rethrows HTTP errors enriched with the normalized ApiErrorView', () => {
    const result = capture('/api/things');
    backend
      .expectOne('/api/things')
      .flush(apiError(ApiErrorCode.ValidationFailed), { status: 400, statusText: 'Bad Request' });
    expect(result.error).toBeInstanceOf(ApiHttpErrorResponse);
    expect(result.error).toBeInstanceOf(HttpErrorResponse);
    const view = (result.error as ApiHttpErrorResponse).view;
    expect(view.status).toBe(400);
    expect(view.code).toBe(ApiErrorCode.ValidationFailed);
    expect(view.traceId).toBe('trace123');
    expect(view.fieldErrors).toEqual([{ field: 'email', code: 'Email' }]);
  });

  it('passes 403, 404, 409 and 429 through to the caller', () => {
    for (const [status, code] of [
      [404, ApiErrorCode.ResourceNotFound],
      [409, ApiErrorCode.ConcurrentModification],
      [429, ApiErrorCode.TooManyRequests],
    ] as const) {
      const result = capture('/api/x');
      backend.expectOne('/api/x').flush(apiError(code), { status, statusText: 'x' });
      expect((result.error as ApiHttpErrorResponse).view.code).toBe(code);
    }
    expect(expired.visible()).toBe(false);
  });

  it('opens the session-expired dialog on 401 while logged in', () => {
    logIn();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/account/sessions');
    capture('/api/auth/sessions');
    backend
      .expectOne('/api/auth/sessions')
      .flush(apiError(ApiErrorCode.AuthenticationRequired), {
        status: 401,
        statusText: 'Unauthorized',
      });
    expect(expired.visible()).toBe(true);
    expect(expired.returnUrl()).toBe('/account/sessions');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('does not open the dialog for anonymous users, the session endpoint or opted-out requests', () => {
    capture('/api/users/me');
    backend
      .expectOne('/api/users/me')
      .flush(apiError(ApiErrorCode.AuthenticationRequired), { status: 401, statusText: 'x' });
    expect(expired.visible()).toBe(false);

    logIn();
    capture('/api/auth/session');
    backend
      .expectOne('/api/auth/session')
      .flush(apiError(ApiErrorCode.AuthenticationRequired), { status: 401, statusText: 'x' });
    capture('/api/users/me', new HttpContext().set(NO_SESSION_EXPIRED_DIALOG, true));
    backend
      .expectOne('/api/users/me')
      .flush(apiError(ApiErrorCode.AuthenticationRequired), { status: 401, statusText: 'x' });
    expect(expired.visible()).toBe(false);
  });

  it('refreshes the session after a 403 while logged in', () => {
    logIn();
    const refresh = vi
      .spyOn(TestBed.inject(AuthFacadeService), 'refreshSession')
      .mockImplementation(() => undefined);
    capture('/api/history');
    backend
      .expectOne('/api/history')
      .flush(apiError(ApiErrorCode.AccessDenied), { status: 403, statusText: 'Forbidden' });
    expect(refresh).toHaveBeenCalledWith(true);
  });

  it('reports network failures (status 0) to the online status, and success afterwards', () => {
    let error: unknown;
    http.post('/api/x', {}).subscribe({ error: (e: unknown) => (error = e) });
    backend.expectOne('/api/x').error(new ProgressEvent('error'), { status: 0 });
    expect((error as ApiHttpErrorResponse).view.code).toBe('NETWORK');
    expect(online.failures).toBe(1);
    expect(online.online()).toBe(false);

    http.get('/api/y').subscribe();
    backend.expectOne('/api/y').flush({});
    expect(online.online()).toBe(true);
  });
});

describe('retryInterceptor + errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let online: TestingOnlineStatusService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, retryInterceptor])),
        provideHttpClientTesting(),
        provideApi(''),
        TestingOnlineStatusService,
        { provide: OnlineStatusService, useExisting: TestingOnlineStatusService },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    online = TestBed.inject(TestingOnlineStatusService);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('retries a failing GET twice with backoff and then reports offline', () => {
    let error: unknown;
    http.get('/api/x').subscribe({ error: (e: unknown) => (error = e) });
    backend.expectOne('/api/x').error(new ProgressEvent('error'), { status: 0 });
    vi.advanceTimersByTime(RETRY_DELAYS_MS[0]);
    backend.expectOne('/api/x').error(new ProgressEvent('error'), { status: 0 });
    vi.advanceTimersByTime(RETRY_DELAYS_MS[1]);
    backend.expectOne('/api/x').error(new ProgressEvent('error'), { status: 0 });
    expect((error as ApiHttpErrorResponse).view.code).toBe('NETWORK');
    expect(online.failures).toBe(1);
  });

  it('succeeds when a retry succeeds', () => {
    let value: unknown;
    http.get('/api/x').subscribe((v) => (value = v));
    backend
      .expectOne('/api/x')
      .flush(apiError(ApiErrorCode.UnexpectedError, true), { status: 503, statusText: 'x' });
    vi.advanceTimersByTime(RETRY_DELAYS_MS[0]);
    backend.expectOne('/api/x').flush({ ok: true });
    expect(value).toEqual({ ok: true });
  });

  it('never retries 4xx or mutations', () => {
    http.get('/api/x').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/x')
      .flush(apiError(ApiErrorCode.ResourceNotFound), { status: 404, statusText: 'x' });
    http.post('/api/y', {}).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/y')
      .flush(apiError(ApiErrorCode.UnexpectedError, true), { status: 500, statusText: 'x' });
    vi.advanceTimersByTime(5000);
    backend.expectNone('/api/x');
    backend.expectNone('/api/y');
  });
});
