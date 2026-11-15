import { type ApiClient, SEED_NOW, expectDataState, journeyIndicator } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm, skipPasskeyNudge } from '../../fixtures/flows/auth';
import { singleQuestionQuiz, solutionKey } from '../../fixtures/flows/quiz';
import { Walk } from '../../fixtures/persona';
import type { Page } from '@playwright/test';

/**
 * EA4 / Ve1: the consent wall. Mia comes from the landing page with Google (two clicks to an account, D11.5.1), meets
 * the privacy policy before anything else, leaves through the wall's own logout (decision 176) and comes back later to
 * accept it. Sarah (foodsaver@local) is asked again after the privacy policy changed, loses a race with a newer
 * version published meanwhile, reads the public document, and accepts the confidentiality notice when she confirms
 * the store manager quiz.
 *
 * Rules behind it: LegalConsentRulesTest (which documents a role has to accept), LegalServiceTest (pending, re-consent
 * only when a version requires it, outdated versions refused, history entry), QuizSessionServiceTest (the store
 * manager confirmation needs the confidentiality notice), consent-page.spec (the wall's logout).
 */

const MIA = { email: 'mia.neu@example.org', name: 'Mia Neu' };

interface Consent {
  kind: string;
  currentVersion: string;
  acceptedVersion?: string;
  required: boolean;
  pending: boolean;
}

const consentOf = async (api: ApiClient, kind: string) =>
  (await api.getJson<Consent[]>('/api/legal/consents')).find((c) => c.kind === kind)!;

async function publishPrivacyPolicy(orga: ApiClient, version: string, requireReconsent: boolean): Promise<void> {
  await orga.postJson('/api/legal/PRIVACY_POLICY/versions', {
    version,
    summaries: { de: `Neu in ${version}: kürzere Aufbewahrung.` },
    bodies: { de: `## Datenschutz ${version}\n\nWir speichern weniger.`, en: `## Privacy ${version}\n\nWe keep less.` },
    requireReconsent,
  });
}

/** Landing → "with Google" → the fake Google's form → back in the app. Two clicks, two fields. */
async function joinWithGoogle(walk: Walk, page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();
  await walk.click(page.getByTestId('public.landing.joinGoogle'));
  // no login page in between: straight to Google
  await expect(page.getByTestId('dev.oidc.page')).toBeVisible();
  await walk.fill(page.getByTestId('dev.oidc.new-email'), MIA.email);
  await walk.fill(page.getByTestId('dev.oidc.new-name'), MIA.name);
  await walk.click(page.getByTestId('dev.oidc.new-submit'));
  await expect(page).toHaveURL(/\/consent(\?|$)/);
}

function document(page: Page, kind: string) {
  return page.locator(`[data-testid="legal.consent.document"][data-kind="${kind}"]`);
}

