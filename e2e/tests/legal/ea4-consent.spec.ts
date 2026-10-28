import { expect, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * EA4 (consent part): users with pending legal documents (newbie@local has not accepted the privacy policy) are held on
 * /consent until they accept; afterwards they continue where they wanted to go, and the consent is in the history.
 */

test.use({ seedReset: 'test' });

interface HistoryEntryPage {
  items: { action: string; metadata?: Record<string, unknown> }[];
}

test('EA4 pending consent gates every page and continues after accepting @EA4 @page', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await page.goto('/me/activity');
  await expect(page).toHaveURL(/\/consent\?returnUrl=%2Fme%2Factivity$/);

  const documents = page.getByTestId('legal.consent.documents');
  await expect(documents).toHaveAttribute('data-state', 'success');
  const docs = page.getByTestId('legal.consent.document');
  await expect(docs.first()).toBeVisible();
  await expect(page.locator('[data-kind="PRIVACY_POLICY"]')).toBeVisible();

  // the full text is available, accepting needs every checkbox
  await page.getByTestId('legal.consent.fullText.PRIVACY_POLICY').click();
  await expect(page.locator('[data-kind="PRIVACY_POLICY"] details fs-markdown')).not.toBeEmpty();
  const submit = page.getByTestId('legal.consent.submit').locator('button');
  await expect(submit).toBeDisabled();
  const checks = page.locator('[data-testid^="legal.consent.check."] input');
  for (let i = 0; i < (await checks.count()); i++) await checks.nth(i).check();
  await expect(submit).toBeEnabled();
  await submit.click();

  await expect(page).toHaveURL(/\/me\/activity$/);
  // no more gate
  await page.goto('/dashboard');
  await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();

  const api = apiOf(page);
  const session = await api.session();
  expect((session as { pendingConsents?: string[] }).pendingConsents ?? []).toEqual([]);
  const history = await api.getJson<HistoryEntryPage>('/api/history/me?actions=CONSENT_GIVEN');
  expect(history.items.length).toBeGreaterThan(0);
});

test('EA4 consent page shows errors with retry @EA4', async ({ as }) => {
  const page = await as('newbie');
  const failure = await injectApiError(page, { url: '/api/legal/consents', method: 'GET', status: 500 });
  await page.goto('/dashboard');
  const { retry } = await expectErrorState(page, 'legal.consent.documents');
  await failure.remove();
  await retry.click();
  await expect(page.getByTestId('legal.consent.documents')).toHaveAttribute('data-state', 'success');
});

test('EA4 users without pending consents never see the consent page @EA4', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/dashboard');
  await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
  await page.goto('/consent');
  await expect(page.getByTestId('legal.consent.documents')).toHaveAttribute('data-state', 'empty');
});
