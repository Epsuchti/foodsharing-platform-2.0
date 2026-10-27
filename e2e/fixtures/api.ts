import type { APIRequestContext, APIResponse, BrowserContext, Page } from '@playwright/test';
import { SEED_NOW, roleEmail, type Role } from '../support/env';
import type { CapturedMail, DevClockState, DevUser, SessionInfo, UserRole } from './types';

export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

type FetchOptions = NonNullable<Parameters<APIRequestContext['fetch']>[1]>;
export type RequestOptions = Omit<FetchOptions, 'method' | 'data'>;
const UNSAFE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Thin wrapper around an APIRequestContext that behaves like the Angular client: it sends the `XSRF-TOKEN` cookie
 * back as `X-XSRF-TOKEN` header on every unsafe request (docs/conventions.md A9). The cookie is obtained lazily
 * from any GET (`/api/public/app-info`) when it is not in the cookie jar yet.
 *
 * `get/post/put/patch/delete` return the raw response (assert status yourself); the `*Json` variants throw a
 * descriptive error on a non-2xx status and return the parsed body.
 */
export class ApiClient {
  constructor(
    readonly request: APIRequestContext,
    readonly baseURL: string,
  ) {}

  /** API client sharing the cookie jar of a browser context/page (e.g. act as the user the page is logged in as). */
  static of(target: Page | BrowserContext, baseURL: string): ApiClient {
    const context = 'context' in target ? target.context() : target;
    return new ApiClient(context.request, baseURL);
  }

  url(path: string): string {
    return /^https?:\/\//.test(path) ? path : `${this.baseURL}${path.startsWith('/') ? '' : '/'}${path}`;
  }

  async csrfToken(): Promise<string> {
    let token = await this.cookie(CSRF_COOKIE);
    if (!token) {
      await this.request.get(this.url('/api/public/app-info'));
      token = await this.cookie(CSRF_COOKIE);
    }
    if (!token) throw new Error(`The server did not set the ${CSRF_COOKIE} cookie`);
    return token;
  }

  async cookie(name: string): Promise<string | undefined> {
    const state = await this.request.storageState();
    return state.cookies.find((cookie) => cookie.name === name)?.value;
  }

  async fetch(method: string, path: string, data?: unknown, options: RequestOptions = {}): Promise<APIResponse> {
    const upper = method.toUpperCase();
    const headers: Record<string, string> = { Accept: 'application/json', ...(options.headers ?? {}) };
    if (UNSAFE.has(upper) && !hasHeader(headers, CSRF_HEADER)) headers[CSRF_HEADER] = await this.csrfToken();
    return this.request.fetch(this.url(path), {
      ...options,
      method: upper,
      headers,
      ...(data === undefined ? {} : { data }),
    });
  }

  get(path: string, options?: RequestOptions): Promise<APIResponse> {
    return this.fetch('GET', path, undefined, options);
  }
  post(path: string, data?: unknown, options?: RequestOptions): Promise<APIResponse> {
    return this.fetch('POST', path, data, options);
  }
  put(path: string, data?: unknown, options?: RequestOptions): Promise<APIResponse> {
    return this.fetch('PUT', path, data, options);
  }
  patch(path: string, data?: unknown, options?: RequestOptions): Promise<APIResponse> {
    return this.fetch('PATCH', path, data, options);
  }
  delete(path: string, options?: RequestOptions): Promise<APIResponse> {
    return this.fetch('DELETE', path, undefined, options);
  }

  async getJson<T>(path: string, options?: RequestOptions): Promise<T> {
    return parse<T>(await this.get(path, options), 'GET', path);
  }
  async postJson<T>(path: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return parse<T>(await this.post(path, data, options), 'POST', path);
  }
  async putJson<T>(path: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return parse<T>(await this.put(path, data, options), 'PUT', path);
  }
  async patchJson<T>(path: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return parse<T>(await this.patch(path, data, options), 'PATCH', path);
  }

  /** Current session (`GET /api/auth/session`). */
  session(): Promise<SessionInfo> {
    return this.getJson<SessionInfo>('/api/auth/session');
  }

  /** Logs this cookie jar in as a seed user via the dev API (`POST /api/dev/login-as`). */
  async loginAs(role: Role): Promise<SessionInfo> {
    const user = await new DevApi(this).user(role);
    return this.postJson<SessionInfo>('/api/dev/login-as', { userId: user.id });
  }

  /** `POST /api/auth/logout` (with CSRF header). */
  async logout(): Promise<void> {
    await expectStatus(await this.post('/api/auth/logout'), [204], 'POST /api/auth/logout');
  }
}

/** Dev API (`/api/dev/**`, local/e2e profile only; see openapi/paths/dev.yaml). */
export class DevApi {
  private static usersByBaseURL = new Map<string, DevUser[]>();

  constructor(private readonly api: ApiClient) {}

  /** Seed users; cached per base URL because their ids are fixed seed UUIDs. */
  async users(): Promise<DevUser[]> {
    const cached = DevApi.usersByBaseURL.get(this.api.baseURL);
    if (cached) return cached;
    const users = await this.api.getJson<DevUser[]>('/api/dev/users');
    DevApi.usersByBaseURL.set(this.api.baseURL, users);
    return users;
  }

  async user(role: Role): Promise<DevUser> {
    const email = roleEmail(role);
    const user = (await this.users()).find((candidate) => candidate.email.toLowerCase() === email);
    if (!user) throw new Error(`Seed user ${email} is not returned by GET /api/dev/users`);
    return user;
  }

  /** Seed reset + captured mails cleared + server clock back at SEED_NOW. */
  async resetSeed(): Promise<void> {
    await expectStatus(await this.api.post('/api/dev/seed/reset'), [204], 'POST /api/dev/seed/reset');
    await this.clearMails();
    await this.setClock(SEED_NOW);
  }

  async setRole(role: Role, newRole: UserRole): Promise<DevUser> {
    const user = await this.user(role);
    return this.api.putJson<DevUser>(`/api/dev/users/${user.id}/role`, { role: newRole });
  }

  /** Fix the server clock at `iso`, or release it to the system clock when omitted. */
  setClock(iso?: string): Promise<DevClockState> {
    return this.api.putJson<DevClockState>('/api/dev/clock', iso ? { fixedAt: iso } : {});
  }

  mails(to?: string): Promise<CapturedMail[]> {
    return this.api.getJson<CapturedMail[]>('/api/dev/mails', to ? { params: { to } } : undefined);
  }

  async clearMails(): Promise<void> {
    await expectStatus(await this.api.delete('/api/dev/mails'), [204], 'DELETE /api/dev/mails');
  }
}

export async function expectStatus(response: APIResponse, expected: number[], label: string): Promise<void> {
  if (!expected.includes(response.status())) {
    throw new Error(`${label} → HTTP ${response.status()} (expected ${expected.join('/')}): ${await safeText(response)}`);
  }
}

async function parse<T>(response: APIResponse, method: string, path: string): Promise<T> {
  if (!response.ok()) {
    throw new Error(`${method} ${path} → HTTP ${response.status()}: ${await safeText(response)}`);
  }
  return (await response.json()) as T;
}

async function safeText(response: APIResponse): Promise<string> {
  try {
    return (await response.text()).slice(0, 2000);
  } catch {
    return '<no body>';
  }
}

function hasHeader(headers: Record<string, string>, name: string): boolean {
  return Object.keys(headers).some((key) => key.toLowerCase() === name.toLowerCase());
}
