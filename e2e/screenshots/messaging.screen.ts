import { CONVERSATIONS, injectApiError, seedStore } from '../fixtures';
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

    screenshotTest('group conversation with members dialog @N1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/messages/${CONVERSATIONS.kochgruppe}`);
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
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

    screenshotTest('store team chat tab @S10 @screenshot', async ({ page, visual }) => {
      await page.goto(`/stores/${S01}/chat`);
      await expect(page.getByTestId('stores.chat.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `stores-chat-${variant.name}`);
    });
  });
});
