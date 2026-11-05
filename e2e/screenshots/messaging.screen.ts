import { CONVERSATIONS, injectApiError, seedStore, sendMessage } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Messaging screens (M5 N1/S10): conversation list, direct and group conversation, members dialog, new message
 * dialog, store team chat, empty and error. Baselines in the end batch (decision 69).
 */
const S01 = seedStore('S-01').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`messaging ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('conversation list and direct conversation @N1 @screenshot', async ({ page, visual }) => {
      await page.goto('/messages');
      await expect(page.getByTestId('messaging.list.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `messaging-list-${variant.name}`);
      await page.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `messaging-direct-${variant.name}`);
    });

    screenshotTest('mention pills: mentioned me (ring), in my own bubble @N1 @screenshot', async ({ page, as, apiOf, dev, visual }) => {
      const me = await dev.user('foodsaver');
      const bieb = await dev.user('bieb');
      await sendMessage(apiOf(await as('bieb')), CONVERSATIONS.foodsaverBieb, `Danke @[${me.displayName}](user:${me.id}) fürs Abholen!`);
      await sendMessage(apiOf(page), CONVERSATIONS.foodsaverBieb, `Gern, @[${bieb.displayName}](user:${bieb.id}) – bis Donnerstag`);
      await page.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('messaging.conversation.mention')).toHaveCount(2);
      await visual.expectScreen(page, `messaging-mentions-${variant.name}`);
    });

    screenshotTest('group conversation with members dialog @N1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/messages/${CONVERSATIONS.kochgruppe}`);
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      // seeded: seq 4 deleted by its sender, seq 6 edited
      await expect(page.locator('[data-testid="messaging.conversation.message"][data-seq="4"]')).toHaveAttribute('data-deleted', 'true');
      await expect(page.locator('[data-testid="messaging.conversation.message"][data-seq="6"]')).toHaveAttribute('data-edited', 'true');
      await visual.expectScreen(page, `messaging-group-${variant.name}`);
      await page.getByTestId('messaging.conversation.members').click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await visual.expectScreen(page, `messaging-members-${variant.name}`);
    });

    screenshotTest('new message dialog @N1 @screenshot', async ({ page, visual }) => {
      await page.goto('/messages/new');
      await page.getByTestId('messaging.new.picker.search').fill('Juli');
      await expect(page.getByTestId('messaging.new.picker.hit').first()).toBeVisible();
      await visual.expectScreen(page, `messaging-new-${variant.name}`);
    });

    screenshotTest('list error @N1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: /\/api\/conversations(\?.*)?$/, method: 'GET', status: 500 });
      await page.goto('/messages');
      await expect(page.getByTestId('messaging.list.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `messaging-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`store chat ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bieb' });

    screenshotTest('message menu, edit mode, moderator delete and deleted states @N1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/messages/${CONVERSATIONS.s01Team}`);
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      // seeded: seq 5 deleted by the store manager (moderation placeholder)
      await expect(page.locator('[data-testid="messaging.conversation.message"][data-seq="5"]')).toHaveAttribute('data-deleted', 'true');
      const own = page.locator('[data-testid="messaging.conversation.message"][data-seq="6"]');
      await own.hover();
      await own.getByTestId('messaging.conversation.messageMenu').click();
      await expect(page.getByTestId('messaging.conversation.edit')).toBeVisible();
      await visual.expectScreen(page, `messaging-message-menu-${variant.name}`);
      await page.getByTestId('messaging.conversation.edit').click();
      await expect(page.getByTestId('messaging.conversation.editBanner')).toBeVisible();
      await visual.expectScreen(page, `messaging-edit-mode-${variant.name}`);
      await page.getByTestId('messaging.conversation.editCancel').click();
      await expect(page.getByTestId('messaging.conversation.editBanner')).toHaveCount(0);

      const other = page.locator('[data-testid="messaging.conversation.message"][data-seq="2"]');
      await other.hover();
      await other.getByTestId('messaging.conversation.messageMenu').click();
      await page.getByTestId('messaging.conversation.delete').click();
      await expect(page.getByTestId('messaging.conversation.deleteDialog.message')).toBeVisible();
      await visual.expectScreen(page, `messaging-moderate-dialog-${variant.name}`);
    });

    screenshotTest('store team chat tab @S10 @screenshot', async ({ page, visual }) => {
      await page.goto(`/stores/${S01}/chat`);
      await expect(page.getByTestId('stores.chat.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `stores-chat-${variant.name}`);
    });
  });
});
