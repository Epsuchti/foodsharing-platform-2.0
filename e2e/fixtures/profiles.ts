import { request as playwrightRequest, type APIResponse } from '@playwright/test';
import type { Role } from '../support/env';
import { ApiClient, DevApi } from './api';

/**
 * Profile module helpers (M6 B2 / plan-m7 Q2): act as a seed user through the real API. Seed (docs/seed-ids.md):
 * 14 bananas, buddies foodsaver↔bieb and foodsaver↔guide CONFIRMED, jumper→foodsaver REQUESTED, 6 wall posts on
 * foodsaver; sleepy is in FULL sleep mode, jumper TEMPORARY 2026-11-01..15.
 */

/** A banana message long enough for the server rule (≥ 100 characters after trimming). */
export const BANANA_TEXT =
  'Mit dir machen Abholungen Freude: immer pünktlich, gut gelaunt und du lässt nie etwas liegen. Vielen Dank dafür!';

/** Seed user id of a role (fixed seed UUIDs). */
export async function seedUserId(baseURL: string, role: Role): Promise<string> {
  const request = await playwrightRequest.newContext({ baseURL });
  try {
    return (await new DevApi(new ApiClient(request, baseURL)).user(role)).id;
  } finally {
    await request.dispose();
  }
}

/** Runs `action` with an API client logged in as `role`, and returns its result. */
async function as<T>(baseURL: string, role: Role, action: (api: ApiClient) => Promise<T>): Promise<T> {
  const request = await playwrightRequest.newContext({ baseURL });
  try {
    const api = new ApiClient(request, baseURL);
    await api.loginAs(role);
    return await action(api);
  } finally {
    await request.dispose();
  }
}

/** Read the body before the request context is disposed. */
async function settled(response: APIResponse): Promise<{ status: number; body: string }> {
  return { status: response.status(), body: await response.text() };
}

export class ProfileHelpers {
  constructor(private readonly baseURL: string) {}

  userId(role: Role): Promise<string> {
    return seedUserId(this.baseURL, role);
  }

  /** `POST /api/users/{userId}/bananas` as `role`; returns status and body (201, 400, 403, 409). */
  giveBanana(role: Role, userId: string, message = BANANA_TEXT): Promise<{ status: number; body: string }> {
    return as(this.baseURL, role, async (api) => settled(await api.post(`/api/users/${userId}/bananas`, { message })));
  }

  /** `POST /api/users/{userId}/buddies` as `role` (sends a request or accepts theirs); returns the relation. */
  buddy(role: Role, userId: string): Promise<{ status: string }> {
    return as(this.baseURL, role, (api) => api.postJson<{ status: string }>(`/api/users/${userId}/buddies`));
  }

  /** `GET /api/users/{userId}/profile` as `role`. */
  profile<T = Record<string, unknown>>(role: Role, userId: string): Promise<T> {
    return as(this.baseURL, role, (api) => api.getJson<T>(`/api/users/${userId}/profile`));
  }
}
