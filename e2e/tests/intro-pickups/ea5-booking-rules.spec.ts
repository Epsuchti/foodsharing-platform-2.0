import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, test } from '../../fixtures';
import { bookableCards, einabId, openBookable, reasonOf, setIntroSettings } from './_intro';

/**
 * EA5: the booking rules decide which EinAbs a newcomer sees. Only bookable EinAbs are listed (review 2026-11-10 B1);
 * when none is, the empty state says why. einab1 (Luca): 1 SUPERMARKET done with guide, bot's BAKERY booked. Region
 * rules are switched through the region settings (EA22) to isolate one rule per test.
 */
test.use({ seedReset: 'test', role: 'einab1' });

/** The card of seed EinAb `n` (`_intro.ts` SEED_CATEGORIES). */
function card(page: Page, n: number) {
  return page.locator(`[data-testid="introPickups.bookable.card"][data-id="${einabId(n)}"]`);
}

test('EA5 booking limit hides the dates it blocks @EA5', async ({ page, api, apiOf }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { maxActiveBookingsPerUser: 1, preventDuplicateGuideBookings: false });
  await openBookable(page);
  // ONLINE is an ignored category in Zürich: it does not count towards the limit
  await expect(bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book')).toBeVisible();
  await expect(bookableCards(page, 'TAKEOUT')).toHaveCount(0);
  expect(await reasonOf(apiOf(page), 2)).toBe('USER_BOOKING_LIMIT_REACHED');
  await expect(page.getByTestId('introPickups.bookable.reason')).toHaveCount(0);
  // every listed card can be booked
  const cards = page.getByTestId('introPickups.bookable.card');
  await expect(cards).toHaveCount(await page.getByTestId('introPickups.bookable.book').count());
});

test('EA5 duplicate guide and the own booking are not listed @EA5', async ({ page, apiOf }) => {
  await openBookable(page);
  // the test seed's guide gives every on-site EinAb but the online one: only ONLINE is left for Luca
  await expect(bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book')).toBeVisible();
  // SUPERMARKET 1: same guide as the done one; BAKERY 11: already booked
  await expect(card(page, 1)).toHaveCount(0);
  await expect(card(page, 11)).toHaveCount(0);
  expect(await reasonOf(apiOf(page), 1)).toBe('USER_ALREADY_BOOKED_WITH_GUIDE');
  expect(await reasonOf(apiOf(page), 11)).toBe('USER_ALREADY_BOOKED_IN_INTRO_PICKUP');
});

test('EA5 duplicate category is not listed @EA5', async ({ page, api, apiOf }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { preventDuplicateGuideBookings: false, requireFairteilerVisitByThirdIntroPickup: false });
  await openBookable(page);
  await expect(card(page, 1)).toHaveCount(0);
  expect(await reasonOf(apiOf(page), 1)).toBe('USER_ALREADY_BOOKED_IN_CATEGORY');
  await expect(bookableCards(page, 'TAKEOUT').first().getByTestId('introPickups.bookable.book')).toBeVisible();
});

test('EA5 Fairteiler visit by the third EinAb: only Fairteiler dates are listed @EA5', async ({ page, api, apiOf }) => {
  await api.loginAs('bot');
  await setIntroSettings(api, { preventDuplicateGuideBookings: false });
  await openBookable(page);
  // 1 done + 1 booked: the third must include a Fairteiler visit
  await expect(bookableCards(page, 'TAKEOUT')).toHaveCount(0);
  expect(await reasonOf(apiOf(page), 2)).toBe('FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP');
  const cleaning = bookableCards(page, 'FAIRTEILER_CLEANING').first();
  await expect(cleaning.getByTestId('introPickups.bookable.book')).toBeVisible();
});

test('EA5 minimum pickups hides the large pickup @EA5', async ({ as, apiOf }) => {
  // quiz@local: Zürich member without any pickup yet
  const page = await as('quiz');
  await acceptPendingConsents(apiOf(page));
  await openBookable(page);
  await expect(bookableCards(page, 'SUPERMARKET').first().getByTestId('introPickups.bookable.book')).toBeVisible();
  await expect(bookableCards(page, 'GROSSABHOLUNG')).toHaveCount(0);
  expect(await reasonOf(apiOf(page), 7)).toBe('MINIMUM_PICKUP_COUNT_NOT_REACHED');
});

test('EA5 nothing bookable: the empty state says why and links to the bookings @EA5', async ({ page, api }) => {
  await api.loginAs('bot');
  // Luca's booked BAKERY reaches a limit of 1 in every category (the guide and category rules come first, so off)
  await setIntroSettings(api, {
    maxActiveBookingsPerUser: 1,
    ignoredCategories: [],
    preventDuplicateGuideBookings: false,
    preventDuplicateCategoryBookings: false,
  });
  await openBookable(page);
  const state = page.getByTestId('introPickups.bookable.state');
  await expect(state).toHaveAttribute('data-state', 'empty');
  await expect(state).toHaveAttribute('data-empty-kind', 'enough');
  await expect(page.getByTestId('introPickups.bookable.card')).toHaveCount(0);
  await page.getByTestId('introPickups.bookable.state.action').click();
  await expect(page).toHaveURL(/\/me\/intro-pickups$/);
});
