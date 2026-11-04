import type { Locator, Page } from '@playwright/test';
import { type ApiClient, expect, expectDataState, seedStore, test } from '../../fixtures';
import { type AuditDto, S06, S06_A, S06_B, createPickup, openAutomation, savePolicy, setSwitch, typeNumber } from './_automation';

/**
 * AU1 gap rule with a shared store list (decisions 151, 152). Seed: S-01 (own gap 2, rule on) and S-03 (own rule off)
 * share a group gap of 2 days; S-03's policy is off ("Keine Automatisierung"). bieb may automate S-01, S-02, S-03, S-05
 * and S-06. The own gap checks pickups in the store only, the group gap pickups in all stores of the group.
 */
test.use({ role: 'bieb', seedReset: 'test' });

const S02 = seedStore('S-02');
const S03 = seedStore('S-03');
const S05 = seedStore('S-05');

/**
 * Picks a store in the gap list picker and closes the list with a click outside it (the overlay closes on any document
 * click outside itself). Escape does not close it on touch, and depending on the viewport the list covers the field or
 * the hint, so the click is dispatched on the hint instead of at screen coordinates.
 */
async function pickGapStore(page: Page, name: string): Promise<void> {
  await page.getByTestId('automation.policy.gapStores').click();
  const overlay = page.locator('.p-multiselect-overlay');
  await overlay.getByText(name, { exact: true }).click();
  await expect(page.locator(`[data-testid="automation.policy.gapMember"]`).filter({ hasText: name })).toHaveCount(1);
  // the overlay may still be animating in when the first outside click arrives
  await expect(async () => {
    await page.locator('#pp-gap-stores-hint').dispatchEvent('click');
    await expect(overlay).toHaveCount(0, { timeout: 1_000 });
  }).toPass();
}

/** The seed has no manager of S-01 without rights on S-03: the saved list is answered with S-03 locked. */
async function lockListedStores(page: Page): Promise<void> {
  await page.route(/\/api\/stores\/[^/]+\/automation$/, async (route) => {
    const response = await route.fetch();
    const json = await response.json();
    for (const gap of json.pickupPolicy?.gapStores ?? []) gap.mayEdit = false;
    await route.fulfill({ response, json });
  });
}

function member(page: Page, storeId: string): Locator {
  return page.locator(`[data-testid="automation.policy.gapMember"][data-store="${storeId}"]`);
}

interface SignUpRunner {
  signUp(role: 'jumper' | 'sleepy', key: string, pickupAt: string): Promise<unknown>;
  withRole<T>(role: 'jumper' | 'sleepy', action: (client: ApiClient) => Promise<T>): Promise<T>;
}

/** bieb adds jumper (who has the seeded S-02 pickup on Saturday) to S-06's team, so jumper can sign up there. */
async function jumperJoinsS06(api: ApiClient, store: SignUpRunner): Promise<void> {
  const jumper = (await store.withRole('jumper', (c) => c.getJson<{ user: { id: string } }>('/api/auth/session'))).user.id;
  expect((await api.post(`/api/stores/${S06.id}/team/${jumper}`)).ok()).toBe(true);
}

/**
 * Writes S-06's policy through the API: rules on (no dry run or preview any more, decision 165), only the gap rules,
 * with this own gap and group.
 */
