import { request as playwrightRequest, type APIResponse } from '@playwright/test';
import type { Role } from '../support/env';
import { ApiClient } from './api';

/**
 * Food basket helpers (M6 B5 / plan-m7 Q5). Seed (docs/seed-ids.md, `163-basket-seed.yaml`): baskets `01`–`08` ACTIVE
 * within 5 km of Zürich HB (owners foodsaver 01/02, bieb 03/04, guide 05/06, jumper 07/08), `09` TAKEN, `10` EXPIRED,
 * `11`/`12` ACTIVE CUSTOM in Winterthur (~20 km). Open baskets expire 2099-12-31. foodsaver has open requests on
 * bieb's `03` (REQUESTED, with message) and `04` (READ); guide on `01`; jumper on `05` (DENIED).
 */
export const BASKETS = {
  /** foodsaver, HOME, photo, message only; guide requested it. */
  brot: '00000048-0001-4000-8000-000000000001',
  /** foodsaver, CUSTOM at Zürich HB, message + mobile. */
  gemuese: '00000048-0001-4000-8000-000000000002',
  /** bieb, HOME, photo, message + phone; foodsaver requested it (REQUESTED). */
  joghurt: '00000048-0001-4000-8000-000000000003',
  /** bieb, CUSTOM, photo; foodsaver requested it (READ). */
  aepfel: '00000048-0001-4000-8000-000000000004',
  /** guide, HOME, message only; jumper's request was DENIED. */
  reis: '00000048-0001-4000-8000-000000000005',
  /** guide, CUSTOM, mobile only (no messages). */
  konserven: '00000048-0001-4000-8000-000000000006',
  /** jumper, HOME, photo, no requests. */
  schokolade: '00000048-0001-4000-8000-000000000007',
  /** jumper, CUSTOM, no requests. */
  getraenke: '00000048-0001-4000-8000-000000000008',
  /** bieb, TAKEN. */
  salat: '00000048-0001-4000-8000-000000000009',
  /** foodsaver, EXPIRED. */
  pizza: '00000048-0001-4000-8000-000000000010',
  /** guide / jumper, ACTIVE CUSTOM in Winterthur (about 20 km from Zürich HB). */
  winterthurBrot: '00000048-0001-4000-8000-000000000011',
  winterthurGemuese: '00000048-0001-4000-8000-000000000012',
} as const;

/** Zürich HB, the centre of the seed baskets. */
export const ZURICH_HB = { latitude: 47.3769, longitude: 8.5417 } as const;

/** Basket ids that are open (ACTIVE, not expired) in the seed, nearest-first order not implied. */
export const OPEN_ZURICH_BASKETS = [
  BASKETS.brot,
  BASKETS.gemuese,
  BASKETS.joghurt,
  BASKETS.aepfel,
  BASKETS.reis,
  BASKETS.konserven,
  BASKETS.schokolade,
  BASKETS.getraenke,
] as const;

/** `BasketCreate` (contract `schemas/baskets.yaml`). */
export interface BasketCreateBody {
  description: string;
  locationType: 'HOME' | 'CUSTOM';
  contactMessage: boolean;
  contactPhone: boolean;
  contactMobile: boolean;
  lifetimeDays?: number;
  weightGrams?: number;
  groceryIds?: string[];
  kinds?: string[];
  lat?: number;
  lon?: number;
  phone?: string;
  mobile?: string;
  photoUploadId?: string;
}

export interface BasketDto {
  id: string;
  description: string;
  status: string;
  lat: number;
  lon: number;
  locationType: string;
  kinds: string[];
  photoUrl?: string;
  expiresAt: string;
  owner: { id: string; displayName: string };
  requests?: { requester: { id: string }; status: string; message?: string }[];
  myRequest?: { status: string };
}

/** Runs `action` with an API client logged in as `role`. */
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

async function settled(response: APIResponse): Promise<{ status: number; body: string }> {
  return { status: response.status(), body: await response.text() };
}

export class BasketHelpers {
  constructor(private readonly baseURL: string) {}

  /** `POST /api/baskets` as `role` with sensible defaults (CUSTOM at Zürich HB, message contact); returns the basket. */
  create(role: Role, body: Partial<BasketCreateBody> = {}): Promise<BasketDto> {
    const full: BasketCreateBody = {
      description: `e2e Korb ${Date.now()}`,
      locationType: 'CUSTOM',
      lat: ZURICH_HB.latitude,
      lon: ZURICH_HB.longitude,
      contactMessage: true,
      contactPhone: false,
      contactMobile: false,
      lifetimeDays: 7,
      ...body,
    };
    return as(this.baseURL, role, (api) => api.postJson<BasketDto>('/api/baskets', full));
  }

  /** `POST /api/baskets` as `role` without defaults; returns status and body (validation tests). */
  tryCreate(role: Role, body: Partial<BasketCreateBody>): Promise<{ status: number; body: string }> {
    return as(this.baseURL, role, async (api) => settled(await api.post('/api/baskets', body)));
  }

  /** `GET /api/baskets/{id}` as `role`. */
  get(role: Role, basketId: string): Promise<BasketDto> {
    return as(this.baseURL, role, (api) => api.getJson<BasketDto>(`/api/baskets/${basketId}`));
  }

  /** `POST /api/baskets/{id}/requests` as `role`. */
  request(role: Role, basketId: string, message?: string): Promise<{ status: number; body: string }> {
    return as(this.baseURL, role, async (api) => settled(await api.post(`/api/baskets/${basketId}/requests`, message ? { message } : {})));
  }
}
