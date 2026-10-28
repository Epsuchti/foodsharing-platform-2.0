import { acceptPendingConsents, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Content screens (C1): learning overview with progress and chapter states, a chapter, a CMS page, the content admin
 * list and editor, and the learning overview error state — × light/dark × de/fr.
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
    });
  });
});
