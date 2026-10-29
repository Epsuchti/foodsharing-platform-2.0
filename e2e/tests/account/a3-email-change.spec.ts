import {
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  test,
} from '../../fixtures';

/**
 * A3 email change (plan-m7 Q3, A49): settings card with the pending change, request form, the confirm link to the new
 * address (logged in) and the abort link to the old address (works logged out). Seed: profile@local (Noah Keller)
 * has a pending change to noah.keller@local.test, raw tokens `seed-email-change-confirm-profile` /
 * `seed-email-change-abort-profile` (docs/seed-ids.md).
 */
test.use({ seedReset: 'test' });

const SEED_PENDING = 'noah.keller@local.test';
const NEW_ADDRESS = 'noah.neu@local.test';

test('A3 request email change, confirm from the mail, old address can abort, history shows it @A3 @N6 @page', async ({
  page: guest,
  as,
  apiOf,
  mails,
}) => {
  const page = await as('profile');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.email.current')).toHaveText('profile@local');
  await expect(page.getByTestId('account.settings.email.pending')).toHaveAttribute('data-email', SEED_PENDING);

  // the old address aborts the seed change with the link of its notice mail, logged out
  await guest.goto('/account/email/abort?token=seed-email-change-abort-profile');
  await expect(guest.getByTestId('account.email.abort.success')).toBeVisible();
  await page.reload();
  await expectDataState(page, 'account.settings.email.pendingState', 'empty');
  // a used abort link answers with the link error, not with a retry
  await guest.reload();
  await expect(guest.getByTestId('account.email.abort.tokenError')).toBeVisible();

  // an address of another member is refused below the field
  await page.getByTestId('account.settings.email.new').fill('foodsaver@local');
  await page.getByTestId('account.settings.email.submit').click();
  await expect(page.getByTestId('account.settings.email.error')).toHaveAttribute('data-code', 'EMAIL_TAKEN');

  // request: confirmation link to the new address, notice with abort link to the old one
  await page.getByTestId('account.settings.email.new').fill(NEW_ADDRESS);
  await page.getByTestId('account.settings.email.submit').click();
  await expect(page.getByTestId('account.settings.email.pending')).toHaveAttribute('data-email', NEW_ADDRESS);
  await expect(page.getByTestId('account.settings.email.error')).toHaveCount(0);
  const confirmMail = await mails.waitForMail({ to: NEW_ADDRESS, predicate: (m) => m.links.some((l) => l.includes('/account/email/confirm')) });
  const notice = await mails.waitForMail({ to: 'profile@local', predicate: (m) => m.links.some((l) => l.includes('/account/email/abort')) });

  // confirm from the mail (logged in as the owner)
  await page.goto(mails.extractPath(confirmMail, '/account/email/confirm'));
  await expect(page.getByTestId('account.email.confirm.success')).toBeVisible();
  await page.getByTestId('account.email.confirm.continue').click();
  await expect(page.getByTestId('account.settings.email.current')).toHaveText(NEW_ADDRESS);
  await expectDataState(page, 'account.settings.email.pendingState', 'empty');

  // the notice link no longer works once the change is complete
  await guest.goto(mails.extractPath(notice, '/account/email/abort'));
  await expect(guest.getByTestId('account.email.abort.tokenError')).toBeVisible();

  // the own activity history lists request, abort and completion
  await page.goto('/me/activity');
  await expectDataState(page, 'history.myActivity.list', 'success');
  for (const action of ['EMAIL_CHANGE_REQUESTED', 'EMAIL_CHANGE_ABORTED', 'EMAIL_CHANGE_COMPLETED']) {
    await expect(page.locator(`[data-testid="history.myActivity.entry"][data-action="USER.${action}"]`).first()).toBeVisible();
  }
});

test('A3 the owner cancels the pending change in the settings @A3', async ({ as, apiOf }) => {
  const page = await as('profile');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.email.pending')).toBeVisible();
  await page.getByTestId('account.settings.email.abort').click();
  await page.locator('.p-confirmdialog-accept-button').click();
  await expectDataState(page, 'account.settings.email.pendingState', 'empty');
  // the seed confirm link is dead now
  await page.goto('/account/email/confirm?token=seed-email-change-confirm-profile');
  await expect(page.getByTestId('account.email.confirm.tokenError')).toBeVisible();
});

test('A3 email card and confirm page error states @A3', async ({ as, apiOf }) => {
  const page = await as('profile');
  await acceptPendingConsents(apiOf(page));
  // GETs are retried twice by the retry interceptor
  await injectApiError(page, { url: '/api/users/me/email', method: 'GET', status: 500, times: 3 });
  await page.goto('/account/settings');
  await expectErrorState(page, 'account.settings.email.pendingState');

  await injectApiError(page, { url: '/api/users/me/email/confirm', status: 503, times: 1 });
  await page.goto('/account/email/confirm?token=seed-email-change-confirm-profile');
  const { retry } = await expectErrorState(page, 'account.email.confirm.state');
  // retry reaches the server and completes the seed change
  await retry.click();
  await expect(page.getByTestId('account.email.confirm.success')).toBeVisible();
});
