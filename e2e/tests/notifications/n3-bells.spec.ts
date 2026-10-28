import type { Page } from '@playwright/test';
import {
  type ApiClient,
  acceptPendingConsents,
  type BellDto,
  LANGUAGES,
  expect,
  expectErrorState,
  expectNoMissingKeys,
  injectApiError,
  setLanguage,
  test,
} from '../../fixtures';

/** N3: bells in the header (badge, panel), the /bells page and realtime delivery over /ws. */
test.use({ seedReset: 'test' });

async function unread(api: ApiClient): Promise<number> {
  return (await api.getJson<{ count: number }>('/api/bells/unread-count')).count;
}

/** Below 48rem the header bell is hidden: the tab bar's "Menu" carries the badge and the drawer links /bells. */
function isMobile(page: Page): boolean {
  return (page.viewportSize()?.width ?? 1280) < 768;
}

/** The visible unread badge (header bell on desktop, "Menu" tab on mobile). */
function badge(page: Page) {
  return page.getByTestId(isMobile(page) ? 'nav.tab.menu.badge' : 'bells.badge');
}

/** Opens the bell list: the header panel on desktop, the /bells page through the menu drawer on mobile. */
async function openBells(page: Page): Promise<string> {
  if (isMobile(page)) {
    await page.getByTestId('nav.tab.menu').click();
    await expect(page.getByTestId('nav.drawer.bells.badge')).toBeVisible();
    await page.getByTestId('nav.drawer.bells').click();
    await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
    return 'bells.page.item';
  }
  await page.getByTestId('bells.button').click();
  await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', 'success');
  return 'bells.panel.item';
}

test('N3 header badge counts unread bells and panel marks read on click @N3 @page', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  const api = apiOf(page);
  const before = await unread(api);
  expect(before).toBeGreaterThan(1);
  await page.goto('/dashboard');
  await expect(badge(page)).toHaveText(String(before));

  const item = await openBells(page);
  if (!isMobile(page)) {
    expect(await page.getByTestId(item).count()).toBeLessThanOrEqual(10);
  }
  // the newest unread bell with a link: click → read and opened
  const bells = (await api.getJson<{ items: BellDto[] }>('/api/bells?size=10')).items;
  const target = bells.find((bell) => !bell.readAt && bell.link)!;
  await page.locator(`[data-testid="${item}"][data-id="${target.id}"]`).getByTestId(`${item}.open`).click();
  await expect(page.getByTestId('bells.panel')).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`${target.link!.split('?')[0].replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`));
  await expect(badge(page)).toHaveText(String(before - 1));
  await expect.poll(() => unread(api)).toBe(before - 1);

  // mark all read: the badge disappears
  if (isMobile(page)) {
    await openBells(page);
    await page.getByTestId('bells.page.markAllRead').click();
  } else {
    await openBells(page);
    await page.getByTestId('bells.panel.markAllRead').click();
    await expect(page.locator('[data-testid="bells.panel.item"][data-unread="true"]')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('bells.panel')).toHaveCount(0);
  }
  await expect(badge(page)).toHaveCount(0);
  expect(await unread(api)).toBe(0);
});

