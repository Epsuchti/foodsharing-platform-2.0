import type { Locator, Page } from '@playwright/test';
import { type ApiClient, expect, expectDataState, seedStore } from '../../fixtures';

/** Shared helpers of the M3 pickup specs (seed: docs/seed-ids.md "Pickups", SEED_NOW Monday 2026-11-02 10:00 Zürich). */

/** Opens the pickups tab of a seed store and waits for the list. */
export async function openPickups(page: Page, key: string): Promise<void> {
  await page.goto(`/stores/${seedStore(key).id}/pickups`);
  await expect(page.getByTestId('stores.page.name')).toHaveText(seedStore(key).name);
  await expectDataState(page, 'stores.pickups.state', 'success');
}

/** One pickup row of the pickups tab by its `pickupAt` instant. */
export function pickupRow(page: Page, pickupAt: string): Locator {
  return page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${pickupAt}"]`);
}

/** Clicks the first free slot of a pickup and waits for the eligibility result in the sign-up dialog. */
export async function openSignup(page: Page, pickupAt: string): Promise<void> {
  const row = pickupRow(page, pickupAt);
  await row.scrollIntoViewIfNeeded();
  await row.getByTestId('stores.pickups.slot.free').first().click();
  await expectDataState(page, 'pickups.signup.state', 'success');
}

/** Eligibility reasons of `client` for a pickup (`GET …/eligibility`). */
export async function reasonsOf(client: ApiClient, key: string, pickupAt: string): Promise<string[]> {
  const result = await client.getJson<{ eligible: boolean; reasons: string[] }>(
    `/api/stores/${seedStore(key).id}/pickups/${encodeURIComponent(pickupAt)}/eligibility`,
  );
  return result.reasons;
}

/** Number of bells of one type of `client`. */
export async function bellCount(client: ApiClient, type: string): Promise<number> {
  const page = await client.getJson<{ items: { type: string }[] }>('/api/bells', { params: { size: 50 } });
  return page.items.filter((bell) => bell.type === type).length;
}
