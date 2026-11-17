import type { Page } from '@playwright/test';
import {
  CONVERSATIONS,
  bubble,
  bubbles,
  deleteMessage,
  editMessage,
  getConversation,
  expectDataState,
  listItem,
  sendMessage,
  waitForRealtime,
} from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { gateRealtime } from '../../fixtures/flows/messaging';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) chats with her store manager Nina (bieb@local) in real time (N2, A32: STOMP at
 * /ws, `/user/queue/events`, refetch after a reconnect): Nina's messages, her mention of Sarah with a link, her
 * correction and her deletion reach Sarah's open conversation without a reload; Sarah answers with the mention
 * autocomplete and Nina sees it within 2 s; Sarah's connection drops, Nina writes and corrects meanwhile, and after the
 * reconnect everything is there; on the dashboard the messages badge counts a new message from Reto (bot@local) live
 * and clears when she reads it. Rules: mention tokens and the reload on reconnect are client unit tests
 * (message-body.spec, conversation-view.spec), the reconnect backoff is `nextReconnectDelay` (bells.spec), the refused
 * anonymous handshake and per-user delivery are server tests (WebSocketHandshakeIT).
 */
test.use({ role: 'foodsaver' });

const clockTexts = (page: Page) => [page.locator('time'), page.locator('.day')];

