import { acceptPendingConsents, expect, test } from '../../fixtures';
import { bookableCards, openBookable, setIntroSettings } from './_intro';

/**
 * EA5: every booking rule shows its translated reason on the card (einab1: 1 SUPERMARKET done with guide, bot's
 * BAKERY booked). Region rules are switched through the region settings (EA22) to isolate one rule per test.
 */
test.use({ seedReset: 'test', role: 'einab1' });

function reasonOf(page: import('@playwright/test').Page, category: string, index = 0) {
  return bookableCards(page, category).nth(index).getByTestId('introPickups.bookable.reason');
}

test('EA5 booking limit shows its reason @EA5', async ({ page, api }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { maxActiveBookingsPerUser: 1, preventDuplicateGuideBookings: false });
  await openBookable(page);
  const takeout = reasonOf(page, 'TAKEOUT');
  await expect(takeout).toHaveAttribute('data-reason', 'USER_BOOKING_LIMIT_REACHED');
  await expect(takeout).toHaveText(/\S/);
  // ONLINE is an ignored category in Zürich: it does not count towards the limit
  await expect(bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book')).toBeVisible();
});

test('EA5 duplicate guide shows its reason @EA5', async ({ page }) => {
  await openBookable(page);
  await expect(reasonOf(page, 'SUPERMARKET')).toHaveAttribute('data-reason', 'USER_ALREADY_BOOKED_WITH_GUIDE');
  await expect(reasonOf(page, 'BAKERY')).toHaveAttribute('data-reason', 'USER_ALREADY_BOOKED_IN_INTRO_PICKUP');
});

test('EA5 duplicate category shows its reason @EA5', async ({ page, api }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { preventDuplicateGuideBookings: false, requireFairteilerVisitByThirdIntroPickup: false });
  await openBookable(page);
  await expect(reasonOf(page, 'SUPERMARKET')).toHaveAttribute('data-reason', 'USER_ALREADY_BOOKED_IN_CATEGORY');
  await expect(bookableCards(page, 'TAKEOUT').first().getByTestId('introPickups.bookable.book')).toBeVisible();
});

test('EA5 Fairteiler visit by the third EinAb shows its reason @EA5', async ({ page, api }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { preventDuplicateGuideBookings: false });
  await openBookable(page);
  // 1 done + 1 booked: the third must include a Fairteiler visit
  await expect(reasonOf(page, 'TAKEOUT')).toHaveAttribute('data-reason', 'FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP');
  const cleaning = bookableCards(page, 'FAIRTEILER_CLEANING').first();
  await expect(cleaning.getByTestId('introPickups.card.fairteiler')).toBeVisible();
  await expect(cleaning.getByTestId('introPickups.bookable.book')).toBeVisible();
});

test('EA5 minimum pickups shows its reason @EA5', async ({ as, apiOf }) => {
  // quiz@local: Zürich member without any pickup yet
  const page = await as('quiz');
  await acceptPendingConsents(apiOf(page));
  await openBookable(page);
  await expect(bookableCards(page, 'SUPERMARKET').first().getByTestId('introPickups.bookable.book')).toBeVisible();
  await expect(reasonOf(page, 'GROSSABHOLUNG')).toHaveAttribute('data-reason', 'MINIMUM_PICKUP_COUNT_NOT_REACHED');
});
