import { expect, expectDataState, test } from '../../fixtures';
import { solutionKey } from '../quiz/_quiz';
import { Walk } from './_persona';

/**
 * D11.5.5 persona walkthrough "newcomer" (decision 3, design review M7 round 3): Lea signs in for the first time
 * (newbie@local, nothing done yet) and goes, by hand, from the consent page through profile & region, the learning
 * chapters and the foodsaver quiz to her first EinAb booking. Only the quiz answer key comes from the quiz editor
 * API (a real newcomer would have learned it). Runs on desktop and on the phone project.
 */
test.use({ seedReset: 'test', role: 'newbie' });

interface JourneyDto {
  currentStep?: string;
  steps: { key: string; status: string; progress?: { done: number; booked: number; required: number } }[];
}

test('D11.5.5 newcomer: consent, profile, learning, quiz, first EinAb booking @D11.5.5 @EA8 @A19 @C1 @Q2 @EA7 @page', async ({
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
    await expect(page).toHaveURL(/\/consent(\?|$)/);
    await expectDataState(page, 'legal.consent.documents', 'success');
    const checks = page.locator('[data-testid^="legal.consent.check."] input');
    const count = await checks.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) await walk.check(checks.nth(i));
    const phone = page.getByTestId('legal.consent.phoneInput');
    if (await phone.isVisible()) await walk.fill(phone, '079 123 45 99');
    await walk.click(page.getByTestId('legal.consent.submit'));
    await expect(page).not.toHaveURL(/\/consent/);
  });

  await walk.step('follows "your next step" to profile and region', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.nextStep')).toHaveAttribute('data-journey-step', 'PROFILE_REGION');
    await walk.click(page.getByTestId('dashboard.page.nextStepAction'));
    await expect(page).toHaveURL(/\/onboarding\/profile$/);
    await walk.fill(page.getByTestId('onboarding.profile.firstName'), 'Lea');
    await walk.fill(page.getByTestId('onboarding.profile.lastName'), 'Brunner');
    await walk.fill(page.locator('#ob-phone'), '079 123 45 67');
    await walk.fill(page.getByTestId('onboarding.profile.picker.postcode'), '8004');
    await walk.click(page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt'));
    await expect(page.getByTestId('onboarding.profile.regionCard')).toBeVisible();
    await walk.click(page.getByTestId('onboarding.profile.save'));
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByTestId('onboarding.journey.stepper.step.PROFILE_REGION')).toHaveAttribute('data-status', 'DONE');
    await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'LEARN');
  });

  await walk.step('reads every learning chapter', async () => {
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page).toHaveURL(/\/learn\/[a-z-]+$/);
    for (;;) {
      const article = page.getByTestId('content.chapter.article');
      await expect(article).toBeVisible();
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
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'QUIZ');
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page).toHaveURL(/\/quizzes\/foodsaver$/i);
  });

  await walk.step('passes the foodsaver quiz and accepts the agreement', async () => {
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
      if (await results.isVisible()) {
        await walk.click(results);
        break;
      }
      await walk.click(page.getByTestId('quiz.session.next'));
      await expect(page.getByTestId('quiz.session.question')).not.toHaveAttribute('data-question', id);
    }
    await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
    const confirm = page.getByTestId('quiz.results.confirm');
    await expectDataState(page, 'quiz.results.document', 'success');
    await walk.click(confirm.getByTestId('quiz.results.agree'));
    await walk.click(confirm.getByTestId('quiz.results.confirmButton'));
    await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
    const steps = (await journey()).steps.filter((step) => ['QUIZ', 'AGREEMENT'].includes(step.key));
    expect(steps.map((step) => step.status)).toEqual(['DONE', 'DONE']);
  });

  await walk.step('goes on from the quiz success to the EinAb list', async () => {
    // round 3: the success state carries the next step of the journey
    await walk.click(page.getByTestId('quiz.results.toJourney'));
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'INTRO_PICKUPS');
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page).toHaveURL(/\/intro-pickups$/);
    await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'success');
  });

  await walk.step('books the first free EinAb', async () => {
    const card = page.locator('[data-testid="introPickups.bookable.card"]').filter({ has: page.getByTestId('introPickups.bookable.book') }).first();
    await card.scrollIntoViewIfNeeded();
    await walk.click(card.getByTestId('introPickups.bookable.book'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
    await expect(page.getByTestId('introPickups.booked.private').first()).toBeVisible();
    const step = (await journey()).steps.find((s) => s.key === 'INTRO_PICKUPS')!;
    expect(step.progress?.booked).toBe(1);
  });

  await walk.step('finds the booking under "my EinAbs"', async () => {
    await walk.click(page.getByTestId('introPickups.bookable.toMyBookings'));
    await expect(page).toHaveURL(/\/me\/intro-pickups$/);
    await expect(page.getByTestId('introPickups.my.upcomingCard')).toHaveCount(1);
  });

  // round 3: 50–52 clicks (desktop / phone); the quiz draws its questions at random, so its clicks vary a little
  walk.finish({ steps: 8, clicks: 56, fields: 5 });
});
