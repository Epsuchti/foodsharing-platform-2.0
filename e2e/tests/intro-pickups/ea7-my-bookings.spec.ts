import { acceptPendingConsents, expect, test } from '../../fixtures';
import { acceptConfirm, bookableCards, openBookable } from './_intro';

/** EA7: book with one tap (+ confirmation), see the private details, export to the calendar, cancel. */
test.use({ seedReset: 'test', role: 'quiz' });

test('EA7 book and cancel, calendar export @EA7 @page', async ({ page, apiOf }) => {
  await acceptPendingConsents(apiOf(page));
  await openBookable(page);
  const card = bookableCards(page, 'SUPERMARKET').first();
  await expect(card.getByTestId('introPickups.card.free')).toContainText('3');
  await card.getByTestId('introPickups.bookable.book').click();
  await acceptConfirm(page);

  const success = page.getByTestId('introPickups.bookable.success');
  await expect(success).toBeVisible();
  await expect(page.getByTestId('introPickups.booked.private')).toContainText('Badenerstrasse 1');
  const download = page.waitForEvent('download');
  await page.getByTestId('introPickups.bookable.calendar').click();
  const file = await download;
  expect(file.suggestedFilename()).toBe('einab.ics');
  const ics = await (await file.createReadStream()).toArray();
  expect(Buffer.concat(ics).toString('utf8')).toContain('BEGIN:VCALENDAR');

  await page.getByTestId('introPickups.bookable.toMyBookings').click();
  await expect(page).toHaveURL(/\/me\/intro-pickups$/);
  const upcoming = page.getByTestId('introPickups.my.upcomingCard');
  await expect(upcoming).toHaveCount(1);
  await expect(upcoming.getByTestId('introPickups.booked.private')).toContainText('Personaleingang');
  await expect(upcoming.getByTestId('introPickups.my.calendar')).toBeVisible();

  await upcoming.getByTestId('introPickups.my.cancel').click();
  await acceptConfirm(page);
  await expect(page.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', 'empty');

  // the slot is free again
  await openBookable(page);
  await expect(bookableCards(page, 'SUPERMARKET').first().getByTestId('introPickups.card.free')).toContainText('3');
});
