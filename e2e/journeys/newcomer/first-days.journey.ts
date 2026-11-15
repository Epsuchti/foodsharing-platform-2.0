import { acceptPendingConsents, bubbles, expectDataState, journeyProgress } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { solutionKey } from '../../fixtures/flows/quiz';
import { Walk } from '../../fixtures/persona';
import type { Page } from '@playwright/test';

/**
 * D11.5.5 persona walkthrough "newcomer" (decision 3, design review M7 round 3): Lea signs in for the first time
 * (newbie@local, nothing done yet) and goes, by hand, from the consent wall through profile & region, the learning
 * chapters and the foodsaver quiz (the real question pool) to the EinAb list. Only the quiz answer key comes from the
 * quiz editor API (a real newcomer would have learned it). Booking the EinAb is the business of
 * journeys/einab/newcomer-books-intro-pickups; the quiz's other paths (practice, timed, failures) are
 * take-the-foodsaver-quiz. At every stage she passes, the journey page, the dashboard card and the journey progress
 * (header bar on phones, side-menu meter on desktop) name the same step and one next action (D11.5.2).
 *
 * Rules behind it: JourneyCalculatorTest (stages, steps, tracks, one figure while waiting), QuizRuleServiceTest
 * (pool, failure points), LegalConsentRulesTest (who has to accept what).
 */

interface JourneyDto {
  currentStep?: string;
  steps: { key: string; status: string }[];
}

/** The journey page shows `step` as the current one, with exactly one next action. */
async function expectCurrentStep(page: Page, step: string): Promise<void> {
  await expect(page).toHaveURL(/\/onboarding$/);
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', step);
  await expect(page.getByTestId(`onboarding.journey.stepper.step.${step}`)).toHaveAttribute('aria-current', 'step');
  await expect(page.getByTestId('onboarding.journey.cta').or(page.getByTestId('onboarding.journey.waiting'))).toHaveCount(1);
  await expect(journeyProgress(page)).toHaveAttribute('data-current', step);
}

