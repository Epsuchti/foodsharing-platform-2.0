import type { Page } from '@playwright/test';
import { FSPS, acceptPendingConsents, expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, seedStore, test } from '../../fixtures';

/**
 * C5 global search (plan-m7 Q12, A55, decision 147). Seed: foodsaver (Sarah Müller) is a member of zuerich-stadt and
 * of the group Kochgruppe Zürich and follows the FSP Wiedikon (own search index); "Wiedikon" also finds the store Coop
 * Wiedikon (Birmensdorferstrasse 150) and the forum thread "Fairteiler Wiedikon: Kühlschrank defekt". People search is
 * contact-based: Nina Schneider (bieb) is a confirmed buddy and shares zuerich-stadt, Lea Brunner (newbie) shares
 * nothing with her, so only ORGA finds her. Only assertions that hold on the H2 (LIKE) path as well.
 */
const BIEB = '00000001-0001-4000-8000-000000000009';
const NEWBIE = '00000001-0001-4000-8000-000000000001';
const KOCHGRUPPE = '00000011-0001-4000-8000-000000000033';
/** "Willkommen im Forum Zürich Stadt": "Werbung" only occurs in the text of its first post. */
const WELCOME_THREAD = '00000043-0001-4000-8000-000000000001';

const pageHit = (page: Page, link: string) => page.locator(`[data-testid="search.page.hit"][data-link="${link}"]`);

const input = (page: Page) => page.getByTestId('search.global.input');
const option = (page: Page, link: string) => page.locator(`[data-testid="search.global.option"][data-link="${link}"]`);

/**
 * Phones show the header search behind a search icon (`shell.searchToggle`): tap it to open the field. On wider
 * screens the field is always there and the toggle is hidden.
 */
async function openSearch(page: Page): Promise<void> {
  await expect(page.locator('[data-testid="shell.searchToggle"]:visible, [data-testid="search.global.input"]:visible').first()).toBeVisible();
  const toggle = page.getByTestId('shell.searchToggle');
  if (await toggle.isVisible()) {
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click();
    await expect(input(page)).toBeFocused();
  }
  await expect(input(page)).toBeVisible();
}

