import { injectApiError, regionId } from '../fixtures';
import { postCard, replyVia } from '../tests/forum/forum-helpers';
import { expect, forEachVariant, screenshotTest } from './_setup';

/** Forum screens (M5 F1–F5, G3): list, thread, hidden post, new thread, moderation log, empty/error. Baselines later (decision 69). */
const zs = regionId('zuerich-stadt');
const thread = (n: number) => `/forum/threads/00000043-0001-4000-8000-${String(n).padStart(12, '0')}`;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`forum ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest('thread list and thread with reactions @F1 @F3 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${zs}/forum`);
      await expect(page.getByTestId('forum.list.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `forum-list-${variant.name}`);
      await page.goto(thread(3));
      await expect(page.getByTestId('forum.thread.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `forum-thread-${variant.name}`);
    });

    screenshotTest('post with name mentions, one of them me (ring) @F3 @screenshot', async ({ page, apiOf, dev, visual }) => {
      const me = await dev.user('bot');
      const bieb = await dev.user('bieb');
      const threadId = thread(3).split('/').pop()!;
      const postId = await replyVia(
        apiOf(page),
        threadId,
        `Merci @[${bieb.displayName}](user:${bieb.id})! Ich (@[${me.displayName}](user:${me.id})) übernehme den Rest.`,
      );
      await page.goto(thread(3));
      await expect(page.getByTestId('forum.thread.state')).toHaveAttribute('data-state', 'success');
      const card = postCard(page, postId);
      await expect(card.getByTestId('shared.markdown.mention')).toHaveCount(2);
      await expect(card.locator('.fs-mention--me')).toHaveCount(1);
      await visual.expectElement(card, `forum-post-mentions-${variant.name}`);
    });

    screenshotTest('hidden post and moderation log @G3 @D10.3 @screenshot', async ({ page, visual }) => {
      await page.goto(thread(9));
      await expect(page.getByTestId('forum.post.hiddenNote').first()).toBeVisible();
      await visual.expectScreen(page, `forum-hidden-${variant.name}`);
      await page.goto(`/regions/${zs}/moderation-log`);
      await expect(page.locator('[data-action="THREAD.POST_HIDDEN"]').first()).toBeVisible();
      await visual.expectScreen(page, `forum-log-${variant.name}`);
    });

    screenshotTest('new thread, search and error @F2 @F5 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${zs}/forum/new`);
      await expect(page.getByTestId('forum.new.titleInput')).toBeVisible();
      await visual.expectScreen(page, `forum-new-${variant.name}`);
      await page.goto(`/regions/${zs}/forum`);
      await page.getByTestId('forum.search.input').fill('xyzzy');
      await page.getByTestId('forum.search.submit').click();
      await expect(page.getByTestId('forum.search.state')).toHaveAttribute('data-state', 'empty');
      await visual.expectScreen(page, `forum-search-empty-${variant.name}`);
      await injectApiError(page, { url: /\/forum\/threads(\?|$)/, status: 500 });
      await page.goto(`/regions/${zs}/forum`);
      await expect(page.getByTestId('forum.list.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `forum-error-${variant.name}`);
    });
  });
});
