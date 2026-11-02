import { expect, test } from '../../fixtures';

/** EA2: public next dates show date, on site or online, city and free places; "notify me" leads to the login and then to the subscription. */

test('EA2 public slots show only date, mode, city and free places and notify-me leads to login @EA2 @page', async ({ page }) => {
  await page.goto('/r/zuerich-stadt/intro-pickups');
  await expect(page.getByTestId('introPickups.public.state')).toHaveAttribute('data-state', 'success');
  const cards = page.getByTestId('introPickups.public.card');
  await expect(cards).toHaveCount(14);
  const first = cards.first();
  await expect(first.getByTestId('introPickups.card.mode')).toBeVisible();
  await expect(first.getByTestId('introPickups.card.city')).toHaveText('Zürich Stadt');
  await expect(first.getByTestId('introPickups.card.free')).toBeVisible();
  // no category, business, area text, exact address, guide or private info (decision 156, S17 D5)
  await expect(page.getByTestId('introPickups.card.category')).toHaveCount(0);
  await expect(page.getByTestId('introPickups.card.location')).toHaveCount(0);
  await expect(page.getByText('Kreis 4')).toHaveCount(0);
  await expect(page.getByText('Babu')).toHaveCount(0);
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
