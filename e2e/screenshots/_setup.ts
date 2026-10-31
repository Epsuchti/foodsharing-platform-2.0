import fs from 'node:fs';
import path from 'node:path';
import type { Locator, Page, PageScreenshotOptions, Request } from '@playwright/test';
import { SEED_NOW, expect, test, type Language } from '../fixtures';
import { animationsDone } from '../fixtures/a11y';
import { isHeldRequest } from '../fixtures/errors';
import { LANG_STORAGE_KEY, THEME_STORAGE_KEY } from '../fixtures/i18n';

/**
 * Every capture is rendered in Inter, what a returning user sees (the product makes Inter a progressive enhancement:
 * system font on the first visit, `fs.font=inter` + `font-display: optional` afterwards, see client/src/styles.scss).
 * `fs.font` alone is not enough: with `optional` the browser keeps the fallback for the whole page lifetime when the
 * download misses its ~100 ms block period (machine load). So the init script below also adds the two Inter faces to
 * `document.fonts` from bytes it carries itself (the app's own /fonts files, fetched once per worker). A FontFace made
 * from a buffer needs no network, and it is declared `display: 'block'`: text waits for it and can never be painted in
 * the fallback. It is added after the CSS faces, so it wins the match for the same family and unicode ranges.
 * `assertInter` fails a capture that still is not in Inter.
 */
const INTER_FAMILY = 'Inter Variable';
const FONT_STORAGE_KEY = 'fs.font';
const INTER_FACES = [
  {
    file: '/fonts/inter-latin-ext-wght-normal.woff2',
    unicodeRange:
      'U+0100-02BA, U+02BD-02C5, U+02C7-02CC, U+02CE-02D7, U+02DD-02FF, U+0304, U+0308, U+0329, U+1D00-1DBF, U+1E00-1E9F, U+1EF2-1EFF, U+2020, U+20A0-20AB, U+20AD-20C0, U+2113, U+2C60-2C7F, U+A720-A7FF',
  },
  {
    file: '/fonts/inter-latin-wght-normal.woff2',
    unicodeRange:
      'U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD',
  },
];
const interBytes = new Map<string, Promise<string>>();

async function interFaces(page: Page): Promise<{ data: string; unicodeRange: string }[]> {
  return Promise.all(
    INTER_FACES.map(async ({ file, unicodeRange }) => {
      let bytes = interBytes.get(file);
      if (!bytes) {
        bytes = page
          .context()
          .request.get(file)
          .then(async (response) => {
            expect(response.ok(), `GET ${file} → ${response.status()}`).toBe(true);
            return (await response.body()).toString('base64');
          });
        interBytes.set(file, bytes);
        bytes.catch(() => interBytes.delete(file));
      }
      return { data: await bytes, unicodeRange };
    }),
  );
}

/** Pins Inter for every document of the page's context (also pages opened later). */
async function pinInter(page: Page): Promise<void> {
  const faces = await interFaces(page);
  await page.context().addInitScript(
    ([family, storageKey, list]) => {
      try {
        window.localStorage.setItem(storageKey, 'inter');
      } catch {
        /* storage disabled */
      }
      const pinned: FontFace[] = [];
      (window as unknown as { __fsPinnedInter: FontFace[] }).__fsPinnedInter = pinned;
      for (const { data, unicodeRange } of list) {
        const bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
        const face = new FontFace(family, bytes.buffer, { style: 'normal', weight: '100 900', display: 'block', unicodeRange });
        document.fonts.add(face);
        pinned.push(face);
        face.load().catch(() => undefined);
      }
    },
    [INTER_FAMILY, FONT_STORAGE_KEY, faces] as const,
  );
}

/** Fails when the page is not rendered in Inter: `.fs-inter` set, both pinned faces loaded, body resolves to Inter. */
async function assertInter(page: Page): Promise<void> {
  const state = await page.evaluate(async (family) => {
    const faces = (window as unknown as { __fsPinnedInter?: FontFace[] }).__fsPinnedInter ?? [];
    await Promise.all(faces.map((f) => f.loaded.catch(() => undefined)));
    return {
      isInter: document.documentElement.classList.contains('fs-inter'),
      loaded: faces.filter((f) => f.status === 'loaded').length,
      faces: faces.length,
      resolved: document.body ? getComputedStyle(document.body).fontFamily.includes(family) : false,
    };
  }, INTER_FAMILY);
  const ok = state.isInter && state.loaded >= INTER_FACES.length && state.resolved;
  expect(ok, `screenshot is not rendered in Inter: ${JSON.stringify(state)}`).toBe(true);
}

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
  const waitForData = options.waitForData ?? true;
  if (waitForData) {
    await expect(page.locator('[data-state="loading"], [data-state="refreshing"]')).toHaveCount(0);
  }
  // banners, the release-notes dot and toggled nav items appear after the first page (StartupService.markPageShown);
  // its idle callback falls back to a 5 s timeout under the fixed page clock. A loading-state capture (waitForData
  // false) holds a request of its own on purpose; neither this marker nor the quiet check waits for that one, so the
  // shell is still at rest with its data, like in every other capture.
  if (await page.evaluate(() => document.body?.querySelector('fs-shell') !== null)) {
    await expect(page.locator('html[data-page-shown]')).toHaveCount(1, { timeout: 15_000 });
  }
  await networkQuiet(page);
  if (waitForData) {
    await expect(page.locator('[data-state="loading"], [data-state="refreshing"]')).toHaveCount(0);
  }
  // Opening dialogs, drawers and popovers animate in and move the focus when their animation ends; a field filled
  // during that animation kept or lost its focus ring depending on machine load (AC1 define, C13 create, N3 bells
  // under parallel shards). Wait for running animations/transitions, then take the focus off form fields: a capture
  // shows fields at rest, never a focus ring that depends on timing.
  await animationsDone(page);
  await page.evaluate(() => {
    const active = document.activeElement as HTMLElement | null;
    const field = 'input, textarea, select, [contenteditable=""], [contenteditable="true"]';
    // a combobox/autocomplete keeps its focus: blurring it closes the open list the capture is about (C5 search)
    const opensList = '[role="combobox"], [aria-autocomplete], [aria-expanded="true"], [aria-controls]';
    if (active && active.matches(field) && !active.matches(opensList)) active.blur();
  });
  await animationsDone(page);
  await page.evaluate(
    () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
  );
}

