import { acceptPendingConsents, expectPdfContains } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { USERS } from '../../fixtures/flows/verification';
import { Walk } from '../../fixtures/persona';

/**
 * Marco (sleepy@local) finds his passport expired and asks for a new one. Ambassador Reto (bot@local) opens the
 * passports of Zürich Stadt, takes the open requests (Sarah's and Marco's) plus Jonas, creates the passports as one A4 PDF and downloads it.
 * Marco's passport is valid again: he downloads it, the wallets say they are not configured, and his passport history
 * shows Reto's issue, which Reto also sees in Marco's verification history. Rules behind it: PassportServiceRulesTest
 * (403 NOT_VERIFIED, 409 PASSPORT_NOT_VALID, wallet availability), PassportValidityTest.
 */
test.use({ role: 'bot' });

test('ambassador fulfils a passport request and batch-creates passports, the member downloads his @Ve3 @A8 @D10.3', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'ambassador');
  const marco = await as('sleepy');
  await acceptPendingConsents(apiOf(marco));
  const status = marco.getByTestId('verification.passport.status');

  // Marco's passport has expired: no download until it is renewed; he asks the ambassadors for a new one
  await marco.goto('/me/passport');
  await expect(status).toHaveAttribute('data-valid', 'false');
  await expect(marco.getByTestId('verification.passport.invalidTag')).toBeVisible();
  await expect(marco.getByTestId('verification.passport.download')).toHaveCount(0);
  await checkpoint(marco, 'my-passport-expired');
  await marco.getByTestId('verification.passport.request').click();
  await expect(status).toHaveAttribute('data-pending', 'true');
  await expect(marco.getByTestId('verification.passport.pendingTag')).toBeVisible();

  const request = page.locator(`[data-testid="verification.passports.request"][data-user="${USERS.sleepy}"]`);
  const member = (id: string) => page.locator(`[data-testid="verification.passports.member"][data-user="${id}"]`);

  await walk.step('opens the passports of his region', async () => {
    await page.goto('/regions/home/passports');
    await expect(page.getByTestId('verification.passports.membersState')).toHaveAttribute('data-state', 'success');
    await expect(request).toHaveAttribute('data-fulfilled', 'false');
    await checkpoint(page, 'region-passports');
  });

  await walk.step('takes the open requests and Jonas and creates the passports', async () => {
    await walk.click(page.getByTestId('verification.passports.selectRequests'));
    // the seed has Sarah's request open too
    await expect(page.getByTestId('verification.passports.selectedCount')).toContainText('2');
    await walk.click(member(USERS.jumper).locator('.p-checkbox'));
    await expect(page.getByTestId('verification.passports.selectedCount')).toContainText('3');
    await walk.click(page.getByTestId('verification.passports.create'));
    await expect(page.getByTestId('verification.passports.job')).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 });
    // fulfilled requests leave the list
    await expect(request).toHaveCount(0);
    await checkpoint(page, 'passports-created');
  });

  await walk.step('downloads the PDF', async () => {
    const download = page.waitForEvent('download');
    await walk.click(page.getByTestId('verification.passports.downloadResult'));
    await expectPdfContains(await download, ['Sarah', 'Marco', 'Jonas']);
    await walk.click(page.getByTestId('verification.passports.includeFulfilled'));
    await expect(request).toHaveAttribute('data-fulfilled', 'true');
  });

  // Marco's passport is valid again, the request is done; he downloads it
  await marco.reload();
  await expect(status).toHaveAttribute('data-valid', 'true');
  await expect(status).toHaveAttribute('data-pending', 'false');
  await expect(marco.getByTestId('verification.passport.validUntil')).toBeVisible();
  await expect(marco.getByTestId('verification.passport.walletsNotConfigured')).toBeVisible();
  await expect(marco.getByTestId('verification.passport.apple')).toHaveAttribute('data-availability', 'NOT_CONFIGURED');
  await expect(marco.getByTestId('verification.passport.apple').getByRole('button')).toBeDisabled();
  await expect(marco.getByTestId('verification.passport.google').getByRole('button')).toBeDisabled();
  const issued = '[data-action="USER.PASSPORT_GENERATED_BY_AMBASSADOR"]';
  await expect(marco.locator(`[data-testid^="verification.passport.history"]${issued}`).first()).toBeVisible();
  await checkpoint(marco, 'my-passport-valid');
  const own = marco.waitForEvent('download');
  await marco.getByTestId('verification.passport.download').click();
  await expectPdfContains(await own, ['Marco', 'Bernasconi']);

  await walk.step('sees the issue in Marco\'s verification history', async () => {
    await page.goto(`/users/${USERS.sleepy}/verification-history`);
    await expect(page.getByTestId('verification.history.userState')).toHaveAttribute('data-state', 'success');
    await expect(page.locator(`[data-testid^="verification.history.passport"]${issued}`).first()).toBeVisible();
    await checkpoint(page, 'verification-history-passport');
  });

  walk.finish({ steps: 4, clicks: 5, fields: 0 });
});
