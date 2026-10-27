import { expect, test, type Theme } from '../../fixtures';
import { skipPasskeyNudge } from '../auth/_auth';

/** A17: theme switcher light / dark / system, saved with the account; `system` follows prefers-color-scheme. */

test.use({ role: 'foodsaver' });

test('A17 theme light, dark and system @A17 @page', async ({ page, apiOf }) => {
  await skipPasskeyNudge(page);
  const api = apiOf(page);
  const html = page.locator('html');
  const theme = async () => (await api.getJson<{ theme: Theme }>('/api/users/me')).theme;
  const choose = async (mode: 'light' | 'dark' | 'system') => {
    const saved = page.waitForResponse(
      (response) => response.url().endsWith('/api/users/me/settings') && response.request().method() === 'PATCH',
    );
    await page.getByTestId(`account.settings.theme.${mode}`).click();
    expect((await saved).status()).toBe(200);
    await expect(page.getByTestId(`account.settings.theme.${mode}`)).toHaveAttribute('aria-checked', 'true');
  };

  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.preferences')).toBeVisible();

  await choose('dark');
  await expect(html).toHaveClass(/fs-dark/);
  expect(await theme()).toBe('DARK');
  await page.reload();
  await expect(html).toHaveClass(/fs-dark/);

  await choose('light');
  await expect(html).not.toHaveClass(/fs-dark/);
  expect(await theme()).toBe('LIGHT');
  // an explicit choice ignores the device setting
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(html).not.toHaveClass(/fs-dark/);

  await choose('system');
  expect(await theme()).toBe('SYSTEM');
  await expect(html).toHaveClass(/fs-dark/);
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(html).not.toHaveClass(/fs-dark/);
  // a fresh load keeps "system" (and lets the accessibility check run without colour transitions in flight)
  await page.reload();
  await expect(page.getByTestId('account.settings.theme.system')).toHaveAttribute('aria-checked', 'true');
  await expect(html).not.toHaveClass(/fs-dark/);
});
