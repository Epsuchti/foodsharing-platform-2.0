import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectErrorState, injectApiError, seedRegion, test } from '../../fixtures';

/** N7: newsletters — compose, test mail, queue, job sends with unsubscribe link; region scope; public unsubscribe. */
test.use({ seedReset: 'test' });

const ZH = seedRegion('zuerich-stadt').id;

async function compose(page: Page, subject: string, body: string): Promise<void> {
  await page.getByTestId('newsletters.list.create').click();
  await expect(page).toHaveURL(/\/newsletters\/[0-9a-f-]{36}$/);
  await expect(page.getByTestId('newsletters.editor.state')).toHaveAttribute('data-state', 'success');
  await page.getByTestId('newsletters.editor.subject').fill(subject);
  await page.getByTestId('newsletters.editor.body').fill(body);
  await expect(page.getByTestId('newsletters.editor.preview')).toContainText(subject);
  await page.getByTestId('newsletters.editor.save').click();
  await expect(page.getByTestId('newsletters.editor.save').locator('button')).toBeDisabled();
}

async function queue(page: Page): Promise<number> {
  const count = Number(await page.getByTestId('newsletters.editor.recipients').getAttribute('data-count'));
  await page.getByTestId('newsletters.editor.queue').click();
  await expect(page.locator('.p-confirmdialog')).toContainText(String(count));
  await page.locator('.p-confirmdialog-accept-button').click();
  await expect(page.getByTestId('newsletters.editor.status')).toHaveText(/Eingeplant|Wird gesendet|Gesendet|Iiplant|Queued/);
  return count;
}

test('N7 orga composes global newsletter, test mail, queue, job sends to opt-in users with unsubscribe link @N7 @page', async ({
  as,
  mails,
  notifications,
}) => {
  const page = await as('orga');
  await page.goto('/newsletters');
  await expect(page.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', /success|empty/);
  const subject = `E2E Newsletter ${Date.now()}`;
  await compose(page, subject, '**Hallo** zusammen,\n\n- Punkt eins\n- Punkt zwei');
  await expect(page.getByTestId('newsletters.editor.preview').locator('strong')).toHaveText('Hallo');

  await page.getByTestId('newsletters.editor.test').click();
  await mails.waitForMail({ to: 'orga@local', subjectContains: subject });

  const count = await queue(page);
  expect(count).toBeGreaterThan(0);
  await notifications.runJob('newsletter-send');
  await expect(page.getByTestId('newsletters.editor.progress')).toHaveAttribute('data-status', 'SENT', { timeout: 20_000 });
  await expect(page.getByTestId('newsletters.editor.status')).toHaveAttribute('class', /badge--SENT/);

  // jumper@local is opted in (seed): the mail carries a personal unsubscribe link
  const mail = await mails.waitForMail({ to: 'jumper@local', subjectContains: subject });
  expect(mail.links.some((link) => /\/newsletter\/unsubscribe\/[^/]+$/.test(link))).toBe(true);
});

test('N7 unsubscribe link opts out and stops future newsletters @N7', async ({ as, apiOf, mails, notifications, page: guest }) => {
  const orga = await as('orga');
  await orga.goto('/newsletters');
  const first = `E2E Erste ${Date.now()}`;
  await compose(orga, first, 'Text eins');
  await queue(orga);
  await notifications.runJob('newsletter-send');
  const mail = await mails.waitForMail({ to: 'jumper@local', subjectContains: first });
  const link = mail.links.find((l) => l.includes('/newsletter/unsubscribe/'))!;

  // public page without app menu. The token contains a dot, which the server's SPA forwarding does not serve yet
  // (backend request); until then the app is entered at / and routes to the link client-side.
  await guest.goto('/');
  await guest.evaluate((path) => {
    history.pushState({}, '', path);
    dispatchEvent(new PopStateEvent('popstate'));
  }, new URL(link).pathname);
  await expect(guest.getByTestId('newsletters.unsubscribe.state')).toHaveAttribute('data-state', 'success');
  await expect(guest.getByTestId('newsletters.unsubscribe.page')).toContainText('@');
  await guest.getByTestId('newsletters.unsubscribe.confirm').click();
  await expect(guest.getByTestId('newsletters.unsubscribe.done')).toBeVisible();

  const jumper = await as('jumper');
  await acceptPendingConsents(apiOf(jumper));
  expect((await apiOf(jumper).getJson<{ newsletterOptIn: boolean }>('/api/users/me/notification-settings')).newsletterOptIn).toBe(false);

  // the next newsletter does not reach jumper
  await orga.goto('/newsletters');
  const second = `E2E Zweite ${Date.now()}`;
  await compose(orga, second, 'Text zwei');
  await queue(orga);
  await notifications.runJob('newsletter-send');
  await expect(orga.getByTestId('newsletters.editor.progress')).toHaveAttribute('data-status', 'SENT', { timeout: 20_000 });
  expect((await mails.list('jumper@local')).some((m) => m.subject.includes(second))).toBe(false);

  // an unknown token shows the not-found state
  await guest.goto('/newsletter/unsubscribe/not-a-token');
  await expect(guest.getByTestId('newsletters.unsubscribe.state')).toHaveAttribute('data-state', /notFound|error/);
});

test('N7 ambassador region newsletter reaches only region members; foodsaver forbidden @N7', async ({ as, apiOf }) => {
  const bot = await as('bot');
  await acceptPendingConsents(apiOf(bot));
  await bot.goto(`/regions/${ZH}/newsletters`);
  await expect(bot.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', /success|empty/);
  await compose(bot, `Zürich intern ${Date.now()}`, 'Nur für Zürich');
  const id = bot.url().split('/').pop()!;
  const created = await apiOf(bot).getJson<{ region?: { id: string } }>(`/api/newsletters/${id}`);
  expect(created.region?.id).toBe(ZH);
  const regionCount = (await apiOf(bot).getJson<{ count: number }>(`/api/newsletters/${id}/recipient-count`)).count;
  const orga = apiOf(await as('orga'));
  const global = await orga.postJson<{ id: string }>('/api/newsletters', { subject: 'Global', bodyMarkdown: 'x' });
  const globalCount = (await orga.getJson<{ count: number }>(`/api/newsletters/${global.id}/recipient-count`)).count;
  expect(regionCount).toBeLessThanOrEqual(globalCount);

  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/newsletters');
  await expect(foodsaver.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', 'forbidden');
});

test('N7 newsletter error states @N7', async ({ as }) => {
  const page = await as('orga');
  await injectApiError(page, { url: /\/api\/newsletters(\?|$)/, method: 'GET', status: 500 });
  await page.goto('/newsletters');
  await expectErrorState(page, 'newsletters.list.state');
  await injectApiError(page, { url: '**/api/newsletters/00000004-0006-4000-8000-000000000001', status: 503 });
  await page.goto('/newsletters/00000004-0006-4000-8000-000000000001');
  await expectErrorState(page, 'newsletters.editor.state');
});
