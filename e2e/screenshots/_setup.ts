import type { Locator, Page, PageScreenshotOptions } from '@playwright/test';
import { SEED_NOW, expect, test, type Language } from '../fixtures';
import { animationsDone } from '../fixtures/a11y';
import {
  assertInter,
  fixedOrigin,
  maskColor,
  overlayOpen,
  pinInter,
  settle,
  stubVisuals,
  tickFixedClock,
  trackPendingRequests,
} from '../fixtures/checkpoint';
import { LANG_STORAGE_KEY, THEME_STORAGE_KEY } from '../fixtures/i18n';

// The stability, font and stub code moved to fixtures/checkpoint.ts (decision 180); this file goes with the old
// screenshot suite.
export { tickFixedClock };

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

/** Default matrix (decision 109): one variant, light theme in German, on the desktop and mobile projects. Dark mode is covered by the axe sweep in both themes; other languages by the i18n checks. */
export const THEMES: readonly ThemeName[] = ['light'];
export const SCREENSHOT_LANGUAGES: readonly Language[] = ['de'];

export function variants(themes: readonly ThemeName[] = THEMES, languages: readonly Language[] = SCREENSHOT_LANGUAGES): Variant[] {
  return themes.flatMap((theme) => languages.map((lang) => ({ theme, lang, name: `${theme}-${lang}` })));
}

/** Calls `define` once per variant (declare tests inside, typically in a `describe(variant.name)` block). */
export function forEachVariant(define: (variant: Variant) => void, matrix: Variant[] = variants()): void {
  for (const variant of matrix) define(variant);
}

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

const screen: ScreenHelpers = {
  settle,
  async expectScreen(page, name, options = {}) {
    await settle(page, options);
    await assertInter(page);
    await fixedOrigin(page);
    // A full-page capture of an open modal shows its backdrop only over the first viewport (fixed elements keep the
    // viewport's size), so the page below it looked brighter than the dimmed part. Modals are captured as the
    // viewport the user sees. So are the side menu's cards: a full-page capture resizes the viewport to 1x1 and back,
    // and on a slow machine the app sees that width change and closes the open card (N3 bell panel on CI).
    const fullPage = options.fullPage ?? !(await overlayOpen(page));
    // A full-page capture draws the sticky side menu at the page's scroll offset, and a click before the capture
    // leaves that offset depending on what had arrived by then (S2 import result). Always capture from the top.
    if (fullPage) {
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      await animationsDone(page);
    }
    await expect(page).toHaveScreenshot(`${name}.png`, {
      fullPage,
      mask: [page.locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
      maskColor: await maskColor(page),
      ...(options.clip ? { clip: options.clip } : {}),
    });
  },
  async expectElement(locator, name, options = {}) {
    await settle(locator.page(), options);
    await assertInter(locator.page());
    await fixedOrigin(locator.page());
    // An element taller than the viewport (AU1 gap card) is scrolled into view and captured beyond the viewport, and
    // the sticky bars (header, store tabs, sub-navigation) and the fixed tab bar were painted over it at a spot that
    // depends on the scroll position of the moment: two consecutive captures differed. Such an element is captured in
    // a viewport that holds it from the top of the page: no scroll, the sticky bars stay above it in the flow and the
    // fixed tab bar below it. Only the height changes (a width change closes open side menu cards).
    const page = locator.page();
    const viewport = page.viewportSize();
    const box = await locator.evaluate((el) => {
      const rect = el.getBoundingClientRect();
      return { bottom: rect.bottom + window.scrollY, height: rect.height };
    });
    const restore = viewport !== null && box.height > viewport.height ? viewport : null;
    if (restore) {
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      await page.setViewportSize({ width: restore.width, height: Math.ceil(box.bottom) + 120 });
      await settle(page, { waitForData: false });
    }
    try {
      await expect(locator).toHaveScreenshot(`${name}.png`, {
        mask: [page.locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
        maskColor: await maskColor(page),
      });
    } finally {
      if (restore) await page.setViewportSize(restore);
    }
  },
};

/**
 * The page clock is fixed at SEED_NOW (timers keep running, but the time does not move). Work that waits for time to
 * pass therefore never happens: RxJS `debounceTime` measures elapsed time with `Date.now()` and reschedules forever
 * (debounced searches), and the fake `requestIdleCallback` never reports idle time (`@defer (on idle)` blocks). Call
 * this after the step that starts such work; it moves the fixed time on by `ms`, so the work runs on its next check.
 */
/** MT1: this page draws the self-hosted vector basemap instead of the raster stub (call before `goto`). */
export async function useVectorBasemap(page: Page): Promise<void> {
  await page.addInitScript(() => {
    delete (window as unknown as { __fsTileUrl?: string }).__fsTileUrl;
  });
}


export const screenshotTest = test.extend<{ variant: Variant | undefined; visual: ScreenHelpers }>({
  variant: [undefined, { option: true }],

  page: async ({ page, variant, role, apiOf }, use) => {
    await pinInter(page.context());
    trackPendingRequests(page);
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await stubVisuals(page.context());
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
      if (role) {
        // A signed-in account's own language/theme win over local storage (seed accounts use gws, fr, …), so the
        // account is set to the variant too; the seed reset restores it.
        const response = await apiOf(page).patch('/api/users/me/settings', {
          language: variant.lang,
          theme: variant.theme.toUpperCase(),
        });
        expect(response.ok(), `PATCH /api/users/me/settings → ${response.status()}`).toBe(true);
      }
    }
    await use(page);
  },

  // Not called `screen`: that name is Playwright's built-in screen-size option.
  visual: async ({}, use) => {
    await use(screen);
  },
});

export { expect };
