import { type ApiClient, SEED_NOW, expect, expectDataState, seedStore, test, zurichPickupAt } from '../../fixtures';
import { openPickups, pickupRow } from '../pickups/_pickups';

/**
 * Decision 167 S3: the linked store is booked in the same step. S-04 (manager welcome) links S-01 one-way; ORGA sets the
 * rule to "the same day or up to 2 days after" and switches S-04's rules on, and both stores leave the Zürich pickup rule
 * (2 per 7 days) so foodsaver's seeded pickups do not interfere. foodsaver is in both teams: S-04 Saturday 7.11. 19:30
 * needs an S-01 pickup between Saturday and Monday (seed: Sunday 8.11. 10:00 extra, Monday 9.11. 19:00 with one place).
 * Signing out of the S-01 pickup leaves S-04 "Verknüpfung fehlt" with a bell; a new S-01 pickup repairs it; after the
 * deadline (120 minutes) the evaluate job frees the S-04 place.
 */
test.use({ seedReset: 'test', role: 'foodsaver' });

const S01 = seedStore('S-01');
const S04 = seedStore('S-04');
const X = zurichPickupAt(5, '19:30');
const SUNDAY = zurichPickupAt(6, '10:00');
const MONDAY = zurichPickupAt(7, '19:00');

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

const mine = (pickups: { pickupAt: string; occupiedSlots: { user: { displayName: string } }[] }[], at: string) =>
  pickups.find((p) => p.pickupAt === at)?.occupiedSlots.some((slot) => slot.user.displayName === 'Sarah Müller') ?? false;

test('AU1 the linked store is booked in the same step: choose, both stored together @AU1 @PU3', async ({ page, as, apiOf, store, api }) => {
  await linkS04ToS01(apiOf(await as('orga')));
  await openPickups(page, 'S-04');
  // the rule card says the linked pickup is chosen right away
  await page.getByTestId('stores.pickups.rules.toggle').click();
  await expect(page.locator('[data-testid="stores.pickups.rules.line"][data-rule="LINKED_STORE"]')).toBeVisible();
  // only the linked pickup is missing: a free place, not a lock
  const row = pickupRow(page, X);
  await expect(row.getByTestId('stores.pickups.slot.blocked')).toHaveCount(0);
  await row.getByTestId('stores.pickups.slot.free').first().click();
  await expectDataState(page, 'pickups.signup.linked.state', 'success');
  const step = page.getByTestId('pickups.signup.linked.step');
  await expect(step).toHaveAttribute('data-step', '2');
  await expect(step).toHaveAttribute('data-total', '2');
  await expect(page.getByTestId('pickups.signup.confirm')).toHaveCount(0);
  const confirm = page.getByTestId('pickups.signup.linked.confirm').locator('button');
  await expect(confirm).toBeDisabled();
  await expect(page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${SUNDAY}"]`)).toBeVisible();
  await page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${MONDAY}"] input`).check();
  await expect(confirm).toBeEnabled();
  await confirm.click();
  await expect(page.getByTestId('pickups.signup.linked.confirm')).toHaveCount(0);
  expect(mine(await store.pickupsOf(api, 'S-04'), X)).toBe(true);
  expect(mine(await store.pickupsOf(api, 'S-01'), MONDAY)).toBe(true);

  // a plain sign-up without the linked pickup is refused (and stores nothing)
  const refused = await api.post(`/api/stores/${S04.id}/pickups/${encodeURIComponent(zurichPickupAt(9, '18:30'))}/signups`);
  expect(refused.status()).toBe(409);
  expect(((await refused.json()) as { code: string }).code).toBe('LINKED_PICKUP_REQUIRED');
});

test('AU1 the S-01 sign-out leaves "Verknüpfung fehlt" with a bell; repaired, then freed after the deadline @AU1 @PU3', async ({
  page,
  as,
  apiOf,
  store,
  api,
  clock,
  notifications,
  profiles,
}) => {
  await linkS04ToS01(apiOf(await as('orga')));
  const both = await api.post(`/api/stores/${S04.id}/pickups/${encodeURIComponent(X)}/signups`, {
    linkedPickups: [{ storeId: S01.id, pickupAt: MONDAY }],
  });
  expect(both.status()).toBe(200);
  const me = await profiles.userId('foodsaver');
  const signOut = async (at: string) => {
    const response = await api.fetch('DELETE', `/api/stores/${S01.id}/pickups/${encodeURIComponent(at)}/signups/${me}`);
    expect(response.ok(), `sign out of S-01 ${at}`).toBe(true);
  };
  await signOut(MONDAY);
  await expect.poll(async () => (await notifications.bells('foodsaver', 'PICKUP_LINK_MISSING')).length).toBe(1);

  // the own slot says until when and opens the same dialog to choose a new S-01 pickup
  await openPickups(page, 'S-04');
  const pill = pickupRow(page, X).getByTestId('stores.pickups.linkMissing');
  await expect(pill).toBeVisible();
  await expect(pill).toHaveAttribute('aria-haspopup', 'dialog');
  await pill.click();
  await expect(page.getByTestId('pickups.signup.linked.repair')).toBeVisible();
  await expectDataState(page, 'pickups.signup.linked.state', 'success');
  await page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${SUNDAY}"] input`).check();
  await page.getByTestId('pickups.signup.linked.confirm').locator('button').click();
  await expect(page.getByTestId('pickups.signup.linked.confirm')).toHaveCount(0);
  await expect(pickupRow(page, X).getByTestId('stores.pickups.linkMissing')).toHaveCount(0);
  expect(mine(await store.pickupsOf(api, 'S-01'), SUNDAY)).toBe(true);

  // signed out again: within the deadline the job keeps the place, afterwards it frees it
  await signOut(SUNDAY);
  await notifications.runJob('automation-evaluate');
  expect(mine(await store.pickupsOf(api, 'S-04'), X)).toBe(true);
  await clock.fixServer(new Date(Date.parse(SEED_NOW) + 3 * 3_600_000).toISOString());
  await notifications.runJob('automation-evaluate');
  expect(mine(await store.pickupsOf(api, 'S-04'), X)).toBe(false);
  await expect.poll(async () => (await notifications.bells('foodsaver', 'PICKUP_AUTO_DECLINED')).length).toBe(1);
});