test.describe(() => {
  test.use({ role: 'newbie' });

  test('newcomer accepts the privacy policy, completes profile and region, reads the chapters and passes the foodsaver quiz @D11.5.5 @D11.5.2 @EA4 @EA8 @A19 @C1 @Q2 @Ve1', async ({
    page,
    as,
    apiOf,
  }, testInfo) => {
    test.setTimeout(180_000);
    const walk = new Walk(page, testInfo, 'newcomer');
    const me = apiOf(page);
    const key = await solutionKey(apiOf(await as('orga')), 'FOODSAVER');
    const journey = () => me.getJson<JourneyDto>('/api/onboarding/journey');

    await walk.step('opens the app and accepts the privacy policy', async () => {
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/\/consent\?returnUrl=%2Fdashboard$/);
      await expectDataState(page, 'legal.consent.documents', 'success');
      // D3 (review 2026-11-10): no menu behind the consent wall
      await expect(page.locator('fs-shell[data-chrome="minimal"]')).toBeVisible();
      await expect(page.getByTestId('nav.dashboard')).toHaveCount(0);
      await expect(page.getByTestId('nav.tab.dashboard')).toHaveCount(0);
      // the legal agreement belongs to the quiz confirmation, not to the first login
      await expect(page.locator('[data-testid="legal.consent.document"][data-kind="LEGAL_AGREEMENT"]')).toHaveCount(0);
      const checks = page.locator('[data-testid^="legal.consent.check."] input');
      const count = await checks.count();
      expect(count).toBeGreaterThan(0);
      for (let i = 0; i < count; i++) await walk.check(checks.nth(i));
      await walk.click(page.getByTestId('legal.consent.submit'));
      await expect(page).toHaveURL(/\/dashboard$/);
    });

    await walk.step('follows "your next step" to profile and region', async () => {
      // D3: no chips for store walls or mailboxes a newcomer cannot use
      await expectDataState(page, 'dashboard.activity.filtersState', 'success');
      await expect(page.getByTestId('dashboard.activity.filter.FORUM')).toBeVisible();
      await expect(page.getByTestId('dashboard.activity.filter.STORE_WALL')).toHaveCount(0);
      await expect(page.getByTestId('dashboard.activity.filter.MAILBOX')).toHaveCount(0);
      await expect(page.getByTestId('dashboard.page.nextStep')).toHaveAttribute('data-journey-step', 'PROFILE_REGION');
      await expect(journeyProgress(page)).toHaveAttribute('data-current', 'PROFILE_REGION');
      await checkpoint(page, 'dashboard-next-step');
      await walk.click(page.getByTestId('dashboard.page.nextStepAction'));
      await expect(page).toHaveURL(/\/onboarding\/profile$/);
      await walk.fill(page.getByTestId('onboarding.profile.firstName'), 'Lea');
      await walk.fill(page.getByTestId('onboarding.profile.lastName'), 'Brunner');
      await walk.fill(page.locator('#ob-phone'), '079 123 45 67');
      await walk.fill(page.getByTestId('onboarding.profile.picker.postcode'), '8004');
      await walk.click(page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt'));
      await expect(page.getByTestId('onboarding.profile.regionCard')).toBeVisible();
      await checkpoint(page, 'profile-region');
      await walk.click(page.getByTestId('onboarding.profile.save'));
      await expectCurrentStep(page, 'LEARN');
      await expect(page.getByTestId('onboarding.journey.stepper.step.PROFILE_REGION')).toHaveAttribute('data-status', 'DONE');
      // D3: before the quiz step no further tracks (all locked, hygiene comes after the quiz)
      await expect(page.getByTestId('onboarding.journey.track')).toHaveCount(0);
      await checkpoint(page, 'journey-learn');
    });

    await walk.step('reads every learning chapter', async () => {
      await walk.click(page.getByTestId('onboarding.journey.cta'));
      await expect(page).toHaveURL(/\/learn\/[a-z-]+$/);
      let first = true;
      for (;;) {
        const article = page.getByTestId('content.chapter.article');
        await expect(article).toBeVisible();
        if (first) await checkpoint(page, 'first-chapter');
        first = false;
        const chapter = await article.getAttribute('data-key');
        await walk.click(page.getByTestId('content.chapter.markRead'));
        const done = page.getByTestId('content.chapter.done');
        await expect(done.or(page.locator(`[data-testid="content.chapter.article"]:not([data-key="${chapter}"])`))).toBeVisible();
        if (await done.isVisible()) break;
      }
      expect((await journey()).steps.find((step) => step.key === 'LEARN')?.status).toBe('DONE');
    });

    await walk.step('continues from the last chapter to the quiz', async () => {
      // round 3: the last chapter leads back into the journey instead of the chapter overview
      await walk.click(page.getByTestId('content.chapter.toJourney'));
      await expectCurrentStep(page, 'QUIZ');
      await walk.click(page.getByTestId('onboarding.journey.cta'));
      await expect(page).toHaveURL(/\/quizzes\/foodsaver$/i);
    });

    await walk.step('passes the foodsaver quiz and accepts the agreement', async () => {
      // the mode choice is part of the loaded intro: wait for it before branching
      await expect(page.getByTestId('quiz.intro.startButton')).toBeVisible();
      const untimed = page.getByTestId('quiz.intro.untimed');
      if (await untimed.isVisible()) await walk.click(untimed);
      await walk.click(page.getByTestId('quiz.intro.startButton'));
      for (;;) {
        const question = page.getByTestId('quiz.session.question');
        await expect(question).toBeVisible();
        const id = (await question.getAttribute('data-question'))!;
        for (const answerId of key.get(id) ?? []) await walk.check(question.locator(`[data-answer="${answerId}"]`));
        await walk.click(page.getByTestId('quiz.session.submit'));
        await expect(page.getByTestId('quiz.session.verdict')).toBeVisible();
        const results = page.getByTestId('quiz.session.results');
        await expect(results.or(page.getByTestId('quiz.session.next')).first()).toBeVisible();
        if (await results.isVisible()) {
          await walk.click(results);
          break;
        }
        await walk.click(page.getByTestId('quiz.session.next'));
        await expect(page.getByTestId('quiz.session.question')).not.toHaveAttribute('data-question', id);
      }
      await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
      await expect(page.getByTestId('quiz.results.question')).toHaveCount(10);
      const confirm = page.getByTestId('quiz.results.confirm');
      await expectDataState(page, 'quiz.results.document', 'success');
      await expect(confirm.getByTestId('quiz.results.confirmButton').locator('button')).toBeDisabled();
      // the drawn questions are random: the capture shows the agreement box only
      await checkpointElement(confirm, 'legal-agreement');
      await walk.click(confirm.getByTestId('quiz.results.agree'));
      await walk.click(confirm.getByTestId('quiz.results.confirmButton'));
      await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
      const steps = (await journey()).steps.filter((step) => ['QUIZ', 'AGREEMENT'].includes(step.key));
      expect(steps.map((step) => step.status)).toEqual(['DONE', 'DONE']);
      const consents = await me.getJson<{ kind: string; pending: boolean; acceptedVersion?: string }[]>('/api/legal/consents');
      expect(consents.find((c) => c.kind === 'LEGAL_AGREEMENT')).toMatchObject({ pending: false, acceptedVersion: expect.any(String) });
      expect((await me.session()).user?.role).toBe('FOODSAVER');
    });

    await walk.step('goes on from the quiz success to the EinAb list', async () => {
      // round 3: the success state carries the next step of the journey
      await walk.click(page.getByTestId('quiz.results.toJourney'));
      await expectCurrentStep(page, 'INTRO_PICKUPS');
      // D3: with the quiz step done the hygiene track shows; the locked ones stay hidden
      await expect(page.getByTestId('onboarding.journey.track')).toHaveCount(1);
      await expect(page.locator('[data-testid="onboarding.journey.track"][data-track="HYGIENE"]')).toBeVisible();
      await checkpoint(page, 'journey-intro-pickups');
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.nextStep')).toHaveAttribute('data-journey-step', 'INTRO_PICKUPS');
      await page.goto('/onboarding');
      await walk.click(page.getByTestId('onboarding.journey.cta'));
      await expect(page).toHaveURL(/\/intro-pickups$/);
      await expectDataState(page, 'introPickups.bookable.state', 'success');
    });

    // round 3 counted the walk up to "my EinAbs" (56–57 clicks, budget 60); the booking (book, confirm, to my
    // bookings: 3 clicks, journeys/einab) is no longer part of it
    walk.finish({ steps: 6, clicks: 57, fields: 5 });
  });
});

