import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';
import { acceptConfirm, openStore } from './_stores';

/**
 * S11 store wall (decision 54). Seed S-01 (docs/seed-ids.md, store wall): milestones STORE_CREATED and
 * COOPERATION_STARTED, `…-83` by foodsaver (40 days old), `…-84` by bieb (1 day old). jumper is a JUMPER of S-01.
 */
const OLD_FOODSAVER_POST = '00000020-0008-4000-8000-000000000083';
const NEW_BIEB_POST = '00000020-0008-4000-8000-000000000084';

const post = (page: Page, id: string) => page.locator(`[data-testid="stores.wall.post"][data-post-id="${id}"]`);

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver', seedReset: 'test' });

  test('S11 members post, jumper cannot see wall, milestones shown @S11 @page', async ({ page, as }) => {
    await openStore(page, 'S-01', 'wall');
    await expectDataState(page, 'stores.wall.state', 'success');
    await expect(page.locator('[data-testid="stores.wall.post"][data-milestone="COOPERATION_STARTED"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="stores.wall.post"][data-milestone="STORE_CREATED"]')).toHaveCount(1);
    await expect(page.getByTestId('stores.wall.milestone').first()).toBeVisible();

    const text = `Schlüssel liegt beim Hintereingang ${Date.now()}`;
    await page.getByTestId('stores.wall.text').fill(text);
    await page.getByTestId('stores.wall.submit').click();
    await expect(page.getByTestId('stores.wall.text')).toHaveValue('');
    const first = page.getByTestId('stores.wall.post').first();
    await expect(first.getByTestId('stores.wall.postText')).toHaveText(text);
    // own post: deletable
    await expect(first.getByTestId('stores.wall.delete')).toBeVisible();

    const jumper = await as('jumper');
    await openStore(jumper, 'S-01', 'overview');
    await expect(jumper.getByTestId('stores.page.tab.team')).toBeVisible();
    await expect(jumper.getByTestId('stores.page.tab.wall')).toHaveCount(0);
    await jumper.goto(`${new URL(jumper.url()).pathname.replace(/overview$/, 'wall')}`);
    await expectDataState(jumper, 'stores.wall.state', 'forbidden');
    await expect(jumper.getByTestId('stores.wall.composer')).toHaveCount(0);
  });

  test('S11 delete rules: own, manager older than month, orga any @S11', async ({ page, as }) => {
    await openStore(page, 'S-01', 'wall');
    await expectDataState(page, 'stores.wall.state', 'success');
    // foodsaver: own old post yes, bieb's fresh post no
    await expect(post(page, OLD_FOODSAVER_POST).getByTestId('stores.wall.delete')).toBeVisible();
    await expect(post(page, NEW_BIEB_POST)).toBeVisible();
    await expect(post(page, NEW_BIEB_POST).getByTestId('stores.wall.delete')).toHaveCount(0);

    // manager bieb: foodsaver's post older than 30 days yes; deletes it
    const bieb = await as('bieb');
    await openStore(bieb, 'S-01', 'wall');
    await expectDataState(bieb, 'stores.wall.state', 'success');
    await expect(post(bieb, NEW_BIEB_POST).getByTestId('stores.wall.delete')).toBeVisible();
    await post(bieb, OLD_FOODSAVER_POST).getByTestId('stores.wall.delete').click();
    await acceptConfirm(bieb);
    await expect(post(bieb, OLD_FOODSAVER_POST)).toHaveCount(0);

    // a fresh post of foodsaver: bieb (manager, younger than 30 days) may not delete it, orga may
    const text = `Neue Notiz ${Date.now()}`;
    await page.reload();
    await page.getByTestId('stores.wall.text').fill(text);
    await page.getByTestId('stores.wall.submit').click();
    await expect(page.getByTestId('stores.wall.post').first().getByTestId('stores.wall.postText')).toHaveText(text);
    await bieb.reload();
    const fresh = bieb.getByTestId('stores.wall.post').first();
    await expect(fresh.getByTestId('stores.wall.postText')).toHaveText(text);
    await expect(fresh.getByTestId('stores.wall.delete')).toHaveCount(0);

    const orga = await as('orga');
    await openStore(orga, 'S-01', 'wall');
    await expectDataState(orga, 'stores.wall.state', 'success');
    const count = await orga.getByTestId('stores.wall.post').count();
    await expect(orga.getByTestId('stores.wall.delete')).toHaveCount(count);
  });
});
