import path from 'node:path';
import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, expectErrorState, expectStatus, injectApiError, regionId, test } from '../../fixtures';

/**
 * C6 resources (plan-m7 Q12, A55). Seed (`174-resource-seed.yaml`): categories `…0001-…01` Werkzeug, `02` Küche,
 * `03` Transport; foodsaver owns `…0003-…01` Lastenvelo and `02` Einkochtopf; jumper's `03` is private; commons of
 * zuerich-stadt `07` Festzelt, `08` Waage, `09` Infostand-Material, `10` Thermoboxen; foodsaver's favourites 04, 07, 08.
 * Nobody administers the RESOURCES group `ressourcen-zuerich` in the seed: orga makes jumper its admin.
 */
const ZURICH = regionId('zuerich-stadt');
const KANTON = regionId('kanton-zuerich');
const RESOURCES_GROUP = regionId('ressourcen-zuerich');
const JUMPER = '00000001-0001-4000-8000-000000000007';
const CATEGORY = { tools: '00000056-0001-4000-8000-000000000001', kitchen: '00000056-0001-4000-8000-000000000002', transport: '00000056-0001-4000-8000-000000000003' };
const RESOURCE = { infostand: '00000056-0003-4000-8000-000000000009' };
const PHOTO = path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png');

const card = (page: Page, id: string) => page.locator(`[data-testid="resources.card"][data-resource-id="${id}"]`);

async function openForm(page: Page, trigger: string): Promise<void> {
  await page.getByTestId(trigger).click();
  await expect(page.getByTestId('resources.form.name')).toBeVisible();
  await expect(page.getByTestId('resources.form.categoriesState')).toHaveAttribute('data-state', 'success');
}

