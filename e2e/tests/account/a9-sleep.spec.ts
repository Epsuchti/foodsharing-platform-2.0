import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';
import { NAMES, ZURICH, memberRow, openStore } from '../stores/_stores';

/**
 * A9 sleep mode (plan-m7 Q3, A39): the settings card sets NONE / TEMPORARY / FULL with dates and a message; while
 * sleeping the moon shows on the public profile, in the region member list (user chip) and in the store team.
 * Server and browser clock at SEED_NOW (2026-11-02). Seed: foodsaver (Sarah Müller) is awake, member of Zürich and
 * of the S-01 team (bieb manages S-01).
 */
test.use({ seedReset: 'test' });

const FOODSAVER = '00000001-0001-4000-8000-000000000006';

test('A9 user sets temporary sleep mode and the badge appears in profile, member list and chips @A9 @page', async ({
  as,
  clock,
}) => {
  const page = await as('foodsaver');
  await clock.fixBrowser(page);
  await page.goto('/account/settings');
  await expectDataState(page, 'account.settings.sleep.state', 'success');
  await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'NONE');

  await page.getByTestId('account.settings.sleep.status.TEMPORARY').click();
  await expect(page.getByTestId('account.settings.sleep.problem')).toBeVisible();
  await expect(page.getByTestId('account.settings.sleep.save').locator('button')).toBeDisabled();
  // the last day before the first one is refused before asking the server
  await page.getByTestId('account.settings.sleep.from').fill('2026-11-10');
  await page.getByTestId('account.settings.sleep.until').fill('2026-11-05');
  await expect(page.getByTestId('account.settings.sleep.problem')).toBeVisible();
  await page.getByTestId('account.settings.sleep.from').fill('2026-11-02');
  await page.getByTestId('account.settings.sleep.until').fill('2026-11-20');
  await page.getByTestId('account.settings.sleep.message').fill('Ferien im Tessin, ab dem 21. wieder dabei.');
  await expect(page.getByTestId('account.settings.sleep.problem')).toHaveCount(0);
  await page.getByTestId('account.settings.sleep.save').click();
  await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'TEMPORARY');

  const bieb = await as('bieb');
  await bieb.goto(`/profile/${FOODSAVER}`);
  await expect(bieb.getByTestId('profiles.page.sleep')).toBeVisible();

  await bieb.goto(`/regions/${ZURICH}/members`);
  await expectDataState(bieb, 'regions.members.list', 'success');
  await bieb.getByTestId('regions.members.search').fill('Müller');
  const member = bieb.locator(`[data-testid="regions.members.member"][data-user="${FOODSAVER}"]`);
  await expect(member.getByTestId('shared.userChip.sleep')).toBeVisible();

  await openStore(bieb, 'S-01', 'team');
  await expectDataState(bieb, 'stores.team.state', 'success');
  await expect(memberRow(bieb, NAMES.foodsaver).getByTestId('stores.team.sleeping')).toBeVisible();

  // awake again: the moon is gone
  await page.getByTestId('account.settings.sleep.status.NONE').click();
  await page.getByTestId('account.settings.sleep.save').click();
  await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'NONE');
  await bieb.goto(`/profile/${FOODSAVER}`);
  await expect(bieb.getByTestId('profiles.page.name')).toBeVisible();
  await expect(bieb.getByTestId('profiles.page.sleep')).toHaveCount(0);
});

test('A9 full sleep mode without dates; a failed save keeps the choice @A9', async ({ as, clock }) => {
  const page = await as('foodsaver');
  await clock.fixBrowser(page);
  await page.goto('/account/settings');
  await expectDataState(page, 'account.settings.sleep.state', 'success');
  await page.getByTestId('account.settings.sleep.status.FULL').click();
  await expect(page.getByTestId('account.settings.sleep.from')).toHaveCount(0);

  await injectApiError(page, { url: '/api/users/me/sleep-mode', status: 400, code: 'SLEEP_DATES_INVALID', times: 1 });
  await page.getByTestId('account.settings.sleep.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
  await expect(page.getByTestId('account.settings.sleep.status.FULL')).toHaveAttribute('aria-checked', 'true');

  await page.getByTestId('account.settings.sleep.save').click();
  await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'FULL');

  await injectApiError(page, { url: '/api/users/me', method: 'GET', status: 500 });
  await page.reload();
  await expectErrorState(page, 'account.settings.sleep.state');
});
