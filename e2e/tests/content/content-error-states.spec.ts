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
  const failure = await injectApiError(page, { url: /\/api\/public\/content\/partners/, status: 503 });
  await page.goto('/pages/partners');
  const { retry } = await expectErrorState(page, 'content.page.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.page.state', 'success');
  await expect(page.getByTestId('content.page.body')).not.toBeEmpty();
});

test.describe('reader', () => {
  // profile@local has read no chapter yet (C1: an already read chapter has no mark button)
  test.use({ role: 'profile' });

  test('learning chapter: marking read fails with a message and can be repeated @C1', async ({ page }) => {
    await page.goto('/learn/principles');
    await expectDataState(page, 'content.chapter.state', 'success');
    const failure = await injectApiError(page, { url: /\/api\/onboarding\/learning\/principles\/read/, method: 'POST', status: 500 });
    await page.getByTestId('content.chapter.markRead').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
    await expect(page).toHaveURL(/\/learn\/principles$/);
    await failure.remove();
    await page.getByTestId('content.chapter.markRead').click();
    await expect(page).toHaveURL(/\/learn\/code-of-conduct$/);
  });

  test('learning chapter: the read state loads with error and retry @C1', async ({ page }) => {
    const failure = await injectApiError(page, { url: /\/api\/onboarding\/journey(\?|$)/, status: 500 });
    // a chapter the reader has not read (the test above marks principles)
    await page.goto('/learn/hygiene-rules');
    await expectDataState(page, 'content.chapter.state', 'success');
    const { retry } = await expectErrorState(page, 'content.chapter.readState');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'content.chapter.readState', 'success');
    await expect(page.getByTestId('content.chapter.markRead')).toBeVisible();
  });
});

test.describe('signed in', () => {
  test.use({ role: 'orga' });

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
