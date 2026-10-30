import path from 'node:path';
import { LANGUAGES, expect, expectDataState, expectNoMissingKeys, pickOption, setLanguage, test } from '../../fixtures';

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

/** A 256 px PNG (the screenshot tile stub). */
const PICTURE = path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png');

test('C1 editor edits a page body and image @C1', async ({ as, apiOf, page }) => {
  // Q11: ORGA uploads an image, inserts it into the German body, picks another editor function and saves
  const orga = await as('orga');
  await setLanguage(orga, 'de');
  await orga.goto('/admin/content/press');
  await expectDataState(orga, 'content.editor.state', 'success');
  await expect(orga.getByTestId('content.editor.image')).toHaveCount(0);
  const body = orga.getByTestId('content.editor.bodies.field.de');
  await body.fill('## Medienanfragen\n\nSchreib an presse@foodsharing.ch.');
  await orga.getByTestId('content.editor.imageUpload.input').setInputFiles(PICTURE);
  await expect(orga.getByTestId('content.editor.image')).toHaveCount(1);
  const imageId = await orga.getByTestId('content.editor.image').getAttribute('data-id');
  expect(imageId).toMatch(/^[0-9a-f-]{36}$/);
  await orga.getByTestId('content.editor.insertImage').click();
  const imageUrl = `/api/uploads/${imageId}?size=lg`;
  await expect(body).toHaveValue(`## Medienanfragen\n\nSchreib an presse@foodsharing.ch.\n\n![](${imageUrl})`);
  await pickOption(orga, 'content.editor.editorFunction', 'MODERATION');
  await orga.getByTestId('content.editor.reason').fill('e2e: Bild');
  await orga.getByTestId('content.editor.save').click();
  await expect(orga.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

  // saved: the image is claimed by the page, the function changed
  const saved = await apiOf(orga).getJson<PageForEdit & { editorFunction?: string; imageUploadIds?: string[] }>(
    '/api/content/pages/press',
  );
  expect(saved.imageUploadIds).toEqual([imageId]);
  expect(saved.editorFunction).toBe('MODERATION');
  expect(saved.bodies.de).toContain(`![](${imageUrl})`);
  await orga.reload();
  await expectDataState(orga, 'content.editor.state', 'success');
  await expect(orga.getByTestId('content.editor.image')).toHaveCount(1);

  // public: the page shows the image, readable without login (CONTENT_IMAGE is public, A41)
  await setLanguage(page, 'de');
  await page.goto('/about/press');
  await expectDataState(page, 'content.page.state', 'success');
  const image = page.getByTestId('content.page.body').locator('img');
  await expect(image).toHaveAttribute('src', imageUrl);
  await expect.poll(() => image.evaluate((node) => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
});
