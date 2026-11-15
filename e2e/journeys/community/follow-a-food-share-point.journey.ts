import type { Page } from '@playwright/test';
import { FSPS, expectDataState, regionId } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Jonas (jumper@local) finds the Fairteiler Langstrasse on his region's list (Sarah's inactive proposal is not shown to
 * him), looks up the way there and follows it by bell and mail. Nina (bieb@local), its manager, writes on its wall
 * through her own session: Jonas is told by bell and mail, reads the post and finds the Fairteiler among his own. Who
 * may see an inactive Fairteiler and write on a wall is unit-tested (FspPermissionMatrixTest).
 */
test.use({ role: 'jumper' });

const ZURICH = regionId('zuerich-stadt');
const card = (page: Page, fspId: string) => page.locator(`fs-fsp-card[data-fsp-id="${fspId}"]`);

test('foodsaver follows a Fairteiler by bell and mail and reads its new wall post @FS1 @N3 @N6', async ({
  page,
  api,
  as,
  apiOf,
  mails,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'follower');
  const post = 'Neu aufgefüllt: Brot und Äpfel';

  await walk.step('finds the Fairteiler Langstrasse on the region list', async () => {
    await page.goto(`/regions/${ZURICH}/food-share-points`);
    await expectDataState(page, 'foodSharePoints.region.state', 'success');
    await expect(card(page, FSPS.altstetten)).toHaveCount(0);
    await walk.click(card(page, FSPS.langstrasse).getByTestId('foodSharePoints.card.link'));
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}$`));
    await expectDataState(page, 'foodSharePoints.detail.state', 'success');
    await expectDataState(page, 'foodSharePoints.detail.wall.state', 'success');
    await expect(page.getByTestId('foodSharePoints.detail.edit')).toHaveCount(0);
    await checkpoint(page, 'fairteiler', { mask: [page.getByTestId('foodSharePoints.detail.wall').locator('time')] });
  });

  await walk.step('looks up the way there', async () => {
    await walk.click(page.getByTestId('foodSharePoints.detail.navigate'));
    await expect(page.getByTestId('foodSharePoints.detail.navigate.google')).toHaveAttribute('href', /destination=47\.\d+,8\.\d+/);
    await expect(page.getByTestId('foodSharePoints.detail.navigate.apple')).toHaveAttribute('href', /daddr=47\.\d+,8\.\d+/);
    await checkpointElement(page.locator('.p-popover'), 'navigate-menu');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('foodSharePoints.detail.navigate')).toBeFocused();
  });

  await walk.step('follows it by bell and mail', async () => {
    const follow = page.getByTestId('foodSharePoints.detail.follow');
    await expect(follow).toHaveAttribute('data-following', 'false');
    await walk.check(page.locator('#fsp-follow-mail'));
    await walk.click(page.getByTestId('foodSharePoints.follow.submit'));
    await expect(follow).toHaveAttribute('data-following', 'true');
    await expect(page.locator('#fsp-follow-bell')).toBeChecked();
    await expect(page.locator('#fsp-follow-mail')).toBeChecked();
    await expect(page.getByTestId('foodSharePoints.follow.unfollow')).toBeVisible();
    await checkpointElement(follow, 'following');
  });

  await walk.step('is told about Nina\'s wall post and reads it', async () => {
    const written = await apiOf(await as('bieb')).post(`/api/food-share-points/${FSPS.langstrasse}/wall`, { body: post });
    expect(written.status(), await written.text()).toBe(201);
    await openNewestBell(walk, api, 'FSP_NEW_POST');
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.langstrasse}`));
    await expectDataState(page, 'foodSharePoints.detail.wall.state', 'success');
    await expect(page.getByTestId('foodSharePoints.detail.wall.post').filter({ hasText: post })).toHaveCount(1);
    const mail = await mails.waitForMail({ to: 'jumper@local', predicate: (m) => m.text.includes(post) });
    expect(mail.text).toContain('Langstrasse');
  });

  await walk.step('finds it among his Fairteiler', async () => {
    await page.goto('/food-share-points/mine');
    await expectDataState(page, 'foodSharePoints.mine.state', 'success');
    await expect(card(page, FSPS.langstrasse).getByTestId('foodSharePoints.card.following')).toBeVisible();
    await checkpoint(page, 'my-fairteiler');
  });

  // measured 2026-11-15: 6 clicks on desktop, 7 on the phone
  walk.finish({ steps: 5, clicks: 7, fields: 0 });
});
