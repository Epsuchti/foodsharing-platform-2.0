import { acceptPendingConsents, expect, expectNoSeriousViolations, test } from '../../fixtures';
import { devices, type Page } from '@playwright/test';
import type { ApiClient } from '../../fixtures/api';
import { animationsDone } from './animations';
import { THEME_STORAGE_KEY } from '../../fixtures/i18n';
import { SWEEP_ROUTES, type RouteCase } from './routes';

/**
 * Axe sweep (plan §18.3 "no serious/critical violations", plan-m7 F6): every route of the app (table in routes.ts,
 * kept complete by `scripts/a11y-route-coverage.mjs`) as the role that normally sees it, in the light and the dark
 * theme, plus the top-level pages on a phone (Pixel 7).
 */

// Default seed reset per file: account theme/language of the seed users are SYSTEM/de, so the emulated color scheme
// decides the theme of logged-in pages.

/** Data states that mean "this page did not render what the sweep wants to see" unless the route expects them. */
const FAILED_STATES = ['error', 'forbidden', 'notFound', 'offline'];

async function settle(page: Page, route: Pick<RouteCase, 'ready' | 'states'>): Promise<void> {
  if (route.ready) await expect(page.getByTestId(route.ready).first()).toBeVisible();
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
  const unexpected = FAILED_STATES.filter((state) => !(route.states as string[] | undefined)?.includes(state));
  const failed = await page.evaluate(
    (states) =>
      [...document.querySelectorAll('main [data-state]')]
        .filter((node) => states.includes(node.getAttribute('data-state') ?? '') && !node.closest('section[data-testid^="gallery."]'))
        .map((node) => `${node.getAttribute('data-testid') ?? node.tagName}=${node.getAttribute('data-state')}`),
    unexpected,
  );
  expect(failed, 'data states that did not load').toEqual([]);
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
  // Lazy routes render in steps: wait for a heading and until Optimus buttons have their label/icon.
  await expect(page.locator('main h1, main h2').first()).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          [...document.querySelectorAll('main button')].filter(
            (button) => !button.textContent?.trim() && !button.getAttribute('aria-label') && !button.querySelector('[class*="pi-"], svg'),
          ).length,
      ),
    )
    .toBe(0);
}

interface SweepFixtures {
  as: (role: RouteCase['role']) => Promise<Page>;
  apiOf: (page: Page) => ApiClient;
}

async function sweep({ as, apiOf }: SweepFixtures, route: RouteCase, theme: 'light' | 'dark'): Promise<void> {
  const page = await as(route.role);
  if (route.role && route.consent !== false) await acceptPendingConsents(apiOf(page));
  await route.setup?.(apiOf(page));
  await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [THEME_STORAGE_KEY, theme] as const);
  await page.goto(route.path);
  await settle(page, route);
  if (theme === 'dark') await expect(page.locator('html')).toHaveClass(/fs-dark/);
  await animationsDone(page);
  await expectNoSeriousViolations(page);
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(theme, () => {
    test.use({ colorScheme: theme });

    for (const route of SWEEP_ROUTES) {
      test(`AD1 axe ${route.path} as ${route.role ?? 'visitor'} (${theme}) @AD1`, async ({ as, apiOf }) => {
        await sweep({ as, apiOf }, route, theme);
      });
    }

    test(`AD1 axe shell overlays (${theme}) @AD1`, async ({ as }) => {
      const page = await as('foodsaver');
      await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [THEME_STORAGE_KEY, theme] as const);

      // Desktop: language and theme popovers.
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/dashboard');
      await settle(page, { ready: 'dashboard.page.welcome' });
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
      await expect(page.getByTestId('nav.drawer.activity')).toBeVisible();
      await animationsDone(page);
      await expectNoSeriousViolations(page);
    });

    test(`AD1 axe offline banner and offline data state (${theme}) @AD1`, async ({ page, context }) => {
      await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [THEME_STORAGE_KEY, theme] as const);
      await page.goto('/dev/gallery');
      await settle(page, { ready: 'gallery.dataState.live' });
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

// Phone pass (light theme; dark mode is the same CSS at every width): the top-level pages of the navigation.
// `defaultBrowserType` cannot be set inside a describe; the project's browser is used.
const { defaultBrowserType: _browserType, ...pixel7 } = devices['Pixel 7'];
void _browserType;
test.describe('mobile', () => {
  test.use({ ...pixel7, colorScheme: 'light' });

  for (const route of SWEEP_ROUTES.filter((entry) => entry.mobile)) {
    test(`AD1 axe ${route.path} as ${route.role ?? 'visitor'} (Pixel 7) @AD1`, async ({ as, apiOf }) => {
      await sweep({ as, apiOf }, route, 'light');
    });
  }
});
