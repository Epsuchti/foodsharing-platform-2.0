import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';
import { acceptConfirm, settleToasts, thread } from './forum-helpers';

/** F2 threads (A36/A37a): zuerich-stadt is moderated; its ambassador bot moderates (MODERATION group has no admins). */
test.use({ seedReset: 'test', role: 'bot' });

test('F2 create thread, rename, close reopen, sticky by moderator; jumper in moderated region waits for activation and moderator activates (bell) @F2 @G3 @page', async ({ page, region, as, notifications }) => {
  const zs = region.id('zuerich-stadt');
  const jumper = await as('jumper');
  await jumper.goto(`/regions/${zs}/forum`);
  await jumper.getByTestId('forum.list.new').click();
  await expect(jumper).toHaveURL(new RegExp(`/regions/${zs}/forum/new$`));
  await expect(jumper.getByTestId('forum.new.moderated')).toBeVisible();
  await expect(jumper.getByTestId('forum.new.submit').locator('button')).toBeDisabled();
  await jumper.getByTestId('forum.new.titleInput').fill('Velo-Anhänger für Samstag gesucht');
  await jumper.getByTestId('forum.new.body').fill('Hat jemand einen **Anhänger**?');
  await expectNoSeriousViolations(jumper);
  await jumper.getByTestId('forum.new.submit').click();
  await expect(jumper).toHaveURL(/\/forum\/threads\/[0-9a-f-]{36}$/);
  const threadId = jumper.url().split('/').pop()!;
  await expect(jumper.getByTestId('forum.thread.inactive')).toBeVisible();
  await expect(jumper.getByTestId('forum.post.body').first()).toContainText('Anhänger');

  await expect.poll(async () => (await notifications.bells('bot', 'FORUM_THREAD_TO_ACTIVATE')).length).toBeGreaterThan(0);

  await page.goto(`/forum/threads/${threadId}`);
  await expectDataState(page, 'forum.thread.state', 'success');
  await page.getByTestId('forum.thread.activate').click();
  await expect(page.getByTestId('forum.thread.inactive')).toHaveCount(0);

  await page.getByTestId('forum.thread.rename').click();
  await page.getByTestId('forum.thread.renameInput').fill('Velo-Anhänger gesucht');
  await page.getByTestId('forum.thread.renameSave').click();
  await expect(page.locator('h1')).toContainText('Velo-Anhänger gesucht');

  await page.getByTestId('forum.thread.toggleStatus').click();
  await expect(page.getByTestId('forum.thread.closed')).toBeVisible();
  await jumper.reload();
  await expect(jumper.getByTestId('forum.thread.closedNote')).toBeVisible();
  await expect(jumper.getByTestId('forum.thread.replyBody')).toHaveCount(0);

  await page.getByTestId('forum.thread.toggleStatus').click();
  await expect(page.getByTestId('forum.thread.closed')).toHaveCount(0);
  await page.getByTestId('forum.thread.toggleSticky').click();
  await expect(page.getByTestId('forum.thread.sticky')).toBeVisible();
  await settleToasts(page);
  await expectNoSeriousViolations(page);

  await page.goto(`/regions/${zs}/forum`);
  await expectDataState(page, 'forum.list.state', 'success');
  await expect(page.locator(`[data-thread-id="${threadId}"]`).getByTestId('forum.list.sticky')).toBeVisible();
  await settleToasts(page);
});

test('F2 delete inactive thread by moderator @F2', async ({ page, region }) => {
  await page.goto(`/forum/threads/${thread(14)}`);
  await expect(page.getByTestId('forum.thread.inactive')).toBeVisible();
  await page.getByTestId('forum.thread.delete').click();
  await acceptConfirm(page);
  await expect(page).toHaveURL(new RegExp(`/regions/${region.id('zuerich-stadt')}/forum$`));
  await expectDataState(page, 'forum.list.state', 'success');
  await expect(page.locator(`[data-thread-id="${thread(14)}"]`)).toHaveCount(0);
});
