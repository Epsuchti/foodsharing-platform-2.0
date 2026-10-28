import type { Page } from '@playwright/test';
import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';
import { acceptConfirm } from './_stores';

/**
 * S4 categories admin (ORGA, decision 55): add, rename, merge (stores move, source deleted) for categories and
 * groceries; deleting a used entry answers 409 CATEGORY_IN_USE.
 */
interface Item {
  id: string;
  name: string;
  storeCount?: number;
}

const row = (page: Page, kind: string, name: string) =>
  page.locator(`[data-testid="storeCategories.${kind}.row"][data-name="${name}"]`);

async function chooseTarget(page: Page, name: string): Promise<void> {
  await page.getByTestId('storeCategories.mergeTarget').click();
  await page.getByRole('option', { name }).first().click();
}

test.describe('orga', () => {
  test.use({ role: 'orga', seedReset: 'test' });

  test('S4 orga merges two categories and groceries, stores move @S4', async ({ page, api }) => {
    const categories = await api.getJson<Item[]>('/api/store-categories');
    const groceries = await api.getJson<Item[]>('/api/groceries');
    const [catSource, catTarget] = [...categories].filter((c) => (c.storeCount ?? 0) > 0).slice(0, 2);
    const [grocSource, grocTarget] = [...groceries].filter((g) => (g.storeCount ?? 0) > 0).slice(0, 2);
    expect(catTarget, 'two used categories in the seed').toBeTruthy();
    expect(grocTarget, 'two used groceries in the seed').toBeTruthy();

    await page.goto('/admin/store-categories');
    await expectDataState(page, 'storeCategories.category.state', 'success');
    await expectDataState(page, 'storeCategories.grocery.state', 'success');

    // add + rename a category
    await page.getByTestId('storeCategories.category.newName').fill('Hofladen');
    await page.getByTestId('storeCategories.category.add').click();
    await expect(row(page, 'category', 'Hofladen')).toBeVisible();
    await row(page, 'category', 'Hofladen').getByTestId('storeCategories.category.rename').click();
    await page.getByTestId('storeCategories.category.renameInput').fill('Hofladen & Markt');
    await page.getByTestId('storeCategories.category.renameSave').click();
    await expect(row(page, 'category', 'Hofladen & Markt')).toBeVisible();

    // used category cannot be deleted
    await row(page, 'category', catSource.name).getByTestId('storeCategories.category.delete').click();
    await acceptConfirm(page);
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
    await expect(row(page, 'category', catSource.name)).toBeVisible();

    // merge categories
    await row(page, 'category', catSource.name).getByTestId('storeCategories.category.merge').click();
    await expect(page.getByTestId('storeCategories.mergeHint')).toContainText(String(catSource.storeCount));
    await chooseTarget(page, catTarget.name);
    await page.getByTestId('storeCategories.mergeConfirm').click();
    await expect(row(page, 'category', catSource.name)).toHaveCount(0);
    await expect(row(page, 'category', catTarget.name).getByTestId('storeCategories.category.count')).toHaveText(
      String((catSource.storeCount ?? 0) + (catTarget.storeCount ?? 0)),
    );

    // merge groceries
    await row(page, 'grocery', grocSource.name).getByTestId('storeCategories.grocery.merge').click();
    await chooseTarget(page, grocTarget.name);
    await page.getByTestId('storeCategories.mergeConfirm').click();
    await expect(row(page, 'grocery', grocSource.name)).toHaveCount(0);
    const after = await api.getJson<Item[]>('/api/groceries');
    expect(after.find((g) => g.id === grocTarget.id)!.storeCount).toBeGreaterThanOrEqual(grocTarget.storeCount ?? 0);

    // the unused new category can be deleted
    await row(page, 'category', 'Hofladen & Markt').getByTestId('storeCategories.category.delete').click();
    await acceptConfirm(page);
    await expect(row(page, 'category', 'Hofladen & Markt')).toHaveCount(0);

    // settle (no toasts or closing dialogs) for the a11y check of @page
    await page.reload();
    await expectDataState(page, 'storeCategories.category.state', 'success');
    // a11y of the page content; not @page: ORGA's 15 header nav items squeeze the last (active) one below 24 px
    // (target-size in layout/header, reported to the layout owner)
    await expectNoSeriousViolations(page, { include: ['main'] });
  });

  test('S4 categories admin is ORGA only @S4', async ({ as }) => {
    const bieb = await as('bieb');
    await bieb.goto('/admin/store-categories');
    await expect(bieb.getByTestId('storeCategories.category.section')).toHaveCount(0);
  });
});
