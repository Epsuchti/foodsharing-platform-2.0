import { SEED_NOW, expect, test } from '../../fixtures';
import { ZH } from './_intro';

/**
 * EA17: user managers pause guides (with a reason); paused guides cannot offer EinAbs. Q4 (decision 167): only
 * ambassadors and ORGA release, and only people who passed the EinAb guide quiz (Ready-for-release list).
 */
test.use({ seedReset: 'test', role: 'bot' });

test('EA17 ambassador releases a quiz passer and pauses the guide again @EA17 @Q4', async ({ page, api, dev }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/admin?tab=guides`);
  await expect(page.getByTestId('introPickups.admin.guidesState')).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Julien Favre' })).toHaveAttribute('data-active', 'true');

  // Q4: Jonas Weber (jumper) passed the EinAb guide quiz and waits in "Ready for release" …
  await expect(page.getByTestId('introPickups.admin.candidatesState')).toHaveAttribute('data-state', 'success');
  const candidate = page.getByTestId('introPickups.admin.candidate').filter({ hasText: 'Jonas' });
  await candidate.getByTestId('introPickups.admin.candidateRelease').click();
  await page.getByTestId('introPickups.admin.releaseDialog.confirm').click();
  await expect(candidate).toHaveCount(0);
  const jonas = page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Jonas' });
  await expect(jonas).toHaveAttribute('data-active', 'true');
  await expect(jonas.getByTestId('introPickups.admin.quizTag')).toBeVisible();
  await api.loginAs('jumper');
  expect((await api.getJson<{ id: string }[]>('/api/guide/regions')).map((r) => r.id)).toContain(ZH);

  // … and is paused again
  await jonas.getByTestId('introPickups.admin.toggleGuide').click();
  await page.getByTestId('introPickups.admin.enableDialog.message').fill('Pause bis Januar');
  await page.getByTestId('introPickups.admin.enableDialog.confirm').click();
  // he stays in the list as DISABLED (with the enable action) and is no longer a guide of the region
  await expect(jonas).toHaveAttribute('data-status', 'DISABLED');
  await expect(jonas.getByTestId('introPickups.admin.guideStatus')).toHaveText(/Deaktiviert/);
  await expect(jonas.getByTestId('introPickups.admin.toggleGuide')).toHaveText(/Freischalte/);
  expect((await api.getJson<{ id: string }[]>('/api/guide/regions')).map((r) => r.id)).not.toContain(ZH);

  // a paused guide cannot offer dates
  const refused = await api.post(`/api/regions/${ZH}/guide/intro-pickups`, {
    category: 'BAKERY',
    startAt: new Date(Date.parse(SEED_NOW) + 5 * 86_400_000).toISOString(),
    slotCount: 1,
    visitFairteiler: false,
    businessName: 'Bäckerei',
    privateAddress: 'Weg 1',
    publicGeneralLocation: 'Kreis 1',
  });
  expect(refused.ok()).toBe(false);

  // Q4: people without a quiz pass cannot be released (Sarah, foodsaver)
  await api.loginAs('bot');
  const sarah = (await dev.user('foodsaver')).id;
  const noPass = await api.fetch('PUT', `/api/regions/${ZH}/admin/guides/${sarah}/enabled`, { enabled: true });
  expect(noPass.status()).toBe(409);
  expect((await noPass.json()).code).toBe('GUIDE_QUIZ_NOT_PASSED');
});

test('EA17 guide admins who are no ambassadors see no release @EA17 @Q4', async ({ as, apiOf, dev }) => {
  const bot = apiOf(await as('bot'));
  const guide = await as('guide');
  const guideId = (await dev.user('guide')).id;
  await bot.putJson(`/api/regions/${ZH}/admin/guides/${guideId}/admin`, { admin: true });

  await guide.goto(`/regions/${ZH}/intro-pickups/admin?tab=guides`);
  await expect(guide.getByTestId('introPickups.admin.candidatesState')).toHaveAttribute('data-state', 'success');
  const candidate = guide.getByTestId('introPickups.admin.candidate').filter({ hasText: 'Jonas' });
  await expect(candidate.getByTestId('introPickups.admin.releaseByAmbassadors')).toBeVisible();
  await expect(candidate.getByTestId('introPickups.admin.candidateRelease')).toHaveCount(0);
  await expect(guide.getByTestId('introPickups.admin.addGuide')).toHaveCount(0);
});

test('EA17 region admin grants and revokes the newcomer-manager right of a guide @EA17', async ({ page }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/admin`);
  await page.getByTestId('introPickups.admin.tab.guides').click();
  const julien = page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Julien Favre' });
  await expect(julien).toHaveAttribute('data-status', 'ACTIVE');
  const before = await julien.getAttribute('data-admin');

  await julien.getByTestId('introPickups.admin.toggleAdmin').click();
  const granted = before === 'true' ? 'false' : 'true';
  await expect(julien).toHaveAttribute('data-admin', granted);
  await expect(julien.getByTestId('introPickups.admin.managerTag')).toHaveCount(granted === 'true' ? 1 : 0);

  await julien.getByTestId('introPickups.admin.toggleAdmin').click();
  await expect(julien).toHaveAttribute('data-admin', before!);
});

test('EA17 plain members get the forbidden state @EA17', async ({ as }) => {
  const einab1 = await as('einab1');
  await einab1.goto(`/regions/${ZH}/intro-pickups/admin`);
  await expect(einab1.getByTestId('introPickups.admin.einabsState')).toHaveAttribute('data-state', 'forbidden');
});