/** The server search answered for the current text (not only the instant index results). */
async function serverAnswered(page: Page): Promise<void> {
  await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', /^(success|empty)$/);
}

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test("C5 header search finds users of my region, stores, threads and FSPs; other regions' users hidden; orga searches globally @C5 @page", async ({
    page,
    as,
    apiOf,
  }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/dashboard');
    await expect(input(page)).toHaveAttribute('role', 'combobox');
    await openSearch(page);

    // stores, threads and FSPs of the region
    await input(page).fill('Wiedikon');
    await expect(input(page)).toHaveAttribute('aria-expanded', 'true');
    await serverAnswered(page);
    await expect(option(page, `/stores/${seedStore('Coop Wiedikon').id}`)).toHaveCount(1);
    await expect(option(page, `/food-share-points/${FSPS.wiedikon}`)).toHaveCount(1);
    await expect(page.locator('[data-testid="search.global.group"][data-group="remote.threads"]')).toContainText('Kühlschrank defekt');
    // the open combobox and its grouped listbox pass axe
    await expectNoSeriousViolations(page);

    // a user of the own region, opened with the keyboard
    await input(page).fill('Nina Schneider');
    await serverAnswered(page);
    await expect(page.locator('[data-testid="search.global.group"][data-group="remote.users"]')).toContainText('Nina Schneider');
    await input(page).press('ArrowDown');
    const active = await input(page).getAttribute('aria-activedescendant');
    expect(active).toBeTruthy();
    await expect(page.locator(`#${active}`)).toHaveAttribute('data-link', `/profile/${BIEB}`);
    await input(page).press('Enter');
    await expect(page).toHaveURL(new RegExp(`/profile/${BIEB}$`));
    await expect(input(page)).toHaveValue('');

    // a user without a region stays hidden; Enter opens the full search page
    await openSearch(page);
    await input(page).fill('Lea Brunner');
    await expect(page.getByTestId('search.global.empty')).toBeVisible();
    await expect(option(page, `/profile/${NEWBIE}`)).toHaveCount(0);
    await input(page).press('Enter');
    await expect(page).toHaveURL(/\/search\?q=Lea(%20|\+)Brunner$/);
    await expectDataState(page, 'search.page.state', 'empty');

    // ORGA searches everywhere
    const orga = await as('orga');
    await acceptPendingConsents(apiOf(orga));
    await orga.goto('/search?q=Lea%20Brunner');
    await expectDataState(orga, 'search.page.state', 'success');
    await expect(orga.locator(`[data-testid="search.page.hit"][data-link="/profile/${NEWBIE}"]`)).toBeVisible();

    // the search page filters by kind
    await page.goto('/search?q=Wiedikon');
    await expectDataState(page, 'search.page.state', 'success');
    await page.locator('[data-testid="search.page.kind"][data-kind="STORE"]').click();
    await expect(page.getByTestId('search.page.group')).toHaveCount(1);
    await expect(page.locator(`[data-testid="search.page.hit"][data-link="/stores/${seedStore('Coop Wiedikon').id}"]`)).toBeVisible();
  });

  test('C5 event hits show their start in the one date format, not a raw ISO date (review 2026-11-10 F5) @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    const brunch = '/events/00000044-0002-4000-8000-000000000001';
    // decision 177: "05.11.2026, 10:00" (dd.MM.yyyy, HH:mm in the user's time zone)
    const formatted = /^05\.11\.2026, \d{2}:\d{2}$/;
    await page.goto('/dashboard');
    await openSearch(page);
    await input(page).fill('Brunch');
    await serverAnswered(page);
    await expect(option(page, brunch).locator('.gs__option-sub')).toHaveText(formatted);
    await expect(option(page, brunch)).not.toContainText('2026-11-05');

    await page.goto('/search?q=Brunch');
    await expectDataState(page, 'search.page.state', 'success');
    await expect(pageHit(page, brunch).locator('.hit__sub')).toHaveText(formatted);
  });

  test('C5 instant index results appear without a request @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    const searches: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/search') searches.push(request.url());
    });
    await page.goto('/dashboard');
    const index = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/search/index');
    await openSearch(page);
    await input(page).click();
    await index;

    // one character: matched in the browser only (the server needs two)
    await input(page).fill('W');
    await expect(option(page, `/food-share-points/${FSPS.wiedikon}`)).toBeVisible();
    await expect(page.locator('[data-testid="search.global.group"][data-group="index.foodSharePoints"]')).toBeVisible();
    await page.waitForTimeout(800);
    expect(searches).toEqual([]);

    // with more characters the index hit is there at once, before the debounced server request
    await input(page).fill('Fairteiler Wied');
    await expect(option(page, `/food-share-points/${FSPS.wiedikon}`)).toBeVisible();
    expect(searches).toEqual([]);
    await serverAnswered(page);
    expect(searches).toHaveLength(1);
    // shown once although the server finds it as well
    await expect(option(page, `/food-share-points/${FSPS.wiedikon}`)).toHaveCount(1);
  });

  test('C5 header search and search page show error states with retry @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    // the retry interceptor repeats GETs twice: three failures reach the error state
    await injectApiError(page, { url: '/api/search', status: 503, times: 3 });
    await page.goto('/dashboard');
    await openSearch(page);
    await input(page).fill('Nina');
    await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', 'error');

    await injectApiError(page, { url: '/api/search', status: 503, times: 3 });
    await page.goto('/search?q=Nina');
    const { retry } = await expectErrorState(page, 'search.page.state');
    await retry.click();
    await expectDataState(page, 'search.page.state', 'success');
  });

  test('C5 own groups come first; a store address needs a second word @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));

    // many "… Zürich" groups match; the own one (Kochgruppe) leads although others sort before it by name
    await page.goto('/search?q=Z%C3%BCrich');
    await expectDataState(page, 'search.page.state', 'success');
    const groups = page.locator('[data-testid="search.page.group"][data-group="remote.groups"] [data-testid="search.page.hit"]');
    await expect(groups.first()).toHaveAttribute('data-link', `/groups/${KOCHGRUPPE}`);
    expect(await groups.count()).toBeGreaterThan(1);

    // the street alone does not find the store, name + street does
    const coop = `/stores/${seedStore('Coop Wiedikon').id}`;
    await page.goto('/search?q=Birmensdorferstrasse');
    await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', /^(success|empty)$/);
    await expect(pageHit(page, coop)).toHaveCount(0);
    await page.goto('/search?q=Coop%20Birmensdorferstrasse');
    await expectDataState(page, 'search.page.state', 'success');
    await expect(pageHit(page, coop)).toBeVisible();
  });

  test('C5 the search rate limit (429) shows its own message with retry @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await injectApiError(page, { url: '/api/search', status: 429, retryAfterSeconds: 42, times: 1 });
    await page.goto('/search?q=Nina');
    const { root, retry } = await expectErrorState(page, 'search.page.state');
    await expect(root).toContainText(/Zu viele Suchanfragen|Too many searches/);
    await expect(root).toContainText('42');
    await retry.click();
    await expectDataState(page, 'search.page.state', 'success');

    // the header search shows the same message in its popup
    await injectApiError(page, { url: '/api/search', status: 429, retryAfterSeconds: 42, times: 1 });
    await page.goto('/dashboard');
    await openSearch(page);
    await input(page).fill('Nina');
    await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', 'error');
    await expect(page.getByTestId('search.global.state')).toContainText(/Zu viele Suchanfragen|Too many searches/);
  });
});

test.describe('forum post contents', () => {
  test.use({ role: 'foodsaver', seedReset: 'test' });

  test('C5 forum posts are searched only with SEARCH_FORUM_POSTS on; the hit shows a snippet @C5', async ({ page, as, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/search?q=Werbung');
    await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', /^(success|empty)$/);
    await expect(pageHit(page, `/forum/threads/${WELCOME_THREAD}`)).toHaveCount(0);

    const orga = await as('orga');
    const switched = await apiOf(orga).patch('/api/admin/feature-toggles/SEARCH_FORUM_POSTS', { enabled: true });
    expect(switched.status()).toBe(200);

    await page.goto('/search?q=Werbung');
    await expectDataState(page, 'search.page.state', 'success');
    const hit = pageHit(page, `/forum/threads/${WELCOME_THREAD}`);
    await expect(hit).toBeVisible();
    await expect(hit).toContainText('keine Werbung');
  });
});
