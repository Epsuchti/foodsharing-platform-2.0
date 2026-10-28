import { expect, expectDataState, test } from '../../fixtures';
import { ZURICH, center, chooseOption, chooseOptions, openStore } from './_stores';

/** S2 create and edit stores. bieb@local is STORE_MANAGER in Zürich Stadt and manager of Coop Bahnhofbrücke (S-01). */

test.describe('store manager', () => {
  test.use({ role: 'bieb' });

  test('S2 store manager creates a store with all fields and becomes manager @S2 @page', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await page.getByTestId('stores.region.create').click();
    await expect(page).toHaveURL(new RegExp(`/stores/new\\?regionId=${ZURICH}`));
    await expectDataState(page, 'stores.form.state', 'success');

    await page.getByTestId('stores.form.name').fill('Quartierladen Wipkingen');
    await chooseOption(page, 'stores.form.category', 'Bäckerei');
    await chooseOption(page, 'stores.form.chain', 'Coop');
    await chooseOptions(page, 'stores.form.groceries', ['Brot & Backwaren']);
    await page.getByTestId('stores.form.description').fill('Kleiner Laden im Quartier.');
    await page.getByTestId('stores.form.publicInfo').fill('Eingang hinten.');
    await page.getByTestId('stores.form.address.street').fill('Röschibachstrasse 20');
    await page.getByTestId('stores.form.address.postcode').fill('8037');
    await page.getByTestId('stores.form.address.city').fill('Zürich');
    await center(page, 'stores.form.address.map');
    await page.getByTestId('stores.form.address.map').click();
    await expect(page.getByTestId('stores.form.address.coordinates')).toHaveText(/\d+\.\d{5}, \d+\.\d{5}/);
    await chooseOption(page, 'stores.form.cooperationStatus', 'Kooperiert');
    await page.getByTestId('stores.form.cooperationStart').fill('2026-10-01');
    await chooseOption(page, 'stores.form.convinceStatus', 'Ganz unkompliziert');
    await chooseOption(page, 'stores.form.publicity', 'Erlaubt');
    await chooseOption(page, 'stores.form.sticker', 'Aufkleber hängt');
    await page.getByTestId('stores.form.contactPerson').fill('Frau Huber');
    await page.getByTestId('stores.form.contactPhone').fill('+41 44 000 00 99');
    await page.getByTestId('stores.form.contactEmail').fill('laden@example.ch');
    await chooseOption(page, 'stores.form.publicPickupTime', 'Abends');
    await chooseOption(page, 'stores.form.weightCategory', /^5–10 kg/);
    await page.getByTestId('stores.form.prefetchDays').locator('input').fill('21');
    await center(page, 'stores.form.teamStatus.OPEN_SEARCHING');
    await page.getByTestId('stores.form.teamStatus.OPEN_SEARCHING').check();
    await center(page, 'stores.form.requiresPhone');
    await page.getByTestId('stores.form.requiresPhone').click();
    await page.getByTestId('stores.form.specialNotes').fill('Bitte klingeln.');
    await page.getByTestId('stores.form.firstWallPost').fill('Willkommen im neuen Team!');
    await center(page, 'stores.form.submit');
    await page.getByTestId('stores.form.submit').click();

    await expect(page).toHaveURL(/\/stores\/[0-9a-f-]+\/overview$/);
    await expect(page.getByTestId('stores.page.name')).toHaveText('Quartierladen Wipkingen');
    await expect(page.getByTestId('stores.page.membership')).toContainText('Verantwortlich');
    await expect(page.getByTestId('stores.page.edit')).toBeVisible();
    await expect(page.getByTestId('stores.overview.description')).toHaveText('Kleiner Laden im Quartier.');
    await expect(page.getByTestId('stores.overview.groceries')).toContainText('Brot & Backwaren');
    await expect(page.getByTestId('stores.overview.chain')).toHaveText('Coop');
    await expect(page.getByTestId('stores.overview.specialNotes')).toHaveText('Bitte klingeln.');
    await expect(page.getByTestId('stores.overview.requirements').locator('[data-requirement="phone"]')).toBeVisible();
    await expect(page.getByTestId('stores.overview.managers')).toContainText('Nina Schneider');

    await page.goto('/stores/mine');
    await expect(page.getByTestId('stores.mine.group.manager')).toContainText('Quartierladen Wipkingen');
  });

  test('S2 edit every field group and see FIELD_CHANGED in the log @S2 @S12', async ({ page, api, store }) => {
    await openStore(page, 'S-01');
    await page.getByTestId('stores.page.edit').click();
    await expectDataState(page, 'stores.form.state', 'success');
    await expect(page.getByTestId('stores.form.name')).toHaveValue('Coop Bahnhofbrücke');

    await page.getByTestId('stores.form.description').fill('Neue Beschreibung.');
    await page.getByTestId('stores.form.address.street').fill('Bahnhofbrücke 2');
    await chooseOption(page, 'stores.form.convinceStatus', 'Schwierige Verhandlung');
    await page.getByTestId('stores.form.contactPerson').fill('Herr Meier');
    await chooseOption(page, 'stores.form.publicPickupTime', 'Morgens');
    await center(page, 'stores.form.teamStatus.CLOSED');
    await page.getByTestId('stores.form.teamStatus.CLOSED').check();
    await page.getByTestId('stores.form.reason').fill('Neue Filialleitung');
    await center(page, 'stores.form.submit');
    await page.getByTestId('stores.form.submit').click();

    await expect(page).toHaveURL(/\/stores\/[0-9a-f-]+\/overview$/);
    await expect(page.getByTestId('stores.overview.description')).toHaveText('Neue Beschreibung.');
    await expect(page.getByTestId('stores.page.address')).toContainText('Bahnhofbrücke 2');
    await expect(page.locator('fs-team-status-badge')).toHaveAttribute('data-status', 'CLOSED');

    const log = await api.getJson<{ items: { action: string; field?: string }[] }>(
      `/api/stores/${store.id('S-01')}/log?size=50`,
    );
    const changed = log.items.filter((entry) => entry.action === 'FIELD_CHANGED').map((entry) => entry.field);
    expect(changed.length).toBeGreaterThan(0);
    expect(log.items.some((entry) => entry.action === 'TEAM_STATUS_CHANGED' || entry.field === 'teamStatus')).toBe(true);
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('S2 foodsaver may not create stores @S2', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    await expect(page.getByTestId('stores.region.create')).toHaveCount(0);
    await page.goto(`/stores/new?regionId=${ZURICH}`);
    await expect(page).toHaveURL(/\/forbidden/);
    // members of a store don't get the edit button either
    await openStore(page, 'S-01');
    await expect(page.getByTestId('stores.page.edit')).toHaveCount(0);
  });
});
