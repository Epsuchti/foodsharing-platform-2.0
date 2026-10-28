import type { Locator, Page } from '@playwright/test';
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

/** Seed users' display names (db/seed/user). */
export const NAMES = {
  foodsaver: 'Sarah Müller',
  jumper: 'Jonas Weber',
  sleepy: 'Marco Bernasconi',
  bieb: 'Nina Schneider',
  guide: 'Julien Favre',
  bot: 'Reto Baumann',
  welcome: 'Priya Sharma',
  reports: 'Céline Rochat',
} as const;

/** A member row of the team tab (F3) by display name. */
export function memberRow(page: Page, name: string): Locator {
  return page.locator(`[data-testid="stores.team.member"][data-user="${name}"]`);
}

/** Opens the row menu of a team member and clicks one of its actions (`stores.team.<action>`). */
export async function memberAction(page: Page, name: string, action: string): Promise<void> {
  const row = memberRow(page, name);
  await row.scrollIntoViewIfNeeded();
  await row.getByTestId('stores.team.menu').click();
  await page.getByTestId(`stores.team.${action}`).click();
}

/** Fills and confirms the required-message dialog of the team tab. */
export async function confirmWithMessage(page: Page, message: string): Promise<void> {
  await page.getByTestId('stores.team.messageDialog.message').fill(message);
  await page.getByTestId('stores.team.messageDialog.confirm').click();
  await expect(page.getByTestId('stores.team.messageDialog.message')).toBeHidden();
}

/** Clicks the accept button of the shell's confirm dialog. */
export async function acceptConfirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog-accept-button').click();
}

/** Toast of the shell (success or error). */
export function toast(page: Page): Locator {
  return page.getByTestId('shell.toast').locator('.p-toast-message').first();
}
