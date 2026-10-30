import path from 'node:path';
import {
  acceptPendingConsents,
  type BellDto,
  expect,
  expectDataState,
  expectErrorState,
  expectNoMissingKeys,
  injectApiError,
  pickOption,
  setLanguage,
  test,
} from '../../fixtures';

/**
 * C2 blog (plan-m7 Q11, A54): ambassadors (admins of a region or group) write posts of their units and ask for a
 * check, which bells the PR group admins (BLOG_NEW_CHECK, N3); an editor (ORGA, PR group admin) publishes; the public
 * list and post follow. Seed: welcome administrates Begrüssungsteam Zürich (group 20) and has post 07 awaiting the
 * check; bot administrates the PR group.
 */
test.use({ seedReset: 'test' });

const WELCOME_GROUP = '00000011-0001-4000-8000-000000000020';
const AWAITING_POST = '00000012-0006-4000-8000-000000000007';
const ZURICH = '00000010-0001-4000-8000-000000000040';
/** A 256 px PNG to crop (the screenshot tile stub). */
const PICTURE = path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png');

test('C2 ambassador drafts a region post, PR admin gets check bell and publishes, public list shows it @C2 @N3 @page', async ({
  as,
  apiOf,
  notifications,
  page,
}) => {
  const title = `Rekord im Oktober ${Date.now()}`;

  // welcome: from the blog to the management list, where the seed post waits for the check
  const welcome = await as('welcome');
  await acceptPendingConsents(apiOf(welcome));
  await setLanguage(welcome, 'de');
  await welcome.goto('/blog');
  await expectDataState(welcome, 'blog.list.state', 'success');
  await welcome.getByTestId('blog.list.manage').click();
  await expect(welcome).toHaveURL(/\/blog\/manage$/);
  await expectDataState(welcome, 'blog.manage.state', 'success');
  await expect(
    welcome.locator(`[data-testid="blog.manage.post"][data-id="${AWAITING_POST}"] [data-testid="blog.manage.status"]`),
  ).toHaveAttribute('data-status', 'awaitingCheck');

  // a new post for the Begrüssungsteam with a cropped picture; no publish button for ambassadors
  await welcome.getByTestId('blog.manage.new').click();
  await expect(welcome).toHaveURL(/\/blog\/new$/);
  await welcome.getByTestId('blog.editor.postTitle').fill(title);
  await welcome.getByTestId('blog.editor.body').fill('## Danke!\n\nIm Oktober haben wir **12 Tonnen** gerettet.');
  await pickOption(welcome, 'blog.editor.region', WELCOME_GROUP);
  await welcome.getByTestId('blog.editor.picture.input').setInputFiles(PICTURE);
  await welcome.getByTestId('blog.editor.picture.apply').click();
  await expect(welcome.getByTestId('blog.editor.picturePreview')).toBeVisible();
  await expect(welcome.getByTestId('blog.editor.pictureUploading')).toHaveCount(0);
  await welcome.getByTestId('blog.editor.save').click();
  await expect(welcome).toHaveURL(/\/blog\/[0-9a-f-]{36}\/edit$/);
  const postId = welcome.url().split('/').at(-2)!;
  await expectDataState(welcome, 'blog.editor.state', 'success');
  await expect(welcome.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'draft');
  await expect(welcome.getByTestId('blog.editor.publish')).toHaveCount(0);
  await welcome.getByTestId('blog.editor.requestCheck').click();
  await expect(welcome.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'awaitingCheck');
  await expect(welcome.getByTestId('blog.editor.withdraw')).toBeVisible();
  await expectNoMissingKeys(welcome);

  // not public yet
  await page.goto(`/blog/${postId}`);
  await expectErrorState(page, 'blog.post.state', { status: 'notFound', retry: false });

  // bot (PR group admin) gets the check bell; its link opens the editor, where bot publishes
  await expect
    .poll(async () => (await notifications.bells('bot', 'BLOG_NEW_CHECK')).map((bell) => bell.link))
    .toContain(`/blog/${postId}/edit`);
  const bot = await as('bot');
  const botApi = apiOf(bot);
  await acceptPendingConsents(botApi);
  // the bot's current bells (GET /api/bells): the check bell is there until the post is published
  const checkBell = async () =>
    (await botApi.getJson<{ items: BellDto[] }>('/api/bells', { params: { size: 50 } })).items.filter(
      (bell) => bell.identifier === `blog-check-${postId}`,
    );
  await expect.poll(async () => (await checkBell()).map((bell) => bell.link)).toEqual([`/blog/${postId}/edit`]);
  await setLanguage(bot, 'de');
  await bot.goto(`/blog/${postId}/edit`);
  await expectDataState(bot, 'blog.editor.state', 'success');
  await expect(bot.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'awaitingCheck');
  await expect(bot.getByTestId('blog.editor.requestCheck')).toHaveCount(0);
  await bot.getByTestId('blog.editor.publish').click();
  await expect(bot.getByTestId('blog.editor.status')).toHaveAttribute('data-status', 'published');
  await expect(bot.getByTestId('blog.editor.unpublish')).toBeVisible();
  // publishing removes the check bell
  await expect.poll(async () => (await checkBell()).length).toBe(0);

  // public: the newest post comes first, with picture; it opens with its Markdown body and generated teaser
  await setLanguage(page, 'de');
  await page.goto('/blog');
  await expectDataState(page, 'blog.list.state', 'success');
  const first = page.getByTestId('blog.list.post').first();
  await expect(first).toHaveAttribute('data-id', postId);
  await expect(first.locator('img')).toBeVisible();
  await first.getByTestId('blog.list.open').click();
  await expect(page).toHaveURL(new RegExp(`/blog/${postId}$`));
  await expectDataState(page, 'blog.post.state', 'success');
  await expect(page.getByTestId('blog.post.body').locator('strong')).toHaveText('12 Tonnen');
  await expect(page.getByTestId('blog.post.teaser')).toContainText('Danke! Im Oktober');
  await expect(page.getByTestId('blog.post.picture')).toBeVisible();
  await expectNoMissingKeys(page);
});

