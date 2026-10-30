import { isPlatformServer } from '@angular/common';
import {
  DestroyRef,
  Injector,
  PLATFORM_ID,
  Signal,
  assertInInjectionContext,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Observable, Subscription, from, isObservable } from 'rxjs';

import { OnlineStatusService } from '../online/online-status.service';
import { ApiErrorView, toApiErrorView } from './api-error';

/**
 * - `idle`: params() returned undefined, nothing loaded yet
 * - `loading`: first load (skeleton)
 * - `refreshing`: reload with data shown (inline spinner)
 * - `success` / `empty`: loaded
 * - `error`: failed (data of a previous load may still be shown when keepDataOnError)
 * - `forbidden` (403) / `notFound` (404): dedicated error variants
 * - `offline`: no connection (status 0); last data stays visible, auto-retry when back online
 */
export type DataStatus =
  | 'idle'
  | 'loading'
  | 'refreshing'
  | 'success'
  | 'empty'
  | 'error'
  | 'forbidden'
  | 'notFound'
  | 'offline';

export interface DataState<T> {
  readonly status: Signal<DataStatus>;
  readonly data: Signal<T | undefined>;
  readonly error: Signal<ApiErrorView | undefined>;
  /** True while data from an earlier successful load is shown after a failed reload. */
  readonly isStale: Signal<boolean>;
  /** True for `loading` and `refreshing`. */
  readonly isLoading: Signal<boolean>;
  /** Loads again with the current params (keeps the data visible → `refreshing`). */
  reload(): void;
  /**
   * Background refetch (realtime event or reconnect): reloads only while data is shown (success, empty, refreshing,
   * or stale after a failed reload). A first load, an error without data, forbidden and not found are left alone:
   * a shown error keeps its retry button until the user retries, instead of being swapped for a skeleton under the
   * user's pointer.
   */
  refresh(): void;
  /** Replaces the data locally (after a mutation or optimistic update); recomputes success/empty. */
  set(value: T): void;
  update(fn: (value: T | undefined) => T): void;
}

export type Loader<T, P> = (params: P, signal: AbortSignal) => Observable<T> | Promise<T>;

export interface LoadResourceOptions<T, P> {
  /** Signal-driven params; the loader re-runs when they change. Returning undefined → `idle`. */
  params?: () => P | undefined;
  loader: Loader<T, P>;
  /** Default: [] or { items: [] } → `empty`. */
  isEmpty?: (value: T) => boolean;
  /** Default true: a failed reload keeps the previous data (shown with an inline error). */
  keepDataOnError?: boolean;
  /** Default true: reloads automatically when the connection comes back after an offline failure. */
  autoRetryWhenOnline?: boolean;
  /** Needed when called outside an injection context. */
  injector?: Injector;
}

export function defaultIsEmpty(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (value && typeof value === 'object' && Array.isArray((value as { items?: unknown }).items)) {
    return (value as { items: unknown[] }).items.length === 0;
  }
  return false;
}

/** Maps a failure to the status shown by fs-data-state. */
export function statusForError(error: ApiErrorView): DataStatus {
  if (error.status === 0 || error.code === 'NETWORK') {
    return 'offline';
  }
  if (error.status === 403) {
    return 'forbidden';
  }
  if (error.status === 404) {
    return 'notFound';
  }
  return 'error';
}

/**
 * Wraps a generated API call in a DataState. Must run in an injection context (field initializer of a
 * component/service) or get `injector`. Without `params` it loads immediately.
 *
 * ```ts
 * readonly sessions = loadResource({ loader: () => this.authApi.listSessions() });
 * readonly entry = loadResource({ params: () => this.id(), loader: (id) => this.api.get({ id }) });
 * ```
 */
