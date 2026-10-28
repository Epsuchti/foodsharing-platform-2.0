import { type ApiClient, acceptPendingConsents, injectApiError } from '../fixtures';
import { ZH } from '../tests/intro-pickups/_intro';
import { expect, forEachVariant, screenshotTest, type Variant } from './_setup';

/**
 * Intro pickup screens (M2 T21): bookable list (incl. reasons, booking success), no-region picker, public dates,
 * my bookings with progress and subscription, unsubscribe, guide dashboard (upcoming, past with slots, form dialog,
 * comments), guide settings, admin (dates, bookings, guides), import preview, funnel, forbidden and error states —
 * × light/dark × de/fr. Baselines are generated in one batch (decision 69), not per task.
 */
screenshotTest.use({ seedReset: 'test' });

async function language(api: ApiClient, variant: Variant): Promise<void> {
  await api.patchJson('/api/users/me/settings', { language: variant.lang });
}

const CSV =
  'ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n' +
  '100004;01.09.2026;1;babus;Bäckerei;Anna;nein;gut\n' +
  '100004;01.09.2026;1;Babus Bakery;Bäckerei;Anna;nein;doppelt\n' +
  '100099;02.09.2026;1;Coop;Supermarkt;Anna;ja;x\n' +
  '100005;05.09.2026;x;Coop;FT-Reinigung;Anna;ja;Nicht erschienen\n';

forEachVariant((variant) => {
  screenshotTest.describe(`intro-pickups ${variant.name}`, () => {
    screenshotTest.describe('newcomer', () => {
      screenshotTest.use({ variant, role: 'einab1' });

      screenshotTest('bookable list and booking success @EA1 @EA5 @EA7 @screenshot', async ({ page, apiOf, visual }) => {
        await language(apiOf(page), variant);
        await page.goto('/intro-pickups');
        await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-bookable-${variant.name}`);
        await page.locator('[data-testid="introPickups.bookable.card"][data-category="ONLINE"]').getByTestId('introPickups.bookable.book').click();
        await page.locator('.p-confirmdialog-accept-button').click();
        await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
        await visual.expectScreen(page, `intro-booked-${variant.name}`, { fullPage: false });
      });

      screenshotTest('my bookings with progress and subscription @EA7 @EA8 @EA9 @screenshot', async ({ page, apiOf, visual }) => {
        await language(apiOf(page), variant);
        await page.goto('/me/intro-pickups');
        await expect(page.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-my-bookings-${variant.name}`);
      });

      screenshotTest('bookable list error @EA1 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `**/api/regions/${ZH}/intro-pickups/bookable**`, status: 400, code: 'VALIDATION_FAILED' });
        await page.goto('/intro-pickups');
        await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `intro-bookable-error-${variant.name}`);
      });

      screenshotTest('bookable list loading @EA1 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `**/api/regions/${ZH}/intro-pickups/bookable**`, status: 400, code: 'VALIDATION_FAILED', delayMs: 60_000 });
        await page.goto('/intro-pickups');
        await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `intro-bookable-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('admin page forbidden @EA18 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZH}/intro-pickups/admin`);
        await expect(page.getByTestId('introPickups.admin.einabsState')).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `intro-admin-forbidden-${variant.name}`);
      });
    });

    screenshotTest.describe('newbie', () => {
      screenshotTest.use({ variant, role: 'newbie' });

      screenshotTest('no region yet @EA1 @screenshot', async ({ page, apiOf, visual }) => {
        await acceptPendingConsents(apiOf(page));
        await page.goto('/intro-pickups');
        await expect(page.getByTestId('introPickups.bookable.noRegion')).toBeVisible();
        await visual.expectScreen(page, `intro-no-region-${variant.name}`);
      });
    });

    screenshotTest.describe('anonymous', () => {
      screenshotTest.use({ variant });

      screenshotTest('public dates and unsubscribe @EA2 @EA9 @screenshot', async ({ page, visual }) => {
        await page.goto('/r/zuerich-stadt/intro-pickups');
        await expect(page.getByTestId('introPickups.public.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-public-${variant.name}`);
        await page.goto('/intro-pickups/unsubscribe?token=seed-unsubscribe-quiz');
        await expect(page.getByTestId('introPickups.unsubscribe.done')).toBeVisible();
        await visual.expectScreen(page, `intro-unsubscribe-${variant.name}`);
      });
    });

    screenshotTest.describe('guide', () => {
      screenshotTest.use({ variant, role: 'guide' });

      screenshotTest('guide dashboard, form, comments and settings @EA10 @EA11 @EA13 @EA15 @screenshot', async ({ page, apiOf, visual }) => {
        await language(apiOf(page), variant);
        await page.goto('/guide');
        await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-guide-${variant.name}`);
        await page.getByTestId('introPickups.guide.create').click();
        await expect(page.getByTestId('introPickups.form')).toBeVisible();
        await visual.expectScreen(page, `intro-guide-form-${variant.name}`, { fullPage: false });
        await page.keyboard.press('Escape');
        await page.getByTestId('introPickups.guide.showPast').click();
        await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-guide-past-${variant.name}`);
        await page.locator('[data-testid="introPickups.slot"][data-status="DONE"]').first().getByTestId('introPickups.slot.comments').click();
        await expect(page.getByTestId('introPickups.comments.state')).toHaveAttribute('data-state', /success|empty/);
        await visual.expectScreen(page, `intro-guide-comments-${variant.name}`, { fullPage: false });
        await page.goto('/guide/settings');
        await expect(page.getByTestId('introPickups.settings.profileState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-guide-settings-${variant.name}`);
      });
    });

    screenshotTest.describe('ambassador', () => {
      screenshotTest.use({ variant, role: 'bot' });

      screenshotTest('admin, import preview and funnel @EA17 @EA18 @EA19 @EA21 @screenshot', async ({ page, apiOf, visual }) => {
        await language(apiOf(page), variant);
        await page.goto(`/regions/${ZH}/intro-pickups/admin`);
        await expect(page.getByTestId('introPickups.admin.einabsState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-admin-${variant.name}`);
        await page.getByTestId('introPickups.admin.tab.bookings').click();
        await page.getByTestId('introPickups.admin.pastBookings').click();
        await expect(page.getByTestId('introPickups.admin.bookingsState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-admin-bookings-${variant.name}`);
        await page.getByTestId('introPickups.admin.tab.guides').click();
        await expect(page.getByTestId('introPickups.admin.guidesState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-admin-guides-${variant.name}`);

        await page.goto(`/regions/${ZH}/intro-pickups/import`);
        await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'einab.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
        await page.getByTestId('introPickups.import.previewButton').click();
        await expect(page.getByTestId('introPickups.import.preview')).toBeVisible();
        await visual.expectScreen(page, `intro-import-preview-${variant.name}`);

        await page.goto(`/regions/${ZH}/newcomers`);
        await expect(page.getByTestId('introPickups.funnel.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `intro-funnel-${variant.name}`);
      });
    });
  });
});
