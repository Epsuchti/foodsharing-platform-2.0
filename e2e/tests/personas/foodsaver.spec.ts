import { S07_WEDNESDAY, expect, expectDataState, seedStore, test } from '../../fixtures';
import { pickupRow } from '../pickups/_pickups';
import { NAMES } from '../stores/_stores';
import { Walk, openNewestBell } from './_persona';

/**
 * D11.5.5 persona walkthrough "foodsaver" (decision 3, design review M7 round 3): Sarah (foodsaver@local) finds
 * her store through the navigation, signs up for a free pickup, gets the manager's confirmation as a bell, then
 * offers a food basket and is told about the first request. The store manager (welcome@local) and the requester
 * (jumper@local) act through their own API sessions. Runs on desktop and on the phone project.
 */
test.use({ seedReset: 'test', role: 'foodsaver' });

const S07 = seedStore('S-07');

test('D11.5.5 foodsaver: pickup sign-up confirmed, basket offered and requested @D11.5.5 @PU3 @N3 @B1 @B2 @S1 @page', async ({
  page,
  api,
  as,
  apiOf,
  dev,
  baskets,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const manager = apiOf(await as('welcome'));
  const me = await dev.user('foodsaver');
  let basketId = '';

  await walk.step('opens "my stores" and the store', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('stores');
    await expect(page).toHaveURL(/\/stores\/mine$/);
    const card = page.getByTestId('stores.mine.store').filter({ hasText: S07.name });
    await walk.click(card.first());
    await expect(page.getByTestId('stores.page.name')).toHaveText(S07.name);
  });

  await walk.step('signs up for Wednesday evening', async () => {
    await walk.click(page.getByTestId('stores.page.tab.pickups'));
    await expectDataState(page, 'stores.pickups.state', 'success');
    const row = pickupRow(page, S07_WEDNESDAY);
    await row.scrollIntoViewIfNeeded();
    await walk.click(row.getByTestId('stores.pickups.slot.free').first());
    await expectDataState(page, 'pickups.signup.state', 'success');
    await expect(page.getByTestId('pickups.signup.eligible')).toBeVisible();
    await walk.click(page.getByTestId('pickups.signup.confirm'));
    await expect(row.getByTestId('stores.pickups.unconfirmed')).toBeVisible();
    await expect(row.locator(`[data-testid="stores.pickups.slot.taken"][data-user="${NAMES.foodsaver}"]`)).toHaveAttribute('data-confirmed', 'false');
  });

  await walk.step('gets the confirmation as a bell and sees the confirmed slot', async () => {
    // the store manager confirms (their own session)
    const confirmed = await manager.post(`/api/stores/${S07.id}/pickups/${encodeURIComponent(S07_WEDNESDAY)}/signups/${me.id}/confirm`);
    expect(confirmed.ok(), await confirmed.text()).toBe(true);
    await openNewestBell(walk, api, 'PICKUP_CONFIRMED');
    await expectDataState(page, 'stores.pickups.state', 'success');
    const slot = pickupRow(page, S07_WEDNESDAY).locator(`[data-testid="stores.pickups.slot.taken"][data-user="${NAMES.foodsaver}"]`);
    await expect(slot).toHaveAttribute('data-confirmed', 'true');
  });

  await walk.step('offers a food basket', async () => {
    await walk.nav('baskets');
    await expect(page).toHaveURL(/\/baskets$/);
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await walk.click(page.getByTestId('baskets.nearby.offer'));
    await expect(page).toHaveURL(/\/baskets\/new$/);
    await expectDataState(page, 'baskets.form.state', 'success');
    await walk.fill(page.getByTestId('baskets.form.description'), `Zwei Zöpfe und Gipfeli vom Samstag ${Date.now()}`);
    await walk.check(page.getByTestId('baskets.form.kind.BAKED_GOODS'));
    await walk.click(page.getByTestId('baskets.form.submit'));
    await expect(page).toHaveURL(/\/baskets\/[0-9a-f-]{36}$/);
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.requestsEmpty')).toBeVisible();
    basketId = page.url().split('/').pop()!;
  });

  await walk.step('is told about the first request and sees it on the basket', async () => {
    const requested = await baskets.request('jumper', basketId, 'Ich könnte heute um 18 Uhr vorbeikommen.');
    expect(requested.status, requested.body).toBeLessThan(300);
    await page.goto('/dashboard');
    await openNewestBell(walk, api, 'BASKET_REQUEST');
    await expect(page).toHaveURL(new RegExp(`/baskets/${basketId}$`));
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.requests')).toContainText(NAMES.jumper);
  });

  // round 3: 14 clicks on desktop, 17 on the phone (menu drawer for stores, baskets and bells)
  walk.finish({ steps: 5, clicks: 17, fields: 1 });
});
