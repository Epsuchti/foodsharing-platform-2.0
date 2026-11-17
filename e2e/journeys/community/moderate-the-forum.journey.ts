import { expectDataState } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { FORUM, acceptConfirm, postCard, replyVia } from '../../fixtures/flows/forum';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Bot (bot@local), ambassador of zuerich-stadt, moderates its forum: the region is moderated and its MODERATION group
 * has no admins, so the ambassador moderates (A36 fallback). Jonas (jumper@local) starts a thread through his own
 * session; Bot is told by bell, activates it, renames it, closes and reopens it and pins it. He hides one of Sarah's
 * (foodsaver@local) posts with a reason, shows it again, reads both in the moderation log, looks into the ambassador
 * forum and deletes a thread that waits for activation. What members see of a hidden post and who may read the log are
 * unit-tested (ForumPermissionMatrixTest).
 */
test.use({ role: 'bot' });

test('moderator activates, renames, closes and pins a thread, hides a post and reads the moderation log @F1 @F2 @G3 @D10.3 @N3', async ({
  page,
  api,
  as,
  apiOf,
  notifications,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'moderator');
  const jumper = apiOf(await as('jumper'));
  const author = apiOf(await as('foodsaver'));
  let threadId = '';

  await walk.step('is told about a thread waiting for activation and activates it', async () => {
    const created = await jumper.post(`/api/regions/${FORUM.zuerich}/forum/threads`, {
      title: 'Velo-Anhänger für Samstag gesucht',
      body: 'Hat jemand einen **Anhänger**?',
    });
    expect(created.status(), await created.text()).toBe(201);
    threadId = ((await created.json()) as { id: string }).id;
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await openNewestBell(walk, api, 'FORUM_THREAD_TO_ACTIVATE');
    await expect(page).toHaveURL(new RegExp(`/forum/threads/${threadId}$`));
    await expectDataState(page, 'forum.thread.state', 'success');
    await expect(page.getByTestId('forum.thread.inactive')).toBeVisible();
    await checkpoint(page, 'thread-to-activate', { mask: [page.locator('time')] });
    await walk.click(page.getByTestId('forum.thread.activate'));
    await expect(page.getByTestId('forum.thread.inactive')).toHaveCount(0);
  });

  await walk.step('renames, closes, reopens and pins the thread', async () => {
    await walk.click(page.getByTestId('forum.thread.rename'));
    await walk.fill(page.getByTestId('forum.thread.renameInput'), 'Velo-Anhänger gesucht');
    await walk.click(page.getByTestId('forum.thread.renameSave'));
    await expect(page.locator('h1')).toContainText('Velo-Anhänger gesucht');
    await walk.click(page.getByTestId('forum.thread.toggleStatus'));
    await expect(page.getByTestId('forum.thread.closed')).toBeVisible();
    // members cannot answer in a closed thread
    const reply = await jumper.post(`/api/forum/threads/${threadId}/posts`, { body: 'Noch offen?' });
    expect(reply.status()).toBe(409);
    await walk.click(page.getByTestId('forum.thread.toggleStatus'));
    await expect(page.getByTestId('forum.thread.closed')).toHaveCount(0);
    await walk.click(page.getByTestId('forum.thread.toggleSticky'));
    await expect(page.getByTestId('forum.thread.sticky')).toBeVisible();
    await checkpoint(page, 'thread-moderated', { mask: [page.locator('time')] });
  });

  const postId = await replyVia(author, FORUM.thread(4), 'Das ist ein Beitrag zum Ausblenden.');
  await walk.step('hides a post with a reason and shows it again', async () => {
    await walk.nav('forum');
    await expect(page).toHaveURL(new RegExp(`/regions/${FORUM.zuerich}/forum$`));
    await expectDataState(page, 'forum.list.state', 'success');
    await expect(page.locator(`[data-thread-id="${threadId}"]`).getByTestId('forum.list.sticky')).toBeVisible();
    await walk.click(page.locator(`[data-testid="forum.list.thread"][data-thread-id="${FORUM.thread(4)}"]`));
    await expectDataState(page, 'forum.thread.state', 'success');
    const card = postCard(page, postId);
    // the toast of the last step has gone (axe saw its leave animation next to the dialog's close button)
    await page.mouse.move(0, 0);
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
    await walk.click(card.getByTestId('forum.post.hide'));
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('forum.post.hideConfirm').locator('button')).toBeDisabled();
    await walk.fill(dialog.getByTestId('forum.post.hideReason'), 'Bitte sachlich bleiben');
    await checkpoint(page, 'hide-dialog');
    await walk.click(dialog.getByTestId('forum.post.hideConfirm'));
    await expect(card.getByTestId('forum.post.hiddenNote')).toContainText('Bitte sachlich bleiben');
    await expect(card.getByTestId('forum.post.body')).toHaveCount(0);
    await checkpointElement(card, 'post-hidden', { mask: [card.locator('time')] });
    // the author is told
    await expect.poll(async () => (await notifications.bells('foodsaver', 'POST_HIDDEN')).length).toBeGreaterThan(0);
    await walk.click(card.getByTestId('forum.post.toggleHidden'));
    await expect(card.getByTestId('forum.post.body')).toContainText('Ausblenden');
    await walk.click(card.getByTestId('forum.post.unhide'));
    await expect(card.getByTestId('forum.post.hiddenNote')).toHaveCount(0);
    await expect(card.getByTestId('forum.post.body')).toBeVisible();
  });

  await walk.step('reads both in the moderation log', async () => {
    await walk.nav('forum');
    await expectDataState(page, 'forum.list.state', 'success');
    await walk.click(page.getByTestId('forum.list.log'));
    await expect(page).toHaveURL(new RegExp(`/regions/${FORUM.zuerich}/moderation-log$`));
    await expect(page.locator('[data-action="THREAD.POST_HIDDEN"]').first()).toBeVisible();
    await expect(page.locator('[data-action="THREAD.POST_UNHIDDEN"]').first()).toBeVisible();
    await expect(page.locator('[data-action="THREAD.THREAD_ACTIVATED"]').first()).toBeVisible();
    await checkpoint(page, 'moderation-log', { mask: [page.locator('time')] });
  });

  await walk.step('looks into the ambassador forum', async () => {
    await walk.nav('forum');
    await expectDataState(page, 'forum.list.state', 'success');
    await walk.click(page.getByTestId('forum.list.tabAmbassadors'));
    await expect(page).toHaveURL(/\/forum\/ambassadors$/);
    await expectDataState(page, 'forum.list.state', 'success');
    await expect(page.locator(`[data-thread-id="${FORUM.thread(15)}"]`)).toBeVisible();
    await checkpoint(page, 'ambassador-forum');
    await walk.click(page.locator(`[data-testid="forum.list.thread"][data-thread-id="${FORUM.thread(15)}"]`));
    await expectDataState(page, 'forum.thread.state', 'success');
    await expect(page.getByTestId('forum.thread.post').first()).toBeVisible();
  });

  await walk.step('deletes a thread that waits for activation', async () => {
    await walk.nav('forum');
    await expectDataState(page, 'forum.list.state', 'success');
    const waiting = page.locator(`[data-testid="forum.list.thread"][data-thread-id="${FORUM.thread(14)}"]`);
    await expect(waiting.getByTestId('forum.list.inactive')).toBeVisible();
    await walk.click(waiting);
    await expect(page.getByTestId('forum.thread.inactive')).toBeVisible();
    await walk.click(page.getByTestId('forum.thread.delete'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(page).toHaveURL(new RegExp(`/regions/${FORUM.zuerich}/forum$`));
    await expectDataState(page, 'forum.list.state', 'success');
    await expect(page.locator(`[data-thread-id="${FORUM.thread(14)}"]`)).toHaveCount(0);
  });

  // measured 2026-11-15: 23 clicks on desktop, 28 on the phone
  walk.finish({ steps: 6, clicks: 28, fields: 2 });
});
