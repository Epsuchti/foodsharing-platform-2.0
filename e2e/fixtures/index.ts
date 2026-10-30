import { test as base, expect, request as playwrightRequest, type BrowserContext, type Page } from '@playwright/test';
import { resolveBaseURL, type Role } from '../support/env';
import { a11y, expectNoSeriousViolations, type A11yHelpers } from './a11y';
import { ApiClient, DevApi } from './api';
import { BasketHelpers } from './baskets';
import { ClockControl } from './clock';
import { errors, type ErrorHelpers } from './errors';
import { i18n, type I18nHelpers } from './i18n';
import { MailBox } from './mails';
import { NotificationHelpers } from './notifications';
import { pdf, type PdfHelpers } from './pdf';
import { ProfileHelpers } from './profiles';
import { RegionHelpers } from './regions';
import { StoreHelpers } from './stores';
import { webauthn, type WebAuthnHelpers } from './webauthn';

export type SeedResetMode = 'file' | 'test' | 'off';

export interface Options {
  /**
   * Log the default `context`/`page` (and `api`) in as this seed user: `test.use({ role: 'orga' })`.
   * Uses `POST /api/dev/login-as` after the seed reset, so sessions are always valid (stored states die with a reset).
   */
  role: Role | undefined;
  /**
   * When to reset the database to the seed state (`POST /api/dev/seed/reset` + clear mails + clock = SEED_NOW):
   * `file` (default) before the first test of every spec file, `test` before every test, `off` never
   * (read-only specs that must not depend on the dev API).
   */
  seedReset: SeedResetMode;
}

export interface Fixtures {
  /** API client (own cookie jar; logged in as `role` when set) with automatic CSRF header. */
  api: ApiClient;
  /** Dev endpoints: users, loginAs, resetSeed, setRole, setClock, mails. */
  dev: DevApi;
  /** Resets seed + mails + clock now (in addition to the automatic per-file reset). */
  resetSeed: () => Promise<void>;
  /** New, isolated browser page logged in as `role` (`null` = anonymous), with the project's device settings. */
  as: (role: Role | null) => Promise<Page>;
  /** API client sharing the cookies of a page/context (act as the user the page is logged in as). */
  apiOf: (target: Page | BrowserContext) => ApiClient;
  mails: MailBox;
  clock: ClockControl;
  a11y: A11yHelpers;
  errors: ErrorHelpers;
  i18n: I18nHelpers;
  webauthn: WebAuthnHelpers;
  /** Seed regions by slug and `joinRegion(role, slugOrId)` through the real API (M2). */
  region: RegionHelpers;
  /** Seed stores by code/name (`S-01`), pickups of a store and `signUp(role, store, pickupAt)` (M3). */
  store: StoreHelpers;
  /** PDF text assertions: `expectPdfContains(responseOrDownload, ['text'])`. */
  pdf: PdfHelpers;
  /** M4 bells/pushes/Telegram captured by the dev endpoints, `runJob(name)`, `simulateTelegramStart(token)`. */
  notifications: NotificationHelpers;
  /** M6 profiles: `userId(role)`, `giveBanana(role, userId, message?)`, `buddy(role, userId)`, `profile(role, userId)`. */
  profiles: ProfileHelpers;
  /** M6 food baskets: `create(role, body?)`, `tryCreate(role, body)`, `get(role, id)`, `request(role, id, message?)`. */
  baskets: BasketHelpers;
  /** internal: performs the automatic seed reset according to `seedReset`. */
  _seed: void;
}

let lastResetFile: string | undefined;

