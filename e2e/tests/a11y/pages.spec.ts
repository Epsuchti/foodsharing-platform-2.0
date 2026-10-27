import { expect, expectNoSeriousViolations, test, type Role } from '../../fixtures';
import type { Page } from '@playwright/test';
import { animationsDone } from './animations';
import { THEME_STORAGE_KEY } from '../../fixtures/i18n';

/**
 * Axe sweep (plan §18.3 "no serious/critical violations"): every M1 route as the role that normally sees it, in the
 * light and the dark theme. Routes of features that are not built yet render the not-found page, which is swept too.
 */

// Default seed reset per file: account theme/language of the seed users are SYSTEM/de, so the emulated color scheme
// decides the theme of logged-in pages.

interface RouteCase {
  path: string;
  role: Role | null;
  /** Something that proves the page rendered (beyond the shell). */
  ready?: string;
}

const ROUTES: RouteCase[] = [
  { path: '/', role: null, ready: 'public.landing.hero' },
  { path: '/login', role: null },
  { path: '/forbidden', role: null },
  { path: '/does-not-exist', role: null },
  { path: '/dev/gallery', role: null, ready: 'gallery.dataState.live' },
  { path: '/dashboard', role: 'newbie', ready: 'dashboard.page.welcome' },
  { path: '/dashboard', role: 'orga', ready: 'dashboard.page.welcome' },
  { path: '/account/settings', role: 'foodsaver' },
  { path: '/account/sessions', role: 'foodsaver' },
  { path: '/account/security-log', role: 'foodsaver' },
  { path: '/me/activity', role: 'foodsaver' },
];

async function settle(page: Page, ready?: string): Promise<void> {
  if (ready) await expect(page.getByTestId(ready)).toBeVisible();
  await expect(page.locator('main#main-content')).toBeVisible();
  // Real DataStates only: the gallery's static specimens (`gallery.dataState.loading`, …) stay loading on purpose.
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          [...document.querySelectorAll('[data-state="loading"], [data-state="refreshing"]')].filter(
            (node) => !node.closest('section[data-testid^="gallery."]:not([data-testid="gallery.dataState.live"])'),
          ).length,
      ),
    )
    .toBe(0);
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(theme, () => {
    test.use({ colorScheme: theme });

    for (const route of ROUTES) {
      test(`AD1 axe ${route.path} as ${route.role ?? 'visitor'} (${theme}) @AD1`, async ({ as }) => {
        const page = await as(route.role);
        await page.addInitScript(
          ([key, value]) => window.localStorage.setItem(key, value),
          [THEME_STORAGE_KEY, theme] as const,
        );
        await page.goto(route.path);
        await settle(page, route.ready);
        if (theme === 'dark') await expect(page.locator('html')).toHaveClass(/fs-dark/);
        await animationsDone(page);
        await expectNoSeriousViolations(page);
      });
    }

    test(`AD1 axe shell overlays (${theme}) @AD1`, async ({ as }) => {
      const page = await as('foodsaver');
      await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [THEME_STORAGE_KEY, theme] as const);

      // Desktop: language and theme popovers.
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/dashboard');
      await settle(page, 'dashboard.page.welcome');
      await page.getByTestId('shell.languageMenu').click();
      await expect(page.getByTestId('shell.language.fr')).toBeVisible();
      await animationsDone(page);
      await expectNoSeriousViolations(page);
      await page.keyboard.press('Escape');
      await page.getByTestId('shell.themeMenu').click();
      await expect(page.getByTestId('shell.theme.dark')).toBeVisible();
      await animationsDone(page);
      await expectNoSeriousViolations(page);
      await page.keyboard.press('Escape');

      // Phone: tab bar and the menu drawer.
      await page.setViewportSize({ width: 390, height: 844 });
      await expect(page.getByTestId('shell.tabBar')).toBeVisible();
      await animationsDone(page);
      await expectNoSeriousViolations(page);
      await page.getByTestId('nav.tab.menu').click();
      await expect(page.getByTestId('nav.drawer.gallery')).toBeVisible();
      await animationsDone(page);
      await expectNoSeriousViolations(page);
    });

    test(`AD1 axe offline banner and offline data state (${theme}) @AD1`, async ({ page, context }) => {
      await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [THEME_STORAGE_KEY, theme] as const);
      await page.goto('/dev/gallery');
      await settle(page, 'gallery.dataState.live');
      await context.setOffline(true);
      await expect(page.getByTestId('shell.offlineBanner')).toBeVisible();
      await page.getByTestId('gallery.live.reload').click();
      await expect(page.getByTestId('gallery.live.state')).toHaveAttribute('data-state', 'offline');
      await animationsDone(page);
      await expectNoSeriousViolations(page);
      await context.setOffline(false);
    });
  });
}
