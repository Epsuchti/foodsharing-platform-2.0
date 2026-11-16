import type { Page } from '@playwright/test';
import {
  ACTIVE_ZURICH_FSPS,
  LANGUAGES,
  PHONE_MAX,
  acceptPendingConsents,
  clippedTextsOn,
  expectNoMissingKeys,
  expectNoOverflow,
  type Language,
} from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';
import { consentRequest } from '../../tests/a11y/routes';

/**
 * Five-language smoke (D18.3, plan §18.3, plan-m7 F6; AD1 §14.4 "translations"): a visitor and a signed-in member
 * switch the UI language through the shell (the side menu's "Sprache & Darstellung" or the user menu on desktop, the
 * header's language menu on phones) and read the pages of every module in each of the five languages: the right
 * `<html lang>`, no raw translation key, no horizontal overflow and no text cut off sideways (`expectNoOverflow`,
 * review-m7 rec. 7; also toasts closer than 16px to the edge). The overflow audit itself is proven to catch clipped
 * text. Public pages keep the old dark green in the dark theme (decision 178).
 *
 * Language and theme are not pinned here (`pinSettings: false`): the visitor starts in German from the browser
 * locale (de-CH), so the checkpoints are taken before the first switch.
 *
 * Covered below e2e: `npm run i18n:check` (scripts/check-i18n.mjs: the five catalogs have the same keys, every key
 * used in code exists in every language, errors.<CODE> for every ApiErrorCode, same placeholders), so every page of
 * every module no longer needs its own visit per language; optimus-locale.spec (datepicker month/day names, Today,
 * Monday first per language); shell.component.spec "toast breakpoints" (16px gutter on phones).
 */
test.use({ pinSettings: false });

const HTML_LANG: Record<Language, string> = { de: 'de', gws: 'de-CH', fr: 'fr', it: 'it', en: 'en' };
/** From the other end of the list, so every language is a real switch; German comes last. */
const ROUNDS: Language[] = ['it', 'fr', 'gws', 'en', 'de'];

const ZH = '00000010-0001-4000-8000-000000000040';
const BASKET_JOGHURT = '00000048-0001-4000-8000-000000000003';

interface Visit {
  path: string;
  /** Proves the page content (not only the shell) rendered. */
  ready: string;
  /** The page shows loading specimens on purpose (the gallery): do not wait for data states. */
  live?: boolean;
  /** Opens this URL instead of `path` (computed once per visit, e.g. a fresh OAuth authorize request). */
  url?: () => Promise<string>;
}

const VISITOR_PAGES: Visit[] = [
  { path: '/', ready: 'public.landing.hero' },
  { path: '/about/partners', ready: 'content.page.body' },
  { path: '/statistics', ready: 'content.statistics.tile' },
  { path: '/map', ready: 'map.page.layers' },
  { path: '/faq', ready: 'content.faq.entry' },
  { path: '/blog', ready: 'blog.list.grid' },
  { path: '/does-not-exist', ready: 'errorPages.notFound' },
  { path: '/dev/gallery', ready: 'gallery.live.state', live: true },
];

/** One page per module of the signed-in app (A6 profiles, B1 baskets, FS1, M1, C5, AC1, C6 resources, regions). */
const MEMBER_PAGES: Visit[] = [
  { path: '/dashboard', ready: 'dashboard.page.welcome' },
  { path: '/profile/me?tab=bananas', ready: 'profiles.bananas.list' },
  { path: '/baskets', ready: 'baskets.nearby.list' },
  { path: `/baskets/${BASKET_JOGHURT}`, ready: 'baskets.detail.content' },
  { path: `/food-share-points/${ACTIVE_ZURICH_FSPS[0]}`, ready: 'foodSharePoints.detail.wall.list' },
  { path: '/map', ready: 'map.page.storeFilter' },
  { path: '/search?q=Wiedikon', ready: 'search.page.input' },
  { path: `/regions/${ZH}/achievements`, ready: 'achievements.board.list' },
  { path: `/regions/${ZH}/statistics`, ready: 'regions.statistics.genderList' },
  { path: `/regions/${ZH}/resources`, ready: 'resources.region.commons' },
  { path: `/regions/${ZH}/mediation`, ready: 'reports.mediation.team' },
];

const isPhoneWidth = (page: Page) => (page.viewportSize()?.width ?? 1280) <= PHONE_MAX;

/** Every animated counter has counted up to its final, locale-formatted value. */
async function countersDone(page: Page): Promise<void> {
  const counters = page.locator('.fs-animated-counter');
  await expect(counters.first()).toBeVisible();
  await expect
    .poll(() => counters.evaluateAll((nodes) => nodes.filter((node) => node.children[0]?.textContent !== node.children[1]?.textContent).length))
    .toBe(0);
}

/** Opens the page and waits until its content is there. */
async function open(page: Page, visit: Visit): Promise<void> {
  await page.goto(visit.url ? await visit.url() : visit.path);
  await expect(page.getByTestId(visit.ready).first(), visit.path).toBeVisible();
  if (!visit.live) {
    await expect
      .poll(() => page.locator('[data-state="loading"], [data-state="refreshing"]').count(), { message: `${visit.path} still loading` })
      .toBe(0);
  }
  if (visit.path === '/statistics') await countersDone(page);
}

/** The page reads right in `lang`: `<html lang>`, no raw keys, nothing overflowing or cut off. */
async function readsIn(page: Page, visit: Visit, lang: Language, who: string): Promise<void> {
  await open(page, visit);
  await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[lang]);
  await expectNoMissingKeys(page);
  await expectNoOverflow(page, `${visit.path} as ${who} in ${lang}`);
}

