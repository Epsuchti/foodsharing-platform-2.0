import { expect, expectDataState, expectErrorState, expectSkeleton, injectApiError, test } from '../../fixtures';

/** Loading, error + retry and not-found states of the legal document pages (M2 T20). */

test('legal document: loading skeleton, then error with retry recovers @Ve1', async ({ page }) => {
  const slow = await injectApiError(page, { url: /\/api\/public\/legal\/PRIVACY_POLICY/, delayMs: 30_000 });
  await page.goto('/legal/privacy-policy');
  await expectSkeleton(page, 'legal.document.state');
  await slow.remove();

  const failure = await injectApiError(page, { url: /\/api\/public\/legal\/PRIVACY_POLICY/, status: 500 });
  await page.goto('/legal/privacy-policy');
  const { retry } = await expectErrorState(page, 'legal.document.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'legal.document.state', 'success');
  await page.getByTestId('legal.document.link.LEGAL_AGREEMENT').click();
  await expect(page).toHaveURL(/\/legal\/legal-agreement$/);
  await expect(page.getByTestId('legal.document.view')).toHaveAttribute('data-kind', 'LEGAL_AGREEMENT');
});

test('unknown legal documents are not found @Ve1', async ({ page }) => {
  await page.goto('/legal/terms-of-war');
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('legal versions: error with retry, publish conflict keeps the form @Ve1', async ({ page }) => {
    const failure = await injectApiError(page, { url: /\/api\/legal\/PRIVACY_POLICY\/versions/, method: 'GET', status: 500 });
    await page.goto('/admin/legal');
    const { retry } = await expectErrorState(page, 'legal.admin.versions');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'legal.admin.versions', 'success');

    // an existing version label is refused (409) and the entered text stays
    await page.getByTestId('legal.admin.version.input').fill('2026-01');
    await page.getByTestId('legal.admin.bodies.field.de').fill('Text');
    await page.getByTestId('legal.admin.publish').click();
    await page.locator('.p-confirmdialog-accept-button').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
    await expect(page.getByTestId('legal.admin.version.input')).toHaveValue('2026-01');
    await expect(page.getByTestId('legal.admin.version')).toHaveCount(1);
  });
});