test('N3 bells page filters unread, deletes one and all read @N3', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  const api = apiOf(page);
  const all = (await api.getJson<{ items: BellDto[]; page: { totalItems: number } }>('/api/bells?size=50')).page.totalItems;
  const unreadCount = await unread(api);
  await page.goto('/bells');
  const state = page.getByTestId('bells.page.state');
  await expect(state).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('bells.page.item')).toHaveCount(Math.min(all, 20));

  // unread filter
  await page.getByTestId('bells.page.filter').getByRole('button', { name: 'Ungelesen' }).click();
  await expect(page.getByTestId('bells.page.item')).toHaveCount(unreadCount);
  await expect(page.locator('[data-testid="bells.page.item"][data-unread="false"]')).toHaveCount(0);

  // delete one
  const first = page.getByTestId('bells.page.item').first();
  const id = await first.getAttribute('data-id');
  await first.getByTestId('bells.page.item.delete').click();
  await expect(page.locator(`[data-testid="bells.page.item"][data-id="${id}"]`)).toHaveCount(0);
  await expect(page.getByTestId('bells.page.item')).toHaveCount(unreadCount - 1);

  // all: delete the read ones, only unread stay
  await page.getByTestId('bells.page.filter').getByRole('button', { name: 'Alle' }).click();
  await page.getByTestId('bells.page.deleteRead').click();
  await expect(page.locator('[data-testid="bells.page.item"][data-unread="false"]')).toHaveCount(0);
  await expect(page.getByTestId('bells.page.item')).toHaveCount(unreadCount - 1);

  // mark all read on the page, then delete read leaves the empty state
  await page.getByTestId('bells.page.markAllRead').click();
  await expect(page.locator('[data-testid="bells.page.item"][data-unread="true"]')).toHaveCount(0);
  await page.getByTestId('bells.page.deleteRead').click();
  await expect(state).toHaveAttribute('data-state', 'empty');
  await expect(badge(page)).toHaveCount(0);
});

test('N3 every bell type renders a title in five languages @N3', async ({ as }) => {
  for (const role of ['foodsaver', 'bieb'] as const) {
    const page = await as(role);
    for (const lang of LANGUAGES) {
      await setLanguage(page, lang);
      await page.goto('/bells');
      await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
      const titles = await page.getByTestId('bells.page.item.title').allTextContents();
      expect(titles.length, `${role} ${lang}`).toBeGreaterThan(0);
      for (const title of titles) {
        expect(title.trim(), `${role} ${lang}`).not.toMatch(/^bell\./);
        expect(title, `${role} ${lang}`).not.toContain('{');
      }
      await expectNoMissingKeys(page);
    }
  }
});

test('N3 a new bell arrives over the realtime connection without reload @N3', async ({ as, apiOf, region, notifications }) => {
  const welcome = await as('welcome');
  await acceptPendingConsents(apiOf(welcome));
  await welcome.goto('/dashboard');
  await notifications.waitForRealtime('welcome');
  const before = Number((await welcome.getByTestId('bells.button').getAttribute('data-unread')) ?? '0');

  // newbie joins Zürich: the welcome team gets REGION_NEW_MEMBER (polling would take 60 s; the test waits 10 s)
  await region.joinRegion('newbie', 'zuerich-stadt');
  await expect(welcome.getByTestId('bells.button')).toHaveAttribute('data-unread', String(before + 1), { timeout: 10_000 });
  const item = await openBells(welcome);
  await expect(welcome.locator(`[data-testid="${item}"][data-type="REGION_NEW_MEMBER"]`).first()).toBeVisible();
});

test('N3 bells error states: list failure with retry, failed mark-read keeps the bell unread @N3', async ({ as }) => {
  const page = await as('foodsaver');
  const failure = await injectApiError(page, { url: /\/api\/bells(\?|$)/, method: 'GET', status: 500 });
  await page.goto('/bells');
  const { retry } = await expectErrorState(page, 'bells.page.state');
  expect(failure.calls()).toBeGreaterThan(0);
  await failure.remove();
  await retry.click();
  await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');

  await injectApiError(page, { url: '**/api/bells/read', method: 'POST', status: 429 });
  await page.getByTestId('bells.page.filter').getByRole('button', { name: 'Ungelesen' }).click();
  const first = page.getByTestId('bells.page.item').first();
  const id = await first.getAttribute('data-id');
  await first.getByTestId('bells.page.item.open').click();
  await page.goto('/bells');
  await page.getByTestId('bells.page.filter').getByRole('button', { name: 'Ungelesen' }).click();
  await expect(page.locator(`[data-testid="bells.page.item"][data-id="${id}"]`)).toHaveAttribute('data-unread', 'true');

  // the header panel (desktop) shows the error state too
  if (!isMobile(page)) {
    await injectApiError(page, { url: /\/api\/bells\?size=10/, method: 'GET', status: 503 });
    await page.getByTestId('bells.button').click();
    await expectErrorState(page, 'bells.panel.state');
  }
});
