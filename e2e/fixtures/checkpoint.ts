import fs from 'node:fs';
import path from 'node:path';
import {
  test as base,
  type BrowserContext,
  type Locator,
  type Page,
  type Request,
  type TestInfo,
} from '@playwright/test';
import { expect, test, type Role } from './index';
import { animationsDone, expectNoSeriousViolations, type A11yOptions } from './a11y';
import { ApiClient } from './api';
import { isHeldRequest } from './errors';
import { LANG_STORAGE_KEY, THEME_STORAGE_KEY } from './i18n';

/**
 * Journey checkpoints (decision 180). A journey (`e2e/journeys/<persona>/<slug>.journey.ts`) walks a persona through a
 * real process and calls `checkpoint(page, 'name')` at the states worth looking at. A checkpoint
 *
 * - always waits until the page is at rest: no DataState loading, the shell's after-first-page requests done, the
 *   network quiet, animations finished, form fields blurred;
 * - on the checkpoint projects (`chromium`, `mobile-chrome`: `metadata.checkpoints`) runs axe
 *   (`expectNoSeriousViolations`);
 * - there, inside the pinned Playwright image (`E2E_SCREENSHOTS=1`, scripts/screenshots.sh), also compares the page
 *   with `__screenshots__/<project>/journeys/<persona>/<file>/<name>.png`. A plain run on a developer machine never
 *   compares or writes host-rendered baselines (plan-m1 risk 6).
 *
 * Journeys import `test` from here: seed reset before every test (journeys never depend on each other), every
 * context in German and the light theme (the account settings of the seed users differ), and on the checkpoint
 * projects Inter pinned, the map drawn from a raster stub and external avatars stubbed, so captures are the same on
 * every run.
 */

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

