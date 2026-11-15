import type { Page } from '@playwright/test';
import { MAILBOXES, expectDataState, injectIncoming, mailRow, waitForRealtime } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Ambassador Reto (bot@local) works through the Zürich region mailbox (zuerich@local.test; seed: INBOX 01–25 with
 * 23–25 unread, 05 PDF and 12 PNG attachments, 10 answered, 15 HTML newsletter, 20 bounce; SENT 26–31; TRASH 32/33):
 * the navigation counts his unread mails, he reads one and keeps it unread, throws one away and restores it, finds the
 * hygiene concept and downloads its PDF, reads the HTML newsletter (scripts and remote images removed) and a bounce,
 * answers a question that arrives while he looks at the inbox (MB5, through the dev endpoint, the same processor as
 * IMAP and the webhook), writes an invitation with bold text and a shopping list attached, and forwards the hygiene
 * concept with its PDF. Outgoing mail is captured by the dev mail transport. The rules (folder moves, recipient
 * validation and limits, the send rate limit, reply headers, sanitising, unknown recipients dropped, bounce flags,
 * the unread total) are unit tests: MailServiceTest, MailHtmlSanitizerTest, IncomingMailProcessorTest,
 * mailboxes.resource.spec.
 */
test.use({ role: 'bot' });

const inbox = `/mailboxes/${MAILBOXES.zuerich}/inbox`;
const mailId = (n: number) => `00000042-0003-4000-8000-0000000000${String(n).padStart(2, '0')}`;
const zuerich = (page: Page) => page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="zuerich@"]');
const clockTexts = (page: Page) => [page.locator('time')];

