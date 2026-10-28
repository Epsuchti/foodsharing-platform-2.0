import type { Page } from '@playwright/test';
import { expect, test } from '../../fixtures';
import { animationsDone } from '../a11y/animations';

/**
 * D4.3 shell: header navigation on desktop, bottom tab bar + menu drawer on phones (plan §12.5). Each test sets its
 * own viewport so both layouts are proven in every browser project.
 */

// Default seed reset per file: the mobile test changes the account language/theme of the foodsaver.

const DESKTOP = { width: 1280, height: 800 };
const PHONE = { width: 390, height: 844 };

function path(page: Page): string {
  return new URL(page.url()).pathname;
}

test.describe('logged in', () => {
  test.use({ role: 'foodsaver' });

  test('D4.3 desktop header navigation @D4.3 @page', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await page.goto('/dashboard');

    const nav = page.getByRole('navigation', { name: /.+/ }).filter({ has: page.getByTestId('nav.dashboard') });
    await expect(nav).toBeVisible();
    await expect(page.getByTestId('nav.dashboard')).toHaveClass(/hdr__link--active/);
    await expect(page.getByTestId('nav.activity')).toHaveAttribute('href', '/me/activity');
    // The dev gallery lives in the footer, not in the main navigation.
    await expect(page.getByTestId('nav.gallery')).toHaveCount(0);
    await expect(page.getByTestId('nav.footer.gallery')).toBeVisible();
    // Features of later milestones stay hidden.
    await expect(page.getByTestId('nav.pickups')).toHaveCount(0);
    // Desktop has no tab bar; the user menu sits in the header.
    await expect(page.getByTestId('shell.tabBar')).toBeHidden();
    await expect(page.getByTestId('shell.userMenu')).toBeVisible();
    await expect(page.getByTestId('shell.login')).toHaveCount(0);

    await page.getByTestId('nav.activity').click();
    await expect.poll(() => path(page)).toBe('/me/activity');
    await expect(page.getByTestId('nav.activity')).toHaveClass(/hdr__link--active/);
    await expect(page.getByTestId('nav.dashboard')).not.toHaveClass(/hdr__link--active/);

    await page.getByTestId('shell.brand').click();
    await expect.poll(() => path(page)).toBe('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();

    // Footer links and version.
    await expect(page.getByTestId('nav.footer.privacy')).toHaveAttribute('href', '/legal/privacy-policy');
    await expect(page.getByTestId('shell.version')).toBeVisible();
  });

  test('D4.3 mobile tab bar and menu drawer @D4.3 @page', async ({ page }) => {
    await page.setViewportSize(PHONE);
    await page.goto('/dashboard');

    const tabBar = page.getByTestId('shell.tabBar');
    await expect(tabBar).toBeVisible();
    await expect(page.getByTestId('nav.dashboard')).toBeHidden();
    const dashboardTab = page.getByTestId('nav.tab.dashboard');
    await expect(dashboardTab).toHaveAttribute('aria-current', 'page');
    // The tab bar does not cover the page: the footer can be scrolled above it.
    const footerVersion = page.getByTestId('shell.version');
    await footerVersion.scrollIntoViewIfNeeded();
    const versionBox = await footerVersion.boundingBox();
    const barBox = await tabBar.boundingBox();
    expect(versionBox && barBox && versionBox.y + versionBox.height <= barBox.y + 1).toBeTruthy();

    const menuTab = page.getByTestId('nav.tab.menu');
    await expect(menuTab).toHaveAttribute('aria-expanded', 'false');
    await menuTab.click();
    await expect(menuTab).toHaveAttribute('aria-expanded', 'true');
    // Appended to <body> (outside the fixed tab bar's stacking context, so the modal mask stays behind it).
    const drawer = page.locator('.fs-mobile-menu');
    await expect(drawer).toBeVisible();
    // The drawer sits above its modal mask (not dimmed by it).
    await animationsDone(page);
    const activityLink = drawer.getByTestId('nav.drawer.activity');
    const linkBox = (await activityLink.boundingBox())!;
    const onTop = await page.evaluate(
      ([x, y]) => !!document.elementFromPoint(x, y)?.closest('.fs-mobile-menu'),
      [linkBox.x + linkBox.width / 2, linkBox.y + linkBox.height / 2] as const,
    );
    expect(onTop, 'drawer is the top-most element at its links').toBe(true);
    await expect(drawer.getByTestId('nav.drawer.activity')).toHaveAttribute('href', '/me/activity');
    await expect(drawer.getByTestId('nav.drawer.account')).toHaveAttribute('href', '/account');

    // Language chips switch the UI language immediately.
    await drawer.getByTestId('shell.drawer.language.fr').click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    await expect(drawer.getByTestId('shell.drawer.language.fr')).toHaveAttribute('aria-pressed', 'true');
    // Theme chips switch the theme.
    await drawer.getByTestId('shell.drawer.theme.dark').click();
    await expect(page.locator('html')).toHaveClass(/fs-dark/);
    await drawer.getByTestId('shell.drawer.theme.light').click();
    await expect(page.locator('html')).not.toHaveClass(/fs-dark/);

    await drawer.getByTestId('nav.drawer.activity').click();
    await expect(drawer).toBeHidden();
    await expect.poll(() => path(page)).toBe('/me/activity');
    await expect(dashboardTab).not.toHaveAttribute('aria-current', 'page');
    // Activity and the account are tabs of their own (M1 has no pickups/messages yet).
    await expect(page.getByTestId('nav.tab.activity')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('nav.tab.account')).toHaveAttribute('href', '/account');
    // No horizontal overflow on a phone (it would widen the layout viewport and move the fixed tab bar).
    await expect(page.getByTestId('history.myActivity.list')).toHaveAttribute('data-state', 'success');
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth))
      .toBeLessThanOrEqual(0);

    await dashboardTab.click();
    await expect.poll(() => path(page)).toBe('/dashboard');
    await expect(dashboardTab).toHaveAttribute('aria-current', 'page');
  });
});

test.describe('logged out', () => {
  test('D4.3 anonymous shell shows login instead of navigation tabs @D4.3', async ({ page }) => {
    for (const viewport of [DESKTOP, PHONE]) {
      await page.setViewportSize(viewport);
      await page.goto('/');
      await expect(page.getByTestId('shell.login')).toBeVisible();
      await expect(page.getByTestId('shell.tabBar')).toHaveCount(0);
      await expect(page.getByTestId('shell.userMenu')).toHaveCount(0);
      if (viewport === DESKTOP) await expect(page.getByTestId('nav.home')).toBeVisible();
      else await expect(page.getByTestId('nav.home')).toBeHidden();
    }
  });

  test('D4.3 skip link jumps to the main content @D4.3', async ({ page, browserName }) => {
    await page.setViewportSize(DESKTOP);
    await page.goto('/dev/gallery');
    await expect(page.locator('main h1').first()).toBeVisible();
    // Safari only tabs to links with Option+Tab (default macOS setting).
    await page.keyboard.press(browserName === 'webkit' ? 'Alt+Tab' : 'Tab');
    const skip = page.getByTestId('shell.skipLink');
    await expect(skip).toBeFocused();
    await expect(skip).toBeInViewport();
    await page.keyboard.press('Enter');
    await expect(page.locator('#main-content')).toBeFocused();
  });
});
