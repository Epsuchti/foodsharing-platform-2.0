import { acceptPendingConsents, expectDataState, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * A visitor finds Zürich Stadt by postcode and reads its public page (contact, parent region, the active pin on the
 * map, sign-up link); a mistyped region address shows "not found". Signed in as newcomer Noah (newbie@local, no region
 * yet) he is sent to the finder, joins Zürich Stadt (the first region becomes the home region), then moves: he joins
 * Winterthur, makes it his home region and leaves Zürich. Rules behind it: MembershipServiceTest (first region is the
 * home region, the home region needs another membership to be left, only open regions are joinable),
 * RegionSuggestionServiceTest (postcode suggestions), regions-pages.spec (only an active pin becomes a marker).
 */
const ZURICH = regionId('zuerich-stadt');
const WINTERTHUR = regionId('winterthur');

test('a visitor finds a region by postcode, signs in, joins it and later moves to another @R4 @R2', async ({ page, as, apiOf }, testInfo) => {
  test.setTimeout(150_000);
  const visitor = new Walk(page, testInfo, 'visitor');

  await visitor.step('finds Zürich by postcode', async () => {
    await page.goto('/find-region');
    await expect(page.getByTestId('regions.find.picker.postcode')).toBeVisible();
    await visitor.fill(page.getByTestId('regions.find.picker.postcode'), '8004');
    await expect(page.getByTestId('regions.find.picker.suggestion.zuerich-stadt')).toBeVisible();
    await checkpoint(page, 'find-region');
    await visitor.click(page.getByTestId('regions.find.picker.suggestion.zuerich-stadt'));
    await expect(page).toHaveURL(/\/r\/zuerich-stadt$/);
    await expect(page.getByTestId('regions.public.name')).toHaveText('Zürich Stadt');
    await expect(page.getByTestId('regions.public.contact')).toContainText('Reto Baumann');
    await expect(page.getByTestId('regions.public.parent')).toContainText('Zürich');
    await expect(page.getByTestId('regions.public.signup')).toHaveAttribute('href', '/login');
    // the active seed pin is on the map
    await expect(page.getByTestId('regions.public.map')).toBeVisible();
    await checkpoint(page, 'public-region');
  });

  // a mistyped address
  await page.goto('/r/gibt-es-nicht');
  await expect(page.getByTestId('regions.public.state')).toHaveAttribute('data-state', 'notFound');
  await checkpoint(page, 'public-region-not-found');
  visitor.finish({ steps: 1, clicks: 1, fields: 1 });

  const noah = await as('newbie');
  await acceptPendingConsents(apiOf(noah));
  const walk = new Walk(noah, testInfo, 'newcomer');

  await walk.step('is sent to the finder and joins Zürich Stadt', async () => {
    // without a region, "my home region" leads to the finder
    await noah.goto('/regions/home');
    await expect(noah).toHaveURL(/\/find-region$/);
    await walk.fill(noah.getByTestId('regions.find.picker.postcode'), '8004');
    await walk.click(noah.getByTestId('regions.find.picker.suggestion.zuerich-stadt'));
    await expect(noah).toHaveURL(new RegExp(`/regions/${ZURICH}/overview$`));
    await checkpoint(noah, 'region-before-joining');
    await walk.click(noah.getByTestId('regions.page.join'));
    // the first region becomes the home region
    await expect(noah.getByTestId('regions.page.memberBadge')).toContainText('Stammbezirk');
  });

  await walk.step('finds it in his regions', async () => {
    await walk.nav('regions');
    await expectDataState(noah, 'regions.mine.state', 'success');
    await expect(noah.getByTestId('regions.mine.region').first()).toContainText('Zürich Stadt');
    await checkpoint(noah, 'my-regions');
  });

  await walk.step('moves to Winterthur and leaves Zürich', async () => {
    await noah.goto(`/regions/${WINTERTHUR}/overview`);
    await walk.click(noah.getByTestId('regions.page.join'));
    await expect(noah.getByTestId('regions.page.memberBadge')).toBeVisible();
    await walk.click(noah.getByTestId('regions.page.makeHome'));
    await expect(noah.getByTestId('regions.page.memberBadge')).toContainText('Stammbezirk');

    await noah.goto(`/regions/${ZURICH}/overview`);
    await walk.click(noah.getByTestId('regions.page.leave'));
    const confirm = noah.locator('.p-confirmdialog');
    await expect(confirm).toBeVisible();
    await checkpoint(noah, 'leave-dialog');
    await walk.click(confirm.getByRole('button', { name: 'Austreten' }));
    await expect(noah.getByTestId('regions.page.join')).toBeVisible();
    await noah.goto('/regions/home');
    await expect(noah).toHaveURL(new RegExp(`/regions/${WINTERTHUR}/overview$`));
  });

  // the phone opens "Regionen" from the menu drawer (one tap more)
  walk.finish({ steps: 3, clicks: isPhone(noah) ? 8 : 7, fields: 1 });
});
