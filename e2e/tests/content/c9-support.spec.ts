import path from 'node:path';
import { expect, expectNoMissingKeys, injectApiError, setLanguage, test } from '../../fixtures';

/**
 * C9 support (plan-m7 Q11, A54): the form sends subject, message, the page the request is about (`?from=`) and the
 * browser's user agent (shown read-only), signed in with attachments; the ticket is mailed to `support.email`
 * (seed support@local.test) and ORGA gets the daily SUPPORT_TICKET bell (N6). 429 SUPPORT_RATE_LIMITED shows its
 * notice and keeps the form (the e2e server allows 10 000 tickets per hour, so the 429 is injected).
 */
test.use({ seedReset: 'test' });

const SUPPORT_MAIL = 'support@local.test';
const ATTACHMENT = path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png');

test.describe('signed in', () => {
  test.use({ role: 'foodsaver' });

  test('C9 support ticket includes page and user agent, mail captured, rate limit 429 shown @C9 @N6 @page', async ({
    page,
    mails,
    notifications,
  }) => {
    await setLanguage(page, 'de');
    const subject = `Abholung lässt sich nicht eintragen ${Date.now()}`;
    const userAgent = await page.evaluate(() => navigator.userAgent);

    await page.goto('/support?from=%2Fpickups%3Fweek%3D45');
    await expect(page.getByTestId('content.support.form')).toBeVisible();
    await expect(page.getByTestId('content.support.firstName')).toHaveCount(0);
    await expect(page.getByTestId('content.support.page')).toHaveValue('/pickups?week=45');
    await expect(page.getByTestId('content.support.page')).toHaveAttribute('readonly', '');
    await expect(page.getByTestId('content.support.userAgent')).toHaveValue(userAgent);
    await expect(page.getByTestId('content.support.send').locator('button')).toBeDisabled();

    await page.getByTestId('content.support.subject').fill(subject);
    await page.getByTestId('content.support.body').fill('Beim Eintragen kommt eine Fehlermeldung.');
    await page.getByTestId('content.support.files.input').setInputFiles(ATTACHMENT);
    await expect(page.getByTestId('content.support.files.file')).toHaveCount(1);
    await expect(page.getByTestId('content.support.files.uploading')).toHaveCount(0);
    await expectNoMissingKeys(page);
    await page.getByTestId('content.support.send').click();
    await expect(page.getByTestId('content.support.sent')).toBeVisible();
    await expect(page.getByTestId('content.support.ticketId')).toHaveText(/^[0-9a-f-]{36}$/);

    // the mail to the support address carries page, user agent and the attachment link
    const mail = await mails.waitForMail({ to: SUPPORT_MAIL, subjectContains: subject });
    expect(mail.text).toContain('Seite: /pickups?week=45');
    expect(mail.text).toContain(`Browser: ${userAgent}`);
    expect(mail.text).toContain('foodsaver@local');
    expect(mail.text).toMatch(/\/api\/uploads\/[0-9a-f-]{36}/);
    // N6: ORGA gets the support bell of the day
    await expect.poll(async () => (await notifications.bells('orga', 'SUPPORT_TICKET')).length).toBeGreaterThan(0);

    // a second request runs into the rate limit: notice, the form keeps its text
    await page.getByTestId('content.support.another').click();
    await page.getByTestId('content.support.subject').fill('Noch eine Frage');
    await page.getByTestId('content.support.body').fill('Text');
    const limited = await injectApiError(page, {
      url: '/api/support/tickets',
      method: 'POST',
      status: 429,
      code: 'SUPPORT_RATE_LIMITED',
    });
    await page.getByTestId('content.support.send').click();
    await expect(page.getByTestId('content.support.rateLimited')).toBeVisible();
    await expect(page.getByTestId('content.support.sent')).toHaveCount(0);
    await expect(page.getByTestId('content.support.subject')).toHaveValue('Noch eine Frage');
    expect(limited.calls()).toBe(1);
    await limited.remove();
  });
});

test('C9 visitors write with name and email, without attachments @C9 @page', async ({ page, mails }) => {
  await setLanguage(page, 'de');
  const subject = `Frage zur Registrierung ${Date.now()}`;
  await page.goto('/support');
  await expect(page.getByTestId('content.support.page')).toHaveValue(/.+/);
  await expect(page.getByTestId('content.support.files.input')).toHaveCount(0);
  await page.getByTestId('content.support.firstName').fill('Mia');
  await page.getByTestId('content.support.email').fill('mia@example.org');
  await page.getByTestId('content.support.subject').fill(subject);
  await page.getByTestId('content.support.body').fill('Wie werde ich Foodsaver?');
  await page.getByTestId('content.support.send').click();
  await expect(page.getByTestId('content.support.sent')).toBeVisible();
  const mail = await mails.waitForMail({ to: SUPPORT_MAIL, subjectContains: subject });
  expect(mail.text).toContain('mia@example.org');
  expect(mail.text).toContain('Mia');
  await expectNoMissingKeys(page);
});