async function interFaces(context: BrowserContext): Promise<{ data: string; unicodeRange: string }[]> {
  return Promise.all(
    INTER_FACES.map(async ({ file, unicodeRange }) => {
      let bytes = interBytes.get(file);
      if (!bytes) {
        bytes = context.request.get(file).then(async (response) => {
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

/** Pins Inter for every document of the context (also pages opened later). */
export async function pinInter(context: BrowserContext): Promise<void> {
  const faces = await interFaces(context);
  await context.addInitScript(
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
        const face = new FontFace(family, bytes.buffer, {
          style: 'normal',
          weight: '100 900',
          display: 'block',
          unicodeRange,
        });
        document.fonts.add(face);
        pinned.push(face);
        face.load().catch(() => undefined);
      }
    },
    [INTER_FAMILY, FONT_STORAGE_KEY, faces] as const,
  );
}

/** Fails when the page is not rendered in Inter: `.fs-inter` set, both pinned faces loaded, body resolves to Inter. */
export async function assertInter(page: Page): Promise<void> {
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

const VISUAL_DIR = path.join(__dirname, 'visual');
const TILE = fs.readFileSync(path.join(VISUAL_DIR, 'tile.png'));
const AVATAR = fs.readFileSync(path.join(VISUAL_DIR, 'avatar.svg'));
/**
 * MT1 (decision 159): captures draw the map from this raster stub (`window.__fsTileUrl`), not the vector basemap,
 * so the baselines stay the same in every engine.
 */
const STUB_TILE_PATH = '/__fs-stub-tile/';
const AVATAR_HOSTS = /(^|\.)(googleusercontent\.com|gravatar\.com)$/;

/** Raster map tiles and external avatars from local fixtures, for every page of the context. */
export async function stubVisuals(context: BrowserContext): Promise<void> {
  await context.addInitScript((template) => {
    (window as unknown as { __fsTileUrl?: string }).__fsTileUrl = template;
  }, `${STUB_TILE_PATH}{z}/{x}/{y}.png`);
  await context.route(
    (url) => url.pathname.startsWith(STUB_TILE_PATH),
    (route) => route.fulfill({ status: 200, contentType: 'image/png', body: TILE }),
  );
  await context.route(
    (url) => AVATAR_HOSTS.test(url.hostname),
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: AVATAR,
      }),
  );
}

export async function settle(page: Page, options: { waitForData?: boolean } = {}): Promise<void> {
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
    await expect(page.locator('html[data-page-shown]')).toHaveCount(1, {
      timeout: 15_000,
    });
  }
  await networkQuiet(page);
  if (waitForData) {
    await expect(page.locator('[data-state="loading"], [data-state="refreshing"]')).toHaveCount(0);
    await expect(page.getByTestId('shell.requestBar')).toHaveCount(0);
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
  // The side menu scrolls the current item into view once after each navigation (SidenavComponent). Toggled nav items
  // that arrive afterwards change the list's height and leave that scroll at an offset which depends on arrival
  // order (C10 uploads). Everything has arrived now: repeat the app's own scroll so the menu is at rest, always.
  await page.evaluate(() => {
    // the menu's own scroll only: scrollIntoView also scrolled the window, so a viewport capture of a popover further
    // down the page showed the top of the page (store team menu). The window keeps its position.
    const { scrollX, scrollY } = window;
    document.querySelector<HTMLElement>('#sn-nav [aria-current="page"]')?.scrollIntoView({ block: 'nearest' });
    window.scrollTo({ left: scrollX, top: scrollY, behavior: 'instant' });
  });
  await page.evaluate(
    () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
  );
}

/** Running requests per page (started, neither finished nor failed yet). */
const PENDING_TYPES = new Set(['document', 'stylesheet', 'script', 'font', 'image', 'fetch', 'xhr']);
const pendingRequests = new WeakMap<Page, Set<Request>>();
const requestStarts = new WeakMap<Request, number>();
const STALE_REQUEST_MS = 10_000;

export function trackPendingRequests(page: Page): void {
  const pending = new Set<Request>();
  pendingRequests.set(page, pending);
  // A request the previous document started shortly before a page load replaced it does not always report
  // `requestfailed` (a resize right before `page.goto` loads a chunk and the release notes, AU1 phone step): it stayed
  // "running" for good and the quiet check timed out. When a new document commits in the main frame after a document
  // request (a real page load, not an SPA route change), what the old document left is dropped.
  let documentRequested = false;
  page.on('request', (request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame()) documentRequested = true;
    if (PENDING_TYPES.has(request.resourceType())) {
      pending.add(request);
      requestStarts.set(request, Date.now());
    }
  });
  page.on('framenavigated', (frame) => {
    if (frame !== page.mainFrame() || !documentRequested) return;
    documentRequested = false;
    for (const request of pending)
      if (!(request.isNavigationRequest() && request.frame() === frame)) pending.delete(request);
  });
  const done = (request: Request): void => void pending.delete(request);
  page.on('requestfinished', done);
  page.on('requestfailed', done);
}

/** Running requests except the ones `injectApiError({ delayMs })` holds on purpose (they never end during a capture). */
function pendingCount(page: Page): number {
  let count = 0;
  const now = Date.now();
  for (const request of pendingRequests.get(page) ?? []) {
    // A request cut off by a navigation does not always report an end (a goto right after logout cut the landing
    // page's explainer request): after STALE_REQUEST_MS it no longer counts as running.
    if (now - (requestStarts.get(request) ?? now) > STALE_REQUEST_MS) continue;
    if (!isHeldRequest(request)) count++;
  }
  return count;
}

/**
 * Waits until no request has finished for 500 ms (wall clock, so it also works under `page.clock.setFixedTime`) and no
 * request is running, except the ones a loading-state capture holds on purpose. The after-first-page requests
 * (feature toggles, nav badges, banners) otherwise land in some captures and not in others. The browser must also have
 * answered several polls in that time: under load one slow answer can use up the 500 ms while the page is still busy.
 */
async function networkQuiet(page: Page): Promise<void> {
  let last = -1;
  let since = Date.now();
  let quietPolls = 0;
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
          quietPolls = 0;
        } else {
          quietPolls++;
        }
        return Date.now() - since >= 500 && quietPolls >= 5;
      },
      { intervals: [100], timeout: 15_000 },
    )
    .toBe(true);
}

