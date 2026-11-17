import type { Locator } from '@playwright/test';
import { expectDataState, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { NAMES, ZURICH, center } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';
import { fillDate } from '../../support/date-inputs';

/**
 * Store manager Nina (bieb@local, STORE_MANAGER in Zürich Stadt, manager of S-01 Coop Bahnhofbrücke and KAM of the
 * Coop chain) sets up a store: she looks through the region's store list, creates "Quartierladen Wipkingen" with every
 * field group (the pin follows the typed address until she sets it by hand, decision 171; the texts are Markdown), finds
 * it under "my stores", edits Coop Bahnhofbrücke (team closed with a reason, no longer named on the partners page,
 * decision 156) and updates the Coop chain as its KAM (decision 55). Who may create, edit, name publicly or edit KAMs
 * is unit-tested (StorePermissionMatrixTest, PublicNamingRulesTest, StoreChainServiceTest).
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');
const COOP = '00000020-0003-4000-8000-000000000001';
const NEW_STORE = 'Quartierladen Wipkingen';

test('store manager creates a store, edits it and updates the chain @S1 @S2 @S3 @S12 @S17', async ({ page, as }, testInfo) => {
  test.setTimeout(180_000);
  const walk = new Walk(page, testInfo, 'store manager');

  /** Picks an option of a p-select (two taps). */
  const choose = async (testId: string, label: string | RegExp) => {
    await center(page, testId);
    await walk.click(page.getByTestId(testId));
    await walk.click(page.getByRole('option', { name: label }).first());
    await expect(page.locator('.p-overlay')).toHaveCount(0);
  };
  const field = (testId: string): Locator => page.getByTestId(testId);

  await walk.step('looks through the stores of Zürich Stadt', async () => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    // the four active cooperation states by default
    await expect(page.getByTestId('stores.region.row')).toHaveCount(20);
    await walk.fill(page.getByTestId('stores.region.search'), 'Coop');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(3);
    for (const row of await page.getByTestId('stores.region.row').all()) {
      await expect(row).toContainText('Coop');
    }
    await checkpoint(page, 'region-stores-search');
    await walk.fill(page.getByTestId('stores.region.search'), '');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(20);
    await walk.click(page.getByTestId('stores.region.showMap'));
    await expect(page.getByTestId('stores.region.map')).toBeVisible();
  });

  await walk.step('creates Quartierladen Wipkingen', async () => {
    await walk.click(page.getByTestId('stores.region.create'));
    await expect(page).toHaveURL(new RegExp(`/stores/new\\?regionId=${ZURICH}`));
    await expectDataState(page, 'stores.form.state', 'success');
    await walk.fill(field('stores.form.name'), NEW_STORE);
    await choose('stores.form.category', 'Bäckerei');
    await choose('stores.form.chain', 'Coop');
    await center(page, 'stores.form.groceries');
    await walk.click(field('stores.form.groceries'));
    await walk.click(page.getByRole('option', { name: 'Brot & Backwaren' }).first());
    await page.keyboard.press('Escape');
    try {
      await expect(page.locator('.p-overlay')).toHaveCount(0, { timeout: 2_000 });
    } catch {
      // some engines keep the multiselect open on Escape: the persona taps the field again
      await walk.click(field('stores.form.groceries'));
      await expect(page.locator('.p-overlay')).toHaveCount(0);
    }
    await expect(field('stores.form.description')).toHaveAttribute('rows', '10');
    await walk.fill(field('stores.form.description'), 'Kleiner Laden im Quartier, Hintereingang **Rampe 2**.');
    await walk.fill(field('stores.form.publicInfo'), 'Abholung _nur_ nach Absprache.');
    // the pin follows the typed address (static geocoder: the postcode's point) …
    const coordinates = field('stores.form.address.coordinates');
    await walk.fill(field('stores.form.address.street'), 'Röschibachstrasse 20');
    await walk.fill(field('stores.form.address.postcode'), '8001');
    await walk.fill(field('stores.form.address.city'), 'Zürich');
    await expect(coordinates).toHaveText('47.37170, 8.54230');
    await expect(field('stores.form.address.geocodeStatus')).toHaveAttribute('data-status', 'found');
    await checkpoint(page, 'store-form-address');
    // … until it is set by hand; the button puts it back on the address
    await center(page, 'stores.form.address.map');
    // a tap beside the marker in the middle (on iPhone a tap on the marker itself did not move it; the zoom buttons
    // sit in the top corner)
    walk.count(1);
    const map = field('stores.form.address.map');
    const box = (await map.boundingBox())!;
    await map.click({ position: { x: box.width / 2 + 60, y: box.height / 2 + 30 } });
    await expect(coordinates).not.toHaveText('47.37170, 8.54230');
    const manual = await coordinates.textContent();
    await walk.fill(field('stores.form.address.postcode'), '8003');
    await expect(coordinates).toHaveText(manual ?? '');
    await walk.click(field('stores.form.address.geocode'));
    await expect(coordinates).toHaveText('47.37350, 8.51300');
    await choose('stores.form.cooperationStatus', 'Kooperiert');
    await fillDate(field('stores.form.cooperationStart'), '2026-10-01');
    walk.count(1);
    await choose('stores.form.convinceStatus', 'Ganz unkompliziert');
    await choose('stores.form.publicity', 'Erlaubt');
    await choose('stores.form.sticker', 'Aufkleber hängt');
    await walk.fill(field('stores.form.contactPerson'), 'Frau Huber');
    await walk.fill(field('stores.form.contactPhone'), '+41 44 000 00 99');
    await walk.fill(field('stores.form.contactEmail'), 'laden@example.ch');
    await choose('stores.form.publicPickupTime', 'Abends');
    await choose('stores.form.weightCategory', /^5–10 kg/);
    await walk.fill(field('stores.form.prefetchDays').locator('input'), '21');
    await center(page, 'stores.form.teamStatus.OPEN_SEARCHING');
    await walk.check(field('stores.form.teamStatus.OPEN_SEARCHING'));
    await center(page, 'stores.form.requiresPhone');
    await walk.click(field('stores.form.requiresPhone'));
    await walk.fill(field('stores.form.specialNotes'), 'Bitte klingeln.');
    await walk.fill(field('stores.form.firstWallPost'), 'Willkommen im neuen Team!');
    await center(page, 'stores.form.submit');
    await walk.click(field('stores.form.submit'));

    await expect(page).toHaveURL(/\/stores\/[0-9a-f-]+\/overview$/);
    await expect(page.getByTestId('stores.page.name')).toHaveText(NEW_STORE);
    await expect(page.getByTestId('stores.page.membership')).toContainText('Verantwortlich');
    await expect(page.getByTestId('stores.overview.description').locator('strong')).toHaveText('Rampe 2');
    await expect(page.getByTestId('stores.overview.publicInfo').locator('em')).toHaveText('nur');
    await expect(page.getByTestId('stores.overview.groceries')).toContainText('Brot & Backwaren');
    await expect(page.getByTestId('stores.overview.chain')).toHaveText('Coop');
    await expect(page.getByTestId('stores.overview.specialNotes')).toHaveText('Bitte klingeln.');
    await expect(page.getByTestId('stores.overview.requirements').locator('[data-requirement="phone"]')).toBeVisible();
    await expect(page.getByTestId('stores.overview.managers')).toContainText(NAMES.bieb);
    await checkpoint(page, 'new-store-overview');
  });

  await walk.step('finds it under "my stores"', async () => {
    await walk.nav('stores');
    await expect(page).toHaveURL(/\/stores\/mine$/);
    await expectDataState(page, 'stores.mine.state', 'success');
    await expect(page.getByTestId('stores.mine.group.manager')).toContainText(NEW_STORE);
  });

  const visitor = await as(null);
  await test.step('a visitor sees store counts on the map and Coop Bahnhofbrücke on the partners page', async () => {
    // decision 156: nobody unverified learns which stores work with foodsharing, only exact counts per city
    const asked: string[] = [];
    visitor.on('request', (request) => asked.push(request.url()));
    await visitor.goto('/map');
    await expectDataState(visitor, 'map.page.map.state', 'success');
    const bubbles = visitor.getByTestId('map.page.map').locator('.fs-map-marker[data-kind="store-area"]');
    await expect(bubbles).toHaveCount(2);
    await expect(bubbles.first()).toContainText(/^\d+$/);
    await bubbles.first().focus();
    await bubbles.first().press('Enter');
    await expect(visitor.getByTestId('map.area.text')).toContainText('Winterthur');
    await expect(visitor.getByTestId('map.area').locator('a')).toHaveCount(0);
    await expect(visitor.getByTestId('map.page.layer.stores')).toHaveCount(0);
    expect(asked.filter((url) => url.includes('/api/map/markers/stores'))).toEqual([]);
    await visitor.goto('/about/partners');
    await expectDataState(visitor, 'content.partners.state', 'success');
    await expect(visitor.getByTestId('content.partners.store')).toHaveCount(2);
    await expect(visitor.getByTestId('content.partners.state')).toContainText(S01.name);
  });

  await walk.step('closes the Coop Bahnhofbrücke team and takes it off the partners page', async () => {
    await walk.click(page.getByTestId('stores.mine.store').filter({ hasText: S01.name }).first());
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await walk.click(page.getByTestId('stores.page.edit'));
    await expectDataState(page, 'stores.form.state', 'success');
    await expect(field('stores.form.name')).toHaveValue(S01.name);
    await walk.fill(field('stores.form.description'), 'Neue Beschreibung.');
    await center(page, 'stores.form.teamStatus.CLOSED');
    await walk.check(field('stores.form.teamStatus.CLOSED'));
    await walk.fill(field('stores.form.reason'), 'Neue Filialleitung');
    await expect(field('stores.form.publicNameWarning')).toBeVisible();
    await center(page, 'stores.form.publicName');
    await walk.click(page.locator('label[for="sf-public-name"]'));
    await checkpoint(page, 'store-form-edit');
    await center(page, 'stores.form.submit');
    await walk.click(field('stores.form.submit'));
    await expect(page).toHaveURL(/\/stores\/[0-9a-f-]+\/overview$/);
    await expect(page.getByTestId('stores.overview.description')).toHaveText('Neue Beschreibung.');
    await expect(page.getByTestId('stores.page.header').locator('fs-team-status-badge')).toHaveAttribute('data-status', 'CLOSED');
    // the change and the closed team are in the store log
    await walk.click(page.getByTestId('stores.page.tab.log'));
    await expectDataState(page, 'stores.log.list', 'success');
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.STORE_UPDATED"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.TEAM_STATUS_CHANGED"]').first()).toBeVisible();
  });

  await test.step('the partners page no longer names Coop Bahnhofbrücke', async () => {
    await visitor.reload();
    await expectDataState(visitor, 'content.partners.state', 'success');
    await expect(visitor.getByTestId('content.partners.store')).toHaveCount(1);
    await expect(visitor.getByTestId('content.partners.state')).not.toContainText(S01.name);
  });

  await walk.step('updates the Coop chain as its KAM', async () => {
    await page.goto('/store-chains');
    await expectDataState(page, 'storeChains.list.state', 'success');
    const coop = page.locator('[data-testid="storeChains.list.row"][data-chain="Coop"]');
    await expect(coop.getByTestId('storeChains.list.kam')).toHaveText(NAMES.bieb);
    await expect(page.locator('[data-testid="storeChains.list.row"][data-chain="Migros"]')).toBeVisible();
    await walk.click(coop.getByTestId('storeChains.list.link'));
    await expect(page).toHaveURL(new RegExp(`/store-chains/${COOP}$`));
    await expectDataState(page, 'storeChains.chain.state', 'success');
    await expect(page.locator(`[data-testid="storeChains.chain.kam"][data-user="${NAMES.bieb}"]`)).toBeVisible();
    // a KAM edits the chain but not the KAM list
    await expect(page.getByTestId('storeChains.chain.kamSearch')).toHaveCount(0);
    await expectDataState(page, 'storeChains.chain.storesState', 'success');
    await expect(page.getByTestId('storeChains.chain.store').filter({ hasText: NEW_STORE })).toBeVisible();
    await walk.click(page.getByTestId('storeChains.chain.edit'));
    await walk.fill(page.getByTestId('storeChains.edit.estimated'), '123');
    await walk.fill(page.getByTestId('storeChains.edit.notes'), 'Ansprechperson: Frau Keller');
    await checkpoint(page, 'chain-edit-dialog');
    await walk.click(page.getByTestId('storeChains.edit.submit'));
    await expect(page.getByTestId('storeChains.edit.name')).toBeHidden();
    await expect(page.getByTestId('storeChains.chain.count')).toContainText('/ 123');
    await expect(page.getByTestId('storeChains.chain.notes')).toContainText('Frau Keller');
    await checkpoint(page, 'chain-page');
  });

  // the phone taps the groceries field once more to close its list
  walk.finish({ steps: 5, clicks: 37, fields: 19 });
});
