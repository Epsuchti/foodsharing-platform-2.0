import { expect, test } from '../../fixtures';
import { ZH, bookableCards, openBookable } from './_intro';

/** EA22: the region's EinAb settings (region settings tab) change what newcomers can book. */
test.use({ seedReset: 'test', role: 'bot' });

test('EA22 region EinAb settings change booking behaviour @EA22', async ({ page, as }) => {
  const einab1 = await as('einab1');
  await openBookable(einab1);
  const reason = bookableCards(einab1, 'TAKEOUT').first().getByTestId('introPickups.bookable.reason');
  await expect(reason).toHaveAttribute('data-reason', 'USER_ALREADY_BOOKED_WITH_GUIDE');

  // the ambassador allows several EinAbs with the same guide
  await page.goto(`/regions/${ZH}/settings`);
  await expect(page.getByTestId('regions.settings.state')).toHaveAttribute('data-state', 'success');
  await page.locator('#set-guide').click();
  await page.getByTestId('regions.settings.save').click();
  await expect(page.getByTestId('shell.toast')).toBeVisible();

  // now the third booking must include a Fairteiler visit (still a region rule)
  await openBookable(einab1);
  await expect(reason).toHaveAttribute('data-reason', 'FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP');

  // … until that rule is switched off, too
  await page.getByTestId('regions.settings.requireFairteiler').click();
  await page.getByTestId('regions.settings.save').click();
  await openBookable(einab1);
  await expect(bookableCards(einab1, 'TAKEOUT').first().getByTestId('introPickups.bookable.book')).toBeVisible();
});