export function loadResource<T, P = undefined>(options: LoadResourceOptions<T, P>): DataState<T> {
  if (!options.injector) {
    assertInInjectionContext(loadResource);
  }
  const injector = options.injector ?? inject(Injector);
  const online = injector.get(OnlineStatusService);
  // Prerendering (SSG, D11.5.3) has no backend: data-loading elements render their skeleton and the browser
  // loads the data after hydration, so the hydrated DOM matches the prerendered one.
  const onServer = isPlatformServer(injector.get(PLATFORM_ID));
  const isEmpty = options.isEmpty ?? defaultIsEmpty;
  const keepDataOnError = options.keepDataOnError ?? true;

  const status = signal<DataStatus>(options.params ? 'idle' : 'loading');
  const data = signal<T | undefined>(undefined);
  const error = signal<ApiErrorView | undefined>(undefined);
  const isStale = signal(false);

  let subscription: Subscription | null = null;
  let abort: AbortController | null = null;
  let currentParams: P | undefined = undefined;
  let hasParams = !options.params;
  let destroyed = false;

  const cancel = () => {
    subscription?.unsubscribe();
    subscription = null;
    abort?.abort();
    abort = null;
  };

  const settle = (value: T) => {
    data.set(value);
    error.set(undefined);
    isStale.set(false);
    status.set(isEmpty(value) ? 'empty' : 'success');
  };

  const run = (params: P, keepData: boolean) => {
    cancel();
    if (destroyed) {
      return;
    }
    if (!keepData) {
      data.set(undefined);
      isStale.set(false);
    }
    status.set(data() === undefined ? 'loading' : 'refreshing');
    if (onServer) {
      return;
    }
    const controller = new AbortController();
    abort = controller;
    let source: Observable<T>;
    try {
      const result = options.loader(params, controller.signal);
      source = isObservable(result) ? result : from(result);
    } catch (thrown) {
      fail(thrown);
      return;
    }
    let received = false;
    subscription = source.subscribe({
      next: (value) => {
        received = true;
        settle(value);
      },
      error: (thrown: unknown) => fail(thrown),
      complete: () => {
        if (!received) {
          settle(undefined as T);
        }
        abort = null;
      },
    });
  };

  function fail(thrown: unknown): void {
    abort = null;
    const view = toApiErrorView(thrown);
    error.set(view);
    const next = statusForError(view);
    if (!keepDataOnError || next === 'forbidden' || next === 'notFound') {
      data.set(undefined);
    }
    isStale.set(data() !== undefined);
    status.set(next);
  }

  const reload = () => {
    if (hasParams) {
      run(currentParams as P, true);
    }
  };

  const refresh = () => {
    if (data() !== undefined || status() === 'empty') {
      reload();
    }
  };

  if (options.params) {
    const paramsFn = options.params;
    effect(
      () => {
        const params = paramsFn();
        untracked(() => {
          if (params === undefined) {
            cancel();
            hasParams = false;
            currentParams = undefined;
            data.set(undefined);
            error.set(undefined);
            isStale.set(false);
            status.set('idle');
            return;
          }
          hasParams = true;
          currentParams = params;
          // new params = different data: show the skeleton again instead of stale content
          run(params, false);
        });
      },
      { injector },
    );
  } else {
    run(undefined as P, false);
  }

  if (options.autoRetryWhenOnline ?? true) {
    let wasOnline = untracked(online.online);
    effect(
      () => {
        const isOnline = online.online();
        untracked(() => {
          if (isOnline && !wasOnline && (status() === 'offline' || error()?.code === 'NETWORK')) {
            reload();
          }
          wasOnline = isOnline;
        });
      },
      { injector },
    );
  }

  injector.get(DestroyRef).onDestroy(() => {
    destroyed = true;
    cancel();
  });

  return {
    status: status.asReadonly(),
    data: data.asReadonly(),
    error: error.asReadonly(),
    isStale: isStale.asReadonly(),
    isLoading: computed(() => status() === 'loading' || status() === 'refreshing'),
    reload,
    refresh,
    set: (value: T) => settle(value),
    update: (fn) => settle(fn(data())),
  };
}

/**
 * A DataState with fixed content (dev gallery, component tests, static demo data). `reload` calls
 * `onReload` if given.
 */
export function staticDataState<T>(
  status: DataStatus,
  value?: T,
  error?: ApiErrorView,
  onReload?: () => void,
): DataState<T> {
  const statusSignal = signal(status);
  const dataSignal = signal<T | undefined>(value);
  return {
    status: statusSignal.asReadonly(),
    data: dataSignal.asReadonly(),
    error: signal(error).asReadonly(),
    isStale: signal(value !== undefined && ['error', 'offline'].includes(status)).asReadonly(),
    isLoading: computed(() => statusSignal() === 'loading' || statusSignal() === 'refreshing'),
    reload: () => onReload?.(),
    refresh: () => {
      if (dataSignal() !== undefined || statusSignal() === 'empty') {
        onReload?.();
      }
    },
    set: (next: T) => {
      dataSignal.set(next);
      statusSignal.set(defaultIsEmpty(next) ? 'empty' : 'success');
    },
    update: (fn) => {
      const next = fn(dataSignal());
      dataSignal.set(next);
      statusSignal.set(defaultIsEmpty(next) ? 'empty' : 'success');
    },
  };
}
