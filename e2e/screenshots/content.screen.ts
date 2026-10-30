import { acceptPendingConsents, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Content screens (C1): learning overview with progress and chapter states, a chapter, a CMS page, the content admin
 * list and editor, the learning overview error state, the about overview, FAQ, team, statistics (+ error), release
 * notes (+ ORGA editor) and the petition page with its banner (Q10) — × light/dark × de/fr.
 */
screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`content ${variant.name}`, () => {
    screenshotTest.describe('newbie', () => {
      screenshotTest.use({ variant, role: 'newbie' });

      screenshotTest('learning overview and chapter @C1 @screenshot', async ({ page, apiOf, visual }) => {
        const api = apiOf(page);
        await acceptPendingConsents(api);
        await api.postJson('/api/onboarding/learning/rules/read', {});
        await page.goto('/learn');
        await expect(page.getByTestId('content.learn.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('content.learn.progress')).toHaveAttribute('data-read', '1');
        await visual.expectScreen(page, `learn-${variant.name}`);

        await page.getByTestId('content.learn.continue').click();
        await expect(page.getByTestId('content.chapter.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `learn-chapter-${variant.name}`);
      });
    });

    screenshotTest.describe('guest', () => {
      screenshotTest.use({ variant, role: undefined });

      screenshotTest('content page @C1 @screenshot', async ({ page, visual }) => {
        await page.goto('/pages/vision');
        await expect(page.getByTestId('content.page.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `content-page-${variant.name}`);
      });

      screenshotTest('about overview and FAQ @C1 @screenshot', async ({ page, visual }) => {
        await page.goto('/about');
        await expect(page.getByTestId('content.about.link').first()).toBeVisible();
        await visual.expectScreen(page, `about-${variant.name}`);

        await page.goto('/faq');
        await expect(page.getByTestId('content.faq.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('content.faq.question').first().click();
        await expect(page.getByTestId('content.faq.answer').first()).toBeVisible();
        await visual.expectScreen(page, `faq-${variant.name}`);
      });

      screenshotTest('team page @C16 @screenshot', async ({ page, visual }) => {
        await page.goto('/team');
        await expect(page.getByTestId('content.team.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `team-${variant.name}`);
      });

      screenshotTest('statistics page @C3 @screenshot', async ({ page, visual }) => {
        // the screenshot projects run with reduced motion: the counters show their final values at once
        await page.goto('/statistics');
        await expect(page.getByTestId('content.statistics.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('content.statistics.tile')).toHaveCount(7);
        await visual.expectScreen(page, `statistics-${variant.name}`);
      });

      screenshotTest('release notes and petition @C4 @C8 @screenshot', async ({ page, visual }) => {
        await page.goto('/release-notes');
        await expect(page.getByTestId('content.releaseNotes.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('content.releaseNotes.new')).toHaveCount(1);
        await visual.expectScreen(page, `release-notes-${variant.name}`);

        await page.goto('/petition');
        await expect(page.getByTestId('content.petition.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('content.petition.banner')).toBeVisible();
        await visual.expectScreen(page, `petition-${variant.name}`);
      });

      screenshotTest('statistics error @C3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/statistics', status: 500 });
        await page.goto('/statistics');
        await expect(page.getByTestId('content.statistics.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `statistics-error-${variant.name}`);
      });

      screenshotTest('learning overview error @C1 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: /\/api\/public\/content\/learning(\?|$)/, status: 500 });
        await page.goto('/learn');
        await expect(page.getByTestId('content.learn.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `learn-error-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('content admin list and editor @C1 @screenshot', async ({ page, visual }) => {
        await page.goto('/admin/content');
        await expect(page.getByTestId('content.admin.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `content-admin-${variant.name}`);

        await page.goto('/admin/content/rules');
        await expect(page.getByTestId('content.editor.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `content-editor-${variant.name}`);
      });

      screenshotTest('release notes editor @C4 @screenshot', async ({ page, visual }) => {
        await page.goto('/admin/release-notes');
        await expect(page.getByTestId('content.releaseAdmin.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `release-notes-admin-${variant.name}`);

        await page.getByTestId('content.releaseAdmin.edit').first().click();
        await expect(page.getByTestId('content.releaseAdmin.dialog')).toBeVisible();
        await visual.expectScreen(page, `release-notes-admin-dialog-${variant.name}`);
      });
    });
  });
});
