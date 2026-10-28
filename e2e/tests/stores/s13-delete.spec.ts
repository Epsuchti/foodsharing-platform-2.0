import { expect, expectDataState, test } from '../../fixtures';

/**
 * S13 delete store (decision 54, coordinators): S-01 had pickups → 409 STORE_HAS_PICKUPS explained; S-17 never had a
 * sign-up → soft delete, its team (foodsaver, added directly first) gets the STORE_DELETED bell.
 */
interface BellPage {
  items: { type: string }[];
}

test.describe('orga', () => {
  test.use({ role: 'orga', seedReset: 'test' });

  test('S13 store with pickups cannot be deleted, empty store can, team gets bell @S13', async ({ page, api, dev, store }) => {
    // S-01 has (past) pickups
    await page.goto(`/stores/${store.id('S-01')}/edit`);
    await expectDataState(page, 'stores.form.state', 'success');
    await page.getByTestId('stores.delete.open').click();
    await page.getByTestId('stores.delete.dialog.message').fill('Betrieb schliesst endgültig.');
    await page.getByTestId('stores.delete.dialog.confirm').click();
    await expect(page.getByTestId('stores.delete.error')).toBeVisible();
    await expect(page.getByTestId('stores.delete.dialog.message')).toBeHidden();
    expect((await api.fetch('GET', `/api/stores/${store.id('S-01')}`)).status()).toBe(200);

    // S-17 never had a sign-up: add foodsaver to its team, then delete
    const s17 = store.id('S-17');
    const foodsaver = await dev.user('foodsaver');
    expect((await api.post(`/api/stores/${s17}/team/${foodsaver.id}`)).ok()).toBe(true);
    const bellsBefore = await store.withRole('foodsaver', async (client) =>
      (await client.getJson<BellPage>('/api/bells', { params: { size: 50 } })).items.filter((b) => b.type === 'STORE_DELETED').length,
    );

    await page.goto(`/stores/${s17}/edit`);
    await expectDataState(page, 'stores.form.state', 'success');
    await page.getByTestId('stores.delete.open').click();
    await page.getByTestId('stores.delete.dialog.message').fill('Betrieb gibt es nicht mehr.');
    await page.getByTestId('stores.delete.dialog.confirm').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${store.byKey('S-17').regionId}/stores`));
    expect((await api.fetch('GET', `/api/stores/${s17}`)).status()).toBe(404);

    await expect
      .poll(() =>
        store.withRole('foodsaver', async (client) =>
          (await client.getJson<BellPage>('/api/bells', { params: { size: 50 } })).items.filter((b) => b.type === 'STORE_DELETED').length,
        ),
      )
      .toBeGreaterThan(bellsBefore);
  });
});
