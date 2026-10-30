import type { Page } from '@playwright/test';
import { LANGUAGES, acceptPendingConsents, expect, expectNoMissingKeys, setLanguage, test, type ApiClient, type Role } from '../../fixtures';
import type { Language } from '../../fixtures/types';
import { consentRequest } from '../a11y/routes';

/**
 * Five-language smoke (plan §18.3, plan-m7 F6) and the M6 translation sweep (plan-m6 F9): pages open in every UI
 * language without raw translation keys and with the right `<html lang>`. Signed-in users get the language through
 * their account setting (it wins over the device setting), visitors through the stored device language.
 */

const HTML_LANG: Record<Language, string> = { de: 'de', gws: 'de-CH', fr: 'fr', it: 'it', en: 'en' };

const ZH = '00000010-0001-4000-8000-000000000040';
const S01 = '00000020-0001-4000-8000-000000000001';
const BASKET_BROT = '00000048-0001-4000-8000-000000000001';
const BASKET_JOGHURT = '00000048-0001-4000-8000-000000000003';
const FSP_WIEDIKON = '00000049-0001-4000-8000-000000000001';
const REPORT_IN_PROGRESS = '00000050-0001-4000-8000-000000000003';
const JUMPER = '00000001-0001-4000-8000-000000000007';

interface Visit {
  path: string;
  role: Role | null;
  /** Proves the page content (not only the shell) rendered. */
  ready: string;
  /** Computes the URL to open (instead of `path`) as the page's user. */
  resolve?: (api: ApiClient) => Promise<string>;
}

/** Opens the pages of one role in `lang` and checks each for raw keys and the `lang` attribute. */
async function visitAll(
  as: (role: Role | null) => Promise<Page>,
  apiOf: (page: Page) => ApiClient,
  lang: Language,
  visits: Visit[],
  afterReady?: (page: Page, visit: Visit) => Promise<void>,
): Promise<void> {
  const roles = [...new Set(visits.map((visit) => visit.role))];
  for (const role of roles) {
    const page = await as(role);
    if (role) {
      const api = apiOf(page);
      await acceptPendingConsents(api);
      const response = await api.patch('/api/users/me/settings', { language: lang });
      expect(response.status(), await response.text()).toBe(200);
    } else {
      await setLanguage(page, lang);
    }
    for (const visit of visits.filter((entry) => entry.role === role)) {
      await page.goto(visit.resolve ? await visit.resolve(apiOf(page)) : visit.path);
      await expect(page.getByTestId(visit.ready).first(), `${visit.path} as ${role ?? 'visitor'}`).toBeVisible();
      await expect
        .poll(() => page.locator('[data-state="loading"], [data-state="refreshing"]').count(), { message: `${visit.path} still loading` })
        .toBe(0);
      await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[lang]);
      await afterReady?.(page, visit);
      await expectNoMissingKeys(page);
    }
    await page.context().close();
  }
}

/** Every animated counter has counted up to its final, locale-formatted value. */
async function countersDone(page: Page): Promise<void> {
  const counters = page.locator('.fs-animated-counter');
  await expect(counters.first()).toBeVisible();
  await expect
    .poll(() =>
      counters.evaluateAll((nodes) =>
        nodes.filter((node) => node.children[0]?.textContent !== node.children[1]?.textContent).length,
      ),
    )
    .toBe(0);
}

const SMOKE: Visit[] = [
  { path: '/', role: null, ready: 'public.landing.hero' },
  { path: '/about/vision', role: null, ready: 'content.page.body' },
  { path: '/statistics', role: null, ready: 'content.statistics.tile' },
  { path: '/map', role: null, ready: 'map.page.layers' },
  { path: '/onboarding', role: 'newbie', ready: 'onboarding.journey.state' },
  { path: '/dashboard', role: 'foodsaver', ready: 'dashboard.page.welcome' },
  { path: '/profile/me', role: 'foodsaver', ready: 'profiles.page.name' },
  { path: `/baskets/${BASKET_JOGHURT}`, role: 'foodsaver', ready: 'baskets.detail.content' },
  { path: `/food-share-points/${FSP_WIEDIKON}`, role: 'foodsaver', ready: 'foodSharePoints.detail.wall.list' },
  { path: '/search?q=Wiedikon', role: 'foodsaver', ready: 'search.page.input' },
  // C13 consent page of a real authorize request
  { path: '/oauth/consent', role: 'foodsaver', ready: 'oauth.consent.approve', resolve: consentRequest },
  { path: `/stores/${S01}/overview`, role: 'bieb', ready: 'stores.overview.info' },
];