/**
 * Every shard runs on a free port, and some screens show the app's own address (AU5 adverts, surveys, support). A
 * capture always shows the same address, so a baseline never depends on the port of the run that took it.
 */
export async function fixedOrigin(page: Page): Promise<void> {
  await page.evaluate((shown) => {
    const origin = location.origin;
    if (origin === shown) return;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.nodeValue?.includes(origin)) node.nodeValue = node.nodeValue.split(origin).join(shown);
    }
    for (const field of document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input, textarea')) {
      if (field.value.includes(origin)) field.value = field.value.split(origin).join(shown);
    }
  }, 'http://localhost:8081');
}

export async function overlayOpen(page: Page): Promise<boolean> {
  return page
    .locator(
      '[aria-modal="true"]:visible, [data-pc-section="mask"]:visible, .p-drawer:visible, .p-overlay-mask:visible, .sn-card:visible',
    )
    .count()
    .then((n) => n > 0);
}

/**
 * Masked regions (version, trace ids) are painted in the page's muted surface colour instead of Playwright's
 * magenta, so the screenshots stay readable in reviews (review-m1 round 2 #4).
 */
export async function maskColor(page: Page): Promise<string> {
  const dark = await page.evaluate(() => document.documentElement.classList.contains('fs-dark'));
  return dark ? '#212821' : '#f3f6f1';
}

/** Projects whose checkpoints run axe and (inside the Playwright image) compare screenshots. */
export function isCheckpointProject(info: TestInfo = base.info()): boolean {
  return (info.project.metadata as { checkpoints?: boolean } | undefined)?.checkpoints === true;
}

/** Screenshots are compared only inside the pinned Playwright image (scripts/screenshots.sh sets E2E_SCREENSHOTS=1). */
export const COMPARE_SCREENSHOTS = process.env.E2E_SCREENSHOTS === '1';

/** Language and theme of every journey context: German, light (decision 180: checkpoints are light-de). */
export const JOURNEY_LANGUAGE = 'de';
export const JOURNEY_THEME = 'light';

export interface JourneyOptions {
  /**
   * Pin language and theme (default true): local storage for anonymous pages, the account settings for logged-in
   * ones (the seed users use gws, fr, dark, …; the seed reset restores them). The five-language smoke turns it off.
   */
  pinSettings: boolean;
}

/** Base `test` of every journey. */
export const journey = test.extend<JourneyOptions>({
  seedReset: 'test',
  pinSettings: [true, { option: true }],

  _prepareContext: async ({ baseURL, pinSettings }, use, testInfo) => {
    const visual = isCheckpointProject(testInfo);
    await use(async (context: BrowserContext, role: Role | null) => {
      if (visual) {
        await pinInter(context);
        await stubVisuals(context);
        for (const page of context.pages()) trackPendingRequests(page);
        context.on('page', trackPendingRequests);
      }
      if (!pinSettings) return;
      await context.addInitScript(
        ([langKey, lang, themeKey, theme]) => {
          try {
            window.localStorage.setItem(langKey, lang);
            window.localStorage.setItem(themeKey, theme);
          } catch {
            /* storage disabled */
          }
        },
        [LANG_STORAGE_KEY, JOURNEY_LANGUAGE, THEME_STORAGE_KEY, JOURNEY_THEME] as const,
      );
      if (role) {
        // A signed-in account's own language/theme win over local storage, so the account is set too.
        const response = await new ApiClient(context.request, baseURL!).patch('/api/users/me/settings', {
          language: JOURNEY_LANGUAGE,
          theme: JOURNEY_THEME.toUpperCase(),
        });
        expect(response.ok(), `PATCH /api/users/me/settings → ${response.status()}`).toBe(true);
      }
    });
  },
});

