import { LANGUAGES, acceptPendingConsents, expect, expectNoMissingKeys, setLanguage, test } from '../../fixtures';

/**
 * C1: the knowledge area ("Wissen", decision 174): the foodsharing-Grundlagen in five languages, grouped, marked read via
 * the journey and reread without marking again; content pages edited by ORGA.
 */

test.use({ seedReset: 'test' });

const CHAPTERS = [
  'principles',
  'code-of-conduct',
  'rule-violations',
  'hygiene-rules',
  'role-requirements',
  'legal-agreement',
  'context-and-self-image',
];
const FIRST_TITLE: Record<string, string> = {
  de: 'Grundsätze',
  en: 'Principles',
  gws: 'Grundsätz',
  fr: 'Principes',
  it: 'Principi',
};

test('C1 learning chapters readable in five languages and marked read @C1 @page', async ({ as, apiOf, page: guest }) => {
  // public: guests can read the chapters in every language
  for (const lang of LANGUAGES) {
    await setLanguage(guest, lang);
    await guest.goto('/learn');
    await expect(guest.getByTestId('content.learn.state')).toHaveAttribute('data-state', 'success');
    await expect(guest.getByTestId('content.learn.chapter')).toHaveCount(CHAPTERS.length);
    await expect(guest.getByTestId('content.learn.chapter').first()).toContainText(FIRST_TITLE[lang]);
    // all seven are foodsharing-Grundlagen, so there is one group heading
    await expect(guest.getByTestId('content.learn.group')).toHaveCount(1);
    await expect(guest.getByTestId('content.learn.group')).toHaveAttribute('data-group', 'BASICS');
    await expectNoMissingKeys(guest);
  }
  await guest.getByTestId('content.learn.chapter').first().click();
  await expect(guest).toHaveURL(/\/learn\/principles$/);
  await expect(guest.getByTestId('content.chapter.body')).not.toBeEmpty();
  await expect(guest.getByTestId('content.chapter.markRead')).toHaveCount(0);

  // newbie@local has read nothing yet
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/learn');
  const progress = page.getByTestId('content.learn.progress');
  await expect(progress).toHaveAttribute('data-read', '0');
  await expect(page.locator(`[data-testid="content.learn.chapter"][data-key="principles"]`)).toHaveAttribute(
    'data-read-state',
    'next',
  );
  await page.getByTestId('content.learn.continue').click();
  await expect(page).toHaveURL(/\/learn\/principles$/);
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

  // rereading: the overview offers it, a read chapter says so and marks nothing again (no API call)
  await expect(page.getByTestId('content.learn.reread')).toBeVisible();
  const marks: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().includes('/api/onboarding/learning/')) marks.push(request.url());
  });
  await page.getByTestId('content.learn.reread').click();
  await expect(page).toHaveURL(/\/learn\/principles$/);
  await expect(page.getByTestId('content.chapter.alreadyRead')).toBeVisible();
  await expect(page.getByTestId('content.chapter.markRead')).toHaveCount(0);
  await page.getByTestId('content.chapter.next').click();
  await expect(page).toHaveURL(/\/learn\/code-of-conduct$/);
  await expect(page.getByTestId('content.chapter.alreadyRead')).toBeVisible();
  await page.getByTestId('content.chapter.overview').click();
  await expect(progress).toHaveAttribute('data-read', String(CHAPTERS.length));
  expect(marks).toEqual([]);
});

test('C1 the knowledge area stays reachable: visitor menu, a finished foodsaver\'s menu and journey @C1', async ({ as, page: guest }) => {
  // visitors: with the public pages (side menu on desktop, footer everywhere)
  await guest.goto('/statistics');
  await expect(guest.getByTestId('nav.footer.learnAbout')).toHaveAttribute('href', '/learn');
  if ((guest.viewportSize()?.width ?? 0) >= 1024) await expect(guest.getByTestId('nav.learnAbout')).toBeAttached();

  // foodsaver@local finished the journey: "Wissen" in Community and the relearn link on the done LEARN step
  const page = await as('foodsaver');
  await page.goto('/dashboard');
  if ((page.viewportSize()?.width ?? 0) >= 1024) await expect(page.getByTestId('nav.learn')).toBeAttached();
  await page.goto('/onboarding');
  await expect(page.getByTestId('onboarding.journey.stepper.step.LEARN')).toHaveAttribute('data-status', 'DONE');
  await page.getByTestId('onboarding.journey.stepper.relearn.LEARN').click();
  await expect(page).toHaveURL(/\/learn$/);
  await expect(page.getByTestId('content.learn.progress')).toHaveAttribute('data-read', String(CHAPTERS.length));
  await page.locator('[data-testid="content.learn.chapter"][data-key="hygiene-rules"]').click();
  await expect(page.getByTestId('content.chapter.alreadyRead')).toBeVisible();
  // a section link of another chapter navigates in the app and lands on the section
  await page.goto('/learn/rule-violations');
  await page.getByTestId('content.chapter.body').getByRole('link', { name: 'C6)' }).click();
  await expect(page).toHaveURL(/\/learn\/code-of-conduct#c-betriebe-betriebsteam-planung-von-abholungen$/);
  await expect(page.locator('[id="c-betriebe-betriebsteam-planung-von-abholungen"]')).toBeInViewport();
});

test('C1 orga edits a content page in five languages @C1', async ({ as, page: guest }) => {
  const orga = await as('orga');
  await orga.goto('/dashboard');
  if ((orga.viewportSize()?.width ?? 0) >= 1024) await expect(orga.getByTestId('nav.adminContent')).toBeAttached();
  await orga.goto('/admin/content');
  await expect(orga.getByTestId('content.admin.state')).toHaveAttribute('data-state', 'success');
  await orga.locator('[data-testid="content.admin.page"][data-slug="education"]').click();
  await expect(orga).toHaveURL(/\/admin\/content\/education$/);
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
    await guest.goto('/pages/education');
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
