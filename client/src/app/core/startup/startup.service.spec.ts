import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, vi } from 'vitest';

import { provideApi } from '../../api/provide-api';
import { SERVICE_WORKER_DELAY_MS, StartupService } from './startup.service';

describe('StartupService', () => {
  let startup: StartupService;
  let http: HttpTestingController;
  let ready: boolean;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    });
    startup = TestBed.inject(StartupService);
    http = TestBed.inject(HttpTestingController);
    ready = false;
    startup.serviceWorkerReady.subscribe(() => (ready = true));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Lets idle callbacks/timeouts run, then flushes the signal → observable bridge. */
  async function advance(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    TestBed.tick();
  }

  it('starts nothing before the first page was shown (D11.5.3)', async () => {
    await advance(60_000);
    expect(startup.pageShown()).toBe(false);
    expect(ready).toBe(false);
    http.expectNone(() => true);
  });

  it('marks the document once the first page was shown (screenshot tests wait for it)', () => {
    document.documentElement.removeAttribute('data-page-shown');
    startup.markPageShown();
    http.match(() => true).forEach((request) => request.flush({}));
    expect(document.documentElement.hasAttribute('data-page-shown')).toBe(true);
    document.documentElement.removeAttribute('data-page-shown');
  });

  it('lets the service worker register only a quiet period after the first page', async () => {
    startup.markPageShown();
    http.match(() => true).forEach((request) => request.flush({}));
    expect(startup.pageShown()).toBe(true);
    await advance(SERVICE_WORKER_DELAY_MS);
    expect(ready).toBe(false);
    // idle before and after the delay (jsdom has no requestIdleCallback: 1 s fallback each)
    await advance(2_000);
    expect(ready).toBe(true);
  });

  it('lets the service worker register at once when a feature needs it (push)', async () => {
    startup.requestServiceWorker();
    await advance(0);
    expect(ready).toBe(true);
  });
});
