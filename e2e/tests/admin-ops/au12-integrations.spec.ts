import { type ApiClient, expect, expectErrorState, injectApiError, test } from '../../fixtures';

/** AU12 (remainder): integration status page for ORGA, probe/test, breaker opened by an outage and the ORGA bell. */
test.use({ seedReset: 'test' });

/** Breakers live in memory (a seed reset keeps them): a successful probe closes them again. */
async function closeBreakers(api: ApiClient): Promise<void> {
  for (const integration of ['SMTP', 'WEB_PUSH', 'TELEGRAM', 'GEOCODING']) {
    const response = await api.fetch('POST', `/api/admin/integrations/${integration}/probe`);
    expect(response.ok(), await response.text()).toBe(true);
  }
}

test('AU12 integration status page lists channels in log mode, probe and test message @AU12 @page', async ({ as, apiOf }) => {
  const page = await as('orga');
  await closeBreakers(apiOf(page));
  await page.goto('/admin/integrations');
  await expect(page.getByTestId('adminOps.integrations.state')).toHaveAttribute('data-state', 'success');
  for (const integration of ['SMTP', 'WEB_PUSH', 'TELEGRAM']) {
    const card = page.locator(`[data-testid="adminOps.integrations.card"][data-integration="${integration}"]`);
    await expect(card, integration).toHaveAttribute('data-mode', 'LOG');
    await expect(card, integration).toHaveAttribute('data-state', 'CLOSED');
  }

  const smtp = page.locator('[data-testid="adminOps.integrations.card"][data-integration="SMTP"]');
  await smtp.getByTestId('adminOps.integrations.probe').click();
  await expect(page.locator('.p-toast-message-success')).toBeVisible();
  await smtp.getByTestId('adminOps.integrations.test').click();
  await expect(page.locator('.p-toast-message-success')).toHaveCount(2);
});

test('AU12 open breaker shows state and bell to orga @AU12', async ({ as, api, apiOf, notifications }) => {
  const page = await as('orga');
  await closeBreakers(apiOf(page));
  const before = (await notifications.bells('orga', 'INTEGRATION_DOWN')).length;
  const response = await api.fetch('POST', '/api/dev/integrations/GEOCODING/fail');
  expect(response.ok(), await response.text()).toBe(true);

  await page.goto('/admin/integrations');
  const card = page.locator('[data-testid="adminOps.integrations.card"][data-integration="GEOCODING"]');
  await expect(card).toHaveAttribute('data-state', 'OPEN');
  await expect(card.getByTestId('adminOps.integrations.downSince')).toBeVisible();
  await expect.poll(async () => (await notifications.bells('orga', 'INTEGRATION_DOWN')).length).toBe(before + 1);
});

test('AU12 admin operation pages are ORGA only and show error states @AU12 @D6.3 @N6', async ({ as }) => {
  const foodsaver = await as('foodsaver');
  for (const path of ['/admin/integrations', '/admin/jobs', '/admin/mail-outbox']) {
    await foodsaver.goto(path);
    await expect(foodsaver.getByTestId('adminOps.integrations.state').or(foodsaver.getByTestId('adminOps.jobs.state'))).toHaveCount(0);
  }

  const page = await as('orga');
  await injectApiError(page, { url: '**/api/admin/integrations', status: 500 });
  await page.goto('/admin/integrations');
  await expectErrorState(page, 'adminOps.integrations.state');
  await injectApiError(page, { url: '**/api/admin/jobs', status: 503 });
  await page.goto('/admin/jobs');
  await expectErrorState(page, 'adminOps.jobs.state');
  await injectApiError(page, { url: /\/api\/admin\/mail-outbox(\?|$)/, status: 500 });
  await page.goto('/admin/mail-outbox');
  await expectErrorState(page, 'adminOps.outbox.state');
});
