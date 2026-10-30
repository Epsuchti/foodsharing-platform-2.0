import {
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  regionId,
  test,
} from '../../fixtures';

/**
 * G2 mediation (plan-m7 Q8, A51): the mediation team of zuerich-stadt is its MEDIATION group (reports@local is its
 * admin, docs/seed-ids.md); a request rings the team (MEDIATION_REQUEST, link = the requester's profile).
 */
const ZURICH = regionId('zuerich-stadt');

test.use({ seedReset: 'test', role: 'foodsaver' });

test('G2 mediation page shows team and sends request @G2 @N3', async ({ page, apiOf, profiles, notifications }) => {
  await acceptPendingConsents(apiOf(page));
  const [foodsaverId, jumperId, reportsId] = await Promise.all([
    profiles.userId('foodsaver'),
    profiles.userId('jumper'),
    profiles.userId('reports'),
  ]);

  // a failing load retries (the retry interceptor repeats GETs twice: three failures reach the error state)
  const injection = await injectApiError(page, { url: `/api/regions/${ZURICH}/mediation`, status: 503, times: 3 });
  await page.goto(`/regions/${ZURICH}`);
  await page.getByTestId('regions.page.tab.mediation').click();
  await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/mediation$`));
  const { retry } = await expectErrorState(page, 'reports.mediation.state');
  expect(injection.calls()).toBe(3);
  await retry.click();
  await expectDataState(page, 'reports.mediation.state', 'success');

  // the team
  await expect(page.locator(`[data-testid="reports.mediation.member"][data-user-id="${reportsId}"]`)).toBeVisible();

  // too short: nothing is sent
  await page.getByTestId('reports.mediation.message').fill('Hilfe');
  await page.getByTestId('reports.mediation.send').click();
  await expect(page.getByTestId('reports.mediation.message')).toHaveAttribute('aria-invalid', 'true');

  // with jumper as the other person
  await page.getByTestId('reports.mediation.picker.search').fill('Weber');
  await page.locator(`[data-testid="reports.mediation.picker.hit"][data-user-id="${jumperId}"]`).getByTestId('reports.mediation.picker.add').click();
  await expect(page.getByTestId('reports.mediation.counterpart')).toHaveAttribute('data-user-id', jumperId);
  const message = `Wir sind uns bei den Abholzeiten von S-01 uneinig und brauchen Hilfe ${Date.now()}`;
  await page.getByTestId('reports.mediation.message').fill(message);
  await page.getByTestId('reports.mediation.send').click();
  await expect(page.getByTestId('reports.mediation.sent')).toBeVisible();

  // the team is rung with a link to the requester
  await expect
    .poll(async () => (await notifications.bells('reports', 'MEDIATION_REQUEST')).map((bell) => bell.link))
    .toContain(`/profile/${foodsaverId}`);
});
