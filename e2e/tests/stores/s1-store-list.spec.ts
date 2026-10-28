import { expect, expectDataState, test } from '../../fixtures';
import { ZURICH, chooseOptions, openStore } from './_stores';

/**
 * S1 store lists. Seed (docs/seed-ids.md): jumper@local is member of Bäckerei Vohdin (S-02), jumper in Coop
 * Bahnhofbrücke (S-01) and Coop Stauffacher (S-05), invited to Wochenmarkt Helvetiaplatz (S-08). Zürich Stadt has
 * 22 stores, 20 of them in the four default cooperation states.
 */

test.describe('jumper', () => {
  test.use({ role: 'jumper' });

  test('S1 my stores grouped by membership with next pickup and free slots @S1 @page', async ({ page }) => {
    await page.goto('/stores/mine');
    await expectDataState(page, 'stores.mine.state', 'success');
    const member = page.getByTestId('stores.mine.group.member');
    await expect(member.getByTestId('stores.mine.store')).toHaveCount(1);
    await expect(member).toContainText('Bäckerei Vohdin');
    const jumper = page.getByTestId('stores.mine.group.jumper');
    await expect(jumper.getByTestId('stores.mine.store')).toHaveCount(2);
    await expect(jumper).toContainText('Coop Bahnhofbrücke');
    await expect(page.getByTestId('stores.mine.group.invited')).toContainText('Wochenmarkt Helvetiaplatz');
    await expect(page.getByTestId('stores.mine.group.manager')).toHaveCount(0);

    const vohdin = member.getByTestId('stores.mine.store').first();
    await expect(vohdin.getByTestId('stores.mine.nextPickup')).toContainText(/\d/);
    await expect(vohdin.locator('fs-free-slot-dot .dot')).toHaveAttribute('aria-label', /\d/);
    await expect(vohdin.locator('fs-cooperation-status-badge')).toHaveAttribute('data-status', 'COOPERATION_ESTABLISHED');

    await vohdin.click();
    await expect(page.getByTestId('stores.page.name')).toHaveText('Bäckerei Vohdin');
  });

  test('S1 jumper and unverified user see store information but no pickups tab @S1', async ({ page }) => {
    await openStore(page, 'S-01');
    await expect(page.getByTestId('stores.page.membership')).toContainText(/Springer/);
    await expect(page.getByTestId('stores.overview.info')).toBeVisible();
    await expect(page.getByTestId('stores.overview.requirements')).toBeVisible();
    // jumpers neither see pickups, the wall nor the store contact (A19)
    await expect(page.getByTestId('stores.page.tab.overview')).toBeVisible();
    await expect(page.getByTestId('stores.page.tab.pickups')).toHaveCount(0);
    await expect(page.getByTestId('stores.page.tab.wall')).toHaveCount(0);
    await expect(page.getByTestId('stores.overview.contact')).toHaveCount(0);
    await expect(page.getByTestId('stores.page.call')).toHaveCount(0);
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('S1 region store list filters by cooperation and team status @S1 @page', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(20);
    // foodsavers cannot create stores
    await expect(page.getByTestId('stores.region.create')).toHaveCount(0);

    await page.getByTestId('stores.region.search').fill('Coop');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(3);
    for (const row of await page.getByTestId('stores.region.row').all()) {
      await expect(row).toContainText('Coop');
    }
    await page.getByTestId('stores.region.search').fill('');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(20);

    // team status: only stores urgently searching
    await chooseOptions(page, 'stores.region.teamStatus', ['Sucht dringend Leute']);
    await expect(
      page.locator('[data-testid="stores.region.row"] fs-team-status-badge:not([data-status="OPEN_SEARCHING"])'),
    ).toHaveCount(0);
    await expect(page.getByTestId('stores.region.row').first()).toBeVisible();

    // map view
    await page.getByTestId('stores.region.showMap').click();
    await expect(page.getByTestId('stores.region.map')).toBeVisible();

    // row click opens the store
    await page.getByTestId('stores.region.row').first().click();
    await expect(page).toHaveURL(/\/stores\/[0-9a-f-]+\/overview$/);
  });
});
