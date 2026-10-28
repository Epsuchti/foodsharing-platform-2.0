import { MAILBOXES, expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, test } from '../../fixtures';

/**
 * MB2 folders and MB3 reading. Seed (zuerich@local.test, read by bot): INBOX 01–25 (23–25 unread, 05 PDF and 12 PNG
 * attachments, 10 answered, 15 HTML newsletter, 20 bounce), SENT 26–31, TRASH 32/33.
 */
test.use({ seedReset: 'test', role: 'bot' });

const mailId = (n: number) => `00000042-0003-4000-8000-0000000000${String(n).padStart(2, '0')}`;
const inbox = `/mailboxes/${MAILBOXES.zuerich}/inbox`;
const zuerich = (page: import('@playwright/test').Page) =>
  page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="zuerich@"]');

test('MB2 inbox, sent, trash, read/unread, answered flag and nav unread count @MB2 @page', async ({ page }) => {
  await page.goto('/mailboxes');
  await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
  await page.goto(inbox);
  await expectDataState(page, 'mailboxes.list.state', 'success');
  const badge = zuerich(page).getByTestId('mailboxes.rail.unread');
  await expect(badge).toHaveText('3');
  await expect(page.getByTestId('mailboxes.list.answered').first()).toBeVisible();
  await expectNoSeriousViolations(page);

  const unread = page.locator('[data-testid="mailboxes.list.mail"][data-read="false"]').first();
  const subject = await unread.getAttribute('data-subject');
  await unread.click();
  await expectDataState(page, 'mailboxes.mail.state', 'success');
  await expect(badge).toHaveText('2');
  await page.getByTestId('mailboxes.mail.markUnread').click();
  await expect(badge).toHaveText('3');

  await page.locator(`[data-testid="mailboxes.list.mail"][data-subject="${subject}"]`).click();
  await page.getByTestId('mailboxes.mail.trash').click();
  await expect(page.locator(`[data-testid="mailboxes.list.mail"][data-subject="${subject}"]`)).toHaveCount(0);
  await zuerich(page).getByTestId('mailboxes.rail.folder.TRASH').click();
  const trashed = page.locator(`[data-testid="mailboxes.list.mail"][data-subject="${subject}"]`);
  await expect(trashed).toBeVisible();
  await trashed.click();
  await page.getByTestId('mailboxes.mail.restore').click();
  await expect(trashed).toHaveCount(0);

  await zuerich(page).getByTestId('mailboxes.rail.folder.SENT').click();
  await expect(page.getByTestId('mailboxes.list.mail')).toHaveCount(6);
  await expect(page.getByTestId('mailboxes.list.bounce')).toHaveCount(0);

  await page.goto(inbox);
  await page.getByTestId('mailboxes.list.search').fill('Hygienekonzept');
  await page.getByTestId('mailboxes.list.search').press('Enter');
  await expect.poll(() => page.getByTestId('mailboxes.list.mail').count()).toBeLessThan(25);
  await expect(page.getByTestId('mailboxes.list.mail').first()).toBeVisible();
});

test('MB3 HTML mail is sanitised (script and remote image removed), attachments download @MB3 @page', async ({ page }) => {
  await page.goto(`${inbox}/${mailId(15)}`);
  await expectDataState(page, 'mailboxes.mail.state', 'success');
  const frame = page.frameLocator('[data-testid="mailboxes.mail.html"]');
  await expect(frame.locator('body')).not.toBeEmpty();
  await expect(frame.locator('script')).toHaveCount(0);
  await expect(frame.locator('img[src^="http"]')).toHaveCount(0);
  await expect(page.getByTestId('mailboxes.mail.html')).toHaveAttribute('sandbox', /allow-popups/);
  await page.getByTestId('mailboxes.mail.toggleText').click();
  await expect(page.getByTestId('mailboxes.mail.text')).toBeVisible();
  await expectNoSeriousViolations(page);

  await page.goto(`${inbox}/${mailId(5)}`);
  const attachment = page.locator('[data-testid="mailboxes.mail.attachment"][data-name="Hygienekonzept-Fairteiler.pdf"]');
  const [download] = await Promise.all([page.waitForEvent('download'), attachment.click()]);
  expect(download.suggestedFilename()).toBe('Hygienekonzept-Fairteiler.pdf');

  await page.goto(`${inbox}/${mailId(20)}`);
  await expect(page.getByTestId('mailboxes.mail.bounce')).toBeVisible();
});

test('MB2 error states: list 500 with retry, mail 404 @MB2', async ({ page }) => {
  const injection = await injectApiError(page, { url: /\/folders\/INBOX\/mails/, status: 500 });
  await page.goto(inbox);
  const { retry } = await expectErrorState(page, 'mailboxes.list.state');
  await injection.remove();
  await retry.click();
  await expectDataState(page, 'mailboxes.list.state', 'success');
  await page.goto(`${inbox}/00000042-0003-4000-8000-00000000dead`);
  await expect(page.getByTestId('mailboxes.mail.state')).toHaveAttribute('data-state', /notFound|forbidden/);
});
