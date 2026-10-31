import { injectApiError, seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest, tickFixedClock } from './_setup';

/**
 * Store team screens (M3 F3): team tab as manager and member, row menu, required-message dialog, invite dialog,
 * apply dialog with a refusal, invitations on /stores/mine, loading and error. Baselines in the end batch
 * (decision 69).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const S01 = seedStore('S-01').id;
const S08 = seedStore('S-08').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`stores team ${variant.name}`, () => {
    screenshotTest.describe('bieb', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('team tab as manager @S5 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-team-manager-${variant.name}`);
      });

      screenshotTest('team row menu and message dialog @S8 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await page.locator('[data-testid="stores.team.member"][data-user="Sarah Müller"]').getByTestId('stores.team.menu').click();
        await expect(page.getByTestId('stores.team.toStandby')).toBeVisible();
        // viewport only: a full-page capture resizes the viewport, and p-popover closes on resize (desktop)
        await visual.expectScreen(page, `stores-team-menu-${variant.name}`, { fullPage: false });
        await expect(page.getByTestId('stores.team.toStandby')).toBeVisible();
        await page.getByTestId('stores.team.toStandby').click();
        await expect(page.getByTestId('stores.team.messageDialog.message')).toBeVisible();
        await visual.expectScreen(page, `stores-team-message-${variant.name}`);
      });

      screenshotTest('invite dialog with results @S7 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S08}/team`);
        await page.getByTestId('stores.team.invite').click();
        await page.getByTestId('stores.invite.search').fill('Julien');
        await tickFixedClock(page);
        await expect(page.getByTestId('stores.invite.result').first()).toBeVisible();
        await visual.expectScreen(page, `stores-team-invite-${variant.name}`);
      });

      screenshotTest('team tab loading and error @S5 @screenshot', async ({ page, visual }) => {
        const delay = await injectApiError(page, { url: `/api/stores/${S01}/team`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `stores-team-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S01}/team`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `stores-team-error-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('team tab as member @S5 @S9 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-team-member-${variant.name}`);
      });
    });

    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('my stores with invitation actions @S7 @screenshot', async ({ page, visual }) => {
        await page.goto('/stores/mine');
        await expect(page.getByTestId('stores.mine.invitation').first()).toBeVisible();
        await visual.expectScreen(page, `stores-mine-invitation-${variant.name}`);
      });
    });

    screenshotTest.describe('guide', () => {
      screenshotTest.use({ variant, role: 'guide' });

      screenshotTest('apply dialog with refusal @S6 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores`);
        await page.getByTestId('stores.region.search').fill('Bäckerei Vohdin');
        await page.locator('[data-testid="stores.region.row"][data-store="Bäckerei Vohdin"]').getByTestId('stores.region.apply').click();
        await page.getByTestId('stores.apply.text').fill('Ich helfe gerne mit.');
        await page.getByTestId('stores.apply.submit').click();
        await expect(page.getByTestId('stores.apply.error')).toBeVisible();
        await visual.expectScreen(page, `stores-apply-refused-${variant.name}`);
      });
    });
  });
});
