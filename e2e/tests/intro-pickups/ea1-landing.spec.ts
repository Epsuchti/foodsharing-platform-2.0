import { acceptPendingConsents, expect, test } from '../../fixtures';
import { bookableCards, openBookable } from './_intro';

/** EA1: `/intro-pickups` lists the home region's EinAbs; users without a region pick one first. */
test.use({ role: 'einab1' });

test('EA1 logged-in user lands on home region list, newbie gets region picker @EA1 @page', async ({ page, as, apiOf }) => {
  // newbie: no region yet → region picker, then the public list of a region
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto('/intro-pickups');
  await expect(newbie.getByTestId('introPickups.bookable.noRegion')).toBeVisible();
  await expect(newbie.getByTestId('introPickups.bookable.regionPicker')).toBeVisible();
  await newbie.goto('/r/zuerich-stadt/intro-pickups');
  await expect(newbie.getByTestId('introPickups.public.state')).toHaveAttribute('data-state', 'success');

  // einab1 (home region Zürich Stadt): the region's 14 upcoming EinAbs
  await openBookable(page);
  await expect(page.getByTestId('introPickups.bookable.card')).toHaveCount(14);
  await expect(page.locator('fs-page-header')).toContainText('Zürich');
  await expect(bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book')).toBeVisible();

  // no filters: each one would let a caller test for a hidden value (decision 153, S17 D5)
  await expect(page.getByTestId('introPickups.bookable.search')).toHaveCount(0);
  await expect(page.getByTestId('introPickups.bookable.category')).toHaveCount(0);
  await expect(page.getByTestId('introPickups.bookable.fairteiler')).toHaveCount(0);

  await page.getByTestId('introPickups.bookable.myBookings').click();
  await expect(page).toHaveURL(/\/me\/intro-pickups$/);
  await expect(page.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', 'success');
});
