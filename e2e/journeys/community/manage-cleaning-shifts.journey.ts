import type { Page } from '@playwright/test';
import { FSPS, expectDataState, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { openAutomation, setSwitch } from '../../fixtures/flows/automation';
import { Walk, openNewestBell } from '../../fixtures/persona';
import { fillDate, fillTime } from '../../support/date-inputs';

/**
 * Cleaning shifts (decision 148) and the cleaning rule of the pickup automation (AU1). Nina (bieb@local) manages the
 * Fairteiler Wiedikon and Langstrasse: she switches cleaning shifts on for Langstrasse, adds a one-time shift, is told
 * by bell when Sarah (foodsaver@local, her own session) signs up and confirms her; for Wiedikon she lets sign-ups be
 * confirmed at once and sets its time zone. Bot (bot@local), ambassador of zuerich-stadt, adds Langstrasse to the
 * region's cleaning Fairteiler, which the pickup policy of Nina's S-06 then lists. Sign-up, confirmation and candidate
 * rules are unit-tested (FspCleaningRulesTest, FspCleaningRegionServiceTest, fsp-cleaning.tab.spec.ts); the cleaning
 * rule of the automation too (PickupPolicyEvaluatorTest).
 */
const ZURICH = regionId('zuerich-stadt');
/** Tuesday 2026-11-10 18:00 in Zürich (CET). */
const NEW_SHIFT = '2026-11-10T17:00:00Z';
const shift = (page: Page, at: string) => page.locator(`[data-testid="fsp.cleaning.shift"][data-shift-at="${at}"]`);

test.describe('Fairteiler manager', () => {
  test.use({ role: 'bieb' });

  test('Fairteiler manager switches cleaning on, adds a shift, confirms a sign-up and lets sign-ups confirm at once @FS2 @N3', async ({
    page,
    api,
    as,
    apiOf,
  }, testInfo) => {
    test.setTimeout(150_000);
    const walk = new Walk(page, testInfo, 'Fairteiler manager');

    await walk.step('switches cleaning shifts on for Langstrasse', async () => {
      await page.goto(`/food-share-points/${FSPS.langstrasse}`);
      await expectDataState(page, 'foodSharePoints.detail.state', 'success');
      await walk.click(page.getByTestId('foodSharePoints.detail.edit'));
      await expectDataState(page, 'foodSharePoints.form.state', 'success');
      await setSwitch(page.getByTestId('foodSharePoints.form.cleaning'), true);
      walk.count(1);
      await walk.click(page.getByTestId('foodSharePoints.form.submit'));
      await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}$`));
      // the Fairteiler page lists the next shifts; "All shifts" leads to the manager tools
      await expectDataState(page, 'fsp.cleaningCard.state', 'empty');
      await walk.click(page.getByTestId('fsp.cleaning.open'));
      await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}/cleaning$`));
      await expectDataState(page, 'fsp.cleaning.state', 'empty');
      await checkpoint(page, 'cleaning-empty');
    });

    await walk.step('adds a one-time shift', async () => {
      await walk.click(page.getByTestId('fsp.cleaning.add'));
      await fillDate(page.getByTestId('fsp.cleaning.edit.date'), '2026-11-10');
      await fillTime(page.getByTestId('fsp.cleaning.edit.time'), '18:00');
      await walk.fill(page.getByTestId('fsp.cleaning.edit.slots'), '2');
      await walk.fill(page.getByTestId('fsp.cleaning.edit.description'), 'Regal auswischen');
      await checkpoint(page, 'shift-dialog');
      await walk.click(page.getByTestId('fsp.cleaning.edit.save').locator('button'));
      await expectDataState(page, 'fsp.cleaning.state', 'success');
      await expect(shift(page, NEW_SHIFT)).toHaveAttribute('data-slots', '2');
      await expect(shift(page, NEW_SHIFT).getByTestId('fsp.cleaning.description')).toHaveText('Regal auswischen');
    });

    await walk.step('is told about Sarah\'s sign-up and confirms her', async () => {
      const signup = await apiOf(await as('foodsaver')).post(
        `/api/food-share-points/${FSPS.langstrasse}/cleaning/shifts/${encodeURIComponent(NEW_SHIFT)}/signups`,
      );
      expect(signup.ok(), await signup.text()).toBe(true);
      await openNewestBell(walk, api, 'FSP_CLEANING_SIGNUP_UNCONFIRMED');
      await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}/cleaning`));
      await expectDataState(page, 'fsp.cleaning.state', 'success');
      await expect(shift(page, NEW_SHIFT).locator('[data-testid="fsp.cleaning.slot.taken"][data-confirmed="false"]')).toHaveCount(1);
      await expect(shift(page, NEW_SHIFT).getByTestId('fsp.cleaning.slot.confirm')).toBeVisible();
      await checkpoint(page, 'signup-to-confirm');
      await walk.click(shift(page, NEW_SHIFT).getByTestId('fsp.cleaning.slot.confirm'));
      await walk.click(page.getByTestId('fsp.cleaning.confirmSlot').locator('button'));
      await expect(shift(page, NEW_SHIFT).locator('[data-testid="fsp.cleaning.slot.taken"][data-confirmed="true"]')).toHaveCount(1);
      await expectDataState(page, 'fsp.cleaning.team.state', 'success');
      await expect(page.locator('[data-testid="fsp.cleaning.team.member"][data-manager="true"]')).toHaveCount(1);
      await checkpoint(page, 'signup-confirmed');
    });

    await walk.step('lets sign-ups at Wiedikon be confirmed at once and sets its time zone', async () => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}`);
      await expectDataState(page, 'foodSharePoints.detail.state', 'success');
      await walk.click(page.getByTestId('foodSharePoints.detail.edit'));
      await expectDataState(page, 'foodSharePoints.form.state', 'success');
      await expect(page.getByTestId('foodSharePoints.form.timeZone')).toContainText('Europe/Zurich');
      await walk.click(page.locator('label[for="ff-auto-confirm"]'));
      await walk.click(page.getByTestId('foodSharePoints.form.timeZone'));
      await walk.click(page.getByRole('option', { name: 'Europe/Paris' }));
      await checkpoint(page, 'cleaning-settings');
      await walk.click(page.getByTestId('foodSharePoints.form.submit'));
      await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.wiedikon}$`));
      // Sarah's next sign-up there is confirmed at once (her own session)
      const sarah = apiOf(await as('foodsaver'));
      const shifts = await sarah.getJson<{ shiftAt: string; occupiedSlots: unknown[]; slotCount: number }[]>(
        `/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts`,
      );
      const free = shifts.find((s) => s.occupiedSlots.length < s.slotCount)!;
      const signup = await sarah.post(`/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts/${encodeURIComponent(free.shiftAt)}/signups`);
      expect(signup.ok(), await signup.text()).toBe(true);
      expect(((await signup.json()) as { confirmed: boolean }).confirmed).toBe(true);
    });

    // measured 2026-11-15: 15 clicks on desktop, 16 on the phone
    walk.finish({ steps: 4, clicks: 16, fields: 2 });
  });
});

test.describe('ambassador', () => {
  test.use({ role: 'bot' });

  test('ambassador adds a Fairteiler to the region\'s cleaning Fairteiler @FS2 @AU1', async ({ page, as, apiOf }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'ambassador');
    // Nina has switched cleaning shifts on for Langstrasse (her own session)
    const bieb = await as('bieb');
    const enabled = await apiOf(bieb).patch(`/api/food-share-points/${FSPS.langstrasse}`, { cleaningEnabled: true });
    expect(enabled.ok(), await enabled.text()).toBe(true);

    await walk.step('adds Langstrasse to the cleaning Fairteiler of zuerich-stadt', async () => {
      await page.goto(`/regions/${ZURICH}/automation/cleaning-fsps`);
      await expectDataState(page, 'automation.cleaningFsps.state', 'success');
      await expect(page.locator('[data-testid="automation.cleaningFsps.row"][data-fsp="Fairteiler Wiedikon"]')).toHaveAttribute('data-selected', 'true');
      const langstrasse = page.locator('[data-testid="automation.cleaningFsps.row"][data-fsp="Fairteiler Langstrasse"]');
      await setSwitch(langstrasse.getByTestId('automation.cleaningFsps.toggle'), true);
      walk.count(1);
      await checkpoint(page, 'cleaning-fairteiler');
      await walk.click(page.getByTestId('automation.cleaningFsps.save').locator('button'));
      await page.reload();
      await expectDataState(page, 'automation.cleaningFsps.state', 'success');
      await expect(langstrasse).toHaveAttribute('data-selected', 'true');
    });

    // the pickup policy of Nina's S-06 lists both Fairteiler
    await openAutomation(bieb, 'S-06', 'policy');
    await expect(bieb.getByTestId('automation.policy.cleaningFsp')).toHaveCount(2);

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 1, clicks: 2, fields: 0 });
  });
});
