import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';
import { thread } from './forum-helpers';

/** F5 forum search per region (title search, optionally post bodies with snippets). */
test.use({ role: 'foodsaver' });

test('F5 search titles and bodies in region forum with snippets @F5 @page', async ({ page, region }) => {
  await page.goto(`/regions/${region.id('zuerich-stadt')}/forum`);
  await expectDataState(page, 'forum.list.state', 'success');
  const title = (await page.locator(`[data-thread-id="${thread(4)}"]`).getAttribute('data-title'))!;
  const word = title.split(/\s+/).find((w) => w.length > 3)!;

  await page.getByTestId('forum.search.input').fill(word);
  await page.getByTestId('forum.search.submit').click();
  await expectDataState(page, 'forum.search.state', 'success');
  await expect(page.locator(`[data-testid="forum.search.hit"][data-thread-id="${thread(4)}"]`)).toBeVisible();

  await page.getByTestId('forum.search.input').fill('xyzzy-nichts');
  await page.getByTestId('forum.search.submit').click();
  await expectDataState(page, 'forum.search.state', 'empty');

  // body search: a word only found in posts
  await page.getByTestId('forum.search.body').click();
  await page.getByTestId('forum.search.input').fill('Milchprodukte');
  await page.getByTestId('forum.search.submit').click();
  await expectDataState(page, 'forum.search.state', 'success');
  await expect(page.getByTestId('forum.search.snippet').first().locator('mark').first()).toBeVisible();
  await expectNoSeriousViolations(page);

  await page.getByTestId('forum.search.clear').click();
  await expectDataState(page, 'forum.list.state', 'success');
});
