import fs from 'node:fs';
import path from 'node:path';
import type { Locator, Page, PageScreenshotOptions } from '@playwright/test';
import { SEED_NOW, expect, test, type Language } from '../fixtures';
import { LANG_STORAGE_KEY, THEME_STORAGE_KEY } from '../fixtures/i18n';

/**
 * Base for `screenshots/<area>.screen.ts` (plan §18.4). Every page gets: browser clock fixed at SEED_NOW, map tiles and
 * external avatars stubbed with local fixtures, the variant's theme/language applied before the app boots, and
 * reduced motion (set on the screenshot projects). Baselines are only produced inside the official Playwright image
 * via `e2e/scripts/screenshots.sh` (never on the host).
 *
 * Usage:
 *   forEachVariant((variant) => {
 *     screenshotTest.describe(variant.name, () => {
 *       screenshotTest.use({ variant, role: 'foodsaver' });
 *       screenshotTest('sessions page @A22 @screenshot', async ({ page, visual }) => {
 *         await page.goto('/account/sessions');
 *         await visual.expectScreen(page, `sessions-${variant.name}`);
 *       });
 *     });
 *   });
 */
export type ThemeName = 'light' | 'dark';

export interface Variant {
  theme: ThemeName;
  lang: Language;
  /** `light-de`, `dark-fr`, … (use it in screenshot names and describe titles). */
  name: string;
}

/** Default matrix: both themes × German plus one long-text language (fr) to catch overflow. */
export const THEMES: readonly ThemeName[] = ['light', 'dark'];
export const SCREENSHOT_LANGUAGES: readonly Language[] = ['de', 'fr'];

export function variants(themes: readonly ThemeName[] = THEMES, languages: readonly Language[] = SCREENSHOT_LANGUAGES): Variant[] {
  return themes.flatMap((theme) => languages.map((lang) => ({ theme, lang, name: `${theme}-${lang}` })));
}

/** Calls `define` once per variant (declare tests inside, typically in a `describe(variant.name)` block). */
export function forEachVariant(define: (variant: Variant) => void, matrix: Variant[] = variants()): void {
  for (const variant of matrix) define(variant);
}

const TILE = fs.readFileSync(path.join(__dirname, 'fixtures', 'tile.png'));
const AVATAR = fs.readFileSync(path.join(__dirname, 'fixtures', 'avatar.svg'));
const TILE_HOSTS = /(^|\.)(tile\.openstreetmap\.org|tile\.osm\.org|basemaps\.cartocdn\.com|tiles\.stadiamaps\.com)$/;
const AVATAR_HOSTS = /(^|\.)(googleusercontent\.com|gravatar\.com)$/;

export interface ExpectScreenOptions {
  /** Default true. */
  fullPage?: boolean;
  /** Wait until no DataState is loading/refreshing (default true; pass false for skeleton screenshots). */
  waitForData?: boolean;
  /** Extra regions to mask; `[data-screenshot-mask]` elements (version, trace ids, …) are always masked. */
  mask?: Locator[];
  clip?: PageScreenshotOptions['clip'];
}

export interface ScreenHelpers {
  /** Fonts loaded, data settled (optional), two animation frames. */
  settle(page: Page, options?: { waitForData?: boolean }): Promise<void>;
  /** Full-page screenshot compared with `__screenshots__/<project>/<file>/<name>.png`. */
  expectScreen(page: Page, name: string, options?: ExpectScreenOptions): Promise<void>;
  /** Element screenshot (gallery sections, dialogs). */
  expectElement(locator: Locator, name: string, options?: Omit<ExpectScreenOptions, 'fullPage' | 'clip'>): Promise<void>;
}

async function settle(page: Page, options: { waitForData?: boolean } = {}): Promise<void> {
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
  if (options.waitForData ?? true) {
    await expect(page.locator('[data-state="loading"], [data-state="refreshing"]')).toHaveCount(0);
  }
  await page.evaluate(
    () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
  );
}

/**
 * Masked regions (version, trace ids) are painted in the page's muted surface colour instead of Playwright's
 * magenta, so the screenshots stay readable in reviews (review-m1 round 2 #4).
 */
async function maskColor(page: Page): Promise<string> {
  const dark = await page.evaluate(() => document.documentElement.classList.contains('fs-dark'));
  return dark ? '#212821' : '#f3f6f1';
}

const screen: ScreenHelpers = {
  settle,
  async expectScreen(page, name, options = {}) {
    await settle(page, options);
    await expect(page).toHaveScreenshot(`${name}.png`, {
      fullPage: options.fullPage ?? true,
      mask: [page.locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
      maskColor: await maskColor(page),
      ...(options.clip ? { clip: options.clip } : {}),
    });
  },
  async expectElement(locator, name, options = {}) {
    await settle(locator.page(), options);
    await expect(locator).toHaveScreenshot(`${name}.png`, {
      mask: [locator.page().locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
      maskColor: await maskColor(locator.page()),
    });
  },
};

export const screenshotTest = test.extend<{ variant: Variant | undefined; visual: ScreenHelpers }>({
  variant: [undefined, { option: true }],

  page: async ({ page, variant }, use) => {
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await page.route(
      (url) => TILE_HOSTS.test(url.hostname),
      (route) => route.fulfill({ status: 200, contentType: 'image/png', body: TILE }),
    );
    await page.route(
      (url) => AVATAR_HOSTS.test(url.hostname),
      (route) => route.fulfill({ status: 200, contentType: 'image/svg+xml', body: AVATAR }),
    );
    if (variant) {
      await page.emulateMedia({ colorScheme: variant.theme, reducedMotion: 'reduce' });
      await page.addInitScript(
        ([langKey, lang, themeKey, theme]) => {
          try {
            window.localStorage.setItem(langKey, lang);
            window.localStorage.setItem(themeKey, theme);
          } catch {
            /* storage disabled */
          }
        },
        [LANG_STORAGE_KEY, variant.lang, THEME_STORAGE_KEY, variant.theme] as const,
      );
    }
    await use(page);
  },

  // Not called `screen`: that name is Playwright's built-in screen-size option.
  visual: async ({}, use) => {
    await use(screen);
  },
});

export { expect };
