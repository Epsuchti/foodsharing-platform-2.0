import type { Locator, Page } from '@playwright/test';
import { type ApiClient, expect, seedRegion } from '../../fixtures';

/** Zürich Stadt (bot is ambassador, welcome is FSMANAGEMENT admin; docs/seed-ids.md). */
export const ZH = seedRegion('zuerich-stadt').id;

/** Seed user ids `00000001-0001-4000-8000-0000000000NN` (docs/seed-ids.md). */
export const USERS = {
  pending: '00000001-0001-4000-8000-000000000005',
  foodsaver: '00000001-0001-4000-8000-000000000006',
  jumper: '00000001-0001-4000-8000-000000000007',
  sleepy: '00000001-0001-4000-8000-000000000008',
  bot: '00000001-0001-4000-8000-000000000011',
} as const;

export const VERIFY_MESSAGE = 'Willkommen im Team, Ausweis geprüft!';

/** The queue card of one user. */
export function queueCard(page: Page, userId: string): Locator {
  return page.locator(`[data-testid="verification.queue.card"][data-user="${userId}"]`);
}

/** Opens the verification queue of Zürich and waits for it. */
export async function openQueue(page: Page, filter: 'READY' | 'ALL_UNVERIFIED' = 'READY'): Promise<void> {
  await page.goto(`/regions/${ZH}/verification`);
  const state = page.getByTestId('verification.queue.state');
  await expect(state).toHaveAttribute('data-state', /success|empty/);
  if (filter === 'ALL_UNVERIFIED') {
    await page.getByTestId('verification.queue.filter').locator('.p-togglebutton').nth(1).click();
    await expect(state).toHaveAttribute('data-state', /success|empty/);
  }
}

/** Fills and confirms a required-message dialog (`verification.verifyDialog` / `verification.unverifyDialog`). */
export async function confirmMessage(page: Page, dialog: string, message: string): Promise<void> {
  const field = page.getByTestId(`${dialog}.message`);
  await field.fill(message);
  await page.getByTestId(`${dialog}.confirm`).click();
  await expect(field).toBeHidden();
}

/** Verifies a user through the API as the given ambassador client. */
export async function verifyViaApi(ambassador: ApiClient, userId: string, message = VERIFY_MESSAGE): Promise<void> {
  await ambassador.postJson(`/api/users/${userId}/verification`, { message });
}
