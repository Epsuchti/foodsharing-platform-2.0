import type { Locator, Page } from '@playwright/test';
import { type ApiClient, type BellDto, collapseSidenav } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) keeps up with her bells (N3, N2): the badge on the dashboard (header bell on
 * desktop, "Menu" tab on phones) counts her unread bells; she opens the list (the side menu card on desktop, the bells
 * page from the menu drawer on phones), follows the newest bell to its page, gets Reto's buddy request live over the
 * realtime connection without a reload, and tidies up the bells page: unread filter, delete one, delete the read ones,
 * mark all read, empty. The second test keeps long German bell titles inside their card (owner's report). Rules: the
 * paging, own-only deletes and unread counts are BellServiceTest/BellsControllerTest, badge text, icons and every
 * bell title in five languages are client unit tests (bells.spec, lucide-icons.spec).
 */
test.use({ role: 'foodsaver' });

const clockTexts = (page: Page) => [page.locator('time')];

async function unread(api: ApiClient): Promise<number> {
  return (await api.getJson<{ count: number }>('/api/bells/unread-count')).count;
}

/** The visible unread badge: header bell on desktop, "Menu" tab on phones. */
function badge(page: Page): Locator {
  return page.getByTestId(isPhone(page) ? 'nav.tab.menu.badge' : 'bells.badge');
}

/** Opens the bell list: the side menu card on desktop, the bells page through the menu drawer on phones. */
async function openBells(walk: Walk): Promise<string> {
  const { page } = walk;
  if (isPhone(page)) {
    await walk.click(page.getByTestId('nav.tab.menu'));
    await expect(page.getByTestId('nav.drawer.bells.badge')).toBeVisible();
    await walk.click(page.getByTestId('nav.drawer.bells'));
    await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
    return 'bells.page.item';
  }
  await walk.click(page.getByTestId('bells.button'));
  await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', 'success');
  return 'bells.panel.item';
}

test('foodsaver follows her bells, gets one live and tidies them up @N3 @N2', async ({ page, api, as, apiOf, dev }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const before = await unread(api);
  expect(before).toBeGreaterThan(1);

  await walk.step('sees her unread bells on the dashboard and opens the newest', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(badge(page)).toHaveText(String(before));
    const item = await openBells(walk);
    if (!isPhone(page)) expect(await page.getByTestId(item).count()).toBeLessThanOrEqual(10);
    await checkpoint(page, 'bell-list', { mask: clockTexts(page) });

    const bells = (await api.getJson<{ items: BellDto[] }>('/api/bells?size=10')).items;
    const target = bells.find((bell) => !bell.readAt && bell.link)!;
    await walk.click(page.locator(`[data-testid="${item}"][data-id="${target.id}"]`).getByTestId(`${item}.open`));
    await expect(page.getByTestId('bell.ui.panel')).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(target.link!.split('?')[0].replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    await expect(badge(page)).toHaveText(String(before - 1));
  });

  await walk.step("gets Reto's buddy request without reloading", async () => {
    const [me, reto] = await Promise.all([dev.user('foodsaver'), as('bot')]);
    await expect
      .poll(() => page.evaluate(() => (window as unknown as { __fsRealtime?: { connected(): boolean } }).__fsRealtime?.connected() ?? false))
      .toBe(true);
    const response = await apiOf(reto).post(`/api/users/${me.id}/buddies`);
    expect(response.status(), await response.text()).toBe(200);
    await expect(badge(page)).toHaveText(String(before), { timeout: 10_000 });
  });

  await walk.step('tidies up the bells page', async () => {
    await openBells(walk);
    // desktop: "all bells" at the end of the side menu card (phones are on the bells page already)
    if (!isPhone(page)) await walk.click(page.getByTestId('bells.panel.all'));
    await expect(page).toHaveURL(/\/bells$/);
    const state = page.getByTestId('bells.page.state');
    await expect(state).toHaveAttribute('data-state', 'success');
    const items = page.getByTestId('bells.page.item');
    const count = await unread(api);
    await walk.click(page.getByTestId('bells.page.filter').getByRole('button', { name: 'Ungelesen' }));
    await expect(items).toHaveCount(count);
    await expect(page.locator('[data-testid="bells.page.item"][data-unread="false"]')).toHaveCount(0);
    await checkpoint(page, 'unread-bells', { mask: clockTexts(page) });

    const first = items.first();
    const id = await first.getAttribute('data-id');
    await walk.click(first.getByTestId('bells.page.item.delete'));
    await expect(page.locator(`[data-testid="bells.page.item"][data-id="${id}"]`)).toHaveCount(0);
    await expect(items).toHaveCount(count - 1);

    await walk.click(page.getByTestId('bells.page.filter').getByRole('button', { name: 'Alle' }));
    await walk.click(page.getByTestId('bells.page.deleteRead'));
    await expect(page.locator('[data-testid="bells.page.item"][data-unread="false"]')).toHaveCount(0);
    await expect(items).toHaveCount(count - 1);
    await checkpoint(page, 'bells-page', { mask: clockTexts(page) });

    await walk.click(page.getByTestId('bells.page.markAllRead'));
    await expect(page.locator('[data-testid="bells.page.item"][data-unread="true"]')).toHaveCount(0);
    await walk.click(page.getByTestId('bells.page.deleteRead'));
    await expect(state).toHaveAttribute('data-state', 'empty');
    await expect(badge(page)).toHaveCount(0);
    expect(await unread(api)).toBe(0);
    await checkpoint(page, 'bells-empty');
  });

  walk.finish({ steps: 3, clicks: 11, fields: 0 });
});

/** Fails when the element or any descendant is wider than its box (clipped or overflowing content). */
async function expectNoHorizontalOverflow(root: Locator): Promise<void> {
  const overflow = await root.evaluate((el) =>
    [el, ...Array.from(el.querySelectorAll('*'))]
      .map((node) => ({ node: `${node.tagName}.${String((node as HTMLElement).className)}`, over: node.scrollWidth - node.clientWidth }))
      .filter((entry) => entry.over > 0),
  );
  expect(overflow, 'elements with horizontally clipped content').toEqual([]);
}

/** Every box must lie within the container's box (left and right edge). */
async function expectInside(container: Locator, boxes: Locator[]): Promise<void> {
  const outer = (await container.boundingBox())!;
  for (const locator of boxes) {
    const box = (await locator.boundingBox())!;
    expect(box.x, 'inside (left)').toBeGreaterThanOrEqual(outer.x - 0.5);
    expect(box.x + box.width, 'inside (right)').toBeLessThanOrEqual(outer.x + outer.width + 0.5);
  }
}

test('long bell titles stay inside the bell card and the bells page @N3', async ({ page }) => {
  // the seed has no long titles: the newest bells get the longest German titles of the owner's report
  const long = ['bell.STORE_MANAGER_APPOINTED', 'bell.PICKUP_SIGNUP_UNCONFIRMED', 'bell.FSP_CLEANING_SIGNUP_UNCONFIRMED'];
  await page.route(
    (url) => url.pathname === '/api/bells',
    async (route) => {
      const response = await route.fetch();
      const body = await response.json();
      body.items.slice(0, long.length).forEach((item: BellDto, index: number) => {
        item.titleKey = long[index];
        item.type = long[index].replace('bell.', '');
      });
      await route.fulfill({ response, json: body });
    },
  );
  const longest = 'Du bist jetzt Betriebsverantwortliche·r';

  if (isPhone(page)) {
    await page.goto('/bells');
    await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
    await expect(page.getByTestId('bells.page.item.title').first()).toHaveText(longest);
    const list = page.getByTestId('bells.page.state');
    await expectNoHorizontalOverflow(list);
    await expectInside(list, await page.getByTestId('bells.page.item').all());
    return;
  }

  // the side menu card at full width, then opened from the collapsed rail
  for (const { width, collapse } of [
    { width: 1280, collapse: false },
    { width: 1000, collapse: true },
  ]) {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/dashboard');
    if (collapse) await collapseSidenav(page);
    await page.getByTestId('bells.button').click();
    await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', 'success');
    await expect(page.getByTestId('bells.panel.item.title').first()).toHaveText(longest);
    const panel = page.getByTestId('bell.ui.panel');
    const card = page.locator('#sn-card-bells');
    await expectNoHorizontalOverflow(card);
    await expectNoHorizontalOverflow(panel);
    await expectInside(card, [panel, page.getByTestId('bells.panel.markAllRead'), ...(await page.getByTestId('bells.panel.item').all())]);
  }
});
