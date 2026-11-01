import type { Locator, Page } from '@playwright/test';
import { expect, expectDataState, seedStore, test } from '../../fixtures';
import { S06, S06_A, createPickup, openAutomation, savePolicy, setSwitch } from './_automation';

/**
 * AU1 gap rule with a shared store list (decision 151). Seed: S-01 and S-03 share a gap of 2 days; S-03's policy is
 * off ("Keine Automatisierung") and bieb may not automate S-03 (locked member). bieb manages S-01, S-02, S-05, S-06.
 */
test.use({ role: 'bieb', seedReset: 'test' });

const S02 = seedStore('S-02');
const S03 = seedStore('S-03');
const S05 = seedStore('S-05');

async function pickGapStore(page: Page, name: string): Promise<void> {
  const picker = page.getByTestId('automation.policy.gapStores');
  await picker.click();
  await page.locator('.p-multiselect-overlay').getByText(name, { exact: true }).click();
  await picker.locator('.p-multiselect-dropdown').click();
  await expect(page.locator('.p-multiselect-overlay')).toHaveCount(0);
}

function member(page: Page, storeId: string): Locator {
  return page.locator(`[data-testid="automation.policy.gapMember"][data-store="${storeId}"]`);
}

test('AU1 gap list: saving writes the shared gap to the listed stores and a pickup there declines @AU1 @page', async ({ page, api, store }) => {
  await createPickup(api, 'S-06', S06_A);
  await openAutomation(page, 'S-06', 'policy');
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
  await expectDataState(page, 'automation.policy.gapStores.state', 'success');
  await pickGapStore(page, S02.name);
  await pickGapStore(page, S05.name);
  await expect(member(page, S02.id)).toHaveAttribute('data-locked', 'false');
  await expect(page.getByTestId('automation.policy.gapNote')).toContainText(S06.name);
  await expect(page.getByTestId('automation.policy.gapNote')).toContainText(S05.name);
  await expect(page.getByTestId('automation.policy.gapPreview.day')).toHaveCount(11);
  await expect(page.locator('[data-testid="automation.policy.gapPreview.day"][data-result="PICKUP"]')).toHaveCount(1);
  await expect(page.getByTestId('automation.policy.gapPreview.message')).toContainText(S02.name);
  await savePolicy(page);

  // the other stores show the same list, gap and switch
  await openAutomation(page, 'S-05', 'policy');
  await expect(page.getByTestId('automation.policy.gapEnabled').locator('input')).toBeChecked();
  await expect(member(page, S06.id)).toBeVisible();
  await expect(member(page, S02.id)).toBeVisible();
  await expect(page.getByTestId('automation.policy.gapDays').locator('input')).toHaveValue('2');

  // jumper's S-02 pickup on Saturday blocks an S-06 sign-up on Friday (D4), with the group text
  const jumper = await store.withRole('jumper', (c) => c.getJson<{ user: { id: string } }>('/api/auth/session'));
  const preview = await api.postJson<{ decision: string; reasons: { rule: string; text: string }[] }>(
    `/api/stores/${S06.id}/automation/pickup-policy/preview`,
    { userId: jumper.user.id, pickupAt: S06_A },
  );
  expect(preview.decision).toBe('WOULD_DECLINE');
  const gap = preview.reasons.find((r) => r.rule === 'GAP')!;
  expect(gap.text).toContain(S02.name);
});

test('AU1 gap list: a locked member cannot be removed; switching the rule off leaves the list @AU1', async ({ page, api }) => {
  await openAutomation(page, 'S-01', 'policy');
  const locked = member(page, S03.id);
  await expect(locked).toHaveAttribute('data-locked', 'true');
  await expect(locked).toHaveAttribute('data-automation', 'false');
  await expect(locked.getByTestId('automation.policy.gapMember.remove')).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.gapLockedLeave')).toBeVisible();
  await expect(page.getByTestId('automation.policy.gapDays').locator('input')).toBeDisabled();
  await expect(page.locator('[data-testid="automation.policy.gapWarning"][data-kind="NO_AUTOMATION"]')).toContainText(S03.name);

  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), false);
  await expect(page.getByTestId('automation.policy.gapNote')).toHaveCount(0);
  await savePolicy(page);
  const s01 = await api.getJson<{ pickupPolicy: { gapStoreIds: string[]; gapRuleEnabled: boolean } }>(`/api/stores/${seedStore('S-01').id}/automation`);
  expect(s01.pickupPolicy.gapStoreIds).toEqual([]);
  expect(s01.pickupPolicy.gapRuleEnabled).toBe(false);
});

test('AU1 gap list: a same-day link to a listed store blocks save, in both cards @AU1', async ({ page }) => {
  await openAutomation(page, 'S-06', 'policy');
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
  await page.getByTestId('automation.policy.addLink').click();
  const stores = page.getByTestId('automation.policy.link.0.stores');
  await stores.click();
  await page.locator('.p-multiselect-overlay').getByText(S05.name, { exact: true }).click();
  await stores.locator('.p-multiselect-dropdown').click();
  await expect(page.locator('.p-multiselect-overlay')).toHaveCount(0);

  await expectDataState(page, 'automation.policy.gapStores.state', 'success');
  await pickGapStore(page, S05.name);
  const conflict = page.locator('[data-testid="automation.policy.gapWarning"][data-kind="CONFLICT"]');
  await expect(conflict).toHaveAttribute('data-store', S05.id);
  await expect(conflict).toContainText(S05.name);
  await expect(page.locator('[data-testid="automation.policy.link.0.gapWarning"][data-kind="CONFLICT"]')).toBeVisible();
  await expect(page.getByTestId('automation.policy.save').locator('button')).toBeDisabled();

  // removing the store from the list lifts the block
  await member(page, S05.id).getByTestId('automation.policy.gapMember.remove').click();
  await expect(conflict).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.save').locator('button')).toBeEnabled();
});
