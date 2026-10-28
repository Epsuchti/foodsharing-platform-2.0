import { expect, test } from '../../fixtures';

/** EA2: public next dates show the general area only; "notify me" leads to the login and then to the subscription. */

test('EA2 public slots show general area only and notify-me leads to login @EA2 @page', async ({ page }) => {
  await page.goto('/r/zuerich-stadt/intro-pickups');
  await expect(page.getByTestId('introPickups.public.state')).toHaveAttribute('data-state', 'success');
  const cards = page.getByTestId('introPickups.public.card');
  await expect(cards).toHaveCount(14);
  const first = cards.first();
  await expect(first.getByTestId('introPickups.card.location')).toHaveText('Kreis 4');
  // no exact address, no guide, no private info
  await expect(page.getByText('Badenerstrasse 1')).toHaveCount(0);
  await expect(page.locator('fs-user-chip')).toHaveCount(0);
  await expect(page.getByText('Personaleingang')).toHaveCount(0);

  await page.getByTestId('introPickups.public.notifyMe').click();
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  expect(decodeURIComponent(page.url())).toContain('/me/intro-pickups?subscribe=');
});

test('EA2 unknown region shows not found @EA2', async ({ page }) => {
  await page.goto('/r/no-such-region/intro-pickups');
  await expect(page.getByTestId('introPickups.public.regionState')).toHaveAttribute('data-state', 'notFound');
});
