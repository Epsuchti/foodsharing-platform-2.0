import type { Page } from '@playwright/test';
import {
  ACTIVE_ZURICH_FSPS,
  FSPS,
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  regionId,
  test,
} from '../../fixtures';

/**
 * FS1 food share points (plan-m7 Q6, plan-m6 B6). Seed (`e2e/fixtures/fsp.ts`): four ACTIVE FSPs in zuerich-stadt,
 * Altstetten INACTIVE (created by foodsaver), Enge deleted. bot administers the Zürich FSP group, so a member's new FSP
 * rings FSP_ACTIVATE for him; followers get FSP_NEW_POST bells and/or mails per their flags (not the author).
 */
const ZURICH = regionId('zuerich-stadt');

const card = (page: Page, fspId: string) => page.locator(`fs-fsp-card[data-fsp-id="${fspId}"]`);
const fspIdFromUrl = (page: Page) => new URL(page.url()).pathname.split('/').pop()!;

test.describe('member proposes, admin activates', () => {
  test.use({ seedReset: 'test', role: 'foodsaver' });

  test('FS1 member creates inactive FSP, FSP admin activates (bell), managers set, follow by bell and mail, wall post notifies follower @FS1 @N3 @N6 @page', async ({
    page,
    as,
    apiOf,
    profiles,
    notifications,
    mails,
  }) => {
    await acceptPendingConsents(apiOf(page));
    const guideId = await profiles.userId('guide');
    const name = `Fairteiler Albisrieden ${Date.now()}`;

    // foodsaver proposes a new FSP from the region tab; it waits for activation
    await page.goto(`/regions/${ZURICH}/food-share-points`);
    await expectDataState(page, 'foodSharePoints.region.state', 'success');
    await page.getByTestId('foodSharePoints.region.add').click();
    await expect(page).toHaveURL(new RegExp(`/food-share-points/new\\?regionId=${ZURICH}$`));
    await expectDataState(page, 'foodSharePoints.form.state', 'success');
    await expect(page.getByTestId('foodSharePoints.form.activationNote')).toBeVisible();
    await page.getByTestId('foodSharePoints.form.name').fill(name);
    await page.getByTestId('foodSharePoints.form.description').fill('Regal im Velokeller, täglich offen.');
    await page.getByTestId('foodSharePoints.form.street').fill('Albisriederstrasse 300');
    await page.getByTestId('foodSharePoints.form.postcode').fill('8047');
    await page.getByTestId('foodSharePoints.form.city').fill('Zürich');
    // a click on the map sets the pin (without one the server geocodes the address)
    const formMap = page.getByTestId('foodSharePoints.form.map');
    await expect(formMap.locator('.leaflet-container')).toBeVisible();
    await formMap.locator('.leaflet-container').click({ position: { x: 120, y: 80 } });
    await expect(page.getByTestId('foodSharePoints.form.position')).toBeVisible();
    await page.getByTestId('foodSharePoints.form.submit').click();
    await expect(page).toHaveURL(/\/food-share-points\/[0-9a-f-]{36}$/);
    const fspId = fspIdFromUrl(page);
    await expectDataState(page, 'foodSharePoints.detail.state', 'success');
    await expect(page.getByTestId('foodSharePoints.detail.inactive')).toBeVisible();
    await expect(page.getByTestId('foodSharePoints.detail.activate')).toHaveCount(0);
    await expect(page.getByTestId('foodSharePoints.managers.item')).toHaveCount(1);
    await expect(page.getByTestId('foodSharePoints.detail.map').locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(1);

    // bot, admin of the FSP group, is rung and activates it from the bell
    await expect
      .poll(async () => (await notifications.bells('bot', 'FSP_ACTIVATE')).map((bell) => bell.link))
      .toContain(`/food-share-points/${fspId}`);
    const bot = await as('bot');
    await acceptPendingConsents(apiOf(bot));
    await bot.goto('/bells');
    await expectDataState(bot, 'bells.page.state', 'success');
    await bot.locator('[data-testid="bells.page.item"][data-type="FSP_ACTIVATE"]').first().getByTestId('bells.page.item.open').click();
    await expect(bot).toHaveURL(new RegExp(`/food-share-points/${fspId}$`));
    await expectDataState(bot, 'foodSharePoints.detail.state', 'success');
    await bot.getByTestId('foodSharePoints.detail.activate').click();
    await expect(bot.getByTestId('foodSharePoints.detail.inactive')).toHaveCount(0);

    // bot adds guide as second manager
    await bot.getByTestId('foodSharePoints.managers.edit').click();
    await bot.getByTestId('foodSharePoints.managers.picker.search').fill('Favre');
    await bot.locator(`[data-testid="foodSharePoints.managers.picker.hit"][data-user-id="${guideId}"]`).getByTestId('foodSharePoints.managers.picker.add').click();
    await expect(bot.getByTestId('foodSharePoints.managers.draftItem')).toHaveCount(2);
    await bot.getByTestId('foodSharePoints.managers.save').click();
    await expect(bot.getByTestId('foodSharePoints.managers.item')).toHaveCount(2);
    await expect(bot.locator(`[data-testid="foodSharePoints.managers.item"][data-user-id="${guideId}"]`)).toBeVisible();

    // jumper follows with bell and mail
    const jumper = await as('jumper');
    await acceptPendingConsents(apiOf(jumper));
    await jumper.goto(`/food-share-points/${fspId}`);
    await expectDataState(jumper, 'foodSharePoints.detail.state', 'success');
    const follow = jumper.getByTestId('foodSharePoints.detail.follow');
    await expect(follow).toHaveAttribute('data-following', 'false');
    await jumper.locator('#fsp-follow-mail').check();
    await jumper.getByTestId('foodSharePoints.follow.submit').click();
    await expect(follow).toHaveAttribute('data-following', 'true');
    await expect(jumper.locator('#fsp-follow-bell')).toBeChecked();
    await expect(jumper.locator('#fsp-follow-mail')).toBeChecked();
    await expect(jumper.getByTestId('foodSharePoints.follow.unfollow')).toBeVisible();

    // foodsaver writes on the wall: jumper gets a bell and a mail
    const post = `Neu aufgefüllt: Brot und Äpfel ${Date.now()}`;
    await page.reload();
    await expectDataState(page, 'foodSharePoints.detail.state', 'success');
    await expect(page.getByTestId('foodSharePoints.detail.inactive')).toHaveCount(0);
    await page.getByTestId('foodSharePoints.detail.wall.text').fill(post);
    await page.getByTestId('foodSharePoints.detail.wall.submit').click();
    await expect(page.getByTestId('foodSharePoints.detail.wall.post').filter({ hasText: post })).toHaveCount(1);
    await expect
      .poll(async () => (await notifications.bells('jumper', 'FSP_NEW_POST')).map((bell) => bell.link))
      .toContain(`/food-share-points/${fspId}`);
    const mail = await mails.waitForMail({ to: 'jumper@local', predicate: (m) => m.text.includes(post) });
    expect(mail.text).toContain(name);

    // the new FSP is on jumper's list and on the region tab
    await jumper.goto('/food-share-points/mine');
    await expectDataState(jumper, 'foodSharePoints.mine.state', 'success');
    await expect(card(jumper, fspId)).toBeVisible();
    await expect(card(jumper, fspId).getByTestId('foodSharePoints.card.following')).toBeVisible();
  });
});

test.describe('region tab', () => {
  // the seed list exactly (the proposal test above adds an FSP)
  test.use({ seedReset: 'test', role: 'foodsaver' });

  test('FS1 region tab lists FSPs with map @FS1 @R3', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto(`/regions/${ZURICH}`);
    await page.getByTestId('regions.page.tab.foodSharePoints').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/food-share-points$`));
    await expectDataState(page, 'foodSharePoints.region.state', 'success');
    // four active FSPs plus foodsaver's own proposal, the deleted one never
    for (const id of ACTIVE_ZURICH_FSPS) await expect(card(page, id)).toBeVisible();
    await expect(page.getByTestId('foodSharePoints.card')).toHaveCount(5);
    await expect(card(page, FSPS.altstetten).getByTestId('foodSharePoints.card.inactive')).toBeVisible();
    await expect(card(page, FSPS.wiedikon).getByTestId('foodSharePoints.card.inactive')).toHaveCount(0);
    await expect(card(page, FSPS.wiedikon).getByTestId('foodSharePoints.card.following')).toBeVisible();
    await expect(card(page, FSPS.enge)).toHaveCount(0);
    // pins for the active ones only
    const map = page.getByTestId('foodSharePoints.region.map');
    await expect(map.locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(ACTIVE_ZURICH_FSPS.length);

    // the detail page has the address with navigation links, managers and the seeded wall
    await card(page, FSPS.wiedikon).getByTestId('foodSharePoints.card.link').click();
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.wiedikon}$`));
    await expectDataState(page, 'foodSharePoints.detail.state', 'success');
    await expect(page.getByTestId('foodSharePoints.detail.addressText')).toContainText('Schlossgasse 10');
    await expect(page.getByTestId('foodSharePoints.detail.googleMaps')).toHaveAttribute('href', /query=47\.3721,8\.5172/);
    await expect(page.getByTestId('foodSharePoints.managers.item')).toContainText('Nina Schneider');
    await expect(page.getByTestId('foodSharePoints.detail.edit')).toHaveCount(0);
    await expect(page.getByTestId('foodSharePoints.detail.follow')).toHaveAttribute('data-following', 'true');
    await expectDataState(page, 'foodSharePoints.detail.wall.state', 'success');

    // my FSPs: followed and managed ones
    await page.goto('/food-share-points/mine');
    await expectDataState(page, 'foodSharePoints.mine.state', 'success');
    for (const id of [FSPS.wiedikon, FSPS.langstrasse, FSPS.altstetten]) await expect(card(page, id)).toBeVisible();
  });

  test('FS1 inactive and deleted FSPs are not found for others; load errors retry @FS1', async ({ page, as, apiOf }) => {
    const jumper = await as('jumper');
    await acceptPendingConsents(apiOf(jumper));
    await jumper.goto(`/food-share-points/${FSPS.altstetten}`);
    await expectDataState(jumper, 'foodSharePoints.detail.state', 'notFound');
    await jumper.goto(`/food-share-points/${FSPS.enge}`);
    await expectDataState(jumper, 'foodSharePoints.detail.state', 'notFound');
    // the inactive proposal is not on jumper's region tab
    await jumper.goto(`/regions/${ZURICH}/food-share-points`);
    await expectDataState(jumper, 'foodSharePoints.region.state', 'success');
    await expect(card(jumper, FSPS.altstetten)).toHaveCount(0);

    // the retry interceptor repeats GETs twice: three failures reach the error state
    await acceptPendingConsents(apiOf(page));
    const injection = await injectApiError(page, { url: `/api/regions/${ZURICH}/food-share-points`, status: 503, times: 3 });
    await page.goto(`/regions/${ZURICH}/food-share-points`);
    const { retry } = await expectErrorState(page, 'foodSharePoints.region.state');
    expect(injection.calls()).toBe(3);
    await retry.click();
    await expectDataState(page, 'foodSharePoints.region.state', 'success');

    await injectApiError(page, { url: `/api/food-share-points/${FSPS.wiedikon}/wall`, status: 503, times: 3 });
    await page.goto(`/food-share-points/${FSPS.wiedikon}`);
    await expectDataState(page, 'foodSharePoints.detail.state', 'success');
    const wall = await expectErrorState(page, 'foodSharePoints.detail.wall.state');
    await wall.retry.click();
    await expectDataState(page, 'foodSharePoints.detail.wall.state', 'success');
  });
});
