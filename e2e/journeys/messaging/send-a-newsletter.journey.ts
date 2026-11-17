import type { Page } from '@playwright/test';
import { acceptPendingConsents } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * ORGA (orga@local) sends a newsletter to everybody who opted in (N7): writes it in Markdown with the live preview,
 * sends herself a test mail, queues it after the confirmation with the recipient count, and the send job (run on
 * demand here instead of waiting for the scheduler) sends it. Jonas (jumper@local, opted in) gets it with his
 * personal unsubscribe link, opens the link without being logged in and unsubscribes. Rules: who may write global
 * and region newsletters, region recipients (opted-in members of the subtree), queue/cancel states, the send batches,
 * unsubscribe tokens (also the legacy format) and Markdown sanitising are unit tests (NewsletterServiceTest,
 * NewsletterCapabilityTest, NewsletterMarkdownTest, newsletters.spec).
 */
test.use({ role: 'orga' });

const SUBJECT = 'Herbstgruss aus dem Vorstand';
/** Created/queued/sent dates follow the server clock: the list rows' meta line, the editor's "sent at" line. */
const clockTexts = (page: Page) => [
  page.getByTestId('newsletters.list.state').locator('.muted'),
  page.getByTestId('newsletters.editor.state').locator('p.muted').filter({ hasText: /\d:\d\d/ }),
];

test('ORGA writes, tests, queues and sends a newsletter; a reader unsubscribes @N7', async ({
  page,
  as,
  apiOf,
  mails,
  notifications,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'orga');

  await walk.step('writes the newsletter with the live preview', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('newsletters');
    await expect(page).toHaveURL(/\/newsletters$/);
    await expect(page.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', /success|empty/);
    await checkpoint(page, 'newsletter-list', { mask: clockTexts(page) });
    await walk.click(page.getByTestId('newsletters.list.create'));
    await expect(page).toHaveURL(/\/newsletters\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('newsletters.editor.state')).toHaveAttribute('data-state', 'success');
    await walk.fill(page.getByTestId('newsletters.editor.subject'), SUBJECT);
    await walk.fill(page.getByTestId('newsletters.editor.body'), '**Hallo** zusammen,\n\n- Punkt eins\n- Punkt zwei');
    const preview = page.getByTestId('newsletters.editor.preview');
    await expect(preview).toContainText(SUBJECT);
    await expect(preview.locator('strong')).toHaveText('Hallo');
    await walk.click(page.getByTestId('newsletters.editor.save'));
    await expect(page.getByTestId('newsletters.editor.save').locator('button')).toBeDisabled();
    await checkpoint(page, 'newsletter-editor', { mask: clockTexts(page) });
  });

  await walk.step('sends herself a test mail', async () => {
    await walk.click(page.getByTestId('newsletters.editor.test'));
    await mails.waitForMail({ to: 'orga@local', subjectContains: SUBJECT });
  });

  await walk.step('queues it for everybody who opted in', async () => {
    const count = Number(await page.getByTestId('newsletters.editor.recipients').getAttribute('data-count'));
    expect(count).toBeGreaterThan(0);
    await walk.click(page.getByTestId('newsletters.editor.queue'));
    await expect(page.locator('.p-confirmdialog')).toContainText(String(count));
    await checkpoint(page, 'queue-confirmation');
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(page.getByTestId('newsletters.editor.status')).toHaveText(/Eingeplant|Wird gesendet|Gesendet/);

    // the scheduler's send job
    await notifications.runJob('newsletter-send');
    await expect(page.getByTestId('newsletters.editor.progress')).toHaveAttribute('data-status', 'SENT', { timeout: 20_000 });
    await expect(page.getByTestId('newsletters.editor.status')).toHaveAttribute('class', /badge--SENT/);
    await checkpoint(page, 'newsletter-sent', { mask: clockTexts(page) });
  });

  await test.step('Jonas unsubscribes through the link in his mail', async () => {
    const mail = await mails.waitForMail({ to: 'jumper@local', subjectContains: SUBJECT });
    const path = mails.extractPath(mail, /\/newsletter\/unsubscribe\/[^/]+$/);
    // opened from the mail client: not logged in, the server forwards the link to the app
    const guest = await as(null);
    await guest.goto(path);
    await expect(guest.getByTestId('newsletters.unsubscribe.state')).toHaveAttribute('data-state', 'success');
    await expect(guest.getByTestId('newsletters.unsubscribe.page')).toContainText('@');
    await checkpoint(guest, 'unsubscribe');
    await guest.getByTestId('newsletters.unsubscribe.confirm').click();
    await expect(guest.getByTestId('newsletters.unsubscribe.done')).toBeVisible();
    await checkpoint(guest, 'unsubscribed');

    const jonas = apiOf(await as('jumper'));
    await acceptPendingConsents(jonas);
    expect((await jonas.getJson<{ newsletterOptIn: boolean }>('/api/users/me/notification-settings')).newsletterOptIn).toBe(false);
  });

  walk.finish({ steps: 3, clicks: 7, fields: 2 });
});
