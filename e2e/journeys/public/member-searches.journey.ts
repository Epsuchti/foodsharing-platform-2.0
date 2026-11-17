import type { Page } from '@playwright/test';
import { FSPS, acceptPendingConsents, expectDataState, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local, Zürich Stadt, follows the FSP Wiedikon) searches (C5, plan-m7 Q12, A55, decision
 * 147): the header combobox shows instant index hits before the server answers (one character never asks the server),
 * then stores, threads and FSPs of her region in groups; she opens a buddy's profile with the keyboard; a person she
 * shares nothing with stays hidden and Enter opens the full search page, which filters by kind; an event hit shows its
 * start in the one date format (decision 177).
 *
 * Rules behind it: SearchVisibilityTest (visibility matrix: orga everywhere, members their regions), SearchServiceTest
 * (forum posts only with SEARCH_FORUM_POSTS, snippets, the address indexed apart from the name, rate limit 429 with
 * Retry-After), SearchPostgresIT (own regions/groups/stores first, address only with two words, contact-based people
 * search, forum post contents), search-pages.spec (combobox, debounce, keyboard, error and 429 states with retry,
 * event dates), search.util.spec (index matching, grouping, dedup of server hits).
 */
test.use({ role: 'foodsaver' });

const BIEB = '00000001-0001-4000-8000-000000000009';
const NEWBIE = '00000001-0001-4000-8000-000000000001';
const BRUNCH = '/events/00000044-0002-4000-8000-000000000001';

const input = (page: Page) => page.getByTestId('search.global.input');
const option = (page: Page, link: string) => page.locator(`[data-testid="search.global.option"][data-link="${link}"]`);
const pageHit = (page: Page, link: string) => page.locator(`[data-testid="search.page.hit"][data-link="${link}"]`);

/** The server search answered for the current text (not only the instant index results). */
async function serverAnswered(page: Page): Promise<void> {
  await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', /^(success|empty)$/);
}

test('member searches her region from the header and on the search page @C5', async ({ page, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'foodsaver');
  const coop = `/stores/${seedStore('Coop Wiedikon').id}`;
  const fsp = `/food-share-points/${FSPS.wiedikon}`;
  const searches: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/search') searches.push(request.url());
  });

  /** Phones show the header search behind a search icon: tap it to open the field. */
  const openSearch = async () => {
    await expect(page.locator('[data-testid="shell.searchToggle"]:visible, [data-testid="search.global.input"]:visible').first()).toBeVisible();
    const toggle = page.getByTestId('shell.searchToggle');
    if ((await toggle.isVisible()) && (await toggle.getAttribute('aria-expanded')) !== 'true') {
      await walk.click(toggle);
      await expect(input(page)).toBeFocused();
    }
    await expect(input(page)).toBeVisible();
  };

  await walk.step('types a first letter and sees her Fairteiler at once', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(input(page)).toHaveAttribute('role', 'combobox');
    const index = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/search/index');
    await openSearch();
    await walk.click(input(page));
    await index;
    // one character: matched in the browser only (the server needs two)
    await walk.fill(input(page), 'W');
    await expect(option(page, fsp)).toBeVisible();
    await expect(page.locator('[data-testid="search.global.group"][data-group="index.foodSharePoints"]')).toBeVisible();
    await page.waitForTimeout(800);
    expect(searches).toEqual([]);
  });

  await walk.step('searches Wiedikon and finds a store, a Fairteiler and a thread', async () => {
    await walk.fill(input(page), 'Wiedikon');
    // the index hit is there at once, before the debounced server request
    await expect(option(page, fsp)).toBeVisible();
    await expect(input(page)).toHaveAttribute('aria-expanded', 'true');
    await serverAnswered(page);
    expect(searches).toHaveLength(1);
    await expect(option(page, coop)).toHaveCount(1);
    // shown once although the server finds it as well
    await expect(option(page, fsp)).toHaveCount(1);
    await expect(page.locator('[data-testid="search.global.group"][data-group="remote.threads"]')).toContainText('Kühlschrank defekt');
    await checkpoint(page, 'header-search', { fullPage: false });
  });

  await walk.step("opens Nina's profile with the keyboard", async () => {
    await walk.fill(input(page), 'Nina Schneider');
    await serverAnswered(page);
    await expect(page.locator('[data-testid="search.global.group"][data-group="remote.users"]')).toContainText('Nina Schneider');
    await input(page).press('ArrowDown');
    const active = await input(page).getAttribute('aria-activedescendant');
    expect(active).toBeTruthy();
    await expect(page.locator(`#${active}`)).toHaveAttribute('data-link', `/profile/${BIEB}`);
    await input(page).press('Enter');
    await expect(page).toHaveURL(new RegExp(`/profile/${BIEB}$`));
    await expect(input(page)).toHaveValue('');
  });

  await walk.step('looks for someone outside her contacts and opens the full search', async () => {
    await openSearch();
    await walk.fill(input(page), 'Lea Brunner');
    await expect(page.getByTestId('search.global.empty')).toBeVisible();
    await expect(option(page, `/profile/${NEWBIE}`)).toHaveCount(0);
    await input(page).press('Enter');
    await expect(page).toHaveURL(/\/search\?q=Lea(%20|\+)Brunner$/);
    await expectDataState(page, 'search.page.state', 'empty');
    await checkpoint(page, 'search-empty');
  });

  await walk.step('searches Wiedikon on the search page and keeps the stores only', async () => {
    const field = page.getByTestId('search.page.input');
    await walk.fill(field, 'Wiedikon');
    await field.press('Enter');
    await expect(page).toHaveURL(/\/search\?q=Wiedikon$/);
    await expectDataState(page, 'search.page.state', 'success');
    await expect(pageHit(page, fsp)).toBeVisible();
    await checkpoint(page, 'search-page');
    await walk.click(page.locator('[data-testid="search.page.kind"][data-kind="STORE"]'));
    await expect(page.getByTestId('search.page.group')).toHaveCount(1);
    await expect(pageHit(page, coop)).toBeVisible();
    await expect(pageHit(page, fsp)).toHaveCount(0);
    await checkpoint(page, 'search-stores-only');
    // the kind filter stays for the next query: she lets go of it again
    await walk.click(page.locator('[data-testid="search.page.kind"][data-kind="STORE"]'));
    await expect(pageHit(page, fsp)).toBeVisible();
  });

  await walk.step('finds the brunch with its start date', async () => {
    // decision 177: "05.11.2026, 10:00" (dd.MM.yyyy, HH:mm in the user's time zone), never the raw ISO date
    const formatted = /^05\.11\.2026, \d{2}:\d{2}$/;
    await openSearch();
    await walk.fill(input(page), 'Brunch');
    await serverAnswered(page);
    await expect(option(page, BRUNCH).locator('.gs__option-sub')).toHaveText(formatted);
    await expect(option(page, BRUNCH)).not.toContainText('2026-11-05');
    await input(page).press('Enter');
    await expect(page).toHaveURL(/\/search\?q=Brunch$/);
    await expectDataState(page, 'search.page.state', 'success');
    await expect(pageHit(page, BRUNCH).locator('.hit__sub')).toHaveText(formatted);
  });

  // desktop 3 clicks; the phone opens the header search behind its icon three times (6)
  walk.finish({ steps: 6, clicks: 6, fields: 6 });
});
