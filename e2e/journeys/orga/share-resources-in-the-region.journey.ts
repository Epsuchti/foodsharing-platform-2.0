import path from 'node:path';
import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState, expectErrorState, expectStatus, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * C6 resources (plan-m7 Q12, A55): a member (foodsaver@local) of Zürich Stadt lends a dehydrator with a picture and two
 * categories, filters the region's resources, keeps a commons resource as a favourite and meets the limit of ten own
 * resources. The ORGA has made Jumper (jumper@local) admin of the resources group, who adds a trailer to the region's
 * commons; the member is rung and finds it. A canton has no resources tab. Seed (`174-resource-seed.yaml`): four commons
 * in Zürich Stadt, the member owns two resources, Jumper's private one is hidden from non-buddies. Rules behind it:
 * ResourceRulesTest (which units have resources, the limit of 10, private resources for owner and buddies); client
 * resources.util.spec, resources-pages.spec.
 */
test.use({ role: 'foodsaver' });

const ZURICH = regionId('zuerich-stadt');
const KANTON = regionId('kanton-zuerich');
const RESOURCES_GROUP = regionId('ressourcen-zuerich');
const JUMPER = '00000001-0001-4000-8000-000000000007';
const CATEGORY = {
  tools: '00000056-0001-4000-8000-000000000001',
  kitchen: '00000056-0001-4000-8000-000000000002',
  transport: '00000056-0001-4000-8000-000000000003',
};
const INFOSTAND = '00000056-0003-4000-8000-000000000009';
const JUMPERS_PRIVATE = '00000056-0003-4000-8000-000000000003';
const PHOTO = path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png');

const card = (page: Page, id: string) => page.locator(`[data-testid="resources.card"][data-resource-id="${id}"]`);

async function openForm(walk: Walk, trigger: string): Promise<void> {
  await walk.click(walk.page.getByTestId(trigger));
  await expect(walk.page.getByTestId('resources.form.name')).toBeVisible();
  await expect(walk.page.getByTestId('resources.form.categoriesState')).toHaveAttribute('data-state', 'success');
}

