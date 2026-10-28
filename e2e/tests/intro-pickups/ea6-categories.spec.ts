import { SEED_NOW, acceptPendingConsents, expect, test } from '../../fixtures';
import { ZH, acceptConfirm, bookableCards, openBookable } from './_intro';

/** EA6: all nine categories can be booked; ONLINE EinAbs show the call link instead of an address after booking. */
test.use({ seedReset: 'test', role: 'quiz' });

const CATEGORIES = ['SUPERMARKET', 'TAKEOUT', 'MARKET', 'BAKERY', 'RESTAURANT', 'FAIRTEILER_CLEANING', 'MESSE', 'GROSSABHOLUNG', 'ONLINE'];

test('EA6 every category incl. online with call link is bookable @EA6', async ({ page, api, apiOf }) => {
  // the seeded GROSSABHOLUNG needs one previous pickup: the guide adds one without a minimum
  await api.loginAs('guide');
  await api.postJson(`/api/regions/${ZH}/guide/intro-pickups`, {
    category: 'GROSSABHOLUNG',
    startAt: new Date(Date.parse(SEED_NOW) + 20 * 86_400_000).toISOString(),
    slotCount: 2,
    visitFairteiler: false,
    businessName: 'Gemüse Grosshandel',
    privateAddress: 'Hohlstrasse 400, 8048 Zürich',
    publicGeneralLocation: 'Altstetten',
  });

  await acceptPendingConsents(apiOf(page));
  await openBookable(page);
  for (const category of CATEGORIES) {
    await expect(bookableCards(page, category).getByTestId('introPickups.bookable.book').first(), category).toBeVisible();
  }

  await bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book').click();
  await acceptConfirm(page);
  await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
  await expect(page.getByTestId('introPickups.booked.callLink')).toHaveAttribute('href', /^https:\/\//);
});