test('C2 the region chip filters the list; pages follow the BLOG toggle @C2 @C11', async ({ api, page }) => {
  await setLanguage(page, 'de');
  await page.goto('/blog');
  await expectDataState(page, 'blog.list.state', 'success');
  await expect(page.getByTestId('blog.list.post')).toHaveCount(6);
  await page.locator(`[data-testid="blog.list.region"]`).first().click();
  await expect(page).toHaveURL(new RegExp(`/blog\\?region=${ZURICH}$`));
  await expect(page.getByTestId('blog.list.filter')).toContainText('Zürich');
  // seed: posts 01, 02, 05 belong to Zürich Stadt
  await expect(page.getByTestId('blog.list.post')).toHaveCount(3);
  await page.getByTestId('blog.list.clearFilter').click();
  await expect(page.getByTestId('blog.list.post')).toHaveCount(6);

  await api.loginAs('orga');
  const off = await api.fetch('PATCH', '/api/admin/feature-toggles/BLOG', { enabled: false });
  expect(off.status(), await off.text()).toBe(200);
  await page.goto('/blog');
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();
  // back on: the server caches toggle states and a seed reset does not clear that cache (reported to the backend)
  const on = await api.fetch('PATCH', '/api/admin/feature-toggles/BLOG', { enabled: true });
  expect(on.status(), await on.text()).toBe(200);
});

test.describe('error states', () => {
  test('blog list: server error with retry @C2', async ({ page }) => {
    // the retry interceptor repeats GETs twice: three failures reach the page
    const failure = await injectApiError(page, { url: /\/api\/public\/blog(\?|$)/, status: 500, times: 3 });
    await page.goto('/blog');
    const { retry } = await expectErrorState(page, 'blog.list.state');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'blog.list.state', 'success');
  });

  test.describe('signed in', () => {
    test.use({ role: 'foodsaver' });

    test('blog management is forbidden without blog rights @C2', async ({ page }) => {
      await page.goto('/blog/manage');
      await expectErrorState(page, 'blog.manage.state', { status: 'forbidden', retry: false });
    });
  });
});
