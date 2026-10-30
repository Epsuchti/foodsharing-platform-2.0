import type { Page } from '@playwright/test';
import { FSPS, acceptPendingConsents, expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, seedStore, test } from '../../fixtures';

/**
 * C5 global search (plan-m7 Q12, A55). Seed: foodsaver (Sarah Müller) is a member of zuerich-stadt and follows the
 * FSP Wiedikon (own search index); "Wiedikon" also finds the store Coop Wiedikon and the forum thread "Fairteiler
 * Wiedikon: Kühlschrank defekt". Users are visible by home region: Nina Schneider (bieb) lives in zuerich-stadt, Lea
 * Brunner (newbie) has no region, so only ORGA finds her.
 */
const BIEB = '00000001-0001-4000-8000-000000000009';
const NEWBIE = '00000001-0001-4000-8000-000000000001';

const input = (page: Page) => page.getByTestId('search.global.input');
const option = (page: Page, link: string) => page.locator(`[data-testid="search.global.option"][data-link="${link}"]`);

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

  test('C5 instant index results appear without a request @C5', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    const searches: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/search') searches.push(request.url());
    });
    await page.goto('/dashboard');
    const index = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/search/index');
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
    await input(page).fill('Nina');
    await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', 'error');

    await injectApiError(page, { url: '/api/search', status: 503, times: 3 });
    await page.goto('/search?q=Nina');
    const { retry } = await expectErrorState(page, 'search.page.state');
    await retry.click();
    await expectDataState(page, 'search.page.state', 'success');
  });
});