test.describe('resources', () => {
  test.use({ seedReset: 'test', role: 'foodsaver' });

  test('C6 member adds resource with images and categories, favourites a commons resource, limit of 10 enforced; RESOURCES group admin adds commons (bell) @C6 @N3 @page', async ({
    page,
    api,
    as,
    apiOf,
    notifications,
  }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto(`/regions/${ZURICH}`);
    await page.getByTestId('regions.page.tab.resources').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/resources$`));
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(page.getByTestId('resources.region.commons').getByTestId('resources.card')).toHaveCount(4);
    // jumper's private resource: foodsaver is no confirmed buddy
    await expect(card(page, '00000056-0003-4000-8000-000000000003')).toHaveCount(0);

    // add a resource with two categories, an openness and a picture
    await openForm(page, 'resources.region.add');
    await page.getByTestId('resources.form.name').fill('Dörrex');
    await page.getByTestId('resources.form.description').fill('Dörrgerät mit 5 Etagen.');
    await page.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.kitchen}"]`).click();
    await page.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.tools}"]`).click();
    await page.locator('[data-testid="resources.form.openness"][data-value="FREE_TO_BORROW"]').check();
    await page.getByTestId('resources.form.restrict').check();
    await page.getByTestId('resources.form.images.input').setInputFiles(PHOTO);
    await expect(page.getByTestId('resources.form.images.file')).toHaveCount(1);
    await page.getByTestId('resources.form.submit').click();
    await expect(page.getByTestId('resources.form.name')).toBeHidden();
    const created = page.getByTestId('resources.region.members').getByTestId('resources.card').filter({ hasText: 'Dörrex' });
    await expect(created).toHaveCount(1);
    await expect(created.getByTestId('resources.card.category')).toHaveCount(2);
    await expect(created.getByTestId('resources.card.images').locator('img')).toHaveCount(1);
    await expect(created.locator('[data-openness="FREE_TO_BORROW"]')).toBeVisible();

    // category chips filter the list
    await page.locator(`[data-testid="resources.region.category"][data-category-id="${CATEGORY.kitchen}"]`).click();
    await expect(created).toBeVisible();
    await page.getByTestId('resources.region.category.all').click();

    // favourite a commons resource; it shows on the favourites page
    await expect(card(page, RESOURCE.infostand)).toHaveAttribute('data-favourite', 'false');
    await card(page, RESOURCE.infostand).getByTestId('resources.card.favourite').click();
    await expect(card(page, RESOURCE.infostand)).toHaveAttribute('data-favourite', 'true');
    await page.getByTestId('resources.region.favourites').click();
    await expect(page).toHaveURL(/\/resources\/favourites$/);
    await expectDataState(page, 'resources.favourites.state', 'success');
    await expect(card(page, RESOURCE.infostand)).toBeVisible();

    // the limit: 3 own resources + 7 more = 10, then adding is disabled and the server refuses
    for (let i = 1; i <= 7; i++) {
      const response = await api.post('/api/resources', {
        name: `Kiste ${i}`,
        categoryIds: [CATEGORY.transport],
        private: false,
        openness: 'ASK_FIRST',
        commons: false,
      });
      await expectStatus(response, [201], `create resource ${i}`);
    }
    await page.goto('/resources/mine');
    await expectDataState(page, 'resources.mine.state', 'success');
    await expect(page.getByTestId('resources.mine.count')).toHaveAttribute('data-count', '10');
    // the limit comes from the server (getMyResourceLimits), not a client constant
    await expect(page.getByTestId('resources.mine.count')).toHaveAttribute('data-max', '10');
    await expect(page.getByTestId('resources.mine.add').locator('button')).toBeDisabled();
    const refused = await api.post('/api/resources', { name: 'Zu viel', categoryIds: [CATEGORY.transport], private: false, openness: 'ASK_FIRST', commons: false });
    expect(refused.status()).toBe(409);
    expect((await refused.json()).code).toBe('RESOURCE_LIMIT_REACHED');
    await page.goto(`/regions/${ZURICH}/resources`);
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(page.getByTestId('resources.region.limit')).toBeVisible();
    await expect(page.getByTestId('resources.region.add')).toHaveCount(0);

    // orga makes jumper admin of the RESOURCES group; jumper adds a commons resource
    const orga = await as('orga');
    const orgaApi = apiOf(orga);
    await expectStatus(await orgaApi.put(`/api/groups/${RESOURCES_GROUP}/members/${JUMPER}`), [200, 201, 204], 'add jumper to the group');
    await expectStatus(await orgaApi.put(`/api/groups/${RESOURCES_GROUP}/admins/${JUMPER}`), [200, 201, 204], 'make jumper group admin');
    const jumper = await as('jumper');
    await acceptPendingConsents(apiOf(jumper));
    await jumper.goto(`/regions/${ZURICH}/resources`);
    await expectDataState(jumper, 'resources.region.state', 'success');
    await openForm(jumper, 'resources.region.add');
    await jumper.getByTestId('resources.form.commons').check();
    await expect(jumper.getByTestId('resources.form.private')).toHaveCount(0);
    await jumper.getByTestId('resources.form.name').fill('Velo-Anhänger');
    await jumper.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.transport}"]`).click();
    await jumper.getByTestId('resources.form.submit').click();
    await expect(jumper.getByTestId('resources.form.name')).toBeHidden();
    await expect(jumper.getByTestId('resources.region.commons').getByTestId('resources.card').filter({ hasText: 'Velo-Anhänger' })).toHaveCount(1);

    // the members are rung; the bell leads to the region's resources
    await expect
      .poll(async () => (await notifications.bells('foodsaver', 'NEW_RESOURCE')).map((bell) => bell.link))
      .toContain(`/regions/${ZURICH}/resources`);
    await page.goto('/bells');
    await expectDataState(page, 'bells.page.state', 'success');
    await page.locator('[data-testid="bells.page.item"][data-type="NEW_RESOURCE"]').first().getByTestId('bells.page.item.open').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/resources$`));
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(page.getByTestId('resources.region.commons').getByTestId('resources.card').filter({ hasText: 'Velo-Anhänger' })).toHaveCount(1);
  });

  test('C6 resources tab hidden for canton-level regions @C6', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto(`/regions/${KANTON}`);
    await expect(page.getByTestId('regions.page.tab.overview')).toBeVisible();
    await expect(page.getByTestId('regions.page.tab.resources')).toHaveCount(0);
    await page.goto(`/regions/${ZURICH}`);
    await expect(page.getByTestId('regions.page.tab.resources')).toBeVisible();

    // the address itself shows why
    await page.goto(`/regions/${KANTON}/resources`);
    const { root } = await expectErrorState(page, 'resources.region.state', { status: 'forbidden', retry: false });
    await expect(root).toContainText('In diesem Bezirk gibt es kein Ressourcen-Mosaik.');
  });

  test('C6 resource lists show error states with retry @C6', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    // the retry interceptor repeats GETs twice: three failures reach the error state
    await injectApiError(page, { url: '/api/resources/mine', status: 503, times: 3 });
    await page.goto('/resources');
    await expect(page).toHaveURL(/\/resources\/mine$/);
    const { retry } = await expectErrorState(page, 'resources.mine.state');
    await retry.click();
    await expectDataState(page, 'resources.mine.state', 'success');
    await expect(page.getByTestId('resources.mine.count')).toHaveAttribute('data-count', '2');

    await injectApiError(page, { url: `/api/regions/${ZURICH}/resources`, status: 503, times: 3 });
    await page.goto(`/regions/${ZURICH}/resources`);
    const region = await expectErrorState(page, 'resources.region.state');
    await region.retry.click();
    await expectDataState(page, 'resources.region.state', 'success');
  });
});
