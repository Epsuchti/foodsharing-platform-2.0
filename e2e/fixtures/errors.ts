import { expect, type BrowserContext, type Locator, type Page, type Request, type Route } from '@playwright/test';

/** URL matcher: a path (`/api/auth/sessions`, exact pathname match; `*` wildcards allowed) or a RegExp on the full URL. */
export type UrlMatcher = string | RegExp;

export interface InjectApiErrorOptions {
  url: UrlMatcher;
  /** HTTP status to answer with. Omit together with `delayMs` to only slow the real request down. */
  status?: number;
  /** `ApiErrorCode`; defaults from the status (403 → ACCESS_DENIED, 429 → TOO_MANY_REQUESTS, …). */
  code?: string;
  /** Only intercept this method (default: any). */
  method?: string;
  /** Intercept the first `times` matching requests only (default: all, until `remove()`). */
  times?: number;
  /** Delay before answering (or before continuing to the server when `status` is omitted). */
  delayMs?: number;
  /** Seconds for `Retry-After` / `retryAfterSeconds` (429/503; default 30). */
  retryAfterSeconds?: number;
  /** Override the ApiError body completely. */
  body?: unknown;
}

export interface Injection {
  /** Number of requests intercepted so far. */
  calls(): number;
  /** Stop intercepting (later requests reach the server again). */
  remove(): Promise<void>;
}

const DEFAULT_CODES: Record<number, string> = {
  400: 'VALIDATION_FAILED',
  401: 'AUTHENTICATION_REQUIRED',
  403: 'ACCESS_DENIED',
  404: 'RESOURCE_NOT_FOUND',
  409: 'CONCURRENT_MODIFICATION',
  429: 'TOO_MANY_REQUESTS',
  500: 'UNEXPECTED_ERROR',
  502: 'UNEXPECTED_ERROR',
  503: 'UNEXPECTED_ERROR',
};

export const INJECTED_TRACE_ID = 'e2e000000000';

/** Builds a predicate for `page.route` from a path or RegExp. */
export function urlPredicate(matcher: UrlMatcher): (url: URL) => boolean {
  if (matcher instanceof RegExp) return (url) => matcher.test(url.href);
  const pattern = new RegExp(`^${matcher.split('*').map(escapeRegExp).join('[^?#]*')}$`);
  return (url) => pattern.test(url.pathname);
}

/**
 * Answers matching requests with an `ApiError` body (§12.3) — or only delays them (`delayMs` without `status`).
 * Register before the page triggers the request (usually before `page.goto`).
 */
export async function injectApiError(page: Page, options: InjectApiErrorOptions): Promise<Injection> {
  const matches = urlPredicate(options.url);
  let calls = 0;
  const handler = async (route: Route, request: Request): Promise<void> => {
    if (options.method && request.method().toUpperCase() !== options.method.toUpperCase()) {
      await route.fallback();
      return;
    }
    if (options.times !== undefined && calls >= options.times) {
      await route.fallback();
      return;
    }
    calls++;
    if (options.delayMs) await new Promise((resolve) => setTimeout(resolve, options.delayMs));
    if (options.status === undefined) {
      await route.fallback();
      return;
    }
    const status = options.status;
    const retryable = status >= 500 || status === 429 || status === 503;
    const retryAfter = status === 429 || status === 503 ? (options.retryAfterSeconds ?? 30) : undefined;
    const body = options.body ?? {
      code: options.code ?? DEFAULT_CODES[status] ?? 'UNEXPECTED_ERROR',
      message: `Injected by e2e (${status})`,
      traceId: INJECTED_TRACE_ID,
      retryable,
      ...(retryAfter !== undefined ? { retryAfterSeconds: retryAfter } : {}),
    };
    await route.fulfill({
      status,
      contentType: 'application/json',
      headers: {
        'X-Trace-Id': INJECTED_TRACE_ID,
        ...(retryAfter !== undefined ? { 'Retry-After': String(retryAfter) } : {}),
      },
      body: JSON.stringify(body),
    });
  };
  const predicate = (url: URL): boolean => matches(url);
  await page.route(predicate, handler);
  return {
    calls: () => calls,
    remove: () => page.unroute(predicate, handler),
  };
}