test('new account from the landing page meets the privacy policy, leaves through the wall and accepts it later @D11.5.1 @EA4 @Ve1', async ({
  page,
  apiOf,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  await skipPasskeyNudge(page);
  const me = apiOf(page);

  await walk.step('joins with Google from the landing page', async () => {
    await joinWithGoogle(walk, page);
    // D11.5.1: the account exists after two clicks
    expect((await me.session()).authenticated).toBe(true);
  });

  await walk.step('reads the privacy policy on the consent wall', async () => {
    await expectDataState(page, 'legal.consent.documents', 'success');
    // decision 176: no menu, footer or tab bar here
    await expect(page.locator('fs-shell[data-chrome="minimal"]')).toBeVisible();
    await expect(page.getByTestId('shell.userMenu')).toHaveCount(0);
    // A6: a new account accepts for the first time, nothing has "changed" for it
    await expect(page.locator('fs-page-header .ph__subtitle')).toHaveText('Bevor du startest: Bitte lies diese Dokumente und stimme ihnen zu.');
    await expect(page.getByTestId('legal.consent.document')).toHaveCount(1);
    const privacy = await consentOf(me, 'PRIVACY_POLICY');
    expect(privacy).toMatchObject({ required: true, pending: true });
    await expect(document(page, 'PRIVACY_POLICY')).toContainText(privacy.currentVersion);
    // the legal agreement belongs to the foodsaver quiz; the phone number to onboarding step 2 (D2)
    expect(await consentOf(me, 'LEGAL_AGREEMENT')).toMatchObject({ required: false, pending: false });
    await expect(page.getByTestId('legal.consent.phone')).toHaveCount(0);
    await walk.click(page.getByTestId('legal.consent.fullText.PRIVACY_POLICY'));
    await expect(document(page, 'PRIVACY_POLICY').locator('details fs-markdown')).not.toBeEmpty();
    await expect(page.getByTestId('legal.consent.submit').locator('button')).toBeDisabled();
    await checkpoint(page, 'consent-first-time');
  });

  await walk.step('leaves for now through the logout on the wall', async () => {
    const logout = page.getByTestId('legal.consent.logout').locator('button');
    await expect(logout).toHaveText('Abmelden');
    await walk.click(logout);
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByTestId('shell.login')).toBeVisible();
    expect((await me.session()).authenticated).toBe(false);
    // logged out, not consented: a member page asks for the login again
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  await walk.step('comes back with Google and accepts', async () => {
    await joinWithGoogle(walk, page);
    await expectDataState(page, 'legal.consent.documents', 'success');
    const submit = page.getByTestId('legal.consent.submit').locator('button');
    await walk.check(document(page, 'PRIVACY_POLICY').locator('[data-testid^="legal.consent.check."] input'));
    await expect(submit).toBeEnabled();
    await walk.click(submit);
    // straight into the journey
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'PROFILE_REGION');
    await expect(journeyIndicator(page)).toBeVisible();
    const privacy = await consentOf(me, 'PRIVACY_POLICY');
    expect(privacy).toMatchObject({ pending: false, acceptedVersion: privacy.currentVersion });
    // her avatar colour comes from the random id of the new account
    await checkpoint(page, 'journey-start', { mask: [page.locator('fs-user-avatar')] });
  });

  // 2 clicks + 2 fields to the account (D11.5.1), twice; full text, logout, check, accept
  walk.finish({ steps: 4, clicks: 8, fields: 4 });
});

test.describe(() => {
  test.use({ role: 'foodsaver' });

  test('foodsaver accepts a changed privacy policy and the confidentiality notice as new store manager @Ve1 @EA4 @Q2 @D10.3', async ({
    page,
    api,
    apiOf,
    as,
    dev,
  }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'foodsaver');
    const me = apiOf(page);
    await api.loginAs('orga');
    const privacy = document(page, 'PRIVACY_POLICY');

    // the orga team publishes a new privacy policy that needs everybody's consent again (its own journey captures the
    // page; here only what Sarah gets to see matters)
    const orga = await as('orga');
    await orga.goto('/admin/legal');
    await expectDataState(orga, 'legal.admin.versions', 'success');
    await expect(orga.getByTestId('legal.admin.version')).toHaveCount(1);
    const publish = orga.getByTestId('legal.admin.publish').locator('button');
    await expect(publish).toBeDisabled();
    await orga.getByTestId('legal.admin.version.input').fill('2026-11');
    await orga.getByTestId('legal.admin.bodies.field.de').fill('## Datenschutz 2026-11\n\nNeue Fassung mit kürzerer Aufbewahrung.');
    await orga.getByTestId('legal.admin.reconsent').locator('input').check();
    await expect(publish).toBeEnabled();
    await publish.click();
    await acceptConfirm(orga);
    const published = orga.locator('[data-testid="legal.admin.version"][data-version="2026-11"]');
    await expect(published).toBeVisible();
    await expect(published.getByTestId('legal.admin.reconsentChip')).toBeVisible();
    // the form is reset after publishing
    await expect(orga.getByTestId('legal.admin.version.input')).toHaveValue('');
    await orga.close();

    await walk.step('is asked to accept the changed privacy policy', async () => {
      await page.goto('/me/activity');
      await expect(page).toHaveURL(/\/consent\?returnUrl=%2Fme%2Factivity$/);
      await expectDataState(page, 'legal.consent.documents', 'success');
      // A6: an earlier version was accepted, so the page talks about a changed document
      await expect(page.locator('fs-page-header .ph__subtitle')).toContainText('geänderte Dokumente');
      await expect(page.getByTestId('legal.consent.document')).toHaveCount(1);
      await expect(privacy).toContainText('2026-11');
      await expect(privacy).toContainText('kürzerer Aufbewahrung');
      await checkpoint(page, 'consent-changed');
    });

    await walk.step('accepts, but a newer version came out meanwhile', async () => {
      // a minute later the orga team publishes a correction that needs no new consent
      await dev.setClock(new Date(Date.parse(SEED_NOW) + 60_000).toISOString());
      await publishPrivacyPolicy(api, '2026-12', false);
      await walk.check(privacy.locator('[data-testid^="legal.consent.check."] input'));
      await walk.click(page.getByTestId('legal.consent.submit'));
      await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
      await expect(page).toHaveURL(/\/consent/);
      expect(await consentOf(me, 'PRIVACY_POLICY')).toMatchObject({ currentVersion: '2026-12', pending: true });

      await page.reload();
      await expectDataState(page, 'legal.consent.documents', 'success');
      await expect(privacy).toContainText('2026-12');
      await walk.check(privacy.locator('[data-testid^="legal.consent.check."] input'));
      await walk.click(page.getByTestId('legal.consent.submit'));
      await expect(page).toHaveURL(/\/me\/activity$/);
      expect(await consentOf(me, 'PRIVACY_POLICY')).toMatchObject({ acceptedVersion: '2026-12', pending: false });
      // the consent is in her activity
      await expectDataState(page, 'history.myActivity.list', 'success');
      await expect(page.locator('[data-testid="history.myActivity.entry"][data-action="USER.CONSENT_GIVEN"]').first()).toBeVisible();
    });

    await walk.step('reads the public privacy policy', async () => {
      const link = page.getByTestId('nav.footer.privacy');
      await link.scrollIntoViewIfNeeded();
      await walk.click(link);
      await expect(page).toHaveURL(/\/legal\/privacy-policy$/);
      await expectDataState(page, 'legal.document.state', 'success');
      await expect(page.getByTestId('legal.document.version')).toContainText('2026-12');
      await checkpoint(page, 'privacy-policy');
      // nothing left to accept: the consent page says so
      await page.goto('/consent');
      await expectDataState(page, 'legal.consent.documents', 'empty');
    });

    await walk.step('passes the store manager quiz and accepts the confidentiality notice', async () => {
      await singleQuestionQuiz(api, 'STORE_MANAGER');
      const key = await solutionKey(api, 'STORE_MANAGER');
      expect(await consentOf(me, 'CONFIDENTIALITY_NOTICE')).toMatchObject({ required: false, pending: false });

      await page.goto('/quizzes');
      await expectDataState(page, 'quiz.list.state', 'success');
      // Q2: her hygiene quiz (passed 2026-01-06) runs out within 90 days: 66 days left at the seed date
      const hygiene = page.locator('[data-testid="quiz.list.card"][data-kind="HYGIENE"]');
      await expect(hygiene.getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'expiring');
      await expect(hygiene.getByTestId('quiz.list.status')).toContainText('66');
      await checkpoint(page, 'quizzes-foodsaver');

      await walk.click(page.locator('[data-testid="quiz.list.card"][data-kind="STORE_MANAGER"]').getByTestId('quiz.list.open'));
      await expect(page).toHaveURL(/\/quizzes\/store-manager$/);
      await expect(page.getByTestId('quiz.intro.startButton')).toBeVisible();
      const untimed = page.getByTestId('quiz.intro.untimed');
      if (await untimed.isVisible()) await walk.click(untimed);
      await walk.click(page.getByTestId('quiz.intro.startButton'));
      const question = page.getByTestId('quiz.session.question');
      await expect(question).toBeVisible();
      const id = (await question.getAttribute('data-question'))!;
      for (const answerId of key.get(id) ?? []) await walk.check(question.locator(`[data-answer="${answerId}"]`));
      await walk.click(page.getByTestId('quiz.session.submit'));
      await expect(page.getByTestId('quiz.session.verdict')).toBeVisible();
      await walk.click(page.getByTestId('quiz.session.results'));
      await expect(page).toHaveURL(/\/quizzes\/store-manager\/results$/);
      await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');

      const confirm = page.getByTestId('quiz.results.confirm');
      await expectDataState(page, 'quiz.results.document', 'success');
      await expect(confirm.getByTestId('quiz.results.confirmButton').locator('button')).toBeDisabled();
      await checkpoint(page, 'store-manager-confirm', { mask: [page.locator('fs-quiz-solution-list')] });
      await walk.click(confirm.getByTestId('quiz.results.agree'));
      await walk.click(confirm.getByTestId('quiz.results.confirmButton'));
      await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
      expect((await me.session()).user?.role).toBe('STORE_MANAGER');
      expect(await consentOf(me, 'CONFIDENTIALITY_NOTICE')).toMatchObject({ required: true, pending: false });
      // accepted in the confirmation: the consent wall does not ask again
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    });

    // two tries at the wall (check + accept each), footer link, the quiz (open, start, answer, submit, results) and
    // the confirmation (agree, confirm)
    walk.finish({ steps: 4, clicks: 13, fields: 0 });
  });
});