export interface CheckpointOptions {
  /** Default true. False captures the viewport only (an open dialog is always captured as the viewport). */
  fullPage?: boolean;
  /** Wait until no DataState is loading/refreshing (default true; false for skeleton captures). */
  waitForData?: boolean;
  /** Extra regions to mask; `[data-screenshot-mask]` elements (version, trace ids, …) are always masked. */
  mask?: Locator[];
  /** Options of the axe check (excluded regions, disabled rules with a reason). */
  a11y?: A11yOptions;
  /** Skip the axe check of this checkpoint (a state axe cannot judge, e.g. a skeleton); give the reason. */
  skipAxe?: string;
  /**
   * Show toasts in the capture (default false). Toasts are transient: one from the previous step is still visible or
   * fading depending on timing, so captures hide them; the journey asserts their text. With true, the capture waits
   * until the toast's entry animation is done and shows it.
   */
  toasts?: boolean;
}

/** Hides transient toasts in a capture unless the checkpoint is about one. */
const HIDE_TOASTS = '[data-testid="shell.toast"] { visibility: hidden !important; }';
/**
 * Animated counters (`fs-animated-counter`, statistics) count up when they appear, also when the tall capture
 * viewport brings them into view: the capture waits until every counter shows its final value (the text screen readers get).
 */
async function countersAtRest(page: Page): Promise<void> {
  await expect
    .poll(
      () =>
        page.evaluate(() =>
          [...document.querySelectorAll('fs-animated-counter')].every(
            // the visible (aria-hidden) number against the final one the screen reader text carries
            (node) =>
              (node.querySelector('[aria-hidden="true"]')?.textContent ?? '') ===
              (node.querySelector('.fs-visually-hidden')?.textContent ?? ''),
          ),
        ),
      { message: 'animated counters at their final value' },
    )
    .toBe(true);
}

/** Full-page phone captures: the fixed tab bar sits at the end of the shell instead of where the viewport ended. */
const TAB_BAR_AT_END = 'fs-shell { position: relative !important; } fs-mobile-tab-bar { position: absolute !important; }';

/** Element captures are about the element: the fixed phone tab bar would be drawn over its lower part. */
const HIDE_TAB_BAR = 'fs-mobile-tab-bar { visibility: hidden !important; }';

/** Height of the app's content: the end of the shell (its padding holds the phone tab bar), else the body. */
async function contentHeight(page: Page): Promise<number> {
  return page.evaluate(() => {
    const root = document.querySelector('fs-shell') ?? document.body;
    return Math.ceil(root.getBoundingClientRect().bottom + window.scrollY);
  });
}

/** Moves the pointer off the page, so the last click leaves no hover highlight in the capture. */
async function releaseHover(page: Page): Promise<void> {
  await page.mouse.move(-1, -1).catch(() => undefined);
  await animationsDone(page);
}

/**
 * Times counted from the browser clock ("vor 18 Sekunden", "vor 2 Minuten") depend on how fast the journey ran:
 * their elements are masked in every capture.
 */
const RELATIVE_TIME = '\\bvor (\\d+|einer|einem) (Sekunden?|Minuten?)\\b|\\bgerade eben\\b|\\bsoeben\\b';
async function maskRelativeTimes(page: Page): Promise<void> {
  await page.evaluate(
    ([pattern, attr]) => {
      const re = new RegExp(pattern, 'i');
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      while (walker.nextNode()) {
        const node = walker.currentNode;
        if (node.nodeValue && re.test(node.nodeValue)) node.parentElement?.setAttribute(attr, '');
      }
    },
    [RELATIVE_TIME, CAPTURE_MASK_ATTR] as const,
  );
}

