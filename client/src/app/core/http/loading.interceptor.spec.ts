import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { HttpActivityService } from './http-activity.service';
import { NO_LOADING_BAR } from './http-context';
import { loadingInterceptor } from './loading.interceptor';

/** AD1 request bar (§14.4): requests in flight are counted until they succeed, fail or are cancelled. */
describe('loadingInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let activity: HttpActivityService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    activity = TestBed.inject(HttpActivityService);
  });

  afterEach(() => backend.verify());

  it('is busy while a request runs and idle once it succeeded', () => {
    http.get('/api/a').subscribe();
    http.get('/api/b').subscribe();
    expect(activity.count()).toBe(2);
    expect(activity.busy()).toBe(true);
    backend.expectOne('/api/a').flush({});
    expect(activity.count()).toBe(1);
    backend.expectOne('/api/b').flush({});
    expect(activity.busy()).toBe(false);
  });

  it('ends when the request fails', () => {
    http.get('/api/missing').subscribe({ error: () => undefined });
    expect(activity.busy()).toBe(true);
    backend.expectOne('/api/missing').flush({ code: 'RESOURCE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(activity.busy()).toBe(false);
  });

  it('ends when the caller unsubscribes', () => {
    const subscription = http.get('/api/slow').subscribe();
    expect(activity.busy()).toBe(true);
    subscription.unsubscribe();
    expect(backend.expectOne('/api/slow').cancelled).toBe(true);
    expect(activity.busy()).toBe(false);
  });

  it('leaves background requests out', () => {
    http.get('/api/poll', { context: new HttpContext().set(NO_LOADING_BAR, true) }).subscribe();
    expect(activity.busy()).toBe(false);
    backend.expectOne('/api/poll').flush({});
  });

  it('never counts below zero', () => {
    activity.end();
    expect(activity.count()).toBe(0);
  });
});
