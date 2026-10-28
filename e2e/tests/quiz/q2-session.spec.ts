import { ApiClient, acceptPendingConsents, expect, test } from '../../fixtures';
import { SEED_NOW } from '../../support/env';
import { acceptPrivacy, playInBrowser, playSession, solutionKey } from './_quiz';

/** Q2: quiz sessions, results, confirmation, wait / disqualification, hygiene expiry, practice mode; A8 history. */

test.use({ seedReset: 'test' });

const QUIZ_USER = '00000001-0001-4000-8000-000000000003';
const plusDays = (days: number) => new Date(Date.parse(SEED_NOW) + days * 86_400_000).toISOString();

async function role(api: ApiClient): Promise<string> {
  const session = await api.getJson<{ user: { role: string } }>('/api/auth/session');
  return session.user.role;
}

test('Q2 untimed foodsaver quiz pass and confirm raises role @Q2 @page', async ({ as, apiOf, api }) => {
  const key = await solutionKey(await api.loginAs('orga').then(() => api), 'FOODSAVER');
  const page = await as('newbie');
  const own = apiOf(page);
  await acceptPrivacy(own);

  await page.goto('/quizzes/foodsaver');
  await expect(page.getByTestId('quiz.intro.failed')).toBeVisible();
  await page.getByTestId('quiz.intro.untimed').click();
  await page.getByTestId('quiz.intro.startButton').click();
  await expect(page).toHaveURL(/\/quizzes\/foodsaver\/session$/);
  await expect(page.getByTestId('quiz.session.progress')).toContainText('1');
  await expect(page.getByTestId('quiz.session.countdown')).toHaveCount(0);

  const answered = await playInBrowser(page, key);
  expect(answered).toBe(10);

  await expect(page).toHaveURL(/\/quizzes\/foodsaver\/results$/);
  await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
  await expect(page.getByTestId('quiz.results.question')).toHaveCount(10);
  const confirm = page.getByTestId('quiz.results.confirm');
  await expect(confirm).toBeVisible();
  await expect(confirm.getByTestId('quiz.results.confirmButton').locator('button')).toBeDisabled();
  await confirm.getByTestId('quiz.results.agree').click();
  await confirm.getByTestId('quiz.results.confirmButton').click();
  await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
  expect(await role(own)).toBe('FOODSAVER');

  const consents = await own.getJson<{ kind: string; pending: boolean; acceptedVersion?: string }[]>('/api/legal/consents');
  expect(consents.find((c) => c.kind === 'LEGAL_AGREEMENT')?.acceptedVersion).toBeTruthy();

  await page.goto('/quizzes');
  await expect(
    page.locator('[data-testid="quiz.list.card"][data-kind="FOODSAVER"]').getByTestId('quiz.list.status'),
  ).toHaveAttribute('data-phase', 'passed');
});

test('Q2 timed question times out with fixed clock @Q2', async ({ as, apiOf, dev }) => {
  const page = await as('newbie');
  await acceptPrivacy(apiOf(page));
  await page.goto('/quizzes/foodsaver');
  await page.getByTestId('quiz.intro.timed').click();
  await page.getByTestId('quiz.intro.startButton').click();
  const countdown = page.getByTestId('quiz.session.countdown');
  await expect(countdown).toBeVisible();
  await expect(page.getByTestId('quiz.session.timedOut')).toHaveCount(0);

  // the server clock moves on; the browser clock is irrelevant
  await dev.setClock(plusDays(1 / 24));
  await page.reload();
  await expect(page.getByTestId('quiz.session.timedOut')).toBeVisible();
  await expect(page.getByTestId('quiz.session.answer').first()).toBeDisabled();
  await page.getByTestId('quiz.session.submit').click();
  await expect(page.getByTestId('quiz.session.answeredLate')).toBeVisible();
  await expect(page.getByTestId('quiz.session.verdict')).toContainText(/\d/);
});

test('Q2 third failure imposes 30 day wait, fourth allowed, fifth disqualifies @Q2', async ({ as, apiOf, dev }) => {
  // profile@local failed twice already (seed)
  const page = await as('profile');
  const own = apiOf(page);
  await acceptPrivacy(own);
  await playSession(own, 'FOODSAVER', { correct: false });

  await page.goto('/quizzes/foodsaver/results');
  await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'FAILED');
  await expect(page.getByTestId('quiz.results.wait')).toContainText('30');
  await page.goto('/quizzes/foodsaver');
  await expect(page.getByTestId('quiz.intro.wait')).toContainText('30');
  await expect(page.getByTestId('quiz.intro.start')).toHaveCount(0);
  const refused = await own.fetch('POST', '/api/quizzes/FOODSAVER/sessions', { timed: false });
  expect(refused.status()).toBe(409);

  await dev.setClock(plusDays(31));
  await page.reload();
  await expect(page.getByTestId('quiz.intro.nextFailure')).toBeVisible();
  await playSession(own, 'FOODSAVER', { correct: false });

  await page.goto('/quizzes/foodsaver');
  await expect(page.getByTestId('quiz.intro.disqualified')).toBeVisible();
  await expect(page.getByTestId('quiz.intro.start')).toHaveCount(0);
  await page.goto('/quizzes');
  await expect(
    page.locator('[data-testid="quiz.list.card"][data-kind="FOODSAVER"]').getByTestId('quiz.list.status'),
  ).toHaveAttribute('data-phase', 'disqualified');
  const fifth = await own.fetch('POST', '/api/quizzes/FOODSAVER/sessions', { timed: false });
  expect(fifth.status()).toBe(409);
});