/** Runs `capture` with masks painted, toasts hidden (unless `toasts`) and, full-page, the tab bar at the end; all removed afterwards. */
async function withCaptureStyle<T>(
  page: Page,
  options: { toasts?: boolean; mask?: Locator[]; hideTabBar?: boolean; tabBarAtEnd?: boolean },
  capture: () => Promise<T>,
): Promise<T> {
  // Masks are painted by the page itself: Playwright's mask boxes land off their element in full-page phone captures
  // (measured before the capture resizes the device metrics) and covered the tab bar's labels.
  for (const locator of options.mask ?? []) {
    await locator.evaluateAll((nodes, attr) => nodes.forEach((node) => node.setAttribute(attr, '')), CAPTURE_MASK_ATTR);
  }
  await countersAtRest(page);
  await maskRelativeTimes(page);
  const color = await maskColor(page);
  const css = [MASK_CSS(color), options.toasts ? '' : HIDE_TOASTS, options.hideTabBar ? HIDE_TAB_BAR : '', options.tabBarAtEnd ? TAB_BAR_AT_END : ''].join(' ');
  const style = await page.addStyleTag({ content: css });
  try {
    return await capture();
  } finally {
    await style.evaluate((node) => (node as Element).remove()).catch(() => undefined);
    await page
      .evaluate(
        (attr) => document.querySelectorAll(`[${attr}]`).forEach((node) => node.removeAttribute(attr)),
        CAPTURE_MASK_ATTR,
      )
      .catch(() => undefined);
  }
}

const CAPTURE_MASK_ATTR = 'data-fs-capture-mask';
/**
 * Masked regions keep their size and show the muted surface colour, their content hidden. The public totals
 * (`fs-animated-counter`) are always masked: the server keeps them in memory, a dev seed reset does not drop that
 * cache and under the fixed e2e clock it never expires, so they depend on which journeys ran on the jar before
 * (journeys assert the numbers themselves).
 */
const MASKED = `[data-screenshot-mask], [${CAPTURE_MASK_ATTR}], fs-animated-counter`;
const MASK_CSS = (color: string): string =>
  `:is(${MASKED}) { background: ${color} !important; border-color: ${color} !important; ` +
  `color: transparent !important; box-shadow: none !important; } ` +
  `:is(${MASKED}) * { visibility: hidden !important; }`;

/**
 * A checkpoint of a journey: page at rest; on the checkpoint projects axe, and inside the Playwright image a
 * full-page screenshot named `name` (unique per journey file). Other browsers only wait for stability.
 */
export async function checkpoint(page: Page, name: string, options: CheckpointOptions = {}): Promise<void> {
  await base.step(`checkpoint ${name}`, async () => {
    await settle(page, { waitForData: options.waitForData });
    if (!isCheckpointProject()) return;
    if (!options.skipAxe) await expectNoSeriousViolations(page, options.a11y);
    if (!COMPARE_SCREENSHOTS) return;
    await captureCheckpoint(page, name, options);
  });
}