test('ambassador reads, sorts, answers, writes and forwards mail of the region mailbox @MB2 @MB3 @MB4 @MB5 @N6', async ({
  page,
  api,
  mails,
}, testInfo) => {
  test.setTimeout(180_000);
  const walk = new Walk(page, testInfo, 'ambassador');
  const badge = zuerich(page).getByTestId('mailboxes.rail.unread');
  const body = page.getByTestId('mailboxes.compose.body').locator('.ql-editor');

  await walk.step('opens the Zürich inbox from the navigation', async () => {
    const total = (await api.getJson<{ unreadCount: number }[]>('/api/mailboxes')).reduce((n, m) => n + m.unreadCount, 0);
    expect(total).toBeGreaterThan(0);
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    // phones list the mailboxes in the "Menu" drawer
    if (isPhone(page)) await walk.click(page.getByTestId('nav.tab.menu'));
    await expect(page.locator('[data-testid="nav.badge.mailboxes"]:visible')).toHaveAttribute('data-count', String(total));
    if (isPhone(page)) {
      await walk.click(page.getByTestId('nav.drawer.mailboxes'));
    } else {
      await walk.nav('mailboxes');
    }
    await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
    if (!page.url().endsWith(inbox)) await walk.click(zuerich(page).getByTestId('mailboxes.rail.folder.INBOX'));
    await expect(page).toHaveURL(new RegExp(`${inbox}$`));
    await expectDataState(page, 'mailboxes.list.state', 'success');
    await expect(badge).toHaveText('3');
    await expect(page.getByTestId('mailboxes.list.answered').first()).toBeVisible();
    await waitForRealtime(page);
    await checkpoint(page, 'inbox', { mask: clockTexts(page) });
  });

  await walk.step('reads a new mail, keeps it unread, throws one away and gets it back', async () => {
    const unread = page.locator('[data-testid="mailboxes.list.mail"][data-read="false"]').first();
    const subject = (await unread.getAttribute('data-subject'))!;
    await walk.click(unread);
    await expectDataState(page, 'mailboxes.mail.state', 'success');
    await expect(badge).toHaveText('2');
    await walk.click(page.getByTestId('mailboxes.mail.markUnread'));
    await expect(badge).toHaveText('3');

    await walk.click(mailRow(page, subject));
    await walk.click(page.getByTestId('mailboxes.mail.trash'));
    await expect(mailRow(page, subject)).toHaveCount(0);
    await walk.click(zuerich(page).getByTestId('mailboxes.rail.folder.TRASH'));
    await expect(mailRow(page, subject)).toBeVisible();
    await checkpoint(page, 'trash', { mask: clockTexts(page) });
    await walk.click(mailRow(page, subject));
    await walk.click(page.getByTestId('mailboxes.mail.restore'));
    await expect(mailRow(page, subject)).toHaveCount(0);
  });

  await walk.step('finds the hygiene concept and downloads its PDF', async () => {
    await walk.click(zuerich(page).getByTestId('mailboxes.rail.folder.INBOX'));
    await expectDataState(page, 'mailboxes.list.state', 'success');
    await walk.fill(page.getByTestId('mailboxes.list.search'), 'Hygienekonzept');
    await page.getByTestId('mailboxes.list.search').press('Enter');
    await expect.poll(() => page.getByTestId('mailboxes.list.mail').count()).toBeLessThan(25);
    await walk.click(page.getByTestId('mailboxes.list.mail').filter({ hasText: 'Hygienekonzept' }).first());
    await expectDataState(page, 'mailboxes.mail.state', 'success');
    const attachment = page.locator('[data-testid="mailboxes.mail.attachment"][data-name="Hygienekonzept-Fairteiler.pdf"]');
    await expect(attachment).toBeVisible();
    await checkpoint(page, 'mail-with-attachment', { mask: clockTexts(page) });
    const [download] = await Promise.all([page.waitForEvent('download'), walk.click(attachment)]);
    expect(download.suggestedFilename()).toBe('Hygienekonzept-Fairteiler.pdf');
  });

  await walk.step('reads the HTML newsletter safely, and a bounce', async () => {
    await page.goto(`${inbox}/${mailId(15)}`);
    await expectDataState(page, 'mailboxes.mail.state', 'success');
    const frame = page.frameLocator('[data-testid="mailboxes.mail.html"]');
    await expect(frame.locator('body')).not.toBeEmpty();
    await expect(frame.locator('script')).toHaveCount(0);
    await expect(frame.locator('img[src^="http"]')).toHaveCount(0);
    await expect(page.getByTestId('mailboxes.mail.html')).toHaveAttribute('sandbox', /allow-popups/);
    await checkpoint(page, 'html-mail', {
      mask: clockTexts(page),
      // the mail's own HTML runs in a sandbox without scripts: axe cannot be injected there (its frame step hangs);
      // the page around it is scanned
      a11y: { exclude: ['[data-testid="mailboxes.mail.html"]'] },
    });
    await walk.click(page.getByTestId('mailboxes.mail.toggleText'));
    await expect(page.getByTestId('mailboxes.mail.text')).toBeVisible();

    await page.goto(`${inbox}/${mailId(20)}`);
    await expectDataState(page, 'mailboxes.mail.state', 'success');
    await expect(page.getByTestId('mailboxes.mail.bounce')).toBeVisible();
    await checkpoint(page, 'bounce-mail', { mask: clockTexts(page) });
  });

  await walk.step('answers a question that just came in', async () => {
    await page.goto(inbox);
    await expectDataState(page, 'mailboxes.list.state', 'success');
    await waitForRealtime(page);
    const before = Number(await badge.textContent());
    await injectIncoming(api, {
      to: 'zuerich@local.test',
      from: 'Marco Rossi <marco@example.ch>',
      subject: 'Fairteiler am Sonntag offen?',
      text: 'Ist der Fairteiler am Sonntag offen?',
      html: '<p>Ist der Fairteiler am <b>Sonntag</b> offen?</p><script>alert(1)</script>',
    });
    // live: no reload
    const question = mailRow(page, 'Fairteiler am Sonntag offen?');
    await expect(question).toBeVisible();
    await expect(badge).toHaveText(String(before + 1));
    await walk.click(question);
    await expect(page.getByTestId('mailboxes.mail.from')).toContainText('marco@example.ch');
    await expect(page.frameLocator('[data-testid="mailboxes.mail.html"]').locator('b')).toHaveText('Sonntag');
    await walk.click(page.getByTestId('mailboxes.mail.reply'));
    await expect(page.getByTestId('mailboxes.compose.to')).toHaveValue('marco@example.ch');
    await expect(page.getByTestId('mailboxes.compose.subject')).toHaveValue('Re: Fairteiler am Sonntag offen?');
    await expect(body.locator('blockquote').first()).toBeVisible();
    await walk.click(body.locator('p').first());
    await page.keyboard.type('Ja, von 10 bis 18 Uhr.');
    await expect(body.locator('p').first()).toHaveText('Ja, von 10 bis 18 Uhr.');
    await checkpoint(page, 'reply');
    await walk.click(page.getByTestId('mailboxes.compose.send'));
    await expect(page).toHaveURL(new RegExp(`/mailboxes/${MAILBOXES.zuerich}/sent$`));
    await expect(mailRow(page, 'Re: Fairteiler am Sonntag offen?')).toBeVisible();
    await mails.waitForMail({ to: 'marco@example.ch', subjectContains: 'Re: Fairteiler am Sonntag offen?' });
    await walk.click(zuerich(page).getByTestId('mailboxes.rail.folder.INBOX'));
    await expect(question.getByTestId('mailboxes.list.answered')).toBeVisible();
  });

  await walk.step('invites Anna to a cooking evening with the shopping list', async () => {
    await walk.click(page.getByTestId('mailboxes.compose'));
    await expect(page).toHaveURL(/\/compose$/);
    await walk.fill(page.getByTestId('mailboxes.compose.to'), 'anna@example.ch, kein-mail');
    await expect(page.getByTestId('mailboxes.compose.toHint')).toContainText('kein-mail');
    await expect(page.getByTestId('mailboxes.compose.send').locator('button')).toBeDisabled();
    await walk.fill(page.getByTestId('mailboxes.compose.to'), 'anna@example.ch');
    await walk.fill(page.getByTestId('mailboxes.compose.subject'), 'Einladung Kochabend');
    // the rich-text editor (p-editor, deferred until idle) replaces the loading placeholder
    await walk.click(body);
    await page.keyboard.type('Hallo Anna');
    await page.keyboard.press('Enter');
    await walk.click(page.getByTestId('mailboxes.compose.body').locator('button.ql-bold'));
    await page.keyboard.type('wir kochen am Freitag.');
    await expect(body.locator('strong')).toHaveText('wir kochen am Freitag.');
    await page.getByTestId('mailboxes.compose.files').setInputFiles({
      name: 'einkaufsliste.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('Reis, Gemüse, Brot'),
    });
    await expect(page.locator('[data-testid="mailboxes.compose.attachment"][data-name="einkaufsliste.txt"]')).toBeVisible();
    await checkpoint(page, 'compose');
    await walk.click(page.getByTestId('mailboxes.compose.send'));
    await expect(page).toHaveURL(new RegExp(`/mailboxes/${MAILBOXES.zuerich}/sent$`));
    await expect(mailRow(page, 'Einladung Kochabend')).toBeVisible();
    const sent = await mails.waitForMail({ to: 'anna@example.ch', subjectContains: 'Einladung Kochabend' });
    expect(sent.text).toContain('Freitag');
    await checkpoint(page, 'sent', { mask: clockTexts(page) });
  });

  await walk.step('forwards the hygiene concept to the board', async () => {
    await page.goto(`${inbox}/${mailId(5)}`);
    await expectDataState(page, 'mailboxes.mail.state', 'success');
    await walk.click(page.getByTestId('mailboxes.mail.forward'));
    await expect(page.getByTestId('mailboxes.compose.forwardedAttachment')).toContainText('Hygienekonzept-Fairteiler.pdf');
    await walk.fill(page.getByTestId('mailboxes.compose.to'), 'vorstand@example.ch');
    await walk.click(page.getByTestId('mailboxes.compose.send'));
    await expect(page).toHaveURL(/\/sent$/);
    await mails.waitForMail({ to: 'vorstand@example.ch', subjectContains: 'Fwd:' });
  });

  walk.finish({ steps: 7, clicks: 25, fields: 5 });
});
