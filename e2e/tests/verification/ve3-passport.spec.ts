import { type ApiClient, expect, expectPdfContains, test } from '../../fixtures';
import { USERS, ZH } from './_verification';

/** Ve3: own passport (PDF, request, wallets, history) and the ambassadors' batch passports with progress job. */
test.use({ seedReset: 'test' });

interface JobProgress {
  status: string;
  resultAvailable: boolean;
}

/** Creates passports through the API as an ambassador and waits for the job. */
async function createPassports(ambassador: ApiClient, userIds: string[], renew = true): Promise<string> {
  const job = await ambassador.postJson<{ jobId: string }>(`/api/regions/${ZH}/passports`, {
    userIds,
    createPdf: true,
    renew,
    paperSize: 'A4',
    notifyUsers: false,
  });
  await expect
    .poll(async () => (await ambassador.getJson<JobProgress>(`/api/jobs/${job.jobId}`)).status, { timeout: 30_000 })
    .toBe('DONE');
  return job.jobId;
}

test('Ve3 verified user downloads passport PDF containing name and number @Ve3 @page', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/me/passport');
  const status = page.getByTestId('verification.passport.status');
  await expect(status).toHaveAttribute('data-valid', 'true');
  await expect(page.getByTestId('verification.passport.validUntil')).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByTestId('verification.passport.download').click();
  await expectPdfContains(await download, ['Sarah', 'Müller', '100006']);
});

test('Ve3 unverified user sees locked state @Ve3', async ({ as, apiOf }) => {
  const page = await as('einab1');
  await page.goto('/me/passport');
  await expect(page.getByTestId('verification.passport.locked')).toBeVisible();
  await expect(page.getByTestId('verification.passport.download')).toHaveCount(0);
  expect((await apiOf(page).get('/api/users/me/passport.pdf')).status()).toBe(403);
});

test('Ve3 expired passport cannot be downloaded until renewed @Ve3', async ({ as, apiOf, api }) => {
  const page = await as('sleepy');
  await page.goto('/me/passport');
  const status = page.getByTestId('verification.passport.status');
  await expect(status).toHaveAttribute('data-valid', 'false');
  await expect(page.getByTestId('verification.passport.invalidTag')).toBeVisible();
  await expect(page.getByTestId('verification.passport.download')).toHaveCount(0);
  const blocked = await apiOf(page).get('/api/users/me/passport.pdf');
  expect(blocked.status()).toBe(409);
  expect((await blocked.json()).code).toBe('PASSPORT_NOT_VALID');

  await page.getByTestId('verification.passport.request').click();
  await expect(status).toHaveAttribute('data-pending', 'true');

  await api.loginAs('bot');
  await createPassports(api, [USERS.sleepy]);
  await page.reload();
  await expect(status).toHaveAttribute('data-valid', 'true');
  await expect(page.getByTestId('verification.passport.download')).toBeVisible();
});

test('Ve3 ambassador batch renew for region with progress and A4 PDF @Ve3 @EA20', async ({ as }) => {
  const page = await as('bot');
  await page.goto('/regions/home/passports');
  await expect(page.getByTestId('verification.passports.membersState')).toHaveAttribute('data-state', 'success');
  await page.locator(`[data-testid="verification.passports.member"][data-user="${USERS.foodsaver}"] .p-checkbox`).click();
  await page.locator(`[data-testid="verification.passports.member"][data-user="${USERS.jumper}"] .p-checkbox`).click();
  await expect(page.getByTestId('verification.passports.selectedCount')).toContainText('2');
  await page.getByTestId('verification.passports.create').click();
  await expect(page.getByTestId('verification.passports.job')).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 });
  const download = page.waitForEvent('download');
  await page.getByTestId('verification.passports.downloadResult').click();
  await expectPdfContains(await download, ['Sarah', 'Jonas']);
});

test('Ve3 passport request appears for ambassadors and is fulfilled @Ve3', async ({ as }) => {
  const jumper = await as('jumper');
  await jumper.goto('/me/passport');
  await jumper.getByTestId('verification.passport.request').click();
  await expect(jumper.getByTestId('verification.passport.pendingTag')).toBeVisible();

  const page = await as('bot');
  await page.goto('/regions/home/passports');
  const request = page.locator(`[data-testid="verification.passports.request"][data-user="${USERS.jumper}"]`);
  await expect(request).toHaveAttribute('data-fulfilled', 'false');
  await page.getByTestId('verification.passports.selectRequests').click();
  await page.getByTestId('verification.passports.create').click();
  await expect(page.getByTestId('verification.passports.job')).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 });
  await expect(request).toHaveCount(0);
  await page.getByTestId('verification.passports.includeFulfilled').click();
  await expect(request).toHaveAttribute('data-fulfilled', 'true');

  await jumper.reload();
  await expect(jumper.getByTestId('verification.passport.status')).toHaveAttribute('data-pending', 'false');
});

test('Ve3 wallets show not configured state @Ve3', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  await page.goto('/me/passport');
  await expect(page.getByTestId('verification.passport.walletsNotConfigured')).toBeVisible();
  await expect(page.getByTestId('verification.passport.apple')).toHaveAttribute('data-availability', 'NOT_CONFIGURED');
  await expect(page.getByTestId('verification.passport.apple').getByRole('button')).toBeDisabled();
  await expect(page.getByTestId('verification.passport.google').getByRole('button')).toBeDisabled();
  const google = await apiOf(page).get('/api/users/me/wallets/google');
  expect(google.status()).toBe(503);
  expect((await google.json()).code).toBe('WALLET_NOT_CONFIGURED');
});

test('Ve3 passport history visible to user and ambassador @Ve3 @A8', async ({ as, apiOf }) => {
  const bot = await as('bot');
  await createPassports(apiOf(bot), [USERS.foodsaver]);

  const page = await as('foodsaver');
  await page.goto('/me/passport');
  const own = page.locator('[data-testid^="verification.passport.history"][data-action="USER.PASSPORT_GENERATED_BY_AMBASSADOR"]');
  await expect(own.first()).toBeVisible();

  await bot.goto(`/users/${USERS.foodsaver}/verification-history`);
  const seen = bot.locator('[data-testid^="verification.history.passport"][data-action="USER.PASSPORT_GENERATED_BY_AMBASSADOR"]');
  await expect(seen.first()).toBeVisible();
});
