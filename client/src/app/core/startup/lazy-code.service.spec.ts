import { ErrorHandler, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { OnlineStatusService } from '../online/online-status.service';
import { LazyCodeService } from './lazy-code.service';
import { StartupService } from './startup.service';
import { TestingOnlineStatusService } from '../testing/data-state-testing';

describe('LazyCodeService (overlay code for @defer prefetch, no NG0750 offline)', () => {
  let online: TestingOnlineStatusService;
  let pageShown: ReturnType<typeof signal<boolean>>;
  let errors: unknown[];

  beforeEach(() => {
    online = new TestingOnlineStatusService();
    pageShown = signal(false);
    errors = [];
    TestBed.configureTestingModule({
      providers: [
        { provide: OnlineStatusService, useValue: online },
        { provide: StartupService, useValue: { pageShown } },
        { provide: ErrorHandler, useValue: { handleError: (e: unknown) => errors.push(e) } },
      ],
    });
  });

  const flush = async () => {
    TestBed.tick();
    await Promise.resolve();
    await Promise.resolve();
    TestBed.tick();
  };

  it('loads only after the first page and while online', async () => {
    const load = vi.fn(() => Promise.resolve({}));
    const ready = TestBed.inject(LazyCodeService).ready('test', load);
    online.setOnline(false);
    pageShown.set(true);
    await flush();
    expect(load).not.toHaveBeenCalled();
    expect(ready()).toBe(false);
    online.setOnline(true);
    await flush();
    expect(load).toHaveBeenCalledTimes(1);
    expect(ready()).toBe(true);
  });

  it('swallows a failed import, retries once the connection is back, then reports ready', async () => {
    const load = vi
      .fn<() => Promise<unknown>>()
      .mockRejectedValueOnce(new TypeError('Failed to fetch dynamically imported module'))
      .mockResolvedValue({});
    const ready = TestBed.inject(LazyCodeService).ready('test', load);
    pageShown.set(true);
    await flush();
    expect(load).toHaveBeenCalledTimes(1);
    expect(ready()).toBe(false);
    expect(errors).toEqual([]);
    online.setOnline(false);
    await flush();
    online.setOnline(true);
    await flush();
    expect(load).toHaveBeenCalledTimes(2);
    expect(ready()).toBe(true);
    expect(errors).toEqual([]);
  });

  it('shares one load per key', async () => {
    const load = vi.fn(() => Promise.resolve({}));
    const service = TestBed.inject(LazyCodeService);
    expect(service.ready('popover', load)).toBe(service.ready('popover', load));
  });
});
