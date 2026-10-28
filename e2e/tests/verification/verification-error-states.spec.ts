import { expect, injectApiError, test } from '../../fixtures';
import { USERS, ZH } from './_verification';

/** Error, retry, forbidden and not-found states of the verification and passport pages (DataState + mutations). */
test.use({ seedReset: 'test' });

test('verification: queue error with retry, verify conflict shows a toast @Ve2', async ({ as }) => {
  const page = await as('bot');
  await injectApiError(page, { url: `**/api/regions/${ZH}/verification/queue**`, status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto(`/regions/${ZH}/verification`);
  const state = page.getByTestId('verification.queue.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('verification.queue.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');

  await page.route(`**/api/users/${USERS.pending}/verification`, (route) =>
    route.fulfill({ status: 409, json: { status: 409, code: 'ALREADY_VERIFIED', message: 'x', retryable: false } }),
  );
  const card = page.locator(`[data-testid="verification.queue.card"][data-user="${USERS.pending}"]`);
  await card.getByTestId('verification.queue.verify').click();
  await page.getByTestId('verification.verifyDialog.message').fill('Willkommen im Team, alles bestens!');
  await page.getByTestId('verification.verifyDialog.confirm').click();
  await expect(page.getByTestId('shell.toast')).toBeVisible();
  await expect(card).toHaveAttribute('data-verified', 'false');
});

test('verification: passport status error with retry, region passports forbidden for members @Ve3', async ({ as }) => {
  const page = await as('foodsaver');
  await injectApiError(page, { url: '**/api/users/me/passport', status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto('/me/passport');
  const state = page.getByTestId('verification.passport.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('verification.passport.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');

  await page.goto(`/regions/${ZH}/passports`);
  await expect(page.getByTestId('verification.passports.requestsState')).toHaveAttribute('data-state', 'forbidden');
});

test('verification: history of an unknown user is not found, member sees forbidden @A8', async ({ as }) => {
  const page = await as('bot');
  await page.goto('/users/00000001-0001-4000-8000-000000000999/verification-history');
  await expect(page.getByTestId('verification.history.userState')).toHaveAttribute('data-state', 'notFound');

  const member = await as('foodsaver');
  await member.goto(`/users/${USERS.jumper}/verification-history`);
  await expect(member.getByTestId('verification.history.identityState')).toHaveAttribute('data-state', 'forbidden');
});
