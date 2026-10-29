import { expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, test } from '../../fixtures';

/**
 * R12 video conference link per region/group: admins set it in the region settings (bot administrates
 * zuerich-stadt), members see and open it on the overview, non-members do not get it (A10).
 */
const KOCHGRUPPE = '00000011-0001-4000-8000-000000000033';
const URL_1 = 'https://meet.jit.si/foodsharing-zuerich';
const URL_2 = 'https://meet.jit.si/foodsharing-zuerich-neu';

test.use({ seedReset: 'test', role: 'bot' });

test('R12 admin sets and changes the conference link, members see and open it, non-members do not get it @R12 @page', async ({ page, region, as, apiOf }) => {
  const zs = region.id('zuerich-stadt');
  await page.goto(`/regions/${zs}/settings`);
  await expectDataState(page, 'regions.settings.state', 'success');
  const input = page.getByTestId('regions.settings.conferenceUrl');
  await input.fill('ftp://meet.example.org');
  await expect(page.getByTestId('regions.settings.save').locator('button')).toBeDisabled();
  await input.fill(URL_1);
  await page.getByTestId('regions.settings.save').click();
  await page.goto(`/regions/${zs}/overview`);
  await expect(page.getByTestId('regions.overview.conference')).toHaveAttribute('href', URL_1);

  await page.goto(`/regions/${zs}/settings`);
  await expect(input).toHaveValue(URL_1);
  await input.fill(URL_2);
  await page.getByTestId('regions.settings.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });

  const member = await as('foodsaver');
  await member.goto(`/regions/${zs}/overview`);
  const link = member.getByTestId('regions.overview.conference');
  await expect(link).toHaveAttribute('href', URL_2);
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('rel', /noopener/);
  const conference = await apiOf(member).get(`/api/regions/${zs}/conference`);
  expect(conference.status()).toBe(200);
  expect(JSON.stringify(await conference.json())).toContain(URL_2);
  await expectNoSeriousViolations(member);

  const outsider = await as('profile');
  const denied = await apiOf(outsider).get(`/api/regions/${zs}/conference`);
  expect([403, 404]).toContain(denied.status());
  const region_ = await apiOf(outsider).get(`/api/regions/${zs}`);
  if (region_.ok()) expect(JSON.stringify(await region_.json())).not.toContain(URL_2);
});

test('R12 group conference link is shown to group members only @R12', async ({ as, apiOf }) => {
  const set = await apiOf(await as('orga')).patch(`/api/regions/${KOCHGRUPPE}/settings`, { conferenceUrl: URL_1 });
  expect(set.status(), await set.text()).toBe(200);
  const member = await as('foodsaver');
  await member.goto(`/groups/${KOCHGRUPPE}`);
  await expect(member.getByTestId('regions.group.conference')).toHaveAttribute('href', URL_1);
  const outsider = await as('bieb');
  await outsider.goto(`/groups/${KOCHGRUPPE}`);
  await expect(outsider.getByTestId('regions.group.head')).toBeVisible();
  await expect(outsider.getByTestId('regions.group.conference')).toHaveCount(0);
});

test('R12 settings save error shows the error and keeps the input @R12', async ({ page, region }) => {
  const zs = region.id('zuerich-stadt');
  await page.goto(`/regions/${zs}/settings`);
  await expectDataState(page, 'regions.settings.state', 'success');
  await injectApiError(page, { url: /\/api\/regions\/[^/]+\/settings$/, method: 'PATCH', status: 500 });
  await page.getByTestId('regions.settings.conferenceUrl').fill(URL_1);
  await page.getByTestId('regions.settings.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toBeVisible();
  await expect(page.getByTestId('regions.settings.conferenceUrl')).toHaveValue(URL_1);

  const failing = await injectApiError(page, { url: /\/api\/regions\/[^/]+\/settings$/, status: 500 });
  await page.reload();
  await expectErrorState(page, 'regions.settings.state');
  await failing.remove();
});