test.describe('five-language smoke', () => {
  for (const lang of LANGUAGES) {
    test(`D18.3 landing, onboarding, dashboard, profile, store, basket, FSP, statistics, map, search, a content page and the OAuth consent in ${lang} @D18.3 @C1 @C13`, async ({
      as,
      apiOf,
    }) => {
      await visitAll(as, apiOf, lang, SMOKE, async (page, visit) => {
        if (visit.path === '/statistics') await countersDone(page);
      });
    });
  }
});

/** plan-m6 F9: every M6 page (profiles, baskets, FSPs, map, reports, achievements, content, search). */
const M6_PAGES: Visit[] = [
  // A6 profiles
  { path: '/profile/me?tab=bananas', role: 'foodsaver', ready: 'profiles.bananas.list' },
  { path: '/profile/me/buddies', role: 'foodsaver', ready: 'profiles.buddies.state' },
  { path: `/profile/${JUMPER}`, role: 'reports', ready: 'reports.user.list' },
  // B1 baskets
  { path: '/baskets', role: 'foodsaver', ready: 'baskets.nearby.list' },
  { path: '/baskets/mine', role: 'foodsaver', ready: 'baskets.mine.offeredList' },
  { path: '/baskets/new', role: 'foodsaver', ready: 'baskets.form' },
  { path: `/baskets/${BASKET_BROT}`, role: 'foodsaver', ready: 'baskets.detail.requests' },
  // FS1 food share points
  { path: `/regions/${ZH}/food-share-points`, role: 'foodsaver', ready: 'foodSharePoints.region.list' },
  { path: '/food-share-points/mine', role: 'foodsaver', ready: 'foodSharePoints.mine.list' },
  { path: `/food-share-points/new?regionId=${ZH}`, role: 'foodsaver', ready: 'foodSharePoints.form' },
  // M1 map (signed in: store filters)
  { path: '/map', role: 'foodsaver', ready: 'map.page.storeFilter' },
  // G1 reports and mediation
  { path: `/regions/${ZH}/reports`, role: 'reports', ready: 'reports.region.list' },
  { path: `/regions/${ZH}/report-log`, role: 'reports', ready: 'reports.log.count' },
  { path: `/reports/${REPORT_IN_PROGRESS}`, role: 'reports', ready: 'reports.detail.wall.list' },
  { path: `/regions/${ZH}/mediation`, role: 'foodsaver', ready: 'reports.mediation.team' },
  // AC1 achievements
  { path: `/regions/${ZH}/achievements`, role: 'foodsaver', ready: 'achievements.board.list' },
  { path: '/admin/achievements', role: 'orga', ready: 'achievements.board.list' },
  // C1 content (+ C3/C4/C7/C8/C16 pages)
  { path: '/about', role: null, ready: 'content.about.link' },
  { path: '/imprint', role: null, ready: 'content.page.body' },
  { path: '/faq', role: null, ready: 'content.faq.entry' },
  { path: '/team', role: null, ready: 'content.team.group' },
  { path: '/release-notes', role: null, ready: 'content.releaseNotes.note' },
  { path: '/petition', role: null, ready: 'content.petition.card' },
  { path: '/donate', role: null, ready: 'content.donate.info' },
  { path: '/blog', role: null, ready: 'blog.list.grid' },
  { path: `/regions/${ZH}/statistics`, role: 'foodsaver', ready: 'regions.statistics.genderList' },
  // C5 search, C6 resources
  { path: `/regions/${ZH}/resources`, role: 'foodsaver', ready: 'resources.region.commons' },
  { path: '/resources/mine', role: 'foodsaver', ready: 'resources.mine.list' },
];

test.describe('M6 pages translated', () => {
  for (const lang of LANGUAGES) {
    test(`M6 pages translated (${lang}) @A6 @B1 @FS1 @M1 @G1 @AC1 @C1 @C5`, async ({ as, apiOf }) => {
      await visitAll(as, apiOf, lang, M6_PAGES);
    });
  }
});
