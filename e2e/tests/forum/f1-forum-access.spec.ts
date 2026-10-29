import { expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, test } from '../../fixtures';
import { KOCHGRUPPE, thread } from './forum-helpers';

/** F1 forum access (A36): region forum for members, ambassador forum for ambassadors, group forum for group members. */
test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('F1 member sees region forum, ambassador forum only for ambassadors, group forum for group members @F1 @page', async ({ page, region, as }) => {
    const zs = region.id('zuerich-stadt');
    await page.goto(`/regions/${zs}`);
    await page.getByTestId('regions.page.tab.forum').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${zs}/forum$`));
    await expectDataState(page, 'forum.list.state', 'success');
    const threads = page.getByTestId('forum.list.thread');
    // sticky threads first, closed marked, inactive thread of someone else hidden
    await expect(threads.nth(0).getByTestId('forum.list.sticky')).toBeVisible();
    await expect(threads.nth(1).getByTestId('forum.list.sticky')).toBeVisible();
    await expect(page.locator(`[data-thread-id="${thread(6)}"]`).getByTestId('forum.list.closed')).toBeVisible();
    await expect(page.locator(`[data-thread-id="${thread(14)}"]`)).toHaveCount(0);
    await expect(page.getByTestId('forum.list.tabAmbassadors')).toHaveCount(0);
    await expect(page.getByTestId('forum.list.follow')).toHaveAttribute('data-following', 'true');
    await expectNoSeriousViolations(page);

    await page.goto(`/regions/${zs}/forum/ambassadors`);
    await expectDataState(page, 'forum.list.state', 'forbidden');

    await page.goto(`/regions/${KOCHGRUPPE}/forum`);
    await expectDataState(page, 'forum.list.state', 'success');
    await expect(page.locator(`[data-thread-id="${thread(18)}"]`)).toBeVisible();

    const bieb = await as('bieb');
    await bieb.goto(`/regions/${KOCHGRUPPE}/forum`);
    await expectDataState(bieb, 'forum.list.state', 'forbidden');

    const bot = await as('bot');
    await bot.goto(`/regions/${zs}/forum`);
    await expectDataState(bot, 'forum.list.state', 'success');
    await expect(bot.locator(`[data-thread-id="${thread(14)}"]`).getByTestId('forum.list.inactive')).toBeVisible();
    await bot.getByTestId('forum.list.tabAmbassadors').click();
    await expect(bot).toHaveURL(/\/forum\/ambassadors$/);
    await expect(bot.locator(`[data-thread-id="${thread(15)}"]`)).toBeVisible();
    await bot.locator(`[data-thread-id="${thread(15)}"]`).click();
    await expectDataState(bot, 'forum.thread.state', 'success');
    await expect(bot.getByTestId('forum.thread.post').first()).toBeVisible();
  });

  test('F1 forum errors show retry and recover; unknown thread is not found @F1', async ({ page, region }) => {
    const zs = region.id('zuerich-stadt');
    const injection = await injectApiError(page, { url: /\/forum\/threads(\?|$)/, status: 500 });
    await page.goto(`/regions/${zs}/forum`);
    const { retry } = await expectErrorState(page, 'forum.list.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'forum.list.state', 'success');
    await page.goto(`/forum/threads/${thread(999)}`);
    await expect(page.getByTestId('forum.thread.state')).toHaveAttribute('data-state', /notFound|forbidden/);
  });
});
