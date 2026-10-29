import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * A19 dashboard (plan-m7 Q7): activity feed (`/api/activity`, 10 per page, newest first) with filter chips stored on
 * the server (`/api/users/me/activity-filters`), and the three nearest food baskets around the home address.
 * bot@local (ambassador of Zürich Stadt, region mailbox, events) sees all five types in the seed: MAILBOX and
 * STORE_WALL on the first page, EVENT_WALL, FORUM and FSP_WALL further down. profile@local has no address.
 */

interface BasketDto {
  id: string;
}

const items = (page: Page, type?: string) =>
  page.getByTestId('dashboard.activity.list').locator(type ? `[data-testid="dashboard.activity.item"][data-type="${type}"]` : '[data-testid="dashboard.activity.item"]');
const chip = (page: Page, type: string) => page.getByTestId(`dashboard.activity.filter.${type}`);

test('A19 activity feed shows forum, store wall, mailbox, event and FSP items; filters hide types; nearby baskets card @A19 @page', async ({
  as,
  apiOf,
}) => {
  const page = await as('bot');
  const api = apiOf(page);
  await acceptPendingConsents(api);

  await page.goto('/dashboard');
  await expectDataState(page, 'dashboard.activity.state', 'success');
  await expect(items(page)).toHaveCount(10);
  await expect(items(page, 'MAILBOX').first()).toBeVisible();
  await expect(items(page, 'STORE_WALL').first()).toBeVisible();
  for (const type of ['FORUM', 'STORE_WALL', 'MAILBOX', 'EVENT_WALL', 'FSP_WALL', 'BUDDY_WALL']) {
    await expect(chip(page, type)).toHaveAttribute('aria-pressed', 'true');
  }

  // "load more" appends the next pages until the event and forum items show
  for (let more = 0; more < 2; more++) {
    const before = await items(page).count();
    await page.getByTestId('dashboard.activity.loadMore').click();
    await expect(items(page)).toHaveCount(before + 10);
  }
  await expect(items(page, 'EVENT_WALL').first()).toBeVisible();
  await expect(items(page, 'FORUM').first()).toBeVisible();

  // hide mailboxes, store walls and forum: the feed starts again with only events and food share points
  for (const type of ['MAILBOX', 'STORE_WALL', 'FORUM']) {
    await chip(page, type).click();
    await expect(chip(page, type)).toHaveAttribute('aria-pressed', 'false');
  }
  await expectDataState(page, 'dashboard.activity.state', 'success');
  await expect(items(page, 'FSP_WALL').first()).toBeVisible();
  await expect(items(page, 'EVENT_WALL').first()).toBeVisible();
  await expect(items(page, 'MAILBOX')).toHaveCount(0);
  await expect(items(page, 'STORE_WALL')).toHaveCount(0);
  await expect(items(page, 'FORUM')).toHaveCount(0);
  expect((await api.getJson<{ excluded: string[] }>('/api/users/me/activity-filters')).excluded.sort()).toEqual(['FORUM', 'MAILBOX', 'STORE_WALL']);

  // the choice is stored on the server; the forum comes back with its chip
  await page.reload();
  await expectDataState(page, 'dashboard.activity.state', 'success');
  await expect(chip(page, 'MAILBOX')).toHaveAttribute('aria-pressed', 'false');
  await expect(items(page, 'MAILBOX')).toHaveCount(0);
  await chip(page, 'FORUM').click();
  await expect(items(page, 'FORUM').first()).toBeVisible();

  // an item links to its source
  const event = items(page, 'EVENT_WALL').first();
  await expect(event).toContainText('Foodsharing-Brunch am Fairteiler Wiedikon');
  await event.getByTestId('dashboard.activity.link').click();
  await expect(page).toHaveURL(/\/events\/00000044-0002-4000-8000-000000000001/);

  // nearby baskets: the three nearest around bot's home address
  const me = await api.getJson<{ latitude: number; longitude: number }>('/api/users/me');
  const nearby = await api.getJson<BasketDto[]>(`/api/baskets/nearby?lat=${me.latitude}&lon=${me.longitude}&distanceKm=30`);
  expect(nearby.length).toBeGreaterThanOrEqual(3);
  await page.goto('/dashboard');
  await expectDataState(page, 'dashboard.baskets.state', 'success');
  const cards = page.getByTestId('dashboard.baskets.list').locator('fs-basket-card');
  await expect(cards).toHaveCount(3);
  for (const [index, basket] of nearby.slice(0, 3).entries()) {
    await expect(cards.nth(index)).toHaveAttribute('data-basket-id', basket.id);
  }
  await expect(page.getByTestId('dashboard.baskets.all')).toHaveAttribute('href', '/baskets');

  // without an address the card links to the profile
  const noAddress = await as('profile');
  await acceptPendingConsents(apiOf(noAddress));
  await noAddress.goto('/dashboard');
  await expect(noAddress.getByTestId('dashboard.baskets.noPosition')).toBeVisible();
  await noAddress.getByTestId('dashboard.baskets.addAddress').click();
  await expect(noAddress).toHaveURL(/\/account\/profile$/);
});

test('A19 activity feed shows the error state and loads again on retry @A19', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  await acceptPendingConsents(apiOf(page));
  // the retry interceptor repeats a failed GET twice: fail all three attempts
  await injectApiError(page, { url: '/api/activity', status: 503, times: 3 });
  await page.goto('/dashboard');
  const { retry } = await expectErrorState(page, 'dashboard.activity.state');
  await retry.click();
  await expectDataState(page, 'dashboard.activity.state', 'success');
  await expect(items(page).first()).toBeVisible();
});
