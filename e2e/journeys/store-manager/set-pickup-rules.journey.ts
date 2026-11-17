import { type ApiClient, expectDataState, seedStore, zurichPickupAt } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { S06, S06_A, S06_B, createPickup, savePolicy, setSwitch, typeNumber } from '../../fixtures/flows/automation';
import { openBlocked, openPickups, pickupRow } from '../../fixtures/flows/pickups';
import { Walk } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-06 Babu's Bakery, whose pickup rules are live: decision 60) sets the
 * sign-up rules on the Automatisierung tab (one switch, no dry run: decision 167): the gap rule alone with three days,
 * shared with Bäckerei Vohdin as a store list (decisions 151, 152), and she tries the store picker, which finds Coop
 * Winterthur Bahnhof in another region but locks "both ways" there (S4). Marco (sleepy@local, in the S-06 team) then
 * sees the rules on the pickups tab, a "Gesperrt" chip with the reason for the Sunday two days after his Friday
 * (S2), his attempt anyway is refused, and Nina finds it as "Gesperrt" in the protocol. The rule permutations are unit
 * tests (PickupPolicyEvaluatorTest, PickupPolicyServiceGapTest, PickupRuleGateImplTest).
 *
 * The second test is the linked store from a member's side (decision 167 S3): Denner Langstrasse (S-04) links Coop
 * Bahnhofbrücke (S-01) one way, the same day or up to two days after. Sarah (foodsaver@local, in both teams) books
 * Saturday at S-04 and the S-01 Monday in the same step; when she signs out of Monday, S-04 says "Verknüpfung fehlt"
 * and she picks Sunday instead. The deadline job and the refusals are unit tests (PickupRuleGateImplTest,
 * PickupPolicyEvaluatorTest).
 */
test.use({ role: 'bieb' });

const S02 = seedStore('S-02');
const S23 = seedStore('S-23');

