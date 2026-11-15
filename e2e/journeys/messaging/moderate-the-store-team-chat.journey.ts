import type { Page } from '@playwright/test';
import { CONVERSATIONS, bubble, bubbles, expectDataState, expectStatus, seedStore } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-01 Coop Bahnhofbrücke) keeps her team chat in order (N1, S10): Julien
 * (guide@local) is accepted into the team and is in the locked team chat at once — he greets the team from the
 * store's chat tab; Nina reads it there, opens the menu of her own message (edit and delete) and of Sarah's
 * (foodsaver@local) misplaced one (only the moderation delete), deletes Sarah's with a reason of at least 10
 * characters, and Sarah sees the moderation placeholder. Accepting an application is the store-manager team journey's;
 * who joins which store chat per membership status (team, standby → jumper chat) is StoreConversationSyncTest, who
 * may moderate what is ChatModerationTest and MessageEditRulesTest, locked chats cannot be renamed or left
 * (MessagingServiceTest).
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');
const clockTexts = (page: Page) => [page.locator('time'), page.locator('.day')];

test('store manager moderates the team chat a new member joined @N1 @S10', async ({ page, as, apiOf, dev, api }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'store manager');
  const guide = await dev.user('guide');

  await test.step('Julien is accepted into the team and greets it in the store chat', async () => {
    const julien = await as('guide');
    await expectStatus(
      await apiOf(julien).post(`/api/stores/${S01.id}/applications`, { text: 'Ich wohne gleich um die Ecke.' }),
      [200, 201, 204],
      'apply',
    );
    await expectStatus(await api.post(`/api/stores/${S01.id}/applications/${guide.id}/accept`), [200, 201, 204], 'accept');
    await julien.goto(`/stores/${S01.id}/chat`);
    await expectDataState(julien, 'stores.chat.state', 'success');
    await expectDataState(julien, 'messaging.conversation.state', 'success');
    // the team chat is locked: no rename, no leave
    await expect(julien.getByTestId('messaging.conversation.header')).toBeVisible();
    await expect(julien.getByTestId('messaging.conversation.rename')).toHaveCount(0);
    await expect(julien.getByTestId('messaging.conversation.leave')).toHaveCount(0);
    await julien.getByTestId('messaging.conversation.composer').fill('Hallo Team!');
    await julien.getByTestId('messaging.conversation.send').click();
    await expect(bubbles(julien).last()).toContainText('Hallo Team!');
  });

  await walk.step("reads Julien's greeting in the store's chat tab", async () => {
    await page.goto(`/stores/${S01.id}`);
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await walk.click(page.getByTestId('stores.page.tab.chat'));
    await expectDataState(page, 'stores.chat.state', 'success');
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await expect(bubbles(page).last()).toContainText('Hallo Team!');
    await expect(page.getByTestId('messaging.conversation.rename')).toHaveCount(0);
    await expect(page.getByTestId('messaging.conversation.leave')).toHaveCount(0);
    // seeded: seq 5 was deleted by the store manager
    await expect(bubble(page, 5).getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');
    await checkpoint(page, 'store-chat-tab', { mask: clockTexts(page) });
  });

  await walk.step("removes Sarah's misplaced message with a reason", async () => {
    // her own message (seq 6): edit and a plain delete
    const own = bubble(page, 6);
    await own.hover();
    await walk.click(own.getByTestId('messaging.conversation.messageMenu'));
    await expect(page.getByTestId('messaging.conversation.edit')).toBeVisible();
    await expect(page.getByTestId('messaging.conversation.delete')).toHaveAttribute('data-moderation', 'false');
    // the popover's host element has no box of its own: the overlay panel is captured
    const menu = page.locator('.p-popover').filter({ has: page.getByTestId('messaging.conversation.edit') });
    await checkpointElement(menu, 'own-message-menu');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('messaging.conversation.edit')).toHaveCount(0);

    // Sarah's "Danke für die Info!" (seq 2): only the moderation delete
    const target = bubble(page, 2);
    await target.hover();
    await walk.click(target.getByTestId('messaging.conversation.messageMenu'));
    await expect(page.getByTestId('messaging.conversation.edit')).toHaveCount(0);
    await expect(page.getByTestId('messaging.conversation.delete')).toHaveAttribute('data-moderation', 'true');
    await walk.click(page.getByTestId('messaging.conversation.delete'));
    const reason = page.getByTestId('messaging.conversation.deleteDialog.message');
    const confirm = page.getByTestId('messaging.conversation.deleteDialog.confirm');
    await expect(reason).toBeVisible();
    await walk.fill(reason, 'zu kurz');
    await walk.click(confirm);
    await expect(reason).toHaveAttribute('aria-invalid', 'true');
    await checkpoint(page, 'moderation-dialog');
    await walk.fill(reason, 'Gehört in den Direktchat, nicht ins Team.');
    await walk.click(confirm);
    await expect(reason).toBeHidden();
    await expect(target.getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');
    await expect(target.getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);
    await checkpoint(page, 'moderated', { mask: clockTexts(page) });
  });

  await test.step('Sarah sees the moderation placeholder', async () => {
    const sarah = await as('foodsaver');
    await sarah.goto(`/messages/${CONVERSATIONS.s01Team}`);
    await expectDataState(sarah, 'messaging.conversation.state', 'success');
    await expect(bubble(sarah, 2).getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');
    await expect(bubble(sarah, 2).getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);
    await expect(bubbles(sarah).last()).toContainText('Hallo Team!');
  });

  walk.finish({ steps: 2, clicks: 6, fields: 2 });
});
