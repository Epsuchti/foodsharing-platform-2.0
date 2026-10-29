import { expect, test } from '../../fixtures';
import { replyVia, thread } from './forum-helpers';

/** F4 follow (A37a): thread followers by bell and/or mail, forum followers get a bell for every new thread. */
test.use({ seedReset: 'test', role: 'jumper' });

test('F4 follow thread by bell and mail, follow forum gets bell for new thread @F4 @N3 @N6', async ({ page, region, as, apiOf, notifications, mails }) => {
  const zs = region.id('zuerich-stadt');
  await page.goto(`/forum/threads/${thread(4)}`);
  const bell = page.getByTestId('forum.thread.followBell');
  const mail = page.getByTestId('forum.thread.followMail');
  await expect(bell.locator('input')).not.toBeChecked();
  await bell.click();
  await expect(bell.locator('input')).toBeChecked();
  await mail.click();
  await expect(mail.locator('input')).toBeChecked();

  const bieb = await as('bieb');
  await replyVia(apiOf(bieb), thread(4), 'Neuer Beitrag für alle, die folgen.');
  await expect.poll(async () => (await notifications.bells('jumper', 'FORUM_POST')).length).toBeGreaterThan(0);
  await mails.waitForMail({ to: 'jumper@local' });

  await page.goto(`/regions/${zs}/forum`);
  const follow = page.getByTestId('forum.list.follow');
  await expect(follow).toHaveAttribute('data-following', 'false');
  await follow.click();
  await expect(follow).toHaveAttribute('data-following', 'true');

  const bot = await as('bot');
  const created = await apiOf(bot).post(`/api/regions/${zs}/forum/threads`, { title: 'Neues Thema für Folgende', body: 'Hallo zusammen' });
  expect(created.status(), await created.text()).toBe(201);
  await expect.poll(async () => (await notifications.bells('jumper', 'FORUM_NEW_THREAD')).length).toBeGreaterThan(0);
});
