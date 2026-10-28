import fs from 'node:fs';
import path from 'node:path';
import { request as playwrightRequest } from '@playwright/test';
import { SEED_NOW, SERVER_DIR, type Role } from '../support/env';
import { ApiClient, expectStatus } from './api';

/** Seed stores (`db/seed/store/store.csv`, docs/seed-ids.md): S-01 … S-25 in seed order. */
export interface SeedStore {
  id: string;
  /** `S-01` … `S-25` (the scenario code of docs/seed-ids.md / plan-m3). */
  code: string;
  name: string;
  regionId: string;
  cooperationStatus: string;
}

/** One pickup as returned by `GET /api/stores/{id}/pickups`. */
export interface SeedPickup {
  storeId: string;
  pickupAt: string;
  slotCount: number;
  description?: string;
  occupiedSlots: { user: { id: string; displayName: string }; confirmed: boolean }[];
  isRegular: boolean;
}

const STORE_CSV = path.join(SERVER_DIR, 'src/main/resources/db/seed/store/store.csv');
let cache: SeedStore[] | undefined;

/** Splits one CSV line (RFC 4180 quotes). */
function cells(line: string): string[] {
  const out: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (quoted) {
      if (char === '"' && line[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        cell += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      out.push(cell);
      cell = '';
    } else {
      cell += char;
    }
  }
  out.push(cell);
  return out;
}

/** All 25 seed stores (read once from the seed CSV, fixed UUIDs). */
export function seedStores(): SeedStore[] {
  if (cache) return cache;
  const [header, ...rows] = fs.readFileSync(STORE_CSV, 'utf8').trim().split(/\r?\n/);
  const columns = cells(header);
  const col = (name: string) => columns.indexOf(name);
  cache = rows.map((row, index) => {
    const values = cells(row);
    return {
      id: values[col('id')],
      code: `S-${String(index + 1).padStart(2, '0')}`,
      name: values[col('name')],
      regionId: values[col('region_id')],
      cooperationStatus: values[col('cooperation_status')],
    };
  });
  return cache;
}

/** Seed store by code (`S-01`), name (`Coop Bahnhofbrücke`) or id. */
export function seedStore(key: string): SeedStore {
  const store = seedStores().find((s) => s.code === key || s.name === key || s.id === key);
  if (!store) throw new Error(`Unknown seed store "${key}" (see db/seed/store/store.csv)`);
  return store;
}

/** `pickupAt` of a weekday at a Zürich local time, `daysFromSeed` days after SEED_NOW (Monday 2026-11-02). */
export function zurichPickupAt(daysFromSeed: number, localTime: string): string {
  const day = new Date(Date.parse(SEED_NOW) + daysFromSeed * 86_400_000).toISOString().slice(0, 10);
  // November: Zürich is UTC+1 (CET)
  const [hours, minutes] = localTime.split(':').map(Number);
  const utc = new Date(`${day}T00:00:00Z`);
  utc.setUTCHours(hours - 1, minutes, 0, 0);
  return utc.toISOString().replace('.000Z', 'Z');
}

/** S-01 Thursday 2026-11-05 19:00 (foodsaver unconfirmed + sleepy, one free slot). */
export const S01_THURSDAY = zurichPickupAt(3, '19:00');
/** S-07 Wednesday 2026-11-04 20:30, free for sign-up tests. */
export const S07_WEDNESDAY = zurichPickupAt(2, '20:30');
/** S-02 Saturday 2026-11-07 18:00, full (bieb, jumper). */
export const S02_SATURDAY = zurichPickupAt(5, '18:00');
/** S-04 Wednesday 2026-11-04 18:30 (foodsaver confirmed; with S-01 the region rule blocks a third sign-up). */
export const S04_WEDNESDAY = zurichPickupAt(2, '18:30');

export class StoreHelpers {
  constructor(private readonly baseURL: string) {}

  /** Seed store by code, name or id. */
  byKey(key: string): SeedStore {
    return seedStore(key);
  }

  id(key: string): string {
    return seedStore(key).id;
  }

  all(): SeedStore[] {
    return seedStores();
  }

  /** Pickups of a store as `client` sees them (`GET /api/stores/{id}/pickups`). */
  async pickupsOf(client: ApiClient, key: string): Promise<SeedPickup[]> {
    return client.getJson<SeedPickup[]>(`/api/stores/${this.id(key)}/pickups`);
  }

  /** First upcoming pickup of a store as `client` sees it. */
  async nextPickupOf(client: ApiClient, key: string): Promise<SeedPickup | undefined> {
    return (await this.pickupsOf(client, key))[0];
  }

  /** Signs the seed user `role` up for a pickup through the real API (own session). */
  async signUp(role: Role, key: string, pickupAt: string): Promise<void> {
    await this.withRole(role, async (client) => {
      const response = await client.post(`/api/stores/${this.id(key)}/pickups/${encodeURIComponent(pickupAt)}/signups`);
      await expectStatus(response, [200, 201], `sign up ${role} for ${key} ${pickupAt}`);
    });
  }

  /** Runs `action` with an API client logged in as `role`. */
  async withRole<T>(role: Role, action: (client: ApiClient) => Promise<T>): Promise<T> {
    const context = await playwrightRequest.newContext({ baseURL: this.baseURL });
    try {
      const client = new ApiClient(context, this.baseURL);
      await client.loginAs(role);
      return await action(client);
    } finally {
      await context.dispose();
    }
  }
}
