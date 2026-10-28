import { MAILBOXES, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/** Mailbox screens (M5 MB1–MB5): list, HTML mail with attachments, bounce, compose, create, error. Baselines later (decision 69). */
const inbox = `/mailboxes/${MAILBOXES.zuerich}/inbox`;
const mail = (n: number) => `${inbox}/00000042-0003-4000-8000-0000000000${String(n).padStart(2, '0')}`;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`mailboxes ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest('inbox and mail with attachments @MB2 @MB3 @screenshot', async ({ page, visual }) => {
      await page.goto(inbox);
      await expect(page.getByTestId('mailboxes.list.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `mailboxes-inbox-${variant.name}`);
      await page.goto(mail(5));
      await expect(page.getByTestId('mailboxes.mail.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `mailboxes-mail-${variant.name}`);
    });

    screenshotTest('bounce and compose reply @MB4 @screenshot', async ({ page, visual }) => {
      await page.goto(mail(20));
      await expect(page.getByTestId('mailboxes.mail.bounce')).toBeVisible();
      await visual.expectScreen(page, `mailboxes-bounce-${variant.name}`);
      await page.goto(`/mailboxes/${MAILBOXES.zuerich}/compose?replyTo=00000042-0003-4000-8000-000000000010`);
      await expect(page.getByTestId('mailboxes.compose.subject')).toHaveValue(/^Re:/);
      await visual.expectScreen(page, `mailboxes-compose-${variant.name}`);
    });

    screenshotTest('create mailbox and list error @MB1 @screenshot', async ({ page, visual }) => {
      await page.goto('/mailboxes/new');
      await page.getByTestId('mailboxes.create.emailName').fill('Ungültig!');
      await visual.expectScreen(page, `mailboxes-create-${variant.name}`);
      await injectApiError(page, { url: /\/folders\/INBOX\/mails/, status: 500 });
      await page.goto(inbox);
      await expect(page.getByTestId('mailboxes.list.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `mailboxes-error-${variant.name}`);
    });
  });
});