async function putS06(
  api: ApiClient,
  own: { on: boolean; gap: number },
  group: { gap: number; storeIds: string[] },
  enabled = true,
): Promise<void> {
  const current = await api.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06.id}/automation`);
  const policy = { ...current.pickupPolicy };
  delete policy['gapStores'];
  await api.putJson(`/api/stores/${S06.id}/automation/pickup-policy`, {
    ...policy,
    enabled,
    experienceRuleEnabled: false,
    cleaningRuleEnabled: false,
    publicHolidayRuleEnabled: false,
    gapRuleEnabled: own.on,
    minimumGapDays: own.gap,
    gapStoreIds: group.storeIds,
    groupMinimumGapDays: group.gap,
  });
}

/** The decision of the last signUpGapReason call. */
let lastDecision: AuditDto | undefined;

async function pickupAudits(api: ApiClient, pickupAt: string): Promise<AuditDto[]> {
  const page = await api.getJson<{ items: AuditDto[] }>(`/api/stores/${S06.id}/automation/audits?kind=PICKUP&size=100`);
  return page.items.filter((a) => a.pickupAt === pickupAt);
}

/**
 * `role` signs up for `pickupAt` at S-06; returns the GAP reason of the new decision (declined: the sign-up is removed
 * again), or undefined when it passed (confirmed).
 */
async function signUpGapReason(api: ApiClient, store: SignUpRunner, role: 'jumper' | 'sleepy', pickupAt: string): Promise<string | undefined> {
  const before = new Set((await pickupAudits(api, pickupAt)).map((a) => a.id));
  await store.signUp(role, 'S-06', pickupAt);
  // the e2e clock is fixed, so audits cannot be told apart by time: take the one that is new
  let decision: AuditDto | undefined;
  await expect
    .poll(
      async () => {
        decision = (await pickupAudits(api, pickupAt)).find((a) => !before.has(a.id));
        return decision?.status;
      },
      { timeout: 15_000 },
    )
    .toBeTruthy();
  lastDecision = decision!;
  return decision!.reasons.find((r) => r.rule === 'GAP')?.text;
}

test('AU1 gap list: saving writes only the group gap to the listed stores and a pickup there declines @AU1 @page', async ({ page, api, store }) => {
  await createPickup(api, 'S-06', S06_A);
  await openAutomation(page, 'S-06', 'policy');
  // only the gap rules decide the sign-up below
  await setSwitch(page.getByTestId('automation.policy.experience'), false);
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
  await expectDataState(page, 'automation.policy.gapStores.state', 'success');
  await pickGapStore(page, S02.name);
  await pickGapStore(page, S05.name);
  await expect(member(page, S02.id)).toHaveAttribute('data-locked', 'false');
  // the group gap starts from the own gap (2) and is then set apart from it
  await expect(page.getByTestId('automation.policy.groupGapDays').locator('input')).toHaveValue('2');
  await typeNumber(page.getByTestId('automation.policy.groupGapDays'), 1);
  await expect(page.getByTestId('automation.policy.gapNote')).toContainText(S06.name);
  await expect(page.getByTestId('automation.policy.gapNote')).toContainText(S05.name);
  await expect(page.getByTestId('automation.policy.gapStatus')).toHaveAttribute('data-status', 'on');
  // own preview (own gap 2, single-store text) and group preview (group gap 1, group text)
  await expect(page.getByTestId('automation.policy.gapPreview.day')).toHaveCount(11);
  await expect(page.locator('[data-testid="automation.policy.gapPreview.day"][data-result="BLOCKED"]')).toHaveCount(4);
  await expect(page.getByTestId('automation.policy.gapPreview.message')).not.toContainText(S02.name);
  await expect(page.locator('[data-testid="automation.policy.groupGapPreview.day"][data-result="PICKUP"]')).toHaveCount(1);
  await expect(page.locator('[data-testid="automation.policy.groupGapPreview.day"][data-result="BLOCKED"]')).toHaveCount(2);
  await expect(page.getByTestId('automation.policy.groupGapPreview.message')).toContainText(S02.name);
  await savePolicy(page);

  // the other stores show the same list and group gap; S-05 had no policy: its own rule stays off
  await openAutomation(page, 'S-05', 'policy');
  await expect(page.getByTestId('automation.policy.gapEnabled').locator('input')).not.toBeChecked();
  await expect(member(page, S06.id)).toBeVisible();
  await expect(member(page, S02.id)).toBeVisible();
  await expect(page.getByTestId('automation.policy.groupGapDays').locator('input')).toHaveValue('1');
  await expect(page.getByTestId('automation.policy.gapStatus')).toHaveAttribute('data-status', 'on');

  // jumper's S-02 pickup on Saturday blocks an S-06 sign-up on Friday (D4), with the group text
  await jumperJoinsS06(api, store);
  const gap = await signUpGapReason(api, store, 'jumper', S06_A);
  expect(lastDecision?.status).toBe('DECLINED');
  expect(gap).toContain(S02.name);
});

test('AU1 gap list: switching the own rule off keeps the group; the leave action empties the list @AU1', async ({ page, api }) => {
  await lockListedStores(page);
  await openAutomation(page, 'S-01', 'policy');
  const locked = member(page, S03.id);
  await expect(locked).toHaveAttribute('data-locked', 'true');
  await expect(locked).toHaveAttribute('data-automation', 'false');
  await expect(locked.getByTestId('automation.policy.gapMember.remove')).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.gapLockedLeave')).toBeVisible();
  // a locked member locks the group gap, never the own gap
  await expect(page.getByTestId('automation.policy.gapDays').locator('input')).toBeEnabled();
  await expect(page.getByTestId('automation.policy.groupGapDays').locator('input')).toBeDisabled();
  await expect(page.locator('[data-testid="automation.policy.gapWarning"][data-kind="NO_AUTOMATION"]')).toContainText(S03.name);
  await expect(page.getByTestId('automation.policy.gapLeave')).toBeVisible();

  // decision 152: the own rule off keeps the group (group check and group preview stay)
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), false);
  await expect(page.getByTestId('automation.policy.gapNote')).toBeVisible();
  await expect(page.getByTestId('automation.policy.gapPreview')).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.groupGapPreview')).toBeVisible();
  await expect(page.getByTestId('automation.policy.gapStatus')).toHaveAttribute('data-status', 'on');
  await savePolicy(page);
  const s01 = await api.getJson<{ pickupPolicy: { gapStoreIds: string[]; gapRuleEnabled: boolean; groupMinimumGapDays?: number } }>(
    `/api/stores/${seedStore('S-01').id}/automation`,
  );
  expect(s01.pickupPolicy.gapStoreIds).toEqual([S03.id]);
  expect(s01.pickupPolicy.groupMinimumGapDays).toBe(2);
  expect(s01.pickupPolicy.gapRuleEnabled).toBe(false);

  // leaving is an explicit action: it empties the list, saving sends the empty list
  await page.getByTestId('automation.policy.gapLeave').locator('button').click();
  await expect(page.getByTestId('automation.policy.gapMember')).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.gapLeave')).toHaveCount(0);
  await expect(page.getByTestId('automation.policy.gapStatus')).toHaveAttribute('data-status', 'off');
  await savePolicy(page);
  const left = await api.getJson<{ pickupPolicy: { gapStoreIds: string[] } }>(`/api/stores/${seedStore('S-01').id}/automation`);
  expect(left.pickupPolicy.gapStoreIds).toEqual([]);
});

test('AU1 gap list: own rule off with the group on declines a pickup in another store @AU1', async ({ api, store }) => {
  await putS06(api, { on: false, gap: 0 }, { gap: 1, storeIds: [S02.id] });
  await createPickup(api, 'S-06', S06_A);
  await jumperJoinsS06(api, store);
  // jumper's S-02 pickup on Saturday, the S-06 sign-up on Friday: 0 days in between, group gap 1
  const text = await signUpGapReason(api, store, 'jumper', S06_A);
  expect(text).toContain(S02.name);
  expect(text).toContain(S06.name);
});

test('AU1 gap list: own gap and group gap with different numbers decide independently @AU1', async ({ api, store }) => {
  await createPickup(api, 'S-06', S06_A);
  await createPickup(api, 'S-06', S06_B);
  // sleepy: Friday here (and the seeded Tuesday pickup), signed up while the rules are off; Sunday is 1 day from each
  await putS06(api, { on: true, gap: 3 }, { gap: 1, storeIds: [S02.id] }, false);
  await store.signUp('sleepy', 'S-06', S06_A);
  await putS06(api, { on: true, gap: 3 }, { gap: 1, storeIds: [S02.id] });

  // own 3 / group 1: only the own check fails, with the single-store text (no store names)
  const own = await signUpGapReason(api, store, 'sleepy', S06_B);
  expect(own).toBeTruthy();
  expect(own).not.toContain(S06.name);
  expect(own).not.toContain('\n');

  // own 1 / group 3: only the group check fails, with the group text naming the stores
  await putS06(api, { on: true, gap: 1 }, { gap: 3, storeIds: [S02.id] });
  const group = await signUpGapReason(api, store, 'sleepy', S06_B);
  expect(group).toContain(S06.name);
  expect(group).toContain(S02.name);
  expect(group).not.toContain('\n');

  // own 1 / group 1: both pass
  await putS06(api, { on: true, gap: 1 }, { gap: 1, storeIds: [S02.id] });
  expect(await signUpGapReason(api, store, 'sleepy', S06_B)).toBeUndefined();
  expect(lastDecision?.status).toBe('CONFIRMED');
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
