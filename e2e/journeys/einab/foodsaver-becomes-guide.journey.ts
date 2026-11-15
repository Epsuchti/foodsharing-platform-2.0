import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZH } from '../../fixtures/flows/einab';
import { acceptPrivacy, playInBrowser, solutionKey } from '../../fixtures/flows/quiz';
import { Walk } from '../../fixtures/persona';

/**
 * Q4 (decision 168): foodsaver Sarah (foodsaver@local, 24 confirmed past pickups) wants to give intro pickups. Her
 * journey page shows the guide track locked at 24/25 and the quiz list has no guide quiz. Zürich's ambassador Reto
 * lowers the threshold to 24 in the region settings (EA22); now Sarah takes the EinAb guide quiz in the browser, passes
 * and waits for the release. Reto finds her under "Ready for release" and releases her; Sarah has a guide dashboard.
 * Rules behind it: GuideQuizGateTest (threshold), IntroPickupAdminServiceGuideTest (who may release whom).
 */
test.use({ role: 'foodsaver' });

test('foodsaver passes the guide quiz after the region threshold and is released as a guide @Q4 @EA17 @EA22', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const track = page.locator('[data-testid="onboarding.journey.track"][data-track="GUIDE"]');
  const guideQuiz = page.locator('[data-testid="quiz.list.card"][data-kind="EINAB_GUIDE"]');
  const reto = await as('bot');

  await walk.step('sees the guide track locked', async () => {
    await page.goto('/onboarding');
    await expect(page.getByTestId('onboarding.journey.state')).toHaveAttribute('data-state', 'success');
    await expect(track.locator('li[data-status]')).toHaveAttribute('data-status', 'LOCKED');
    await expect(track.getByTestId('onboarding.journey.trackBlocked')).toContainText('24/25');
    await checkpoint(page, 'guide-track-locked');
    await walk.nav('quizzes');
    await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
    await expect(guideQuiz).toHaveCount(0);
  });

  await test.step('the ambassador lowers the threshold to 24 pickups', async () => {
    await reto.goto(`/regions/${ZH}/settings`);
    await expect(reto.getByTestId('regions.settings.state')).toHaveAttribute('data-state', 'success');
    const field = reto.locator('#set-guide-quiz');
    await expect(field).toHaveValue('25');
    await field.fill('24');
    await field.blur();
    await checkpoint(reto, 'threshold-setting');
    await reto.getByTestId('regions.settings.save').click();
    await expect(reto.getByTestId('shell.toast')).toBeVisible();
  });

  await walk.step('takes the EinAb guide quiz', async () => {
    await acceptPrivacy(apiOf(page));
    const key = await solutionKey(apiOf(await as('orga')), 'EINAB_GUIDE');
    await page.reload();
    await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
    await walk.click(guideQuiz.getByTestId('quiz.list.open'));
    await walk.click(page.getByTestId('quiz.intro.untimed'));
    await walk.click(page.getByTestId('quiz.intro.startButton'));
    await expect(page).toHaveURL(/\/session$/);
    const answered = await playInBrowser(page, key);
    // per question "submit" and "next" ("results" after the last one); the ticked answers vary with the shuffle
    walk.count(answered * 2);
    await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
    // the server shuffles questions and answers
    await checkpoint(page, 'quiz-passed', { mask: [page.getByTestId('quiz.results.question')] });
  });

  await walk.step('waits for the release', async () => {
    await page.goto('/onboarding');
    await expect(track.locator('li[data-status]')).toHaveAttribute('data-status', 'WAITING_FOR_OTHERS');
    await expect(track.getByTestId('onboarding.journey.trackWaiting')).toBeVisible();
    await checkpoint(page, 'guide-track-waiting');
  });

  await test.step('the ambassador releases her', async () => {
    await reto.goto(`/regions/${ZH}/intro-pickups/admin?tab=guides`);
    await expect(reto.getByTestId('introPickups.admin.candidatesState')).toHaveAttribute('data-state', 'success');
    const candidate = reto.getByTestId('introPickups.admin.candidate').filter({ hasText: 'Sarah' });
    await expect(candidate).toBeVisible();
    await checkpoint(reto, 'ready-for-release');
    await candidate.getByTestId('introPickups.admin.candidateRelease').click();
    await expect(reto.getByTestId('introPickups.admin.releaseDialog.confirm')).toBeVisible();
    await checkpoint(reto, 'release-dialog');
    await reto.getByTestId('introPickups.admin.releaseDialog.confirm').click();
    await expect(candidate).toHaveCount(0);
    const sarah = reto.getByTestId('introPickups.admin.guide').filter({ hasText: 'Sarah' });
    await expect(sarah).toHaveAttribute('data-active', 'true');
    await expect(sarah.getByTestId('introPickups.admin.quizTag')).toBeVisible();
  });

  await walk.step('opens her guide dashboard', async () => {
    await page.goto('/dashboard');
    await walk.nav('guide');
    await expect(page).toHaveURL(/\/guide$/);
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', /success|empty/);
    await checkpoint(page, 'first-guide-dashboard');
  });

  walk.finish({ steps: 4, clicks: 17, fields: 0 });
});
