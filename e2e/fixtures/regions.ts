import fs from 'node:fs';
import path from 'node:path';
import { request as playwrightRequest } from '@playwright/test';
import { SERVER_DIR, type Role } from '../support/env';
import { ApiClient, expectStatus } from './api';

/** Seed regions/groups (`db/seed/region/region.csv`, docs/seed-ids.md): slug → id, name, type, parent. */
export interface SeedRegion {
  id: string;
  slug: string;
  name: string;
  type: string;
  parentId: string | null;
}

const REGION_CSV = path.join(SERVER_DIR, 'src/main/resources/db/seed/region/region.csv');
let cache: Map<string, SeedRegion> | undefined;

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

/** All seed regions and working groups by slug (read once from the seed CSV, the fixed seed UUIDs). */
export function seedRegions(): Map<string, SeedRegion> {
  if (cache) return cache;
  const [header, ...rows] = fs.readFileSync(REGION_CSV, 'utf8').trim().split(/\r?\n/);
  const columns = cells(header);
  const col = (name: string) => columns.indexOf(name);
  cache = new Map();
  for (const row of rows) {
    const values = cells(row);
    const parent = values[col('parent_id')];
    const region: SeedRegion = {
      id: values[col('id')],
      slug: values[col('slug')],
      name: values[col('name')],
      type: values[col('type')],
      parentId: parent && parent !== 'NULL' ? parent : null,
    };
    cache.set(region.slug, region);
  }
  return cache;
}

/** Seed region by slug (`zuerich-stadt`, `winterthur`, …); throws for unknown slugs. */
export function seedRegion(slug: string): SeedRegion {
  const region = seedRegions().get(slug);
  if (!region) throw new Error(`Unknown seed region slug "${slug}" (see db/seed/region/region.csv)`);
  return region;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** A region id from an id or a seed slug. */
export function regionId(idOrSlug: string): string {
  return UUID.test(idOrSlug) ? idOrSlug : seedRegion(idOrSlug).id;
}

export class RegionHelpers {
  constructor(private readonly baseURL: string) {}

  /** Seed region by slug. */
  bySlug(slug: string): SeedRegion {
    return seedRegion(slug);
  }

  id(idOrSlug: string): string {
    return regionId(idOrSlug);
  }

  /**
   * Lets the seed user `role` join a region through the real API (`PUT /api/regions/{id}/members/me`, own session),
   * e.g. `await region.joinRegion('newbie', 'zuerich-stadt')`. Joining a region the user is already in is accepted.
   */
  async joinRegion(role: Role, idOrSlug: string): Promise<void> {
    const context = await playwrightRequest.newContext({ baseURL: this.baseURL });
    try {
      const client = new ApiClient(context, this.baseURL);
      await client.loginAs(role);
      const response = await client.put(`/api/regions/${regionId(idOrSlug)}/members/me`);
      await expectStatus(response, [200, 204], `PUT /api/regions/${idOrSlug}/members/me as ${role}`);
    } finally {
      await context.dispose();
    }
  }

  /** Same as joinRegion, with an API client that is already logged in (e.g. `api` or `apiOf(page)`). */
  async joinRegionAs(client: ApiClient, idOrSlug: string): Promise<void> {
    const response = await client.put(`/api/regions/${regionId(idOrSlug)}/members/me`);
    await expectStatus(response, [200, 204], `PUT /api/regions/${idOrSlug}/members/me`);
  }
}
