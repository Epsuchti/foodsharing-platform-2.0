import { expect, injectApiError, test } from '../../fixtures';

/** AD1 (§14.4 "request loading bar"): a thin progress bar at the top while API requests run. */

test.use({ seedReset: 'off' });

test('AD1 request bar shows during slow requests @AD1 @page', async ({ page }) => {
  await page.goto('/dev/gallery');
  const live = page.getByTestId('gallery.live.state');
  await expect(live).toHaveAttribute('data-state', 'success');
  const bar = page.getByTestId('shell.requestBar');
  await expect(bar).toBeHidden();

  const slow = await injectApiError(page, { url: '/api/public/app-info', delayMs: 2_500, times: 1 });
  await page.getByTestId('gallery.live.reload').click();

  await expect(bar).toBeVisible();
  await expect(bar).toHaveAttribute('role', 'progressbar');
  await expect(bar).toHaveAccessibleName(/.+/);
  // The data stays visible while it refreshes.
  await expect(live).toHaveAttribute('data-state', 'refreshing');
  await expect(page.getByTestId('gallery.live.languages')).toBeVisible();

  await expect(live).toHaveAttribute('data-state', 'success', { timeout: 10_000 });
  await expect(bar).toBeHidden();
  expect(slow.calls()).toBe(1);
});

test('AD1 request bar also ends when the slow request fails @AD1', async ({ page }) => {
  await page.goto('/dev/gallery');
  const live = page.getByTestId('gallery.live.state');
  await expect(live).toHaveAttribute('data-state', 'success');
  const bar = page.getByTestId('shell.requestBar');

  // 404 is not retried by the interceptor, so exactly one slow failing request runs.
  await injectApiError(page, { url: '/api/public/app-info', status: 404, delayMs: 1_500, times: 1 });
  await page.getByTestId('gallery.live.reload').click();
  await expect(bar).toBeVisible();
  await expect(live).toHaveAttribute('data-state', 'notFound', { timeout: 10_000 });
  await expect(bar).toBeHidden();
});
