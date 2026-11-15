import type { Page } from '@playwright/test';
import { expectDataState, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZURICH, center } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Store coordinator orga@local (ORGA, store coordinator everywhere) tidies up the store data: on the categories admin
 * (decision 55) she adds and renames a category, learns that a used one cannot be deleted, merges two categories and
 * two groceries (the stores move) and deletes the unused new one; she sees Coop Bahnhofbrücke's region and fails to
 * move it to Winterthur, where its team are not members (S16); she cannot delete Coop Bahnhofbrücke, which had
 * pickups, and deletes Bäckerei Kleiner (S-17, never a sign-up) whose team gets the STORE_DELETED bell (S13, decision
 * 54). The rules are unit tests (StoreCategoryServiceTest, StoreServiceTest).
 */
test.use({ role: 'orga' });

const S01 = seedStore('S-01');
const S17 = seedStore('S-17');

interface Item {
  id: string;
  name: string;
  storeCount?: number;
}

const row = (page: Page, kind: string, name: string) => page.locator(`[data-testid="storeCategories.${kind}.row"][data-name="${name}"]`);

test('store coordinator merges categories, fails to move a store and deletes an unused one @S4 @S13 @S16', async ({
  page,
  api,
  dev,
  store,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'store coordinator');
  const categories = await api.getJson<Item[]>('/api/store-categories');
  const groceries = await api.getJson<Item[]>('/api/groceries');
  const [catSource, catTarget] = categories.filter((c) => (c.storeCount ?? 0) > 0).slice(0, 2);
  const [grocSource, grocTarget] = groceries.filter((g) => (g.storeCount ?? 0) > 0).slice(0, 2);
  expect(catTarget, 'two used categories in the seed').toBeTruthy();
  expect(grocTarget, 'two used groceries in the seed').toBeTruthy();

  /** The merge dialog: choose the target and confirm. */
  const merge = async (kind: string, source: Item, target: Item) => {
    await walk.click(row(page, kind, source.name).getByTestId(`storeCategories.${kind}.merge`));
    await expect(page.getByTestId('storeCategories.mergeHint')).toContainText(String(source.storeCount));
    await walk.click(page.getByTestId('storeCategories.mergeTarget'));
    await walk.click(page.getByRole('option', { name: target.name }).first());
    if (kind === 'category') await checkpoint(page, 'merge-dialog');
    await walk.click(page.getByTestId('storeCategories.mergeConfirm'));
    await expect(row(page, kind, source.name)).toHaveCount(0);
  };

  await walk.step('adds and renames a category', async () => {
    await page.goto('/admin/store-categories');
    await expectDataState(page, 'storeCategories.category.state', 'success');
    await expectDataState(page, 'storeCategories.grocery.state', 'success');
    await checkpoint(page, 'categories');
    await walk.fill(page.getByTestId('storeCategories.category.newName'), 'Hofladen');
    await walk.click(page.getByTestId('storeCategories.category.add'));
    await expect(row(page, 'category', 'Hofladen')).toBeVisible();
    await walk.click(row(page, 'category', 'Hofladen').getByTestId('storeCategories.category.rename'));
    await walk.fill(page.getByTestId('storeCategories.category.renameInput'), 'Hofladen & Markt');
    await walk.click(page.getByTestId('storeCategories.category.renameSave'));
    await expect(row(page, 'category', 'Hofladen & Markt')).toBeVisible();
  });

  await walk.step('merges two categories and two groceries', async () => {
    // a used category cannot be deleted
    await walk.click(row(page, 'category', catSource.name).getByTestId('storeCategories.category.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
    await expect(row(page, 'category', catSource.name)).toBeVisible();
    await merge('category', catSource, catTarget);
    await expect(row(page, 'category', catTarget.name).getByTestId('storeCategories.category.count')).toHaveText(
      String((catSource.storeCount ?? 0) + (catTarget.storeCount ?? 0)),
    );
    await merge('grocery', grocSource, grocTarget);
    // the unused new category can be deleted
    await walk.click(row(page, 'category', 'Hofladen & Markt').getByTestId('storeCategories.category.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(row(page, 'category', 'Hofladen & Markt')).toHaveCount(0);
  });

  await walk.step('tries to move Coop Bahnhofbrücke to Winterthur', async () => {
    await page.goto(`/stores/${S01.id}/overview`);
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await expect(page.getByTestId('stores.page.region')).toHaveText(/Zürich Stadt/);
    await expect(page.getByTestId('stores.page.region')).toHaveAttribute('href', `/regions/${ZURICH}/stores`);
    await walk.click(page.getByTestId('stores.page.edit'));
    await expectDataState(page, 'stores.form.state', 'success');
    await center(page, 'stores.form.region');
    await walk.click(page.getByTestId('stores.form.region'));
    await walk.fill(page.getByRole('searchbox').last(), 'Winterthur');
    await walk.click(page.getByRole('option', { name: /Winterthur/ }).first());
    await center(page, 'stores.form.submit');
    await walk.click(page.getByTestId('stores.form.submit'));
    await expect(page.getByTestId('stores.form.error')).toContainText(/Zielbezirk/);
    await expect(page).toHaveURL(/\/edit$/);
    await checkpoint(page, 'move-refused');
  });

  await walk.step('cannot delete Coop Bahnhofbrücke, which had pickups', async () => {
    await page.reload();
    await expectDataState(page, 'stores.form.state', 'success');
    await walk.click(page.getByTestId('stores.delete.open'));
    await walk.fill(page.getByTestId('stores.delete.dialog.message'), 'Betrieb schliesst endgültig.');
    await checkpoint(page, 'delete-dialog');
    await walk.click(page.getByTestId('stores.delete.dialog.confirm'));
    await expect(page.getByTestId('stores.delete.error')).toBeVisible();
    await expect(page.getByTestId('stores.delete.dialog.message')).toBeHidden();
    await checkpoint(page, 'delete-refused');
    // nothing moved
    await page.goto(`/stores/${S01.id}/overview`);
    await expect(page.getByTestId('stores.page.region')).toHaveText(/Zürich Stadt/);
  });

  const foodsaver = await dev.user('foodsaver');
  expect((await api.post(`/api/stores/${S17.id}/team/${foodsaver.id}`)).ok(), 'Sarah joins Bäckerei Kleiner').toBe(true);
  const deletedBells = () =>
    store.withRole('foodsaver', async (client) =>
      (await client.getJson<{ items: { type: string }[] }>('/api/bells', { params: { size: 50 } })).items.filter((b) => b.type === 'STORE_DELETED').length,
    );
  const bellsBefore = await deletedBells();

  await walk.step('deletes Bäckerei Kleiner', async () => {
    await page.goto(`/stores/${S17.id}/edit`);
    await expectDataState(page, 'stores.form.state', 'success');
    await walk.click(page.getByTestId('stores.delete.open'));
    await walk.fill(page.getByTestId('stores.delete.dialog.message'), 'Betrieb gibt es nicht mehr.');
    await walk.click(page.getByTestId('stores.delete.dialog.confirm'));
    await expect(page).toHaveURL(new RegExp(`/regions/${S17.regionId}/stores`));
    await expectDataState(page, 'stores.region.state', 'success');
    await expect(page.locator(`[data-testid="stores.region.row"][data-store="${S17.name}"]`)).toHaveCount(0);
    // the team hears of it
    await expect.poll(deletedBells).toBeGreaterThan(bellsBefore);
  });

  walk.finish({ steps: 5, clicks: 23, fields: 5 });
});
