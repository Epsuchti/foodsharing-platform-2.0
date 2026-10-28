import { expect, test } from '../../fixtures';

/** 403/404 pages: one button set (primary + back); anonymous visitors of /forbidden are offered the login. */

test.use({ seedReset: 'off' });

test('D4.3 forbidden page offers the login to anonymous visitors and comes back @D4.3 @page', async ({ page }) => {
  await page.goto('/dashboard');
  await page.goto('/forbidden?returnUrl=%2Fme%2Factivity');
  const primary = page.getByTestId('errorPages.forbidden.primary');
  await expect(primary).toHaveAttribute('href', '/login?returnUrl=%2Fme%2Factivity');
  await expect(page.getByTestId('errorPages.forbidden.back')).toBeVisible();

  // a foreign return URL is never passed on
  await page.goto('/forbidden?returnUrl=https%3A%2F%2Fevil.example');
  await expect(page.getByTestId('errorPages.forbidden.primary')).toHaveAttribute('href', '/login');
});

test.describe('signed in', () => {
  test.use({ role: 'foodsaver' });

  test('D4.3 forbidden and not-found pages share home and back buttons @D4.3', async ({ page }) => {
    await page.goto('/dashboard');
    await page.goto('/forbidden');
    await expect(page.getByTestId('errorPages.forbidden.primary')).toHaveAttribute('href', '/dashboard');

    await page.goto('/some/deep/link');
    await expect(page.getByTestId('errorPages.notFound.primary')).toHaveAttribute('href', '/dashboard');
    await page.getByTestId('errorPages.notFound.back').click();
    await expect.poll(() => new URL(page.url()).pathname).toBe('/forbidden');
  });
});