export interface InjectNetworkFailureOptions {
  url: UrlMatcher;
  method?: string;
  times?: number;
  /** Playwright abort error code (default `internetdisconnected`). */
  errorCode?: Parameters<Route['abort']>[0];
}

/** Aborts matching requests like a dropped connection (status 0 in the client → `NETWORK`). */
export async function injectNetworkFailure(page: Page, options: InjectNetworkFailureOptions): Promise<Injection> {
  const matches = urlPredicate(options.url);
  let calls = 0;
  const handler = async (route: Route, request: Request): Promise<void> => {
    if (
      (options.method && request.method().toUpperCase() !== options.method.toUpperCase()) ||
      (options.times !== undefined && calls >= options.times)
    ) {
      await route.fallback();
      return;
    }
    calls++;
    await route.abort(options.errorCode ?? 'internetdisconnected');
  };
  const predicate = (url: URL): boolean => matches(url);
  await page.route(predicate, handler);
  return { calls: () => calls, remove: () => page.unroute(predicate, handler) };
}

/** Browser offline (fires `offline`; requests fail). */
export async function goOffline(context: BrowserContext): Promise<void> {
  await context.setOffline(true);
}

/** Browser back online (fires `online`). */
export async function goOnline(context: BrowserContext): Promise<void> {
  await context.setOffline(false);
}

export type DataStatus = 'loading' | 'refreshing' | 'success' | 'empty' | 'error' | 'forbidden' | 'notFound' | 'offline';

/** The `<fs-data-state>` root carrying `data-testid` (A7); its `data-state` attribute is the DataStatus. */
export function dataState(page: Page, testId: string): Locator {
  return page.getByTestId(testId);
}

export async function expectDataState(page: Page, testId: string, status: DataStatus): Promise<Locator> {
  const root = dataState(page, testId);
  await expect(root).toHaveAttribute('data-state', status);
  return root;
}

/** Retry button inside a DataState error view (`data-testid` ending in `retry`, else a button named like "retry"). */
export function retryButton(root: Locator): Locator {
  return root
    .locator('[data-testid$="retry"]')
    .or(root.getByRole('button', { name: /retry|erneut|nochmal|wiederholen|réessayer|riprova/i }))
    .first();
}

export interface ExpectErrorStateOptions {
  /** Expect a visible retry button (default true). */
  retry?: boolean;
  /** Expected status attribute (default `error`; use `forbidden`/`notFound`/`offline` for the variants). */
  status?: Extract<DataStatus, 'error' | 'forbidden' | 'notFound' | 'offline'>;
  /** Expect this trace id in the details (default: not checked). */
  traceId?: string;
}

/** Asserts the DataState error view and returns its retry button (when `retry` is not false). */
export async function expectErrorState(
  page: Page,
  testId: string,
  options: ExpectErrorStateOptions = {},
): Promise<{ root: Locator; retry: Locator }> {
  const root = await expectDataState(page, testId, options.status ?? 'error');
  const retry = retryButton(root);
  if (options.retry ?? true) await expect(retry).toBeVisible();
  if (options.traceId) await expect(root).toContainText(options.traceId);
  return { root, retry };
}

/** Asserts the DataState is loading and renders a skeleton (hold the request with `injectApiError({ delayMs })`). */
export async function expectSkeleton(page: Page, testId: string): Promise<Locator> {
  const root = await expectDataState(page, testId, 'loading');
  await expect(root.locator('[data-skeleton], .p-skeleton, fs-skeleton, [class*="skeleton"]').first()).toBeVisible();
  return root;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export const errors = {
  injectApiError,
  injectNetworkFailure,
  goOffline,
  goOnline,
  dataState,
  expectDataState,
  expectErrorState,
  expectSkeleton,
  retryButton,
};
export type ErrorHelpers = typeof errors;
