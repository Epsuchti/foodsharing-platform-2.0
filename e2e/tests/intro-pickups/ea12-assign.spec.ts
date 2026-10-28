import { acceptPendingConsents, expect, test } from '../../fixtures';
import { einabId } from './_intro';

/** EA12: guides book a newcomer on their behalf and hand an EinAb over to another guide. */
test.use({ seedReset: 'test', role: 'guide' });

test('EA12 assign guide and book on behalf @EA12', async ({ page, as, apiOf }) => {
  await page.goto('/guide');
  const einab = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`);
  await expect(einab.getByTestId('introPickups.slot')).toHaveCount(3);

  // book quiz@local (Chiara Rossi) on the first free slot
  await einab.getByTestId('introPickups.slot.bookFor').first().click();
  const pick = page.getByTestId('introPickups.pick');
  await pick.getByTestId('introPickups.pick.search').fill('Chiara');
  const choose = pick.getByTestId('introPickups.pick.choose');
  await expect(choose).toHaveCount(1);
  await choose.click();
  await expect(pick).toBeHidden();
  const booked = einab.locator('[data-testid="introPickups.slot"][data-status="BOOKED"]');
  await expect(booked).toHaveCount(1);
  await expect(booked).toContainText('Chiara');

  // the newcomer sees the booking
  const quiz = await as('quiz');
  await acceptPendingConsents(apiOf(quiz));
  await quiz.goto('/me/intro-pickups');
  await expect(quiz.getByTestId('introPickups.my.upcomingCard')).toHaveCount(1);

  // hand the EinAb over to Reto Baumann (bot)
  await einab.getByTestId('introPickups.guide.assign').click();
  const assign = page.getByTestId('introPickups.assign');
  await assign.getByTestId('introPickups.assign.guide').click();
  await page.getByRole('option', { name: 'Reto Baumann' }).click();
  await assign.getByTestId('introPickups.assign.save').click();
  await expect(einab).toHaveCount(0);

  const bot = await as('bot');
  await bot.goto('/guide');
  await expect(bot.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`)).toBeVisible();
});
