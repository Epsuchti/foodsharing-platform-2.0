import { acceptPendingConsents, expectStatus, injectApiError, seedStore } from '../fixtures';
import { memberRow, NAMES, pageSize } from '../tests/stores/_stores';
import { expect, forEachVariant, screenshotTest, tickFixedClock } from './_setup';

/**
 * Store team screens (M3 F3, resume item 14): team tab as manager and member, filtered by status, empty filter,
 * page 2, row menu, required-message dialog, invite dialog, apply dialog with a refusal, invitations on /stores/mine,
 * loading and error, the cards with call button and menu at 375px; the per-member statistics card filtered to former members and with an empty filter. Baselines
 * in the end batch (decision 69).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const S01 = seedStore('S-01').id;
const S08 = seedStore('S-08').id;
const S10 = seedStore('S-10').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`stores team ${variant.name}`, () => {
    screenshotTest.describe('bieb', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('team tab as manager @S5 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-team-manager-${variant.name}`);
      });

      screenshotTest('team tab filtered to jumpers @S9 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.team.filter.jumper').click();
        await expect(page.locator('[data-testid="stores.team.member"][data-status="MEMBER"]')).toHaveCount(0);
        await visual.expectScreen(page, `stores-team-filtered-${variant.name}`);
      });

      screenshotTest('team cards with call button and menu at phone width @S9 @screenshot', async ({ page, visual }) => {
        // owner report 2026-11-12: name and stats collapsed letter by letter next to the full phone number
        await page.setViewportSize({ width: 375, height: 812 });
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('stores.team.call').first()).toBeVisible();
        await visual.expectScreen(page, `stores-team-phone-width-${variant.name}`);
      });

      screenshotTest('team tab with an empty filter @S9 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.team.search').fill('zzz');
        await tickFixedClock(page); // the search is debounced; the fixed page clock never fires its timer
        await expect(page.getByTestId('stores.team.emptyFilter')).toBeVisible();
        await visual.expectScreen(page, `stores-team-empty-filter-${variant.name}`);
      });

      screenshotTest('team tab page 2 @S9 @screenshot', async ({ page, visual }) => {
        await pageSize(page, S01, 2);
        await page.goto(`/stores/${S01}/team`);
        await page.getByTestId('stores.team.paginator').locator('.p-paginator-next').click();
        await expect(page.getByTestId('stores.team.paginator').locator('[aria-current="page"]')).toHaveText('2');
        await visual.expectScreen(page, `stores-team-page2-${variant.name}`);
      });

      screenshotTest('statistics per member: former and empty filter @S14 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/statistics`);
        await expect(page.getByTestId('stores.statistics.members.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.statistics.range.all').click();
        await page.getByTestId('stores.statistics.members.filter.former').click();
        await expect(page.getByTestId('stores.statistics.members.filter.former')).toHaveAttribute('aria-pressed', 'true');
        await visual.expectElement(page.getByTestId('stores.statistics.members'), `stores-statistics-members-former-${variant.name}`);
        await page.getByTestId('stores.statistics.members.search').fill('zzz');
        await tickFixedClock(page);
        await expect(page.getByTestId('stores.statistics.members.emptyFilter')).toBeVisible();
        await visual.expectElement(
          page.getByTestId('stores.statistics.members'),
          `stores-statistics-members-empty-filter-${variant.name}`,
        );
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

      screenshotTest('invite dialog: nobody who can be invited @S7 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S08}/team`);
        await page.getByTestId('stores.team.invite').click();
        await page.getByTestId('stores.invite.search').fill('Lea');
        await tickFixedClock(page);
        await expect(page.getByTestId('stores.invite.state')).toHaveAttribute('data-state', 'empty');
        await visual.expectScreen(page, `stores-team-invite-empty-${variant.name}`);
      });

      screenshotTest('team tab loading and error @S5 @screenshot', async ({ page, visual }) => {
        const delay = await injectApiError(page, { url: `/api/stores/${S01}/team*`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/team`);
        await expect(page.getByTestId('stores.team.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `stores-team-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S01}/team*`, status: 500 });
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
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-team-member-${variant.name}`);
      });
    });

    screenshotTest.describe('welcome', () => {
      screenshotTest.use({ variant, role: 'welcome' });

      screenshotTest('row menu with appoint blocked: three managers @S9 @screenshot', async ({ page, as, apiOf, dev, visual }) => {
        // welcome manages S-10 with bot (no coordinator); orga adds two more and appoints one: three managers
        const orga = apiOf(await as('orga'));
        for (const role of ['reports', 'bieb'] as const) {
          const user = await dev.user(role);
          await expectStatus(await orga.post(`/api/stores/${S10}/team/${user.id}`), [200, 201, 204, 409], `add ${role}`);
        }
        const reports = await dev.user('reports');
        await expectStatus(await orga.put(`/api/stores/${S10}/managers/${reports.id}`), [200, 204, 409], 'third manager');
        await acceptPendingConsents(apiOf(page));
        await page.goto(`/stores/${S10}/team`);
        await expect(page.getByTestId('stores.team.list')).toHaveAttribute('data-state', 'success');
        await memberRow(page, NAMES.bieb).getByTestId('stores.team.menu').click();
        await expect(page.getByTestId('stores.team.appointBlocked')).toBeVisible();
        // viewport only: p-popover closes on the resize of a full-page capture
        await visual.expectScreen(page, `stores-team-appoint-blocked-${variant.name}`, { fullPage: false });
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