/** Running requests per page (started, neither finished nor failed yet). */
const PENDING_TYPES = new Set(['document', 'stylesheet', 'script', 'font', 'image', 'fetch', 'xhr']);
const pendingRequests = new WeakMap<Page, Set<Request>>();

function trackPendingRequests(page: Page): void {
  const pending = new Set<Request>();
  pendingRequests.set(page, pending);
  page.on('request', (request) => {
    if (PENDING_TYPES.has(request.resourceType())) pending.add(request);
  });
  const done = (request: Request): void => void pending.delete(request);
  page.on('requestfinished', done);
  page.on('requestfailed', done);
}

/** Running requests except the ones `injectApiError({ delayMs })` holds on purpose (they never end during a capture). */
function pendingCount(page: Page): number {
  let count = 0;
  for (const request of pendingRequests.get(page) ?? []) if (!isHeldRequest(request)) count++;
  return count;
}

/**
 * Waits until no request has finished for 500 ms (wall clock, so it also works under `page.clock.setFixedTime`) and no
 * request is running, except the ones a loading-state capture holds on purpose. The after-first-page requests
 * (feature toggles, nav badges, banners) otherwise land in some captures and not in others.
 */
async function networkQuiet(page: Page): Promise<void> {
  let last = -1;
  let since = Date.now();
  await expect
    .poll(
      async () => {
        const count = await page.evaluate(() => {
          performance.setResourceTimingBufferSize(100_000);
          return performance.getEntriesByType('resource').length;
        });
        if (count !== last || pendingCount(page) > 0) {
          last = count;
          since = Date.now();
        }
        return Date.now() - since >= 500;
      },
      { intervals: [100], timeout: 15_000 },
    )
    .toBe(true);
}

async function modalOpen(page: Page): Promise<boolean> {
  return page.locator('[aria-modal="true"]:visible, [data-pc-section="mask"]:visible').count().then((n) => n > 0);
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
    await assertInter(page);
    await expect(page).toHaveScreenshot(`${name}.png`, {
      // A full-page capture of an open modal shows its backdrop only over the first viewport (fixed elements keep the
      // viewport's size), so the page below it looked brighter than the dimmed part. Modals are captured as the
      // viewport the user sees.
      fullPage: options.fullPage ?? !(await modalOpen(page)),
      mask: [page.locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
      maskColor: await maskColor(page),
      ...(options.clip ? { clip: options.clip } : {}),
    });
  },
  async expectElement(locator, name, options = {}) {
    await settle(locator.page(), options);
    await assertInter(locator.page());
    await expect(locator).toHaveScreenshot(`${name}.png`, {
      mask: [locator.page().locator('[data-screenshot-mask]'), ...(options.mask ?? [])],
      maskColor: await maskColor(locator.page()),
    });
  },
};

/**
 * The page clock is fixed at SEED_NOW (timers keep running, but the time does not move). Work that waits for time to
 * pass therefore never happens: RxJS `debounceTime` measures elapsed time with `Date.now()` and reschedules forever
 * (debounced searches), and the fake `requestIdleCallback` never reports idle time (`@defer (on idle)` blocks). Call
 * this after the step that starts such work; it moves the fixed time on by `ms`, so the work runs on its next check.
 */
export async function tickFixedClock(page: Page, ms = 1_000): Promise<void> {
  const now = await page.evaluate(() => Date.now());
  await page.clock.setFixedTime(new Date(now + ms));
}

export const screenshotTest = test.extend<{ variant: Variant | undefined; visual: ScreenHelpers }>({
  variant: [undefined, { option: true }],

  page: async ({ page, variant, role, apiOf }, use) => {
    await pinInter(page);
    trackPendingRequests(page);
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
