import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Ambassador Daniel (bot@local, Zürich Stadt, region mailbox, events) starts his day on the dashboard (A19): the
 * activity feed (10 per page, "load more"), filter chips stored on the server, an event item that links to its
 * event, and the three nearest food baskets around his home address; a member without an address is sent to the
 * profile instead. Seed: bot sees MAILBOX and STORE_WALL on the first page, EVENT_WALL, FORUM and FSP_WALL further
 * down; profile@local has no address. Paging and mention rules: ActivityFeedServiceTest.
 */
test.use({ role: 'bot' });

interface BasketDto {
  id: string;
}

const items = (page: Page, type?: string) =>
  page
    .getByTestId('dashboard.activity.list')
    .locator(type ? `[data-testid="dashboard.activity.item"][data-type="${type}"]` : '[data-testid="dashboard.activity.item"]');
const chip = (page: Page, type: string) => page.getByTestId(`dashboard.activity.filter.${type}`);

test('ambassador reads and filters the activity feed and finds baskets nearby @A19', async ({ page, as, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'ambassador');
  const api = apiOf(page);
  await acceptPendingConsents(api);

  await walk.step('reads the activity feed and loads more', async () => {
    await page.goto('/dashboard');
    await expectDataState(page, 'dashboard.activity.state', 'success');
    await expect(items(page)).toHaveCount(10);
    await expect(items(page, 'MAILBOX').first()).toBeVisible();
    await expect(items(page, 'STORE_WALL').first()).toBeVisible();
    for (const type of ['FORUM', 'STORE_WALL', 'MAILBOX', 'EVENT_WALL', 'FSP_WALL', 'BUDDY_WALL']) {
      await expect(chip(page, type)).toHaveAttribute('aria-pressed', 'true');
    }
    await expectDataState(page, 'dashboard.baskets.state', 'success');
    await checkpoint(page, 'dashboard');
    for (let more = 0; more < 2; more++) {
      const before = await items(page).count();
      await walk.click(page.getByTestId('dashboard.activity.loadMore'));
      await expect(items(page)).toHaveCount(before + 10);
    }
    await expect(items(page, 'EVENT_WALL').first()).toBeVisible();
    await expect(items(page, 'FORUM').first()).toBeVisible();
  });

  await walk.step('hides mailboxes, store walls and the forum', async () => {
    for (const type of ['MAILBOX', 'STORE_WALL', 'FORUM']) {
      await walk.click(chip(page, type));
      await expect(chip(page, type)).toHaveAttribute('aria-pressed', 'false');
    }
    await expectDataState(page, 'dashboard.activity.state', 'success');
    await expect(items(page, 'FSP_WALL').first()).toBeVisible();
    await expect(items(page, 'EVENT_WALL').first()).toBeVisible();
    await expect(items(page, 'MAILBOX')).toHaveCount(0);
    await expect(items(page, 'STORE_WALL')).toHaveCount(0);
    await expect(items(page, 'FORUM')).toHaveCount(0);
    await checkpointElement(page.getByTestId('dashboard.activity'), 'feed-filtered');
    expect((await api.getJson<{ excluded: string[] }>('/api/users/me/activity-filters')).excluded.sort()).toEqual(['FORUM', 'MAILBOX', 'STORE_WALL']);

    // the choice is stored on the server; the forum comes back with its chip
    await page.reload();
    await expectDataState(page, 'dashboard.activity.state', 'success');
    await expect(chip(page, 'MAILBOX')).toHaveAttribute('aria-pressed', 'false');
    await expect(items(page, 'MAILBOX')).toHaveCount(0);
    await walk.click(chip(page, 'FORUM'));
    await expect(items(page, 'FORUM').first()).toBeVisible();
  });

  await walk.step('opens an event from the feed', async () => {
    const event = items(page, 'EVENT_WALL').first();
    await expect(event).toContainText('Foodsharing-Brunch am Fairteiler Wiedikon');
    await walk.click(event.getByTestId('dashboard.activity.link'));
    await expect(page).toHaveURL(/\/events\/00000044-0002-4000-8000-000000000001/);
  });

  await walk.step('finds the three nearest baskets', async () => {
    const me = await api.getJson<{ latitude: number; longitude: number }>('/api/users/me');
    const nearby = await api.getJson<BasketDto[]>(`/api/baskets/nearby?lat=${me.latitude}&lon=${me.longitude}&distanceKm=30`);
    expect(nearby.length).toBeGreaterThanOrEqual(3);
    await walk.nav('dashboard');
    await expectDataState(page, 'dashboard.baskets.state', 'success');
    const cards = page.getByTestId('dashboard.baskets.list').locator('fs-basket-card');
    await expect(cards).toHaveCount(3);
    for (const [index, basket] of nearby.slice(0, 3).entries()) {
      await expect(cards.nth(index)).toHaveAttribute('data-basket-id', basket.id);
    }
    await expect(page.getByTestId('dashboard.baskets.all')).toHaveAttribute('href', '/baskets');
    await checkpointElement(page.getByTestId('dashboard.baskets'), 'nearby-baskets');

    // a member without an address is sent to the profile
    const noAddress = await as('profile');
    await acceptPendingConsents(apiOf(noAddress));
    await noAddress.goto('/dashboard');
    await expect(noAddress.getByTestId('dashboard.baskets.noPosition')).toBeVisible();
    await checkpointElement(noAddress.getByTestId('dashboard.baskets'), 'baskets-without-address');
    await noAddress.getByTestId('dashboard.baskets.addAddress').click();
    await expect(noAddress).toHaveURL(/\/account\/profile$/);
  });

  // measured 2026-11-15 (rework step 3): 8 clicks on desktop and phone
  walk.finish({ steps: 4, clicks: 8, fields: 0 });
});
