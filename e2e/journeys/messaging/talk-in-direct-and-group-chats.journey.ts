import type { Page } from '@playwright/test';
import { CONVERSATIONS, bubble, bubbles, expectDataState, listItem } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) uses her messages (N1, A33): she opens the list (two unread from Nina), starts a
 * direct conversation with Julien (guide@local) through the person picker and writes to him, plans a cooking evening
 * in a new group with Julien and Jonas (jumper@local) — renames it, adds Nina, leaves it again — and corrects and then
 * deletes a message she sent to Reto (bot@local). The rules behind it (lookup reuses the conversation of exactly these
 * members, group and locked-chat rules, the edit window, delete semantics) are unit tests (MessagingServiceTest,
 * MessageEditRulesTest, conversation-view.spec). What the other member sees live is the realtime journey's.
 */
test.use({ role: 'foodsaver' });

/** Relative dates ("vor 3 Tagen"), times of day and day separators follow the browser clock. */
const clockTexts = (page: Page) => [page.locator('time'), page.locator('.day')];

test('foodsaver writes direct and group messages, edits and deletes her own @N1', async ({ page, dev }, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const [guide, jumper, bieb] = await Promise.all([dev.user('guide'), dev.user('jumper'), dev.user('bieb')]);
  const composer = page.getByTestId('messaging.conversation.composer');

  await walk.step('opens her messages', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('messages');
    await expect(page).toHaveURL(/\/messages$/);
    await expectDataState(page, 'messaging.list.state', 'success');
    await expect(listItem(page, CONVERSATIONS.foodsaverBieb).getByTestId('messaging.list.unread')).toHaveText('2');
    await expect(listItem(page, CONVERSATIONS.kochgruppe)).toBeVisible();
    await checkpoint(page, 'conversation-list', { mask: clockTexts(page) });
  });

  let direct = '';
  await walk.step('starts a conversation with Julien', async () => {
    await walk.click(page.getByTestId('messaging.list.new'));
    await expect(page.getByRole('dialog')).toBeVisible();
    await walk.fill(page.getByTestId('messaging.new.picker.search'), guide.displayName.slice(0, 4));
    const hit = page.locator(`[data-testid="messaging.new.picker.hit"][data-user-id="${guide.id}"]`);
    await expect(hit).toBeVisible();
    await checkpoint(page, 'new-message-dialog');
    await walk.click(hit.getByTestId('messaging.new.picker.add'));
    await expect(page.getByTestId('messaging.new.recipient')).toHaveCount(1);
    await walk.click(page.getByTestId('messaging.new.start'));
    await expect(page).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
    direct = page.url().split('/').pop()!;
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText(guide.displayName);
    await expect(page.getByTestId('messaging.conversation.empty')).toBeVisible();

    await walk.fill(composer, 'Hallo Julien, hast du am Donnerstag Zeit?');
    await composer.press('Enter');
    await expect(bubbles(page).last()).toContainText('Donnerstag');
    await expect(bubbles(page).last()).toHaveAttribute('data-own', 'true');
    await expect(page.getByTestId('messaging.conversation.pending')).toHaveCount(0);
    await expect(listItem(page, direct).getByTestId('messaging.list.preview')).toContainText('Donnerstag');
    await checkpoint(page, 'direct-conversation', { mask: clockTexts(page) });
  });

  await walk.step('plans a cooking evening in a new group', async () => {
    await page.goto('/messages/new');
    await expect(page.getByRole('dialog')).toBeVisible();
    for (const user of [guide, jumper]) {
      await walk.fill(page.getByTestId('messaging.new.picker.search'), user.displayName.slice(0, 4));
      await walk.click(
        page.locator(`[data-testid="messaging.new.picker.hit"][data-user-id="${user.id}"]`).getByTestId('messaging.new.picker.add'),
      );
    }
    await expect(page.getByTestId('messaging.new.recipient')).toHaveCount(2);
    await walk.fill(page.getByTestId('messaging.new.name'), 'Velokurier Planung');
    await walk.click(page.getByTestId('messaging.new.start'));
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText('Velokurier Planung');
    const group = page.url().split('/').pop()!;

    await walk.click(page.getByTestId('messaging.conversation.rename'));
    await walk.fill(page.getByTestId('messaging.conversation.renameInput'), 'Velokurier Team');
    await walk.click(page.getByTestId('messaging.conversation.renameSave'));
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText('Velokurier Team');

    await walk.click(page.getByTestId('messaging.conversation.members'));
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('messaging.conversation.member')).toHaveCount(3);
    await walk.fill(dialog.getByTestId('messaging.conversation.addMember.search'), bieb.displayName.slice(0, 4));
    await walk.click(
      dialog
        .locator(`[data-testid="messaging.conversation.addMember.hit"][data-user-id="${bieb.id}"]`)
        .getByTestId('messaging.conversation.addMember.add'),
    );
    await expect(dialog.getByTestId('messaging.conversation.member')).toHaveCount(4);
    await checkpoint(page, 'group-members');
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);

    await walk.click(page.getByTestId('messaging.conversation.leave'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(page).toHaveURL(/\/messages$/);
    await expect(listItem(page, group)).toHaveCount(0);
    await expect(listItem(page, direct)).toBeVisible();
  });

  await walk.step('corrects a message to Reto, then deletes it', async () => {
    await walk.click(listItem(page, CONVERSATIONS.foodsaverBot));
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await walk.fill(composer, 'Treffpunkt um 18 Uhr beim Bahnhof');
    await composer.press('Enter');
    await expect(page.getByTestId('messaging.conversation.pending')).toHaveCount(0);
    await expect(bubbles(page).last()).toContainText('18 Uhr');
    const mine = bubble(page, Number(await bubbles(page).last().getAttribute('data-seq')));

    // she had started the next message: the draft is parked while she edits and comes back afterwards
    await walk.fill(composer, 'Noch nicht fertig');
    await mine.hover();
    await walk.click(mine.getByTestId('messaging.conversation.messageMenu'));
    await walk.click(page.getByTestId('messaging.conversation.edit'));
    await expect(page.getByTestId('messaging.conversation.editBanner')).toBeVisible();
    await expect(composer).toHaveValue('Treffpunkt um 18 Uhr beim Bahnhof');
    await expect(page.getByTestId('messaging.conversation.editSave').locator('button')).toBeDisabled();
    await checkpoint(page, 'edit-mode', { mask: clockTexts(page) });
    await walk.fill(composer, 'Treffpunkt um 19 Uhr beim Bahnhof');
    await composer.press('Enter');
    await expect(page.getByTestId('messaging.conversation.editBanner')).toHaveCount(0);
    await expect(mine.getByTestId('messaging.conversation.body')).toHaveText('Treffpunkt um 19 Uhr beim Bahnhof');
    await expect(mine.getByTestId('messaging.conversation.edited')).toBeVisible();
    await expect(composer).toHaveValue('Noch nicht fertig');

    await mine.hover();
    await walk.click(mine.getByTestId('messaging.conversation.messageMenu'));
    await expect(page.getByTestId('messaging.conversation.delete')).toHaveAttribute('data-moderation', 'false');
    await walk.click(page.getByTestId('messaging.conversation.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(mine).toHaveAttribute('data-deleted', 'true');
    await expect(mine.getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'false');
    await expect(mine.getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);
    await expect(mine.getByTestId('messaging.conversation.body')).toHaveCount(0);
    await checkpoint(page, 'deleted-message', { mask: clockTexts(page) });
  });

  walk.finish({ steps: 4, clicks: 19, fields: 10 });
});