test('a member lends a dehydrator, keeps a favourite and reaches the limit; the resources group adds to the commons @C6 @N3', async ({
  page,
  api,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(180_000);
  const memberApi = apiOf(page);
  await acceptPendingConsents(memberApi);
  const walk = new Walk(page, testInfo, 'member');
  const commons = page.getByTestId('resources.region.commons').getByTestId('resources.card');
  const dehydrator = page.getByTestId('resources.region.members').getByTestId('resources.card').filter({ hasText: 'Dörrex' });

  await walk.step('opens the resources of Zürich Stadt', async () => {
    await page.goto(`/regions/${ZURICH}`);
    await walk.click(page.getByTestId('regions.page.tab.resources'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/resources$`));
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(commons).toHaveCount(4);
    // Jumper's private resource: the member is no confirmed buddy
    await expect(card(page, JUMPERS_PRIVATE)).toHaveCount(0);
    await checkpoint(page, 'region-resources');
  });

  await walk.step('lends a dehydrator with a picture', async () => {
    await openForm(walk, 'resources.region.add');
    await walk.fill(page.getByTestId('resources.form.name'), 'Dörrex');
    await walk.fill(page.getByTestId('resources.form.description'), 'Dörrgerät mit 5 Etagen.');
    await walk.click(page.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.kitchen}"]`));
    await walk.click(page.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.tools}"]`));
    await walk.check(page.locator('[data-testid="resources.form.openness"][data-value="FREE_TO_BORROW"]'));
    await walk.check(page.getByTestId('resources.form.restrict'));
    await page.getByTestId('resources.form.images.input').setInputFiles(PHOTO);
    walk.count(1);
    await expect(page.getByTestId('resources.form.images.file')).toHaveCount(1);
    await checkpoint(page, 'resource-form', { fullPage: false });
    await walk.click(page.getByTestId('resources.form.submit'));
    await expect(page.getByTestId('resources.form.name')).toBeHidden();
    await expect(dehydrator).toHaveCount(1);
    await expect(dehydrator.getByTestId('resources.card.category')).toHaveCount(2);
    await expect(dehydrator.getByTestId('resources.card.images').locator('img')).toHaveCount(1);
    await expect(dehydrator.locator('[data-openness="FREE_TO_BORROW"]')).toBeVisible();
    // the kitchen chip keeps it in the list
    await walk.click(page.locator(`[data-testid="resources.region.category"][data-category-id="${CATEGORY.kitchen}"]`));
    await expect(dehydrator).toBeVisible();
    await walk.click(page.getByTestId('resources.region.category.all'));
  });

  await walk.step('keeps the information stand as a favourite', async () => {
    await expect(card(page, INFOSTAND)).toHaveAttribute('data-favourite', 'false');
    await walk.click(card(page, INFOSTAND).getByTestId('resources.card.favourite'));
    await expect(card(page, INFOSTAND)).toHaveAttribute('data-favourite', 'true');
    await walk.click(page.getByTestId('resources.region.favourites'));
    await expect(page).toHaveURL(/\/resources\/favourites$/);
    await expectDataState(page, 'resources.favourites.state', 'success');
    await expect(card(page, INFOSTAND)).toBeVisible();
    await checkpoint(page, 'favourites');
  });

  // over the weeks the member adds seven more boxes (3 + 7 = 10)
  for (let i = 1; i <= 7; i++) {
    const response = await memberApi.post('/api/resources', {
      name: `Kiste ${i}`,
      categoryIds: [CATEGORY.transport],
      private: false,
      openness: 'ASK_FIRST',
      commons: false,
    });
    await expectStatus(response, [201], `create resource ${i}`);
  }

  await walk.step('has reached the limit of ten resources', async () => {
    await page.goto('/resources/mine');
    await expectDataState(page, 'resources.mine.state', 'success');
    // the limit comes from the server (getMyResourceLimits)
    await expect(page.getByTestId('resources.mine.count')).toHaveAttribute('data-count', '10');
    await expect(page.getByTestId('resources.mine.count')).toHaveAttribute('data-max', '10');
    await expect(page.getByTestId('resources.mine.add').locator('button')).toBeDisabled();
    await checkpoint(page, 'my-resources-full');
    await page.goto(`/regions/${ZURICH}/resources`);
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(page.getByTestId('resources.region.limit')).toBeVisible();
    await expect(page.getByTestId('resources.region.add')).toHaveCount(0);
  });

  // the ORGA made Jumper admin of the resources group of Zürich
  await api.loginAs('orga');
  await expectStatus(await api.put(`/api/groups/${RESOURCES_GROUP}/members/${JUMPER}`), [200, 201, 204], 'add jumper to the group');
  await expectStatus(await api.put(`/api/groups/${RESOURCES_GROUP}/admins/${JUMPER}`), [200, 201, 204], 'make jumper group admin');
  const jumper = await as('jumper');
  await acceptPendingConsents(apiOf(jumper));
  const group = new Walk(jumper, testInfo, 'resources group');

  await group.step('adds a trailer to the commons', async () => {
    await jumper.goto(`/regions/${ZURICH}/resources`);
    await expectDataState(jumper, 'resources.region.state', 'success');
    await openForm(group, 'resources.region.add');
    await group.check(jumper.getByTestId('resources.form.commons'));
    // commons are never private
    await expect(jumper.getByTestId('resources.form.private')).toHaveCount(0);
    await group.fill(jumper.getByTestId('resources.form.name'), 'Velo-Anhänger');
    await group.click(jumper.locator(`[data-testid="resources.form.category"][data-category-id="${CATEGORY.transport}"]`));
    await group.click(jumper.getByTestId('resources.form.submit'));
    await expect(jumper.getByTestId('resources.form.name')).toBeHidden();
    await expect(jumper.getByTestId('resources.region.commons').getByTestId('resources.card').filter({ hasText: 'Velo-Anhänger' })).toHaveCount(1);
  });

  await walk.step('follows the bell to the new trailer', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await openNewestBell(walk, memberApi, 'NEW_RESOURCE');
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/resources$`));
    await expectDataState(page, 'resources.region.state', 'success');
    await expect(commons.filter({ hasText: 'Velo-Anhänger' })).toHaveCount(1);
  });

  await walk.step('finds no resources in the canton', async () => {
    await page.goto(`/regions/${KANTON}`);
    await expect(page.getByTestId('regions.page.tab.overview')).toBeVisible();
    await expect(page.getByTestId('regions.page.tab.resources')).toHaveCount(0);
    // an old link says why
    await page.goto(`/regions/${KANTON}/resources`);
    const { root } = await expectErrorState(page, 'resources.region.state', { status: 'forbidden', retry: false });
    await expect(root).toContainText('In diesem Bezirk gibt es kein Ressourcen-Mosaik.');
    await checkpoint(page, 'canton-without-resources');
  });

  walk.finish({ steps: 6, clicks: 15, fields: 2 });
  group.finish({ steps: 1, clicks: 4, fields: 1 });
});
