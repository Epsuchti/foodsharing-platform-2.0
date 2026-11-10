import { type Page } from '@playwright/test';

import { expect, test } from '../../fixtures';

/**
 * C17 public reskin (decision 178, docs/tasks/2026-11-10-public-reskin-old-colours.md): while nobody is logged in, the
 * shell's <main> carries `fs-public` and every page inside it uses the old foodsharing palette, Optimus components
 * included (their primary tokens are re-declared in the scope). A logged-in user keeps the app colours everywhere.
 */

const OLD_GREEN = 'rgb(68, 128, 26)';

const main = (page: Page) => page.locator('main#main-content');

const primaryToken = (page: Page) =>
  main(page).evaluate((element) => getComputedStyle(element).getPropertyValue('--fs-primary').trim());

const background = (page: Page, testId: string) =>
  page.getByTestId(testId).evaluate((element) => {
    const button = element.matches('button, a') ? element : element.querySelector('button')!;
    return getComputedStyle(button).backgroundColor;
  });

test.describe('visitor', () => {
  test('C17 public pages use the old colours, Optimus buttons included @C17', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });

    await page.goto('/');
    await expect(main(page)).toHaveClass(/fs-public/);
    expect(await primaryToken(page)).toBe('#44801a');
    expect(await background(page, 'public.landing.joinEmail')).toBe(OLD_GREEN);

    await page.goto('/faq');
    await expect(main(page)).toHaveClass(/fs-public/);
    expect(await primaryToken(page)).toBe('#44801a');

    await page.goto('/login?method=email');
    await expect(main(page)).toHaveClass(/fs-public/);
    expect(await primaryToken(page)).toBe('#44801a');
    // a <p-button>: its colours come from the --p-* tokens re-declared in the scope
    await expect(page.getByTestId('auth.login.emailSubmit')).toBeVisible();
    expect(await background(page, 'auth.login.emailSubmit')).toBe(OLD_GREEN);
  });

  test('C17 public pages use the old dark green in dark mode @C17', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    for (const url of ['/', '/faq', '/login']) {
      await page.goto(url);
      await expect(page.locator('html')).toHaveClass(/fs-dark/);
      await expect(main(page)).toHaveClass(/fs-public/);
      expect(await primaryToken(page), url).toBe('#8fd14d');
    }
  });
});

test.describe('logged in', () => {
  test.use({ role: 'foodsaver' });

  test('C17 the logged-in app keeps the app colours on public pages too @C17', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    for (const url of ['/faq', '/dashboard']) {
      await page.goto(url);
      await expect(main(page)).toBeVisible();
      await expect(main(page)).not.toHaveClass(/fs-public/);
      expect(await primaryToken(page), url).toBe('#25834a');
    }
  });
});
