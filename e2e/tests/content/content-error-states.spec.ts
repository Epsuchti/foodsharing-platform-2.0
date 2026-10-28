import { expect, expectDataState, expectErrorState, expectSkeleton, injectApiError, test } from '../../fixtures';

/** Loading, error + retry and not-found states of the content and learning pages (M2 T20). */

test('learning overview: loading skeleton, then error with retry recovers @C1', async ({ page }) => {
  const slow = await injectApiError(page, { url: /\/api\/public\/content\/learning(\?|$)/, delayMs: 30_000 });
  await page.goto('/learn');
  await expectSkeleton(page, 'content.learn.state');
  await slow.remove();

  const failure = await injectApiError(page, { url: /\/api\/public\/content\/learning(\?|$)/, status: 500 });
  await page.goto('/learn');
  const { retry } = await expectErrorState(page, 'content.learn.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.learn.state', 'success');
});

test('unknown content page and chapter show not found @C1', async ({ page }) => {
  await page.goto('/pages/no-such-page');
  await expectDataState(page, 'content.page.state', 'notFound');
  await page.goto('/learn/no-such-chapter');
  await expectDataState(page, 'content.chapter.state', 'notFound');
});

test('content page: server error with retry @C1', async ({ page }) => {
  const failure = await injectApiError(page, { url: /\/api\/public\/content\/vision/, status: 503 });
  await page.goto('/pages/vision');
  const { retry } = await expectErrorState(page, 'content.page.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.page.state', 'success');
  await expect(page.getByTestId('content.page.body')).not.toBeEmpty();
});

test.describe('signed in', () => {
  test.use({ role: 'orga' });

  test('learning chapter: marking read fails with a message and can be repeated @C1', async ({ page }) => {
    await page.goto('/learn/rules');
    await expectDataState(page, 'content.chapter.state', 'success');
    const failure = await injectApiError(page, { url: /\/api\/onboarding\/learning\/rules\/read/, method: 'POST', status: 500 });
    await page.getByTestId('content.chapter.markRead').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
    await expect(page).toHaveURL(/\/learn\/rules$/);
    await failure.remove();
    await page.getByTestId('content.chapter.markRead').click();
    await expect(page).toHaveURL(/\/learn\/hygiene$/);
  });

  test('content admin: error with retry, editor not found @C1', async ({ page }) => {
    const failure = await injectApiError(page, { url: /\/api\/content\/pages(\?|$)/, status: 500 });
    await page.goto('/admin/content');
    const { retry } = await expectErrorState(page, 'content.admin.state');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'content.admin.state', 'success');
    await page.goto('/admin/content/no-such-page');
    await expectDataState(page, 'content.editor.state', 'notFound');
  });
});
