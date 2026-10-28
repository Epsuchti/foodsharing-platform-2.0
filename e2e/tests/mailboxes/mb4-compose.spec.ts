import { MAILBOXES, expect, expectNoSeriousViolations, injectApiError, mailRow, test } from '../../fixtures';

/** MB4 compose, reply, forward from zuerich@local.test (bot). Outgoing mail is captured by the dev mail transport. */
test.use({ seedReset: 'test', role: 'bot' });

const mailId = (n: number) => `00000042-0003-4000-8000-0000000000${String(n).padStart(2, '0')}`;

test('MB4 compose with attachment sends from the mailbox address, reply sets answered, forward keeps attachment @MB4 @page @N6', async ({ page, mails }) => {
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/compose`);
  await page.getByTestId('mailboxes.compose.to').fill('anna@example.ch, kein-mail');
  await expect(page.getByTestId('mailboxes.compose.toHint')).toContainText('kein-mail');
  await expect(page.getByTestId('mailboxes.compose.send').locator('button')).toBeDisabled();
  await page.getByTestId('mailboxes.compose.to').fill('anna@example.ch');
  await page.getByTestId('mailboxes.compose.subject').fill('Einladung Kochabend');
  await page.getByTestId('mailboxes.compose.body').fill('Hallo Anna\n\nwir kochen am Freitag.');
  await page.getByTestId('mailboxes.compose.files').setInputFiles({
    name: 'einkaufsliste.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('Reis, Gemüse, Brot'),
  });
  await expect(page.locator('[data-testid="mailboxes.compose.attachment"][data-name="einkaufsliste.txt"]')).toBeVisible();
  await expectNoSeriousViolations(page);
  await page.getByTestId('mailboxes.compose.send').click();
  await expect(page).toHaveURL(new RegExp(`/mailboxes/${MAILBOXES.zuerich}/sent$`));
  await expect(mailRow(page, 'Einladung Kochabend')).toBeVisible();
  const sent = await mails.waitForMail({ to: 'anna@example.ch', subjectContains: 'Einladung Kochabend' });
  expect(sent.text).toContain('Freitag');

  // reply: prefilled, quoted; the original becomes answered
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/inbox/${mailId(23)}`);
  const subject = (await page.getByTestId('mailboxes.mail.subject').textContent())!.trim();
  await page.getByTestId('mailboxes.mail.reply').click();
  await expect(page.getByTestId('mailboxes.compose.subject')).toHaveValue(`Re: ${subject}`);
  await expect(page.getByTestId('mailboxes.compose.body')).toHaveValue(/> /);
  await page.getByTestId('mailboxes.compose.body').press('Control+Home');
  await page.getByTestId('mailboxes.compose.body').pressSequentially('Danke für die Nachricht!');
  await page.getByTestId('mailboxes.compose.send').click();
  await expect(page).toHaveURL(/\/sent$/);
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/inbox`);
  await expect(mailRow(page, subject).getByTestId('mailboxes.list.answered')).toBeVisible();

  // forward keeps the PDF attachment
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/inbox/${mailId(5)}`);
  await page.getByTestId('mailboxes.mail.forward').click();
  await expect(page.getByTestId('mailboxes.compose.forwardedAttachment')).toContainText('Hygienekonzept-Fairteiler.pdf');
  await page.getByTestId('mailboxes.compose.to').fill('vorstand@example.ch');
  await page.getByTestId('mailboxes.compose.send').click();
  await expect(page).toHaveURL(/\/sent$/);
  await expect(page.getByTestId('mailboxes.list.mail').first()).toBeVisible();
  await mails.waitForMail({ to: 'vorstand@example.ch', subjectContains: 'Fwd:' });
});

test('MB4 send rate limit is reported and the draft stays @MB4', async ({ page }) => {
  await injectApiError(page, { url: /\/api\/mailboxes\/[0-9a-f-]+\/mails$/, method: 'POST', status: 429 });
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/compose`);
  await page.getByTestId('mailboxes.compose.to').fill('anna@example.ch');
  await page.getByTestId('mailboxes.compose.subject').fill('Zu viele');
  await page.getByTestId('mailboxes.compose.send').click();
  await expect(page.getByTestId('shell.toast')).toBeVisible();
  await expect(page).toHaveURL(/\/compose$/);
  await expect(page.getByTestId('mailboxes.compose.subject')).toHaveValue('Zu viele');
});
