import { SEED_NOW, acceptPendingConsents } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZH, acceptConfirm, bookableCards, einabId, setIntroSettings } from '../../fixtures/flows/einab';
import { Walk } from '../../fixtures/persona';

/**
 * Newcomer Chiara (quiz@local, Zürich member without any pickup yet) books her intro pickups (EinAbs): the list of her
 * home region shows only what she can book, in every category and without filters (decision 156, S17 D5). She books a
 * supermarket EinAb, sees the private details and saves it to her calendar. Zürich's ambassador has set a limit of one
 * active booking, so only the online EinAb is left (online ones do not count), and she books it and gets its call link.
 * Now the list is empty and says she has booked enough (the booking rules of EA5 are
 * unit-tested in BookingRulesTest, the empty kinds in intro-pickups-pages.spec.ts). On "my intro pickups" she cancels the supermarket one, and its place is free again.
 */
test.use({ role: 'quiz' });

const CATEGORIES = ['SUPERMARKET', 'TAKEOUT', 'MARKET', 'BAKERY', 'RESTAURANT', 'FAIRTEILER_CLEANING', 'MESSE', 'GROSSABHOLUNG', 'ONLINE'];

test('newcomer books intro pickups in her home region and cancels one @EA1 @EA5 @EA6 @EA7', async ({ page, api, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  await acceptPendingConsents(apiOf(page));
  // the ambassador (Reto) allows one active booking at a time (online ones do not count) and drops the Fairteiler rule
  await api.loginAs('bot');
  await setIntroSettings(api, { maxActiveBookingsPerUser: 1, requireFairteilerVisitByThirdIntroPickup: false });
  // the seeded Grossabholung asks for one previous pickup: guide Julien adds one without a minimum
  await api.loginAs('guide');
  const large = await api.postJson<{ id: string }>(`/api/regions/${ZH}/guide/intro-pickups`, {
    category: 'GROSSABHOLUNG',
    startAt: new Date(Date.parse(SEED_NOW) + 20 * 86_400_000).toISOString(),
    slotCount: 2,
    visitFairteiler: false,
    businessName: 'Gemüse Grosshandel',
    privateAddress: 'Hohlstrasse 400, 8048 Zürich',
    publicGeneralLocation: 'Altstetten',
  });
  const supermarket = page.locator(`[data-testid="introPickups.bookable.card"][data-id="${einabId(1)}"]`);
  const online = bookableCards(page, 'ONLINE');

  await walk.step('opens the intro pickups of her region', async () => {
    await page.goto('/intro-pickups');
    await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'success');
    await expect(page.locator('fs-page-header')).toContainText('Zürich');
    for (const category of CATEGORIES) {
      const extra = category === 'GROSSABHOLUNG' ? [large.id] : [];
      await expect(bookableCards(page, category, extra).getByTestId('introPickups.bookable.book').first(), category).toBeVisible();
    }
    // every listed card can be booked, and there is no filter that could probe a hidden value
    await expect(page.getByTestId('introPickups.bookable.card')).toHaveCount(await page.getByTestId('introPickups.bookable.book').count());
    await expect(page.getByTestId('introPickups.bookable.search')).toHaveCount(0);
    await expect(page.getByTestId('introPickups.bookable.category')).toHaveCount(0);
    await expect(supermarket.getByTestId('introPickups.card.free')).toContainText('3');
    await checkpoint(page, 'bookable-list');
  });

  await walk.step('books the supermarket EinAb and saves it to her calendar', async () => {
    await walk.click(supermarket.getByTestId('introPickups.bookable.book'));
    await expect(page.locator('.p-confirmdialog')).toBeVisible();
    await checkpoint(page, 'confirm-booking');
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
    await expect(page.getByTestId('introPickups.booked.private')).toContainText('Badenerstrasse 1');
    await checkpoint(page, 'booking-success');
    const download = page.waitForEvent('download');
    await walk.click(page.getByTestId('introPickups.bookable.calendar'));
    const file = await download;
    expect(file.suggestedFilename()).toBe('einab.ics');
    const ics = Buffer.concat(await (await file.createReadStream()).toArray()).toString('utf8');
    expect(ics).toContain('BEGIN:VCALENDAR');
  });

  await walk.step('books the online EinAb and gets its call link', async () => {
    await page.goto('/intro-pickups');
    await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'success');
    // the booking limit hides every on-site date now
    await expect(supermarket).toHaveCount(0);
    await expect(bookableCards(page, 'NONE', [large.id])).toHaveCount(0);
    await expect(page.getByTestId('introPickups.bookable.card')).toHaveCount(1);
    await walk.click(online.getByTestId('introPickups.bookable.book'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
    await expect(page.getByTestId('introPickups.booked.callLink')).toHaveAttribute('href', /^https:\/\//);
  });

  await walk.step('sees that she has booked enough for now', async () => {
    await page.goto('/intro-pickups');
    const state = page.getByTestId('introPickups.bookable.state');
    await expect(state).toHaveAttribute('data-state', 'empty');
    await expect(state).toHaveAttribute('data-empty-kind', 'enough');
    await expect(page.getByTestId('introPickups.bookable.card')).toHaveCount(0);
    await checkpoint(page, 'nothing-left');
  });

  await walk.step('cancels the supermarket EinAb in her bookings', async () => {
    await walk.nav('introPickups');
    await expect(page).toHaveURL(/\/me\/intro-pickups$/);
    await expect(page.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', 'success');
    const upcoming = page.getByTestId('introPickups.my.upcomingCard');
    await expect(upcoming).toHaveCount(2);
    const booked = upcoming.filter({ hasText: 'Personaleingang' });
    await expect(booked.getByTestId('introPickups.my.calendar')).toBeVisible();
    await checkpoint(page, 'my-bookings');
    await walk.click(booked.getByTestId('introPickups.my.cancel'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(upcoming).toHaveCount(1);
    await checkpoint(page, 'after-cancel');
  });

  await walk.step('finds the place free again', async () => {
    await page.goto('/intro-pickups');
    await expect(supermarket.getByTestId('introPickups.card.free')).toContainText('3');
    await expect(supermarket.getByTestId('introPickups.bookable.book')).toBeVisible();
  });

  walk.finish({ steps: 6, clicks: 9, fields: 0 });
});
