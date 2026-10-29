import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';
import { acceptConfirm, postCard, replyVia, settleToasts, thread } from './forum-helpers';

/** F3 posts and G3 hiding (A37a): zuerich-stadt allows hiding; bot moderates. */
test.use({ seedReset: 'test' });

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('F3 reply with quote, edit shows marker, react and see who reacted, delete own post @F3 @page', async ({ page, dev }) => {
    const me = await dev.user('foodsaver');
    await page.goto(`/forum/threads/${thread(4)}`);
    await expectDataState(page, 'forum.thread.state', 'success');
    const posts = page.getByTestId('forum.thread.post');
    const before = await posts.count();

    await posts.first().getByTestId('forum.post.quote').click();
    const composer = page.getByTestId('forum.thread.replyBody');
    await expect(composer).toHaveValue(/^> \*\*.+\*\* /);
    await composer.press('End');
    await composer.pressSequentially('Gerne, ich helfe mit.');
    await page.getByTestId('forum.thread.send').click();
    await expect(posts).toHaveCount(before + 1);
    const mine = posts.last();
    await expect(mine.getByTestId('forum.post.body')).toContainText('Gerne, ich helfe mit.');
    await expect(mine.locator('blockquote')).toBeVisible();

    await mine.getByTestId('forum.post.edit').click();
    await mine.getByTestId('forum.post.editBody').fill('Gerne, ich helfe am Samstag mit.');
    await mine.getByTestId('forum.post.save').click();
    await expect(mine.getByTestId('forum.post.edited')).toBeVisible();
    await expect(mine.getByTestId('forum.post.body')).toContainText('am Samstag');

    const first = posts.first();
    await first.getByTestId('forum.post.addReaction').click();
    await page.locator('[data-testid="forum.post.pick"][data-key="tada"]').click();
    const tada = first.locator('[data-testid="forum.post.reaction"][data-key="tada"]');
    await expect(tada).toHaveAttribute('aria-pressed', 'true');
    await settleToasts(page);
    await expectNoSeriousViolations(page);
    await first.getByTestId('forum.post.whoReacted').click();
    await expect(page.getByTestId('forum.post.whoList')).toContainText(me.displayName);
    await page.keyboard.press('Escape');
    await tada.click();
    await expect(tada).toHaveCount(0);

    await mine.getByTestId('forum.post.delete').click();
    await acceptConfirm(page);
    await expect(posts).toHaveCount(before);
    await settleToasts(page);
  });
});

test.describe('bot', () => {
  test.use({ role: 'bot' });

  test('G3 moderator hides a post with reason, author gets bell, unhide restores; log lists it @G3 @D10.3 @page', async ({ page, region, as, apiOf, notifications }) => {
    const author = await as('foodsaver');
    const postId = await replyVia(apiOf(author), thread(4), 'Das ist ein Beitrag zum Ausblenden.');

    await page.goto(`/forum/threads/${thread(4)}`);
    const card = postCard(page, postId);
    await card.getByTestId('forum.post.hide').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('forum.post.hideConfirm').locator('button')).toBeDisabled();
    await dialog.getByTestId('forum.post.hideReason').fill('Bitte sachlich bleiben');
    await dialog.getByTestId('forum.post.hideConfirm').click();
    await expect(card.getByTestId('forum.post.hiddenNote')).toContainText('Bitte sachlich bleiben');
    await expect(card.getByTestId('forum.post.body')).toHaveCount(0);
    await card.getByTestId('forum.post.toggleHidden').click();
    await expect(card.getByTestId('forum.post.body')).toContainText('Ausblenden');

    await expect.poll(async () => (await notifications.bells('foodsaver', 'POST_HIDDEN')).length).toBeGreaterThan(0);
    await author.goto(`/forum/threads/${thread(4)}`);
    await expect(postCard(author, postId).getByTestId('forum.post.hiddenNote')).toContainText('Bitte sachlich bleiben');

    const other = await as('jumper');
    await other.goto(`/forum/threads/${thread(4)}`);
    const otherCard = postCard(other, postId);
    await expect(otherCard.getByTestId('forum.post.hiddenNote')).toBeVisible();
    await expect(otherCard.getByTestId('forum.post.body')).toHaveCount(0);
    await expect(otherCard.getByTestId('forum.post.toggleHidden')).toHaveCount(0);

    await card.getByTestId('forum.post.unhide').click();
    await expect(card.getByTestId('forum.post.hiddenNote')).toHaveCount(0);
    await expect(card.getByTestId('forum.post.body')).toBeVisible();

    await page.goto(`/regions/${region.id('zuerich-stadt')}/moderation-log`);
    await expect(page.locator('[data-action="THREAD.POST_HIDDEN"]').first()).toBeVisible();
    await expect(page.locator('[data-action="THREAD.POST_UNHIDDEN"]').first()).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('G3 moderation log is forbidden for members without moderation rights @G3 @D10.3', async ({ as, region }) => {
    const foodsaver = await as('foodsaver');
    await foodsaver.goto(`/regions/${region.id('zuerich-stadt')}/moderation-log`);
    await expect(foodsaver.locator('[data-state="forbidden"]').first()).toBeVisible();
  });
});
