import { type ApiClient, S07_WEDNESDAY, injectApiError, seedStore, zurichPickupAt } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Pickup screens (M3 F4): store pickups tab (day list; as manager with split pills, the "Unbestätigt (n)" chip and the
 * summary), sign-up dialog eligible / not eligible, regular editor, one-time pickup dialog, options, my pickups,
 * agenda, dashboard card, loading and error; the store rules card, the "Gesperrt" chip and its reason dialog
 * (decision 165 S2). Baselines in the end batch
 * (decision 69).
 */
const S01 = seedStore('S-01').id;
const S02 = seedStore('S-02').id;
const S07 = seedStore('S-07').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`pickups ${variant.name}`, () => {
    screenshotTest.describe('guide', () => {
      screenshotTest.use({ variant, role: 'guide' });

      screenshotTest('my pickups with a cleaning shift @FS2 @screenshot', async ({ page, visual }) => {
        await page.goto('/pickups');
        await expect(page.getByTestId('pickups.mine.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.mine.cleaning').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-mine-cleaning-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('pickups tab day list @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-tab-${variant.name}`);
      });

      screenshotTest('sign-up dialog eligible @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S07}/pickups`);
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S07_WEDNESDAY}"]`).getByTestId('stores.pickups.slot.free').first().click();
        await expect(page.getByTestId('pickups.signup.eligible')).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-eligible-${variant.name}`);
      });

      screenshotTest('pickups page, agenda, dashboard @PU7 @PU8 @A19 @screenshot', async ({ page, visual }) => {
        // one page since 2026-11-04: my pickups and where can I help (baselines in the end batch, decision 69)
        await page.goto('/pickups');
        await expect(page.getByTestId('pickups.mine.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.options.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-page-${variant.name}`);
        await page.getByTestId('pickups.options.showMore').click();
        await visual.expectScreen(page, `pickups-page-more-${variant.name}`);
        await page.goto('/pickups/agenda/2026-11-05');
        await expect(page.getByTestId('pickups.agenda.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-agenda-${variant.name}`);
        await page.goto('/dashboard');
        await expect(page.getByTestId('dashboard.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-dashboard-${variant.name}`);
      });

      screenshotTest('pickups tab loading and error @PU3 @screenshot', async ({ page, visual }) => {
        const delay = await injectApiError(page, { url: `/api/stores/${S01}/pickups`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-tab-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S01}/pickups`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-tab-error-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver booking', () => {
      // books a slot, so the seed is reset per test and the screens above stay on the untouched seed
      screenshotTest.use({ variant, role: 'foodsaver', seedReset: 'test' });

      screenshotTest('booked slot, unconfirmed @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S07}/pickups`);
        const row = page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S07_WEDNESDAY}"]`);
        await row.getByTestId('stores.pickups.slot.free').first().click();
        await page.getByTestId('pickups.signup.confirm').click();
        await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]')).toHaveCount(1);
        await visual.expectScreen(page, `pickups-booked-${variant.name}`);
      });
    });

    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('eligibility reasons behind the "Gesperrt" chip @PU4 @screenshot', async ({ page, visual }) => {
        // decision 165 S2: eligibility reasons use the same lock chip and reason dialog as the store rules
        await page.goto(`/stores/${S02}/pickups`);
        await page.getByTestId('stores.pickups.slot.blocked').first().click();
        await expect(page.getByTestId('shared.eligibility.reason').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-reasons-${variant.name}`);
      });
    });

    screenshotTest.describe('sleepy with store rules', () => {
      // S-06's live policy with a 3-day gap; sleepy has Friday (confirmed) and the seeded Tuesday, so Sunday is blocked
      screenshotTest.use({ variant, role: 'sleepy', seedReset: 'test' });

      const S06 = seedStore('S-06').id;
      const FRIDAY = zurichPickupAt(4, '19:00');
      const SUNDAY = zurichPickupAt(6, '19:00');

      async function gapRule(bieb: ApiClient): Promise<void> {
        const current = await bieb.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06}/automation`);
        await bieb.putJson(`/api/stores/${S06}/automation/pickup-policy`, {
          ...current.pickupPolicy,
          enabled: true,
          experienceRuleEnabled: false,
          gapRuleEnabled: true,
          minimumGapDays: 3,
        });
        for (const at of [FRIDAY, SUNDAY]) {
          await bieb.putJson(`/api/stores/${S06}/pickups/${encodeURIComponent(at)}`, { slotCount: 2 });
        }
      }

      screenshotTest('rules card, Gesperrt chip and reason dialog @AU1 @PU3 @screenshot', async ({ page, as, apiOf, store, visual }) => {
        await gapRule(apiOf(await as('bieb')));
        await store.signUp('sleepy', 'S-06', FRIDAY);
        await page.goto(`/stores/${S06}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.pickups.rules.toggle').click();
        await expect(page.getByTestId('stores.pickups.rules.line').first()).toBeVisible();
        await expect(page.getByTestId('stores.pickups.slot.blocked')).toBeVisible();
        await visual.expectScreen(page, `pickups-rules-blocked-${variant.name}`);
        await page.getByTestId('stores.pickups.slot.blocked').click();
        await expect(page.getByTestId('pickups.blocked.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.blocked.reason').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-blocked-dialog-${variant.name}`);
      });

      screenshotTest('rules card loading and error @AU1 @screenshot', async ({ page, as, apiOf, visual }) => {
        await gapRule(apiOf(await as('bieb')));
        const delay = await injectApiError(page, { url: `/api/stores/${S06}/automation/rules`, delayMs: 30_000 });
        await page.goto(`/stores/${S06}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.pickups.rules.toggle').click();
        await expect(page.getByTestId('stores.pickups.rules')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-rules-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S06}/automation/rules`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.pickups.rules')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-rules-error-${variant.name}`);
      });
    });

    screenshotTest.describe('bieb', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('regular editor and one-time dialog @PU1 @PU2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await page.getByTestId('stores.pickups.regular').click();
        await expect(page.getByTestId('stores.regular.row').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-regular-${variant.name}`);
        await page.getByRole('button', { name: /Abbrechen|Annuler/ }).click();
        await page.getByTestId('stores.pickups.add').click();
        await expect(page.getByTestId('stores.pickups.edit.date')).toBeVisible();
        await visual.expectScreen(page, `pickups-one-time-${variant.name}`);
      });

      screenshotTest('pickups tab as manager: split pill, chip, summary @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('stores.pickups.range.pending')).toBeVisible();
        await expect(page.getByTestId('stores.pickups.slot.confirm').first()).toBeVisible();
        await expect(page.getByTestId('stores.pickups.summary').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-tab-manager-${variant.name}`);
      });
    });
  });
});
