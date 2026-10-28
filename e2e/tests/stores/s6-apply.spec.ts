import type { Page } from '@playwright/test';
import { type ApiClient, acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { NAMES, ZURICH, confirmWithMessage, openStore, toast } from './_stores';

/**
 * S6 applications. Seed: guide@local (Julien Favre) is in no team of S-01 (OPEN, requires verified + application
 * text, manager bieb), S-02 (requires a hygiene certificate) or S-04 (CLOSED); guide has applied to S-07 (manager
 * welcome). Non-members open the region store list to apply.
 */

test.use({ seedReset: 'test' });

interface BellPage {
  items: { type: string; titleKey: string; createdAt: string }[];
}

async function storeTeamBells(api: ApiClient, type: string): Promise<number> {
  const page = await api.getJson<BellPage>('/api/bells', { params: { size: 50 } });
  return page.items.filter((bell) => bell.type === type).length;
}

/** Opens the apply dialog of a store from the region list. */
async function applyFromList(page: Page, name: string): Promise<void> {
  await page.goto(`/regions/${ZURICH}/stores`);
  await expectDataState(page, 'stores.region.state', 'success');
  await page.getByTestId('stores.region.search').fill(name);
  const row = page.locator(`[data-testid="stores.region.row"][data-store="${name}"]`);
  await row.getByTestId('stores.region.apply').click();
  await expect(page.getByTestId('stores.apply.text')).toBeVisible();
}

test.describe('guide', () => {
  test.use({ role: 'guide' });

  test('S6 foodsaver applies with text, manager accepts, bell received @S6 @page', async ({ page, api, as }) => {
    const bellsBefore = await storeTeamBells(api, 'STORE_REQUEST_ACCEPTED');
    await applyFromList(page, 'Coop Bahnhofbrücke');
    // S-01 asks for an application text
    await expect(page.getByTestId('stores.apply.requirements')).toHaveCount(0);
    await page.getByTestId('stores.apply.submit').click();
    await expect(page.getByTestId('stores.apply.error')).toHaveAttribute('data-code', 'APPLICATION_TEXT_REQUIRED');
    await page.getByTestId('stores.apply.text').fill('Ich wohne gleich um die Ecke und habe abends Zeit.');
    await page.getByTestId('stores.apply.submit').click();
    await expect(page.getByTestId('stores.apply.text')).toBeHidden();
    await expect(toast(page)).toBeVisible();
    await expect(
      page.locator('[data-testid="stores.region.row"][data-store="Coop Bahnhofbrücke"] fs-membership-badge'),
    ).toBeVisible();

    const bieb = await as('bieb');
    await openStore(bieb, 'S-01', 'team');
    const application = bieb.locator(`[data-testid="stores.team.application"][data-user="${NAMES.guide}"]`);
    await expect(application.getByTestId('stores.team.applicationText')).toContainText('um die Ecke');
    await application.getByTestId('stores.team.accept').click();
    await expect(
      bieb.getByTestId('stores.team.members').locator(`[data-testid="stores.team.member"][data-user="${NAMES.guide}"]`),
    ).toBeVisible();

    await expect.poll(() => storeTeamBells(api, 'STORE_REQUEST_ACCEPTED')).toBeGreaterThan(bellsBefore);
    await openStore(page, 'S-01');
    await expect(page.getByTestId('stores.page.membership')).toBeVisible();
    await expect(page.getByTestId('stores.membership.leave')).toBeVisible();
  });

  test('S6 decline requires a message and notifies @S6', async ({ api, as, apiOf }) => {
    const bellsBefore = await storeTeamBells(api, 'STORE_REQUEST_DECLINED');
    const welcome = await as('welcome');
    await acceptPendingConsents(apiOf(welcome));
    await openStore(welcome, 'S-07', 'team');
    const application = welcome.locator(`[data-testid="stores.team.application"][data-user="${NAMES.guide}"]`);
    await application.getByTestId('stores.team.decline').click();
    await welcome.getByTestId('stores.team.messageDialog.message').fill('zu kurz');
    await welcome.getByTestId('stores.team.messageDialog.confirm').click();
    await expect(welcome.locator('.p-dialog')).toContainText('10');
    await confirmWithMessage(welcome, 'Das Team ist im Moment leider komplett, melde dich im Frühling wieder.');
    await expect(application).toHaveCount(0);
    await expect.poll(() => storeTeamBells(api, 'STORE_REQUEST_DECLINED')).toBeGreaterThan(bellsBefore);
  });

  test('S6 apply to closed team is refused @S6', async ({ page, api, store }) => {
    // no apply button for closed teams …
    await page.goto(`/regions/${ZURICH}/stores`);
    await page.getByTestId('stores.region.search').fill('Denner Langstrasse');
    const row = page.locator('[data-testid="stores.region.row"][data-store="Denner Langstrasse"]');
    await expect(row.locator('fs-team-status-badge')).toHaveAttribute('data-status', 'CLOSED');
    await expect(row.getByTestId('stores.region.apply')).toHaveCount(0);
    // … and the server refuses as well
    const response = await api.post(`/api/stores/${store.id('S-04')}/applications`, { text: 'Ich hätte Zeit.' });
    expect(response.status()).toBe(409);
    expect((await response.json()).code).toBe('TEAM_CLOSED');
  });

  test('S6 hygiene requirement blocks application with reason @S6', async ({ page }) => {
    await applyFromList(page, 'Bäckerei Vohdin');
    await page.getByTestId('stores.apply.text').fill('Ich helfe gerne mit.');
    await page.getByTestId('stores.apply.submit').click();
    const error = page.getByTestId('stores.apply.error');
    await expect(error).toHaveAttribute('data-code', 'HYGIENE_CERTIFICATE_REQUIRED');
    await expect(error).not.toBeEmpty();
    await expect(page.getByTestId('stores.apply.text')).toBeVisible();
  });

  test('S6 cancel own application @S6', async ({ page }) => {
    await page.goto('/stores/mine');
    await expectDataState(page, 'stores.mine.state', 'success');
    const applied = page.getByTestId('stores.mine.group.applied');
    await expect(applied).toContainText('Hiltl');
    await applied.locator('[data-testid="stores.mine.application"][data-store="Hiltl"]').getByTestId('stores.mine.cancelApplication').click();
    await expect(toast(page)).toBeVisible();
    await expect(page.getByTestId('stores.mine.group.applied')).toHaveCount(0);
    // the region list offers to apply again
    await page.goto(`/regions/${ZURICH}/stores`);
    await page.getByTestId('stores.region.search').fill('Hiltl');
    await expect(page.locator('[data-testid="stores.region.row"][data-store="Hiltl"]').getByTestId('stores.region.apply')).toBeVisible();
  });
});
