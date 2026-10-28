import { acceptPendingConsents, expect, injectApiError, test } from '../../fixtures';

/** Error and retry states of the journey page, the profile step and the header bar (DataState + mutations). */
test.use({ seedReset: 'test' });

test('onboarding: journey error with retry, bar keeps working @D11.5.2', async ({ as, apiOf }) => {
  const page = await as('einab1');
  await acceptPendingConsents(apiOf(page));
  await injectApiError(page, { url: '**/api/onboarding/journey', status: 400, code: 'VALIDATION_FAILED', times: 2 });
  await page.goto('/onboarding');
  const state = page.getByTestId('onboarding.journey.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('onboarding.journey.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'INTRO_PICKUPS');
});

test('onboarding: profile step keeps the form on a save error @EA8', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/onboarding/profile');
  await page.getByTestId('onboarding.profile.firstName').fill('Lea');
  await page.getByTestId('onboarding.profile.lastName').fill('Brunner');
  await page.locator('#ob-phone').fill('079 123 45 67');
  await page.getByTestId('onboarding.profile.picker.postcode').fill('8004');
  await page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt').click();
  await page.route('**/api/users/me/profile', (route) =>
    route.fulfill({ status: 409, json: { status: 409, code: 'CONFLICT', message: 'x', retryable: false } }),
  );
  await page.getByTestId('onboarding.profile.save').click();
  await expect(page.getByTestId('shell.toast')).toBeVisible();
  await expect(page).toHaveURL(/\/onboarding\/profile$/);
  await expect(page.getByTestId('onboarding.profile.firstName')).toHaveValue('Lea');
});