test('store manager sets the pickup rules, a member is refused with the reason @AU1 @AU2 @PU3 @PU4', async ({
  page,
  api,
  as,
  store,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'store manager');

  await walk.step('opens the store automation', async () => {
    await page.goto(`/stores/${S06.id}/overview`);
    await expect(page.getByTestId('stores.page.name')).toHaveText(S06.name);
    await walk.click(page.getByTestId('stores.page.tab.automation'));
    await expectDataState(page, 'automation.store.state', 'success');
    await expect(page.getByTestId('automation.store.summary.mode')).toHaveAttribute('data-mode', 'ON');
    await expect(page.getByTestId('automation.policy.root')).toHaveAttribute('data-enabled', 'true');
    await expect(page.getByTestId('automation.policy.enabled').locator('input')).toBeChecked();
    await expect(page.getByTestId('automation.policy.enabledHint')).not.toBeEmpty();
    // one switch: no test mode, run or preview buttons any more (decision 167)
    for (const gone of ['dryRun', 'dryRunBanner', 'runDry', 'run', 'runResult', 'preview']) {
      await expect(page.getByTestId(`automation.policy.${gone}`)).toHaveCount(0);
    }
    await checkpoint(page, 'pickup-policy');
  });

  await walk.step('tries the store picker for a linked store', async () => {
    await walk.click(page.getByTestId('automation.policy.addLink'));
    const link = page.getByTestId('automation.policy.link.0');
    await walk.click(link.getByTestId('automation.policy.pickStore').locator('button'));
    const search = page.getByTestId('automation.picker.search');
    await expect(page.getByTestId('automation.picker.state')).toHaveAttribute('data-state', 'idle');
    await walk.fill(search, 'winterthur');
    await expect(page.getByTestId('automation.picker.state')).toHaveAttribute('data-state', 'success');
    const result = page.getByTestId('automation.picker.result').filter({ hasText: S23.name }).first();
    await walk.click(result.getByTestId('automation.picker.select'));
    await checkpoint(page, 'store-picker');
    await walk.click(page.getByTestId('automation.picker.done').locator('button'));
    await expect(search).toBeHidden();
    // Nina does not manage Coop Winterthur Bahnhof: one way only, with the reason
    await expect(page.getByTestId('automation.policy.link.0.stores')).toContainText(S23.name);
    await expect(link.getByTestId('automation.policy.link.0.bidiLocked')).toContainText(S23.name);
    await expect(link.getByTestId('automation.policy.link.0.bidirectional').locator('input')).toBeDisabled();
    await expect(link.getByTestId('automation.policy.link.member')).toContainText(S23.name);
    // the deadline after a sign-out lives in "Erweitert"
    await expect(link.getByTestId('automation.policy.link.0.grace')).toBeHidden();
    await walk.click(link.getByTestId('automation.policy.link.0.advanced').locator('summary'));
    await expect(link.getByTestId('automation.policy.link.0.grace')).toBeVisible();
    await checkpoint(page, 'linked-rule-one-way');
    // not today: she takes the link out again
    await walk.click(link.getByTestId('automation.policy.link.0.remove'));
    await expect(page.getByTestId('automation.policy.link.0')).toHaveCount(0);
  });

  await walk.step('keeps three days between pickups, shared with Bäckerei Vohdin', async () => {
    await setSwitch(page.getByTestId('automation.policy.experience'), false);
    await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
    walk.count(2);
    await typeNumber(page.getByTestId('automation.policy.gapDays'), 3);
    walk.count(1);
    await expectDataState(page, 'automation.policy.gapStores.state', 'success');
    await walk.click(page.getByTestId('automation.policy.gapStores'));
    const overlay = page.locator('.p-multiselect-overlay');
    await walk.click(overlay.getByText(S02.name, { exact: true }));
    await expect(page.locator(`[data-testid="automation.policy.gapMember"][data-store="${S02.id}"]`)).toHaveAttribute('data-locked', 'false');
    // the list closes with a tap outside it (Escape does not reach it on touch)
    await expect(async () => {
      await page.locator('#pp-gap-stores-hint').dispatchEvent('click');
      await expect(overlay).toHaveCount(0, { timeout: 1_000 });
    }).toPass();
    walk.count(1);
    await expect(page.getByTestId('automation.policy.gapNote')).toContainText(S06.name);
    await expect(page.getByTestId('automation.policy.gapStatus')).toHaveAttribute('data-status', 'on');
    // the preview shows the own gap (three blocked days on each side of a pickup) and the group gap
    await expect(page.getByTestId('automation.policy.gapPreview.day')).toHaveCount(11);
    await expect(page.locator('[data-testid="automation.policy.gapPreview.day"][data-result="BLOCKED"]')).toHaveCount(6);
    await expect(page.getByTestId('automation.policy.groupGapPreview.message')).toContainText(S02.name);
    await checkpoint(page, 'gap-rule-with-store-list');
    await savePolicy(page);
    walk.count(1);
    await page.reload();
    await expectDataState(page, 'automation.store.state', 'success');
    await expect(page.getByTestId('automation.policy.gapDays').locator('input')).toHaveValue('3');
    await expect(page.locator(`[data-testid="automation.policy.gapMember"][data-store="${S02.id}"]`)).toBeVisible();
  });

  // two one-time pickups (Friday and Sunday) and Marco signed up for Friday, which the rules confirm
  await createPickup(api, 'S-06', S06_A);
  await createPickup(api, 'S-06', S06_B);
  await store.signUp('sleepy', 'S-06', S06_A);

  await test.step('Marco sees the rules and the locked Sunday with the reason', async () => {
    const sleepy = await as('sleepy');
    await openPickups(sleepy, 'S-06');
    const card = sleepy.getByTestId('stores.pickups.rules');
    await expect(card).toHaveAttribute('data-state', 'success');
    await expect(card).not.toHaveAttribute('open', '');
    await sleepy.getByTestId('stores.pickups.rules.toggle').click();
    await expect(card.locator('[data-testid="stores.pickups.rules.line"][data-rule="GAP"]')).toContainText('3');
    await expect(card.locator('[data-testid="stores.pickups.rules.line"][data-rule="EXPERIENCE"]')).toHaveCount(0);
    // Sunday is one day from Friday: a lock chip instead of "+ Eintragen"
    const sunday = pickupRow(sleepy, S06_B);
    await expect(sunday.getByTestId('stores.pickups.slot.free')).toHaveCount(0);
    const chip = sunday.getByTestId('stores.pickups.slot.blocked');
    await expect(chip).toHaveAttribute('aria-haspopup', 'dialog');
    await expect(chip).toHaveAttribute('data-rules', '1');
    await openBlocked(sleepy, S06_B);
    // his own dates: the close pickup on Friday 6.11. and the next possible day, Saturday 14.11.
    const reason = sleepy.locator('[data-testid="pickups.blocked.reason"][data-rule="GAP"]');
    await expect(reason).toHaveCount(1);
    await expect(reason).toContainText('06.11.2026');
    await expect(reason).toContainText('14.11.2026');
    await expect(reason).not.toContainText('pickups.blocked');
    await checkpoint(sleepy, 'blocked-reason');
    await sleepy.getByTestId('pickups.blocked.action').click();
    await expect(sleepy.getByTestId('pickups.blocked.dialog')).toBeHidden();
    // the server refuses the sign-up anyway (409 AUTOMATION_RULE) and stores nothing
    expect(await store.trySignUp('sleepy', 'S-06', S06_B)).toBe(false);
  });

  await walk.step('finds the refusal in the protocol', async () => {
    await walk.click(page.getByTestId('automation.store.nav.audits'));
    await expectDataState(page, 'automation.audits.state', 'success');
    const row = page.locator('[data-testid="automation.audits.row"][data-status="DECLINED"]').first();
    await expect(row.getByTestId('automation.audits.status')).toHaveText('Gesperrt');
    await walk.click(row.getByTestId('automation.audits.toggle'));
    await expect(row.locator('[data-testid="automation.audits.reason"][data-rule="GAP"]')).toBeVisible();
    await checkpoint(page, 'protocol');
  });

  walk.finish({ steps: 4, clicks: 16, fields: 1 });
});