/**
 * Switches the language like the persona: the header language menu on phones; on desktop the side menu's
 * "Sprache & Darstellung" card (visitors) or the language row of the user menu (signed in).
 */
async function switchLanguage(walk: Walk, lang: Language, signedIn: boolean): Promise<void> {
  const { page } = walk;
  if (isPhoneWidth(page)) {
    await walk.click(page.getByTestId('shell.languageMenu'));
    await walk.click(page.getByTestId(`shell.language.${lang}`));
  } else if (signedIn) {
    await walk.click(page.getByTestId('shell.userMenu'));
    await walk.click(page.getByTestId('shell.userMenu.language'));
    await walk.click(page.getByTestId(`shell.userMenu.language.${lang}`));
    await page.keyboard.press('Escape');
  } else {
    await walk.click(page.getByTestId('shell.prefs'));
    await walk.click(page.getByTestId(`shell.prefs.language.${lang}`));
    await page.keyboard.press('Escape');
  }
  await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[lang]);
}

test('visitor reads the public pages in all five languages @D18.3 @AD1 @C1', async ({ page }, testInfo) => {
  test.setTimeout(240_000);
  const walk = new Walk(page, testInfo, 'visitor');

  await walk.step('arrives in German from the browser language', async () => {
    await open(page, VISITOR_PAGES[2]);
    await expect(page.locator('html')).toHaveAttribute('lang', 'de');
    await checkpoint(page, 'statistics');
    await open(page, VISITOR_PAGES[1]);
    await checkpoint(page, 'partners');
    await open(page, VISITOR_PAGES[6]);
    await checkpoint(page, 'not-found');
    await open(page, VISITOR_PAGES[0]);
    if (isPhoneWidth(page)) {
      await walk.click(page.getByTestId('shell.languageMenu'));
      await expect(page.getByTestId('shell.language.de')).toHaveAttribute('aria-checked', 'true');
    } else {
      await walk.click(page.getByTestId('shell.prefs'));
      await expect(page.getByTestId('shell.prefs.language.de')).toHaveAttribute('aria-pressed', 'true');
    }
    await checkpoint(page, 'language-choice', { fullPage: false });
    await page.keyboard.press('Escape');
  });

  await walk.step('the overflow audit catches clipped text and nothing else', async () => {
    await page.evaluate(() => {
      const word = 'Lebensmittelrettungsbotschafterinnenversammlung';
      document.body.insertAdjacentHTML(
        'beforeend',
        `<div style="width:120px;overflow:hidden"><span data-testid="audit.clipped">${word}</span></div>
         <div style="width:120px;overflow-x:auto"><span data-testid="audit.scroller">${word}</span></div>
         <div style="width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" data-testid="audit.ellipsis">${word}</div>
         <span class="fs-visually-hidden" data-testid="audit.srOnly">${word}</span>`,
      );
    });
    const findings = await clippedTextsOn(page);
    expect(findings.some((finding) => finding.startsWith('audit.clipped '))).toBe(true);
    expect(findings.filter((finding) => /^audit\.(scroller|ellipsis|srOnly) /.test(finding))).toEqual([]);
  });

  for (const lang of ROUNDS) {
    await walk.step(`switches to ${lang} and reads every public page`, async () => {
      await open(page, VISITOR_PAGES[0]);
      await switchLanguage(walk, lang, false);
      for (const visit of VISITOR_PAGES) await readsIn(page, visit, lang, 'visitor');
      // the language menu names the language (decision 133: phones in the header, desktop in the side menu)
      await expect(page.getByTestId(isPhoneWidth(page) ? 'shell.languageMenu' : 'shell.prefs')).toBeVisible();
    });
  }

  await walk.step('reads the public pages in the dark theme', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
    for (const url of ['/', '/faq', '/login']) {
      await page.goto(url);
      await expect(page.locator('html')).toHaveClass(/fs-dark/);
      const main = page.locator('main#main-content');
      await expect(main).toHaveClass(/fs-public/);
      expect(await main.evaluate((element) => getComputedStyle(element).getPropertyValue('--fs-primary').trim()), url).toBe('#8fd14d');
    }
  });

  expect(LANGUAGES.every((lang) => ROUNDS.includes(lang))).toBe(true);
  // 1 open + 2 per round (phones) / 2 per round (desktop)
  walk.finish({ steps: 8, clicks: 11, fields: 0 });
});

test.describe('signed in', () => {
  test.use({ role: 'foodsaver' });

  test('member reads her pages and the OAuth consent in all five languages @D18.3 @C13', async ({ page, apiOf }, testInfo) => {
    test.setTimeout(300_000);
    const api = apiOf(page);
    await acceptPendingConsents(api);
    const walk = new Walk(page, testInfo, 'foodsaver');

    for (const lang of ROUNDS) {
      await walk.step(`switches to ${lang} and reads her pages`, async () => {
        await open(page, MEMBER_PAGES[0]);
        await switchLanguage(walk, lang, true);
        // signed in, the language is saved with the account
        await expect.poll(async () => (await api.getJson<{ language: Language }>('/api/users/me')).language).toBe(lang);
        for (const visit of MEMBER_PAGES) await readsIn(page, visit, lang, 'foodsaver');
        // C13: the consent page of a real OAuth authorize request
        await readsIn(page, { path: '/oauth/consent', ready: 'oauth.consent.approve', url: () => consentRequest(api) }, lang, 'foodsaver');
      });
    }

    // desktop: 3 clicks per round (user menu, language row, language); phones: 2
    walk.finish({ steps: 5, clicks: 15, fields: 0 });
  });
});
