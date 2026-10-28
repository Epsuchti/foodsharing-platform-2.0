import { expect, test } from '../../fixtures';
import { acceptConfirm } from './_intro';

/** EA10: guides create, edit and delete EinAbs; the form is category-driven and shows server validation. */
test.use({ seedReset: 'test', role: 'guide' });

test('EA10 guide creates, edits, deletes EinAb with validation @EA10 @page', async ({ page }) => {
  await page.goto('/guide');
  const list = page.getByTestId('introPickups.guide.state');
  await expect(list).toHaveAttribute('data-state', 'success');
  const einabs = page.getByTestId('introPickups.guide.einab');
  await expect(einabs).toHaveCount(10);
  const form = page.getByTestId('introPickups.form');

  // online: a call link instead of an address, validated by the server
  await page.getByTestId('introPickups.guide.create').click();
  await form.getByTestId('introPickups.form.category').click();
  await page.getByRole('option', { name: 'En ligne' }).click();
  await expect(form.getByTestId('introPickups.form.callLink')).toBeVisible();
  await expect(form.getByTestId('introPickups.form.address')).toHaveCount(0);
  await expect(form.getByTestId('introPickups.form.slotCount')).toHaveCount(0);
  await form.getByTestId('introPickups.form.start').fill('2026-11-20T18:30');
  await page.getByTestId('introPickups.form.save').click();
  await expect(page.getByTestId('introPickups.form.error')).toBeVisible(); // ONLINE_CALL_LINK_REQUIRED
  await form.getByTestId('introPickups.form.callLink').fill('https://meet.jit.si/foodsharing-einab');
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();
  await expect(einabs).toHaveCount(11);
  // all seeded EinAbs start at 18:00 local time
  const online = einabs.filter({ hasText: '18:30' });
  await expect(online).toHaveAttribute('data-category', 'ONLINE');
  await expect(online.getByTestId('introPickups.slot')).toHaveCount(1);

  // a location EinAb with two places, then a third one
  await page.getByTestId('introPickups.guide.create').click();
  await form.getByTestId('introPickups.form.category').click();
  await page.getByRole('option', { name: 'Marché', exact: true }).click();
  await form.getByTestId('introPickups.form.start').fill('2026-11-21T09:15');
  await form.getByTestId('introPickups.form.businessName').fill('Marché Helvetiaplatz');
  await page.getByTestId('introPickups.form.save').click();
  await expect(page.getByTestId('introPickups.form.error')).toBeVisible(); // address required
  await form.getByTestId('introPickups.form.address').fill('Helvetiaplatz, 8004 Zürich');
  await form.getByTestId('introPickups.form.area').fill('Kreis 4');
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();
  const market = einabs.filter({ hasText: 'Marché Helvetiaplatz' });
  await expect(market.getByTestId('introPickups.slot')).toHaveCount(2);

  await market.getByTestId('introPickups.guide.edit').click();
  await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Helvetiaplatz, 8004 Zürich');
  await form.getByTestId('introPickups.form.slotCount').locator('input').fill('3');
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();
  await expect(market.getByTestId('introPickups.slot')).toHaveCount(3);

  // delete (no bookings)
  await market.getByTestId('introPickups.guide.delete').click();
  await acceptConfirm(page);
  await expect(einabs).toHaveCount(11);
  await online.getByTestId('introPickups.guide.delete').click();
  await acceptConfirm(page);
  await expect(einabs).toHaveCount(10);

  // past EinAbs cannot be changed
  await page.getByTestId('introPickups.guide.showPast').click();
  await expect(list).toHaveAttribute('data-state', 'success');
  await expect(einabs.first()).toBeVisible();
  await expect(page.getByTestId('introPickups.guide.edit')).toHaveCount(0);
});
