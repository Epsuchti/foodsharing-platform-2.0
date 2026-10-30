import { LANGUAGES, expect, expectDataState, expectNoMissingKeys, setLanguage, test } from '../../fixtures';

/**
 * C1 (plan-m7 Q10): the public CMS pages under their canonical routes (`/about/*`, `/imprint`, `/for-companies`) in all
 * five languages; a page without a translation shows the German text with the fallback notice. Also the about
 * overview, the FAQ (accordion + filter) and the redirect of body links (`/content/<slug>`).
 */

test.use({ seedReset: 'test' });

const PAGES: Record<string, string> = {
  '/about/vision': 'vision',
  '/about/team': 'team',
  '/about/partners': 'partners',
  '/about/press': 'press',
  '/about/cities': 'foodsharing-cities',
  '/about/demands': 'demands',
  '/about/transparency': 'transparency',
  '/about/education': 'education',
  '/about/communities': 'communities',
  '/about/international': 'international',
  '/about/security': 'security',
  '/imprint': 'imprint',
  '/for-companies': 'for-companies',
};

interface PageForEdit {
  titles: Record<string, string>;
  bodies: Record<string, string>;
  kind: string;
  published: boolean;
  sortOrder?: number;
}

test('C1 every public page renders in five languages with fallback notice for missing translations @C1 @page', async ({
  api,
  page,
}) => {
  test.slow(); // 13 pages × 5 languages
  // ORGA removes every translation of the press page but German
  await api.loginAs('orga');
  const press = await api.getJson<PageForEdit>('/api/content/pages/press');
  await api.putJson('/api/content/pages/press', {
    ...press,
    titles: { de: press.titles.de },
    bodies: { de: press.bodies.de },
    reason: 'e2e: nur Deutsch',
  });

  for (const lang of LANGUAGES) {
    await setLanguage(page, lang);
    for (const [path, slug] of Object.entries(PAGES)) {
      await page.goto(path);
      await expectDataState(page, 'content.page.state', 'success');
      await expect(page.getByTestId('content.page.article')).toHaveAttribute('data-slug', slug);
      await expect(page.getByTestId('content.page.body')).not.toBeEmpty();
      const fallback = page.getByTestId('content.page.fallback');
      if (slug === 'press' && lang !== 'de') {
        await expect(fallback).toBeVisible();
        await expect(page.getByTestId('content.page.body')).toContainText('presse@foodsharing.ch');
      } else {
        await expect(fallback).toHaveCount(0);
      }
    }
    await expectNoMissingKeys(page);

    await page.goto('/about');
    await expect(page.getByTestId('content.about.link')).toHaveCount(Object.keys(PAGES).length + 4);
    await expectNoMissingKeys(page);
  }

  // body links of the seed (`/content/for-companies`) land on the canonical route
  await page.goto('/content/for-companies');
  await expect(page).toHaveURL(/\/for-companies$/);
  await expectDataState(page, 'content.page.state', 'success');

  // the about overview links to every page
  await page.goto('/about');
  await page.locator('[data-testid="content.about.link"][data-page="cities"]').click();
  await expect(page).toHaveURL(/\/about\/cities$/);
  await expect(page.getByTestId('content.page.article')).toHaveAttribute('data-slug', 'foodsharing-cities');
});

test('C1 FAQ lists the published questions and filters them @C1 @page', async ({ page }) => {
  await setLanguage(page, 'de');
  await page.goto('/faq');
  await expectDataState(page, 'content.faq.state', 'success');
  const entries = page.getByTestId('content.faq.entry');
  await expect(entries).toHaveCount(12);
  // answers open on click (native details/summary)
  const first = entries.first();
  await first.getByTestId('content.faq.question').click();
  await expect(first.getByTestId('content.faq.answer')).toBeVisible();

  // the filter searches answers too, without case and accents: "KOSTENLOS" finds faq-01 ("… ist kostenlos …")
  await page.getByTestId('content.faq.filter').fill('KOSTENLOS');
  await expect(entries.first()).toHaveAttribute('data-slug', 'faq-01');
  await expect.poll(() => entries.count()).toBeLessThan(12);

  await page.getByTestId('content.faq.filter').fill('xyz-gibt-es-nicht');
  await expect(entries).toHaveCount(0);
  await expect(page.getByTestId('content.faq.noMatch')).toBeVisible();
  await page.getByTestId('content.faq.filter').fill('');
  await expect(entries).toHaveCount(12);

  // other languages: translated questions, no missing keys
  for (const lang of LANGUAGES) {
    await setLanguage(page, lang);
    await expectDataState(page, 'content.faq.state', 'success');
    await expect(entries).toHaveCount(12);
    await expectNoMissingKeys(page);
  }
});
