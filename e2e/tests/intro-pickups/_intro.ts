import type { Locator, Page } from '@playwright/test';
import { type ApiClient, expect, seedRegion } from '../../fixtures';

/** Zürich Stadt: the seed region with 14 upcoming EinAbs (docs/seed-ids.md, module 0030). */
export const ZH = seedRegion('zuerich-stadt').id;

/** Seed EinAb ids `00000030-0001-4000-8000-0000000000NN`. */
export const einabId = (n: number): string => `00000030-0001-4000-8000-${String(n).padStart(12, '0')}`;

export interface IntroSettings {
  maxActiveBookingsPerUser: number;
  preventDuplicateGuideBookings: boolean;
  preventDuplicateCategoryBookings: boolean;
  requireFairteilerVisitByThirdIntroPickup: boolean;
  ignoredCategories: string[];
}

/** Changes Zürich's EinAb booking rules (EA22) through the region settings API; `ambassador` must be bot or orga. */
export async function setIntroSettings(ambassador: ApiClient, changes: Partial<IntroSettings>): Promise<void> {
  const path = `/api/regions/${ZH}/settings`;
  const settings = await ambassador.getJson<{ introPickups: IntroSettings }>(path);
  await ambassador.patchJson(path, { introPickups: { ...settings.introPickups, ...changes } });
}

/**
 * Zürich's upcoming seed EinAbs per category (`intropickup/intro_pickup.csv`), by date. The list itself no longer
 * tells the category before booking (decision 156, S17 D5), so tests find cards by id.
 */
const SEED_CATEGORIES: Record<string, number[]> = {
  SUPERMARKET: [1, 9],
  TAKEOUT: [2, 13],
  MARKET: [3, 14],
  BAKERY: [11, 10],
  RESTAURANT: [4, 12],
  FAIRTEILER_CLEANING: [5],
  MESSE: [6],
  GROSSABHOLUNG: [7],
  ONLINE: [8],
};

/** Cards of the bookable list with the given seed category (in list order), plus EinAbs a test created. */
export function bookableCards(page: Page, category: string, extraIds: string[] = []): Locator {
  const ids = [...(SEED_CATEGORIES[category] ?? []).map(einabId), ...extraIds];
  return page.locator(ids.map((id) => `[data-testid="introPickups.bookable.card"][data-id="${id}"]`).join(', '));
}

/** Opens `/intro-pickups` and waits for the list. */
export async function openBookable(page: Page): Promise<void> {
  await page.goto('/intro-pickups');
  await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', /success|empty/);
}

/** Accepts the shell's confirmation dialog. */
export async function acceptConfirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog-accept-button').click();
}
