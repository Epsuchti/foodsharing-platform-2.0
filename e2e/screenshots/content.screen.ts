import { acceptPendingConsents, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Content screens (C1): learning overview with progress and chapter states, a chapter, a CMS page, the content admin
 * list and editor, the learning overview error state, the about overview, FAQ, team, statistics (+ error), release
 * notes (+ ORGA editor) and the petition page with its banner (Q10); blog list/post (+ error), management and
 * editor, donate and support (+ rate limit) (Q11) — light/de (decision 109).
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

      screenshotTest('blog list and post @C2 @screenshot', async ({ page, visual }) => {
        await page.goto('/blog');
        await expect(page.getByTestId('blog.list.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('blog.list.post')).toHaveCount(6);
        await visual.expectScreen(page, `blog-list-${variant.name}`);

        await page.goto('/blog/00000012-0006-4000-8000-000000000001');
        await expect(page.getByTestId('blog.post.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `blog-post-${variant.name}`);
      });

      screenshotTest('donate and support @C7 @C9 @screenshot', async ({ page, visual }) => {
        await page.goto('/donate');
        await expect(page.getByTestId('content.donate.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('content.donate.campaign')).toHaveCount(2);
        await visual.expectScreen(page, `donate-${variant.name}`);

        // the user agent differs between machines: the field is masked
        await page.goto('/support?from=%2Fdashboard');
        await expect(page.getByTestId('content.support.form')).toBeVisible();
        await visual.expectScreen(page, `support-${variant.name}`, {
          mask: [page.getByTestId('content.support.userAgent')],
        });

        await injectApiError(page, { url: '/api/public/support/tickets', method: 'POST', status: 429, code: 'SUPPORT_RATE_LIMITED' });
        await page.getByTestId('content.support.firstName').fill('Mia');
        await page.getByTestId('content.support.email').fill('mia@example.org');
        await page.getByTestId('content.support.subject').fill('Frage zur Registrierung');
        await page.getByTestId('content.support.body').fill('Wie werde ich Foodsaver?');
        await page.getByTestId('content.support.send').click();
        await expect(page.getByTestId('content.support.rateLimited')).toBeVisible();
        await visual.expectScreen(page, `support-rate-limited-${variant.name}`, {
          mask: [page.getByTestId('content.support.userAgent')],
        });
      });

      screenshotTest('blog list error @C2 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: /\/api\/public\/blog(\?|$)/, status: 500 });
        await page.goto('/blog');
        await expect(page.getByTestId('blog.list.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `blog-list-error-${variant.name}`);
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

      screenshotTest('blog management and editor @C2 @screenshot', async ({ page, visual }) => {
        await page.goto('/blog/manage');
        await expect(page.getByTestId('blog.manage.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `blog-manage-${variant.name}`);

        // seed post 07 waits for the check: ORGA sees publish
        await page.goto('/blog/00000012-0006-4000-8000-000000000007/edit');
        await expect(page.getByTestId('blog.editor.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('blog.editor.publish')).toBeVisible();
        await visual.expectScreen(page, `blog-editor-${variant.name}`);
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
