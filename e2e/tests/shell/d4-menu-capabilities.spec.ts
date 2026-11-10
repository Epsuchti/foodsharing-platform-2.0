import type { Page } from '@playwright/test';
import { expect, test } from '../../fixtures';

/**
 * Menu visibility from server capabilities (owner feedback 2026-11-04, decision 162): admin of a working group is not
 * enough. Nina (bieb, admin of "Betriebskoordination Zürich") sees only Betriebsketten under Verwalten and no Orga.
 */

const DESKTOP = { width: 1280, height: 900 };

/** Ids of the side menu items of one section (`data-nav-section`). */
async function sectionItems(page: Page, section: string): Promise<string[]> {
  return page
    .locator(`a[data-nav-section="${section}"]`)
    .evaluateAll((links) => links.map((link) => (link.getAttribute('data-testid') ?? '').replace(/^nav\./, '')));
}

test('D4.3 working-group admin sees only store chains under Verwalten and no Orga (UI polish #15) @D4.3', async ({ as }) => {
  const nina = await as('bieb');
  await nina.setViewportSize(DESKTOP);
  const session = await (await nina.request.get('/api/auth/session')).json();
  expect(session.capabilities).toEqual([]);
  await nina.goto('/dashboard');
  await expect(nina.getByTestId('nav.dashboard')).toBeVisible();
  expect(await sectionItems(nina, 'manage')).toEqual(['storeChains']);
  expect(await sectionItems(nina, 'orga')).toEqual([]);
  await expect(nina.getByTestId('nav.section.orga')).toHaveCount(0);
});

test('D4.3 foodsaver has no Verwalten, ambassador manages users and newsletters (UI polish #15) @D4.3', async ({ as }) => {
  const foodsaver = await as('foodsaver');
  await foodsaver.setViewportSize(DESKTOP);
  await foodsaver.goto('/dashboard');
  await expect(foodsaver.getByTestId('nav.dashboard')).toBeVisible();
  expect(await sectionItems(foodsaver, 'manage')).toEqual([]);

  const ambassador = await as('bot');
  await ambassador.setViewportSize(DESKTOP);
  await ambassador.goto('/dashboard');
  await expect(ambassador.getByTestId('nav.dashboard')).toBeVisible();
  expect(await sectionItems(ambassador, 'manage')).toEqual(expect.arrayContaining(['newcomers', 'verification', 'newsletters', 'storeChains']));
  // D1 (review 2026-11-10): the members list is the region's Members tab, not a menu item
  expect(await sectionItems(ambassador, 'manage')).not.toContain('users');
});
