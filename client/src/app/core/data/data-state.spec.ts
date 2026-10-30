import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import { ApiErrorCode } from '../../api';
import { OnlineStatusService } from '../online/online-status.service';
import {
  TestingOnlineStatusService,
  apiHttpError,
  controlledLoader,
  networkError,
  provideDataStateTesting,
} from '../testing/data-state-testing';
import { DataState, LoadResourceOptions, loadResource, staticDataState } from './data-state';

function create<T, P = undefined>(options: LoadResourceOptions<T, P>): DataState<T> {
  return TestBed.runInInjectionContext(() => loadResource(options));
}

describe('loadResource', () => {
  let online: TestingOnlineStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    online = TestBed.inject(OnlineStatusService) as unknown as TestingOnlineStatusService;
  });

  it('goes from loading to success', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    expect(state.status()).toBe('loading');
    expect(state.isLoading()).toBe(true);
    loader.resolve(['a']);
    expect(state.status()).toBe('success');
    expect(state.data()).toEqual(['a']);
    expect(state.error()).toBeUndefined();
  });

  it('reports empty for [] and for pages without items', () => {
    expect(create({ loader: () => of([]) }).status()).toBe('empty');
    expect(create({ loader: () => of({ items: [], page: {} }) }).status()).toBe('empty');
    expect(create({ loader: () => of({ items: [1] }) }).status()).toBe('success');
  });

  it('uses a custom isEmpty', () => {
    const state = create({ loader: () => of({ count: 0 }), isEmpty: (value) => value.count === 0 });
    expect(state.status()).toBe('empty');
  });

  it('supports promise loaders', async () => {
    const state = create({ loader: () => Promise.resolve(['x']) });
    expect(state.status()).toBe('loading');
    await Promise.resolve();
    await Promise.resolve();
    expect(state.status()).toBe('success');
  });

  it('maps a 500 to error with the translated code and recovers on reload', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    loader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    expect(state.status()).toBe('error');
    expect(state.error()?.code).toBe(ApiErrorCode.UnexpectedError);
    expect(state.error()?.traceId).toBe('abc123def456');
    expect(state.error()?.retryable).toBe(true);

    state.reload();
    expect(state.status()).toBe('loading');
    expect(loader.calls).toBe(2);
    loader.resolve(['ok']);
    expect(state.status()).toBe('success');
    expect(state.error()).toBeUndefined();
  });

  it('maps 403 to forbidden and 404 to notFound', () => {
    const forbidden = create({
      loader: () => {
        throw apiHttpError(403, ApiErrorCode.AccessDenied);
      },
    });
    expect(forbidden.status()).toBe('forbidden');
    const missing = controlledLoader<string>();
    const notFound = create({ loader: missing.loader });
    missing.reject(apiHttpError(404, ApiErrorCode.ResourceNotFound));
    expect(notFound.status()).toBe('notFound');
    expect(notFound.data()).toBeUndefined();
  });

  it('maps a network failure to offline', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    loader.reject(networkError());
    expect(state.status()).toBe('offline');
    expect(state.error()?.code).toBe('NETWORK');
  });

  it('keeps data while refreshing and after a failed reload (stale)', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    loader.resolve(['first']);

    state.reload();
    expect(state.status()).toBe('refreshing');
    expect(state.data()).toEqual(['first']);

    loader.reject(apiHttpError(503, ApiErrorCode.UnexpectedError));
    expect(state.status()).toBe('error');
    expect(state.data()).toEqual(['first']);
    expect(state.isStale()).toBe(true);

    state.reload();
    loader.resolve(['second']);
    expect(state.status()).toBe('success');
    expect(state.isStale()).toBe(false);
    expect(state.data()).toEqual(['second']);
  });

  it('drops the data on error when keepDataOnError is false', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader, keepDataOnError: false });
    loader.resolve(['first']);
    state.reload();
    loader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    expect(state.status()).toBe('error');
    expect(state.data()).toBeUndefined();
  });

  it('is idle without params and loads when params arrive or change', () => {
    const id = signal<string | undefined>(undefined);
    const seen: string[] = [];
    const state = create({
      params: () => id(),
      loader: (value: string) => {
        seen.push(value);
        return of(`entry ${value}`);
      },
    });
    TestBed.tick();
    expect(state.status()).toBe('idle');
    expect(seen).toEqual([]);

    id.set('1');
    TestBed.tick();
    expect(state.status()).toBe('success');
    expect(state.data()).toBe('entry 1');

    id.set('2');
    TestBed.tick();
    expect(seen).toEqual(['1', '2']);
    expect(state.data()).toBe('entry 2');

    id.set(undefined);
    TestBed.tick();
    expect(state.status()).toBe('idle');
    expect(state.data()).toBeUndefined();
  });

  it('shows the skeleton again (no stale data) when params change', () => {
    const id = signal('1');
    const loader = controlledLoader<string>();
    const state = create({ params: () => id(), loader: loader.loader });
    TestBed.tick();
    loader.resolve('one');
    id.set('2');
    TestBed.tick();
    expect(state.status()).toBe('loading');
    expect(state.data()).toBeUndefined();
  });

  it('reloads automatically when the connection comes back after an offline failure', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    TestBed.tick();
    loader.reject(networkError());
    expect(state.status()).toBe('offline');

    online.setOnline(false);
    TestBed.tick();
    expect(loader.calls).toBe(1);

    online.setOnline(true);
    TestBed.tick();
    expect(loader.calls).toBe(2);
    expect(state.status()).toBe('loading');
    loader.resolve(['back']);
    expect(state.status()).toBe('success');
  });

  it('does not auto-retry successful states or when disabled', () => {
    const ok = controlledLoader<string[]>();
    create({ loader: ok.loader });
    ok.resolve(['x']);
    const off = controlledLoader<string[]>();
    create({ loader: off.loader, autoRetryWhenOnline: false });
    off.reject(networkError());
    TestBed.tick();

    online.setOnline(false);
    TestBed.tick();
    online.setOnline(true);
    TestBed.tick();
    expect(ok.calls).toBe(1);
    expect(off.calls).toBe(1);
  });

  it('cancels the running request when reloading', () => {
    let unsubscribed = 0;
    let calls = 0;
    const state = create({
      loader: (_params, abort) =>
        new Observable<string>(() => {
          calls++;
          abort.addEventListener('abort', () => unsubscribed++);
        }),
    });
    state.reload();
    expect(calls).toBe(2);
    expect(unsubscribed).toBe(1);
  });

  it('refresh() leaves a first load, an error without data, forbidden and not found alone', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    state.refresh();
    expect(loader.calls).toBe(1);
    loader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    state.refresh();
    expect(loader.calls).toBe(1);
    expect(state.status()).toBe('error');

    const missing = controlledLoader<string>();
    const notFound = create({ loader: missing.loader });
    missing.reject(apiHttpError(404, ApiErrorCode.ResourceNotFound));
    notFound.refresh();
    expect(missing.calls).toBe(1);
    expect(notFound.status()).toBe('notFound');
  });

  it('refresh() reloads shown data, also empty and stale data', () => {
    const loader = controlledLoader<string[]>();
    const state = create({ loader: loader.loader });
    loader.resolve([]);
    state.refresh();
    expect(loader.calls).toBe(2);
    expect(state.status()).toBe('refreshing');
    loader.resolve(['a']);
    state.refresh();
    expect(loader.calls).toBe(3);
    loader.reject(apiHttpError(503, ApiErrorCode.UnexpectedError));
    expect(state.isStale()).toBe(true);
    state.refresh();
    expect(loader.calls).toBe(4);
    expect(state.status()).toBe('refreshing');
  });

  it('set() and update() replace the data locally', () => {
    const state = create({ loader: () => of(['a', 'b']) });
    state.update((list) => (list ?? []).filter((item) => item !== 'a'));
    expect(state.data()).toEqual(['b']);
    state.set([]);
    expect(state.status()).toBe('empty');
  });
});

describe('staticDataState', () => {
  it('exposes a fixed state and calls onReload', () => {
    let reloaded = false;
    const state = staticDataState('error', undefined, undefined, () => (reloaded = true));
    expect(state.status()).toBe('error');
    state.reload();
    expect(reloaded).toBe(true);
  });
});
