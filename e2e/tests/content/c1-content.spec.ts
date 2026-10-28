import { LANGUAGES, acceptPendingConsents, expect, expectNoMissingKeys, setLanguage, test } from '../../fixtures';

/** C1 (partial, M2): learning chapters in five languages marked read via the journey; content pages edited by ORGA. */

test.use({ seedReset: 'test' });

const CHAPTERS = ['rules', 'hygiene', 'how-a-pickup-works', 'etiquette'];
const RULES_TITLE: Record<string, string> = {
  de: 'Die Regeln',
  en: 'The rules',
  gws: 'D Regle',
  fr: 'Les règles',
  it: 'Le regole',
};

test('C1 learning chapters readable in five languages and marked read @C1 @page', async ({ as, apiOf, page: guest }) => {
  // public: guests can read the chapters in every language
  for (const lang of LANGUAGES) {
    await setLanguage(guest, lang);
    await guest.goto('/learn');
    await expect(guest.getByTestId('content.learn.state')).toHaveAttribute('data-state', 'success');
    await expect(guest.getByTestId('content.learn.chapter')).toHaveCount(CHAPTERS.length);
    await expect(guest.getByTestId('content.learn.chapter').first()).toContainText(RULES_TITLE[lang]);
    await expectNoMissingKeys(guest);
  }
  await guest.getByTestId('content.learn.chapter').first().click();
  await expect(guest).toHaveURL(/\/learn\/rules$/);
  await expect(guest.getByTestId('content.chapter.body')).not.toBeEmpty();
  await expect(guest.getByTestId('content.chapter.markRead')).toHaveCount(0);

  // newbie@local has read nothing yet
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/learn');
  const progress = page.getByTestId('content.learn.progress');
  await expect(progress).toHaveAttribute('data-read', '0');
  await expect(page.locator(`[data-testid="content.learn.chapter"][data-key="rules"]`)).toHaveAttribute(
    'data-read-state',
    'next',
  );
  await page.getByTestId('content.learn.continue').click();
  await expect(page).toHaveURL(/\/learn\/rules$/);
  await expect(page.getByTestId('content.chapter.minutes')).toBeVisible();

  // mark every chapter read: each one continues to the next, the last one shows the done state
  for (const [index, key] of CHAPTERS.entries()) {
    await expect(page.getByTestId('content.chapter.article')).toHaveAttribute('data-key', key);
    await page.getByTestId('content.chapter.markRead').click();
    if (index < CHAPTERS.length - 1) {
      await expect(page).toHaveURL(new RegExp(`/learn/${CHAPTERS[index + 1]}$`));
    }
  }
  await expect(page.getByTestId('content.chapter.done')).toBeVisible();
  await page.getByTestId('content.chapter.next').click();
  await expect(page).toHaveURL(/\/learn$/);
  await expect(progress).toHaveAttribute('data-read', String(CHAPTERS.length));
  for (const key of CHAPTERS) {
    await expect(page.locator(`[data-testid="content.learn.chapter"][data-key="${key}"]`)).toHaveAttribute(
      'data-read-state',
      'read',
    );
  }
  const journey = await apiOf(page).getJson<{ steps: { key: string; status: string }[] }>('/api/onboarding/journey');
  expect(journey.steps.find((step) => step.key === 'LEARN')?.status).toBe('DONE');
});

test('C1 orga edits a content page in five languages @C1', async ({ as, page: guest }) => {
  const orga = await as('orga');
  await orga.goto('/dashboard');
  if ((orga.viewportSize()?.width ?? 0) >= 1024) await expect(orga.getByTestId('nav.adminContent')).toBeAttached();
  await orga.goto('/admin/content');
  await expect(orga.getByTestId('content.admin.state')).toHaveAttribute('data-state', 'success');
  await orga.locator('[data-testid="content.admin.page"][data-slug="vision"]').click();
  await expect(orga).toHaveURL(/\/admin\/content\/vision$/);
  await expect(orga.getByTestId('content.editor.state')).toHaveAttribute('data-state', 'success');

  const texts: Record<string, string> = {
    de: 'Wir retten Lebensmittel – gemeinsam.',
    en: 'We save food – together.',
    gws: 'Mir rettet Läbesmittel – zäme.',
    fr: 'Nous sauvons des aliments – ensemble.',
    it: 'Salviamo il cibo – insieme.',
  };
  for (const lang of LANGUAGES) {
    await orga.getByTestId(`content.editor.bodies.tab.${lang}`).click();
    await orga.getByTestId(`content.editor.bodies.field.${lang}`).fill(`## ${texts[lang]}\n\n${texts[lang]}`);
  }
  // a title without body is pointed out and blocks saving
  await orga.getByTestId('content.editor.bodies.tab.it').click();
  await orga.getByTestId('content.editor.bodies.field.it').fill('');
  await expect(orga.getByTestId('content.editor.missingBodies')).toContainText('it');
  await expect(orga.getByTestId('content.editor.save').locator('button')).toBeDisabled();
  await orga.getByTestId('content.editor.bodies.field.it').fill(`## ${texts.it}\n\n${texts.it}`);
  await orga.getByTestId('content.editor.reason').fill('e2e');
  await orga.getByTestId('content.editor.save').click();
  await expect(orga.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

  for (const lang of LANGUAGES) {
    await setLanguage(guest, lang);
    await guest.goto('/pages/vision');
    await expect(guest.getByTestId('content.page.body')).toContainText(texts[lang]);
    await expect(guest.getByTestId('content.page.fallback')).toHaveCount(0);
  }

  // a new draft page is not public until published
  await orga.goto('/admin/content/new');
  await orga.getByTestId('content.editor.slug').fill('e2e-draft');
  await orga.getByTestId('content.editor.titles.field.de').fill('Entwurf');
  await orga.getByTestId('content.editor.bodies.field.de').fill('Noch geheim.');
  await orga.getByTestId('content.editor.save').click();
  await expect(orga).toHaveURL(/\/admin\/content\/e2e-draft$/);
  await guest.goto('/pages/e2e-draft');
  await expect(guest.getByTestId('content.page.state')).toHaveAttribute('data-state', 'notFound');
  await orga.getByTestId('content.editor.published').locator('input').check();
  await orga.getByTestId('content.editor.save').click();
  await expect(orga.getByTestId('content.editor.save').locator('button')).toBeEnabled();
  await guest.reload();
  await expect(guest.getByTestId('content.page.body')).toContainText('Noch geheim.');
  // German fallback in other languages
  await setLanguage(guest, 'fr');
  await guest.reload();
  await expect(guest.getByTestId('content.page.fallback')).toBeVisible();
});

test('C1 content editing is refused for plain foodsavers @C1', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/admin/content');
  await expect(page.getByTestId('content.admin.state')).toHaveAttribute('data-state', 'forbidden');
  await page.goto('/admin/legal');
  await expect(page.getByTestId('legal.admin.versions')).toHaveAttribute('data-state', 'forbidden');
});
