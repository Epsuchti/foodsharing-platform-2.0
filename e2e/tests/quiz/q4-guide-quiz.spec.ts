import { expect, test } from '../../fixtures';
import { ZH } from '../intro-pickups/_intro';
import { acceptPrivacy, playSession, solutionKey } from './_quiz';

/**
 * Q4 (decision 168): the EinAb guide quiz opens after the home region's pickup threshold (default 25), the journey's
 * GUIDE track shows the progress, and a pass waits for the ambassadors' release (Ready-for-release list).
 */
test.use({ seedReset: 'test' });

test('Q4 below the pickup threshold the guide track is locked and the quiz hidden @Q4', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  await page.goto('/onboarding');
  await expect(page.getByTestId('onboarding.journey.state')).toHaveAttribute('data-state', 'success');
  const track = page.locator('[data-testid="onboarding.journey.track"][data-track="GUIDE"]');
  await expect(track.locator('li[data-status]')).toHaveAttribute('data-status', 'LOCKED');
  // Sarah has 24 confirmed past pickups, Zürich asks for 25
  await expect(track.getByTestId('onboarding.journey.trackBlocked')).toContainText('24/25');

  await page.goto('/quizzes');
  await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
  await expect(page.locator('[data-testid="quiz.list.card"][data-kind="EINAB_GUIDE"]')).toHaveCount(0);
  const refused = await apiOf(page).fetch('POST', '/api/quizzes/EINAB_GUIDE/sessions', { timed: false });
  expect(refused.status()).toBe(403);
  expect((await refused.json()).code).toBe('QUIZ_NOT_ALLOWED');
});

test('Q4 from the region threshold the quiz shows and a pass waits for the release @Q4', async ({ as, apiOf }) => {
  // the ambassador lowers Zürich's threshold to Sarah's 24 pickups
  const bot = await as('bot');
  const botApi = apiOf(bot);
  const settings = await botApi.getJson<{ introPickups: Record<string, unknown> }>(`/api/regions/${ZH}/settings`);
  expect(settings.introPickups.guideQuizMinPickups).toBe(25);
  await botApi.patchJson(`/api/regions/${ZH}/settings`, { introPickups: { ...settings.introPickups, guideQuizMinPickups: 24 } });

  const page = await as('foodsaver');
  const api = apiOf(page);
  await page.goto('/quizzes');
  await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
  await expect(page.locator('[data-testid="quiz.list.card"][data-kind="EINAB_GUIDE"]')).toBeVisible();

  await acceptPrivacy(api);
  const key = await solutionKey(apiOf(await as('orga')), 'EINAB_GUIDE');
  await playSession(api, 'EINAB_GUIDE', { correct: true, key });

  await page.goto('/onboarding');
  const track = page.locator('[data-testid="onboarding.journey.track"][data-track="GUIDE"]');
  await expect(track.locator('li[data-status]')).toHaveAttribute('data-status', 'WAITING_FOR_OTHERS');
  await expect(track.getByTestId('onboarding.journey.trackWaiting')).toBeVisible();

  // Sarah is now ready for release on the guide page
  await bot.goto(`/regions/${ZH}/intro-pickups/admin?tab=guides`);
  await expect(bot.getByTestId('introPickups.admin.candidatesState')).toHaveAttribute('data-state', 'success');
  await expect(bot.getByTestId('introPickups.admin.candidate').filter({ hasText: 'Sarah' })).toBeVisible();
});