const S01 = seedStore('S-01');
const S04 = seedStore('S-04');
/** S-04 Saturday 7.11. 19:30; S-01 has Sunday 8.11. 10:00 (extra) and Monday 9.11. 19:00 with one place (seed). */
const SATURDAY = zurichPickupAt(5, '19:30');
const SUNDAY = zurichPickupAt(6, '10:00');
const MONDAY = zurichPickupAt(7, '19:00');

/** ORGA links S-04 to S-01 (one way, 0–2 days after, 120 minutes after a sign-out); both leave the region rule. */
async function linkS04ToS01(orga: ApiClient): Promise<void> {
  for (const key of ['S-01', 'S-04']) {
    await orga.patchJson(`/api/stores/${seedStore(key).id}`, { useRegionPickupRule: false });
  }
  const current = await orga.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S04.id}/automation`);
  await orga.putJson(`/api/stores/${S04.id}/automation/pickup-policy`, {
    ...current.pickupPolicy,
    enabled: true,
    gapRuleEnabled: false,
    experienceRuleEnabled: false,
    cleaningRuleEnabled: false,
    publicHolidayRuleEnabled: false,
    linkedStores: [
      { storeIds: [S01.id], bidirectional: false, windowMode: 'LAST_DAYS', windowDaysBefore: 0, windowDaysAfter: 2, graceMinutes: 120 },
    ],
  });
}

test.describe('member', () => {
  test.use({ role: 'foodsaver' });

  test('member books the linked store in the same step and repairs a missing link @AU1 @PU3', async ({
    page,
    api,
    as,
    apiOf,
    store,
    notifications,
    profiles,
  }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'foodsaver');
    await linkS04ToS01(apiOf(await as('orga')));
    const mine = async (key: string, at: string) =>
      (await store.pickupsOf(api, key)).find((p) => p.pickupAt === at)?.occupiedSlots.some((slot) => slot.user.displayName === 'Sarah Müller') ?? false;

    await walk.step('books Saturday together with a Coop pickup', async () => {
      await openPickups(page, 'S-04');
      await walk.click(page.getByTestId('stores.pickups.rules.toggle'));
      await expect(page.locator('[data-testid="stores.pickups.rules.line"][data-rule="LINKED_STORE"]')).toBeVisible();
      // only the linked pickup is missing: a free place, not a lock
      const row = pickupRow(page, SATURDAY);
      await row.scrollIntoViewIfNeeded();
      await expect(row.getByTestId('stores.pickups.slot.blocked')).toHaveCount(0);
      await walk.click(row.getByTestId('stores.pickups.slot.free').first());
      await expectDataState(page, 'pickups.signup.linked.state', 'success');
      const step = page.getByTestId('pickups.signup.linked.step');
      await expect(step).toHaveAttribute('data-step', '2');
      await expect(step).toHaveAttribute('data-total', '2');
      await expect(page.getByTestId('pickups.signup.confirm')).toHaveCount(0);
      const confirm = page.getByTestId('pickups.signup.linked.confirm').locator('button');
      await expect(confirm).toBeDisabled();
      await expect(page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${SUNDAY}"]`)).toBeVisible();
      await walk.check(page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${MONDAY}"] input`));
      await expect(confirm).toBeEnabled();
      await checkpoint(page, 'linked-step');
      await walk.click(confirm);
      await expect(page.getByTestId('pickups.signup.linked.confirm')).toHaveCount(0);
      expect(await mine('S-04', SATURDAY)).toBe(true);
      expect(await mine('S-01', MONDAY)).toBe(true);
    });

    // Sarah signs out of the Coop Monday (her own pickups list is the foodsaver journeys' part)
    const me = await profiles.userId('foodsaver');
    const out = await api.fetch('DELETE', `/api/stores/${S01.id}/pickups/${encodeURIComponent(MONDAY)}/signups/${me}`);
    expect(out.ok(), 'sign out of the S-01 Monday').toBe(true);
    await expect.poll(async () => (await notifications.bells('foodsaver', 'PICKUP_LINK_MISSING')).length).toBe(1);

    await walk.step('picks Sunday for the missing link', async () => {
      await openPickups(page, 'S-04');
      const pill = pickupRow(page, SATURDAY).getByTestId('stores.pickups.linkMissing');
      await pill.scrollIntoViewIfNeeded();
      await expect(pill).toHaveAttribute('aria-haspopup', 'dialog');
      await walk.click(pill);
      await expect(page.getByTestId('pickups.signup.linked.repair')).toBeVisible();
      await expectDataState(page, 'pickups.signup.linked.state', 'success');
      await walk.check(page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${SUNDAY}"] input`));
      await checkpoint(page, 'link-repair');
      await walk.click(page.getByTestId('pickups.signup.linked.confirm').locator('button'));
      await expect(page.getByTestId('pickups.signup.linked.confirm')).toHaveCount(0);
      await expect(pickupRow(page, SATURDAY).getByTestId('stores.pickups.linkMissing')).toHaveCount(0);
      expect(await mine('S-01', SUNDAY)).toBe(true);
    });

    walk.finish({ steps: 2, clicks: 7, fields: 0 });
  });
});
