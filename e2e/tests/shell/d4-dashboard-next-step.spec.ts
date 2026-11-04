import { acceptPendingConsents, expect, expectStatus, test, type Role } from '../../fixtures';

/**
 * Dashboard "next step" card: one concrete action, only when something is missing (owner feedback 2026-11-04);
 * newcomers get the journey's next step (M2 T22).
 */

// accepting newbie's consents and removing the photo change data: seed reset per test
test.use({ seedReset: 'test' });

const CASES: { role: Role; step: string; href: string; removePhoto?: boolean }[] = [
  { role: 'newbie', step: 'journey', href: '/onboarding/profile' },
  // every seed foodsaver has avatar and phone: without the photo the profile step comes back (without the phone the
  // onboarding journey reopens and shows its own step)
  { role: 'foodsaver', step: 'profile', href: '/account/profile', removePhoto: true },
];

for (const { role, step, href, removePhoto } of CASES) {
  test(`D4.3 dashboard next step for ${role} is actionable @D4.3`, async ({ as, apiOf }) => {
    const page = await as(role);
    await acceptPendingConsents(apiOf(page));
    if (removePhoto) {
      await expectStatus(await apiOf(page).delete('/api/users/me/photo'), [204], 'remove photo');
    }
    await page.goto('/dashboard');
    const card = page.getByTestId('dashboard.page.nextStep');
    await expect(card).toHaveAttribute('data-step', step);
    const action = page.getByTestId('dashboard.page.nextStepAction');
    await expect(action).toHaveAttribute('href', href);
    await action.click();
    await expect.poll(() => new URL(page.url()).pathname).toBe(href.split('?')[0]);
  });
}

test('D4.3 dashboard has no next step when nothing is missing, no role hint, quick links or admin card @D4.3', async ({ as, apiOf }) => {
  for (const role of ['foodsaver', 'orga'] as const) {
    const page = await as(role);
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome'), role).toBeVisible();
    await expect(page.getByTestId('dashboard.page.role'), role).toBeVisible();
    await expect(page.getByTestId('dashboard.page.nextStep'), role).toHaveCount(0);
    for (const removed of ['roleHint', 'adminCard', 'sessionsLink', 'activityLink', 'settingsLink']) {
      await expect(page.getByTestId(`dashboard.page.${removed}`), `${role} ${removed}`).toHaveCount(0);
    }
  }
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/dashboard');
  await expect(foodsaver.getByTestId('dashboard.page.homeRegion')).toContainText('Zürich');

  // newcomers have no home region yet
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto('/dashboard');
  await expect(newbie.getByTestId('dashboard.page.welcome')).toBeVisible();
  await expect(newbie.getByTestId('dashboard.page.homeRegion')).toHaveCount(0);
});
