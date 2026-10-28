import type { Page } from '@playwright/test';
import { expect, seedStore } from '../../fixtures';

/** Shared helpers of the M3 store specs. */

export const ZURICH = '00000010-0001-4000-8000-000000000040';
export const KANTON_ZURICH = '00000010-0001-4000-8000-000000000010';

/** Opens `/stores/<id>/<tab>` of a seed store (code, name or id) and waits for the header. */
export async function openStore(page: Page, key: string, tab = 'overview'): Promise<void> {
  await page.goto(`/stores/${seedStore(key).id}/${tab}`);
  await expect(page.getByTestId('stores.page.name')).toHaveText(seedStore(key).name);
}

/** Scrolls an element to the middle of the viewport (clear of the sticky header and the mobile tab bar). */
export async function center(page: Page, testId: string): Promise<void> {
  await page.getByTestId(testId).evaluate((el) => el.scrollIntoView({ block: 'center' }));
}

/** Selects an option of an Optimus `p-select` by its visible label. */
export async function chooseOption(page: Page, testId: string, label: string | RegExp): Promise<void> {
  await center(page, testId);
  await page.getByTestId(testId).click();
  await page.getByRole('option', { name: label }).first().click();
  await expect(page.locator('.p-overlay')).toHaveCount(0);
}

/** Toggles options of an Optimus `p-multiselect` and closes its overlay again. */
export async function chooseOptions(page: Page, testId: string, labels: (string | RegExp)[]): Promise<void> {
  await center(page, testId);
  await page.getByTestId(testId).click();
  for (const label of labels) {
    await page.getByRole('option', { name: label }).first().click();
  }
  await page.keyboard.press('Escape');
  try {
    await expect(page.locator('.p-overlay')).toHaveCount(0, { timeout: 2_000 });
  } catch {
    // some engines keep the overlay open on Escape: toggle it closed through the field
    await page.getByTestId(testId).click({ force: true });
    await expect(page.locator('.p-overlay')).toHaveCount(0);
  }
}
