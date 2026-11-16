import path from 'node:path';
import { LANGUAGES, acceptPendingConsents, expectDataState, pickOption, setLanguage } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * ORGA (orga@local) looks after the public texts: rewrites the education page in all five languages (a language with a
 * title but no text blocks saving), puts a picture into the press page, starts a draft page that stays hidden until
 * it is published (French readers get the German text with a notice), writes the release note of the next version
 * with a French translation, corrects it and removes it again, and publishes a new privacy policy version. A visitor
 * checks every result. Rules behind it: ContentEditorPermissionTest, ContentPageImagesTest, ReleaseNoteServiceTest,
 * LegalServiceTest; client content-q10.spec / content.util.spec (release note translations, header dot).
 */
test.use({ role: 'orga' });

/** A 256 px PNG (the screenshot tile stub). */
const PICTURE = path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png');

const EDUCATION: Record<string, string> = {
  de: 'Wir retten Lebensmittel – gemeinsam.',
  en: 'We save food – together.',
  gws: 'Mir rettet Läbesmittel – zäme.',
  fr: 'Nous sauvons des aliments – ensemble.',
  it: 'Salviamo il cibo – insieme.',
};

test('orga edits content pages in five languages, writes a release note and publishes a legal version @C1 @C4 @Ve1', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(180_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');
  const guest = await as(null);
  const toast = page.getByTestId('shell.toast').locator('.p-toast-message').first();

  await walk.step('rewrites the education page in five languages', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminContent');
    await expect(page).toHaveURL(/\/admin\/content$/);
    await expectDataState(page, 'content.admin.state', 'success');
    await checkpoint(page, 'content-admin');
    await walk.click(page.locator('[data-testid="content.admin.page"][data-slug="education"]'));
    await expect(page).toHaveURL(/\/admin\/content\/education$/);
    await expectDataState(page, 'content.editor.state', 'success');
    for (const lang of LANGUAGES) {
      await walk.click(page.getByTestId(`content.editor.bodies.tab.${lang}`));
      await walk.fill(page.getByTestId(`content.editor.bodies.field.${lang}`), `## ${EDUCATION[lang]}\n\n${EDUCATION[lang]}`);
    }
    // a title without text is pointed out and blocks saving
    await walk.fill(page.getByTestId('content.editor.bodies.field.it'), '');
    await expect(page.getByTestId('content.editor.missingBodies')).toContainText('it');
    await expect(page.getByTestId('content.editor.save').locator('button')).toBeDisabled();
    await checkpoint(page, 'content-editor-missing-text');
    await walk.fill(page.getByTestId('content.editor.bodies.field.it'), `## ${EDUCATION.it}\n\n${EDUCATION.it}`);
    await walk.fill(page.getByTestId('content.editor.reason'), 'Neuer Leitsatz');
    await walk.click(page.getByTestId('content.editor.save'));
    await expect(toast).toBeVisible();
    for (const lang of ['de', 'fr'] as const) {
      await setLanguage(guest, lang);
      await guest.goto('/about/education');
      await expect(guest.getByTestId('content.page.body')).toContainText(EDUCATION[lang]);
      await expect(guest.getByTestId('content.page.fallback')).toHaveCount(0);
    }
    await setLanguage(guest, 'de');
  });

  await walk.step('puts a picture into the press page', async () => {
    await page.goto('/admin/content/press');
    await expectDataState(page, 'content.editor.state', 'success');
    await expect(page.getByTestId('content.editor.image')).toHaveCount(0);
    const body = page.getByTestId('content.editor.bodies.field.de');
    await walk.fill(body, '## Medienanfragen\n\nSchreib an presse@foodsharing.ch.');
    await page.getByTestId('content.editor.imageUpload.input').setInputFiles(PICTURE);
    walk.count(1);
    await expect(page.getByTestId('content.editor.image')).toHaveCount(1);
    const imageId = await page.getByTestId('content.editor.image').getAttribute('data-id');
    await walk.click(page.getByTestId('content.editor.insertImage'));
    const imageUrl = `/api/uploads/${imageId}?size=lg`;
    await expect(body).toHaveValue(`## Medienanfragen\n\nSchreib an presse@foodsharing.ch.\n\n![](${imageUrl})`);
    await pickOption(page, 'content.editor.editorFunction', 'MODERATION');
    walk.count(2);
    await walk.fill(page.getByTestId('content.editor.reason'), 'Bild ergänzt');
    await checkpoint(page, 'content-editor-image');
    await walk.click(page.getByTestId('content.editor.save'));
    await expect(toast).toBeVisible();
    // public: the picture is readable without login
    await guest.goto('/about/press');
    await expectDataState(guest, 'content.page.state', 'success');
    const image = guest.getByTestId('content.page.body').locator('img');
    await expect(image).toHaveAttribute('src', imageUrl);
    await expect.poll(() => image.evaluate((node) => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  });

  await walk.step('starts a draft page and publishes it', async () => {
    await page.goto('/admin/content');
    await walk.click(page.getByTestId('content.admin.new'));
    await expect(page).toHaveURL(/\/admin\/content\/new$/);
    await walk.fill(page.getByTestId('content.editor.slug'), 'sommerfest');
    await walk.fill(page.getByTestId('content.editor.titles.field.de'), 'Sommerfest');
    await walk.fill(page.getByTestId('content.editor.bodies.field.de'), 'Am 21. Juni feiern wir im Gemeinschaftsgarten.');
    await walk.click(page.getByTestId('content.editor.save'));
    await expect(page).toHaveURL(/\/admin\/content\/sommerfest$/);
    await guest.goto('/pages/sommerfest');
    await expectDataState(guest, 'content.page.state', 'notFound');
    await walk.click(page.getByTestId('content.editor.published').locator('input'));
    await walk.click(page.getByTestId('content.editor.save'));
    await expect(page.getByTestId('content.editor.save').locator('button')).toBeEnabled();
    await guest.reload();
    await expect(guest.getByTestId('content.page.body')).toContainText('Gemeinschaftsgarten');
    await checkpoint(guest, 'draft-page-published');
    // German text with a notice in the other languages
    await setLanguage(guest, 'fr');
    await expect(guest.getByTestId('content.page.fallback')).toBeVisible();
    await setLanguage(guest, 'de');
  });

  await walk.step('writes the release note of the next version', async () => {
    await walk.nav('adminReleaseNotes');
    await expect(page).toHaveURL(/\/admin\/release-notes$/);
    await expectDataState(page, 'content.releaseAdmin.state', 'success');
    const notes = page.getByTestId('content.releaseAdmin.note');
    await expect(notes).toHaveCount(5);
    await walk.click(page.getByTestId('content.releaseAdmin.new'));
    await walk.fill(page.getByTestId('content.releaseAdmin.version'), '6.0.0');
    await walk.fill(page.getByTestId('content.releaseAdmin.titles.field.de'), 'Inhalte und Statistik');
    await walk.fill(page.getByTestId('content.releaseAdmin.bodies.field.de'), 'Neue **Teamseite**, Statistik und FAQ.');
    await walk.click(page.getByTestId('content.releaseAdmin.titles.tab.fr'));
    await walk.fill(page.getByTestId('content.releaseAdmin.titles.field.fr'), 'Contenus et statistiques');
    // a title without text cannot be saved
    await expect(page.getByTestId('content.releaseAdmin.incomplete')).toContainText('FR');
    await expect(page.getByTestId('content.releaseAdmin.save').locator('button')).toBeDisabled();
    await checkpoint(page, 'release-note-incomplete');
    await walk.click(page.getByTestId('content.releaseAdmin.bodies.tab.fr'));
    await walk.fill(page.getByTestId('content.releaseAdmin.bodies.field.fr'), 'Nouvelle **page de l’équipe**, statistiques et FAQ.');
    await walk.click(page.getByTestId('content.releaseAdmin.save'));
    await expect(page.getByTestId('content.releaseAdmin.dialog')).toBeHidden();
    await expect(notes).toHaveCount(6);
    await expect(notes.first()).toHaveAttribute('data-version', '6.0.0');

    // a French visitor reads the new note first, translated and marked new
    await setLanguage(guest, 'fr');
    await guest.goto('/release-notes');
    await expectDataState(guest, 'content.releaseNotes.state', 'success');
    const publicNotes = guest.getByTestId('content.releaseNotes.note');
    await expect(publicNotes.first()).toHaveAttribute('data-version', '6.0.0');
    await expect(publicNotes.first()).toContainText('Contenus et statistiques');
    await expect(publicNotes.first().locator('strong')).toHaveText('page de l’équipe');
    await expect(publicNotes.first()).toHaveAttribute('data-new', 'true');
    await expect(guest.getByTestId('content.releaseNotes.new')).toHaveCount(1);
  });

  await walk.step('corrects the note and removes it again', async () => {
    const row = page.locator('[data-testid="content.releaseAdmin.note"][data-version="6.0.0"]');
    await walk.click(row.getByTestId('content.releaseAdmin.edit'));
    await expect(page.getByTestId('content.releaseAdmin.translationsHint')).toBeVisible();
    await walk.click(page.getByTestId('content.releaseAdmin.titles.tab.de'));
    // the dialog is filled with the saved note first (webkit fills it after the tab is shown)
    await expect(page.getByTestId('content.releaseAdmin.titles.field.de')).toHaveValue('Inhalte und Statistik');
    await walk.fill(page.getByTestId('content.releaseAdmin.titles.field.de'), 'Inhalte, Team und Statistik');
    // emptying the French title and text removes the translation
    await walk.click(page.getByTestId('content.releaseAdmin.titles.tab.fr'));
    await expect(page.getByTestId('content.releaseAdmin.titles.field.fr')).toHaveValue('Contenus et statistiques');
    await walk.fill(page.getByTestId('content.releaseAdmin.titles.field.fr'), '');
    await walk.click(page.getByTestId('content.releaseAdmin.bodies.tab.fr'));
    await walk.fill(page.getByTestId('content.releaseAdmin.bodies.field.fr'), '');
    await walk.click(page.getByTestId('content.releaseAdmin.save'));
    await expect(page.getByTestId('content.releaseAdmin.dialog')).toBeHidden();
    await expect(row).toContainText('Inhalte, Team und Statistik');
    await guest.reload();
    await expect(guest.getByTestId('content.releaseNotes.note').first()).toContainText('Inhalte, Team und Statistik');
    await setLanguage(guest, 'de');

    await walk.click(row.getByTestId('content.releaseAdmin.delete'));
    await expect(page.locator('.p-confirmdialog-accept-button')).toBeVisible();
    await checkpoint(page, 'release-note-delete');
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(row).toHaveCount(0);
    await guest.goto('/release-notes');
    await expectDataState(guest, 'content.releaseNotes.state', 'success');
    await expect(guest.getByTestId('content.releaseNotes.note')).toHaveCount(5);
    await checkpoint(guest, 'release-notes');
  });

  await walk.step('publishes a new privacy policy version', async () => {
    await page.goto('/admin/content');
    await walk.click(page.getByTestId('content.admin.legal'));
    await expect(page).toHaveURL(/\/admin\/legal$/);
    await expectDataState(page, 'legal.admin.versions', 'success');
    const publish = page.getByTestId('legal.admin.publish').locator('button');
    await expect(publish).toBeDisabled();
    await walk.fill(page.getByTestId('legal.admin.version.input'), '2026-11');
    await walk.fill(page.getByTestId('legal.admin.bodies.field.de'), '## Datenschutz 2026-11\n\nKürzere Aufbewahrung der Protokolle.');
    await expect(publish).toBeEnabled();
    await checkpoint(page, 'legal-versions');
    await walk.click(publish);
    await acceptConfirm(page);
    walk.count(1);
    const published = page.locator('[data-testid="legal.admin.version"][data-version="2026-11"]');
    await expect(published).toBeVisible();
    // without "accept again" nobody is asked again
    await expect(published.getByTestId('legal.admin.reconsentChip')).toHaveCount(0);
    await guest.goto('/legal/privacy-policy');
    await expectDataState(guest, 'legal.document.state', 'success');
    await expect(guest.getByTestId('legal.document.state')).toContainText('Kürzere Aufbewahrung');
  });

  // measured: 33 clicks on desktop, 34 on the phone (Orga through the menu drawer)
  walk.finish({ steps: 6, clicks: 34, fields: 23 });
});
