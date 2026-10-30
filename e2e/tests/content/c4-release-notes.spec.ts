import {
  expect,
  expectDataState,
  expectErrorState,
  expectNoMissingKeys,
  injectApiError,
  setLanguage,
  test,
} from '../../fixtures';
import type { Page } from '@playwright/test';

/**
 * C4 (plan-m7 Q10): release notes newest first in the UI language, the header "what's new" dot until the newest note
 * was seen (`localStorage['fs.releaseNotesSeen']`), and the ORGA editor `/admin/release-notes`.
 */

test.use({ seedReset: 'test' });

/** The header link is hidden on phones (decision: release notes stay reachable from the footer and /about). */
const hasHeaderLink = (page: Page) => (page.viewportSize()?.width ?? 0) >= 768;

async function expectDot(page: Page, shown: boolean): Promise<void> {
  if (!hasHeaderLink(page)) return;
  const dot = page.getByTestId('shell.whatsNew').getByTestId('content.releaseNotes.dot');
  if (shown) await expect(dot).toBeVisible();
  else await expect(dot).toHaveCount(0);
}

test("C4 release notes list and what's new indicator @C4 @page", async ({ as, page }) => {
  // a first-time visitor: the dot shows, the page marks only the newest note new
  await setLanguage(page, 'de');
  await page.goto('/about');
  await expectDot(page, true);
  if (hasHeaderLink(page)) await page.getByTestId('shell.whatsNew').click();
  else await page.goto('/release-notes');
  await expect(page).toHaveURL(/\/release-notes$/);
  await expectDataState(page, 'content.releaseNotes.state', 'success');
  const notes = page.getByTestId('content.releaseNotes.note');
  await expect(notes).toHaveCount(5);
  await expect(notes.first()).toHaveAttribute('data-version', '5.0.0');
  await expect(notes.first()).toContainText('Gemeinsam reden und entscheiden');
  await expect(page.getByTestId('content.releaseNotes.new')).toHaveCount(1);
  await expectDot(page, false);
  expect(await page.evaluate(() => localStorage.getItem('fs.releaseNotesSeen'))).toBe('5.0.0');

  // seen: no dot and nothing new after a reload
  await page.reload();
  await expectDataState(page, 'content.releaseNotes.state', 'success');
  await expect(page.getByTestId('content.releaseNotes.new')).toHaveCount(0);
  await expectDot(page, false);

  // ORGA adds a note with a French translation
  const orga = await as('orga');
  await orga.goto('/admin/release-notes');
  await expectDataState(orga, 'content.releaseAdmin.state', 'success');
  await expect(orga.getByTestId('content.releaseAdmin.note')).toHaveCount(5);
  await orga.getByTestId('content.releaseAdmin.new').click();
  await orga.getByTestId('content.releaseAdmin.version').fill('6.0.0');
  await orga.getByTestId('content.releaseAdmin.titles.field.de').fill('Inhalte und Statistik');
  await orga.getByTestId('content.releaseAdmin.bodies.field.de').fill('Neue **Teamseite**, Statistik und FAQ.');
  await orga.getByTestId('content.releaseAdmin.titles.tab.fr').click();
  await orga.getByTestId('content.releaseAdmin.titles.field.fr').fill('Contenus et statistiques');
  // a title without text cannot be saved
  await expect(orga.getByTestId('content.releaseAdmin.incomplete')).toContainText('FR');
  await expect(orga.getByTestId('content.releaseAdmin.save').locator('button')).toBeDisabled();
  await orga.getByTestId('content.releaseAdmin.bodies.tab.fr').click();
  await orga.getByTestId('content.releaseAdmin.bodies.field.fr').fill('Nouvelle **page de l’équipe**, statistiques et FAQ.');
  await orga.getByTestId('content.releaseAdmin.save').click();
  await expect(orga.getByTestId('content.releaseAdmin.dialog')).toBeHidden();
  await expect(orga.getByTestId('content.releaseAdmin.note')).toHaveCount(6);
  await expect(orga.getByTestId('content.releaseAdmin.note').first()).toHaveAttribute('data-version', '6.0.0');

  // the visitor sees the dot again; in French the new note is translated and the only new one
  await setLanguage(page, 'fr');
  await page.goto('/about');
  await expectDot(page, true);
  await page.goto('/release-notes');
  await expectDataState(page, 'content.releaseNotes.state', 'success');
  await expect(notes.first()).toHaveAttribute('data-version', '6.0.0');
  await expect(notes.first()).toContainText('Contenus et statistiques');
  await expect(notes.first().locator('strong')).toHaveText('page de l’équipe');
  await expect(page.getByTestId('content.releaseNotes.new')).toHaveCount(1);
  await expect(notes.first()).toHaveAttribute('data-new', 'true');
  await expectNoMissingKeys(page);

  // ORGA edits the German title (the French translation is prefilled and stays) and deletes the note again
  const row = orga.locator('[data-testid="content.releaseAdmin.note"][data-version="6.0.0"]');
  await row.getByTestId('content.releaseAdmin.edit').click();
  await expect(orga.getByTestId('content.releaseAdmin.translationsHint')).toBeVisible();
  await orga.getByTestId('content.releaseAdmin.titles.tab.fr').click();
  await expect(orga.getByTestId('content.releaseAdmin.titles.field.fr')).toHaveValue('Contenus et statistiques');
  await orga.getByTestId('content.releaseAdmin.titles.tab.de').click();
  await orga.getByTestId('content.releaseAdmin.titles.field.de').fill('Inhalte, Team und Statistik');
  await orga.getByTestId('content.releaseAdmin.save').click();
  await expect(row).toContainText('Inhalte, Team und Statistik');
  await page.reload();
  await expect(notes.first()).toContainText('Contenus et statistiques');

  // emptying the French title and text removes the translation: French readers get the German text
  await row.getByTestId('content.releaseAdmin.edit').click();
  await orga.getByTestId('content.releaseAdmin.titles.tab.fr').click();
  await orga.getByTestId('content.releaseAdmin.titles.field.fr').fill('');
  await orga.getByTestId('content.releaseAdmin.bodies.tab.fr').click();
  await orga.getByTestId('content.releaseAdmin.bodies.field.fr').fill('');
  await orga.getByTestId('content.releaseAdmin.save').click();
  await expect(orga.getByTestId('content.releaseAdmin.dialog')).toBeHidden();
  await page.reload();
  await expect(notes.first()).toContainText('Inhalte, Team und Statistik');

  await row.getByTestId('content.releaseAdmin.delete').click();
  await orga.locator('.p-confirmdialog-accept-button').click();
  await expect(row).toHaveCount(0);
  await page.reload();
  await expect(notes).toHaveCount(5);
});

test('C4 release notes: error with retry; the editor is ORGA only @C4', async ({ as, page }) => {
  const failure = await injectApiError(page, { url: /\/api\/public\/release-notes(\?|$)/, status: 500 });
  await page.goto('/release-notes');
  const { retry } = await expectErrorState(page, 'content.releaseNotes.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.releaseNotes.state', 'success');

  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/admin/release-notes');
  await expect(foodsaver).toHaveURL(/\/forbidden$/);
});
