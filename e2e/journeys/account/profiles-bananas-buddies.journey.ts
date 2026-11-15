import { BANANA_TEXT, acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/auth';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local) on the social side of the platform: she opens her own profile from the user menu
 * and writes on her wall (A6), gives the welcome team member Mia a trust banana with exactly 100 characters, which
 * Mia then deletes from her received bananas (A15, G4, bell N3), and accepts the buddy request of Julien (guide@local)
 * from its bell, finds him in her buddies and removes him again (A16, N3). Seed: bieb has not given welcome a banana;
 * bieb and guide are not buddies. Rules (100 characters, once per pair, who may delete, who sees stores and the
 * foodsaver number) are unit tests: ProfilePermissionMatrixTest, client profiles.util.spec.
 */
test.use({ role: 'bieb' });

const WALL_TEXT = 'Ab heute wieder für Abholungen in Wiedikon da.';

test('store manager gives a banana, accepts a buddy request and writes on her wall @A6 @A15 @G4 @A16 @N3', async ({
  page,
  as,
  apiOf,
  profiles,
  notifications,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'store manager');
  const [biebId, welcomeId, guideId] = await Promise.all([profiles.userId('bieb'), profiles.userId('welcome'), profiles.userId('guide')]);

  await walk.step('opens her own profile and writes on her wall', async () => {
    await page.goto('/dashboard');
    await walk.click(page.getByTestId('shell.userMenu'));
    await walk.click(page.getByTestId('nav.user.profile'));
    await expect(page).toHaveURL(new RegExp(`/profile/${biebId}$`));
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('profiles.page.stat.pickups')).toBeVisible();
    await expect(page.getByTestId('profiles.page.stat.bananas')).toBeVisible();
    await expect(page.getByTestId('profiles.page.region').first()).toBeVisible();
    await expect(page.getByTestId('profiles.page.stores')).toBeVisible();
    await expect(page.getByTestId('profiles.page.myBuddies')).toBeVisible();
    // no foodsaver number on the own profile (only for administrators of the user)
    await expect(page.getByTestId('profiles.page.foodsaverNumber')).toHaveCount(0);
    // her wall is still empty in the seed
    await expectDataState(page, 'profiles.wall.state', 'empty');
    await checkpoint(page, 'own-profile');
    await walk.fill(page.getByTestId('profiles.wall.text'), WALL_TEXT);
    await walk.click(page.getByTestId('profiles.wall.submit'));
    await expect(page.getByTestId('profiles.wall.text')).toHaveValue('');
    await expect(page.getByTestId('profiles.wall.post').first().getByTestId('profiles.wall.body')).toContainText(WALL_TEXT);
  });

  const before = await profiles.profile<{ stats: { bananaCount: number } }>('welcome', welcomeId);
  const message = BANANA_TEXT.slice(0, 100);
  expect(message.trim()).toHaveLength(100);

  await walk.step('gives Mia a banana', async () => {
    await page.goto(`/profile/${welcomeId}`);
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('profiles.wall.composer')).toHaveCount(0);
    await checkpoint(page, 'other-profile');
    await walk.click(page.getByTestId('profiles.page.banana'));
    const text = page.getByTestId('profiles.bananaDialog.message');
    await expect(text).toBeVisible();
    // too short: the dialog keeps the text and sends nothing
    await walk.fill(text, 'Danke für alles!');
    await walk.click(page.getByTestId('profiles.bananaDialog.confirm'));
    await expect(text).toHaveAttribute('aria-invalid', 'true');
    await walk.fill(text, message);
    await checkpoint(page, 'banana-dialog');
    await walk.click(page.getByTestId('profiles.bananaDialog.confirm'));
    await expect(text).toBeHidden();
    await expect(page.getByTestId('profiles.page.bananaGiven')).toBeVisible();
    await expect(page.getByTestId('profiles.page.banana')).toHaveCount(0);
  });

  await walk.step('Mia finds the banana and deletes it', async () => {
    await expect.poll(async () => (await notifications.bells('welcome', 'BANANA_GIVEN')).length).toBe(1);
    const welcome = await as('welcome');
    await acceptPendingConsents(apiOf(welcome));
    await welcome.goto('/profile/me?tab=bananas');
    await expect(welcome).toHaveURL(new RegExp(`/profile/${welcomeId}\\?tab=bananas$`));
    await expectDataState(welcome, 'profiles.bananas.state', 'success');
    const item = welcome.locator(`[data-testid="profiles.bananas.item"][data-sender-id="${biebId}"]`);
    await expect(item.getByTestId('profiles.bananas.message')).toHaveText(message);
    const count = welcome.getByTestId('profiles.page.stat.bananas').locator('strong');
    await expect(count).toHaveText(String(before.stats.bananaCount + 1));
    await checkpoint(welcome, 'received-bananas');
    await item.getByTestId('profiles.bananas.delete').click();
    await acceptConfirm(welcome);
    await expect(item).toHaveCount(0);
    await expect(count).toHaveText(String(before.stats.bananaCount));
  });

  await walk.step('accepts Julien\'s buddy request from its bell', async () => {
    // Julien asks on his device
    expect((await profiles.buddy('guide', biebId)).status).toBe('REQUESTED_BY_ME');
    await openNewestBell(walk, apiOf(page), 'BUDDY_REQUEST');
    await expect(page).toHaveURL(new RegExp(`/profile/${guideId}$`));
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('profiles.page.buddyAccept')).toBeVisible();
    await checkpoint(page, 'buddy-request');
    await walk.click(page.getByTestId('profiles.page.buddyAccept'));
    await expect(page.getByTestId('profiles.page.buddyConfirmed')).toBeVisible();
  });

  await walk.step('finds Julien in her buddies and removes him again', async () => {
    await walk.click(page.getByTestId('shell.userMenu'));
    await walk.click(page.getByTestId('nav.user.profile'));
    await walk.click(page.getByTestId('profiles.page.myBuddies'));
    await expect(page).toHaveURL(/\/profile\/me\/buddies$|\/buddies$/);
    await expectDataState(page, 'profiles.buddies.state', 'success');
    const row = page.locator(`[data-testid="profiles.buddies.buddy"][data-user-id="${guideId}"]`);
    await expect(row).toBeVisible();
    await checkpoint(page, 'buddies');
    await walk.click(row.getByTestId('profiles.buddies.remove'));
    walk.count(1);
    await acceptConfirm(page);
    await expect(row).toHaveCount(0);
    await page.goto(`/profile/${guideId}`);
    await expect(page.getByTestId('profiles.page.buddyRequest')).toBeVisible();
  });

  // measured 2026-11-15 (rework step 3): 14 clicks on desktop, 15 on the phone (bells live behind Menu)
  walk.finish({ steps: 5, clicks: 15, fields: 3 });
});
