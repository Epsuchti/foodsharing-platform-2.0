import { expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA18: user managers delete unbooked EinAbs with a reason, add past EinAbs for a member and adjust counting. */
test.use({ seedReset: 'test', role: 'bot' });

test('EA18 admin deletes unbooked with reason, creates historical, adjusts count @EA18 @D10.3 @page', async ({ page, as }) => {
  await page.goto(`/regions/home/intro-pickups/admin`);
  await expect(page).toHaveURL(new RegExp(`/regions/${ZH}/intro-pickups/admin$`));
  const rows = page.getByTestId('introPickups.admin.einab');
  await expect(rows).toHaveCount(14);

  // delete an unbooked EinAb with a reason (booked ones have no delete button)
  await rows.first().getByTestId('introPickups.admin.delete').click();
  await page.getByTestId('introPickups.admin.deleteDialog.message').fill('Betrieb hat geschlossen, Termin entfällt.');
  await page.getByTestId('introPickups.admin.deleteDialog.confirm').click();
  await expect(rows).toHaveCount(13);
  await expect(rows.filter({ hasText: '1 / 1' }).getByTestId('introPickups.admin.delete')).toHaveCount(0);

  // a past EinAb for Luca (einab1), added afterwards
  await page.getByTestId('introPickups.admin.historicalOpen').click();
  await page.getByTestId('introPickups.pick.search').fill('Luca');
  await expect(page.getByTestId('introPickups.pick.choose')).toHaveCount(1);
  await page.getByTestId('introPickups.pick.choose').click();
  const form = page.getByTestId('introPickups.admin.historicalForm');
  await form.getByTestId('introPickups.admin.historicalStart').fill('2026-10-01T10:00');
  await form.getByTestId('introPickups.admin.historicalBusiness').fill('Coop Altstetten');
  await page.getByTestId('introPickups.admin.historicalSave').click();
  await expect(form).toBeHidden();

  await page.getByTestId('introPickups.admin.tab.bookings').click();
  await page.getByTestId('introPickups.admin.pastBookings').click();
  await expect(page.getByTestId('introPickups.admin.bookingsState')).toHaveAttribute('data-state', 'success');
  const bookings = page.getByTestId('introPickups.admin.booking');
  await expect(bookings.filter({ hasText: 'Luca' })).toHaveCount(2);

  // a cancelled booking (sleepy, not counted) is counted after all
  const statuses = await bookings.evaluateAll((rows) => rows.map((row) => row.getAttribute('data-status')));
  const cancelled = bookings.nth(statuses.indexOf('CANCELLED'));
  const toggle = cancelled.getByTestId('introPickups.admin.counted').locator('input');
  await expect(toggle).not.toBeChecked();
  await cancelled.getByTestId('introPickups.admin.counted').click();
  await expect(toggle).toBeChecked();

  // einab1 now has two counted intro pickups
  const einab1 = await as('einab1');
  await einab1.goto('/me/intro-pickups');
  await expect(einab1.getByTestId('introPickups.my.progress')).toHaveAttribute('data-done', '2');
});

test('EA18 admin comments are marked @EA18 @EA13 @D10.3', async ({ page }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/admin`);
  await page.getByTestId('introPickups.admin.tab.bookings').click();
  await page.getByTestId('introPickups.admin.pastBookings').click();
  const booking = page.getByTestId('introPickups.admin.booking').first();
  await booking.getByTestId('introPickups.admin.comments').click();
  const drawer = page.getByTestId('introPickups.comments');
  await expect(drawer.getByTestId('introPickups.comments.state')).toHaveAttribute('data-state', /success|empty/);
  await drawer.getByTestId('introPickups.comments.text').fill('Ausweis vor Ort gesehen.');
  await drawer.getByTestId('introPickups.comments.send').click();
  const mine = drawer.getByTestId('introPickups.comments.item').filter({ hasText: 'Ausweis vor Ort' });
  await expect(mine.getByTestId('introPickups.comments.admin')).toBeVisible();
});
