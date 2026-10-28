import { SEED_NOW, expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA17: user managers pause and re-enable guides (with a reason); paused guides cannot offer EinAbs. */
test.use({ seedReset: 'test', role: 'bot' });

test('EA17 enable/disable guide @EA17', async ({ page, api }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/admin`);
  await page.getByTestId('introPickups.admin.tab.guides').click();
  await expect(page.getByTestId('introPickups.admin.guidesState')).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Julien Favre' })).toHaveAttribute('data-active', 'true');

  // Sarah Müller (foodsaver) becomes a guide …
  await page.getByTestId('introPickups.admin.addGuide').click();
  await page.getByTestId('introPickups.pick.search').fill('Sarah');
  await expect(page.getByTestId('introPickups.pick.choose')).toHaveCount(1);
  await page.getByTestId('introPickups.pick.choose').click();
  await page.getByTestId('introPickups.admin.enableDialog.message').fill('Hilft bei den Einführungen');
  await page.getByTestId('introPickups.admin.enableDialog.confirm').click();
  const sarah = page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Sarah' });
  await expect(sarah).toHaveAttribute('data-active', 'true');
  await api.loginAs('foodsaver');
  expect((await api.getJson<{ id: string }[]>('/api/guide/regions')).map((r) => r.id)).toContain(ZH);

  // … and is paused again
  await sarah.getByTestId('introPickups.admin.toggleGuide').click();
  await page.getByTestId('introPickups.admin.enableDialog.message').fill('Pause bis Januar');
  await page.getByTestId('introPickups.admin.enableDialog.confirm').click();
  await expect(sarah).toHaveCount(0); // no longer a guide of the region

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

});

test('EA17 plain members get the forbidden state @EA17', async ({ as }) => {
  const einab1 = await as('einab1');
  await einab1.goto(`/regions/${ZH}/intro-pickups/admin`);
  await expect(einab1.getByTestId('introPickups.admin.einabsState')).toHaveAttribute('data-state', 'forbidden');
});
