import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * A6 public profile (plan-m7 Q2, A46): foodsaver@local has the GLOBAL hygiene award, store teams in zuerich-stadt and
 * 6 wall posts (docs/seed-ids.md). Stores and the foodsaver number depend on `ProfilePermissions`.
 */
test.use({ seedReset: 'test' });

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('A6 public profile shows stats, regions, stores for admins, achievements and wall post @A6 @page', async ({ page, as, apiOf, profiles }) => {
    const foodsaverId = await profiles.userId('foodsaver');

    // the head of the user menu is the profile entry (/profile/me → own id)
    await page.goto('/dashboard');
    await page.getByTestId('shell.userMenu').click();
    await page.getByTestId('nav.user.profile').click();
    await expect(page).toHaveURL(new RegExp(`/profile/${foodsaverId}$`));
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('profiles.page.name')).toContainText('Sarah Müller');
    await expect(page.getByTestId('profiles.page.stat.pickups')).toBeVisible();
    await expect(page.getByTestId('profiles.page.stat.bananas')).toBeVisible();
    await expect(page.getByTestId('profiles.page.region').first()).toBeVisible();
    await expect(page.getByTestId('profiles.page.stores')).toBeVisible();
    await expect(page.getByTestId('profiles.page.achievement').first()).toBeVisible();
    await expect(page.getByTestId('profiles.page.myBuddies')).toBeVisible();
    // no foodsaver number on the own profile (only for administrators of the user)
    await expect(page.getByTestId('profiles.page.foodsaverNumber')).toHaveCount(0);

    // only the owner writes on the profile wall
    await expectDataState(page, 'profiles.wall.state', 'success');
    const text = `Ab heute wieder für Abholungen in Wiedikon da ${Date.now()}`;
    await page.getByTestId('profiles.wall.text').fill(text);
    await page.getByTestId('profiles.wall.submit').click();
    await expect(page.getByTestId('profiles.wall.text')).toHaveValue('');
    await expect(page.getByTestId('profiles.wall.post').first().getByTestId('profiles.wall.body')).toContainText(text);

    // ORGA administers the user: foodsaver number, stores, admin tabs
    const orga = await as('orga');
    await orga.goto(`/profile/${foodsaverId}`);
    await expectDataState(orga, 'profiles.page.state', 'success');
    await expect(orga.getByTestId('profiles.page.foodsaverNumber')).toBeVisible();
    await expect(orga.getByTestId('profiles.page.stores')).toBeVisible();
    await expect(orga.getByTestId('profiles.page.tab.notes')).toBeVisible();
    await expect(orga.getByTestId('profiles.page.tab.history')).toBeVisible();
    await expect(orga.getByTestId('profiles.wall.composer')).toHaveCount(0);
    await orga.getByTestId('profiles.page.tab.history').click();
    await expect(orga).toHaveURL(/tab=history/);
    await expect(orga.getByTestId('profiles.page.panel')).toHaveAttribute('data-tab', 'history');

    // a user without a common store team: no stores, no number, no admin tabs; reads the wall
    const quiz = await as('quiz');
    await acceptPendingConsents(apiOf(quiz));
    await quiz.goto(`/profile/${foodsaverId}`);
    await expectDataState(quiz, 'profiles.page.state', 'success');
    await expect(quiz.getByTestId('profiles.page.achievement').first()).toBeVisible();
    await expect(quiz.getByTestId('profiles.page.stores')).toHaveCount(0);
    await expect(quiz.getByTestId('profiles.page.foodsaverNumber')).toHaveCount(0);
    await expect(quiz.getByTestId('profiles.page.tab.notes')).toHaveCount(0);
    await expect(quiz.getByTestId('profiles.wall.composer')).toHaveCount(0);
    await expectDataState(quiz, 'profiles.wall.state', 'success');
    await expect(quiz.getByTestId('profiles.wall.post').first().getByTestId('profiles.wall.body')).toContainText(text);
  });

  test('A6 profile of a deleted or unknown user shows not found @A6', async ({ page, profiles, as, apiOf }) => {
    await page.goto('/profile/00000001-0001-4000-8000-0000000009ff');
    await expectErrorState(page, 'profiles.page.state', { status: 'notFound', retry: false });

    // ORGA deletes (anonymises) pending@local; the profile is gone afterwards
    const pendingId = await profiles.userId('pending');
    const orga = await as('orga');
    const deleted = await apiOf(orga).fetch('DELETE', '/api/admin/users/' + pendingId, { reason: 'e2e A6' });
    expect(deleted.status(), await deleted.text()).toBe(204);
    await page.goto(`/profile/${pendingId}`);
    await expectErrorState(page, 'profiles.page.state', { status: 'notFound', retry: false });
  });

  test('A6 profile load error shows retry @A6', async ({ page, profiles }) => {
    const sleepyId = await profiles.userId('sleepy');
    const injection = await injectApiError(page, { url: '/api/users/*/profile', status: 503, times: 3 });
    await page.goto(`/profile/${sleepyId}`);
    const { retry } = await expectErrorState(page, 'profiles.page.state');
    // the retry interceptor retries a GET twice before the error state shows
    expect(injection.calls()).toBe(3);
    await retry.click();
    await expectDataState(page, 'profiles.page.state', 'success');
    // sleepy is in FULL sleep mode (A39)
    await expect(page.getByTestId('profiles.page.sleep')).toBeVisible();
  });
});

