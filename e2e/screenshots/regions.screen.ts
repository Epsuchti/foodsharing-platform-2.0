import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Region screens (M2 T18): region page tabs, group page, public region page, my regions, region admin, user
 * management and the profile — filled, empty, loading, error and forbidden — × light/dark × de/fr.
 * Baselines are generated in Docker (`e2e/scripts/screenshots.sh --update-snapshots`).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const PR_GROUP = '00000011-0001-4000-8000-000000000028';
const SLEEPY = '00000001-0001-4000-8000-000000000008';

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`regions ${variant.name}`, () => {
    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('region overview @R3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/overview`);
        await expect(page.getByTestId('regions.overview.introState')).not.toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `region-overview-${variant.name}`);
      });

      screenshotTest('region statistics @R13 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/statistics`);
        await expect(page.getByTestId('regions.statistics.gender')).toBeVisible();
        await visual.expectScreen(page, `region-statistics-${variant.name}`);
      });

      screenshotTest('region options read-only @R5 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/options`);
        await expect(page.getByTestId('regions.options.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `region-options-${variant.name}`);
      });

      screenshotTest('region later tab @R3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/later/forum`);
        await expect(page.getByTestId('regions.later.empty')).toBeVisible();
        await visual.expectScreen(page, `region-later-${variant.name}`);
      });

      screenshotTest('region page loading @R3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `/api/regions/${ZURICH}`, delayMs: 30_000 });
        await page.goto(`/regions/${ZURICH}/overview`);
        await expect(page.getByTestId('regions.page.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `region-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('region settings forbidden @R3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/settings`);
        await expect(page.getByTestId('regions.settings.state')).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `region-settings-forbidden-${variant.name}`);
      });

      screenshotTest('my regions @R2 @screenshot', async ({ page, visual }) => {
        await page.goto('/regions/mine');
        await expect(page.getByTestId('regions.mine.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `my-regions-${variant.name}`);
      });

      screenshotTest('my regions error @R2 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/regions/mine', status: 500 });
        await page.goto('/regions/mine');
        await expect(page.getByTestId('regions.mine.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `my-regions-error-${variant.name}`);
      });

      screenshotTest('group page as non-member @R9 @screenshot', async ({ page, visual }) => {
        await page.goto('/groups/00000011-0001-4000-8000-000000000023');
        await expect(page.getByTestId('regions.group.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `group-requirements-${variant.name}`);
      });

      screenshotTest('profile @A4 @screenshot', async ({ page, visual }) => {
        await page.goto('/account/profile');
        await expect(page.getByTestId('account.profile.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `profile-${variant.name}`);
      });
    });

    screenshotTest.describe('ambassador', () => {
      screenshotTest.use({ variant, role: 'bot' });

      screenshotTest('region members @R8 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/members`);
        await expect(page.getByTestId('regions.members.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `region-members-${variant.name}`);
      });

      screenshotTest('region settings @R3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/settings`);
        await expect(page.getByTestId('regions.settings.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `region-settings-${variant.name}`);
      });

      screenshotTest('group page as admin @R9 @screenshot', async ({ page, visual }) => {
        await page.goto(`/groups/${PR_GROUP}`);
        await expect(page.getByTestId('regions.group.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `group-admin-${variant.name}`);
      });

      screenshotTest('user management @AD1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/users`);
        await expect(page.getByTestId('usersAdmin.list.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `users-list-${variant.name}`);
      });

      screenshotTest('user admin page @AD1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/users/${SLEEPY}`);
        await expect(page.getByTestId('usersAdmin.user.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('usersAdmin.user.history.list')).not.toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `user-admin-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('region admin @R7 @screenshot', async ({ page, visual }) => {
        await page.goto('/admin/regions');
        await expect(page.getByTestId('regionAdmin.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `region-admin-${variant.name}`);
      });
    });

    screenshotTest.describe('visitor', () => {
      screenshotTest.use({ variant });

      screenshotTest('public region page @R4 @screenshot', async ({ page, visual }) => {
        await page.goto('/r/zuerich-stadt');
        await expect(page.getByTestId('regions.public.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `public-region-${variant.name}`);
      });

      screenshotTest('public region not found @R4 @screenshot', async ({ page, visual }) => {
        await page.goto('/r/gibt-es-nicht');
        await expect(page.getByTestId('regions.public.state')).toHaveAttribute('data-state', 'notFound');
        await visual.expectScreen(page, `public-region-not-found-${variant.name}`);
      });

      screenshotTest('find region @R4 @screenshot', async ({ page, visual }) => {
        await page.goto('/find-region');
        await expect(page.getByTestId('regions.find.picker.postcode')).toBeVisible();
        await visual.expectScreen(page, `find-region-${variant.name}`);
      });
    });
  });
});
