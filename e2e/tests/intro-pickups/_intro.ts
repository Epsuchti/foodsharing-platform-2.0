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
  cleaningStoreId?: string;
}

/** Changes Zürich's EinAb booking rules (EA22) through the region settings API; `ambassador` must be bot or orga. */
export async function setIntroSettings(ambassador: ApiClient, changes: Partial<IntroSettings>): Promise<void> {
  const path = `/api/regions/${ZH}/settings`;
  const settings = await ambassador.getJson<{ introPickups: IntroSettings }>(path);
  await ambassador.patchJson(path, { introPickups: { ...settings.introPickups, ...changes } });
}

/** Cards of the bookable list with the given category (in list order). */
export function bookableCards(page: Page, category: string): Locator {
  return page.locator(`[data-testid="introPickups.bookable.card"][data-category="${category}"]`);
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