test('foodsaver and store manager chat live, through a dropped connection @N1 @N2', async ({
  page,
  context,
  api,
  as,
  apiOf,
  dev,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const [me, bieb] = await Promise.all([dev.user('foodsaver'), dev.user('bieb')]);
  const gate = await gateRealtime(page, context);
  const composer = page.getByTestId('messaging.conversation.composer');
  const unread = async () => (await api.getJson<{ conversations: number }>('/api/conversations/unread-count')).conversations;
  const messagesBadge = page.locator('[data-testid="nav.badge.messages"]:visible');
  /** The navigation badge (header navigation, phone tab bar) shows the unread conversations; none at 0. */
  const expectBadge = async (count: number) =>
    count > 0 ? expect(messagesBadge).toHaveAttribute('data-count', String(count)) : expect(messagesBadge).toHaveCount(0);
  const nina = await as('bieb');
  const ninaApi = apiOf(nina);

  await walk.step('opens her conversation with Nina', async () => {
    await page.goto('/messages');
    await expectDataState(page, 'messaging.list.state', 'success');
    const item = listItem(page, CONVERSATIONS.foodsaverBieb);
    await expect(item.getByTestId('messaging.list.unread')).toHaveText('2');
    await walk.click(item);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await waitForRealtime(page);
    // reading clears the conversation's unread count, and the navigation badge follows
    await expect.poll(async () => (await getConversation(api, CONVERSATIONS.foodsaverBieb)).unreadCount).toBe(0);
    await expectBadge(await unread());
  });

  await walk.step('reads what Nina writes while the conversation is open', async () => {
    await nina.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
    await expectDataState(nina, 'messaging.conversation.state', 'success');
    await waitForRealtime(nina);
    const before = await bubbles(page).count();
    await nina.getByTestId('messaging.conversation.composer').fill('Bist du heute Abend dabei?');
    await nina.getByTestId('messaging.conversation.send').click();
    await expect(bubbles(page)).toHaveCount(before + 1, { timeout: 2_000 });
    await expect(bubbles(page).last()).toContainText('heute Abend');
    await expect(bubbles(page).last()).toHaveAttribute('data-own', 'false');
    if (!isPhone(page)) {
      const item = listItem(page, CONVERSATIONS.foodsaverBieb);
      await expect(item.getByTestId('messaging.list.preview')).toContainText('heute Abend');
      // the open conversation is read right away: no badge
      await expect(item.getByTestId('messaging.list.unread')).toHaveCount(0);
    }

    await sendMessage(ninaApi, CONVERSATIONS.foodsaverBieb, `Danke @[${me.displayName}](user:${me.id}) für https://foodsharing.network/info!`);
    const last = bubbles(page).last();
    const mention = last.getByTestId('messaging.conversation.mention');
    await expect(mention).toHaveText(`@${me.displayName}`, { timeout: 2_000 });
    await expect(mention).toHaveAttribute('href', `/profile/${me.id}`);
    // Sarah is the one mentioned: ring
    await expect(mention).toHaveClass(/fs-mention--me/);
    await expect(last.getByRole('link', { name: 'https://foodsharing.network/info' })).toHaveAttribute('rel', /noopener/);
    await checkpoint(page, 'live-message', { mask: clockTexts(page) });
  });

  await walk.step('answers Nina with a mention', async () => {
    await walk.fill(composer, 'Super, @');
    await expect(page.getByTestId('messaging.conversation.mentionPick')).toHaveCount(1);
    await checkpoint(page, 'mention-suggestions', { mask: clockTexts(page), fullPage: false });
    await walk.click(page.getByTestId('messaging.conversation.mentionPick'));
    // the name reaches the textarea with the next change detection; typing before that appended to the old text
    await expect(composer).toHaveValue(`Super, @${bieb.displayName} `);
    await composer.press('End');
    await composer.pressSequentially('bis bald');
    await walk.click(page.getByTestId('messaging.conversation.send'));
    const sent = bubbles(page).last().getByTestId('messaging.conversation.mention');
    await expect(sent).toHaveAttribute('href', `/profile/${bieb.id}`);
    await expect(sent).not.toHaveClass(/fs-mention--me/);
    await expect(bubbles(nina).last()).toContainText('bis bald', { timeout: 2_000 });
    await expect(bubbles(nina).last().getByTestId('messaging.conversation.mention')).toHaveText(`@${bieb.displayName}`);
  });

  await walk.step("sees Nina's correction and deletion right away", async () => {
    const message = await sendMessage(ninaApi, CONVERSATIONS.foodsaverBieb, 'Abholung um 18 Uhr');
    const live = bubble(page, message.seq);
    await expect(live).toContainText('18 Uhr', { timeout: 2_000 });
    await editMessage(ninaApi, CONVERSATIONS.foodsaverBieb, message.id, 'Abholung um 19 Uhr');
    await expect(live.getByTestId('messaging.conversation.body')).toHaveText('Abholung um 19 Uhr', { timeout: 2_000 });
    await expect(live.getByTestId('messaging.conversation.edited')).toBeVisible();
    await expect(live.getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);

    const earlier = await sendMessage(ninaApi, CONVERSATIONS.foodsaverBieb, 'Ich bringe Kisten mit.');
    await expect(bubble(page, earlier.seq)).toContainText('Kisten', { timeout: 2_000 });
    await deleteMessage(ninaApi, CONVERSATIONS.foodsaverBieb, earlier.id);
    await expect(bubble(page, earlier.seq).getByTestId('messaging.conversation.deleted')).toBeVisible({ timeout: 2_000 });
    if (!isPhone(page)) {
      await expect(listItem(page, CONVERSATIONS.foodsaverBieb).getByTestId('messaging.list.preview')).toHaveAttribute('data-deleted', 'true');
    }
    await checkpoint(page, 'live-edit-and-delete', { mask: clockTexts(page) });
  });

  await walk.step('catches up after her connection dropped', async () => {
    const question = await sendMessage(ninaApi, CONVERSATIONS.foodsaverBieb, 'Bringst du Taschen mit?');
    await expect(bubble(page, question.seq)).toContainText('Taschen', { timeout: 2_000 });
    const before = await bubbles(page).count();
    await gate.goOffline();
    await editMessage(ninaApi, CONVERSATIONS.foodsaverBieb, question.id, 'Bringst du zwei Taschen mit?');
    for (const body of ['Eins', 'Zwei', 'Drei']) {
      await sendMessage(ninaApi, CONVERSATIONS.foodsaverBieb, body);
    }
    await gate.goOnline();
    await waitForRealtime(page);
    await expect(bubbles(page)).toHaveCount(before + 3);
    await expect(bubble(page, question.seq).getByTestId('messaging.conversation.body')).toHaveText('Bringst du zwei Taschen mit?');
    const lastThree = (await bubbles(page).getByTestId('messaging.conversation.body').allTextContents()).slice(-3);
    expect(lastThree.map((text) => text.trim())).toEqual(['Eins', 'Zwei', 'Drei']);
    // she scrolls down to the messages she missed (the reload after the reconnect keeps the list where it was)
    await page.getByTestId('messaging.conversation.messages').evaluate((list) => list.scrollTo({ top: list.scrollHeight, behavior: 'instant' }));
    await checkpoint(page, 'after-reconnect', { mask: clockTexts(page) });
  });

  await walk.step('notices a new message from Reto on the dashboard', async () => {
    await walk.nav('dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await waitForRealtime(page);
    const start = await unread();
    await expectBadge(start);
    await sendMessage(apiOf(await as('bot')), CONVERSATIONS.foodsaverBot, 'Hast du am Freitag Zeit?');
    await expectBadge(start + 1);
    await walk.nav('messages');
    await expectDataState(page, 'messaging.list.state', 'success');
    await walk.click(listItem(page, CONVERSATIONS.foodsaverBot));
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await expect(bubbles(page).last()).toContainText('Freitag');
    await expectBadge(start);
  });

  walk.finish({ steps: 6, clicks: 6, fields: 1 });
});
