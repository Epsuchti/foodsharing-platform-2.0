import { expect, expectStatus, test, zurichPickupAt } from '../../fixtures';
import { openPickups, openSignup, reasonsOf } from './_pickups';

/**
 * PU5 region pickup rule (Zürich: 2 pickups per 7 days, used by S-01 and S-04). Seed: foodsaver has S-04 Wednesday
 * 18:30 and S-01 Thursday 19:00 plus two past pickups in the last 7 days; S-02/S-07 do not use the rule.
 */

test.use({ seedReset: 'test', role: 'foodsaver' });

const S04_SATURDAY = zurichPickupAt(5, '19:30');

test('PU5 third pickup within 7 days refused; allowed within 24 h of pickup @PU5', async ({ page, api, clock }) => {
  await openPickups(page, 'S-04');
  await openSignup(page, S04_SATURDAY);
  await expect(page.locator('[data-testid="shared.eligibility.reason"][data-reason="REGION_RULE_TIMESPAN_LIMIT"]')).toBeVisible();
  await expect(page.getByTestId('shared.eligibility.rule')).toBeVisible();
  await expect(page.getByTestId('pickups.signup.confirm')).toHaveCount(0);

  // on Saturday morning (< 24 h before the pickup) the rule no longer applies
  await clock.fixServer('2026-11-07T07:00:00Z');
  try {
    expect(await reasonsOf(api, 'S-04', S04_SATURDAY)).not.toContain('REGION_RULE_TIMESPAN_LIMIT');
  } finally {
    await clock.fixServer();
  }
});

test('PU5 second pickup same day refused @PU5', async ({ api, as, apiOf, store }) => {
  // welcome (manager of S-04) adds a one-time pickup on Thursday, the day of foodsaver's S-01 pickup
  const thursdayEvening = zurichPickupAt(3, '21:00');
  const welcome = apiOf(await as('welcome'));
  await expectStatus(
    await welcome.put(`/api/stores/${store.id('S-04')}/pickups/${encodeURIComponent(thursdayEvening)}`, { slotCount: 2 }),
    [200, 204],
    'add one-time pickup',
  );
  expect(await reasonsOf(api, 'S-04', thursdayEvening)).toContain('REGION_RULE_DAY_LIMIT');
});

test('PU5 store without rule ignores it @PU5', async ({ api, store }) => {
  // S-07 does not use the region rule: a sign-up despite two pickups in the last week is fine
  const response = await api.post(`/api/stores/${store.id('S-07')}/pickups/${encodeURIComponent(zurichPickupAt(2, '20:30'))}/signups`);
  await expectStatus(response, [200, 201], 'sign up at S-07');
});