async function captureCheckpoint(page: Page, name: string, options: CheckpointOptions): Promise<void> {
  await assertInter(page);
  await fixedOrigin(page);
  // An open modal, drawer or side menu card is captured as the viewport the user sees (its backdrop only covers
  // the viewport).
  const fullPage = (options.fullPage ?? true) && !(await overlayOpen(page));
  await releaseHover(page);
  if (!fullPage) {
    // A dialog or drawer is captured from the top left of its content: how far a fill or a validation message had
    // scrolled it depended on timing.
    await page.evaluate(() => {
      for (const node of document.querySelectorAll<HTMLElement>('.p-dialog-content, .p-drawer-content')) {
        node.scrollTop = 0;
        node.scrollLeft = 0;
      }
    });
    await animationsDone(page);
    await withCaptureStyle(page, options, () => expect(page).toHaveScreenshot(`${name}.png`));
    return;
  }
  // A full page is captured as one tall viewport: Playwright's full-page capture draws everything sized to the
  // viewport (side menu, backdrops) and the fixed phone tab bar only over the first 800 px, and its page height
  // counted invisible overflow (empty strips under the tab bar). The viewport grows to the end of the shell, from
  // the top of the page; only the height changes.
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  // Phones: no viewport resize (the mobile browser kept a changed zoom for the rest of the journey, and the tab bar
  // then covered the next button). Playwright's full-page capture, cut at the end of the shell, with the fixed tab
  // bar moved to the shell's end (into the room the shell keeps for it).
  if (base.info().project.use.isMobile) {
    const height = await contentHeight(page);
    const width = page.viewportSize()!.width;
    await withCaptureStyle(page, { ...options, tabBarAtEnd: true }, () =>
      expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true, clip: { x: 0, y: 0, width, height } }),
    );
    return;
  }
  const viewport = page.viewportSize()!;
  // The shell's "at least one viewport high" rules (main, column, body: 100vh) would grow with the tall viewport and
  // push the footer and tab bar down by another viewport: they keep the height of the real viewport.
  const pinVh = await page.addStyleTag({
    content: `body, fs-shell, fs-shell .shell__col, fs-shell .shell__main { min-height: ${viewport.height}px !important; }`,
  });
  try {
    for (let round = 0; round < 3; round++) {
      const height = await contentHeight(page);
      const current = page.viewportSize()!.height;
      if (Math.abs(height - current) <= 1 || (round > 0 && height <= current)) break;
      await page.setViewportSize({
        width: viewport.width,
        height: Math.max(height, viewport.height),
      });
      await settle(page, { waitForData: false });
    }
    await withCaptureStyle(page, options, () => expect(page).toHaveScreenshot(`${name}.png`));
  } finally {
    await pinVh.evaluate((node) => (node as Element).remove()).catch(() => undefined);
    await page.setViewportSize(viewport);
  }
}

/**
 * An element checkpoint (gallery sections, dialogs). An element taller than the viewport is captured in a viewport
 * that holds it from the top of the page (sticky bars stay above it, the fixed tab bar below it).
 */
export async function checkpointElement(
  locator: Locator,
  name: string,
  options: Omit<CheckpointOptions, 'fullPage'> = {},
): Promise<void> {
  const page = locator.page();
  await base.step(`checkpoint ${name}`, async () => {
    await settle(page, { waitForData: options.waitForData });
    if (!isCheckpointProject()) return;
    if (!options.skipAxe) await expectNoSeriousViolations(page, options.a11y);
    if (!COMPARE_SCREENSHOTS) return;
    await captureElement(locator, name, options);
  });
}

async function captureElement(
  locator: Locator,
  name: string,
  options: Omit<CheckpointOptions, 'fullPage'>,
): Promise<void> {
  const page = locator.page();
  await assertInter(page);
  await fixedOrigin(page);
  const viewport = page.viewportSize();
  const box = await locator.evaluate((el) => {
    const rect = el.getBoundingClientRect();
    return { bottom: rect.bottom + window.scrollY, height: rect.height };
  });
  const restore = viewport !== null && box.height > viewport.height ? viewport : null;
  if (restore) {
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await page.setViewportSize({
      width: restore.width,
      height: Math.ceil(box.bottom) + 120,
    });
    await settle(page, { waitForData: false });
  }
  try {
    await releaseHover(page);
    await withCaptureStyle(page, { toasts: options.toasts, mask: options.mask, hideTabBar: true }, () =>
      expect(locator).toHaveScreenshot(`${name}.png`),
    );
  } finally {
    if (restore) await page.setViewportSize(restore);
  }
}

/**
 * The page clock runs from SEED_NOW, but work that waits for `requestIdleCallback` or a debounce under a fixed time
 * never runs: call this after the step that starts it (moves a fixed time on by `ms`).
 */
export async function tickFixedClock(page: Page, ms = 1_000): Promise<void> {
  const now = await page.evaluate(() => Date.now());
  await page.clock.setFixedTime(new Date(now + ms));
}

export { expect };
export type { Request };
