import type { Page } from '@playwright/test';
import { FSPS, acceptPendingConsents, expect, expectDataState, regionId, test } from '../../fixtures';
import { createPickup, openAutomation, savePolicy, setSwitch, waitForAudit } from '../automation/_automation';

/**
 * FS2 Fairteiler cleaning shifts (decision 148) and the cleaning rule (AU1). Seed (`180-fsp-cleaning-seed.yaml`):
 * Wiedikon has cleaning shifts (Saturdays 10:00) and is zuerich-stadt's cleaning Fairteiler; bieb manages Wiedikon and
 * Langstrasse (cleaning off); bot is ambassador of zuerich-stadt; S-06 (bieb, sleepy) has the live pickup policy.
 */
const ZURICH = regionId('zuerich-stadt');
/** Tuesday 2026-11-10 18:00 in Zürich (CET). */
const NEW_SHIFT = '2026-11-10T17:00:00Z';

const shift = (page: Page, at: string) => page.locator(`[data-testid="fsp.cleaning.shift"][data-shift-at="${at}"]`);

test.describe('cleaning shifts', () => {
  test.use({ seedReset: 'test', role: 'bieb' });

  test('FS2 manager enables cleaning and adds a shift, foodsaver signs up, manager confirms, region admin links the Fairteiler @FS2 @AU1 @page', async ({
    page,
    as,
    apiOf,
    notifications,
  }) => {
    await acceptPendingConsents(apiOf(page));

    // bieb switches cleaning shifts on for Langstrasse
    await page.goto(`/food-share-points/${FSPS.langstrasse}/edit`);
    await expectDataState(page, 'foodSharePoints.form.state', 'success');
    await setSwitch(page.getByTestId('foodSharePoints.form.cleaning'), true);
    await page.getByTestId('foodSharePoints.form.submit').click();
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}$`));
    await page.getByTestId('fsp.cleaning.open').click();
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}/cleaning$`));
    await expectDataState(page, 'fsp.cleaning.state', 'empty');

    // ... and adds a one-time shift (plain text date and time)
    await page.getByTestId('fsp.cleaning.add').click();
    await page.getByTestId('fsp.cleaning.edit.date').fill('10.11.2026');
    await page.getByTestId('fsp.cleaning.edit.time').fill('18:00');
    await page.getByTestId('fsp.cleaning.edit.slots').fill('2');
    await page.getByTestId('fsp.cleaning.edit.description').fill('Regal auswischen');
    await page.getByTestId('fsp.cleaning.edit.save').locator('button').click();
    await expectDataState(page, 'fsp.cleaning.state', 'success');
    await expect(shift(page, NEW_SHIFT)).toHaveAttribute('data-slots', '2');
    await expect(shift(page, NEW_SHIFT).getByTestId('fsp.cleaning.description')).toHaveText('Regal auswischen');

    // foodsaver signs up; the managers get a bell
    const foodsaver = await as('foodsaver');
    await acceptPendingConsents(apiOf(foodsaver));
    await foodsaver.goto(`/food-share-points/${FSPS.langstrasse}/cleaning`);
    await expectDataState(foodsaver, 'fsp.cleaning.state', 'success');
    await shift(foodsaver, NEW_SHIFT).getByTestId('fsp.cleaning.slot.free').first().click();
    await foodsaver.getByTestId('fsp.cleaning.signupConfirm').locator('button').click();
    await expect(shift(foodsaver, NEW_SHIFT).getByTestId('fsp.cleaning.unconfirmed')).toBeVisible();
    await expect
      .poll(async () => (await notifications.bells('bieb', 'FSP_CLEANING_SIGNUP_UNCONFIRMED')).map((bell) => bell.link))
      .toContain(`/food-share-points/${FSPS.langstrasse}/cleaning`);

    // bieb confirms from the split pill; foodsaver gets a bell
    await page.reload();
    await expectDataState(page, 'fsp.cleaning.state', 'success');
    await shift(page, NEW_SHIFT).getByTestId('fsp.cleaning.slot.confirm').click();
    await page.getByTestId('fsp.cleaning.confirmSlot').locator('button').click();
    await expect(shift(page, NEW_SHIFT).locator('[data-testid="fsp.cleaning.slot.taken"][data-confirmed="true"]')).toHaveCount(1);
    await expect.poll(async () => (await notifications.bells('foodsaver', 'FSP_CLEANING_CONFIRMED')).length).toBe(1);
    await expectDataState(page, 'fsp.cleaning.team.state', 'success');
    await expect(page.locator('[data-testid="fsp.cleaning.team.member"][data-manager="true"]')).toHaveCount(1);

    // bot (ambassador) adds Langstrasse to the region's cleaning Fairteiler
    const bot = await as('bot');
    await acceptPendingConsents(apiOf(bot));
    await bot.goto(`/regions/${ZURICH}/automation/cleaning-fsps`);
    await expectDataState(bot, 'automation.cleaningFsps.state', 'success');
    await expect(bot.locator('[data-testid="automation.cleaningFsps.row"][data-fsp="Fairteiler Wiedikon"]')).toHaveAttribute('data-selected', 'true');
    const langstrasse = bot.locator('[data-testid="automation.cleaningFsps.row"][data-fsp="Fairteiler Langstrasse"]');
    await setSwitch(langstrasse.getByTestId('automation.cleaningFsps.toggle'), true);
    await bot.getByTestId('automation.cleaningFsps.save').locator('button').click();
    await bot.reload();
    await expectDataState(bot, 'automation.cleaningFsps.state', 'success');
    await expect(langstrasse).toHaveAttribute('data-selected', 'true');

    // the pickup policy's cleaning card lists both
    await openAutomation(page, 'S-06', 'policy');
    await expect(page.getByTestId('automation.policy.cleaningFsp')).toHaveCount(2);
  });

  test('AU1 cleaning rule declines a user without a cleaning shift and confirms one with a planned shift @FS2 @AU1', async ({
    page,
    api,
    apiOf,
    clock,
    store,
  }) => {
    // three months later sleepy's verification is older than the 12-month back check, so the grace period is over
    await clock.fixServer('2027-02-01T09:00:00Z');
    await acceptPendingConsents(apiOf(page));
    await openAutomation(page, 'S-06', 'policy');
    await setSwitch(page.getByTestId('automation.policy.experience'), false);
    await setSwitch(page.getByTestId('automation.policy.gapEnabled'), false);
    await setSwitch(page.getByTestId('automation.policy.cleaning'), true);
    await savePolicy(page);
    await expect(page.getByTestId('automation.policy.cleaningFsp')).toHaveText(['Fairteiler Wiedikon']);

    const friday = '2027-02-05T18:00:00Z';
    const sunday = '2027-02-07T18:00:00Z';
    await createPickup(api, 'S-06', friday);
    await createPickup(api, 'S-06', sunday);

    // no cleaning in Wiedikon: declined, the message lists Wiedikon's free shifts
    await store.signUp('sleepy', 'S-06', friday);
    const declined = await waitForAudit(api, 'S-06', friday);
    expect(declined.status).toBe('DECLINED');
    expect(declined.reasons.map((r) => r.rule)).toContain('CLEANING_REQUIRED');
    expect(declined.reasons.find((r) => r.rule === 'CLEANING_REQUIRED')?.text).toContain('Fairteiler Wiedikon');

    // sleepy wakes up and signs up for Saturday's cleaning (still unconfirmed, D5): the next pickup is confirmed
    await store.withRole('sleepy', async (sleepy) => {
      await sleepy.putJson('/api/users/me/sleep-mode', { status: 'NONE' });
      await sleepy.postJson(`/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts/${encodeURIComponent('2027-02-06T09:00:00Z')}/signups`);
    });
    await store.signUp('sleepy', 'S-06', sunday);
    const confirmed = await waitForAudit(api, 'S-06', sunday);
    expect(confirmed.status).toBe('CONFIRMED');
  });
});
