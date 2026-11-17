import path from 'node:path';
import { type BellDto, acceptPendingConsents, expectDataState, pickOption } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Blog (C2, N3): the ambassador Welcome (welcome@local, admin of the Begrüssungsteam Zürich) writes a post with a cropped
 * picture and asks for the check; Bot (bot@local, admin of the PR group) gets the BLOG_NEW_CHECK bell, opens the post
 * from it and publishes it; a visitor finds it first in the blog, filters by region and reads it. Rules behind it:
 * BlogRulesTest, BlogServiceTest (who publishes, check bell, unpublished posts are not public, the BLOG toggle);
 * client blog-pages.spec, blog.util.spec (status, teaser, forbidden management).
 */
test.use({ role: 'welcome' });

const WELCOME_GROUP = '00000011-0001-4000-8000-000000000020';
const AWAITING_POST = '00000012-0006-4000-8000-000000000007';
const ZURICH = '00000010-0001-4000-8000-000000000040';
/** A 256 px PNG to crop (the screenshot tile stub). */
const PICTURE = path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png');

test('ambassador writes a blog post, the PR admin publishes it from the check bell and a visitor reads it @C2 @N3', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'ambassador');
  const title = 'Rekord im Oktober';
  let postId = '';

  await walk.step('opens the blog management', async () => {
    // the blog has no menu entry for signed-in members, and "manage blog" is not in Welcome's menu either: she keeps
    // the blog as a bookmark
    walk.note('no menu entry leads a signed-in ambassador to the blog or its management');
    await page.goto('/blog');
    await expectDataState(page, 'blog.list.state', 'success');
    await walk.click(page.getByTestId('blog.list.manage'));
    await expect(page).toHaveURL(/\/blog\/manage$/);
    await expectDataState(page, 'blog.manage.state', 'success');
    await expect(
      page.locator(`[data-testid="blog.manage.post"][data-id="${AWAITING_POST}"] [data-testid="blog.manage.status"]`),
    ).toHaveAttribute('data-status', 'awaitingCheck');
    await checkpoint(page, 'blog-manage');
  });

  await walk.step('writes a post with a picture for the Begrüssungsteam', async () => {
    await walk.click(page.getByTestId('blog.manage.new'));
    await expect(page).toHaveURL(/\/blog\/new$/);
    await walk.fill(page.getByTestId('blog.editor.postTitle'), title);
    await walk.fill(page.getByTestId('blog.editor.body'), '## Danke!\n\nIm Oktober haben wir **12 Tonnen** gerettet.');
    await pickOption(page, 'blog.editor.region', WELCOME_GROUP);
    walk.count(2);
    await page.getByTestId('blog.editor.picture.input').setInputFiles(PICTURE);
    walk.count(1);
    await walk.click(page.getByTestId('blog.editor.picture.apply'));
    await expect(page.getByTestId('blog.editor.picturePreview')).toBeVisible();
    await expect(page.getByTestId('blog.editor.pictureUploading')).toHaveCount(0);
    // ambassadors do not publish themselves
    await expect(page.getByTestId('blog.editor.publish')).toHaveCount(0);
    await checkpoint(page, 'blog-editor-new');
    await walk.click(page.getByTestId('blog.editor.save'));
    await expect(page).toHaveURL(/\/blog\/[0-9a-f-]{36}\/edit$/);
    postId = page.url().split('/').at(-2)!;
    await expectDataState(page, 'blog.editor.state', 'success');
    await expect(page.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'draft');
  });

  await walk.step('asks for the check', async () => {
    await walk.click(page.getByTestId('blog.editor.requestCheck'));
    await expect(page.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'awaitingCheck');
    await expect(page.getByTestId('blog.editor.withdraw')).toBeVisible();
    await checkpoint(page, 'blog-editor-awaiting-check');
  });

  // the PR admin gets the check bell and publishes from it
  const bot = await as('bot');
  const botApi = apiOf(bot);
  await acceptPendingConsents(botApi);
  const pr = new Walk(bot, testInfo, 'PR admin');
  const checkBells = async () =>
    (await botApi.getJson<{ items: BellDto[] }>('/api/bells', { params: { size: 50 } })).items.filter(
      (bell) => bell.identifier === `blog-check-${postId}`,
    );

  await pr.step('opens the post from the check bell and publishes it', async () => {
    await bot.goto('/dashboard');
    await expect(bot.getByTestId('dashboard.page.welcome')).toBeVisible();
    await openNewestBell(pr, botApi, 'BLOG_NEW_CHECK');
    await expect(bot).toHaveURL(new RegExp(`/blog/${postId}/edit$`));
    await expectDataState(bot, 'blog.editor.state', 'success');
    await expect(bot.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'awaitingCheck');
    await expect(bot.getByTestId('blog.editor.requestCheck')).toHaveCount(0);
    await checkpoint(bot, 'blog-editor-check');
    await pr.click(bot.getByTestId('blog.editor.publish'));
    await expect(bot.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'published');
    await expect(bot.getByTestId('blog.editor.unpublish')).toBeVisible();
    // publishing removes the check bell
    await expect.poll(async () => (await checkBells()).length).toBe(0);
  });

  // a visitor reads it
  const guest = await as(null);
  const reader = new Walk(guest, testInfo, 'visitor');
  await reader.step('finds the post first in the blog and reads it', async () => {
    await guest.goto('/blog');
    await expectDataState(guest, 'blog.list.state', 'success');
    const first = guest.getByTestId('blog.list.post').first();
    await expect(first).toHaveAttribute('data-id', postId);
    await expect(first.locator('img')).toBeVisible();
    await checkpoint(guest, 'blog-list');
    await reader.click(first.getByTestId('blog.list.open'));
    await expect(guest).toHaveURL(new RegExp(`/blog/${postId}$`));
    await expectDataState(guest, 'blog.post.state', 'success');
    await expect(guest.getByTestId('blog.post.body').locator('strong')).toHaveText('12 Tonnen');
    await expect(guest.getByTestId('blog.post.teaser')).toContainText('Danke! Im Oktober');
    await expect(guest.getByTestId('blog.post.picture')).toBeVisible();
    await checkpoint(guest, 'blog-post');
  });

  await reader.step('filters the blog by Zürich', async () => {
    await guest.goto('/blog');
    await expectDataState(guest, 'blog.list.state', 'success');
    const posts = guest.getByTestId('blog.list.post');
    await expect(posts).toHaveCount(7);
    await reader.click(guest.locator(`[data-testid="blog.list.post"]:not([data-id="${postId}"]) [data-testid="blog.list.region"]`).first());
    await expect(guest).toHaveURL(new RegExp(`/blog\\?region=${ZURICH}$`));
    await expect(guest.getByTestId('blog.list.filter')).toContainText('Zürich');
    // seed: posts 01, 02, 05 belong to Zürich Stadt
    await expect(posts).toHaveCount(3);
    await reader.click(guest.getByTestId('blog.list.clearFilter'));
    await expect(posts).toHaveCount(7);
  });

  walk.finish({ steps: 3, clicks: 8, fields: 2 });
  pr.finish({ steps: 1, clicks: 4, fields: 0 });
  reader.finish({ steps: 2, clicks: 3, fields: 0 });
});