test.describe(() => {
  test.use({ role: 'pending' });

  test('newcomer waiting for her ID check sees one time figure and writes to the ambassador @D11.5.2 @EA8', async ({
    page,
    apiOf,
  }, testInfo) => {
    const walk = new Walk(page, testInfo, 'waiting newcomer');
    await acceptPendingConsents(apiOf(page));

    await walk.step('opens her journey', async () => {
      await page.goto('/onboarding');
      await expectCurrentStep(page, 'ID_CHECK');
      const step = page.getByTestId('onboarding.journey.stepper.step.ID_CHECK');
      await expect(step).toHaveAttribute('data-status', 'WAITING_FOR_OTHERS');
      // review 2026-11-10 Q3: the region's typical days or the static estimate, never both
      await expect(step.getByTestId('onboarding.journey.stepper.typicalDays').or(step.locator('.step__meta'))).toHaveCount(1);
      await checkpoint(page, 'journey-waiting');
    });

    await walk.step('asks the ambassador when she can come', async () => {
      const message = page.getByTestId('onboarding.journey.stepper.step.ID_CHECK').getByTestId('onboarding.journey.stepper.message').first();
      await expect(message).toHaveAccessibleName(/\S/);
      await walk.click(message);
      await expect(page).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
      await expectDataState(page, 'messaging.conversation.state', 'success');
      await walk.fill(page.getByTestId('messaging.conversation.composer'), 'Hallo, wann kann ich zur ID-Kontrolle kommen?');
      await page.getByTestId('messaging.conversation.composer').press('Enter');
      await expect(bubbles(page).last()).toContainText('ID-Kontrolle');
      await expect(bubbles(page).last()).toHaveAttribute('data-own', 'true');
      await expect(page.getByTestId('messaging.conversation.pending')).toHaveCount(0);
      await checkpoint(page, 'message-to-ambassador');
    });

    walk.finish({ steps: 2, clicks: 1, fields: 1 });
  });
});