test('Q2 hygiene quiz expiry warning at 90 days @Q2', async ({ as }) => {
  // foodsaver@local passed the hygiene quiz on 2026-01-06: valid until 2027-01-06, 66 days left at SEED_NOW (rounded up)
  const page = await as('foodsaver');
  await page.goto('/quizzes');
  const hygiene = page.locator('[data-testid="quiz.list.card"][data-kind="HYGIENE"]');
  await expect(hygiene.getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'expiring');
  await expect(hygiene.getByTestId('quiz.list.status')).toContainText('66');
  await hygiene.getByTestId('quiz.list.open').click();
  await expect(page.getByTestId('quiz.intro.expiring')).toContainText('66');
  await expect(page.getByTestId('quiz.intro.start')).toBeVisible();
});

test('Q2 practice mode has no effect on role @Q2', async ({ as, apiOf, api }) => {
  const key = await solutionKey(await api.loginAs('orga').then(() => api), 'FOODSAVER');
  const page = await as('newbie');
  const own = apiOf(page);
  await acceptPrivacy(own);
  await page.goto('/quizzes');
  await page.getByTestId('quiz.list.practice').click();
  await expect(page).toHaveURL(/practice=true/);
  await expect(page.getByTestId('quiz.list.status')).toHaveCount(0);
  await page.locator('[data-testid="quiz.list.card"][data-kind="FOODSAVER"]').getByTestId('quiz.list.open').click();
  await expect(page.getByTestId('quiz.intro.practiceBanner')).toBeVisible();
  await page.getByTestId('quiz.intro.startButton').click();
  await playInBrowser(page, key);

  await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
  await expect(page.getByTestId('quiz.results.practice')).toBeVisible();
  await expect(page.getByTestId('quiz.results.confirm')).toHaveCount(0);
  expect(await role(own)).toBe('FOODSHARER');
  const status = await own.getJson<{ lastSessionStatus?: string; passedAt?: string }>('/api/quizzes/FOODSAVER/status');
  expect(status.lastSessionStatus).toBe('FAILED');
  expect(status.passedAt ?? null).toBeNull();
});

test('Q2 store manager confirm requires confidentiality consent @Q2', async ({ as, apiOf, api }) => {
  const key = await solutionKey(await api.loginAs('orga').then(() => api), 'STORE_MANAGER');
  const page = await as('foodsaver');
  const own = apiOf(page);
  await playSession(own, 'STORE_MANAGER', { correct: true, key });

  const early = await own.fetch('POST', '/api/quizzes/STORE_MANAGER/confirm');
  expect(early.status()).toBe(409);
  expect((await early.json()).code).toBe('CONSENT_REQUIRED');

  await page.goto('/quizzes');
  const card = page.locator('[data-testid="quiz.list.card"][data-kind="STORE_MANAGER"]');
  await expect(card.getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'confirmPending');
  await card.getByTestId('quiz.list.open').click();
  await expect(page).toHaveURL(/\/quizzes\/store-manager\/results$/);
  const confirm = page.getByTestId('quiz.results.confirm');
  await expect(confirm.getByTestId('quiz.results.document')).toHaveAttribute('data-state', 'success');
  await expect(confirm.getByTestId('quiz.results.confirmButton').locator('button')).toBeDisabled();
  await confirm.getByTestId('quiz.results.agree').click();
  await confirm.getByTestId('quiz.results.confirmButton').click();
  await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
  expect(await role(own)).toBe('STORE_MANAGER');
});

test('Q2 quiz session history visible to user and ambassador @Q2 @A8', async ({ as, apiOf }) => {
  const user = await as('quiz');
  await acceptPendingConsents(apiOf(user));
  await user.goto('/quizzes');
  await user.getByTestId('quiz.list.history').click();
  await expect(user).toHaveURL(/\/quizzes\/history$/);
  const rows = user.getByTestId('quiz.history.row');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toHaveAttribute('data-status', 'PASSED');
  await expect(rows.first()).toHaveAttribute('data-kind', 'FOODSAVER');

  const ambassador = await as('bot');
  await ambassador.goto(`/users/${QUIZ_USER}`);
  await ambassador.getByTestId('usersAdmin.user.quizHistory').click();
  await expect(ambassador).toHaveURL(new RegExp(`/quizzes/history/${QUIZ_USER}$`));
  await expect(ambassador.getByTestId('quiz.history.row')).toHaveCount(1);

  const stranger = await as('jumper');
  await stranger.goto(`/quizzes/history/${QUIZ_USER}`);
  await expect(stranger.getByTestId('quiz.history.state')).toHaveAttribute('data-state', 'forbidden');
});
