import type { Locator, Page } from '@playwright/test';
import { type ApiClient, expect, expectDataState, seedStore, zurichPickupAt } from '../../fixtures';

/**
 * Shared helpers of the automation specs (M4 F3/F4). Seed (docs/seed-ids.md "Automation", decision 60): S-06 Babu's
 * Bakery has the live pickup policy (gap 2, experience, holiday; team bieb manager + sleepy), Tuesdays 19:00;
 * S-01's policy and adverts are dry run; S-07 request policy dry run (welcome manager, guide applied).
 */
export const S06 = seedStore('S-06');
/**
 * One-time S-06 pickups created by the tests (Friday 6 and Sunday 8 November, inside the 14-day sign-up window and
 * more than two days ahead so the rules apply; sleepy's seeded S-06 pickup is Tuesday 10 November).
 */
export const S06_A = zurichPickupAt(4, '19:00');
export const S06_B = zurichPickupAt(6, '19:00');

/** Creates (or changes) a one-time pickup with two slots as the store manager (`client`). */
export async function createPickup(client: ApiClient, key: string, pickupAt: string): Promise<void> {
  await client.putJson(`/api/stores/${seedStore(key).id}/pickups/${encodeURIComponent(pickupAt)}`, { slotCount: 2 });
}

export async function openAutomation(page: Page, key: string, section: string): Promise<void> {
  await page.goto(`/stores/${seedStore(key).id}/automation/${section}`);
  await expectDataState(page, 'automation.store.state', 'success');
}

/** Sets a p-toggleswitch to `on` (clicks only when needed). */
export async function setSwitch(toggle: Locator, on: boolean): Promise<void> {
  const input = toggle.locator('input');
  if ((await input.isChecked()) !== on) {
    await toggle.click();
  }
  await expect(input).toBeChecked({ checked: on });
}

/** Types into a p-inputnumber like a user (fill() is not picked up by every engine). */
export async function typeNumber(field: Locator, value: number): Promise<void> {
  const input = field.locator('input').first();
  await input.click();
  await input.press('ControlOrMeta+a');
  await input.pressSequentially(String(value));
  await input.blur();
}

export interface AuditDto {
  id: string;
  kind: string;
  status: string;
  dryRun: boolean;
  pickupAt?: string;
  user?: { id: string; displayName: string };
  reasons: { rule: string; text: string }[];
  userMessage?: string;
}

/** Waits until the store has an audit for `pickupAt` (pickup kind) and returns it. */
export async function waitForAudit(client: ApiClient, key: string, pickupAt: string): Promise<AuditDto> {
  let found: AuditDto | undefined;
  await expect
    .poll(
      async () => {
        const page = await client.getJson<{ items: AuditDto[] }>(`/api/stores/${seedStore(key).id}/automation/audits?kind=PICKUP&size=50`);
        found = page.items.find((a) => a.pickupAt === pickupAt);
        return found?.status;
      },
      { timeout: 15_000 },
    )
    .toBeTruthy();
  return found!;
}

/** Saves the pickup policy form and waits for the toast + disabled save button. */
export async function savePolicy(page: Page): Promise<void> {
  await page.getByTestId('automation.policy.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
  await expect(page.getByTestId('automation.policy.save').locator('button')).toBeDisabled();
}
