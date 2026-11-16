import path from 'node:path';
import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * C9 support (plan-m7 Q11, A54) and the help around it: a visitor follows an old bookmark of a removed page (decision
 * 172, C16) to the start page, searches the FAQ (C1) and writes to support with name and email; a member (foodsaver@local)
 * rereads the learning chapters she already knows (C1, decision 174) and then asks support about the pickup page with a
 * screenshot attached. Both tickets are mailed to `support.email` (seed support@local.test) with page and browser, and
 * the ORGA gets the daily SUPPORT_TICKET bell (N6). Rules behind it: SupportServiceTest (mail with page, user agent and
 * attachment links, foreign attachments, rate limit per user and IP, the public form), LearningChapterOrderTest; client
 * content-q11.spec (source page from ?from=, 429 keeps the form with a notice), content-q10.spec (FAQ filter, removed
 * pages and body links routed), content.util.spec, learning-progress.spec.
 */
test.use({ role: 'foodsaver' });

const SUPPORT_MAIL = 'support@local.test';
const ATTACHMENT = path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png');
const CHAPTERS = 7;

test('a visitor and a member find help in the FAQ, the chapters and the support form; the orga is rung @C9 @N6 @C1 @C16', async ({
  page,
  as,
  apiOf,
  mails,
  notifications,
}, testInfo) => {
  test.setTimeout(150_000);
  const guest = await as(null);
  const visitor = new Walk(guest, testInfo, 'visitor');
  const visitorSubject = 'Frage zur Registrierung';

  await visitor.step('follows an old bookmark to the start page', async () => {
    // decision 172: the team and about pages are gone
    await guest.goto('/team');
    await expect(guest).toHaveURL(/\/$/);
    await expect(guest.getByTestId('public.landing.hero')).toBeVisible();
  });

  await visitor.step('searches the FAQ', async () => {
    // the start page's footer (phones have no menu for visitors)
    await visitor.click(guest.getByTestId('nav.footer.faq'));
    await expect(guest).toHaveURL(/\/faq$/);
    await expectDataState(guest, 'content.faq.state', 'success');
    const entries = guest.getByTestId('content.faq.entry');
    await expect(entries).toHaveCount(12);
    // the filter searches the answers too, without case and accents: "KOSTENLOS" finds faq-01
    await visitor.fill(guest.getByTestId('content.faq.filter'), 'KOSTENLOS');
    await expect(entries.first()).toHaveAttribute('data-slug', 'faq-01');
    await expect.poll(() => entries.count()).toBeLessThan(12);
    await visitor.click(entries.first().getByTestId('content.faq.question'));
    await expect(entries.first().getByTestId('content.faq.answer')).toBeVisible();
    await checkpoint(guest, 'faq');
    await visitor.fill(guest.getByTestId('content.faq.filter'), 'Abholung am Sonntag');
    await expect(entries).toHaveCount(0);
    await expect(guest.getByTestId('content.faq.noMatch')).toBeVisible();
  });

  await visitor.step('writes to support with name and email', async () => {
    // the support form is linked in the footer of every page
    await visitor.click(guest.getByTestId('nav.footer.support'));
    await expect(guest).toHaveURL(/\/support$/);
    await expect(guest.getByTestId('content.support.form')).toBeVisible();
    // visitors attach nothing
    await expect(guest.getByTestId('content.support.files.input')).toHaveCount(0);
    await visitor.fill(guest.getByTestId('content.support.firstName'), 'Mia');
    await visitor.fill(guest.getByTestId('content.support.email'), 'mia@example.org');
    await visitor.fill(guest.getByTestId('content.support.subject'), visitorSubject);
    await visitor.fill(guest.getByTestId('content.support.body'), 'Wie werde ich Foodsaver?');
    await visitor.click(guest.getByTestId('content.support.send'));
    await expect(guest.getByTestId('content.support.sent')).toBeVisible();
    const mail = await mails.waitForMail({ to: SUPPORT_MAIL, subjectContains: visitorSubject });
    expect(mail.text).toContain('mia@example.org');
    expect(mail.text).toContain('Mia');
  });

  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'member');
  const memberSubject = 'Abholung lässt sich nicht eintragen';

  await walk.step('rereads the chapters she already knows', async () => {
    await page.goto('/onboarding');
    await expect(page.getByTestId('onboarding.journey.stepper.step.LEARN')).toHaveAttribute('data-status', 'DONE');
    await walk.click(page.getByTestId('onboarding.journey.stepper.relearn.LEARN'));
    await expect(page).toHaveURL(/\/learn$/);
    await expect(page.getByTestId('content.learn.progress')).toHaveAttribute('data-read', String(CHAPTERS));
    await checkpoint(page, 'learn-all-read');
    // rereading marks nothing again
    const marks: string[] = [];
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/api/onboarding/learning/')) marks.push(request.url());
    });
    await walk.click(page.locator('[data-testid="content.learn.chapter"][data-key="rule-violations"]'));
    await expect(page).toHaveURL(/\/learn\/rule-violations$/);
    await expect(page.getByTestId('content.chapter.alreadyRead')).toBeVisible();
    await expect(page.getByTestId('content.chapter.markRead')).toHaveCount(0);
    // a section link of another chapter lands on the section
    await walk.click(page.getByTestId('content.chapter.body').getByRole('link', { name: 'C6)' }));
    await expect(page).toHaveURL(/\/learn\/code-of-conduct#c-betriebe-betriebsteam-planung-von-abholungen$/);
    await expect(page.locator('[id="c-betriebe-betriebsteam-planung-von-abholungen"]')).toBeInViewport();
    await expect(page.getByTestId('content.chapter.alreadyRead')).toBeVisible();
    await checkpoint(page, 'chapter-read', { fullPage: false });
    expect(marks).toEqual([]);
  });

  await walk.step('asks support about the pickup page with a screenshot', async () => {
    const userAgent = await page.evaluate(() => navigator.userAgent);
    // a help link that carries the page the question is about
    await page.goto('/support?from=%2Fpickups%3Fweek%3D45');
    await expect(page.getByTestId('content.support.form')).toBeVisible();
    // members write under their account
    await expect(page.getByTestId('content.support.firstName')).toHaveCount(0);
    await expect(page.getByTestId('content.support.page')).toHaveValue('/pickups?week=45');
    await expect(page.getByTestId('content.support.page')).toHaveAttribute('readonly', '');
    await expect(page.getByTestId('content.support.userAgent')).toHaveValue(userAgent);
    await expect(page.getByTestId('content.support.send').locator('button')).toBeDisabled();
    await walk.fill(page.getByTestId('content.support.subject'), memberSubject);
    await walk.fill(page.getByTestId('content.support.body'), 'Beim Eintragen kommt eine Fehlermeldung.');
    await page.getByTestId('content.support.files.input').setInputFiles(ATTACHMENT);
    walk.count(1);
    await expect(page.getByTestId('content.support.files.file')).toHaveCount(1);
    await expect(page.getByTestId('content.support.files.uploading')).toHaveCount(0);
    // the browser string differs per engine and version
    await checkpoint(page, 'support-form', { mask: [page.getByTestId('content.support.userAgent')] });
    await walk.click(page.getByTestId('content.support.send'));
    await expect(page.getByTestId('content.support.sent')).toBeVisible();
    const ticketId = page.getByTestId('content.support.ticketId');
    await expect(ticketId).toHaveText(/^[0-9a-f-]{36}$/);
    await checkpoint(page, 'support-sent', { mask: [ticketId] });

    // the mail to the support address carries page, browser and the attachment link
    const mail = await mails.waitForMail({ to: SUPPORT_MAIL, subjectContains: memberSubject });
    expect(mail.text).toContain('Seite: /pickups?week=45');
    expect(mail.text).toContain(`Browser: ${userAgent}`);
    expect(mail.text).toContain('foodsaver@local');
    expect(mail.text).toMatch(/\/api\/uploads\/[0-9a-f-]{36}/);
  });

  // N6: the ORGA gets the support bell of the day
  await expect.poll(async () => (await notifications.bells('orga', 'SUPPORT_TICKET')).length).toBeGreaterThan(0);

  visitor.finish({ steps: 3, clicks: 4, fields: 6 });
  walk.finish({ steps: 2, clicks: 5, fields: 2 });
});
