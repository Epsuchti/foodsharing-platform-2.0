import { acceptPendingConsents, expect, test, type Role } from '../../fixtures';

/** Dashboard "next step" card: one concrete action per role until the onboarding journey (M2) replaces it. */

// accepting newbie's consents changes data: default per-file seed reset

const CASES: { role: Role; step: string; href: string }[] = [
  { role: 'newbie', step: 'passkey', href: '/account/settings?addPasskey=1' },
  { role: 'foodsaver', step: 'profile', href: '/account/profile' },
  { role: 'orga', step: 'activity', href: '/me/activity' },
];

for (const { role, step, href } of CASES) {
  test(`D4.3 dashboard next step for ${role} is actionable @D4.3`, async ({ as, apiOf }) => {
    const page = await as(role);
    await acceptPendingConsents(apiOf(page));
    await page.goto('/dashboard');
    const card = page.getByTestId('dashboard.page.nextStep');
    await expect(card).toHaveAttribute('data-step', step);
    const action = page.getByTestId('dashboard.page.nextStepAction');
    await expect(action).toHaveAttribute('href', href);
    await action.click();
    await expect.poll(() => new URL(page.url()).pathname).toBe(href.split('?')[0]);
  });
}

test('D4.3 dashboard explains the role, shows the home region and an admin card for ambassadors @D4.3', async ({ as, apiOf }) => {
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/dashboard');
  await expect(foodsaver.getByTestId('dashboard.page.roleHint')).not.toBeEmpty();
  await expect(foodsaver.getByTestId('dashboard.page.homeRegion')).toContainText('Zürich');
  await expect(foodsaver.getByTestId('dashboard.page.adminCard')).toHaveCount(0);

  const ambassador = await as('bot');
  await ambassador.goto('/dashboard');
  await expect(ambassador.getByTestId('dashboard.page.adminCard')).toBeVisible();

  // newcomers have no home region yet
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto('/dashboard');
  await expect(newbie.getByTestId('dashboard.page.roleHint')).toBeVisible();
  await expect(newbie.getByTestId('dashboard.page.homeRegion')).toHaveCount(0);
});
