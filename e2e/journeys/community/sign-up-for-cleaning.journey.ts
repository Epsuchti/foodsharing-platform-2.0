import { FSPS, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Cleaning shifts of the Fairteiler Wiedikon (decision 148, 154): Saturdays 10:00, managed by Nina (bieb@local).
 * Sarah (foodsaver@local) signs up for the first shift on the Fairteiler page, finds it on her dashboard, in "My
 * pickups" and in the day agenda, is told by bell when Nina confirms it (her own session) and finally signs out from
 * "My pickups". Who may sign up and out, free places and the confirmation rules are unit-tested (FspCleaningRulesTest,
 * cleaning.util.spec.ts, fsp-cleaning-card.component.spec.ts); the calendar entries too (CalendarFeedServiceTest).
 */
test.use({ role: 'foodsaver' });

/** Saturday 2026-11-07 10:00 in Zürich: Wiedikon's first regular shift after the fixed e2e clock (2026-11-02). */
const FIRST_SHIFT = '2026-11-07T09:00:00Z';
const WIEDIKON = 'Fairteiler Wiedikon';

test('foodsaver signs up for a cleaning shift, sees it in her plans, is confirmed and signs out @FS2 @PU8 @A19 @N3', async ({
  page,
  api,
  as,
  apiOf,
  dev,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'cleaner');
  const me = await dev.user('foodsaver');
  const mine = page.locator(`[data-testid="pickups.mine.cleaning"][data-fsp="${WIEDIKON}"]`);

  await walk.step('signs up for Saturday\'s shift on the Fairteiler page', async () => {
    await page.goto(`/food-share-points/${FSPS.wiedikon}`);
    await expectDataState(page, 'fsp.cleaningCard.state', 'success');
    await expect(page.getByTestId('fsp.cleaningCard.shift')).toHaveCount(5);
    const first = page.locator(`[data-testid="fsp.cleaningCard.shift"][data-shift-at="${FIRST_SHIFT}"]`);
    await walk.click(first.getByTestId('fsp.cleaningCard.slot.free').first());
    await expect(page.getByTestId('fsp.cleaningCard.signupConfirm')).toBeVisible();
    await checkpoint(page, 'signup-dialog');
    await walk.click(page.getByTestId('fsp.cleaningCard.signupConfirm').locator('button'));
    await expect(first.getByTestId('fsp.cleaningCard.unconfirmed')).toBeVisible();
    await expect(first.locator('[data-testid="fsp.cleaningCard.slot.taken"][data-mine="true"]')).toHaveCount(1);
    await checkpoint(page, 'signed-up', { mask: [page.getByTestId('foodSharePoints.detail.wall').locator('time')] });
  });

  await walk.step('finds the shift on her dashboard, in her pickups and in the day agenda', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.pickups.cleaning').filter({ hasText: WIEDIKON }).first()).toBeVisible();
    await walk.nav('pickups');
    await expectDataState(page, 'pickups.mine.state', 'success');
    await expect(mine).toHaveAttribute('data-confirmed', 'false');
    await expect(mine.getByRole('link')).toHaveAttribute('href', `/food-share-points/${FSPS.wiedikon}/cleaning`);
    await checkpoint(page, 'my-pickups-with-cleaning');
    await page.goto('/pickups/agenda/2026-11-07');
    await expectDataState(page, 'pickups.agenda.state', 'success');
    await expect(page.locator(`[data-testid="pickups.agenda.cleaning"][data-fsp="${WIEDIKON}"]`)).toBeVisible();
    await checkpoint(page, 'agenda-with-cleaning');
  });

  await walk.step('is told that Nina confirmed it', async () => {
    const confirmed = await apiOf(await as('bieb')).post(
      `/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts/${encodeURIComponent(FIRST_SHIFT)}/signups/${me.id}/confirm`,
    );
    expect(confirmed.ok(), await confirmed.text()).toBe(true);
    await openNewestBell(walk, api, 'FSP_CLEANING_CONFIRMED');
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.wiedikon}/cleaning`));
    await expectDataState(page, 'fsp.cleaning.state', 'success');
    const shift = page.locator(`[data-testid="fsp.cleaning.shift"][data-shift-at="${FIRST_SHIFT}"]`);
    await expect(shift.locator('[data-testid="fsp.cleaning.slot.taken"][data-confirmed="true"][data-mine="true"]')).toHaveCount(1);
    await checkpoint(page, 'cleaning-confirmed');
  });

  await walk.step('signs out from "My pickups"', async () => {
    await walk.nav('pickups');
    await expectDataState(page, 'pickups.mine.state', 'success');
    await expect(mine).toHaveAttribute('data-confirmed', 'true');
    await walk.click(mine.getByTestId('pickups.mine.cleaning.signOut'));
    await page.locator('.p-confirmdialog-accept-button').click();
    walk.count(1);
    await expect(mine).toHaveCount(0);
  });

  // measured 2026-11-15: 8 clicks on desktop, 9 on the phone
  walk.finish({ steps: 4, clicks: 9, fields: 0 });
});
