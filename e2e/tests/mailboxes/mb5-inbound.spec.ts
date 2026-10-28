import { MAILBOXES, expect, expectDataState, injectIncoming, mailRow, test, waitForRealtime } from '../../fixtures';

/** MB5 inbound mail through the dev endpoint (same processor as IMAP/webhook, A35). */
test.use({ seedReset: 'test', role: 'bot' });

test('MB5 injected incoming mail lands in the inbox with live update; regional mailbox reply @MB5 @D6.3 @page', async ({ page, api }) => {
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/inbox`);
  await expectDataState(page, 'mailboxes.list.state', 'success');
  await waitForRealtime(page);
  const badge = page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="zuerich@"]').getByTestId('mailboxes.rail.unread');
  await expect(badge).toHaveText('3');

  await injectIncoming(api, {
    to: 'zuerich@local.test',
    from: 'Marco Rossi <marco@example.ch>',
    subject: 'E2E Frage Sonntag',
    text: 'Ist der Fairteiler am Sonntag offen?',
    html: '<p>Ist der Fairteiler am <b>Sonntag</b> offen?</p><script>alert(1)</script>',
  });
  // live: no reload
  await expect(mailRow(page, 'E2E Frage Sonntag')).toBeVisible();
  await expect(badge).toHaveText('4');

  await mailRow(page, 'E2E Frage Sonntag').click();
  await expect(page.getByTestId('mailboxes.mail.from')).toContainText('marco@example.ch');
  await expect(page.frameLocator('[data-testid="mailboxes.mail.html"]').locator('b')).toHaveText('Sonntag');
  await page.getByTestId('mailboxes.mail.reply').click();
  await expect(page.getByTestId('mailboxes.compose.to')).toHaveValue('marco@example.ch');
  await page.getByTestId('mailboxes.compose.send').click();
  await expect(page).toHaveURL(/\/sent$/);
  await expect(mailRow(page, 'Re: E2E Frage Sonntag')).toBeVisible();
});

test('MB5 unknown recipient is dropped @MB5', async ({ page, api }) => {
  await injectIncoming(api, { to: 'gibtesnicht@local.test', subject: 'Ins Leere', text: 'Hallo?' });
  await page.goto(`/mailboxes/${MAILBOXES.zuerich}/inbox`);
  await expectDataState(page, 'mailboxes.list.state', 'success');
  await expect(mailRow(page, 'Ins Leere')).toHaveCount(0);
});