export const test = base.extend<Options & Fixtures>({
  role: [undefined, { option: true }],
  seedReset: ['file', { option: true }],

  // The app URL is only known after global setup started the jar; resolve it per test instead of in the config.
  baseURL: async ({}, use) => {
    await use(resolveBaseURL());
  },

  _seed: [
    async ({ seedReset, baseURL }, use, testInfo) => {
      const due = seedReset === 'test' || (seedReset === 'file' && lastResetFile !== testInfo.file);
      if (due) {
        const request = await playwrightRequest.newContext({ baseURL });
        try {
          await new DevApi(new ApiClient(request, baseURL!)).resetSeed();
        } finally {
          await request.dispose();
        }
        lastResetFile = testInfo.file;
      }
      await use();
    },
    { auto: true },
  ],

  context: async ({ context, role, baseURL, _seed }, use) => {
    void _seed;
    if (role) await new ApiClient(context.request, baseURL!).loginAs(role);
    await use(context);
  },

  page: async ({ page }, use, testInfo) => {
    await use(page);
    // A11y gate for page-level tests (plan §18.3): only when the test itself passed and the page shows something.
    if (testInfo.tags.includes('@page') && testInfo.status === testInfo.expectedStatus && /^https?:/.test(page.url())) {
      await expectNoSeriousViolations(page);
    }
  },

  api: async ({ baseURL, role, _seed }, use) => {
    void _seed;
    const request = await playwrightRequest.newContext({ baseURL });
    const client = new ApiClient(request, baseURL!);
    if (role) await client.loginAs(role);
    await use(client);
    await request.dispose();
  },

  dev: async ({ api }, use) => {
    await use(new DevApi(api));
  },

  resetSeed: async ({ dev }, use) => {
    await use(() => dev.resetSeed());
  },

  as: async (
    { browser, baseURL, _seed, viewport, userAgent, deviceScaleFactor, isMobile, hasTouch, locale, timezoneId, colorScheme, reducedMotion },
    use,
  ) => {
    void _seed;
    const contexts: BrowserContext[] = [];
    await use(async (role) => {
      const context = await browser.newContext({
        baseURL,
        viewport,
        locale,
        timezoneId,
        colorScheme,
        reducedMotion,
        ...(userAgent ? { userAgent } : {}),
        ...(deviceScaleFactor ? { deviceScaleFactor } : {}),
        ...(isMobile ? { isMobile } : {}),
        ...(hasTouch ? { hasTouch } : {}),
      });
      contexts.push(context);
      if (role) await new ApiClient(context.request, baseURL!).loginAs(role);
      return context.newPage();
    });
    for (const context of contexts) await context.close();
  },

  apiOf: async ({ baseURL }, use) => {
    await use((target) => ApiClient.of(target, baseURL!));
  },

  mails: async ({ dev }, use) => {
    await use(new MailBox(dev));
  },

  clock: async ({ dev }, use) => {
    await use(new ClockControl(dev));
  },

  a11y: async ({}, use) => {
    await use(a11y);
  },
  errors: async ({}, use) => {
    await use(errors);
  },
  i18n: async ({}, use) => {
    await use(i18n);
  },
  webauthn: async ({}, use) => {
    await use(webauthn);
  },
  region: async ({ baseURL }, use) => {
    await use(new RegionHelpers(baseURL!));
  },
  pdf: async ({}, use) => {
    await use(pdf);
  },
  store: async ({ baseURL }, use) => {
    await use(new StoreHelpers(baseURL!));
  },
  notifications: async ({ api, dev }, use) => {
    await use(new NotificationHelpers(api, dev));
  },
  profiles: async ({ baseURL }, use) => {
    await use(new ProfileHelpers(baseURL!));
  },
  baskets: async ({ baseURL }, use) => {
    await use(new BasketHelpers(baseURL!));
  },
});

export { expect };
export { ApiClient, DevApi, CSRF_COOKIE, CSRF_HEADER, acceptPendingConsents, expectStatus } from './api';
export { ClockControl } from './clock';
export { MailBox, type WaitForMailOptions } from './mails';
export { NotificationHelpers, type BellDto, type DevNotification } from './notifications';
export { expectNoSeriousViolations } from './a11y';
export * from './errors';
export { LANGUAGES, setLanguage, expectNoMissingKeys } from './i18n';
export { addVirtualAuthenticator, type VirtualAuthenticator } from './webauthn';
export { expectPdfContains, pdfText, type PdfSource, type PdfText } from './pdf';
export { RegionHelpers, regionId, seedRegion, seedRegions, type SeedRegion } from './regions';
export { ROLES, SEED_NOW, authFile, roleEmail, type Role } from '../support/env';
export type * from './types';
export {
  StoreHelpers,
  seedStore,
  seedStores,
  zurichPickupAt,
  S01_THURSDAY,
  S02_SATURDAY,
  S04_WEDNESDAY,
  S07_WEDNESDAY,
  type SeedPickup,
  type SeedStore,
} from './stores';
export {
  CONVERSATIONS,
  bubbles,
  getConversation,
  listItem,
  lookupConversation,
  realtimeEventTypes,
  sendMessage,
  waitForRealtime,
  type ChatMessage,
  type ConversationSummary,
} from './messaging';
export { MAILBOXES, injectBounce, injectIncoming, mailRow, rawMail, type IncomingMail } from './mailbox';
export { BANANA_TEXT, ProfileHelpers, seedUserId } from './profiles';
export { BASKETS, BasketHelpers, OPEN_ZURICH_BASKETS, ZURICH_HB, type BasketCreateBody, type BasketDto } from './baskets';
export { ACTIVE_ZURICH_FSPS, FSPS } from './fsp';
export { REPORTS, pickOption } from './reports';
