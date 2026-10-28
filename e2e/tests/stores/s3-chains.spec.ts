import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { NAMES } from './_stores';

/**
 * S3 store chains (decision 55). Seed: Coop (KAM bieb), Migros (KAM bot); reports@local administrates the global
 * STORE_CHAINS group "Betriebsketten CH" (chain admin without being ORGA).
 */
const COOP = '00000020-0003-4000-8000-000000000001';

const row = (page: Page, name: string) => page.locator(`[data-testid="storeChains.list.row"][data-chain="${name}"]`);
const kam = (page: Page, name: string) => page.locator(`[data-testid="storeChains.chain.kam"][data-user="${name}"]`);

test.describe('bieb (KAM of Coop)', () => {
  test.use({ role: 'bieb', seedReset: 'test' });

  test('S3 chain list and detail with KAMs; KAM edits chain but not KAMs @S3 @page', async ({ page, api }) => {
    await page.goto('/store-chains');
    await expectDataState(page, 'storeChains.list.state', 'success');
    await expect(row(page, 'Coop').getByTestId('storeChains.list.kam')).toHaveText(NAMES.bieb);
    await expect(row(page, 'Migros')).toBeVisible();
    await expect(page.getByTestId('storeChains.list.create')).toHaveCount(0);

    await row(page, 'Coop').getByTestId('storeChains.list.link').click();
    await expect(page).toHaveURL(new RegExp(`/store-chains/${COOP}$`));
    await expectDataState(page, 'storeChains.chain.state', 'success');
    await expect(kam(page, NAMES.bieb)).toBeVisible();
    await expect(page.getByTestId('storeChains.chain.removeKam')).toHaveCount(0);
    await expect(page.getByTestId('storeChains.chain.kamSearch')).toHaveCount(0);
    await expectDataState(page, 'storeChains.chain.storesState', 'success');
    await expect(page.getByTestId('storeChains.chain.store').filter({ hasText: 'Coop Bahnhofbrücke' })).toBeVisible();

    // KAM edits the chain
    await page.getByTestId('storeChains.chain.edit').click();
    await page.getByTestId('storeChains.edit.estimated').fill('123');
    await page.getByTestId('storeChains.edit.notes').fill('Ansprechperson: Frau Keller');
    await page.getByTestId('storeChains.edit.submit').click();
    await expect(page.getByTestId('storeChains.edit.name')).toBeHidden();
    await expect(page.getByTestId('storeChains.chain.count')).toContainText('/ 123');
    await expect(page.getByTestId('storeChains.chain.notes')).toContainText('Frau Keller');

    // … but not the KAM list
    const denied = await api.fetch('PATCH', `/api/store-chains/${COOP}`, { kamIds: [] });
    expect(denied.status()).toBe(403);
    expect((await denied.json()).code).toBe('KAM_CANNOT_EDIT_KAMS');
  });
});

test.describe('reports (chain group admin)', () => {
  test.use({ role: 'reports', seedReset: 'test' });

  test('S3 chain group admin creates chain; foodsaver read-only @S3', async ({ page, as, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/store-chains');
    await expectDataState(page, 'storeChains.list.state', 'success');
    await page.getByTestId('storeChains.list.create').click();
    await page.getByTestId('storeChains.create.name').fill('Spar Schweiz');
    await page.getByTestId('storeChains.create.estimated').fill('40');
    await page.getByTestId('storeChains.create.submit').click();
    await expect(page).toHaveURL(/\/store-chains\/[0-9a-f-]{36}$/);
    await expectDataState(page, 'storeChains.chain.state', 'success');
    await expect(page.getByTestId('storeChains.chain.count')).toContainText('0 / 40');

    // add and remove a KAM
    await page.getByTestId('storeChains.chain.kamSearch').fill('Nina');
    await page.getByTestId('storeChains.chain.addKam').first().click();
    await expect(kam(page, NAMES.bieb)).toBeVisible();
    await kam(page, NAMES.bieb).getByTestId('storeChains.chain.removeKam').click();
    await expect(kam(page, NAMES.bieb)).toHaveCount(0);
    const chainUrl = page.url();

    const foodsaver = await as('foodsaver');
    await foodsaver.goto('/store-chains');
    await expectDataState(foodsaver, 'storeChains.list.state', 'success');
    await expect(row(foodsaver, 'Spar Schweiz')).toBeVisible();
    await expect(foodsaver.getByTestId('storeChains.list.create')).toHaveCount(0);
    await foodsaver.goto(new URL(chainUrl).pathname);
    await expectDataState(foodsaver, 'storeChains.chain.state', 'success');
    await expect(foodsaver.getByTestId('storeChains.chain.edit')).toHaveCount(0);
    await expect(foodsaver.getByTestId('storeChains.chain.kamSearch')).toHaveCount(0);
  });
});
