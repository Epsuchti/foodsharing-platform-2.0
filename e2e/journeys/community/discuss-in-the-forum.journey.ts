import { expectDataState } from '../../fixtures';
import { checkpoint, checkpointElement, expect, settle, journey as test } from '../../fixtures/checkpoint';
import { FORUM, replyVia } from '../../fixtures/flows/forum';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local, member of zuerich-stadt, follows its forum) opens her region's forum from the
 * navigation, searches it by title and by post text, answers in the bike-trailer thread with a quote, corrects her
 * post, reacts to the first post and follows the thread by bell and mail. Nina (bieb@local) answers through her own
 * session and Sarah is told by bell and mail; Bot (bot@local, moderator) starts a thread and Sarah, who follows the
 * forum, gets a bell for it. Finally Sarah starts a thread herself: zuerich-stadt is moderated, so it waits for
 * activation. Who may read, start, moderate and see which thread is unit-tested (ForumPermissionMatrixTest), search
 * snippets too (ForumTextTest).
 */
test.use({ role: 'foodsaver' });

test('foodsaver searches the forum, replies, reacts, follows a thread and starts one @F1 @F3 @F4 @F5 @N3 @N6', async ({
  page,
  api,
  as,
  apiOf,
  dev,
  mails,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const me = await dev.user('foodsaver');
  const threads = page.getByTestId('forum.list.thread');

  await walk.step('opens the forum of her region', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('forum');
    await expect(page).toHaveURL(new RegExp(`/regions/${FORUM.zuerich}/forum$`));
    await expectDataState(page, 'forum.list.state', 'success');
    // sticky threads first, the closed one marked, someone else's thread awaiting activation hidden
    await expect(threads.nth(0).getByTestId('forum.list.sticky')).toBeVisible();
    await expect(threads.nth(1).getByTestId('forum.list.sticky')).toBeVisible();
    await expect(page.locator(`[data-thread-id="${FORUM.thread(6)}"]`).getByTestId('forum.list.closed')).toBeVisible();
    await expect(page.locator(`[data-thread-id="${FORUM.thread(14)}"]`)).toHaveCount(0);
    // the ambassador forum and the moderation log are not for her
    await expect(page.getByTestId('forum.list.tabAmbassadors')).toHaveCount(0);
    await expect(page.getByTestId('forum.list.log')).toHaveCount(0);
    await expect(page.getByTestId('forum.list.follow')).toHaveAttribute('data-following', 'true');
    await checkpoint(page, 'forum-list');
  });

  await walk.step('searches titles and post texts', async () => {
    const title = (await page.locator(`[data-thread-id="${FORUM.thread(4)}"]`).getAttribute('data-title'))!;
    const word = title.split(/\s+/).find((w) => w.length > 3)!;
    await walk.fill(page.getByTestId('forum.search.input'), word);
    await walk.click(page.getByTestId('forum.search.submit'));
    await expectDataState(page, 'forum.search.state', 'success');
    await expect(page.locator(`[data-testid="forum.search.hit"][data-thread-id="${FORUM.thread(4)}"]`)).toBeVisible();
    // a word only found in posts, with highlighted snippets
    await walk.click(page.getByTestId('forum.search.body'));
    await walk.fill(page.getByTestId('forum.search.input'), 'Milchprodukte');
    await walk.click(page.getByTestId('forum.search.submit'));
    await expectDataState(page, 'forum.search.state', 'success');
    await expect(page.getByTestId('forum.search.snippet').first().locator('mark').first()).toBeVisible();
    await checkpoint(page, 'search-in-posts');
    await walk.click(page.getByTestId('forum.search.clear'));
    await expectDataState(page, 'forum.list.state', 'success');
  });

  const posts = page.getByTestId('forum.thread.post');
  await walk.step('answers in the bike-trailer thread with a quote and corrects her post', async () => {
    await walk.click(page.locator(`[data-testid="forum.list.thread"][data-thread-id="${FORUM.thread(5)}"]`));
    await expect(page).toHaveURL(new RegExp(`/forum/threads/${FORUM.thread(5)}$`));
    await expectDataState(page, 'forum.thread.state', 'success');
    const before = await posts.count();
    await walk.click(posts.first().getByTestId('forum.post.quote'));
    const composer = page.getByTestId('forum.thread.replyBody');
    await expect(composer).toHaveValue(/^> \*\*.+\*\* /);
    await composer.press('End');
    await composer.pressSequentially('Gerne, ich helfe mit.');
    await walk.click(page.getByTestId('forum.thread.send'));
    await expect(posts).toHaveCount(before + 1);
    const mine = posts.last();
    await expect(mine.getByTestId('forum.post.body')).toContainText('Gerne, ich helfe mit.');
    await expect(mine.locator('blockquote')).toBeVisible();
    await walk.click(mine.getByTestId('forum.post.edit'));
    await walk.fill(mine.getByTestId('forum.post.editBody'), 'Gerne, ich helfe am Samstag mit.');
    await walk.click(mine.getByTestId('forum.post.save'));
    await expect(mine.getByTestId('forum.post.edited')).toBeVisible();
    await expect(mine.getByTestId('forum.post.body')).toContainText('am Samstag');
    await checkpointElement(mine, 'own-post-edited');
  });

  await walk.step('reacts to the first post and sees who reacted', async () => {
    const first = posts.first();
    await walk.click(first.getByTestId('forum.post.addReaction'));
    await walk.click(page.locator('[data-testid="forum.post.pick"][data-key="tada"]'));
    const tada = first.locator('[data-testid="forum.post.reaction"][data-key="tada"]');
    await expect(tada).toHaveAttribute('aria-pressed', 'true');
    await walk.click(first.getByTestId('forum.post.whoReacted'));
    const who = page.getByTestId('forum.post.whoList');
    await expect(who).toContainText(me.displayName);
    await checkpointElement(who, 'who-reacted');
    await page.keyboard.press('Escape');
    await expect(who).toBeHidden();
  });

  await walk.step('follows the thread by bell and mail and is told about Nina\'s answer', async () => {
    const bell = page.getByTestId('forum.thread.followBell');
    const mail = page.getByTestId('forum.thread.followMail');
    await expect(bell.locator('input')).not.toBeChecked();
    // each switch saves both settings from the thread as loaded: wait until the first one is saved and the thread
    // reloaded (a second click before it would send the old bell setting again)
    const saving = async (toggle: typeof bell) => {
      const put = page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().endsWith(`/api/forum/threads/${FORUM.thread(5)}/follow`));
      await walk.click(toggle);
      expect((await put).ok()).toBe(true);
      await settle(page);
    };
    await saving(bell);
    await expect(bell.locator('input')).toBeChecked();
    await saving(mail);
    await expect(mail.locator('input')).toBeChecked();
    await expect(bell.locator('input')).toBeChecked();
    const answer = 'Ich hätte am Samstag einen Anhänger frei.';
    // she has moved on when Nina answers (an open thread marks its own bells read)
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await replyVia(apiOf(await as('bieb')), FORUM.thread(5), answer);
    await openNewestBell(walk, api, 'FORUM_POST');
    await expectDataState(page, 'forum.thread.state', 'success');
    await expect(posts.last().getByTestId('forum.post.body')).toContainText(answer);
    const sent = await mails.waitForMail({ to: 'foodsaver@local', predicate: (m) => m.text.includes(answer) });
    expect(sent.text).toContain(answer);
    await checkpoint(page, 'thread-after-answer');
  });

  await walk.step('is told about a new thread in the forum she follows', async () => {
    const created = await apiOf(await as('bot')).post(`/api/regions/${FORUM.zuerich}/forum/threads`, {
      title: 'Helferinnen für den Weihnachtsmarkt',
      body: 'Wer hat am 12. Dezember Zeit?',
    });
    expect(created.status(), await created.text()).toBe(201);
    const threadId = ((await created.json()) as { id: string }).id;
    await openNewestBell(walk, api, 'FORUM_NEW_THREAD');
    await expect(page).toHaveURL(new RegExp(`/forum/threads/${threadId}$`));
    await expectDataState(page, 'forum.thread.state', 'success');
    await expect(page.locator('h1')).toContainText('Weihnachtsmarkt');
  });

  await walk.step('starts a thread, which waits for activation', async () => {
    await walk.nav('forum');
    await expectDataState(page, 'forum.list.state', 'success');
    await walk.click(page.getByTestId('forum.list.new'));
    await expect(page).toHaveURL(new RegExp(`/regions/${FORUM.zuerich}/forum/new$`));
    await expect(page.getByTestId('forum.new.moderated')).toBeVisible();
    await expect(page.getByTestId('forum.new.submit').locator('button')).toBeDisabled();
    await walk.fill(page.getByTestId('forum.new.titleInput'), 'Velo-Anhänger für Samstag gesucht');
    await walk.fill(page.getByTestId('forum.new.body'), 'Hat jemand einen **Anhänger**?');
    await checkpoint(page, 'new-thread');
    await walk.click(page.getByTestId('forum.new.submit'));
    await expect(page).toHaveURL(/\/forum\/threads\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('forum.thread.inactive')).toBeVisible();
    await expect(page.getByTestId('forum.post.body').first()).toContainText('Anhänger');
    await expect(page.getByTestId('forum.post.body').first().locator('strong')).toHaveText('Anhänger');
    await checkpoint(page, 'new-thread-waiting');
  });

  // measured 2026-11-15: 22 clicks on desktop, 26 on the phone
  walk.finish({ steps: 7, clicks: 26, fields: 5 });
});
